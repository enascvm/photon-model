/*
 * Copyright (c) 2015-2016 VMware, Inc. All Rights Reserved.
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

import static com.vmware.photon.controller.model.ComputeProperties.RESOURCE_GROUP_NAME;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_OSDISK_CACHING;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_SECURITY_GROUP_DIRECTION_INBOUND;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_SECURITY_GROUP_DIRECTION_OUTBOUND;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_KEY1;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_KEY2;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_NAME;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_TYPE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.COMPUTE_NAMESPACE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.MISSING_SUBSCRIPTION_CODE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.NETWORK_NAMESPACE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.PROVIDER_REGISTRED_STATE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.PROVISIONING_STATE_SUCCEEDED;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.STORAGE_NAMESPACE;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.awaitTermination;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.cleanUpHttpClient;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.getAzureConfig;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.CLOUD_CONFIG_DEFAULT_FILE_INDEX;
import static com.vmware.xenon.common.Operation.STATUS_CODE_UNAUTHORIZED;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.microsoft.azure.CloudError;
import com.microsoft.azure.CloudException;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.compute.ComputeManagementClient;
import com.microsoft.azure.management.compute.ComputeManagementClientImpl;
import com.microsoft.azure.management.compute.models.HardwareProfile;
import com.microsoft.azure.management.compute.models.ImageReference;
import com.microsoft.azure.management.compute.models.NetworkInterfaceReference;
import com.microsoft.azure.management.compute.models.NetworkProfile;
import com.microsoft.azure.management.compute.models.OSDisk;
import com.microsoft.azure.management.compute.models.OSProfile;
import com.microsoft.azure.management.compute.models.StorageProfile;
import com.microsoft.azure.management.compute.models.VirtualHardDisk;
import com.microsoft.azure.management.compute.models.VirtualMachine;
import com.microsoft.azure.management.compute.models.VirtualMachineImage;
import com.microsoft.azure.management.compute.models.VirtualMachineImageResource;
import com.microsoft.azure.management.network.NetworkInterfacesOperations;
import com.microsoft.azure.management.network.NetworkManagementClient;
import com.microsoft.azure.management.network.NetworkManagementClientImpl;
import com.microsoft.azure.management.network.NetworkSecurityGroupsOperations;
import com.microsoft.azure.management.network.PublicIPAddressesOperations;
import com.microsoft.azure.management.network.VirtualNetworksOperations;
import com.microsoft.azure.management.network.models.AddressSpace;
import com.microsoft.azure.management.network.models.NetworkInterface;
import com.microsoft.azure.management.network.models.NetworkInterfaceIPConfiguration;
import com.microsoft.azure.management.network.models.NetworkSecurityGroup;
import com.microsoft.azure.management.network.models.PublicIPAddress;
import com.microsoft.azure.management.network.models.SecurityRule;
import com.microsoft.azure.management.network.models.Subnet;
import com.microsoft.azure.management.network.models.VirtualNetwork;
import com.microsoft.azure.management.resources.ResourceManagementClient;
import com.microsoft.azure.management.resources.ResourceManagementClientImpl;
import com.microsoft.azure.management.resources.SubscriptionClient;
import com.microsoft.azure.management.resources.SubscriptionClientImpl;
import com.microsoft.azure.management.resources.models.Provider;
import com.microsoft.azure.management.resources.models.ResourceGroup;
import com.microsoft.azure.management.resources.models.Subscription;
import com.microsoft.azure.management.storage.StorageAccountsOperations;
import com.microsoft.azure.management.storage.StorageManagementClient;
import com.microsoft.azure.management.storage.StorageManagementClientImpl;
import com.microsoft.azure.management.storage.models.AccountType;
import com.microsoft.azure.management.storage.models.ProvisioningState;
import com.microsoft.azure.management.storage.models.StorageAccount;
import com.microsoft.azure.management.storage.models.StorageAccountCreateParameters;
import com.microsoft.azure.management.storage.models.StorageAccountKeys;
import com.microsoft.rest.ServiceCallback;
import com.microsoft.rest.ServiceResponse;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.adapters.azure.AzureAsyncCallback;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureInstanceContext.AzureNicContext;
import com.vmware.photon.controller.model.adapters.azure.model.diagnostics.AzureDiagnosticSettings;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureProvisioningDeferredResultCallback;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.BaseAdapterStage;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.LifecycleState;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.FileUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Adapter to create/delete a VM instance on Azure.
 */
public class AzureInstanceService extends StatelessService {

    public static final String SELF_LINK = AzureUriPaths.AZURE_INSTANCE_ADAPTER;

    // TODO VSYM-322: Remove unused default properties from AzureInstanceService
    // Name prefixes
    private static final String NICCONFIG_NAME_PREFIX = "nicconfig";

    private static final String PRIVATE_IP_ALLOCATION_METHOD = "Dynamic";

    private static final String DEFAULT_GROUP_PREFIX = "group";

    private static final String DEFAULT_VM_SIZE = "Basic_A0";
    private static final String OS_DISK_CREATION_OPTION = "fromImage";

    private static final AccountType DEFAULT_STORAGE_ACCOUNT_TYPE = AccountType.STANDARD_LRS;
    private static final String VHD_URI_FORMAT = "https://%s.blob.core.windows.net/vhds/%s.vhd";
    private static final String BOOT_DISK_SUFFIX = "-boot-disk";

    private static final long DEFAULT_EXPIRATION_INTERVAL_MICROS = TimeUnit.MINUTES.toMicros(5);
    private static final int RETRY_INTERVAL_SECONDS = 30;

    private ExecutorService executorService;

    @Override
    public void handleStart(Operation startPost) {
        this.executorService = getHost().allocateExecutor(this);

        super.handleStart(startPost);
    }

    @Override
    public void handleStop(Operation delete) {
        this.executorService.shutdown();
        awaitTermination(this, this.executorService);
        super.handleStop(delete);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        AzureInstanceContext ctx = new AzureInstanceContext(this,
                op.getBody(ComputeInstanceRequest.class));

        final BaseAdapterStage startingStage;

        switch (ctx.computeRequest.requestType) {
        case VALIDATE_CREDENTIALS:
            ctx.operation = op;
            startingStage = BaseAdapterStage.PARENTAUTH;
            break;
        default:
            op.complete();
            if (ctx.computeRequest.isMockRequest
                    && ctx.computeRequest.requestType == InstanceRequestType.CREATE) {
                handleAllocation(ctx, AzureInstanceStage.FINISHED);
                return;
            }
            startingStage = BaseAdapterStage.VMDESC;
            break;
        }

        // Populate BaseAdapterContext and then continue with this state machine
        ctx.populateBaseContext(startingStage)
                .whenComplete(thenAllocation(ctx, AzureInstanceStage.CLIENT));
    }

    /**
     * Shortcut method that sets the next stage into the context and delegates to
     * {@link #handleAllocation(AzureInstanceContext)}.
     */
    private void handleAllocation(AzureInstanceContext ctx, AzureInstanceStage nextStage) {
        logInfo("Transition to " + nextStage);
        ctx.stage = nextStage;
        handleAllocation(ctx);
    }

    /**
     * Shortcut method that stores the error into context, sets next stage to
     * {@link AzureInstanceStage#ERROR} and delegates to
     * {@link #handleAllocation(AzureInstanceContext)}.
     */
    private void handleError(AzureInstanceContext ctx, Throwable e) {
        ctx.error = e;
        handleAllocation(ctx, AzureInstanceStage.ERROR);
    }

    /**
     * {@code handleAllocation} version suitable for chaining to
     * {@code DeferredResult.whenComplete}.
     */
    private BiConsumer<AzureInstanceContext, Throwable> thenAllocation(AzureInstanceContext ctx,
            AzureInstanceStage next, String namespace) {
        return (ignoreCtx, exc) -> {
            // NOTE: In case of error 'ignoreCtx' is null so use passed context!
            if (exc != null) {
                if (namespace != null) {
                    handleSubscriptionError(ctx, namespace, exc);
                } else {
                    handleError(ctx, exc);
                }
                return;
            }
            handleAllocation(ctx, next);
        };
    }

    private BiConsumer<AzureInstanceContext, Throwable> thenAllocation(AzureInstanceContext ctx,
            AzureInstanceStage next) {
        return thenAllocation(ctx, next, null);
    }

    /**
     * State machine to handle different stages of VM creation/deletion.
     *
     * @see #handleError(AzureInstanceContext, Throwable)
     * @see #handleAllocation(AzureInstanceContext, AzureInstanceStage)
     */
    private void handleAllocation(AzureInstanceContext ctx) {
        try {
            switch (ctx.stage) {
            case CLIENT:
                if (ctx.credentials == null) {
                    ctx.credentials = getAzureConfig(ctx.parentAuth);
                }

                // Creating a shared singleton Http client instance
                // Reference
                // https://square.github.io/okhttp/3.x/okhttp/okhttp3/OkHttpClient.html
                // TODO: https://github.com/Azure/azure-sdk-for-java/issues/1000
                ctx.httpClient = new OkHttpClient();
                ctx.clientBuilder = ctx.httpClient.newBuilder();

                // now that we have a client lets move onto the next step
                switch (ctx.computeRequest.requestType) {
                case CREATE:
                    handleAllocation(ctx, AzureInstanceStage.CHILDAUTH);
                    break;
                case VALIDATE_CREDENTIALS:
                    validateAzureCredentials(ctx);
                    break;
                case DELETE:
                    handleAllocation(ctx, AzureInstanceStage.DELETE);
                    break;
                default:
                    throw new IllegalStateException("Unknown compute request type: " +
                            ctx.computeRequest.requestType);
                }
                break;
            case CHILDAUTH:
                getChildAuth(ctx, AzureInstanceStage.VMDISKS);
                break;
            case VMDISKS:
                getVMDisks(ctx, AzureInstanceStage.INIT_RES_GROUP);
                break;
            case INIT_RES_GROUP:
                createResourceGroup(ctx, AzureInstanceStage.GET_DISK_OS_FAMILY);
                break;
            case GET_DISK_OS_FAMILY:
                differentiateVMImages(ctx, AzureInstanceStage.INIT_STORAGE);
                break;
            case INIT_STORAGE:
                initStorageAccount(ctx, AzureInstanceStage.POPULATE_NIC_CONTEXT);
                break;
            case POPULATE_NIC_CONTEXT:
                ctx.populateContext()
                        .whenComplete(thenAllocation(ctx, AzureInstanceStage.CREATE_NETWORKS));
                break;
            case CREATE_NETWORKS:
                // Create Azure networks, PIPs and NSGs referred by NIC states
                createNetworkIfNotExist(ctx, AzureInstanceStage.CREATE_PUBLIC_IPS);
                break;
            case CREATE_PUBLIC_IPS:
                createPublicIPs(ctx, AzureInstanceStage.CREATE_SECURITY_GROUPS);
                break;
            case CREATE_SECURITY_GROUPS:
                createSecurityGroupsIfNotExist(ctx, AzureInstanceStage.CREATE_NICS);
                break;
            case CREATE_NICS:
                createNICs(ctx, AzureInstanceStage.CREATE);
                break;
            case CREATE:
                // Finally provision the VM
                createVM(ctx, AzureInstanceStage.GET_PUBLIC_IP_ADDRESS);
                break;
            case ENABLE_MONITORING:
                // TODO VSYM-620: Enable monitoring on Azure VMs
                enableMonitoring(ctx, AzureInstanceStage.GET_STORAGE_KEYS);
                break;
            case GET_PUBLIC_IP_ADDRESS:
                getPublicIpAddress(ctx, AzureInstanceStage.GET_STORAGE_KEYS);
                break;
            case GET_STORAGE_KEYS:
                getStorageKeys(ctx, AzureInstanceStage.FINISHED);
                break;
            case DELETE:
                deleteVM(ctx);
                break;
            case FINISHED:
                // This is the ultimate exit point with success of the state machine
                finishWithSuccess(ctx);
                break;
            case ERROR:
                // This is the ultimate exit point with error of the state machine
                errorHandler(ctx);
                break;
            default:
                throw new IllegalStateException("Unknown stage: " + ctx.stage);
            }
        } catch (Exception e) {
            // NOTE: Do not use handleError(err) cause that might result in endless recursion.
            ctx.error = e;
            errorHandler(ctx);
        }
    }

    /**
     * Validates azure credential by making an API call.
     */
    private void validateAzureCredentials(final AzureInstanceContext ctx) {
        if (ctx.computeRequest.isMockRequest) {
            ctx.operation.complete();
            return;
        }

        SubscriptionClient subscriptionClient = new SubscriptionClientImpl(
                AzureConstants.BASE_URI, ctx.credentials, ctx.clientBuilder,
                getRetrofitBuilder());

        subscriptionClient.getSubscriptionsOperations().getAsync(
                ctx.parentAuth.userLink, new ServiceCallback<Subscription>() {
                    @Override
                    public void failure(Throwable e) {
                        // Azure doesn't send us any meaningful status code to work with
                        ServiceErrorResponse rsp = new ServiceErrorResponse();
                        rsp.message = "Invalid Azure credentials";
                        rsp.statusCode = STATUS_CODE_UNAUTHORIZED;
                        ctx.operation.fail(e, rsp);
                    }

                    @Override
                    public void success(ServiceResponse<Subscription> result) {
                        Subscription subscription = result.getBody();
                        logFine("Got subscription %s with id %s", subscription.getDisplayName(),
                                subscription.getId());
                        ctx.operation.complete();
                    }
                });
    }

    private void deleteVM(AzureInstanceContext ctx) {
        if (ctx.computeRequest.isMockRequest) {
            handleAllocation(ctx, AzureInstanceStage.FINISHED);
            return;
        }

        String resourceGroupName = getResourceGroupName(ctx);

        if (resourceGroupName == null || resourceGroupName.isEmpty()) {
            throw new IllegalArgumentException("Resource group name is required");
        }

        String msg = "Deleting resource group [" + resourceGroupName + "] for [" + ctx.vmName +
                "] VM";

        getResourceManagementClient(ctx).getResourceGroupsOperations().beginDeleteAsync(
                resourceGroupName,
                new TransitionToCallback<Void>(ctx, AzureInstanceStage.FINISHED, msg) {
                    @Override
                    CompletionStage<Void> handleSuccess(Void resultBody) {
                        return CompletableFuture.completedFuture((Void) null);
                    }
                });
    }

    /**
     * The ultimate error handler that should handle errors from all sources.
     * <p>
     * NOTE: Do not use directly. Use it through
     * {@link #handleError(AzureInstanceContext, Throwable)}.
     */
    private void errorHandler(AzureInstanceContext ctx) {
        logSevere(ctx.error);

        if (ctx.computeRequest.isMockRequest) {
            finishWithFailure(ctx);
            return;
        }

        if (ctx.computeRequest.requestType != ComputeInstanceRequest.InstanceRequestType.CREATE) {
            finishWithFailure(ctx);
            return;
        }

        if (ctx.resourceGroup == null) {
            finishWithFailure(ctx);
            return;
        }

        // CREATE request has resulted in RG creation -> clear RG and its content.

        String resourceGroupName = ctx.resourceGroup.getName();

        String msg = "Rollback provisioning for [" + ctx.vmName + "] Azure VM: %s";

        logInfo(msg, "STARTED");

        ResourceManagementClient client = getResourceManagementClient(ctx);

        client.getResourceGroupsOperations().beginDeleteAsync(resourceGroupName,
                new AzureAsyncCallback<Void>() {
                    @Override
                    public void onError(Throwable e) {
                        String rollbackError = String.format(msg + ". Details: %s", "FAILED",
                                e.getMessage());

                        // Wrap original ctx.error with rollback error details.
                        ctx.error = new IllegalStateException(rollbackError, ctx.error);

                        finishWithFailure(ctx);
                    }

                    @Override
                    public void onSuccess(ServiceResponse<Void> result) {
                        logFine(msg, "SUCCESS");

                        finishWithFailure(ctx);
                    }
                });
    }

    private void finishWithFailure(AzureInstanceContext ctx) {

        if (ctx.computeRequest.taskReference != null) {
            // Report the error back to the caller
            AdapterUtils.sendFailurePatchToProvisioningTask(this,
                    ctx.computeRequest.taskReference,
                    ctx.error);
        }

        cleanUpHttpClient(this, ctx.httpClient);
    }

    private void finishWithSuccess(AzureInstanceContext ctx) {

        if (ctx.computeRequest.taskReference != null) {
            // Report the success back to the caller
            AdapterUtils.sendPatchToProvisioningTask(this, ctx.computeRequest.taskReference);
        }

        cleanUpHttpClient(this, ctx.httpClient);
    }

    private void createResourceGroup(AzureInstanceContext ctx, AzureInstanceStage nextStage) {

        String resourceGroupName = getResourceGroupName(ctx);

        ResourceGroup resourceGroup = new ResourceGroup();
        resourceGroup.setLocation(ctx.child.description.regionId);

        String msg = "Creating Azure Resource Group [" + resourceGroupName + "] for [" + ctx.vmName
                + "] VM";

        getResourceManagementClient(ctx).getResourceGroupsOperations().createOrUpdateAsync(
                resourceGroupName,
                resourceGroup,
                new TransitionToCallback<ResourceGroup>(ctx, nextStage, msg) {
                    @Override
                    CompletionStage<ResourceGroup> handleSuccess(ResourceGroup rg) {
                        this.ctx.resourceGroup = rg;
                        return CompletableFuture.completedFuture(rg);
                    }
                });
    }

    private void initStorageAccount(AzureInstanceContext ctx, AzureInstanceStage nextStage) {
        StorageAccountCreateParameters storageParameters = new StorageAccountCreateParameters();
        storageParameters.setLocation(ctx.resourceGroup.getLocation());

        if (ctx.bootDisk.customProperties == null) {
            handleError(ctx,
                    new IllegalArgumentException("Custom properties for boot disk is required"));
            return;
        }

        ctx.storageAccountName = ctx.bootDisk.customProperties.get(AZURE_STORAGE_ACCOUNT_NAME);

        if (ctx.storageAccountName == null) {
            if (ctx.vmName != null) {
                ctx.storageAccountName = ctx.vmName.toLowerCase() + "st";
            } else {
                ctx.storageAccountName = String.valueOf(System.currentTimeMillis()) + "st";
            }
        }

        String accountType = ctx.bootDisk.customProperties
                .getOrDefault(AZURE_STORAGE_ACCOUNT_TYPE, DEFAULT_STORAGE_ACCOUNT_TYPE.toValue());
        storageParameters.setAccountType(AccountType.fromValue(accountType));

        String msg = "Creating Azure Storage Account [" + ctx.storageAccountName + "] for ["
                + ctx.vmName + "] VM";

        StorageAccountsOperations azureClient = getStorageManagementClient(ctx)
                .getStorageAccountsOperations();

        AzureProvisioningDeferredResultCallback<StorageAccount> handler =
                new AzureProvisioningDeferredResultCallback<StorageAccount>(this, msg) {
                    @Override
                    public DeferredResult<StorageAccount> consumeProvisioningSuccess(
                            StorageAccount sa) {
                        ctx.storage = sa;

                        StorageDescription storageDescriptionToCreate = new StorageDescription();
                        storageDescriptionToCreate.name = ctx.storageAccountName;
                        storageDescriptionToCreate.type = ctx.storage.getAccountType().name();

                        Operation createStorageDescOp = Operation
                                .createPost(getHost(), StorageDescriptionService.FACTORY_LINK)
                                .setBody(storageDescriptionToCreate);

                        Operation patchBootDiskOp = Operation
                                .createPatch(
                                        UriUtils.buildUri(getHost(),
                                                ctx.bootDisk.documentSelfLink))
                                .setBody(ctx.bootDisk);

                        return sendWithDeferredResult(createStorageDescOp, StorageDescription.class)
                                // Consume created StorageDescription
                                .thenAccept((storageDescription) -> {
                                    ctx.storageDescription = storageDescription;
                                    ctx.bootDisk.storageDescriptionLink = storageDescription.documentSelfLink;
                                    logFine("Creating StorageDescription [%s]: SUCCESS",
                                            storageDescription.name);
                                })
                                // Start next op, patch boot disk, in the sequence
                                .thenCompose((woid) -> sendWithDeferredResult(patchBootDiskOp))
                                // Log boot disk patch success
                                .thenRun(() -> {
                                    logFine("Updating boot disk [%s]: SUCCESS",
                                            ctx.bootDisk.name);
                                })
                                // Return original StorageAccount
                                .thenApply((woid) -> sa);
                    }

                    @Override
                    public String getProvisioningState(StorageAccount sa) {
                        ProvisioningState provisioningState = sa.getProvisioningState();

                        // For some reason SA.provisioningState is null, so consider it CREATING.
                        if (provisioningState == null) {
                            provisioningState = ProvisioningState.CREATING;
                        }

                        return provisioningState.name();
                    }

                    @Override
                    public Runnable checkProvisioningStateCall(
                            ServiceCallback<StorageAccount> checkProvisioningStateCallback) {
                        return () -> azureClient.getPropertiesAsync(
                                ctx.resourceGroup.getName(),
                                ctx.storageAccountName,
                                checkProvisioningStateCallback);

                    }
                };

        azureClient.createAsync(
                ctx.resourceGroup.getName(),
                ctx.storageAccountName,
                storageParameters,
                handler);

        handler.toDeferredResult()
                .thenApply(ignore -> ctx)
                .whenComplete(thenAllocation(ctx, nextStage, STORAGE_NAMESPACE));
    }

    private void createNetworkIfNotExist(AzureInstanceContext ctx, AzureInstanceStage nextStage) {
        if (ctx.nics.isEmpty()) {
            handleAllocation(ctx, nextStage);
            return;
        }

        // Get NICs with not existing subnets
        List<Subnet> subnetsToCreate = ctx.nics.stream()
                .filter(nicCtx -> nicCtx.subnet == null)
                .map(nicCtx -> newAzureSubnet(nicCtx))
                .collect(Collectors.toList());

        if (subnetsToCreate.isEmpty()) {
            handleAllocation(ctx, nextStage);
            return;
        }

        createNetwork(ctx, nextStage, subnetsToCreate);
    }

    private void createNetwork(
            AzureInstanceContext ctx,
            AzureInstanceStage nextStage,
            List<Subnet> subnetsToCreate) {

        // All subnets MUST be at the same vNet, so we select the Primary vNet.
        final AzureNicContext primaryNic = ctx.getPrimaryNic();

        final VirtualNetwork vNetToCreate = newAzureVirtualNetwork(
                ctx, primaryNic, subnetsToCreate);

        final String vNetToCreateName = primaryNic.networkState.name;

        final String vNetToCreateRGName = primaryNic.networkResourceGroupState.name;

        VirtualNetworksOperations azureClient = getNetworkManagementClient(ctx)
                .getVirtualNetworksOperations();

        final String subnetNames = vNetToCreate.getSubnets().stream()
                .map(Subnet::getName)
                .collect(Collectors.joining(","));

        final String msg = "Creating Azure vNet-Subnet [v=" + vNetToCreateName + "; s="
                + subnetNames
                + "] for ["
                + ctx.vmName + "] VM";

        AzureProvisioningDeferredResultCallback<VirtualNetwork> handler =
                new AzureProvisioningDeferredResultCallback<VirtualNetwork>(this, msg) {

                    @Override
                    public DeferredResult<VirtualNetwork> consumeProvisioningSuccess(
                            VirtualNetwork vNet) {
                        // Populate NICs with Azure Subnet
                        for (AzureNicContext nicCtx : ctx.nics) {
                            if (nicCtx.subnet == null) {
                                nicCtx.subnet = vNet.getSubnets().stream()
                                        .filter(subnet -> subnet.getName()
                                                .equals(nicCtx.subnetState.name))
                                        .findFirst().get();
                            }
                        }
                        return DeferredResult.completed(vNet);
                    }

                    @Override
                    public String getProvisioningState(VirtualNetwork vNet) {
                        // Return first NOT Succeeded state,
                        // or PROVISIONING_STATE_SUCCEEDED if all are Succeeded
                        String subnetPS = vNet.getSubnets().stream()
                                .map(Subnet::getProvisioningState)
                                // Get if any is NOT Succeeded...
                                .filter(ps -> !PROVISIONING_STATE_SUCCEEDED.equalsIgnoreCase(ps))
                                // ...and return it.
                                .findFirst()
                                // Otherwise consider all are Succeeded
                                .orElse(PROVISIONING_STATE_SUCCEEDED);

                        if (PROVISIONING_STATE_SUCCEEDED.equals(vNet.getProvisioningState())
                                && PROVISIONING_STATE_SUCCEEDED.equals(subnetPS)) {

                            return PROVISIONING_STATE_SUCCEEDED;
                        }
                        return vNet.getProvisioningState() + ":" + subnetPS;
                    }

                    @Override
                    public Runnable checkProvisioningStateCall(
                            ServiceCallback<VirtualNetwork> checkProvisioningStateCallback) {
                        return () -> azureClient.getAsync(
                                vNetToCreateRGName,
                                vNetToCreateName,
                                null /* expand */,
                                checkProvisioningStateCallback);
                    }
                };

        azureClient.beginCreateOrUpdateAsync(
                vNetToCreateRGName,
                vNetToCreateName,
                vNetToCreate,
                handler);

        handler.toDeferredResult()
                .thenApply(ignore -> ctx)
                .whenComplete(thenAllocation(ctx, nextStage, NETWORK_NAMESPACE));
    }

    /**
     * Converts Photon model constructs to underlying Azure VirtualNetwork model.
     */
    private VirtualNetwork newAzureVirtualNetwork(
            AzureInstanceContext ctx,
            AzureNicContext nicCtx,
            List<Subnet> subnetsToCreate) {

        VirtualNetwork vNet = new VirtualNetwork();
        vNet.setLocation(ctx.resourceGroup.getLocation());

        vNet.setAddressSpace(new AddressSpace());
        vNet.getAddressSpace()
                .setAddressPrefixes(Collections.singletonList(nicCtx.networkState.subnetCIDR));

        vNet.setSubnets(subnetsToCreate);

        return vNet;
    }

    /**
     * Converts Photon model constructs to underlying Azure VirtualNetwork-Subnet model.
     */
    private Subnet newAzureSubnet(AzureNicContext nicCtx) {

        Subnet subnet = new Subnet();
        subnet.setName(nicCtx.subnetState.name);
        subnet.setAddressPrefix(nicCtx.subnetState.subnetCIDR);

        return subnet;
    }

    private void createPublicIPs(AzureInstanceContext ctx, AzureInstanceStage nextStage) {
        if (ctx.nics.isEmpty()) {
            handleAllocation(ctx, nextStage);
            return;
        }

        PublicIPAddressesOperations azureClient = getNetworkManagementClient(ctx)
                .getPublicIPAddressesOperations();

        AzureNicContext nicCtx = ctx.getPrimaryNic();

        final PublicIPAddress publicIPAddress = newAzurePublicIPAddress(ctx, nicCtx);

        final String publicIPName = ctx.vmName + "-pip";

        String msg = "Creating Azure Public IP [" + publicIPName + "] for [" + ctx.vmName + "] VM";

        AzureProvisioningDeferredResultCallback<PublicIPAddress> handler =
                new AzureProvisioningDeferredResultCallback<PublicIPAddress>(this, msg) {
                    @Override
                    public DeferredResult<PublicIPAddress> consumeProvisioningSuccess(
                            PublicIPAddress publicIP) {
                        nicCtx.publicIP = publicIP;

                        return DeferredResult.completed(publicIP);
                    }

                    @Override
                    public String getProvisioningState(PublicIPAddress publicIP) {
                        return publicIP.getProvisioningState();
                    }

                    @Override
                    public Runnable checkProvisioningStateCall(
                            ServiceCallback<PublicIPAddress> checkProvisioningStateCallback) {
                        return () -> azureClient.getAsync(
                                ctx.resourceGroup.getName(),
                                publicIPName,
                                null /* expand */,
                                checkProvisioningStateCallback);
                    }
                };

        azureClient.beginCreateOrUpdateAsync(
                ctx.resourceGroup.getName(),
                publicIPName,
                publicIPAddress,
                handler);

        handler.toDeferredResult()
                .thenApply(ignore -> ctx)
                .whenComplete(thenAllocation(ctx, nextStage, NETWORK_NAMESPACE));
    }

    /**
     * Converts Photon model constructs to underlying Azure PublicIPAddress model.
     */
    private PublicIPAddress newAzurePublicIPAddress(
            AzureInstanceContext ctx,
            AzureNicContext nicCtx) {

        PublicIPAddress publicIPAddress = new PublicIPAddress();
        publicIPAddress.setLocation(ctx.resourceGroup.getLocation());
        publicIPAddress
                .setPublicIPAllocationMethod(nicCtx.nicStateWithDesc.description.assignment.name());

        return publicIPAddress;
    }

    private void createSecurityGroupsIfNotExist(AzureInstanceContext ctx, AzureInstanceStage
            nextStage) {

        if (ctx.nics.isEmpty()) {
            handleAllocation(ctx, nextStage);
            return;
        }

        List<DeferredResult<NetworkSecurityGroup>> createSGDR = ctx.nics.stream()
                .filter(nicCtx -> (
                        // Security Group is requested but no existing security group is mapped.
                        nicCtx.securityGroupStates != null && nicCtx.securityGroupStates.size() == 1
                                && nicCtx.securityGroup == null))
                .map(nicCtx -> {
                    SecurityGroupState sgState = nicCtx.securityGroupStates.get(0);
                    NetworkSecurityGroup nsg = newAzureSecurityGroup(ctx, sgState);
                    return createSecurityGroup(ctx, nicCtx, nsg);
                })
                .collect(Collectors.toList());

        DeferredResult.allOf(createSGDR)
                .whenComplete((all, exc) -> {
                    if (exc != null) {
                        handleError(ctx, exc);
                        return;
                    }
                    handleAllocation(ctx, nextStage);
                });
    }

    private NetworkSecurityGroup newAzureSecurityGroup(AzureInstanceContext ctx, SecurityGroupState
            sg) {

        if (sg == null) {
            throw new IllegalStateException("SecurityGroup state should not be null.");
        }

        List<SecurityRule> securityRules = new ArrayList<>();
        final AtomicInteger priority = new AtomicInteger(1000);
        if (sg.ingress != null) {
            sg.ingress.forEach(rule ->
                    securityRules.add(newAzureSecurityRule(rule,
                            AZURE_SECURITY_GROUP_DIRECTION_INBOUND, priority.getAndIncrement())));
        }

        priority.set(1000);
        if (sg.egress != null) {
            sg.egress.forEach(rule ->
                    securityRules.add(newAzureSecurityRule(rule,
                            AZURE_SECURITY_GROUP_DIRECTION_OUTBOUND, priority.getAndIncrement())));
        }

        NetworkSecurityGroup nsg = new NetworkSecurityGroup();
        nsg.setLocation(ctx.resourceGroup.getLocation());
        nsg.setSecurityRules(securityRules);

        return nsg;
    }

    private SecurityRule newAzureSecurityRule(Rule rule, String direction, int priority) {
        SecurityRule sr = new SecurityRule();
        sr.setPriority(priority);
        sr.setAccess(rule.access.name());
        sr.setDirection(direction);
        if (AZURE_SECURITY_GROUP_DIRECTION_INBOUND.equalsIgnoreCase(direction)) {
            sr.setSourceAddressPrefix(rule.ipRangeCidr);
            sr.setDestinationAddressPrefix(SecurityGroupService.ANY);

            sr.setSourcePortRange(rule.ports);
            sr.setDestinationPortRange(SecurityGroupService.ANY);
        } else {
            sr.setSourceAddressPrefix(SecurityGroupService.ANY);
            sr.setDestinationAddressPrefix(rule.ipRangeCidr);

            sr.setSourcePortRange(SecurityGroupService.ANY);
            sr.setDestinationPortRange(rule.ports);
        }
        sr.setName(rule.name);
        sr.setProtocol(rule.protocol);

        return sr;
    }

    private DeferredResult<NetworkSecurityGroup> createSecurityGroup(
            AzureInstanceContext ctx,
            AzureNicContext nicCtx,
            NetworkSecurityGroup securityGroupToCreate) {

        NetworkSecurityGroupsOperations azureClient = getNetworkManagementClient(ctx)
                .getNetworkSecurityGroupsOperations();

        String nsgName = nicCtx.securityGroupStates.get(0).name;

        String msg = "Create Azure Security Group["
                + nicCtx.networkResourceGroupState.name + "/"
                + nsgName + "/"
                + nicCtx.securityGroupStates.get(0).name
                + "] for [" + nicCtx.nicStateWithDesc.name + "] NIC for ["
                + ctx.vmName
                + "] VM";
        AzureProvisioningDeferredResultCallback<NetworkSecurityGroup> handler =
                new AzureProvisioningDeferredResultCallback<NetworkSecurityGroup>(this, msg) {
                    @Override
                    public DeferredResult<NetworkSecurityGroup> consumeProvisioningSuccess(
                            NetworkSecurityGroup securityGroup) {

                        nicCtx.securityGroup = securityGroup;

                        return DeferredResult.completed(securityGroup);
                    }

                    @Override
                    public Runnable checkProvisioningStateCall(
                            ServiceCallback<NetworkSecurityGroup> checkProvisioningStateCallback) {
                        return () -> azureClient.getAsync(
                                ctx.resourceGroup.getName(),
                                nsgName,
                                null /* expand */,
                                checkProvisioningStateCallback);
                    }

                    @Override
                    public String getProvisioningState(NetworkSecurityGroup body) {
                        return body.getProvisioningState();
                    }
                };

        azureClient.beginCreateOrUpdateAsync(
                ctx.resourceGroup.getName(),
                nsgName,
                securityGroupToCreate,
                handler);

        return handler.toDeferredResult();
    }

    private void createNICs(AzureInstanceContext ctx, AzureInstanceStage nextStage) {

        if (ctx.nics.isEmpty()) {
            handleAllocation(ctx, nextStage);
            return;
        }

        // Shared state between multi async calls {{
        AzureCallContext callContext = AzureCallContext.newBatchCallContext(ctx.nics.size());
        NetworkInterfacesOperations azureClient = getNetworkManagementClient(ctx)
                .getNetworkInterfacesOperations();
        // }}

        for (AzureNicContext nicCtx : ctx.nics) {

            final NetworkInterface nic = newAzureNetworkInterface(ctx, nicCtx);

            final String nicName = nicCtx.nicStateWithDesc.name;

            String msg = "Creating Azure NIC [" + nicName + "] for [" + ctx.vmName + "] VM";

            azureClient.beginCreateOrUpdateAsync(
                    ctx.resourceGroup.getName(),
                    nicName,
                    nic,
                    new TransitionToCallback<NetworkInterface>(ctx, nextStage, callContext, msg) {
                        @Override
                        protected CompletionStage<NetworkInterface> handleSuccess(
                                NetworkInterface nic) {
                            nicCtx.nic = nic;
                            return CompletableFuture.completedFuture(nic);
                        }
                    });
        }
    }

    /**
     * Converts Photon model constructs to underlying Azure NetworkInterface model.
     */
    private NetworkInterface newAzureNetworkInterface(
            AzureInstanceContext ctx,
            AzureNicContext nicCtx) {

        NetworkInterfaceIPConfiguration ipConfig = new NetworkInterfaceIPConfiguration();
        ipConfig.setName(generateName(NICCONFIG_NAME_PREFIX));
        ipConfig.setPrivateIPAllocationMethod(PRIVATE_IP_ALLOCATION_METHOD);
        ipConfig.setSubnet(nicCtx.subnet);
        ipConfig.setPublicIPAddress(nicCtx.publicIP);

        NetworkInterface nic = new NetworkInterface();
        nic.setLocation(ctx.resourceGroup.getLocation());
        nic.setIpConfigurations(new ArrayList<>());
        nic.getIpConfigurations().add(ipConfig);
        nic.setNetworkSecurityGroup(nicCtx.securityGroup);

        return nic;
    }

    private void createVM(AzureInstanceContext ctx, AzureInstanceStage nextStage) {
        ComputeDescriptionService.ComputeDescription description = ctx.child.description;

        Map<String, String> customProperties = description.customProperties;
        if (customProperties == null) {
            handleError(ctx, new IllegalStateException("Custom properties not specified"));
            return;
        }

        DiskState bootDisk = ctx.bootDisk;
        if (bootDisk == null) {
            handleError(ctx, new IllegalStateException("Azure bootDisk not specified"));
            return;
        }

        String cloudConfig = null;
        if (bootDisk.bootConfig != null
                && bootDisk.bootConfig.files.length > CLOUD_CONFIG_DEFAULT_FILE_INDEX) {
            cloudConfig = bootDisk.bootConfig.files[CLOUD_CONFIG_DEFAULT_FILE_INDEX].contents;
        }

        VirtualMachine request = new VirtualMachine();
        request.setLocation(ctx.resourceGroup.getLocation());

        // Set OS profile.
        OSProfile osProfile = new OSProfile();
        String vmName = ctx.vmName;
        osProfile.setComputerName(vmName);
        osProfile.setAdminUsername(ctx.childAuth.userEmail);
        osProfile.setAdminPassword(ctx.childAuth.privateKey);
        if (cloudConfig != null) {
            try {
                osProfile.setCustomData(Base64.getEncoder()
                        .encodeToString(cloudConfig.getBytes(Utils.CHARSET)));
            } catch (UnsupportedEncodingException e) {
                logWarning("Error encoding user data");
                return;
            }
        }
        request.setOsProfile(osProfile);

        // Set hardware profile.
        HardwareProfile hardwareProfile = new HardwareProfile();
        hardwareProfile.setVmSize(
                description.instanceType != null ? description.instanceType : DEFAULT_VM_SIZE);
        request.setHardwareProfile(hardwareProfile);

        // Set storage profile.
        VirtualHardDisk vhd = new VirtualHardDisk();
        String vhdName = getVHDName(vmName);
        vhd.setUri(String.format(VHD_URI_FORMAT, ctx.storageAccountName, vhdName));

        OSDisk osDisk = new OSDisk();
        osDisk.setName(vmName);
        osDisk.setVhd(vhd);
        osDisk.setCaching(bootDisk.customProperties.get(AZURE_OSDISK_CACHING));
        // We don't support Attach option which allows to use a specialized disk to create the
        // virtual machine.
        osDisk.setCreateOption(OS_DISK_CREATION_OPTION);

        StorageProfile storageProfile = new StorageProfile();
        // Currently we only support platform images.
        storageProfile.setImageReference(ctx.imageReference);
        storageProfile.setOsDisk(osDisk);
        request.setStorageProfile(storageProfile);

        // Set network profile {{
        NetworkProfile networkProfile = new NetworkProfile();
        networkProfile.setNetworkInterfaces(new ArrayList<>());

        for (AzureNicContext nicCtx : ctx.nics) {
            NetworkInterfaceReference nicRef = new NetworkInterfaceReference();
            nicRef.setId(nicCtx.nic.getId());
            // NOTE: First NIC is marked as Primary.
            nicRef.setPrimary(networkProfile.getNetworkInterfaces().isEmpty());

            networkProfile.getNetworkInterfaces().add(nicRef);
        }
        request.setNetworkProfile(networkProfile);

        logFine("Creating virtual machine with name [%s]", vmName);

        getComputeManagementClient(ctx).getVirtualMachinesOperations().createOrUpdateAsync(
                ctx.resourceGroup.getName(), vmName, request,
                new AzureAsyncCallback<VirtualMachine>() {
                    @Override
                    public void onError(Throwable e) {
                        handleSubscriptionError(ctx, COMPUTE_NAMESPACE, e);
                    }

                    @Override
                    public void onSuccess(ServiceResponse<VirtualMachine> result) {
                        VirtualMachine vm = result.getBody();
                        logFine("Successfully created vm [%s]", vm.getName());

                        ComputeState cs = new ComputeState();
                        // Azure for some case changes the case of the vm id.
                        ctx.vmId = vm.getId().toLowerCase();
                        cs.id = ctx.vmId;
                        cs.type = ComputeType.VM_GUEST;
                        cs.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;
                        cs.lifecycleState = LifecycleState.READY;
                        if (ctx.child.customProperties == null) {
                            cs.customProperties = new HashMap<>();
                        } else {
                            cs.customProperties = ctx.child.customProperties;
                        }

                        Operation.CompletionHandler completionHandler = (ox,
                                exc) -> {
                            if (exc != null) {
                                handleError(ctx, exc);
                                return;
                            }
                            handleAllocation(ctx, nextStage);
                        };

                        sendRequest(
                                Operation.createPatch(ctx.computeRequest.resourceReference)
                                        .setBody(cs).setCompletion(completionHandler)
                                        .setReferer(getHost().getUri()));
                    }
                });
    }

    /**
     * Gets the public IP address from the VM and patches the compute state and primary NIC state.
     */
    private void getPublicIpAddress(AzureInstanceContext ctx, AzureInstanceStage nextStage) {

        NetworkManagementClient client = getNetworkManagementClient(ctx);

        client.getPublicIPAddressesOperations().getAsync(
                ctx.resourceGroup.getName(),
                ctx.getPrimaryNic().publicIP.getName(),
                null /* expand */,
                new AzureAsyncCallback<PublicIPAddress>() {

                    @Override
                    public void onError(Throwable e) {
                        handleError(ctx, e);
                    }

                    @Override
                    public void onSuccess(ServiceResponse<PublicIPAddress> result) {
                        ctx.getPrimaryNic().publicIP = result.getBody();

                        OperationJoin operationJoin = OperationJoin
                                .create(patchComputeState(ctx), patchNICState(ctx))
                                .setCompletion((ops, excs) -> {
                                    if (excs != null) {
                                        handleError(ctx, new IllegalStateException(
                                                "Error patching compute state and primary NIC state with VM Public IP address."));
                                        return;
                                    }
                                    handleAllocation(ctx, nextStage);
                                });
                        operationJoin.sendWith(AzureInstanceService.this);
                    }

                    private Operation patchComputeState(AzureInstanceContext ctx) {

                        ComputeState computeState = new ComputeState();

                        computeState.address = ctx.getPrimaryNic().publicIP.getIpAddress();

                        return Operation.createPatch(ctx.computeRequest.resourceReference)
                                .setBody(computeState)
                                .setCompletion((op, exc) -> {
                                    if (exc == null) {
                                        logFine("Patching compute state with VM Public IP address ["
                                                + computeState.address + "]: SUCCESS");
                                    }
                                });

                    }

                    private Operation patchNICState(AzureInstanceContext ctx) {

                        NetworkInterfaceState primaryNicState = new NetworkInterfaceState();

                        primaryNicState.address = ctx.getPrimaryNic().publicIP.getIpAddress();

                        URI primaryNicUri = UriUtils.buildUri(getHost(),
                                ctx.getPrimaryNic().nicStateWithDesc.documentSelfLink);

                        return Operation.createPatch(primaryNicUri)
                                .setBody(primaryNicState)
                                .setCompletion((op, exc) -> {
                                    if (exc == null) {
                                        logFine("Patching primary NIC state with VM Public IP address ["
                                                + primaryNicState.address + "]: SUCCESS");
                                    }
                                });

                    }
                });
    }

    /**
     * Gets the storage keys from azure and patches the credential state.
     */
    private void getStorageKeys(AzureInstanceContext ctx, AzureInstanceStage nextStage) {
        StorageManagementClient client = getStorageManagementClient(ctx);

        client.getStorageAccountsOperations().listKeysAsync(ctx.resourceGroup.getName(),
                ctx.storageAccountName, new AzureAsyncCallback<StorageAccountKeys>() {
                    @Override
                    public void onError(Throwable e) {
                        handleError(ctx, e);
                    }

                    @Override
                    public void onSuccess(ServiceResponse<StorageAccountKeys> result) {
                        logFine("Retrieved the storage account keys for storage account [%s].",
                                ctx.storageAccountName);
                        StorageAccountKeys keys = result.getBody();
                        String key1 = keys.getKey1();
                        String key2 = keys.getKey2();

                        AuthCredentialsServiceState storageAuth = new AuthCredentialsServiceState();
                        storageAuth.customProperties = new HashMap<>();
                        storageAuth.customProperties.put(AZURE_STORAGE_ACCOUNT_KEY1, key1);
                        storageAuth.customProperties.put(AZURE_STORAGE_ACCOUNT_KEY2, key2);
                        Operation patchStorageDescriptionWithKeys = Operation
                                .createPost(getHost(), AuthCredentialsService.FACTORY_LINK)
                                .setBody(storageAuth).setCompletion((o, e) -> {
                                    if (e != null) {
                                        handleError(ctx, e);
                                        return;
                                    }
                                    AuthCredentialsServiceState resultAuth = o.getBody(
                                            AuthCredentialsServiceState.class);
                                    ctx.storageDescription.authCredentialsLink = resultAuth.documentSelfLink;
                                    Operation patch = Operation
                                            .createPatch(UriUtils.buildUri(getHost(),
                                                    ctx.storageDescription.documentSelfLink))
                                            .setBody(ctx.storageDescription)
                                            .setCompletion(((completedOp, failure) -> {
                                                if (failure != null) {
                                                    handleError(ctx, failure);
                                                    return;
                                                }
                                                logFine("Patched storage description.");
                                                handleAllocation(ctx, nextStage);
                                            }));
                                    sendRequest(patch);
                                });
                        sendRequest(patchStorageDescriptionWithKeys);
                    }
                });
    }

    private String getVHDName(String vmName) {
        return vmName + BOOT_DISK_SUFFIX;
    }

    private ImageReference getImageReference(String imageId) {
        String[] imageIdParts = imageId.split(":");
        if (imageIdParts.length != 4) {
            throw new IllegalArgumentException("Azure image ID should be of the format "
                    + "<publisher>:<offer>:<sku>:<version>");
        }

        ImageReference imageReference = new ImageReference();
        imageReference.setPublisher(imageIdParts[0]);
        imageReference.setOffer(imageIdParts[1]);
        imageReference.setSku(imageIdParts[2]);
        imageReference.setVersion(imageIdParts[3]);

        return imageReference;
    }

    /**
     * This method tries to detect a subscription registration error and register subscription for
     * given namespace. Otherwise the fallback is to transition to error state.
     */
    private void handleSubscriptionError(AzureInstanceContext ctx, String namespace,
            Throwable e) {
        if (e instanceof CloudException) {
            CloudException ce = (CloudException) e;
            CloudError body = ce.getBody();
            if (body != null) {
                String code = body.getCode();
                if (MISSING_SUBSCRIPTION_CODE.equals(code)) {
                    registerSubscription(ctx, namespace);
                    return;
                }
            }
        }
        handleError(ctx, e);
    }

    private void registerSubscription(AzureInstanceContext ctx, String namespace) {
        ResourceManagementClient client = getResourceManagementClient(ctx);
        client.getProvidersOperations().registerAsync(namespace,
                new AzureAsyncCallback<Provider>() {
                    @Override
                    public void onError(Throwable e) {
                        handleError(ctx, e);
                    }

                    @Override
                    public void onSuccess(ServiceResponse<Provider> result) {
                        Provider provider = result.getBody();
                        String registrationState = provider.getRegistrationState();
                        if (!PROVIDER_REGISTRED_STATE.equalsIgnoreCase(registrationState)) {
                            logInfo("%s namespace registration in %s state", namespace,
                                    registrationState);
                            long retryExpiration = Utils.getNowMicrosUtc()
                                    + DEFAULT_EXPIRATION_INTERVAL_MICROS;
                            getSubscriptionState(ctx, namespace, retryExpiration);
                            return;
                        }
                        logFine("Successfully registered namespace [%s]", provider.getNamespace());
                        handleAllocation(ctx);
                    }
                });
    }

    private void getSubscriptionState(AzureInstanceContext ctx,
            String namespace, long retryExpiration) {
        if (Utils.getNowMicrosUtc() > retryExpiration) {
            String msg = String.format("Subscription for %s namespace did not reach %s state",
                    namespace, PROVIDER_REGISTRED_STATE);
            handleError(ctx, new RuntimeException(msg));
            return;
        }

        ResourceManagementClient client = getResourceManagementClient(ctx);

        getHost().schedule(
                () -> client.getProvidersOperations().getAsync(namespace,
                        new AzureAsyncCallback<Provider>() {
                            @Override
                            public void onError(Throwable e) {
                                handleError(ctx, e);
                            }

                            @Override
                            public void onSuccess(ServiceResponse<Provider> result) {
                                Provider provider = result.getBody();
                                String registrationState = provider.getRegistrationState();
                                if (!PROVIDER_REGISTRED_STATE.equalsIgnoreCase(registrationState)) {
                                    logInfo("%s namespace registration in %s state",
                                            namespace, registrationState);
                                    getSubscriptionState(ctx, namespace, retryExpiration);
                                    return;
                                }
                                logFine("Successfully registered namespace [%s]",
                                        provider.getNamespace());
                                handleAllocation(ctx);
                            }
                        }),
                RETRY_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void getChildAuth(AzureInstanceContext ctx, AzureInstanceStage next) {
        if (ctx.child.description.authCredentialsLink == null) {
            handleError(ctx, new IllegalStateException("Auth information for compute is required"));
            return;
        }

        String childAuthLink = ctx.child.description.authCredentialsLink;
        Consumer<Operation> onSuccess = (op) -> {
            ctx.childAuth = op.getBody(AuthCredentialsService.AuthCredentialsServiceState.class);
            handleAllocation(ctx, next);
        };
        AdapterUtils.getServiceState(this, childAuthLink, onSuccess, getFailureConsumer(ctx));
    }

    private Consumer<Throwable> getFailureConsumer(AzureInstanceContext ctx) {
        return (t) -> handleError(ctx, t);
    }

    private Retrofit.Builder getRetrofitBuilder() {
        Retrofit.Builder builder = new Retrofit.Builder();
        builder.callbackExecutor(this.executorService);
        return builder;
    }

    private String generateName(String prefix) {
        return prefix + randomString(5);
    }

    private String randomString(int length) {
        Random random = new Random();
        StringBuilder stringBuilder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            stringBuilder.append((char) ('a' + random.nextInt(26)));
        }
        return stringBuilder.toString();
    }

    private ResourceManagementClient getResourceManagementClient(AzureInstanceContext ctx) {
        if (ctx.resourceManagementClient == null) {
            ResourceManagementClient client = new ResourceManagementClientImpl(
                    AzureConstants.BASE_URI, ctx.credentials, ctx.clientBuilder,
                    getRetrofitBuilder());
            client.setSubscriptionId(ctx.parentAuth.userLink);
            ctx.resourceManagementClient = client;
        }
        return ctx.resourceManagementClient;
    }

    public NetworkManagementClient getNetworkManagementClient(AzureInstanceContext ctx) {
        if (ctx.networkManagementClient == null) {
            NetworkManagementClient client = new NetworkManagementClientImpl(
                    AzureConstants.BASE_URI, ctx.credentials, ctx.clientBuilder,
                    getRetrofitBuilder());
            client.setSubscriptionId(ctx.parentAuth.userLink);
            ctx.networkManagementClient = client;
        }
        return ctx.networkManagementClient;
    }

    private StorageManagementClient getStorageManagementClient(AzureInstanceContext ctx) {
        if (ctx.storageManagementClient == null) {
            StorageManagementClient client = new StorageManagementClientImpl(
                    AzureConstants.BASE_URI, ctx.credentials, ctx.clientBuilder,
                    getRetrofitBuilder());
            client.setSubscriptionId(ctx.parentAuth.userLink);
            ctx.storageManagementClient = client;
        }
        return ctx.storageManagementClient;
    }

    private ComputeManagementClient getComputeManagementClient(AzureInstanceContext ctx) {
        if (ctx.computeManagementClient == null) {
            ComputeManagementClient client = new ComputeManagementClientImpl(
                    AzureConstants.BASE_URI, ctx.credentials, ctx.clientBuilder,
                    getRetrofitBuilder());
            client.setSubscriptionId(ctx.parentAuth.userLink);
            ctx.computeManagementClient = client;
        }
        return ctx.computeManagementClient;
    }

    /**
     * Method will retrieve disks for targeted image
     */
    private void getVMDisks(AzureInstanceContext ctx, AzureInstanceStage nextStage) {
        if (ctx.child.diskLinks == null || ctx.child.diskLinks.size() == 0) {
            handleError(ctx, new IllegalStateException("a minimum of 1 disk is required"));
            return;
        }
        Collection<Operation> operations = new ArrayList<>();
        // iterate thru disks and create operations
        operations.addAll(ctx.child.diskLinks.stream()
                .map(disk -> Operation.createGet(UriUtils.buildUri(this.getHost(), disk)))
                .collect(Collectors.toList()));

        OperationJoin operationJoin = OperationJoin.create(operations)
                .setCompletion(
                        (ops, exc) -> {
                            if (exc != null) {
                                handleError(ctx, new IllegalStateException(
                                        "Error getting disk information"));
                                return;
                            }

                            ctx.childDisks = new ArrayList<>();
                            for (Operation op : ops.values()) {
                                DiskState disk = op.getBody(DiskState.class);

                                // We treat the first disk in the boot order as the boot disk.
                                if (disk.bootOrder == 1) {
                                    if (ctx.bootDisk != null) {
                                        handleError(ctx, new IllegalStateException(
                                                "Only 1 boot disk is allowed"));
                                        return;
                                    }

                                    ctx.bootDisk = disk;
                                } else {
                                    ctx.childDisks.add(disk);
                                }
                            }

                            if (ctx.bootDisk == null) {
                                handleError(ctx,
                                        new IllegalStateException("Boot disk is required"));
                                return;
                            }

                            handleAllocation(ctx, nextStage);
                        });
        operationJoin.sendWith(this);
    }

    /**
     * Differentiate between Windows and Linux Images
     */
    private void differentiateVMImages(AzureInstanceContext ctx, AzureInstanceStage nextStage) {
        DiskState bootDisk = ctx.bootDisk;
        if (bootDisk == null) {
            handleError(ctx, new IllegalStateException("Azure bootDisk not specified"));
            return;
        }
        URI imageId = ctx.bootDisk.sourceImageReference;
        if (imageId == null) {
            handleError(ctx, new IllegalStateException("Azure image reference not specified"));
            return;
        }
        ImageReference imageReference = getImageReference(
                ctx.bootDisk.sourceImageReference.toString());

        if (AzureConstants.AZURE_URN_VERSION_LATEST.equalsIgnoreCase(imageReference.getVersion())) {
            logFine("Getting the latest version for %s:%s:%s", imageReference.getPublisher(),
                    imageReference.getOffer(), imageReference.getSku());
            // Get the latest version based on the provided publisher, offer and SKU (filter = null,
            // top = 1, orderBy = name desc)
            getComputeManagementClient(ctx).getVirtualMachineImagesOperations().listAsync(
                    ctx.resourceGroup.getLocation(), imageReference.getPublisher(),
                    imageReference.getOffer(), imageReference.getSku(),
                    null, 1, AzureConstants.ORDER_BY_VM_IMAGE_RESOURCE_NAME_DESC,
                    new AzureAsyncCallback<List<VirtualMachineImageResource>>() {

                        @Override
                        public void onError(Throwable e) {
                            handleError(ctx, new IllegalStateException(e.getLocalizedMessage()));
                        }

                        @Override
                        public void onSuccess(
                                ServiceResponse<List<VirtualMachineImageResource>> result) {
                            List<VirtualMachineImageResource> resource = result.getBody();
                            if (resource == null || resource.get(0) == null) {
                                handleError(ctx,
                                        new IllegalStateException("No latest version found"));
                                return;
                            }
                            // Get the first object because the request asks only for one object
                            // (top = 1)
                            // We don't care what version we use to get the VirtualMachineImage
                            String version = resource.get(0).getName();
                            getVirtualMachineImage(ctx, nextStage, version, imageReference);
                        }
                    });
        } else {
            getVirtualMachineImage(ctx, nextStage, imageReference.getVersion(), imageReference);
        }
    }

    /**
     * Get the VirtualMachineImage using publisher, offer, SKU and version.
     */
    private void getVirtualMachineImage(AzureInstanceContext ctx,
            AzureInstanceStage nextStage, String version, ImageReference imageReference) {

        logFine("URN of the OS - %s:%s:%s:%s", imageReference.getPublisher(),
                imageReference.getOffer(), imageReference.getSku(), version);
        getComputeManagementClient(ctx).getVirtualMachineImagesOperations().getAsync(
                ctx.resourceGroup.getLocation(), imageReference.getPublisher(),
                imageReference.getOffer(), imageReference.getSku(), version,
                new AzureAsyncCallback<VirtualMachineImage>() {
                    @Override
                    public void onError(Throwable e) {
                        handleError(ctx, new IllegalStateException(e.getLocalizedMessage()));
                    }

                    @Override
                    public void onSuccess(ServiceResponse<VirtualMachineImage> result) {
                        VirtualMachineImage image = result.getBody();
                        if (image == null || image.getOsDiskImage() == null) {
                            handleError(ctx, new IllegalStateException("OS Disk Image not found."));
                            return;
                        }
                        // Get the operating system family
                        ctx.operatingSystemFamily = image.getOsDiskImage().getOperatingSystem();
                        logFine("Retrieved the operating system family - %s",
                                ctx.operatingSystemFamily);
                        ctx.imageReference = imageReference;
                        handleAllocation(ctx, nextStage);
                    }
                });
    }

    private void enableMonitoring(AzureInstanceContext ctx, AzureInstanceStage nextStage) {
        Operation readFile = Operation.createGet(null).setCompletion((o, e) -> {
            if (e != null) {
                handleError(ctx, e);
                return;
            }
            AzureDiagnosticSettings azureDiagnosticSettings = o
                    .getBody(AzureDiagnosticSettings.class);
            String vmName = ctx.vmName;
            String azureInstanceId = ctx.vmId;
            String storageAccountName = ctx.storageAccountName;

            // Replace the resourceId and storageAccount keys with correct values
            azureDiagnosticSettings.getProperties()
                    .getPublicConfiguration()
                    .getDiagnosticMonitorConfiguration()
                    .getMetrics()
                    .setResourceId(azureInstanceId);
            azureDiagnosticSettings.getProperties()
                    .getPublicConfiguration()
                    .setStorageAccount(storageAccountName);

            ApplicationTokenCredentials credentials = ctx.credentials;

            URI uri = UriUtils.extendUriWithQuery(
                    UriUtils.buildUri(UriUtils.buildUri(AzureConstants.BASE_URI_FOR_REST),
                            azureInstanceId, AzureConstants.DIAGNOSTIC_SETTING_ENDPOINT,
                            AzureConstants.DIAGNOSTIC_SETTING_AGENT),
                    AzureConstants.QUERY_PARAM_API_VERSION,
                    AzureConstants.DIAGNOSTIC_SETTING_API_VERSION);

            Operation operation = Operation.createPut(uri);
            operation.setBody(azureDiagnosticSettings);
            operation.addRequestHeader(Operation.ACCEPT_HEADER,
                    Operation.MEDIA_TYPE_APPLICATION_JSON);
            operation.addRequestHeader(Operation.CONTENT_TYPE_HEADER,
                    Operation.MEDIA_TYPE_APPLICATION_JSON);
            try {
                operation.addRequestHeader(Operation.AUTHORIZATION_HEADER,
                        AzureConstants.AUTH_HEADER_BEARER_PREFIX + credentials.getToken());
            } catch (Exception ex) {
                handleError(ctx, ex);
                return;
            }

            logFine("Enabling monitoring on the VM [%s]", vmName);
            operation.setCompletion((op, er) -> {
                if (er != null) {
                    handleError(ctx, er);
                    return;
                }

                logFine("Successfully enabled monitoring on the VM [%s]", vmName);
                handleAllocation(ctx, nextStage);
            });
            sendRequest(operation);
        });

        String fileUri = getClass().getResource(AzureConstants.DIAGNOSTIC_SETTINGS_JSON_FILE_NAME)
                .getFile();
        File jsonPayloadFile = new File(fileUri);
        try {
            FileUtils.readFileAndComplete(readFile, jsonPayloadFile);
        } catch (Exception e) {
            handleError(ctx, e);
        }
    }

    private String getResourceGroupName(AzureInstanceContext ctx) {
        String resourceGroupName = null;
        if (ctx.child.customProperties != null) {
            resourceGroupName = ctx.child.customProperties.get(RESOURCE_GROUP_NAME);
        }

        if (resourceGroupName == null && ctx.child.description.customProperties != null) {
            resourceGroupName = ctx.child.description.customProperties.get(RESOURCE_GROUP_NAME);
        }

        if (resourceGroupName == null || resourceGroupName.isEmpty()) {
            resourceGroupName = DEFAULT_GROUP_PREFIX + String.valueOf(System.currentTimeMillis());
        }
        return resourceGroupName;
    }

    /**
     * The class represents the context of async calls(either single or batch) to Azure cloud.
     *
     * @see TransitionToCallback
     */
    static class AzureCallContext {

        static AzureCallContext newSingleCallContext() {
            return new AzureCallContext(1);
        }

        static AzureCallContext newBatchCallContext(int numberOfCalls) {
            return new AzureCallContext(numberOfCalls);
        }

        /**
         * The number of calls associated with this context.
         */
        final AtomicInteger numberOfCalls;

        /**
         * Flag indicating whether any call has failed.
         */
        final AtomicBoolean hasAnyFailed = new AtomicBoolean(false);

        /**
         * Flag indicating whether Azure error should be considered as exceptional. Default behavior
         * is to fail on error.
         */
        boolean failOnError = true;

        private AzureCallContext(int numberOfCalls) {
            this.numberOfCalls = new AtomicInteger(numberOfCalls);
        }

        AzureCallContext withFailOnError(boolean failOnError) {
            this.failOnError = failOnError;
            return this;
        }
    }

    /**
     * Azure async callback implementation that transitions to the next stage of the
     * AzureInstanceService state machine once over.
     */
    private abstract class TransitionToCallback<T> extends AzureAsyncCallback<T> {

        final AzureInstanceContext ctx;

        /**
         * The next stage of {@code AzureInstanceService} state machine to transition once over.
         */
        final AzureInstanceStage nextStage;

        /**
         * The execution context of this call indicating whether it is executed single or in batch
         * mode.
         */
        final AzureCallContext callCtx;

        /**
         * Informative message to log while executing this call.
         */
        final String msg;

        /**
         * Use this callback in case of single Azure async call.
         */
        TransitionToCallback(
                AzureInstanceContext ctx,
                AzureInstanceStage nextStage,
                String message) {

            this(ctx, nextStage, AzureCallContext.newSingleCallContext(), message);
        }

        /**
         * Use this callback in case of multiple/batch Azure async calls. It transitions to next
         * stage when ALL calls have succeeded or transitions exceptionally upon FIRST error. If
         * latter subsequent calls (either success or failure) are just ignored.
         */
        TransitionToCallback(
                AzureInstanceContext ctx,
                AzureInstanceStage nextStage,
                AzureCallContext azureCallContext,
                String message) {

            this.ctx = ctx;
            this.nextStage = nextStage;
            this.callCtx = azureCallContext;
            this.msg = message;

            AzureInstanceService.this.logInfo(this.msg + ": STARTED");
        }

        @Override
        public final void onError(Throwable e) {

            if (this.callCtx.failOnError) {

                e = new IllegalStateException(this.msg + ": FAILED. Details: " + e.getMessage(), e);

                if (this.callCtx.hasAnyFailed.compareAndSet(false, true)) {
                    // Check whether this is the first failure and proceed to next stage.
                    // i.e. fail-fast on batch operations.
                    handleFailure(e);
                } else {
                    // Any subsequent failure is just logged.
                    AzureInstanceService.this.logSevere(e);
                }
            } else {
                AzureInstanceService.this.logFine("%s: SUCCESS with error. Details: %s", this.msg,
                        e.getMessage());

                transition();
            }
        }

        /**
         * Hook that might be implemented by descendants to handle failed Azure call.
         * <p>
         * Default error handling delegates to
         * {@link AzureInstanceService#handleError(AzureInstanceContext, Throwable)}.
         */
        void handleFailure(Throwable e) {
            AzureInstanceService.this.handleError(this.ctx, e);
        }

        @Override
        public final void onSuccess(ServiceResponse<T> result) {

            if (this.callCtx.hasAnyFailed.get()) {
                AzureInstanceService.this.logInfo(this.msg + ": SUCCESS. Still batch calls have "
                        + "failed so SKIP this result.");
                return;
            }

            AzureInstanceService.this.logFine(this.msg + ": SUCCESS");

            // First delegate to descendants to process result body
            CompletionStage<T> handleSuccess = handleSuccess(result.getBody());

            // Then transition upon completion
            handleSuccess.whenComplete((body, exc) -> {
                if (exc != null) {
                    handleFailure(exc);
                } else {
                    transition();
                }
            });
        }

        /**
         * Hook to be implemented by descendants to handle successful Azure call.
         * <p>
         * The implementation should focus on consuming the result. It is responsibility of this
         * class to handle transition to next stage as defined by
         * {@link AzureInstanceService#handleAllocation(AzureInstanceContext, AzureInstanceStage)}.
         */
        abstract CompletionStage<T> handleSuccess(T resultBody);

        /**
         * Transition to the next stage of AzureInstanceService state machine once all Azure calls
         * are completed.
         */
        void transition() {
            if (this.callCtx.numberOfCalls.decrementAndGet() == 0) {
                // Check whether all calls have succeeded and proceed to next stage.
                AzureInstanceService.this.handleAllocation(this.ctx, this.nextStage);
            }
        }
    }
}
