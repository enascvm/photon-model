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

import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.INVALID_RESOURCE_GROUP;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.RESOURCE_GROUP_NOT_FOUND;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.microsoft.azure.CloudError;
import com.microsoft.azure.CloudException;
import com.microsoft.azure.SubResource;
import com.microsoft.azure.management.network.IPAllocationMethod;
import com.microsoft.azure.management.network.ProbeProtocol;
import com.microsoft.azure.management.network.TransportProtocol;
import com.microsoft.azure.management.network.implementation.BackendAddressPoolInner;
import com.microsoft.azure.management.network.implementation.FrontendIPConfigurationInner;
import com.microsoft.azure.management.network.implementation.LoadBalancerInner;
import com.microsoft.azure.management.network.implementation.LoadBalancersInner;
import com.microsoft.azure.management.network.implementation.LoadBalancingRuleInner;
import com.microsoft.azure.management.network.implementation.NetworkInterfaceInner;
import com.microsoft.azure.management.network.implementation.NetworkInterfacesInner;
import com.microsoft.azure.management.network.implementation.ProbeInner;
import com.microsoft.azure.management.network.implementation.PublicIPAddressInner;
import com.microsoft.azure.management.network.implementation.PublicIPAddressesInner;
import com.microsoft.rest.ServiceCallback;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.vmware.photon.controller.model.adapterapi.LoadBalancerInstanceRequest;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureProvisioningCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureSdkClients;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription.HealthCheckConfiguration;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription.RouteConfiguration;
import com.vmware.photon.controller.model.resources.LoadBalancerService.LoadBalancerStateExpanded;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Adapter to create/delete a load balancer on Azure.
 */
public class AzureLoadBalancerService extends StatelessService {

    public static final String SELF_LINK = AzureUriPaths.AZURE_LOAD_BALANCER_ADAPTER;

    /**
     * Load balancer request context.
     */
    private static class AzureLoadBalancerContext extends
            BaseAdapterContext<AzureLoadBalancerContext> implements AutoCloseable {

        final LoadBalancerInstanceRequest loadBalancerRequest;

        AzureSdkClients azureSdkClients;

        LoadBalancerStateExpanded loadBalancerStateExpanded;
        String resourceGroupName;
        List<NetworkInterfaceState> networkInterfaceStates;
        List<NetworkInterfaceInner> networkInterfaceInners;
        String publicIPAddressInnerId;
        LoadBalancerInner loadBalancerAzure;

        AzureLoadBalancerContext(StatelessService service, LoadBalancerInstanceRequest request) {
            super(service, request);
            this.loadBalancerRequest = request;
        }

        @Override
        public void close() {
            if (this.azureSdkClients != null) {
                this.azureSdkClients.close();
                this.azureSdkClients = null;
            }
        }
    }

    private ExecutorService executorService;

    @Override
    public void handleStart(Operation op) {
        this.executorService = getHost().allocateExecutor(this);
        super.handleStart(op);
    }

    @Override
    public void handleStop(Operation op) {
        this.executorService.shutdown();
        AdapterUtils.awaitTermination(this.executorService);

        super.handleStop(op);
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
                .whenComplete((o, e) -> {
                    // Once done patch the calling task with correct stage.
                    if (e == null) {
                        context.taskManager.finishTask();
                    } else {
                        context.taskManager.patchTaskToFailure(e);
                    }
                    context.close();
                });
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
                .thenCompose(this::getCredentials)
                .thenCompose(this::getAuthentication)
                .thenCompose(this::getAzureClients);
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
                    //We create load balancer in the same resource group as the compute that is being load balanced
                    if (CollectionUtils.isNotEmpty(state.computes)) {
                        context.resourceGroupName = AzureUtils
                                .getResourceGroupName(state.computes.iterator().next().id);
                    }
                    return context;
                });
    }

    /**
     * Populate context with endpoint credential info
     *
     * @param context Azure load balancer context
     * @return DeferredResult
     */
    private DeferredResult<AzureLoadBalancerContext> getCredentials(
            AzureLoadBalancerContext context) {
        return context.populateBaseContext(BaseAdapterContext.BaseAdapterStage.PARENTAUTH);
    }

    /**
     * Populate context with endpoint authentication info
     *
     * @param context Azure load balancer context
     * @return DeferredResult
     */
    private DeferredResult<AzureLoadBalancerContext> getAuthentication(
            AzureLoadBalancerContext context) {
        return this
                .sendWithDeferredResult(
                        Operation.createGet(
                                context.service.getHost(),
                                context.loadBalancerStateExpanded.endpointState.authCredentialsLink),
                        AuthCredentialsServiceState.class)
                .thenApply(state -> {
                    context.parentAuth = state;
                    return context;
                });
    }

    /**
     * Initialize Azure SDK client in context
     *
     * @param context Azure load balancer context
     * @return DeferredResult
     */
    private DeferredResult<AzureLoadBalancerContext> getAzureClients(
            AzureLoadBalancerContext context) {
        if (context.loadBalancerRequest.isMockRequest) {
            return DeferredResult.completed(context);
        }
        if (context.azureSdkClients == null) {
            context.azureSdkClients = new AzureSdkClients(this.executorService, context.parentAuth);
        }
        return DeferredResult.completed(context);
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
                        .thenCompose(this::getNetworkInterfaceStates)
                        .thenCompose(this::getNetworkInterfaceInners)
                        .thenCompose(this::createPublicIP)
                        .thenCompose(this::createLoadBalancer)
                        .thenCompose(this::updateLoadBalancerState)
                        .thenCompose(this::addHealthProbes)
                        .thenCompose(this::addLoadBalancingRules)
                        .thenCompose(this::addBackendPoolMembers);
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
     * Populate NetworkInterface States in the context
     *
     * @param context Azure load balancer context
     * @return DeferredResult
     */
    private DeferredResult<AzureLoadBalancerContext> getNetworkInterfaceStates(
            AzureLoadBalancerContext context) {
        List<String> networkInterfaceLinks = Lists.newArrayList();
        context.loadBalancerStateExpanded.computes
                .forEach(compute -> networkInterfaceLinks.addAll(compute.networkInterfaceLinks));

        if (networkInterfaceLinks.isEmpty()) {
            return DeferredResult.completed(context);
        }

        List<DeferredResult<NetworkInterfaceState>> networkInterfaceStates =
                networkInterfaceLinks.stream()
                        .map(networkInterfaceLink -> sendWithDeferredResult(Operation
                                        .createGet(context.service.getHost(), networkInterfaceLink),
                                NetworkInterfaceState.class))
                        .collect(Collectors.toList());

        return DeferredResult.allOf(networkInterfaceStates)
                .thenApply(networkInterfaceStateList -> {
                    context.networkInterfaceStates = networkInterfaceStateList;
                    return context;
                });
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
                        .map(networkInterfacesState -> {
                            String networkInterfaceName = networkInterfacesState.name;
                            return getNetworkInterfaceInner(context, networkInterfaceName);
                        })
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
     * @param context              Azure load balancer context
     * @param networkInterfaceName name of the network interface to be fetched from Azure
     * @return DeferredResult
     */
    private DeferredResult<NetworkInterfaceInner> getNetworkInterfaceInner(
            AzureLoadBalancerContext context, String networkInterfaceName) {
        final String msg =
                "Getting network Interface [" + networkInterfaceName + "] in resource group ["
                        + context.resourceGroupName + "].";
        logInfo(() -> msg);

        AzureDeferredResultServiceCallback<NetworkInterfaceInner> callback = new AzureDeferredResultServiceCallback<NetworkInterfaceInner>(
                this, msg) {
            @Override
            protected DeferredResult<NetworkInterfaceInner> consumeSuccess(
                    NetworkInterfaceInner networkInterface) {
                if (networkInterface == null) {
                    logWarning("Failed to get information for network interface: %s",
                            networkInterfaceName);
                }
                return DeferredResult.completed(networkInterface);
            }
        };

        NetworkInterfacesInner azureNetworkInterfaceClient = context.azureSdkClients
                .getNetworkManagementClientImpl().networkInterfaces();
        azureNetworkInterfaceClient
                .getByResourceGroupAsync(context.resourceGroupName, networkInterfaceName, null /* expand */,
                        callback);
        return callback.toDeferredResult();
    }

    /**
     * Create a public IP in Azure if it is an internet facing load balancer
     *
     * @param context Azure load balancer context
     * @return DeferredResult
     */
    private DeferredResult<AzureLoadBalancerContext> createPublicIP(
            AzureLoadBalancerContext context) {
        if (!context.loadBalancerStateExpanded.internetFacing) {
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

        AzureProvisioningCallback<PublicIPAddressInner> handler = new AzureProvisioningCallback<PublicIPAddressInner>(
                this, msg) {
            @Override
            protected DeferredResult<PublicIPAddressInner> consumeProvisioningSuccess(
                    PublicIPAddressInner publicIPAddress) {
                // Populate the Public IP id with Azure Public IP ID
                context.publicIPAddressInnerId = publicIPAddress.id();
                return DeferredResult.completed(publicIPAddress);
            }

            @Override
            protected String getProvisioningState(PublicIPAddressInner loadBalancer) {
                return loadBalancer.provisioningState();
            }

            @Override
            protected Runnable checkProvisioningStateCall(
                    ServiceCallback<PublicIPAddressInner> checkProvisioningStateCallback) {
                return () -> azurePublicIPAddressClient.getByResourceGroupAsync(
                        context.resourceGroupName,
                        publicIPName,
                        null /* expand */,
                        checkProvisioningStateCallback);
            }
        };

        azurePublicIPAddressClient
                .createOrUpdateAsync(context.resourceGroupName, publicIPName, publicIPAddress,
                        handler);
        return handler.toDeferredResult()
                .thenApply(ignore -> context);
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
        if (context.loadBalancerStateExpanded.internetFacing) {
            FrontendIPConfigurationInner frontendIPConfiguration = new FrontendIPConfigurationInner()
                    .withName(String.format("%s-public-frontend",
                            context.loadBalancerStateExpanded.name));
            frontendIPConfiguration
                    .withPublicIPAddress(new SubResource().withId(context.publicIPAddressInnerId));
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
        final String msg =
                "Update network Interface [" + networkInterfaceInner.name()
                        + "] in resource group ["
                        + context.resourceGroupName + "].";
        logInfo(() -> msg);

        NetworkInterfacesInner azureNetworkInterfaceClient = context.azureSdkClients
                .getNetworkManagementClientImpl().networkInterfaces();

        AzureProvisioningCallback<NetworkInterfaceInner> handler =
                new AzureProvisioningCallback<NetworkInterfaceInner>(this, msg) {
                    @Override
                    protected DeferredResult<NetworkInterfaceInner> consumeProvisioningSuccess(
                            NetworkInterfaceInner networkInterfaceInner) {
                        return DeferredResult.completed(networkInterfaceInner);
                    }

                    @Override
                    protected Runnable checkProvisioningStateCall(
                            ServiceCallback<NetworkInterfaceInner> checkProvisioningStateCallback) {
                        return () -> azureNetworkInterfaceClient.getByResourceGroupAsync(
                                context.resourceGroupName,
                                networkInterfaceInner.name(),
                                null /* expand */,
                                checkProvisioningStateCallback);
                    }

                    @Override
                    protected String getProvisioningState(NetworkInterfaceInner body) {
                        return body.provisioningState();
                    }
                };

        azureNetworkInterfaceClient
                .createOrUpdateAsync(context.resourceGroupName, networkInterfaceInner.name(),
                        networkInterfaceInner, handler);
        return handler.toDeferredResult();
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

        AzureDeferredResultServiceCallback<Void> handler = createDeleteHandler(msg);
        azureLoadBalancerClient.deleteAsync(rgName, loadBalancerName, handler);
        return handler.toDeferredResult().thenApply(ignore -> context);
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
        return new AzureDeferredResultServiceCallback<Void>(this, msg) {
            @Override
            protected DeferredResult<Void> consumeSuccess(Void result) {
                return DeferredResult.completed(null);
            }

            @Override
            protected Throwable consumeError(Throwable exc) {
                exc = super.consumeError(exc);
                if (exc instanceof CloudException) {
                    CloudException azureExc = (CloudException) exc;
                    CloudError body = azureExc.body();
                    String code = body.code();
                    if (RESOURCE_GROUP_NOT_FOUND.equals(code)) {
                        return RECOVERED;
                    } else if (INVALID_RESOURCE_GROUP.equals(code)) {
                        String invalidParameterMsg = String.format(
                                "Invalid resource group parameter. %s",
                                body.message());
                        return new IllegalStateException(invalidParameterMsg, exc);
                    }
                }
                return exc;
            }
        };
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