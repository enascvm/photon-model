/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.model.adapters.azure.instance;

import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.PROVISIONING_STATE_SUCCEEDED;
import static com.vmware.photon.controller.model.util.PhotonModelUriUtils.createInventoryUri;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.microsoft.azure.SubResource;
import com.microsoft.azure.management.network.IPAllocationMethod;
import com.microsoft.azure.management.network.ProbeProtocol;
import com.microsoft.azure.management.network.SecurityRuleAccess;
import com.microsoft.azure.management.network.SecurityRuleDirection;
import com.microsoft.azure.management.network.SecurityRuleProtocol;
import com.microsoft.azure.management.network.TransportProtocol;
import com.microsoft.azure.management.network.implementation.BackendAddressPoolInner;
import com.microsoft.azure.management.network.implementation.FrontendIPConfigurationInner;
import com.microsoft.azure.management.network.implementation.LoadBalancerInner;
import com.microsoft.azure.management.network.implementation.LoadBalancersInner;
import com.microsoft.azure.management.network.implementation.LoadBalancingRuleInner;
import com.microsoft.azure.management.network.implementation.NetworkInterfaceIPConfigurationInner;
import com.microsoft.azure.management.network.implementation.NetworkInterfaceInner;
import com.microsoft.azure.management.network.implementation.NetworkInterfacesInner;
import com.microsoft.azure.management.network.implementation.NetworkManagementClientImpl;
import com.microsoft.azure.management.network.implementation.NetworkSecurityGroupInner;
import com.microsoft.azure.management.network.implementation.NetworkSecurityGroupsInner;
import com.microsoft.azure.management.network.implementation.ProbeInner;
import com.microsoft.azure.management.network.implementation.PublicIPAddressInner;
import com.microsoft.azure.management.network.implementation.PublicIPAddressesInner;
import com.microsoft.azure.management.network.implementation.SecurityRuleInner;
import com.microsoft.rest.ServiceCallback;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.vmware.photon.controller.model.adapterapi.LoadBalancerInstanceRequest;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureBaseAdapterContext;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback.Default;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureProvisioningCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureRetryHandler;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureSecurityGroupUtils;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.BaseAdapterStage;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription.HealthCheckConfiguration;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription.RouteConfiguration;
import com.vmware.photon.controller.model.resources.LoadBalancerService.LoadBalancerStateExpanded;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

/**
 * Adapter to create/delete a load balancer on Azure.
 */
public class AzureLoadBalancerService extends StatelessService {

    public static final String SELF_LINK = AzureUriPaths.AZURE_LOAD_BALANCER_ADAPTER;

    /**
     * Load balancer request context.
     */
    private static class AzureLoadBalancerContext extends
            AzureBaseAdapterContext<AzureLoadBalancerContext> {

        final LoadBalancerInstanceRequest loadBalancerRequest;

        LoadBalancerStateExpanded loadBalancerStateExpanded;
        String resourceGroupName;
        Set<ComputeState> computeStates;
        Set<NetworkInterfaceState> networkInterfaceStates;
        List<NetworkInterfaceInner> networkInterfaceInners;
        List<SecurityGroupState> securityGroupStates;
        List<NetworkSecurityGroupInner> securityGroupInners;
        PublicIPAddressInner publicIPAddressInner;
        LoadBalancerInner loadBalancerAzure;

        AzureLoadBalancerContext(
                StatelessService service,
                LoadBalancerInstanceRequest request) {

            super(service, request);

            this.loadBalancerRequest = request;
        }

        /**
         * Get the authentication object using endpoint authentication.
         */
        @Override
        protected URI getParentAuthRef(AzureLoadBalancerContext context) {
            return UriUtils.buildUri(createInventoryUri(
                    context.service.getHost(),
                    context.loadBalancerStateExpanded.endpointState.authCredentialsLink));
        }
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        // initialize context object
        AzureLoadBalancerContext context = new AzureLoadBalancerContext(
                this, op.getBody(LoadBalancerInstanceRequest.class));

        // Immediately complete the Operation from calling task.
        op.complete();

        DeferredResult.completed(context)
                .thenCompose(this::populateContext)
                .thenCompose(this::handleLoadBalancerInstanceRequest)
                // Once done patch the calling task with correct stage.
                .whenComplete((o, e) -> context.finish(e));
    }

    /**
     * Populate the load balancer context object with
     * - Load balancer state
     * - Azure authentication info
     *
     * @param context Azure load balancer context
     * @return DeferredResult
     */
    private DeferredResult<AzureLoadBalancerContext> populateContext(
            AzureLoadBalancerContext context) {
        return DeferredResult.completed(context)
                .thenCompose(this::getLoadBalancerState)
                .thenCompose(c -> c.populateBaseContext(BaseAdapterStage.PARENTAUTH));
    }

    /**
     * Populate the context with Load balancer state
     *
     * @param context Azure load balancer context
     * @return DeferredResult
     */
    private DeferredResult<AzureLoadBalancerContext> getLoadBalancerState(
            AzureLoadBalancerContext context) {
        return this
                .sendWithDeferredResult(
                        Operation.createGet(LoadBalancerStateExpanded.buildUri(
                                context.loadBalancerRequest.resourceReference)),
                        LoadBalancerStateExpanded.class)
                .thenApply(state -> {
                    context.loadBalancerStateExpanded = state;
                    context.resourceGroupName = AzureUtils.getResourceGroupName(state.subnets
                            .iterator().next().id);
                    return context;
                });
    }

    /**
     * Handle request to Create/Delete a load balancer
     *
     * @param context Azure load balancer context
     * @return DeferredResult
     */
    private DeferredResult<AzureLoadBalancerContext> handleLoadBalancerInstanceRequest(
            AzureLoadBalancerContext context) {
        DeferredResult<AzureLoadBalancerContext> execution = DeferredResult.completed(context);
        switch (context.loadBalancerRequest.requestType) {
        case CREATE:
            if (context.loadBalancerRequest.isMockRequest) {
                // no need to go the end-point; just generate Azure Load Balancer Id.
                context.loadBalancerStateExpanded.id = UUID.randomUUID().toString();
                return execution.thenCompose(this::updateLoadBalancerState);
            } else {
                return execution
                        .thenCompose(this::getTargetStates)
                        .thenCompose(this::getNetworkInterfaceStates)
                        .thenCompose(this::getNetworkInterfaceInners)
                        .thenCompose(this::getSecurityGroupStates)
                        .thenCompose(this::getNetworkSecurityGroupInners)
                        .thenCompose(this::createPublicIP)
                        .thenCompose(this::createLoadBalancer)
                        .thenCompose(this::addHealthProbes)
                        .thenCompose(this::addLoadBalancingRules)
                        .thenCompose(this::addBackendPoolMembers)
                        .thenCompose(this::updateSecurityGroupRules)
                        .thenCompose(this::getPublicIPAddress)
                        .thenCompose(this::updateLoadBalancerState);
            }
        case DELETE:
            if (context.loadBalancerRequest.isMockRequest) {
                // no need to go to the end-point
                logFine(() -> String.format("Mock request to delete an Azure load balancer [%s] "
                        + "processed.", context.loadBalancerStateExpanded.name));

            } else {
                execution = execution
                        .thenCompose(this::deleteLoadBalancer)
                        .thenCompose(this::deletePublicIP);
            }
            return execution.thenCompose(this::deleteLoadBalancerState);
        default:
            IllegalStateException ex = new IllegalStateException("Unsupported request type");
            return DeferredResult.failed(ex);
        }
    }

    /**
     * Populate Computes/NetworkInterfaceStates in the context
     *
     * @param context Azure load balancer context
     * @return DeferredResult
     */
    private DeferredResult<AzureLoadBalancerContext> getTargetStates(
            AzureLoadBalancerContext context) {
        context.networkInterfaceStates = context.loadBalancerStateExpanded
                .getTargetsOfType(NetworkInterfaceState.class);
        context.computeStates = context.loadBalancerStateExpanded
                .getTargetsOfType(ComputeState.class);
        return DeferredResult.completed(context);
    }

    /**
     * Populate the primary nic from all computes being load balanced in the context
     * We load balance the nic with lowest device index (primary nic) if computeLinks are specified
     * as target
     *
     * @param context Azure load balancer context
     * @return DeferredResult
     */
    private DeferredResult<AzureLoadBalancerContext> getNetworkInterfaceStates(
            AzureLoadBalancerContext context) {
        if (CollectionUtils.isEmpty(context.computeStates)) {
            return DeferredResult.completed(context);
        }
        context.networkInterfaceStates = context.networkInterfaceStates == null ?
                new HashSet<>(context.computeStates.size()) : context.networkInterfaceStates;
        return DeferredResult.allOf(context.computeStates
                .stream().map(computeState ->
                        getNetworkInterfaceStatesForLinks(context,
                                computeState.networkInterfaceLinks)
                                .thenApply(networkInterfaceStates -> {
                                    context.networkInterfaceStates
                                            .add(getPrimaryNic(networkInterfaceStates));
                                    return context;
                                })).collect(Collectors.toList()))
                .thenApply(__ -> context);
    }

    /**
     * From the given list find the Network interface with lowest device index
     * Throw error if none are found
     *
     * @param networkInterfaceStates list of NetworkInterfaceState objects
     * @return NetworkInterfaceState with the lowes device index (Primary nic)
     */
    private NetworkInterfaceState getPrimaryNic(
            List<NetworkInterfaceState> networkInterfaceStates) {
        Optional<NetworkInterfaceState> optional = networkInterfaceStates.stream()
                .sorted(Comparator.comparingInt(n -> n.deviceIndex))
                .findFirst();
        AssertUtil.assertTrue(optional.isPresent(), "Could not determine primary nic");
        return optional.get();
    }

    /**
     * Get NetworkInterfaceStates from Links
     *
     * @param context Azure load balancer context
     * @return DeferredResult
     */
    private DeferredResult<List<NetworkInterfaceState>> getNetworkInterfaceStatesForLinks(
            AzureLoadBalancerContext context, List<String> networkInterfaceLinks) {
        if (networkInterfaceLinks == null || networkInterfaceLinks.isEmpty()) {
            return DeferredResult.completed(new ArrayList<>());
        }

        List<DeferredResult<NetworkInterfaceState>> networkInterfaceStates =
                networkInterfaceLinks.stream()
                        .map(networkInterfaceLink -> sendWithDeferredResult(Operation
                                        .createGet(context.service.getHost(), networkInterfaceLink),
                                NetworkInterfaceState.class))
                        .collect(Collectors.toList());
        return DeferredResult.allOf(networkInterfaceStates);

    }

    /**
     * Get network interfaces from Azure and store in context
     * These are used to add members to backend pool to load balancer
     *
     * @param context Azure load balancer context
     * @return DeferredResult
     */
    private DeferredResult<AzureLoadBalancerContext> getNetworkInterfaceInners(
            AzureLoadBalancerContext context) {
        if (CollectionUtils.isEmpty(context.networkInterfaceStates)) {
            return DeferredResult.completed(context);
        }

        List<DeferredResult<NetworkInterfaceInner>> networkInterfaceInners =
                context.networkInterfaceStates.stream()
                        .map(networkInterfacesState -> getNetworkInterfaceInner(context,
                                networkInterfacesState))
                        .collect(Collectors.toList());

        return DeferredResult.allOf(networkInterfaceInners)
                .thenApply(networkInterfaceInnerList -> {
                    context.networkInterfaceInners = networkInterfaceInnerList;
                    return context;
                });
    }

    /**
     * Fetch single Network Interface from Azure
     *
     * @param context               Azure load balancer context
     * @param networkInterfaceState state of the network interface to be fetched from Azure
     * @return DeferredResult
     */
    private DeferredResult<NetworkInterfaceInner> getNetworkInterfaceInner(
            AzureLoadBalancerContext context, NetworkInterfaceState networkInterfaceState) {
        String networkInterfaceResGrp = AzureUtils.getResourceGroupName(networkInterfaceState.id);
        final String msg =
                "Getting network Interface [" + networkInterfaceState.name + "] in resource group ["
                        + networkInterfaceResGrp + "].";
        logInfo(() -> msg);

        AzureDeferredResultServiceCallback<NetworkInterfaceInner> callback = new AzureDeferredResultServiceCallback<NetworkInterfaceInner>(
                this, msg) {
            @Override
            protected DeferredResult<NetworkInterfaceInner> consumeSuccess(
                    NetworkInterfaceInner networkInterface) {
                if (networkInterface == null) {
                    logWarning("Failed to get information for network interface: %s",
                            networkInterfaceState.name);
                }
                return DeferredResult.completed(networkInterface);
            }
        };

        NetworkInterfacesInner azureNetworkInterfaceClient = context.azureSdkClients
                .getNetworkManagementClientImpl().networkInterfaces();
        azureNetworkInterfaceClient
                .getByResourceGroupAsync(networkInterfaceResGrp, networkInterfaceState.name, null /* expand */,
                        callback);
        return callback.toDeferredResult();
    }

    /**
     * Populate SecurityGroupStates in the context
     *
     * @param context Azure load balancer context
     * @return DeferredResult
     */
    private DeferredResult<AzureLoadBalancerContext> getSecurityGroupStates(
            AzureLoadBalancerContext context) {
        if (CollectionUtils.isEmpty(context.loadBalancerStateExpanded.securityGroupLinks)) {
            return DeferredResult.completed(context);
        }

        List<DeferredResult<SecurityGroupState>> securityGroupStates =
                context.loadBalancerStateExpanded.securityGroupLinks.stream()
                        .map(securityGroupLink -> sendWithDeferredResult(Operation
                                        .createGet(context.service.getHost(), securityGroupLink),
                                SecurityGroupState.class))
                        .collect(Collectors.toList());

        return DeferredResult.allOf(securityGroupStates)
                .thenApply(securityGroupStateList -> {
                    context.securityGroupStates = securityGroupStateList;
                    return context;
                });
    }

    /**
     * Get security groups from Azure and store in context
     * These are updated to add firewall rules to allow traffic to flow through the load balancer
     *
     * @param context Azure load balancer context
     * @return DeferredResult
     */
    private DeferredResult<AzureLoadBalancerContext> getNetworkSecurityGroupInners(
            AzureLoadBalancerContext context) {
        if (CollectionUtils.isEmpty(context.securityGroupStates)) {
            return DeferredResult.completed(context);
        }

        NetworkSecurityGroupsInner azureSecurityGroupClient = context.azureSdkClients
                .getNetworkManagementClientImpl().networkSecurityGroups();

        List<DeferredResult<NetworkSecurityGroupInner>> networkSecurityGroupInners =
                context.securityGroupStates.stream()
                        .map(securityGroupState -> {
                            String securityGroupName = securityGroupState.name;
                            final String msg = "Getting Azure Security Group [" +
                                    securityGroupName + "].";
                            return AzureSecurityGroupUtils
                                    .getSecurityGroup(this, azureSecurityGroupClient,
                                            AzureUtils.getResourceGroupName(securityGroupState.id),
                                            securityGroupName, msg);
                        })
                        .collect(Collectors.toList());

        return DeferredResult.allOf(networkSecurityGroupInners)
                .thenApply(networkSecurityGroupInnerList -> {
                    context.securityGroupInners = networkSecurityGroupInnerList;
                    return context;
                });
    }

    /**
     * Create a public IP in Azure if it is an internet facing load balancer
     *
     * @param context Azure load balancer context
     * @return DeferredResult
     */
    private DeferredResult<AzureLoadBalancerContext> createPublicIP(
            AzureLoadBalancerContext context) {
        if (!Boolean.TRUE.equals(context.loadBalancerStateExpanded.internetFacing)) {
            return DeferredResult.completed(context);
        }
        PublicIPAddressesInner azurePublicIPAddressClient = context.azureSdkClients
                .getNetworkManagementClientImpl().publicIPAddresses();

        PublicIPAddressInner publicIPAddress = buildPublicIPAddress(context);
        String publicIPName = String
                .format("%s-pip", context.loadBalancerStateExpanded.name);

        final String msg =
                "Creating Public IP Address [" + publicIPName + "] for Azure Load Balancer ["
                        + context.loadBalancerStateExpanded.name + "].";
        logInfo(() -> msg);

        AzureRetryHandler<PublicIPAddressInner> createCallbackHandler = new
                AzureRetryHandler<PublicIPAddressInner>(msg, this) {
            @Override
            protected Consumer<AzureAsyncCallback> getAzureAsyncFunction() {
                return (callback) -> azurePublicIPAddressClient.createOrUpdateAsync(context
                                .resourceGroupName, publicIPName, publicIPAddress, callback);
            }
        };

        AzureRetryHandler<PublicIPAddressInner> getCallbackHandler = new
                AzureRetryHandler<PublicIPAddressInner>(msg, this) {
            @Override
            protected Consumer<AzureAsyncCallback> getAzureAsyncFunction() {
                return (callback) -> azurePublicIPAddressClient.getByResourceGroupAsync(
                        context.resourceGroupName,
                        publicIPName,
                        null /* expand */,
                        callback);
            }

            @Override
            protected boolean isSuccess(PublicIPAddressInner publicIPAddress, Throwable exception) {
                if (!super.isSuccess(publicIPAddress, exception)) {
                    return false;
                }

                if (PROVISIONING_STATE_SUCCEEDED.equalsIgnoreCase(publicIPAddress
                        .provisioningState())) {
                    return true;
                }

                return false;
            }
        };

        return createCallbackHandler.execute()
                .thenCompose(publicIPAddressCreate -> {
                    return getCallbackHandler.execute()
                            .thenApply(publicIPAddressGet -> {
                                context.publicIPAddressInner = publicIPAddressGet;
                                return context;
                            });
                });
    }

    /**
     * Build Azure PublicIPAddress model
     *
     * @param context Azure load balancer context
     * @return PublicIPAddressInner
     */
    private PublicIPAddressInner buildPublicIPAddress(
            AzureLoadBalancerContext context) {
        PublicIPAddressInner publicIPAddress = new PublicIPAddressInner();
        publicIPAddress.withLocation(context.loadBalancerStateExpanded.regionId);
        publicIPAddress.withPublicIPAllocationMethod(IPAllocationMethod.DYNAMIC);
        return publicIPAddress;
    }

    /**
     * Creates a load balancer in Azure
     *
     * @param context Azure load balancer context
     * @return DeferredResult
     */
    private DeferredResult<AzureLoadBalancerContext> createLoadBalancer(
            AzureLoadBalancerContext context) {
        context.loadBalancerAzure = new LoadBalancerInner();
        context.loadBalancerAzure.withLocation(context.loadBalancerStateExpanded.regionId);
        context.loadBalancerAzure
                .withFrontendIPConfigurations(buildFrontendIPConfiguration(context));
        context.loadBalancerAzure.withBackendAddressPools(buildBackendPools(context));

        final String msg = String
                .format("Creating Azure Load Balancer [%s] in resource group [%s].",
                        context.loadBalancerStateExpanded.name, context.resourceGroupName);

        return createOrUpdateLoadBalancer(context, msg)
                .thenApply(lb -> {
                    // Populate the load balancer id with Azure Load Balancer ID
                    context.loadBalancerStateExpanded.id = lb.id();
                    //We have to update the load balancer with health probes and load balancing rules in a separate update call to Azure
                    //So storing the create load balancer in context to use in update call
                    context.loadBalancerAzure = lb;
                    return context;
                });
    }

    /**
     * Build Azure Frontend IP configuration model
     *
     * @param context Azure load balancer context
     * @return List of frontendIPConfiguration objects
     */
    private List<FrontendIPConfigurationInner> buildFrontendIPConfiguration(
            AzureLoadBalancerContext context) {
        List<FrontendIPConfigurationInner> frontendIPConfigurationInners = Lists.newArrayList();
        if (Boolean.TRUE.equals(context.loadBalancerStateExpanded.internetFacing)) {
            FrontendIPConfigurationInner frontendIPConfiguration = new FrontendIPConfigurationInner()
                    .withName(String.format("%s-public-frontend",
                            context.loadBalancerStateExpanded.name));
            frontendIPConfiguration
                    .withPublicIPAddress(new SubResource().withId(context.publicIPAddressInner.id()));
            frontendIPConfigurationInners.add(frontendIPConfiguration);
        } else {
            context.loadBalancerStateExpanded.subnets.forEach(subnet -> {
                FrontendIPConfigurationInner frontendIPConfiguration = new FrontendIPConfigurationInner()
                        .withName(String.format("%s-%s-frontend",
                                context.loadBalancerStateExpanded.name, subnet.name));
                frontendIPConfiguration.withSubnet(new SubResource().withId(subnet.id));
                frontendIPConfiguration.withPrivateIPAllocationMethod(IPAllocationMethod.DYNAMIC);
                frontendIPConfigurationInners.add(frontendIPConfiguration);
            });
        }
        return frontendIPConfigurationInners;
    }

    /**
     * Build Azure backend pool model
     * We create one backend pool and add all VMs to that pool
     *
     * @param context Azure load balancer context
     * @return List of backend pools
     */
    private List<BackendAddressPoolInner> buildBackendPools(AzureLoadBalancerContext context) {
        BackendAddressPoolInner backendAddressPoolInner = new BackendAddressPoolInner();
        backendAddressPoolInner
                .withName(String.format("%s-backend-pool", context.loadBalancerStateExpanded.name));
        return Lists.newArrayList(backendAddressPoolInner);
    }

    /**
     * Create or update load balancer in Azure
     *
     * @param context Azure load balancer context
     * @param msg     message to log
     * @return DeferredResult
     */
    private DeferredResult<LoadBalancerInner> createOrUpdateLoadBalancer(
            AzureLoadBalancerContext context, String msg) {
        LoadBalancersInner azureLoadBalancerClient = context.azureSdkClients
                .getNetworkManagementClientImpl().loadBalancers();
        logInfo(() -> msg);

        AzureProvisioningCallback<LoadBalancerInner> handler = new AzureProvisioningCallback<LoadBalancerInner>(
                this, msg) {
            @Override
            protected DeferredResult<LoadBalancerInner> consumeProvisioningSuccess(
                    LoadBalancerInner loadBalancer) {
                // Populate the load balancer id with Azure Load Balancer ID
                context.loadBalancerStateExpanded.id = loadBalancer.id();
                return DeferredResult.completed(loadBalancer);
            }

            @Override
            protected String getProvisioningState(LoadBalancerInner loadBalancer) {
                return loadBalancer.provisioningState();
            }

            @Override
            protected Runnable checkProvisioningStateCall(
                    ServiceCallback<LoadBalancerInner> checkProvisioningStateCallback) {
                return () -> azureLoadBalancerClient.getByResourceGroupAsync(
                        context.resourceGroupName,
                        context.loadBalancerAzure.name(),
                        null /* expand */,
                        checkProvisioningStateCallback);
            }
        };

        azureLoadBalancerClient.createOrUpdateAsync(context.resourceGroupName,
                context.loadBalancerStateExpanded.name,
                context.loadBalancerAzure, handler);

        return handler.toDeferredResult();
    }

    /**
     * Update load balancer state with the id generated in Azure
     *
     * @param context Azure load balancer context
     * @return DeferredResult
     */
    private DeferredResult<AzureLoadBalancerContext> updateLoadBalancerState(
            AzureLoadBalancerContext context) {
        return this.sendWithDeferredResult(
                Operation.createPatch(this, context.loadBalancerStateExpanded.documentSelfLink)
                        .setBody(context.loadBalancerStateExpanded))
                .thenApply(op -> context);
    }

    /**
     * Update load balancer with health probes
     *
     * @param context Azure load balancer context
     * @return DeferredResult
     */
    private DeferredResult<AzureLoadBalancerContext> addHealthProbes(
            AzureLoadBalancerContext context) {
        List<ProbeInner> probesList = buildLoadBalancingProbes(context);
        context.loadBalancerAzure.withProbes(probesList);

        final String msg = String
                .format("Updating Azure Load Balancer [%s] in resource group [%s] with health probes.",
                        context.loadBalancerStateExpanded.name, context.resourceGroupName);
        return createOrUpdateLoadBalancer(context, msg)
                .thenApply(lb -> {
                    // Populate the load balancer in context with updated Azure Load Balancer
                    context.loadBalancerAzure = lb;
                    return context;
                });
    }

    /**
     * Build Azure health probe model
     *
     * @param context Azure load balancer context
     * @return List of ProbeInner objects
     */
    private List<ProbeInner> buildLoadBalancingProbes(AzureLoadBalancerContext context) {
        List<ProbeInner> loadBalancingProbes = Lists.newArrayList();
        int index = 1;
        for (RouteConfiguration routes : context.loadBalancerStateExpanded.routes) {
            HealthCheckConfiguration healthCheckConfiguration = routes.healthCheckConfiguration;
            if (healthCheckConfiguration != null) {
                ProbeInner probeInner = new ProbeInner();
                String healthProbeName = String
                        .format("%s-probe-%s", context.loadBalancerStateExpanded.name, index);
                probeInner.withName(healthProbeName);
                probeInner.withIntervalInSeconds(healthCheckConfiguration.intervalSeconds);
                probeInner.withPort(Integer.parseInt(healthCheckConfiguration.port));

                boolean isHttpProtocol = StringUtils.equalsIgnoreCase(ProbeProtocol.HTTP.toString(),
                        healthCheckConfiguration.protocol);
                boolean isTcpProtocol = StringUtils.equalsIgnoreCase(ProbeProtocol.TCP.toString(),
                        healthCheckConfiguration.protocol);
                AssertUtil.assertTrue(isHttpProtocol || isTcpProtocol,
                        String.format("Unsupported protocol %s. Only HTTP and TCP are supported.",
                                healthCheckConfiguration.protocol));
                probeInner.withProtocol(new ProbeProtocol(healthCheckConfiguration.protocol));
                if (isHttpProtocol) {
                    probeInner.withRequestPath(healthCheckConfiguration.urlPath);
                }

                probeInner.withNumberOfProbes(healthCheckConfiguration.unhealthyThreshold);
                loadBalancingProbes.add(probeInner);
                index++;
            }
        }
        return loadBalancingProbes;
    }

    /**
     * Update load balancer with load balancing rules
     *
     * @param context Azure load balancer context
     * @return DeferredResult
     */
    private DeferredResult<AzureLoadBalancerContext> addLoadBalancingRules(
            AzureLoadBalancerContext context) {
        List<LoadBalancingRuleInner> loadBalancingRulesList = buildLoadBalancingRules(context);
        context.loadBalancerAzure.withLoadBalancingRules(loadBalancingRulesList);

        final String msg = String
                .format("Updating Azure Load Balancer [%s] in resource group [%s] with load balancing rules.",
                        context.loadBalancerStateExpanded.name, context.resourceGroupName);
        return createOrUpdateLoadBalancer(context, msg)
                .thenApply(lb -> {
                    // Populate the load balancer in context with updated Azure Load Balancer
                    context.loadBalancerAzure = lb;
                    return context;
                });
    }

    /**
     * Build Azure load balancing rule model
     *
     * @param context Azure load balancer context
     * @return List of LoadBalancingRuleInner objects
     */
    private List<LoadBalancingRuleInner> buildLoadBalancingRules(AzureLoadBalancerContext context) {
        List<LoadBalancingRuleInner> loadBalancingRules = Lists.newArrayList();
        int index = 1;
        for (RouteConfiguration routes : context.loadBalancerStateExpanded.routes) {
            ProbeInner probeInner = findMatchingProbe(context, index);

            LoadBalancingRuleInner loadBalancingRule = new LoadBalancingRuleInner();
            loadBalancingRule.withName(
                    String.format("%s-lb-rule-%s", context.loadBalancerStateExpanded.name,
                            index++));
            loadBalancingRule.withBackendPort(Integer.valueOf(routes.instancePort));
            loadBalancingRule.withFrontendPort(Integer.valueOf(routes.port));
            loadBalancingRule.withBackendAddressPool(new SubResource()
                    .withId(context.loadBalancerAzure.backendAddressPools().get(0).id()));

            //Converting HTTP and HTTPS to TCP to send to Azure as Azure only supports TCP or UCP
            if (StringUtils.equalsIgnoreCase("HTTP", routes.protocol) ||
                    StringUtils.equalsIgnoreCase("HTTPS", routes.protocol)) {
                routes.protocol = TransportProtocol.TCP.toString();
            }

            boolean isTcpProtocol = StringUtils
                    .equalsIgnoreCase(TransportProtocol.TCP.toString(), routes.protocol);
            boolean isUdpProtocol = StringUtils
                    .equalsIgnoreCase(TransportProtocol.UDP.toString(), routes.protocol);
            AssertUtil.assertTrue(isTcpProtocol || isUdpProtocol,
                    String.format("Unsupported protocol %s. Only UDP and TCP are supported.",
                            routes.protocol));
            loadBalancingRule.withProtocol(new TransportProtocol(routes.protocol));

            //TODO support more than one frontend case
            loadBalancingRule.withFrontendIPConfiguration(new SubResource()
                    .withId(context.loadBalancerAzure.frontendIPConfigurations().get(0).id()));
            if (probeInner != null) {
                loadBalancingRule.withProbe(new SubResource().withId(probeInner.id()));
            }
            loadBalancingRules.add(loadBalancingRule);
        }
        return loadBalancingRules;
    }

    /**
     * Find the corresponding azure health probe as defined in load balancer health check configuration
     *
     * @param context Azure load balancer context
     * @return Matching Azure health probe
     */
    private ProbeInner findMatchingProbe(AzureLoadBalancerContext context, int index) {
        String healthProbeName = String
                .format("%s-probe-%s", context.loadBalancerStateExpanded.name, index);
        Optional<ProbeInner> matchingProbe = context.loadBalancerAzure.probes().stream()
                .filter(probe -> StringUtils.equals(probe.name(), healthProbeName))
                .findFirst();
        return matchingProbe.orElse(null);
    }

    /**
     * Update load balancer backend pool with VMs to load balance
     *
     * @param context Azure load balancer context
     * @return DeferredResult
     */
    private DeferredResult<AzureLoadBalancerContext> addBackendPoolMembers(
            AzureLoadBalancerContext context) {
        final String msg = "Adding backendpool members to [" + context.loadBalancerAzure.name()
                + "] in resource " + "group [" + context.resourceGroupName + "].";
        logInfo(() -> msg);

        if (CollectionUtils.isEmpty(context.networkInterfaceInners)) {
            return DeferredResult.completed(context);
        }

        List<DeferredResult<NetworkInterfaceInner>> networkInterfaceInnerList = context.networkInterfaceInners
                .stream().map(networkInterfaceInner -> updateNetworkInterface(context,
                        networkInterfaceInner)).collect(Collectors.toList());

        return DeferredResult.allOf(networkInterfaceInnerList)
                .thenApply(networkInterfaceInner -> {
                    context.networkInterfaceInners = networkInterfaceInner;
                    return context;
                });
    }

    /**
     * Update isolation security group with rule to allow traffic on load balancing ports for VMs
     * being load balanced
     *
     * @param context Azure load balancer context
     * @return DeferredResult
     */
    private DeferredResult<AzureLoadBalancerContext> updateSecurityGroupRules(
            AzureLoadBalancerContext context) {
        if (CollectionUtils.isEmpty(context.securityGroupInners)) {
            return DeferredResult.completed(context);
        }
        //Add security group firewall rules to allow traffic to flow through load balancer routes
        updateSecurityRules(context);

        NetworkSecurityGroupsInner azureSecurityGroupClient = context.azureSdkClients
                .getNetworkManagementClientImpl().networkSecurityGroups();

        List<DeferredResult<NetworkSecurityGroupInner>> networkSecurityGroupInnerList = context
                .securityGroupInners
                .stream().map(networkSecurityGroupInner -> {
                    final String msg =
                            "Updating security group rules for [" + networkSecurityGroupInner.name()
                                    + "] for load balancer ["
                                    + context.loadBalancerStateExpanded.name + "].";
                    logInfo(() -> msg);
                    return AzureSecurityGroupUtils
                            .createOrUpdateSecurityGroup(this, azureSecurityGroupClient,
                                    AzureUtils.getResourceGroupName(networkSecurityGroupInner.id()),
                                    networkSecurityGroupInner.name(), networkSecurityGroupInner,
                                    msg);
                })
                .collect(Collectors.toList());

        return DeferredResult.allOf(networkSecurityGroupInnerList).thenApply(ignored -> context);
    }

    /**
     * Fetch public IP address after an address has been assigned
     */
    private DeferredResult<AzureLoadBalancerContext> getPublicIPAddress(AzureLoadBalancerContext ctx) {
        if (ctx.publicIPAddressInner == null) {
            // No public IP address created -> do nothing.
            return DeferredResult.completed(ctx);
        }

        NetworkManagementClientImpl client = ctx.azureSdkClients.getNetworkManagementClientImpl();

        String msg = "Get public IP address for resource group [" + ctx.resourceGroupName
                + "] and name [" + ctx.publicIPAddressInner.name() + "].";

        AzureDeferredResultServiceCallback<PublicIPAddressInner> callback = new AzureDeferredResultServiceCallback<PublicIPAddressInner>(
                ctx.service, msg) {
            @Override
            protected DeferredResult<PublicIPAddressInner> consumeSuccess(
                    PublicIPAddressInner result) {
                ctx.publicIPAddressInner = result;
                ctx.loadBalancerStateExpanded.address = result.ipAddress();
                return DeferredResult.completed(result);
            }
        };

        client.publicIPAddresses().getByResourceGroupAsync(
                ctx.resourceGroupName,
                ctx.publicIPAddressInner.name(),
                null, callback);

        return callback.toDeferredResult().thenApply(ignored -> ctx);
    }

    /**
     * Build a list of Security group firewall rules to allow traffic through load balancer routes
     *
     * @param context Azure load balancer context
     */
    private void updateSecurityRules(AzureLoadBalancerContext context) {
        List<SecurityRuleInner> securityRuleInnerList = Lists.newArrayList();

        final AtomicInteger priority = new AtomicInteger(2000);
        context.loadBalancerAzure.loadBalancingRules().forEach(loadBalancingRuleInner -> {
            SecurityRuleInner securityRuleInner = new SecurityRuleInner();
            securityRuleInner.withName(String.format("%s-sg-rule", loadBalancingRuleInner.name()));
            securityRuleInner.withDirection(SecurityRuleDirection.INBOUND);
            securityRuleInner.withAccess(SecurityRuleAccess.ALLOW);
            securityRuleInner.withPriority(priority.getAndIncrement());
            securityRuleInner.withProtocol(new SecurityRuleProtocol(loadBalancingRuleInner
                    .protocol().toString()));
            securityRuleInner.withSourcePortRange(SecurityGroupService.ANY);
            securityRuleInner.withSourceAddressPrefix(SecurityGroupService.ANY);
            securityRuleInner.withDestinationPortRange(Integer.toString(loadBalancingRuleInner
                    .backendPort()));
            // Azure API expects destination address prefix to be set even if we are using
            // destination address prefixes
            securityRuleInner.withDestinationAddressPrefix(getDestinationAddressPrefix(context));
            //TODO this should be fixed once Azure API version is updates
            // securityRuleInner.withDestinationAddressPrefixes(getDestinationAddressPrefixes
            // (context));

            securityRuleInnerList.add(securityRuleInner);
        });

        //update rules
        context.securityGroupInners.forEach(securityGroupInner -> {
            if (securityGroupInner != null) {
                securityGroupInner.securityRules().addAll(securityRuleInnerList);
                securityGroupInner.withSecurityRules(securityGroupInner.securityRules());
            }
        });
    }

    /**
     * This is a workaround until Azure API version is upgraded
     * Collect the list of IPs for the VMs being load balanced
     *
     * @param context Azure load balancer context
     * @return comma separated list of all IPs being load balanced
     */
    private String getDestinationAddressPrefix(AzureLoadBalancerContext context) {
        if (context.networkInterfaceInners != null && !context.networkInterfaceInners.isEmpty()) {
            List<NetworkInterfaceIPConfigurationInner> ipConfigurations = context
                    .networkInterfaceInners.iterator().next().ipConfigurations();
            if (ipConfigurations != null && !ipConfigurations.isEmpty()) {
                return ipConfigurations.iterator().next().privateIPAddress();
            }
        }
        return "";
    }

    /**
     * Collect the list of IPs for the VMs being load balanced
     *
     * @param context Azure load balancer context
     * @return comma separated list of all IPs being load balanced
     */
    private List<String> getDestinationAddressPrefixes(AzureLoadBalancerContext context) {
        List<NetworkInterfaceIPConfigurationInner> ipConfigs = Lists.newArrayList();
        if (context.networkInterfaceInners != null) {
            context.networkInterfaceInners.forEach(
                    networkInterfaceInner -> ipConfigs.addAll(networkInterfaceInner
                            .ipConfigurations()));
        }
        return ipConfigs.stream().map(NetworkInterfaceIPConfigurationInner::privateIPAddress)
                .collect(Collectors.toList());
    }

    /**
     * Update Azure network interface construct to associate it with load balancer backend pool
     *
     * @param context               Azure load balancer context
     * @param networkInterfaceInner Azure network interface construct
     * @return DeferredResult
     */
    private DeferredResult<NetworkInterfaceInner> updateNetworkInterface(
            AzureLoadBalancerContext context, NetworkInterfaceInner networkInterfaceInner) {
        //Set the backend pool information for each nic
        networkInterfaceInner.ipConfigurations().forEach(
                networkInterfaceIPConfigurationInner -> networkInterfaceIPConfigurationInner
                        .withLoadBalancerBackendAddressPools(
                                context.loadBalancerAzure.backendAddressPools()));
        return createOrUpdateNetworkInterface(context, networkInterfaceInner);
    }

    /**
     * Create or Update network interface in Azure
     *
     * @param context               Azure load balancer context
     * @param networkInterfaceInner Azure network interface construct
     * @return DeferredResult
     */
    private DeferredResult<NetworkInterfaceInner> createOrUpdateNetworkInterface(
            AzureLoadBalancerContext context, NetworkInterfaceInner networkInterfaceInner) {
        String networkInterfaceResGrp = AzureUtils.getResourceGroupName(networkInterfaceInner.id());
        final String msg =
                "Update network Interface [" + networkInterfaceInner.name()
                        + "] in resource group ["
                        + networkInterfaceResGrp + "].";
        logInfo(() -> msg);

        NetworkInterfacesInner azureNetworkInterfaceClient = context.azureSdkClients
                .getNetworkManagementClientImpl().networkInterfaces();

        AzureRetryHandler<NetworkInterfaceInner> createCallbackHandler = new
                AzureRetryHandler<NetworkInterfaceInner>(msg, this) {
            @Override
            protected Consumer<AzureAsyncCallback> getAzureAsyncFunction() {
                return (callback) -> azureNetworkInterfaceClient
                        .createOrUpdateAsync(networkInterfaceResGrp, networkInterfaceInner.name(),
                                networkInterfaceInner, callback);
            }
        };

        AzureRetryHandler<NetworkInterfaceInner> getCallbackHandler = new
                AzureRetryHandler<NetworkInterfaceInner>(msg, this) {
            @Override
            protected Consumer<AzureAsyncCallback> getAzureAsyncFunction() {
                return (callback) -> azureNetworkInterfaceClient.getByResourceGroupAsync(
                        networkInterfaceResGrp,
                        networkInterfaceInner.name(),
                        null /* expand */,
                        callback);
            }

            @Override
            protected boolean isSuccess(NetworkInterfaceInner networkInterfaceInner, Throwable exception) {
                if (!super.isSuccess(networkInterfaceInner, exception)) {
                    return false;
                }

                if (PROVISIONING_STATE_SUCCEEDED.equalsIgnoreCase(networkInterfaceInner
                        .provisioningState())) {
                    return true;
                }

                return false;
            }
        };

        return createCallbackHandler.execute()
                .thenCompose(networkInterfaceInnerCreate -> {
                    return getCallbackHandler.execute();
                });
    }

    /**
     * Delete load balancer from Azure
     *
     * @param context Azure load balancer context
     * @return DeferredResult
     */
    private DeferredResult<AzureLoadBalancerContext> deleteLoadBalancer(
            AzureLoadBalancerContext context) {

        String rgName = context.resourceGroupName;
        String loadBalancerName = context.loadBalancerStateExpanded.name;

        LoadBalancersInner azureLoadBalancerClient = context.azureSdkClients
                .getNetworkManagementClientImpl().loadBalancers();

        final String msg = String
                .format("Deleting Azure load balancer [%s] in resource group [%s].",
                        loadBalancerName, rgName);

        AzureRetryHandler<Void> handler = new AzureRetryHandler<Void>(msg, this) {
            @Override
            protected Consumer<AzureAsyncCallback> getAzureAsyncFunction() {
                return (callback) -> azureLoadBalancerClient
                        .deleteAsync(rgName, loadBalancerName, callback);
            }
        };
        return handler.execute().thenApply(ignore -> context);
    }

    /**
     * Delete Public IP address from Azure
     *
     * @param context Azure load balancer context
     * @return DeferredResult
     */
    private DeferredResult<AzureLoadBalancerContext> deletePublicIP(
            AzureLoadBalancerContext context) {
        String rgName = context.resourceGroupName;
        String publicIPName = String
                .format("%s-pip", context.loadBalancerStateExpanded.name);

        PublicIPAddressesInner azurePublicIPAddressClient = context.azureSdkClients
                .getNetworkManagementClientImpl().publicIPAddresses();

        final String msg = String
                .format("Deleting Azure Public IP [%s] in resource group [%s].", publicIPName,
                        rgName);

        AzureDeferredResultServiceCallback<Void> handler = createDeleteHandler(msg);
        azurePublicIPAddressClient.deleteAsync(rgName, publicIPName, handler);
        return handler.toDeferredResult().thenApply(ignore -> context);
    }

    /**
     * Create handler for deletion of resource from Azure
     *
     * @param msg Azure load balancer context
     * @return DeferredResult
     */
    private AzureDeferredResultServiceCallback<Void> createDeleteHandler(String msg) {

        return new Default<>(this, msg);
    }

    /**
     * Delete load balancer state
     *
     * @param context Azure load balancer context
     * @return DeferredResult
     */
    private DeferredResult<AzureLoadBalancerContext> deleteLoadBalancerState(
            AzureLoadBalancerContext context) {
        return this.sendWithDeferredResult(
                Operation.createDelete(this, context.loadBalancerStateExpanded.documentSelfLink))
                .thenApply(operation -> context);
    }
}