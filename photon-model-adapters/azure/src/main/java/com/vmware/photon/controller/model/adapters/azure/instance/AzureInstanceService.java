/*
 * Copyright (c) 2015-2018 VMware, Inc. All Rights Reserved.
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

import static com.vmware.photon.controller.model.ComputeProperties.CUSTOM_OS_TYPE;
import static com.vmware.photon.controller.model.ComputeProperties.RESOURCE_GROUP_NAME;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_DATA_DISK_CACHING;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_MANAGED_DISK_TYPE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_OSDISK_CACHING;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_DEFAULT_RG_NAME;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_KEY1;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_NAME;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_RG_NAME;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.COMPUTER_NAME;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.COMPUTE_NAMESPACE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.DISK_CONTROLLER_NUMBER;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.INVALID_PARAMETER;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.INVALID_RESOURCE_GROUP;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.MISSING_SUBSCRIPTION_CODE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.NETWORK_NAMESPACE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.PROVIDER_REGISTRED_STATE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.PROVISIONING_STATE_SUCCEEDED;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.STORAGE_ACCOUNT_ALREADY_EXIST;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.STORAGE_CONNECTION_STRING;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.STORAGE_NAMESPACE;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.getStorageAccountKeyName;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.CLOUD_CONFIG_DEFAULT_FILE_INDEX;
import static com.vmware.photon.controller.model.util.PhotonModelUriUtils.createInventoryUri;
import static com.vmware.xenon.common.Operation.STATUS_CODE_UNAUTHORIZED;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.microsoft.azure.CloudError;
import com.microsoft.azure.CloudException;
import com.microsoft.azure.SubResource;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.compute.AvailabilitySet;
import com.microsoft.azure.management.compute.AvailabilitySetSkuTypes;
import com.microsoft.azure.management.compute.CachingTypes;
import com.microsoft.azure.management.compute.DataDisk;
import com.microsoft.azure.management.compute.Disk;
import com.microsoft.azure.management.compute.DiskCreateOptionTypes;
import com.microsoft.azure.management.compute.HardwareProfile;
import com.microsoft.azure.management.compute.NetworkProfile;
import com.microsoft.azure.management.compute.OSDisk;
import com.microsoft.azure.management.compute.OSProfile;
import com.microsoft.azure.management.compute.StorageAccountTypes;
import com.microsoft.azure.management.compute.StorageProfile;
import com.microsoft.azure.management.compute.VirtualHardDisk;
import com.microsoft.azure.management.compute.VirtualMachine;
import com.microsoft.azure.management.compute.VirtualMachineSizeTypes;
import com.microsoft.azure.management.compute.implementation.AvailabilitySetInner;
import com.microsoft.azure.management.compute.implementation.ComputeManagementClientImpl;
import com.microsoft.azure.management.compute.implementation.ComputeManager;
import com.microsoft.azure.management.compute.implementation.ImageReferenceInner;
import com.microsoft.azure.management.compute.implementation.ManagedDiskParametersInner;
import com.microsoft.azure.management.compute.implementation.NetworkInterfaceReferenceInner;
import com.microsoft.azure.management.compute.implementation.OperationStatusResponseInner;
import com.microsoft.azure.management.compute.implementation.VirtualMachineImageResourceInner;
import com.microsoft.azure.management.compute.implementation.VirtualMachineInner;
import com.microsoft.azure.management.network.AddressSpace;
import com.microsoft.azure.management.network.IPAllocationMethod;
import com.microsoft.azure.management.network.implementation.NetworkInterfaceIPConfigurationInner;
import com.microsoft.azure.management.network.implementation.NetworkInterfaceInner;
import com.microsoft.azure.management.network.implementation.NetworkInterfacesInner;
import com.microsoft.azure.management.network.implementation.NetworkManagementClientImpl;
import com.microsoft.azure.management.network.implementation.NetworkSecurityGroupInner;
import com.microsoft.azure.management.network.implementation.NetworkSecurityGroupsInner;
import com.microsoft.azure.management.network.implementation.PublicIPAddressInner;
import com.microsoft.azure.management.network.implementation.PublicIPAddressesInner;
import com.microsoft.azure.management.network.implementation.SubnetInner;
import com.microsoft.azure.management.network.implementation.VirtualNetworkInner;
import com.microsoft.azure.management.network.implementation.VirtualNetworksInner;
import com.microsoft.azure.management.resources.fluentcore.arm.Region;
import com.microsoft.azure.management.resources.fluentcore.model.Creatable;
import com.microsoft.azure.management.resources.fluentcore.model.Indexable;
import com.microsoft.azure.management.resources.implementation.ProviderInner;
import com.microsoft.azure.management.resources.implementation.ResourceGroupInner;
import com.microsoft.azure.management.resources.implementation.ResourceGroupsInner;
import com.microsoft.azure.management.resources.implementation.ResourceManagementClientImpl;
import com.microsoft.azure.management.resources.implementation.SubscriptionClientImpl;
import com.microsoft.azure.management.resources.implementation.SubscriptionInner;
import com.microsoft.azure.management.storage.Kind;
import com.microsoft.azure.management.storage.ProvisioningState;
import com.microsoft.azure.management.storage.SkuName;
import com.microsoft.azure.management.storage.StorageAccountKey;
import com.microsoft.azure.management.storage.implementation.SkuInner;
import com.microsoft.azure.management.storage.implementation.StorageAccountCreateParametersInner;
import com.microsoft.azure.management.storage.implementation.StorageAccountInner;
import com.microsoft.azure.management.storage.implementation.StorageAccountListKeysResultInner;
import com.microsoft.azure.management.storage.implementation.StorageManagementClientImpl;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudPageBlob;
import com.microsoft.rest.ServiceCallback;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.adapters.azure.AzureAsyncCallback;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureInstanceContext.AzureImageSource;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureInstanceContext.AzureNicContext;
import com.vmware.photon.controller.model.adapters.azure.model.diagnostics.AzureDiagnosticSettings;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDecommissionCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback.Default;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureProvisioningCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureRetryHandler;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureSdkClients;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureSecurityGroupUtils;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.BaseAdapterStage;
import com.vmware.photon.controller.model.adapters.util.instance.BaseComputeInstanceContext.ImageSource;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.LifecycleState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.photon.controller.model.resources.ImageService.ImageState.DiskConfiguration;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.IpAssignment;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.security.util.EncryptionUtils;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.FileUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
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

    private static final String VHD_URI_FORMAT = "https://%s.blob.core.windows.net/vhds/%s.vhd";
    private static final String BOOT_DISK_SUFFIX = "-boot-disk";
    private static final String DATA_DISK_SUFFIX = "-data-disk";

    private static final long DEFAULT_EXPIRATION_INTERVAL_MICROS = TimeUnit.MINUTES.toMicros(5);
    private static final int RETRY_INTERVAL_SECONDS = 30;
    private static final long AZURE_MAXIMUM_OS_DISK_SIZE_MB = 1023 * 1024; // Maximum allowed OS disk size on Azure is 1023 GB
    public static final int WINDOWS_COMPUTER_NAME_MAX_LENGTH = 15;

    private ExecutorService executor;

    /**
     * The class represents the context of async calls(either single or batch) to Azure cloud.
     *
     * @see TransitionToCallback
     */
    static class AzureCallContext {

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

        static AzureCallContext newSingleCallContext() {
            return new AzureCallContext(1);
        }

        static AzureCallContext newBatchCallContext(int numberOfCalls) {
            return new AzureCallContext(numberOfCalls);
        }
    }

    @Override
    public void handleStart(Operation start) {
        this.executor = getHost().allocateExecutor(this);
        super.handleStart(start);
    }

    @Override
    public void handleStop(Operation delete) {
        this.executor.shutdown();
        AdapterUtils.awaitTermination(this.executor);
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
        logFine(() -> "Transition to " + nextStage);
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
     * {@code handleAllocation} version suitable for chaining to {@code DeferredResult.whenComplete}
     * .
     */
    private BiConsumer<AzureInstanceContext, Throwable> thenAllocation(AzureInstanceContext ctx,
            AzureInstanceStage next, String namespace) {
        return (ignoreCtx, exc) -> {
            // NOTE: In case of error 'ignoreCtx' is null so use passed context!
            if (exc != null) {
                if (namespace != null) {
                    handleCloudError(String.format("%s: FAILED. Details:", ctx.stage), ctx,
                            namespace, exc);
                } else {
                    handleError(ctx, exc);
                }
            } else {
                handleAllocation(ctx, next);
            }
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
        logInfo("Azure instance management at stage %s for [%s]", ctx.stage, ctx.vmName);
        try {
            switch (ctx.stage) {
            case CLIENT:
                if (ctx.azureSdkClients == null) {
                    ctx.azureSdkClients = new AzureSdkClients(ctx.endpointAuth);
                }
                switch (ctx.computeRequest.requestType) {
                case CREATE:
                    logInfo("Provisioning [%s] for compute %s", ctx.vmName,
                            ctx.resourceReference.getPath());
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
                createResourceGroup(ctx, AzureInstanceStage.INIT_AVAILABILITY_SET);
                break;
            case INIT_AVAILABILITY_SET:
                createAvailabilitySet(ctx, AzureInstanceStage.GET_IMAGE);
                break;
            case GET_IMAGE:
                getImageSource(ctx)
                        .whenComplete(thenAllocation(ctx, AzureInstanceStage.INIT_STORAGE));
                break;
            case INIT_STORAGE:
                createStorageAccount(ctx, AzureInstanceStage.POPULATE_NIC_CONTEXT);
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
                createNICs(ctx, AzureInstanceStage.GENERATE_VM_ID);
                break;
            case GENERATE_VM_ID:
                generateVmId(ctx, AzureInstanceStage.CREATE);
                break;
            case CREATE:
                // Finally provision the VM
                createVM(ctx, AzureInstanceStage.CREATE_PERSISTENT_DISKS);
                break;
            case ENABLE_MONITORING:
                // TODO VSYM-620: Enable monitoring on Azure VMs
                enableMonitoring(ctx, AzureInstanceStage.GET_STORAGE_KEYS);
                break;
            case CREATE_PERSISTENT_DISKS:
                createPersistentDisks(ctx, AzureInstanceStage.UPDATE_COMPUTE_STATE_DETAILS);
                break;
            case UPDATE_COMPUTE_STATE_DETAILS:
                updateComputeStateDetails(ctx, AzureInstanceStage.GET_STORAGE_KEYS);
                break;
            case GET_STORAGE_KEYS:
                getStorageKeys(ctx, AzureInstanceStage.FINISHED);
                break;
            case DELETE:
                deleteVM(ctx, AzureInstanceStage.FINISHED);
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
        } catch (Throwable e) {
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

        SubscriptionClientImpl subscriptionClient = new SubscriptionClientImpl(
                ctx.azureSdkClients.credentials);

        subscriptionClient.subscriptions().getAsync(
                ctx.endpointAuth.userLink, new ServiceCallback<SubscriptionInner>() {
                    @Override
                    public void failure(Throwable e) {
                        // Azure doesn't send us any meaningful status code to work with
                        ServiceErrorResponse rsp = new ServiceErrorResponse();
                        rsp.message = "Invalid Azure credentials";
                        rsp.statusCode = STATUS_CODE_UNAUTHORIZED;
                        ctx.operation.fail(e, rsp);
                    }

                    @Override
                    public void success(SubscriptionInner result) {
                        logFine(() -> String.format("Got subscription %s with id %s",
                                result.displayName(), result.id()));
                        ctx.operation.complete();
                    }
                });
    }

    private void deleteVM(AzureInstanceContext ctx, AzureInstanceStage nextStage) {
        if (ctx.computeRequest.isMockRequest) {
            handleAllocation(ctx, nextStage);
            return;
        }

        String rgName = AzureUtils.getResourceGroupName(ctx);

        if (rgName == null || rgName.isEmpty()) {
            throw new IllegalArgumentException("Resource group name is required");
        }
        ctx.resourceGroupName = rgName;

        DeferredResult.completed(ctx)
                .thenCompose(this::deleteVirtualMachine)
                .thenCompose(this::getAvailabilitySets)
                .thenCompose(this::deleteResourceGroup)
                .thenCompose(this::deleteVHDBlobsForUnmangedDisks)
                .whenComplete(thenAllocation(ctx, nextStage, STORAGE_NAMESPACE));
    }

    private DeferredResult<AzureInstanceContext> getAvailabilitySets(AzureInstanceContext ctx) {
        String msg = "Getting availability sets. ResourceGroup [" + ctx.resourceGroupName + "]";

        ComputeManagementClientImpl computeManager = getComputeManagementClientImpl(ctx);

        // Note that this get operation can throw NullPointerException from the Azure library.
        // Ignore and handle this as a VM delete only. (VCOM-4303)
        AzureRetryHandler<List<AvailabilitySetInner>> getAvailabilitySetsCallback = new
                AzureRetryHandler<List<AvailabilitySetInner>>(null, msg, this) {
                    @Override
                    protected Consumer<AzureAsyncCallback> getAzureAsyncFunction() {
                        return (callback) -> computeManager.availabilitySets()
                                .listByResourceGroupAsync(ctx.resourceGroupName, callback);
                    }

                    @Override
                    protected boolean isSuccess(List<AvailabilitySetInner> availabilitySetInners, Throwable
                            exception) {
                        if (exception != null) {
                            logWarning("getAvailabilitySets caught an exception but ignoring"
                                    + " as this is for delete VM. Exception: " + exception.getMessage());
                        }
                        return true;
                    }
                };

        return getAvailabilitySetsCallback.execute().thenApply(availabilitySetInners -> {
            ctx.availabilitySetInners = availabilitySetInners;
            return ctx;
        });
    }

    private DeferredResult<AzureInstanceContext> deleteVirtualMachine(AzureInstanceContext ctx) {
        String msg = "Deleting virtual machine [" + ctx.vmName + "]";
        ComputeManagementClientImpl computeManager = getComputeManagementClientImpl(ctx);

        AzureRetryHandler<OperationStatusResponseInner> deleteVirtualMachineCallback = new
                AzureRetryHandler<OperationStatusResponseInner>(msg, this) {
                    @Override
                    protected Consumer<AzureAsyncCallback> getAzureAsyncFunction() {
                        return (callback) -> computeManager.virtualMachines()
                                .deleteAsync(ctx.resourceGroupName, ctx.vmName, callback);
                    }

                    @Override
                    protected boolean isSuccess(OperationStatusResponseInner object,
                            Throwable exception) {
                        if (exception != null && exception instanceof CloudException) {
                            CloudError cloudError = ((CloudException) exception).body();
                            if (cloudError != null && cloudError.code().contains("NotFound")) {
                                logWarning(String.format("Delete VM failed as VM or resource "
                                                + "[resource: %s] [vm: %s] was not found. "
                                                + "Ignoring exception and carrying on. Exception:"
                                                + " %s", ctx.resourceGroupName, ctx.vmName,
                                        exception.getMessage()));
                                return true;
                            }
                        }
                        return super.isSuccess(object, exception);
                    }
                };

        return deleteVirtualMachineCallback.execute().thenApply(result -> ctx);
    }

    private DeferredResult<AzureInstanceContext> deleteResourceGroup(AzureInstanceContext ctx) {
        String msg = "Deleting resource group [" + ctx.resourceGroupName + "]";

        if (ctx.availabilitySetInners == null ||
                ctx.availabilitySetInners.size() == 0 ||
                ctx.availabilitySetInners.get(0).virtualMachines() == null ||
                ctx.availabilitySetInners.get(0).virtualMachines().size() == 0) {
            // there are no other machine in this cluster,  delete the resource group
            ResourceGroupsInner resourceGroups = getResourceManagementClientImpl(ctx)
                    .resourceGroups();

            AzureRetryHandler<Void> deleteResourceGroupCallback =
                    new AzureRetryHandler<Void>(msg, this) {
                        @Override
                        protected Consumer<AzureAsyncCallback> getAzureAsyncFunction() {
                            return (callback) -> resourceGroups.deleteAsync(ctx.resourceGroupName, callback);
                        }

                        @Override
                        protected boolean isSuccess(Void object, Throwable exception) {
                            if (exception != null && exception instanceof CloudException) {
                                CloudError cloudError = ((CloudException) exception).body();
                                if (cloudError != null && cloudError.code().contains("NotFound")) {
                                    logWarning(String.format("Delete resource group failed "
                                            + "as resource [%s] was not found. "
                                            + "Ignoring exception and carrying on. Exception: %s",
                                            ctx.resourceGroupName, exception.getMessage()));
                                    return true;
                                }
                            }
                            return super.isSuccess(object, exception);
                        }

                    };

            return deleteResourceGroupCallback.execute()
                    .thenApply(result -> {
                        logInfo("Successfully deleted resource group: "
                                + ctx.resourceGroupName);
                        return ctx;
                    });
        }

        return DeferredResult.completed(ctx);
    }

    private DeferredResult<AzureInstanceContext> deleteVHDBlobsForUnmangedDisks(
            AzureInstanceContext ctx) {

        return fetchDiskStatesToDelete(ctx)
                .thenCompose(this::getStorageAccountKeysForUnManagedDisks)
                .thenCompose(this::deleteVHDBlobsInAzure)
                .exceptionally(throwable -> {
                    logWarning("Unable to delete VHD blobs for [%s]. Probably the Storage account does not exist or was not created.", ctx.vmName);
                    return null;
                })
                .thenApply(ignore -> ctx);
    }

    private DeferredResult<AzureInstanceContext> getStorageAccountKeysForUnManagedDisks(
            AzureInstanceContext ctx) {

        //short circuit for managed disks
        if (isManagedDisk().test(ctx.bootDiskState)) {
            return DeferredResult.completed(ctx);
        }

        DeferredResult<AzureInstanceContext> dr = new DeferredResult<>();
        List<Operation> fetchAuthCredsForStorageAccount = new ArrayList<>();

        fetchAuthCredsForStorageAccount.add(
                Operation.createGet(
                        UriUtils.buildExpandLinksQueryUri(
                                createInventoryUri(
                                        getHost(),
                                        ctx.bootDiskState.storageDescription.authCredentialsLink)))
                        .setReferer(getUri()));

        for (DiskService.DiskStateExpanded disk : ctx.dataDiskStates) {
            //add null check as all disk will not have it
            if (disk.storageDescription != null
                    && disk.storageDescription.authCredentialsLink != null) {
                fetchAuthCredsForStorageAccount.add(
                        Operation.createGet(
                                UriUtils.buildExpandLinksQueryUri(UriUtils.buildUri(getHost(),
                                        disk.storageDescription.authCredentialsLink)))
                                .setReferer(getUri()));
            }
        }

        OperationJoin.create(fetchAuthCredsForStorageAccount).setCompletion((ops, exc) -> {
            if (exc == null || exc.size() == 0) {
                ctx.storageAccountKeysForDisks = new HashMap<>();
                for (Operation authGet : ops.values()) {
                    AuthCredentialsServiceState auth = authGet
                            .getBody(AuthCredentialsServiceState.class);
                    ctx.storageAccountKeysForDisks.put(auth.documentSelfLink,
                            auth.customProperties.get(AZURE_STORAGE_ACCOUNT_KEY1));
                }
                dr.complete(ctx);

            } else {
                dr.fail(new Throwable(String.format(
                        "Unable to get storage account keys for disks. FAILED with: %s",
                        Utils.toString(exc))));
            }
        }).sendWith(getHost());

        return dr;
    }

    private DeferredResult<Void> deleteVHDBlobsInAzure(AzureInstanceContext ctx) {

        if (isManagedDisk().test(ctx.bootDiskState)) {
            return DeferredResult.completed(null);
        }

        String defaultSAName = ctx.bootDiskState.storageDescription.name;
        String defaultSAKey = ctx.storageAccountKeysForDisks
                .get(ctx.bootDiskState.storageDescription.authCredentialsLink);

        List<DiskService.DiskStateExpanded> diskStatesToDelete = ctx.dataDiskStates.stream()
                .filter(disk -> disk.persistent == false)
                .collect(Collectors.toList());

        if (ctx.bootDiskState.persistent == false) {
            diskStatesToDelete.add(ctx.bootDiskState);
        }

        OperationContext opCtx = OperationContext.getOperationContext();

        // Create DR
        DeferredResult<Void> dr = new DeferredResult<>();

        CompletableFuture.runAsync(() -> {
            diskStatesToDelete.forEach(disk -> {
                try {
                    CloudStorageAccount storageAccountOfVHDBlob;
                    if (disk.storageDescription == null) {
                        storageAccountOfVHDBlob = CloudStorageAccount.parse(
                                String.format(STORAGE_CONNECTION_STRING, defaultSAName,
                                        defaultSAKey));

                    } else {
                        String key = ctx.storageAccountKeysForDisks
                                .get(disk.storageDescription.authCredentialsLink);
                        storageAccountOfVHDBlob = CloudStorageAccount.parse(
                                String.format(STORAGE_CONNECTION_STRING,
                                        disk.storageDescription.name, key));
                    }

                    CloudBlobClient client = storageAccountOfVHDBlob.createCloudBlobClient();
                    CloudBlobContainer container = client.getContainerReference("vhds");
                    String vhdBlobName = disk.id.substring(disk.id.lastIndexOf("/") + 1);
                    CloudPageBlob blob = container.getPageBlobReference(vhdBlobName);
                    blob.deleteIfExists();
                } catch (URISyntaxException | InvalidKeyException | StorageException e) {
                    logSevere("Unable to delete VHD file for disk [%s] because of %s ", disk.name,
                            e.toString());
                }
            });

        }, this.executor).whenComplete((obj, throwable) -> {
            OperationContext.restoreOperationContext(opCtx);
            dr.complete(obj);
        });

        return dr;
    }

    private DeferredResult<AzureInstanceContext> fetchDiskStatesToDelete(AzureInstanceContext ctx) {

        //fetch ComputeState and Get disk states

        DeferredResult<AzureInstanceContext> dr = new DeferredResult<>();

        Operation op = Operation.createGet(ctx.computeRequest.resourceReference);
        op.setCompletion((o, e) -> {

            if (e != null) {
                dr.fail(new Throwable(
                        "Unable to fetch compute state for deleted VM. " + e.getMessage()));
                return;
            }

            ComputeState cs = o.getBody(ComputeState.class);
            List<Operation> fetchAllVMDisks = new ArrayList<>();
            for (String diskLink : cs.diskLinks) {
                fetchAllVMDisks.add(Operation
                        .createGet(UriUtils.buildExpandLinksQueryUri(UriUtils.buildUri(getHost(),
                                diskLink))).setReferer(getUri()));

            }
            OperationJoin.create(fetchAllVMDisks).setCompletion((ops, exc) -> {
                        if (exc == null || exc.size() == 0) {
                            //assign boot disk and data disk to context
                            ctx.dataDiskStates = new ArrayList<>();
                            for (Operation diskGet : ops.values()) {
                                DiskService.DiskStateExpanded disk = diskGet.getBody(
                                        DiskService.DiskStateExpanded.class);
                                if (disk.bootOrder != null && disk.bootOrder == 1) {
                                    ctx.bootDiskState = disk;
                                } else {
                                    ctx.dataDiskStates.add(disk);
                                }
                            }
                            dr.complete(ctx);
                        } else {
                            String stacktrace = Utils.toString(exc);
                            dr.fail(new Throwable(String.format(
                                    "Unable to fetch disks to delete for %s. FAILED with: %s",
                                    cs.name,
                                    stacktrace)));
                            return;
                        }
                    }
            ).sendWith(getHost());
        });
        sendRequest(op);

        return dr;
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

        String rgName = ctx.resourceGroup.name();

        String msg = "Rollback provisioning for [" + ctx.vmName + "] Azure VM";

        ResourceGroupsInner azureClient = getResourceManagementClientImpl(ctx)
                .resourceGroups();

        AzureDecommissionCallback callback = new AzureDecommissionCallback(
                this, msg) {

            @Override
            protected DeferredResult<Void> consumeDecommissionSuccess(Void body) {
                return DeferredResult.completed(body);
            }

            @Override
            protected Throwable consumeError(Throwable e) {
                String rollbackError = String.format(msg + ": FAILED. Details: %s",
                        Utils.toString(e));

                if (ctx.error != null) {
                    rollbackError = rollbackError + " : Original Provisioning Error : " + Utils.toString(ctx.error);
                }

                // Wrap original ctx.error with rollback error details.
                ctx.error = new IllegalStateException(rollbackError, ctx.error);

                return RECOVERED;
            }

            @Override
            protected Runnable checkExistenceCall(ServiceCallback<Boolean> checkExistenceCallback) {
                return () -> azureClient.checkExistenceAsync(rgName, checkExistenceCallback);
            }
        };

        azureClient.deleteAsync(rgName, callback);

        callback.toDeferredResult().whenComplete((o, e) -> finishWithFailure(ctx));
    }

    private void finishWithFailure(AzureInstanceContext ctx) {
        // Report the error back to the caller
        ctx.taskManager.patchTaskToFailure(ctx.error);

        ctx.close();
    }

    private void finishWithSuccess(AzureInstanceContext ctx) {
        // Report the success back to the caller
        ctx.taskManager.finishTask();

        ctx.close();
    }

    private void createResourceGroup(AzureInstanceContext ctx, AzureInstanceStage nextStage) {

        String resourceGroupName = AzureUtils.getResourceGroupName(ctx);

        ResourceGroupInner resourceGroup = new ResourceGroupInner();
        resourceGroup.withLocation(ctx.child.description.regionId);

        String msg = "Creating Azure Resource Group [" + resourceGroupName + "] for [" + ctx.vmName
                + "] VM";

        getResourceManagementClientImpl(ctx).resourceGroups().createOrUpdateAsync(
                resourceGroupName,
                resourceGroup,
                new TransitionToCallback<ResourceGroupInner>(ctx, nextStage, msg) {
                    @Override
                    CompletionStage<ResourceGroupInner> handleSuccess(ResourceGroupInner rg) {

                        this.ctx.resourceGroup = rg;
                        return CompletableFuture.completedFuture(rg);
                    }
                });
    }

    private void createAvailabilitySet(AzureInstanceContext ctx, AzureInstanceStage nextStage) {

        String availabilitySetName = AzureUtils.getAvailabilitySetName(ctx);

        String msg =
                "Creating Azure Availability Set [" + availabilitySetName + "] for [" + ctx.vmName
                        + "] VM";

        AvailabilitySetSkuTypes skuType = ctx.useManagedDisks() ?
                AvailabilitySetSkuTypes.MANAGED : AvailabilitySetSkuTypes.UNMANAGED;

        AvailabilitySet.DefinitionStages.WithCreate availabilitySetDefinition = ctx.azureSdkClients
                .getComputeManager()
                .availabilitySets()
                .define(availabilitySetName)
                .withRegion(ctx.child.description.regionId)
                .withExistingResourceGroup(ctx.resourceGroup.name())
                .withSku(skuType);

        AzureRetryHandler<AvailabilitySet> callbackHandler = new
                AzureRetryHandler<AvailabilitySet>(msg, this) {
            @Override
            protected Consumer<AzureAsyncCallback> getAzureAsyncFunction() {
                return (callback) -> availabilitySetDefinition.createAsync(callback);
            }
        };

        callbackHandler.execute()
                .thenApply(availabilitySet -> {
                    logInfo("Successfully created AvailabilitySet: " + availabilitySet.name());
                    ctx.availabilitySet = availabilitySet;
                    return ctx;
                })
                .whenComplete(thenAllocation(ctx, nextStage, NETWORK_NAMESPACE));
    }

    private void createPersistentDisks(AzureInstanceContext ctx, AzureInstanceStage nextStage) {

        // This flow is not applicable for un-managed disk so move to next stage
        if (!ctx.useManagedDisks()) {
            handleAllocation(ctx, nextStage);
            return;
        }

        // In case of No Persistent disks return and move on.
        if (ctx.dataDiskStates.stream().filter(diskStateExpanded ->
                diskStateExpanded.persistent).count() == 0) {
            handleAllocation(ctx, nextStage);
            return;
        }

        DeferredResult.completed(ctx)
                .thenCompose(this::createRGForPersistentDisks)
                .thenCompose(this::createDisks)
                .thenCompose(this::getAzureVirtualMachine)
                .thenCompose(this::attachPersistentDisksToVM)
                .whenComplete(thenAllocation(ctx, nextStage, AzureConstants.AZURE_STORAGE_DISKS));

    }

    private DeferredResult<AzureInstanceContext> createRGForPersistentDisks(AzureInstanceContext
            ctx) {
        // create a new resource group for persistent disks that will survive VM deletion
        DeferredResult<AzureInstanceContext> dr = new DeferredResult<>();

        // RG name derived from that of the compute
        ctx.rgNameForPersistentDisks = AzureUtils.getResourceGroupName(ctx) + "_persist";

        rx.Observable<Indexable> ob = getComputeManager(ctx).resourceManager().resourceGroups()
                .define(ctx.rgNameForPersistentDisks)
                .withRegion(ctx.child.description.regionId)
                .createAsync();

        ob.doOnCompleted(AzureUtils.injectOperationContext(() -> {
            getHost().log(Level.INFO, "Created New RG for persistent disk [" + ctx
                    .rgNameForPersistentDisks + "]");
            dr.complete(ctx);
        })).doOnError(AzureUtils.injectOperationContext(dr::fail))
                .publish()
                .connect();
        return dr;
    }

    private DeferredResult<AzureInstanceContext> createDisks(AzureInstanceContext
            ctx) {
        // Iterate over managed disk that have the persistent flag set
        DeferredResult<AzureInstanceContext> dr = new DeferredResult<>();
        List<Creatable<Disk>> disksToCreate = ctx.dataDiskStates.stream()
                .filter(isManagedDisk())
                .filter(diskStateExpanded -> diskStateExpanded.persistent)
                .map(diskState -> defineDisk(diskState, getComputeManager(ctx),
                        ctx.rgNameForPersistentDisks,
                        Region.fromName(ctx.child.description.regionId)))
                .collect(Collectors.toList());

        rx.Observable<Indexable> ob = getComputeManager(ctx).disks().createAsync(disksToCreate);
        ob.doOnNext(AzureUtils.injectOperationContext(resource -> {
            if (resource instanceof Disk) {
                //Store the azure data disk in context. Need it to attach it to the VM later.
                getHost().log(Level.INFO,
                        "Created persistent disk [" + ((Disk) resource).name() + "]");
                ctx.persistentDisks.add((Disk) resource);
            }
        })).doOnCompleted(AzureUtils.injectOperationContext(() -> {
            // handle completion
            getHost().log(Level.INFO, "Created persistent " + ctx.persistentDisks.size() + " "
                    + "disks in Resource Group [" + ctx.rgNameForPersistentDisks + "]");
            dr.complete(ctx);
        })).doOnError(AzureUtils.injectOperationContext(dr::fail))
                .publish()
                .connect();

        return dr;
    }

    private DeferredResult<AzureInstanceContext> attachPersistentDisksToVM(AzureInstanceContext
            ctx) {
        // Attach all the persistent disks to the VM
        DeferredResult<AzureInstanceContext> dr = new DeferredResult<>();

        VirtualMachine.Update updateVM = ctx.virtualMachine.update();
        for (Disk disk : ctx.persistentDisks) {
            updateVM = updateVM.withExistingDataDisk(disk);
        }
        rx.Observable observable = updateVM.applyAsync();
        observable.doOnNext(resource -> {
            if (resource instanceof VirtualMachine) {
                //update both the inner VM and the VirtualMachine objects in context
                ctx.virtualMachine = (VirtualMachine) resource;
                ctx.provisionedVm = ((VirtualMachine) resource).inner();
            }
        }).doOnCompleted(AzureUtils.injectOperationContext(() -> {
            // Done with attaching disks. Completing DR.
            getHost().log(Level.INFO, "Attached persistent " + ctx.persistentDisks.size() + " disks"
                    + " to vm " + ctx.provisionedVm.name());
            dr.complete(ctx);
        })).doOnError(AzureUtils.injectOperationContext(dr::fail))
                .publish()
                .connect();

        return dr;

    }

    /**
     * Method to fetch Azure VM reference. Not the inner object that we have in provisionedVM
     */
    private DeferredResult<AzureInstanceContext> getAzureVirtualMachine(
            AzureInstanceContext ctx) {

        final String msg = "Getting Virtual Machine Object for details for [" + ctx.vmName +
                "]" + " with ID " + ctx.vmId;

        AzureDeferredResultServiceCallback<VirtualMachine> handler = new AzureDeferredResultServiceCallback<VirtualMachine>(
                this, msg) {

            @Override
            protected DeferredResult<VirtualMachine> consumeSuccess(VirtualMachine vm) {
                ctx.virtualMachine = vm;
                return DeferredResult.completed(vm);
            }
        };

        getComputeManager(ctx).virtualMachines().getByIdAsync(ctx.vmId, handler);

        return handler.toDeferredResult().thenApply(virtualMachine -> ctx);
    }

    private Creatable<Disk> defineDisk(DiskService.DiskStateExpanded diskState, ComputeManager
            computeManager, String resourceGroupName, Region region) {

        return computeManager.disks().define(diskState.name)
                .withRegion(region)
                .withExistingResourceGroup(resourceGroupName)
                .withData()
                .withSizeInGB((int) diskState.capacityMBytes / 1024); //Convert to GBs
    }

    private void createStorageAccount(AzureInstanceContext ctx, AzureInstanceStage nextStage) {

        // First create SA RG (if does not exist), then create the SA itself (if does not exist)
        DeferredResult.completed(ctx)
                .thenCompose(this::createStorageAccountRG)
                .thenCompose(this::createStorageAccount)
                .whenComplete(thenAllocation(ctx, nextStage, STORAGE_NAMESPACE));
    }

    /**
     * Init storage account name and resource group, using the following approach:
     * <table border=1>
     * <tr>
     * <th>AZURE_STORAGE_ACCOUNT_NAME</th>
     * <th>AZURE_STORAGE_ACCOUNT_RG_NAME</th>
     * <th>Used Parameter</th>
     * </tr>
     * <tr>
     * <td>provided</td>
     * <td>provided</td>
     * <td>SA name = AZURE_STORAGE_ACCOUNT_NAME<br>
     * SA RG name = AZURE_STORAGE_ACCOUNT_RG_NAME</td>
     * </tr>
     * <tr>
     * <td>provided</td>
     * <td>not provided</td>
     * <td>SA name = AZURE_STORAGE_ACCOUNT_NAME<br>
     * SA RG name = AZURE_STORAGE_ACCOUNT_DEFAULT_RG_NAME</td>
     * </tr>
     * <tr>
     * <td>not provided</td>
     * <td>provided</td>
     * <td>SA name = generated name<br>
     * SA RG name = ctx.resourceGroup.getName()</td>
     * </tr>
     * <tr>
     * <td>not provided</td>
     * <td>not provided</td>
     * <td>SA name = generated name<br>
     * SA RG name = ctx.resourceGroup.getName()</td>
     * </tr>
     * </table>
     */
    private DeferredResult<AzureInstanceContext> createStorageAccountRG(AzureInstanceContext ctx) {

        // No need for resource group for storage account.
        // Either we are reusing an existing storage account or using managed disks.
        if (ctx.reuseExistingStorageAccount() || ctx.useManagedDisks()) {
            return DeferredResult.completed(ctx);
        } else {
            ctx.storageAccountName = ctx.bootDiskState.customProperties
                    .get(AZURE_STORAGE_ACCOUNT_NAME);
        }

        ctx.storageAccountRGName = ctx.bootDiskState.customProperties
                .getOrDefault(AZURE_STORAGE_ACCOUNT_RG_NAME, AZURE_STORAGE_ACCOUNT_DEFAULT_RG_NAME);

        if (ctx.storageAccountName == null) {
            // In case SA is not provided in the request, use request VA resource group
            ctx.storageAccountName = String.valueOf(System.currentTimeMillis()) + "st";
            ctx.storageAccountRGName = ctx.resourceGroup.name();

            return DeferredResult.completed(ctx);
        }

        String msg = "Create/Update SA Resource Group [" + ctx.storageAccountRGName + "] for ["
                + ctx.vmName + "] VM";

        AzureDeferredResultServiceCallback<ResourceGroupInner> handler = new Default<>(this, msg);

        // Use shared RG. In case not provided in the bootDisk properties, use the default one
        final ResourceGroupInner sharedSARG = new ResourceGroupInner();
        sharedSARG.withLocation(ctx.child.description.regionId);

        getResourceManagementClientImpl(ctx)
                .resourceGroups()
                .createOrUpdateAsync(ctx.storageAccountRGName, sharedSARG, handler);

        return handler.toDeferredResult().thenApply(ignore -> ctx);
    }

    private DeferredResult<AzureInstanceContext> createStorageAccount(AzureInstanceContext ctx) {

        if (ctx.reuseExistingStorageAccount() || ctx.useManagedDisks()) {
            //no need to create a storage account
            logInfo("Not Creating any new storage Account. Reusing existing ones or using managed"
                    + " disks.");
            return DeferredResult.completed(ctx);
        }

        String msg = "Create Azure Storage Account [" + ctx.storageAccountName + "] for ["
                + ctx.vmName + "] VM";

        StorageAccountCreateParametersInner storageParameters = new StorageAccountCreateParametersInner();
        storageParameters.withLocation(ctx.child.description.regionId);
        storageParameters.withSku(new SkuInner().withName(SkuName.STANDARD_LRS));
        storageParameters.withKind(Kind.STORAGE);

        AzureRetryHandler<StorageAccountInner> createHandler = new
                AzureRetryHandler<StorageAccountInner>(msg, this){
                    @Override
                    protected Consumer<AzureAsyncCallback> getAzureAsyncFunction() {
                        return (callback) -> getStorageManagementClientImpl(ctx).storageAccounts().createAsync(
                                ctx.storageAccountRGName,
                                ctx.storageAccountName,
                                storageParameters,
                                callback);
                    }

                    @Override
                    protected boolean isSuccess(StorageAccountInner object, Throwable exception) {
                        if (exception != null) {
                            String azureErrorCode = getAzureErrorCode(exception);
                            if (azureErrorCode.equalsIgnoreCase(STORAGE_ACCOUNT_ALREADY_EXIST)) {
                                return true;
                            }
                        }
                        return super.isSuccess(object, exception);
                    }
                };

        AzureRetryHandler<StorageAccountInner> getHandler = new
                AzureRetryHandler<StorageAccountInner>(msg, this){
                    @Override
                    protected Consumer<AzureAsyncCallback> getAzureAsyncFunction() {
                        return (callback) -> getStorageManagementClientImpl(ctx)
                                .storageAccounts()
                                .getByResourceGroupAsync(
                                        ctx.storageAccountRGName,
                                        ctx.storageAccountName,
                                        callback);
                    }

                    @Override
                    protected boolean isSuccess(StorageAccountInner storageAccountInner, Throwable exception) {
                        if (!super.isSuccess(storageAccountInner, exception)) {
                            return false;
                        }

                        ProvisioningState provisioningState = storageAccountInner.provisioningState();
                        if (provisioningState == null) {
                            return false;
                        }

                        if (PROVISIONING_STATE_SUCCEEDED.equalsIgnoreCase(provisioningState.name())) {
                            return true;
                        }

                        return false;
                    }
                };


        return createHandler.execute()
                .thenCompose(storageAccountInnerCreate -> {
                    return getHandler.execute();
                })
                .thenCompose(storageAccountInnerGet -> {
                    ctx.storageAccount = storageAccountInnerGet;

                    return createStorageDescription(ctx)
                            // Start next op, patch boot disk, in the sequence
                            .thenCompose(woid -> {
                                URI uri = ctx.computeRequest.buildUri(
                                        ctx.bootDiskState.documentSelfLink);

                                Operation patchBootDiskOp = Operation
                                        .createPatch(uri)
                                        .setBody(ctx.bootDiskState);

                                return ctx.service.sendWithDeferredResult(patchBootDiskOp)
                                        .thenRun(() -> ctx.service.logFine(
                                                () -> String
                                                        .format("Updating boot disk [%s]: SUCCESS",
                                                                ctx.bootDiskState.name)));
                            })
                            // Return processed context with StorageAccount
                            .thenApply(woid -> ctx.storageAccount);
                })
                // Return processed context with StorageAccount
                .thenApply(ignore -> ctx);
    }

    private void createNetworkIfNotExist(AzureInstanceContext ctx, AzureInstanceStage nextStage) {
        if (ctx.nics.isEmpty()) {
            handleAllocation(ctx, nextStage);
            return;
        }

        // Get NICs with not existing subnets
        List<SubnetInner> subnetsToCreate = ctx.nics.stream()
                .filter(nicCtx -> nicCtx.subnet == null)
                .map(this::newAzureSubnet)
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
            List<SubnetInner> subnetsToCreate) {

        // All NICs MUST be at the same vNet (no cross vNet VMs),
        // so we select the Primary vNet.
        final AzureNicContext primaryNic = ctx.getPrimaryNic();

        final VirtualNetworkInner vNetToCreate = newAzureVirtualNetwork(
                ctx, primaryNic, subnetsToCreate);

        final String vNetName = primaryNic.networkState.name;

        final String vNetRGName = primaryNic.networkRGState != null
                ? primaryNic.networkRGState.name
                : ctx.resourceGroup.name();

        VirtualNetworksInner azureClient = getNetworkManagementClientImpl(ctx)
                .virtualNetworks();

        final String subnetNames = vNetToCreate.subnets().stream()
                .map(SubnetInner::name)
                .collect(Collectors.joining(","));

        final String msg = "Creating Azure vNet-Subnet [v=" + vNetName + "; s="
                + subnetNames
                + "] for ["
                + ctx.vmName + "] VM";

        AzureRetryHandler<VirtualNetworkInner> createCallbackHandler = new
                AzureRetryHandler<VirtualNetworkInner>(msg, this) {
            @Override
            protected Consumer<AzureAsyncCallback> getAzureAsyncFunction() {
                return (callback) -> azureClient.createOrUpdateAsync(vNetRGName, vNetName,
                        vNetToCreate, callback);
            }
        };

        AzureRetryHandler<VirtualNetworkInner> getCallbackHandler = new
                AzureRetryHandler<VirtualNetworkInner>(msg, this) {
            @Override
            protected Consumer<AzureAsyncCallback> getAzureAsyncFunction() {
                return (callback) -> azureClient.createOrUpdateAsync(vNetRGName, vNetName,
                        vNetToCreate, callback);
            }

            @Override
            protected boolean isSuccess(VirtualNetworkInner vNet, Throwable exception) {
                if (!super.isSuccess(vNet, exception)) {
                    return false;
                }

                // Return first NOT Succeeded state,
                // or PROVISIONING_STATE_SUCCEEDED if all are Succeeded
                if (vNet.subnets().size() == 0) {
                    return false;
                }
                String subnetPS = vNet.subnets().stream()
                        .map(SubnetInner::provisioningState)
                        // Get if any is NOT Succeeded...
                        .filter(ps -> !PROVISIONING_STATE_SUCCEEDED.equalsIgnoreCase(ps))
                        // ...and return it.
                        .findFirst()
                        // Otherwise consider all are Succeeded
                        .orElse(PROVISIONING_STATE_SUCCEEDED);

                if (PROVISIONING_STATE_SUCCEEDED.equalsIgnoreCase(vNet.provisioningState())
                        && PROVISIONING_STATE_SUCCEEDED.equalsIgnoreCase(subnetPS)) {
                    return true;
                }
                return false;
            }
        };

        createCallbackHandler.execute()
                .thenCompose(vNetCreate -> {
                    return getCallbackHandler.execute().thenApply(vNetGet -> {
                        for (AzureNicContext nicCtx : ctx.nics) {
                            if (nicCtx.subnet == null) {
                                Optional<SubnetInner> firstFoundSubnet = vNetGet.subnets().stream()
                                        .filter(subnet -> subnet.name()
                                                .equals(nicCtx.subnetState.name))
                                        .findFirst();
                                if (firstFoundSubnet.isPresent()) {
                                    nicCtx.subnet = firstFoundSubnet.get();
                                } else {
                                    continue;
                                }
                            }
                        }
                        return ctx;
                    });
                })
                .whenComplete(thenAllocation(ctx, nextStage, NETWORK_NAMESPACE));
    }

    /**
     * Converts Photon model constructs to underlying Azure VirtualNetwork model.
     */
    private VirtualNetworkInner newAzureVirtualNetwork(
            AzureInstanceContext ctx,
            AzureNicContext nicCtx,
            List<SubnetInner> subnetsToCreate) {

        VirtualNetworkInner vNet = new VirtualNetworkInner();
        vNet.withLocation(ctx.resourceGroup.location());

        // Azure's custom serializers don't handle well collections constructed with
        // Collections.singletonList(), so initialize an ArrayList
        List<String> prefix = new ArrayList<>();
        prefix.add(nicCtx.networkState.subnetCIDR);

        AddressSpace addressSpace = new AddressSpace()
                .withAddressPrefixes(prefix);
        vNet.withAddressSpace(addressSpace);

        vNet.withSubnets(subnetsToCreate);

        return vNet;
    }

    /**
     * Converts Photon model constructs to underlying Azure VirtualNetwork-Subnet model.
     */
    private SubnetInner newAzureSubnet(AzureNicContext nicCtx) {

        SubnetInner subnet = new SubnetInner();
        subnet.withName(nicCtx.subnetState.name);
        subnet.withAddressPrefix(nicCtx.subnetState.subnetCIDR);

        return subnet;
    }

    private void createPublicIPs(AzureInstanceContext ctx, AzureInstanceStage nextStage) {
        if (ctx.nics.isEmpty()) {
            handleAllocation(ctx, nextStage);
            return;
        }

        AzureNicContext nicCtx = ctx.getPrimaryNic();

        // For now if not specified default to TRUE!
        if (nicCtx.nicStateWithDesc.description.assignPublicIpAddress == null) {
            nicCtx.nicStateWithDesc.description.assignPublicIpAddress = Boolean.TRUE;
        }

        if (nicCtx.nicStateWithDesc.description.assignPublicIpAddress == Boolean.FALSE) {
            // Do nothing in this method -> proceed to next stage.
            handleAllocation(ctx, nextStage);
            return;
        }

        PublicIPAddressesInner azureClient = getNetworkManagementClientImpl(ctx)
                .publicIPAddresses();

        final PublicIPAddressInner publicIPAddress = newAzurePublicIPAddress(ctx, nicCtx);

        final String publicIPName = ctx.vmName + "-pip";
        final String publicIPRGName = ctx.resourceGroup.name();

        String msg = "Creating Azure Public IP [" + publicIPName + "] for [" + ctx.vmName + "] VM";

        AzureProvisioningCallback<PublicIPAddressInner> handler = new AzureProvisioningCallback<PublicIPAddressInner>(
                this, msg) {
            @Override
            protected DeferredResult<PublicIPAddressInner> consumeProvisioningSuccess(
                    PublicIPAddressInner publicIP) {
                nicCtx.publicIP = publicIP;

                return DeferredResult.completed(publicIP);
            }

            @Override
            protected String getProvisioningState(PublicIPAddressInner publicIP) {
                return publicIP.provisioningState();
            }

            @Override
            protected Runnable checkProvisioningStateCall(
                    ServiceCallback<PublicIPAddressInner> checkProvisioningStateCallback) {
                return () -> azureClient.getByResourceGroupAsync(
                        publicIPRGName,
                        publicIPName,
                        null /* expand */,
                        checkProvisioningStateCallback);
            }
        };

        azureClient.createOrUpdateAsync(publicIPRGName, publicIPName, publicIPAddress, handler);

        handler.toDeferredResult()
                .thenApply(ignore -> ctx)
                .whenComplete(thenAllocation(ctx, nextStage, NETWORK_NAMESPACE));
    }

    /**
     * Converts Photon model constructs to underlying Azure PublicIPAddress model.
     */
    private PublicIPAddressInner newAzurePublicIPAddress(
            AzureInstanceContext ctx,
            AzureNicContext nicCtx) {

        PublicIPAddressInner publicIPAddress = new PublicIPAddressInner();
        publicIPAddress.withLocation(ctx.resourceGroup.location());
        publicIPAddress
                .withPublicIPAllocationMethod(new IPAllocationMethod(
                        nicCtx.nicStateWithDesc.description.assignment.name()));

        return publicIPAddress;
    }

    private void createSecurityGroupsIfNotExist(AzureInstanceContext ctx,
            AzureInstanceStage nextStage) {

        if (ctx.nics.isEmpty()) {
            handleAllocation(ctx, nextStage);
            return;
        }

        NetworkSecurityGroupsInner azureClient = getNetworkManagementClientImpl(ctx)
                .networkSecurityGroups();

        List<DeferredResult<NetworkSecurityGroupInner>> createSGDR = ctx.nics.stream()

                // Security Group is requested but no existing security group is mapped.
                .filter(nicCtx -> nicCtx.securityGroupState() != null
                        && nicCtx.securityGroup == null)

                .map(nicCtx -> {
                    SecurityGroupState sgState = nicCtx.securityGroupState();

                    String rgName = nicCtx.securityGroupRGState != null
                            ? nicCtx.securityGroupRGState.name
                            : ctx.resourceGroup.name();

                    String msg = "Create Azure Security Group ["
                            + rgName + "/" + sgState.name
                            + "] for [" + nicCtx.nicStateWithDesc.name + "] NIC for ["
                            + ctx.vmName
                            + "] VM";

                    return AzureSecurityGroupUtils.createSecurityGroup(this, azureClient,
                            sgState, rgName, ctx.resourceGroup.location(), msg)
                            .thenCompose(sg -> {
                                String addMsg = "Add Azure Security Rules to Group ["
                                        + rgName + "/" + sgState.name
                                        + "] for [" + nicCtx.nicStateWithDesc.name + "] NIC for ["
                                        + ctx.vmName + "] VM";
                                return AzureSecurityGroupUtils.addSecurityRules(this,
                                        azureClient, sgState, rgName, sg, addMsg);
                            })
                            .thenApply(updatedSG -> {
                                nicCtx.securityGroup = updatedSG;
                                return updatedSG;
                            });
                })

                .collect(Collectors.toList());

        DeferredResult.allOf(createSGDR).whenComplete((all, exc) -> {
            if (exc != null) {
                handleError(ctx, exc);
            } else {
                handleAllocation(ctx, nextStage);
            }
        });
    }

    private void createNICs(AzureInstanceContext ctx, AzureInstanceStage nextStage) {

        if (ctx.nics.isEmpty()) {
            handleAllocation(ctx, nextStage);
            return;
        }

        // Shared state between multi async calls {{
        AzureCallContext callContext = AzureCallContext.newBatchCallContext(ctx.nics.size());
        NetworkInterfacesInner azureClient = getNetworkManagementClientImpl(ctx)
                .networkInterfaces();
        // }}

        for (AzureNicContext nicCtx : ctx.nics) {
            final String nicName = nicCtx.nicStateWithDesc.name;

            final NetworkInterfaceInner nic = newAzureNetworkInterface(ctx, nicCtx);

            String msg = "Creating Azure NIC [" + nicName + "] for [" + ctx.vmName + "] VM";

            azureClient.createOrUpdateAsync(
                    ctx.resourceGroup.name(),
                    nicName,
                    nic,
                    new TransitionToCallback<NetworkInterfaceInner>(ctx, nextStage, callContext,
                            msg) {
                        @Override
                        protected CompletionStage<NetworkInterfaceInner> handleSuccess(
                                NetworkInterfaceInner nic) {
                            nicCtx.nic = nic;
                            return CompletableFuture.completedFuture(nic);
                        }
                    });
        }
    }

    private void generateVmId(AzureInstanceContext ctx, AzureInstanceStage nextStage) {
        ComputeState cs = new ComputeState();
        // Set id to the compute state so any new triggered enumeration can patch it instead of
        // creating a new ComputeState, effectively duplicating the ComputeStates related to the
        // same VM resource.
        cs.id = AzureUtils.getVirtualMachineId(
                ctx.endpointAuth.userLink,
                ctx.resourceGroup.name(),
                ctx.vmName);

        sendWithDeferredResult(
                Operation.createPatch(ctx.computeRequest.resourceReference)
                        .setBody(cs))
                .thenApply(op -> ctx)
                .whenComplete(thenAllocation(ctx, nextStage));
    }

    /**
     * Converts Photon model constructs to underlying Azure NetworkInterface model.
     */
    private NetworkInterfaceInner newAzureNetworkInterface(
            AzureInstanceContext ctx,
            AzureNicContext nicCtx) {

        NetworkInterfaceDescription description = nicCtx.nicStateWithDesc.description;

        NetworkInterfaceIPConfigurationInner ipConfig = new NetworkInterfaceIPConfigurationInner();
        ipConfig.withName(AzureUtils.generateClusterCommonName(NICCONFIG_NAME_PREFIX, ctx));
        ipConfig.withSubnet(nicCtx.subnet);

        if (nicCtx.publicIP != null) {
            // Public IP is not auto-assigned so check for existence
            ipConfig.withPublicIPAddress(new SubResource().withId(nicCtx.publicIP.id()));
        }

        ipConfig.withPrivateIPAllocationMethod(
                new IPAllocationMethod(description.assignment.name()));
        if (description.assignment == IpAssignment.STATIC) {
            ipConfig.withPrivateIPAddress(description.address);

        }

        NetworkInterfaceInner nic = new NetworkInterfaceInner();
        nic.withLocation(ctx.resourceGroup.location());

        // Azure's custom serializers don't handle well collections constructed with
        // Collections.singletonList(), so initialize an ArrayList
        List<NetworkInterfaceIPConfigurationInner> ipConfigs = new ArrayList<>();
        ipConfigs.add(ipConfig);
        nic.withIpConfigurations(ipConfigs);
        if (nicCtx.securityGroup != null) {
            // Security group is optional so check for existence
            nic.withNetworkSecurityGroup(new SubResource().withId(nicCtx.securityGroup.id()));
        }

        return nic;
    }

    private void createVM(AzureInstanceContext ctx, AzureInstanceStage nextStage) {
        ComputeDescriptionService.ComputeDescription description = ctx.child.description;

        Map<String, String> customProperties = description.customProperties;
        if (customProperties == null) {
            handleError(ctx, new IllegalStateException("Custom properties not specified"));
            return;
        }

        //DiskService.DiskStateExpanded bootDisk = ctx.bootDiskState;
        if (ctx.bootDiskState == null) {
            handleError(ctx, new IllegalStateException("Azure bootDisk not specified"));
            return;
        }

        String cloudConfig = null;
        if (ctx.bootDiskState.bootConfig != null
                && ctx.bootDiskState.bootConfig.files.length > CLOUD_CONFIG_DEFAULT_FILE_INDEX) {
            cloudConfig = ctx.bootDiskState.bootConfig.files[CLOUD_CONFIG_DEFAULT_FILE_INDEX].contents;
        }

        VirtualMachineInner request = new VirtualMachineInner();
        request.withLocation(ctx.resourceGroup.location());

        SubResource availabilitySetSubResource = new SubResource()
                .withId(ctx.availabilitySet.id());
        request.withAvailabilitySet(availabilitySetSubResource);

        // Set OS profile.
        OSProfile osProfile = new OSProfile();
        osProfile.withComputerName(ctx.vmName);
        if (ctx.childAuth != null) {
            osProfile.withAdminUsername(ctx.childAuth.userEmail);
            osProfile.withAdminPassword(EncryptionUtils.decrypt(ctx.childAuth.privateKey));
        }
        if (cloudConfig != null) {
            try {
                osProfile.withCustomData(Base64.getEncoder()
                        .encodeToString(cloudConfig.getBytes(Utils.CHARSET)));
            } catch (UnsupportedEncodingException e) {
                logWarning(() -> "Error encoding user data");
                return;
            }
        }
        request.withOsProfile(osProfile);

        // Set hardware profile.
        HardwareProfile hardwareProfile = new HardwareProfile();
        hardwareProfile.withVmSize(
                description.instanceType != null
                        ? VirtualMachineSizeTypes.fromString(description.instanceType)
                        : VirtualMachineSizeTypes.BASIC_A0);
        request.withHardwareProfile(hardwareProfile);

        // Set storage profile.
        // Create destination OS VHD
        final OSDisk osDisk = newAzureOsDisk(ctx);

        final StorageProfile storageProfile = new StorageProfile();

        storageProfile.withOsDisk(osDisk);

        List<DataDisk> dataDisks = new ArrayList<>();
        List<Integer> LUNsOnImage = new ArrayList<>();

        storageProfile.withImageReference(ctx.imageSource.asImageReferenceInner());

        if (ctx.imageSource.type == ImageSource.Type.PRIVATE_IMAGE) {

            // set LUNs of data disks present on the custom image.
            final ImageState imageState = ctx.imageSource.asImageState();
            if (imageState != null && imageState.diskConfigs != null) {
                for (DiskConfiguration diskConfig : imageState.diskConfigs) {
                    if (diskConfig.properties != null && diskConfig.properties.containsKey
                            (AzureConstants.AZURE_DISK_LUN)) {
                        DataDisk imageDataDisk = new DataDisk();
                        int lun = Integer.parseInt(diskConfig.properties.get(AzureConstants
                                .AZURE_DISK_LUN));
                        LUNsOnImage.add(lun);
                        imageDataDisk.withLun(lun);
                        imageDataDisk.withCreateOption(DiskCreateOptionTypes.FROM_IMAGE);
                        dataDisks.add(imageDataDisk);
                    }
                }
            }

            String dataDiskCaching = ctx.bootDiskState.customProperties
                    .get(AZURE_DATA_DISK_CACHING);
            if (dataDiskCaching != null) {
                dataDisks.stream().forEach(dataDisk -> dataDisk.withCaching(CachingTypes
                        .fromString(dataDiskCaching)));
            }

            String diskType = ctx.bootDiskState.customProperties
                    .get(AZURE_MANAGED_DISK_TYPE);
            if (diskType != null) {
                ManagedDiskParametersInner managedDiskParams = new ManagedDiskParametersInner();
                managedDiskParams
                        .withStorageAccountType(StorageAccountTypes.fromString(diskType));

                dataDisks.stream()
                        .forEach(dataDisk -> dataDisk.withManagedDisk(managedDiskParams));
            }
        }

        // choose LUN greater than the one specified in case of custom image. Else start from zero.
        int LUNForAdditionalDisk =
                LUNsOnImage.size() == 0 ? 0 : Collections.max(LUNsOnImage) + 1;

        dataDisks.addAll(newAzureDataDisks(ctx, LUNForAdditionalDisk));
        storageProfile.withDataDisks(dataDisks);

        request.withStorageProfile(storageProfile);

        // Set network profile {{
        NetworkProfile networkProfile = new NetworkProfile();
        networkProfile.withNetworkInterfaces(new ArrayList<>());

        for (AzureNicContext nicCtx : ctx.nics) {
            NetworkInterfaceReferenceInner nicRef = new NetworkInterfaceReferenceInner();
            nicRef.withId(nicCtx.nic.id());
            // NOTE: First NIC is marked as Primary.
            nicRef.withPrimary(networkProfile.networkInterfaces().isEmpty());

            networkProfile.networkInterfaces().add(nicRef);
        }
        request.withNetworkProfile(networkProfile);

        logFine(() -> String.format("Creating virtual machine with name [%s]", ctx.vmName));

        AzureAsyncCallback<VirtualMachineInner> callback = new AzureAsyncCallback<VirtualMachineInner>() {
            @Override
            public void onError(Throwable e) {

                // Computer names for windows machines are restricted to 15 characters. Check the
                // exception and try again with a shorter name
                if (isIncorrectNameLength(e)) {
                    request.osProfile()
                            .withComputerName(generateWindowsComputerName(ctx.vmName));

                    getComputeManagementClientImpl(ctx).virtualMachines()
                            .createOrUpdateAsync(
                                    ctx.resourceGroup.name(), ctx.vmName, request, this);
                    return;
                }

                handleCloudError(
                        String.format("Provisioning VM %s: FAILED. Details:", ctx.vmName),
                        ctx,
                        COMPUTE_NAMESPACE, e);
            }

            // Cannot tell for sure, but these checks should be enough
            private boolean isIncorrectNameLength(Throwable e) {
                if (e instanceof CloudException) {
                    CloudException ce = (CloudException) e;
                    CloudError body = ce.body();
                    if (body != null) {
                        String code = body.code();
                        String target = body.target();

                        return INVALID_PARAMETER.equals(code) && COMPUTER_NAME
                                .equals(target)
                                && request.osProfile().computerName().length()
                                > WINDOWS_COMPUTER_NAME_MAX_LENGTH
                                && body.message().toLowerCase().contains("windows");
                    }
                }
                return false;
            }

            private String generateWindowsComputerName(String vmName) {
                String computerName = vmName;
                if (vmName.length() > WINDOWS_COMPUTER_NAME_MAX_LENGTH) {
                    // Take the first 12 and the last 3 chars of the generated VM name
                    computerName = vmName.substring(0, 12) + vmName
                            .substring(vmName.length() - 3, vmName.length());
                }
                return computerName;
            }

            @Override
            public void onSuccess(VirtualMachineInner result) {
                logFine(() -> String.format("Successfully created vm [%s]", result.name()));

                ctx.provisionedVm = result;

                ComputeState cs = new ComputeState();
                // Azure for some case changes the case of the vm id.
                ctx.vmId = result.id().toLowerCase();
                cs.id = ctx.vmId;
                cs.type = ComputeType.VM_GUEST;
                cs.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;
                cs.lifecycleState = LifecycleState.READY;
                if (ctx.child.customProperties == null) {
                    cs.customProperties = new HashMap<>();
                } else {
                    cs.customProperties = ctx.child.customProperties;
                }
                cs.customProperties.put(RESOURCE_GROUP_NAME, ctx.resourceGroup.name());
                cs.customProperties.put(CUSTOM_OS_TYPE,
                        AzureUtils.getNormalizedOSType(ctx.provisionedVm));

                Operation.CompletionHandler completionHandler = (ox, exc) -> {
                    if (exc != null) {
                        handleError(ctx, exc);
                        return;
                    }
                    handleAllocation(ctx, nextStage);
                };

                sendRequest(
                        Operation.createPatch(ctx.computeRequest.resourceReference)
                                .setBody(cs)
                                .setCompletion(completionHandler));
            }
        };

        getComputeManagementClientImpl(ctx).virtualMachines().createOrUpdateAsync(
                ctx.resourceGroup.name(), ctx.vmName, request, callback);
    }

    /**
     * Converts Photon model boot DiskState to underlying Azure OSDisk model.
     */
    private OSDisk newAzureOsDisk(AzureInstanceContext ctx) {

        final OSDisk azureOsDisk = new OSDisk();

        azureOsDisk.withName(ctx.bootDiskState.name);

        if (ctx.reuseExistingStorageAccount()) {
            azureOsDisk.withVhd(getVHDUriForOSDisk(ctx.vmName, ctx.bootDiskState
                    .storageDescription.name));
        } else if (ctx.useManagedDisks()) {
            ManagedDiskParametersInner managedDiskParams = new ManagedDiskParametersInner();
            String accountType = ctx.bootDiskState.customProperties.getOrDefault(AzureConstants
                    .AZURE_MANAGED_DISK_TYPE, StorageAccountTypes.STANDARD_LRS.toString());
            managedDiskParams
                    .withStorageAccountType(StorageAccountTypes.fromString(accountType));
            azureOsDisk.withManagedDisk(managedDiskParams);
        } else {
            azureOsDisk.withVhd(getVHDUriForOSDisk(ctx.vmName, ctx.storageAccountName));
        }

        // We don't support Attach option which allows to use a specialized disk to create the
        // virtual machine.
        azureOsDisk.withCreateOption(DiskCreateOptionTypes.FROM_IMAGE);

        if (ctx.bootDiskState.customProperties != null &&
                ctx.bootDiskState.customProperties.get(AZURE_OSDISK_CACHING) != null) {

            azureOsDisk.withCaching(CachingTypes.fromString(
                    ctx.bootDiskState.customProperties.get(AZURE_OSDISK_CACHING)));
        } else {
            // Recommended default caching for OS disk
            azureOsDisk.withCaching(CachingTypes.NONE);
        }

        if (ctx.bootDiskState.capacityMBytes > 31744
                && ctx.bootDiskState.capacityMBytes < AZURE_MAXIMUM_OS_DISK_SIZE_MB) {
            // In case custom boot disk size is set then use that
            // value. If value more than maximum allowed then proceed with default size.

            // Converting MBs to GBs and casting as int
            int diskSizeInGB = (int) ctx.bootDiskState.capacityMBytes / 1024;
            azureOsDisk.withDiskSizeGB(diskSizeInGB);
        } else {
            logInfo(() -> String.format(
                    "Proceeding with default OS Disk Size defined by the image for the disk %s",
                    azureOsDisk.name()));
        }

        return azureOsDisk;
    }

    /**
     * Converts Photon model data DiskState to underlying Azure DataDisk model.
     */
    private List<DataDisk> newAzureDataDisks(AzureInstanceContext ctx, int
            startLUNForAdditionalDisks) {

        int lunIndex = startLUNForAdditionalDisks;

        final List<DataDisk> azureDataDisks = new ArrayList<>();

        for (DiskService.DiskStateExpanded diskState : ctx.dataDiskStates) {

            if (diskState.persistent == true && ctx.useManagedDisks()) {
                continue;
            }
            final DataDisk dataDisk = new DataDisk();
            dataDisk.withName(diskState.name);
            if (ctx.reuseExistingStorageAccount()) {
                dataDisk.withVhd(
                        getVHDUriForDataDisk(ctx.vmName, diskState.storageDescription.name,
                                lunIndex));
            } else if (ctx.useManagedDisks()) {
                ManagedDiskParametersInner managedDiskParams = new ManagedDiskParametersInner();
                String accountType = diskState.customProperties.getOrDefault(AzureConstants
                        .AZURE_MANAGED_DISK_TYPE, StorageAccountTypes.STANDARD_LRS.toString());
                managedDiskParams
                        .withStorageAccountType(StorageAccountTypes.fromString(accountType));
                dataDisk.withManagedDisk(managedDiskParams);
            } else {
                dataDisk.withVhd(
                        getVHDUriForDataDisk(ctx.vmName, ctx.storageAccountName, lunIndex));
            }

            dataDisk.withCreateOption(DiskCreateOptionTypes.EMPTY);
            dataDisk.withDiskSizeGB((int) diskState.capacityMBytes / 1024);
            dataDisk.withCaching(CachingTypes.fromString(
                    diskState.customProperties.getOrDefault(
                            AZURE_DATA_DISK_CACHING,
                            CachingTypes.READ_WRITE.toString())));
            dataDisk.withLun(lunIndex);

            azureDataDisks.add(dataDisk);

            lunIndex++;
        }

        // Add the external existing disks
        if (!ctx.externalDataDisks.isEmpty()) {
            for (DiskService.DiskStateExpanded diskState : ctx.externalDataDisks) {
                final DataDisk dataDisk = new DataDisk();

                if (ctx.reuseExistingStorageAccount()) {
                    dataDisk.withVhd(
                            getVHDUriForDataDisk(ctx.vmName, diskState.storageDescription.name,
                                    lunIndex));
                } else if (ctx.useManagedDisks()) {
                    ManagedDiskParametersInner managedDiskParams = new ManagedDiskParametersInner();
                    managedDiskParams.withId(diskState.id);
                    dataDisk.withManagedDisk(managedDiskParams);
                } else {
                    dataDisk.withVhd(
                            getVHDUriForDataDisk(ctx.vmName, ctx.storageAccountName, lunIndex));
                }

                dataDisk.withCreateOption(DiskCreateOptionTypes.ATTACH);

                dataDisk.withCaching(CachingTypes.fromString(
                        diskState.customProperties.getOrDefault(
                                AZURE_DATA_DISK_CACHING,
                                CachingTypes.READ_WRITE.toString())));
                dataDisk.withLun(lunIndex);

                azureDataDisks.add(dataDisk);

                lunIndex++;
            }
        }

        return azureDataDisks;
    }

    /**
     * Update Compute related local state details with the actual data from Azure. This includes:
     * <ul>
     * <li>setting the public address to the ComputeState</li>
     * <li>setting private IP addresses to NetworkInterfaceState objects</li>
     * <li>setting VHD URI to DiskState objects</li>
     * </ul>
     */
    private void updateComputeStateDetails(AzureInstanceContext ctx, AzureInstanceStage
            nextStage) {

        DeferredResult.completed(ctx)
                .thenCompose(this::getPublicIPAddress)
                .thenCompose(this::updateComputeState)
                .thenCompose(this::updateNicStates)
                .thenCompose(this::updateDiskStates)
                .whenComplete(thenAllocation(ctx, nextStage));
    }

    private DeferredResult<AzureInstanceContext> getPublicIPAddress(AzureInstanceContext ctx) {
        if (ctx.getPrimaryNic().publicIP == null) {
            // No public IP address created -> do nothing.
            return DeferredResult.completed(ctx);
        }

        NetworkManagementClientImpl client = getNetworkManagementClientImpl(ctx);

        String msg = "Get public IP address for resource group [" + ctx.resourceGroup.name()
                + "] and name [" + ctx.getPrimaryNic().publicIP.name() + "].";

        AzureDeferredResultServiceCallback<PublicIPAddressInner> callback = new AzureDeferredResultServiceCallback<PublicIPAddressInner>(
                ctx.service, msg) {
            @Override
            protected DeferredResult<PublicIPAddressInner> consumeSuccess(
                    PublicIPAddressInner result) {

                ctx.getPrimaryNic().publicIP = result;
                return DeferredResult.completed(result);
            }
        };

        client.publicIPAddresses().getByResourceGroupAsync(
                ctx.resourceGroup.name(),
                ctx.getPrimaryNic().publicIP.name(),
                null /* expand */,
                callback);

        return callback.toDeferredResult().thenApply(ignored -> ctx);
    }

    /**
     * Update {@code computeState.address} with Public IP, if presented.
     */
    private DeferredResult<AzureInstanceContext> updateComputeState(AzureInstanceContext ctx) {
        if (ctx.getPrimaryNic().publicIP == null) {
            // Do nothing.
            return DeferredResult.completed(ctx);
        }

        ComputeState computeState = new ComputeState();

        computeState.address = ctx.getPrimaryNic().publicIP.ipAddress();
        computeState.name = ctx.vmName;

        Operation updateCS = Operation.createPatch(ctx.computeRequest.resourceReference)
                .setBody(computeState);

        return ctx.service
                .sendWithDeferredResult(updateCS)
                .thenApply(ignore -> {
                    logFine(() -> String.format(
                            "Updating Compute state [%s] with Public IP [%s]: SUCCESS",
                            ctx.vmName, computeState.address));
                    return ctx;
                });
    }

    /**
     * Update {@code computeState.nicState[i].address} with Azure NICs' private IP.
     */
    private DeferredResult<AzureInstanceContext> updateNicStates(AzureInstanceContext ctx) {

        if (ctx.nics == null || ctx.nics.isEmpty()) {
            // Do nothing.
            return DeferredResult.completed(ctx);
        }

        List<DeferredResult<Void>> updateNICsDR = new ArrayList<>(ctx.nics.size());

        for (AzureNicContext nicCtx : ctx.nics) {
            if (nicCtx.nic == null) {
                continue;
            }

            final NetworkInterfaceState nicStateToUpdate = new NetworkInterfaceState();
            nicStateToUpdate.id = nicCtx.nic.id();
            nicStateToUpdate.documentSelfLink = nicCtx.nicStateWithDesc.documentSelfLink;

            if (nicCtx.nic.ipConfigurations() != null && !nicCtx.nic.ipConfigurations()
                    .isEmpty()) {
                nicStateToUpdate.address = nicCtx.nic.ipConfigurations().get(0)
                        .privateIPAddress();
            }

            Operation updateNicOp = Operation
                    .createPatch(ctx.service, nicStateToUpdate.documentSelfLink)
                    .setBody(nicStateToUpdate);

            DeferredResult<Void> updateNicDR = ctx.service.sendWithDeferredResult(updateNicOp)
                    .thenAccept(ignored -> logFine(() -> String.format(
                            "Updating NIC state [%s] with Private IP [%s]: SUCCESS",
                            nicCtx.nic.name(), nicStateToUpdate.address)));

            updateNICsDR.add(updateNicDR);
        }

        return DeferredResult.allOf(updateNICsDR).thenApply(ignored -> ctx);
    }

    /**
     * Update {@code computeState.diskState[i].id} with Azure Disks' VHD URI.
     */
    private DeferredResult<AzureInstanceContext> updateDiskStates(AzureInstanceContext ctx) {

        if (ctx.provisionedVm == null) {
            // Do nothing.
            return DeferredResult.completed(ctx);
        }

        List<DeferredResult<Operation>> diskStateDRs = new ArrayList<>();

        // Update boot DiskState with Azure osDisk VHD URI in case of unmanaged disks and
        // disks id in case of managed disks
        {
            final OSDisk azureOsDisk = ctx.provisionedVm.storageProfile().osDisk();

            final DiskState diskStateToUpdate = new DiskState();
            diskStateToUpdate.documentSelfLink = ctx.bootDiskState.documentSelfLink;
            diskStateToUpdate.persistent = ctx.bootDiskState.persistent;
            diskStateToUpdate.regionId = ctx.provisionedVm.location();
            diskStateToUpdate.origin = DiskService.DiskOrigin.DEPLOYED;

            diskStateToUpdate.endpointLink = ctx.endpoint.documentSelfLink;
            AdapterUtils.addToEndpointLinks(diskStateToUpdate, ctx.endpoint.documentSelfLink);

            // The actual value being updated
            if (ctx.useManagedDisks()) {
                diskStateToUpdate.id = azureOsDisk.managedDisk().id();
            } else {
                diskStateToUpdate.id = AzureUtils.canonizeId(azureOsDisk.vhd().uri());
            }
            diskStateToUpdate.status = DiskService.DiskStatus.ATTACHED;
            Operation updateDiskState = Operation
                    .createPatch(ctx.service, diskStateToUpdate.documentSelfLink)
                    .setBody(diskStateToUpdate);

            DeferredResult<Operation> updateDR = ctx.service
                    .sendWithDeferredResult(updateDiskState)
                    .whenComplete((op, exc) -> {
                        if (exc != null) {
                            logSevere(() -> String.format(
                                    "Updating boot DiskState [%s] with VHD URI [%s]: FAILED with %s",
                                    ctx.bootDiskState.name, diskStateToUpdate.id,
                                    Utils.toString(exc)));
                        } else {
                            logFine(() -> String.format(
                                    "Updating boot DiskState [%s] with VHD URI [%s]: SUCCESS",
                                    ctx.bootDiskState.name, diskStateToUpdate.id));
                        }
                    });

            diskStateDRs.add(updateDR);
        }

        for (DataDisk azureDataDisk : ctx.provisionedVm.storageProfile().dataDisks()) {

            // Find corresponding DiskState by name
            Optional<DiskState> dataDiskOpt = ctx.dataDiskStates.stream()
                    .map(DiskState.class::cast)
                    .filter(dS -> azureDataDisk.name().equals(dS.name))
                    .findFirst();

            Optional<DiskState> externalDiskOpt = ctx.externalDataDisks.stream()
                    .map(DiskState.class::cast)
                    .filter(dS -> azureDataDisk.name().equals(dS.name))
                    .findFirst();

            // update disk state
            if (dataDiskOpt.isPresent()) {
                diskStateDRs.add(createDiskToUpdate(ctx, dataDiskOpt, azureDataDisk));
            } else if (externalDiskOpt.isPresent()) {
                diskStateDRs.add(createDiskToUpdate(ctx, externalDiskOpt, azureDataDisk));
            } else {
                // additional disks were created by the custom image. Will always be of
                // managed disk type. Create new disk state.

                DiskState diskStateToCreate = new DiskState();
                diskStateToCreate.id = azureDataDisk.managedDisk().id();
                diskStateToCreate.name = azureDataDisk.name();
                diskStateToCreate.regionId = ctx.provisionedVm.location();
                diskStateToCreate.origin = DiskService.DiskOrigin.DEPLOYED;
                diskStateToCreate.customProperties = new HashMap<>();
                diskStateToCreate.customProperties.put(DISK_CONTROLLER_NUMBER, String.valueOf
                        (azureDataDisk.lun()));
                if (azureDataDisk.managedDisk().storageAccountType() != null) {
                    diskStateToCreate.customProperties
                            .put(AZURE_MANAGED_DISK_TYPE, azureDataDisk
                                    .managedDisk().storageAccountType().toString());
                } else {
                    // set to Standard_LRS default
                    diskStateToCreate.customProperties.put(AZURE_MANAGED_DISK_TYPE,
                            StorageAccountTypes.STANDARD_LRS.toString());
                }
                diskStateToCreate.customProperties.put(AZURE_DATA_DISK_CACHING, azureDataDisk
                        .caching().toString());
                if (azureDataDisk.diskSizeGB() != null) {
                    diskStateToCreate.capacityMBytes = azureDataDisk.diskSizeGB() * 1024;
                }
                diskStateToCreate.status = DiskService.DiskStatus.ATTACHED;
                diskStateToCreate.persistent = ctx.bootDiskState.persistent;

                diskStateToCreate.endpointLink = ctx.endpoint.documentSelfLink;
                AdapterUtils
                        .addToEndpointLinks(diskStateToCreate, ctx.endpoint.documentSelfLink);

                Operation createDiskState = Operation
                        .createPost(ctx.service, DiskService.FACTORY_LINK)
                        .setBody(diskStateToCreate);

                DeferredResult<Operation> createDR = ctx.service.sendWithDeferredResult
                        (createDiskState)
                        .whenComplete((op, exc) -> {
                            if (exc != null) {
                                logSevere(() -> String.format(
                                        "Creating data DiskState [%s] with disk id [%s]: FAILED "
                                                + "with %s", azureDataDisk.name(), azureDataDisk
                                                .managedDisk().id(), Utils.toString(exc)));
                            } else {
                                logFine(() -> String.format(
                                        "Creating data DiskState [%s] with disk id [%s]: SUCCESS",
                                        azureDataDisk.name(),
                                        azureDataDisk.managedDisk().id()));
                                //update compute state with data disks present on custom image
                                ComputeState cs = new ComputeState();
                                List<String> disksLinks = new ArrayList<>();
                                DiskState diskState = op.getBody(DiskState.class);
                                disksLinks.add(diskState.documentSelfLink);
                                cs.diskLinks = disksLinks;
                                Operation.CompletionHandler completionHandler = (ox, ex) -> {
                                    if (ex != null) {
                                        handleError(ctx, ex);
                                        return;
                                    }
                                };
                                sendRequest(
                                        Operation.createPatch(
                                                ctx.computeRequest.resourceReference)
                                                .setBody(cs).setCompletion(completionHandler));
                            }
                        });
                diskStateDRs.add(createDR);
            }

        }

        return DeferredResult.allOf(diskStateDRs).thenApply(ignored -> ctx);
    }

    /**
     * Creates and returns a new diskState object which is updated with id, LUN, status and documentSelfLink
     */
    private DeferredResult<Operation> createDiskToUpdate(AzureInstanceContext ctx,
            Optional<DiskState> diskOpt,
            DataDisk azureDataDisk) {
        // update VHD uri or disk id respectively for un-managed and managed disks
        DiskState diskState = diskOpt.get();
        final DiskState diskStateToUpdate = new DiskState();
        diskStateToUpdate.documentSelfLink = diskState.documentSelfLink;
        diskStateToUpdate.persistent = diskState.persistent;

        if (ctx.useManagedDisks()) {
            diskStateToUpdate.id = azureDataDisk.managedDisk().id();
        } else {
            diskStateToUpdate.id = AzureUtils.canonizeId(azureDataDisk.vhd().uri());
        }

        // The LUN value of disk
        if (diskStateToUpdate.customProperties == null) {
            diskStateToUpdate.customProperties = new HashMap<>();
        }
        diskStateToUpdate.customProperties
                .put(DISK_CONTROLLER_NUMBER, String.valueOf(azureDataDisk.lun()));

        diskStateToUpdate.status = DiskService.DiskStatus.ATTACHED;
        diskStateToUpdate.regionId = ctx.provisionedVm.location();
        diskStateToUpdate.origin = DiskService.DiskOrigin.DEPLOYED;

        diskStateToUpdate.endpointLink = ctx.endpoint.documentSelfLink;
        AdapterUtils.addToEndpointLinks(diskStateToUpdate, ctx.endpoint.documentSelfLink);

        Operation updateDiskState = Operation
                .createPatch(createInventoryUri(getHost(), diskStateToUpdate.documentSelfLink))
                .setBody(diskStateToUpdate);

        DeferredResult<Operation> updateDR = ctx.service.sendWithDeferredResult(updateDiskState)
                .whenComplete((op, exc) -> {
                    if (exc != null) {
                        logSevere(() -> String.format(
                                "Updating data DiskState [%s] with VHD URI [%s]: FAILED with %s",
                                diskState.name, diskStateToUpdate.id, Utils.toString(exc)));

                    } else {
                        logFine(() -> String.format(
                                "Updating data DiskState [%s] with VHD URI [%s]: SUCCESS",
                                diskState.name, diskStateToUpdate.id));
                    }
                });

        return updateDR;
    }

    /**
     * Gets the storage keys from azure and patches the credential state.
     */
    private void getStorageKeys(AzureInstanceContext ctx, AzureInstanceStage nextStage) {

        if (ctx.reuseExistingStorageAccount() || ctx.useManagedDisks()) {
            // no need to get keys as no new storage description was created
            handleAllocation(ctx, nextStage);
            return;
        }

        StorageManagementClientImpl client = getStorageManagementClientImpl(ctx);

        client.storageAccounts().listKeysAsync(ctx.storageAccountRGName,
                ctx.storageAccountName,
                new AzureAsyncCallback<StorageAccountListKeysResultInner>() {
                    @Override
                    public void onError(Throwable e) {
                        handleError(ctx, e);
                    }

                    @Override
                    public void onSuccess(StorageAccountListKeysResultInner result) {
                        logFine(() -> String
                                .format("Retrieved the storage account keys for storage"
                                        + " account [%s]", ctx.storageAccountName));

                        AuthCredentialsServiceState storageAuth = new AuthCredentialsServiceState();
                        storageAuth.customProperties = new HashMap<>();
                        for (StorageAccountKey key : result.keys()) {
                            storageAuth.customProperties.put(
                                    getStorageAccountKeyName(storageAuth.customProperties),
                                    key.value());
                        }
                        storageAuth.tenantLinks = ctx.parent.tenantLinks;

                        Operation patchStorageDescriptionWithKeys = Operation
                                .createPost(createInventoryUri(getHost(),
                                        AuthCredentialsService.FACTORY_LINK))
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
                                                logFine(() -> "Patched storage description.");
                                                handleAllocation(ctx, nextStage);
                                            }));
                                    sendRequest(patch);
                                });
                        sendRequest(patchStorageDescriptionWithKeys);
                    }
                });
    }

    private static VirtualHardDisk getVHDUriForOSDisk(String vmName, String storageAccountName) {

        String vhdName = vmName + BOOT_DISK_SUFFIX;

        return new VirtualHardDisk().withUri(
                String.format(VHD_URI_FORMAT, storageAccountName, vhdName));
    }

    private static VirtualHardDisk getVHDUriForDataDisk(String vmName, String
            storageAccountName,
            int num) {

        String vhdName = vmName + DATA_DISK_SUFFIX + "-" + num;

        return new VirtualHardDisk().withUri(
                String.format(VHD_URI_FORMAT, storageAccountName, vhdName));
    }

    /**
     * This method tries to detect specific CloudErrors by their code and apply some additional
     * handling. In case a subscription registration error is detected the method register
     * subscription for given namespace. In case invalid parameter error is detected, the error
     * message is made better human-readable. Otherwise the fallback is to transition to error state
     * through next specific error handler.
     */
    private void handleCloudError(String msg, AzureInstanceContext ctx, String namespace,
            Throwable e) {
        if (e instanceof CloudException) {
            CloudException ce = (CloudException) e;
            CloudError body = ce.body();
            if (body != null) {
                String code = body.code();
                if (MISSING_SUBSCRIPTION_CODE.equals(code)) {
                    registerSubscription(ctx, namespace);
                    return;
                } else if (INVALID_PARAMETER.equals(code) || INVALID_RESOURCE_GROUP
                        .equals(code)) {
                    String invalidParameterMsg = String.format("%s Invalid parameter. %s",
                            msg, body.message());

                    e = new IllegalStateException(invalidParameterMsg, ctx.error);
                    handleError(ctx, e);
                    return;
                }
            }
        }
        handleError(ctx, e);
    }

    private void registerSubscription(AzureInstanceContext ctx, String namespace) {
        ResourceManagementClientImpl client = getResourceManagementClientImpl(ctx);
        client.providers().registerAsync(namespace,
                new AzureAsyncCallback<ProviderInner>() {
                    @Override
                    public void onError(Throwable e) {
                        handleError(ctx, e);
                    }

                    @Override
                    public void onSuccess(ProviderInner result) {
                        String registrationState = result.registrationState();
                        if (!PROVIDER_REGISTRED_STATE.equalsIgnoreCase(registrationState)) {
                            logInfo(() -> String.format("%s namespace registration in %s state",
                                    namespace, registrationState));
                            long retryExpiration = Utils.getNowMicrosUtc()
                                    + DEFAULT_EXPIRATION_INTERVAL_MICROS;
                            getSubscriptionState(ctx, namespace, retryExpiration);
                            return;
                        }
                        logFine(() -> String.format("Successfully registered namespace [%s]",
                                result.namespace()));
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

        ResourceManagementClientImpl client = getResourceManagementClientImpl(ctx);

        getHost().schedule(
                () -> client.providers().getAsync(namespace,
                        new AzureAsyncCallback<ProviderInner>() {
                            @Override
                            public void onError(Throwable e) {
                                handleError(ctx, e);
                            }

                            @Override
                            public void onSuccess(ProviderInner result) {
                                String registrationState = result.registrationState();
                                if (!PROVIDER_REGISTRED_STATE
                                        .equalsIgnoreCase(registrationState)) {
                                    logInfo(() -> String.format(
                                            "%s namespace registration in %s state",
                                            namespace, registrationState));
                                    getSubscriptionState(ctx, namespace, retryExpiration);
                                    return;
                                }
                                logFine(() -> String.format(
                                        "Successfully registered namespace [%s]",
                                        result.namespace()));
                                handleAllocation(ctx);
                            }
                        }),
                RETRY_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void getChildAuth(AzureInstanceContext ctx, AzureInstanceStage next) {
        if (ctx.child.description.authCredentialsLink == null) {
            AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
            auth.userEmail = AzureConstants.DEFAULT_ADMIN_USER;
            auth.privateKey = AzureConstants.DEFAULT_ADMIN_PASSWORD;
            ctx.childAuth = auth;
            handleAllocation(ctx, next);
            return;
        }

        String childAuthLink = ctx.child.description.authCredentialsLink;
        Consumer<Operation> onSuccess = (op) -> {
            ctx.childAuth = op.getBody(AuthCredentialsServiceState.class);
            handleAllocation(ctx, next);
        };
        AdapterUtils
                .getServiceState(this, createInventoryUri(getHost(), childAuthLink), onSuccess,
                        getFailureConsumer(ctx));
    }

    private Consumer<Throwable> getFailureConsumer(AzureInstanceContext ctx) {
        return (t) -> handleError(ctx, t);
    }

    private ResourceManagementClientImpl getResourceManagementClientImpl(AzureInstanceContext
            ctx) {
        return ctx.azureSdkClients.getResourceManagementClientImpl();
    }

    public NetworkManagementClientImpl getNetworkManagementClientImpl(AzureInstanceContext ctx) {
        return ctx.azureSdkClients.getNetworkManagementClientImpl();
    }

    private StorageManagementClientImpl getStorageManagementClientImpl(AzureInstanceContext ctx) {
        return ctx.azureSdkClients.getStorageManagementClientImpl();
    }

    private ComputeManagementClientImpl getComputeManagementClientImpl(AzureInstanceContext ctx) {
        return ctx.azureSdkClients.getComputeManagementClientImpl();
    }

    private ComputeManager getComputeManager(AzureInstanceContext ctx) {
        return ctx.azureSdkClients.getComputeManager();
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
                .map(disk -> Operation.createGet(UriUtils.buildExpandLinksQueryUri(
                        UriUtils.buildUri(this.getHost(), disk))))
                .collect(Collectors.toList()));

        OperationJoin operationJoin = OperationJoin.create(operations)
                .setCompletion(
                        (ops, exc) -> {
                            if (exc != null) {
                                handleError(ctx, new IllegalStateException(
                                        "Error getting disk information",
                                        exc.values().iterator().next()));
                                return;
                            }

                            ctx.dataDiskStates = new ArrayList<>();
                            ctx.externalDataDisks = new ArrayList<>();
                            ctx.persistentDisks = new ArrayList<>();
                            for (Operation op : ops.values()) {
                                DiskService.DiskStateExpanded disk = op
                                        .getBody(DiskService.DiskStateExpanded.class);

                                // We treat the first disk in the boot order as the boot disk.
                                if (disk.bootOrder != null && disk.bootOrder == 1) {
                                    if (ctx.bootDiskState != null) {
                                        handleError(ctx, new IllegalStateException(
                                                "Only 1 boot disk is allowed"));
                                        return;
                                    }

                                    ctx.bootDiskState = disk;
                                    // incase persistent flag is not specified for boot disk
                                    // default to false
                                    if (ctx.bootDiskState.persistent == null) {
                                        ctx.bootDiskState.persistent = false;
                                    }
                                } else {
                                    if (disk.status == DiskService.DiskStatus.AVAILABLE) {
                                        disk.persistent = true; // external disks are always
                                        // persistent
                                        ctx.externalDataDisks.add(disk);
                                    } else {
                                        if (disk.persistent == null) {
                                            //Default to non persistent disk
                                            disk.persistent = false;
                                        }
                                        ctx.dataDiskStates.add(disk);
                                    }
                                }
                            }

                            if (ctx.bootDiskState == null) {
                                handleError(ctx,
                                        new IllegalStateException("Boot disk is required"));
                                return;
                            }
                            if (!validateDiskStates(ctx)) {
                                handleError(ctx,
                                        new IllegalStateException("Azure VMs can either "
                                                + "have managed disk or un-managed disk. Not Both."));
                                return;
                            }

                            handleAllocation(ctx, nextStage);
                        });
        operationJoin.sendWith(this);
    }

    private boolean validateDiskStates(AzureInstanceContext ctx) {

        if (ctx.useManagedDisks()) {
            //Check if all data disks and external existing disks are of managed disk type
            return (ctx.dataDiskStates.size() == ctx.dataDiskStates.stream()
                    .filter(isManagedDisk())
                    .count()) &&
                    (ctx.externalDataDisks.size() == ctx.externalDataDisks.stream()
                            .filter(isManagedDisk())
                            .count());
        } else if (ctx.reuseExistingStorageAccount() || ctx.bootDiskState.customProperties
                .containsKey(AZURE_STORAGE_ACCOUNT_NAME)) {
            //check if all data disk and external existing disks are of un-managed type
            return (ctx.dataDiskStates.size() == ctx.dataDiskStates.stream()
                    .filter(isUnManagedDisk())
                    .count()) &&
                    (ctx.externalDataDisks.size() == ctx.externalDataDisks.stream()
                            .filter(isUnManagedDisk())
                            .count());
        }
        return true;
    }

    // returns predicate to check if a disk is of managed type
    public static Predicate<DiskService.DiskStateExpanded> isManagedDisk() {
        return disk -> {
            if (disk.customProperties != null && disk.customProperties.containsKey
                    (AZURE_MANAGED_DISK_TYPE)) {
                return true;
            } else {
                return false;
            }
        };
    }

    // returns predicate to check if disk is of un-managed type
    public static Predicate<DiskService.DiskStateExpanded> isUnManagedDisk() {
        return disk -> {
            // Storage account linked through storage description or Storage account name
            // specified in custom properties
            if (disk.storageDescription != null || (disk.customProperties != null && disk
                    .customProperties.containsKey(AZURE_STORAGE_ACCOUNT_NAME))) {
                return true;
            } else {
                return false;
            }
        };
    }

    /**
     * Load {@code ctx.imageSource} from {@code ctx.bootDiskState}.
     */
    private DeferredResult<AzureInstanceContext> getImageSource(AzureInstanceContext ctx) {

        return ctx.getImageSource(ctx.bootDiskState)

                // Load AzureImageSource
                .thenApply(imageSource -> {
                    ctx.imageSource = AzureImageSource.of(imageSource);

                    return ctx;
                })

                // In case of '<publisher>:<offer>:<sku>:LATEST' resolve version
                .thenCompose(this::resolveLatestVirtualMachineImage)

                // Do some customization logic
                .thenCompose(context -> {
                    // AzureImageSource.of ALWAYS return PUBLIC_IMAGE or PRIVATE_IMAGE
                    if (context.imageSource.type == ImageSource.Type.PUBLIC_IMAGE) {
                        return getImageSource_Public(context);
                    }
                    if (context.imageSource.type == ImageSource.Type.PRIVATE_IMAGE) {
                        return getImageSource_Private(context);
                    }
                    return DeferredResult.failed(new IllegalStateException(
                            "Unexpected AzureImageSource.Type: " + context.imageSource.type));
                });
    }

    // Integral part of getImageSource method
    private DeferredResult<AzureInstanceContext> getImageSource_Public(AzureInstanceContext ctx) {
        // Leave as placeholder method, although empty
        return DeferredResult.completed(ctx);
    }

    // Integral part of getImageSource method
    private DeferredResult<AzureInstanceContext> getImageSource_Private(AzureInstanceContext
            ctx) {

        // In-case type of disk is not specified for the boot disk then we set it to managed one
        // because custom/private images only work with managed disk. This is specially
        // needed for cadenza use case where storage profile is unavailable.
        if (!AzureUtils.isTypeOfDiskSpecified(ctx.bootDiskState)) {
            ctx.bootDiskState.customProperties.put(
                    AzureConstants.AZURE_MANAGED_DISK_TYPE,
                    StorageAccountTypes.STANDARD_LRS.toString());
            // patch boot disk states with the new custom property that makes it managed disk
            URI uri = ctx.computeRequest.buildUri(ctx.bootDiskState.documentSelfLink);

            Operation patchBootDiskOp = Operation
                    .createPatch(uri)
                    .setBody(ctx.bootDiskState);

            return sendWithDeferredResult(patchBootDiskOp).thenApply(op -> ctx);
        }

        return DeferredResult.completed(ctx);
    }

    /**
     * Get the LATEST VirtualMachineImage using publisher, offer and SKU.
     */
    private DeferredResult<AzureInstanceContext> resolveLatestVirtualMachineImage(
            AzureInstanceContext ctx) {

        ImageReferenceInner imageReference = ctx.imageSource.asImageReferenceInner();

        if (AzureConstants.AZURE_URN_VERSION_LATEST
                .equalsIgnoreCase(imageReference.version())) {

            String msg = String.format("Getting latest Azure image by %s:%s:%s",
                    imageReference.publisher(),
                    imageReference.offer(),
                    imageReference.sku());

            AzureDeferredResultServiceCallback<List<VirtualMachineImageResourceInner>> callback =
                    new Default<>(ctx.service, msg);

            getComputeManagementClientImpl(ctx).virtualMachineImages().listAsync(
                    ctx.resourceGroup.location(),
                    imageReference.publisher(),
                    imageReference.offer(),
                    imageReference.sku(),
                    null,
                    1,
                    AzureConstants.ORDER_BY_VM_IMAGE_RESOURCE_NAME_DESC,
                    callback);

            return callback.toDeferredResult().thenApply(imageResources -> {

                if (imageResources == null
                        || imageResources.isEmpty()
                        || imageResources.get(0) == null) {

                    throw new IllegalStateException(
                            String.format("No latest Azure image found by %s:%s:%s",
                                    imageReference.publisher(),
                                    imageReference.offer(),
                                    imageReference.sku()));
                }

                // Update 'latest'-version with actual version
                imageReference.withVersion(imageResources.get(0).name());

                return ctx;
            });
        }

        return DeferredResult.completed(ctx);
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

            ApplicationTokenCredentials credentials = ctx.azureSdkClients.credentials;

            URI uri = UriUtils.extendUriWithQuery(
                    UriUtils.buildUri(UriUtils.buildUri(AzureUtils.getAzureBaseUri()),
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
                        AzureConstants.AUTH_HEADER_BEARER_PREFIX
                                + credentials.getToken(AzureUtils.getAzureBaseUri()));
            } catch (Exception ex) {
                handleError(ctx, ex);
                return;
            }

            logFine(() -> String.format("Enabling monitoring on the VM [%s]", vmName));
            operation.setCompletion((op, er) -> {
                if (er != null) {
                    handleError(ctx, er);
                    return;
                }

                logFine(() -> String.format("Successfully enabled monitoring on the VM [%s]",
                        vmName));
                handleAllocation(ctx, nextStage);
            });
            sendRequest(operation);
        });

        String fileUri = getClass()
                .getResource(AzureConstants.DIAGNOSTIC_SETTINGS_JSON_FILE_NAME)
                .getFile();
        File jsonPayloadFile = new File(fileUri);
        try {
            FileUtils.readFileAndComplete(readFile, jsonPayloadFile);
        } catch (Exception e) {
            handleError(ctx, e);
        }
    }

    /**
     * Based on the queried result, in case no SA description exists for the given name, create
     * a new one. For this purpose, StorageAccountKeys should be obtained, and with them
     * AuthCredentialsServiceState is created, and a StorageDescription, pointing to that
     * authentication description document.
     */
    private DeferredResult<StorageDescription> createStorageDescription(
            AzureInstanceContext ctx) {

        String msg = "Getting Azure StorageAccountKeys for [" + ctx.storageAccount.name()
                + "] Storage Account";

        AzureDeferredResultServiceCallback<StorageAccountListKeysResultInner> handler =
                new Default<>(this, msg);

        getStorageManagementClientImpl(ctx)
                .storageAccounts()
                .listKeysAsync(ctx.storageAccountRGName, ctx.storageAccount.name(),
                        handler);

        return handler.toDeferredResult()
                .thenCompose(keys -> {
                    Operation createStorageDescOp = Operation
                            .createPost(getHost(), StorageDescriptionService.FACTORY_LINK)
                            .setBody(AzureUtils.constructStorageDescription(
                                    getHost(), getSelfLink(),
                                    ctx.storageAccount, ctx, keys))
                            .setReferer(getUri());
                    return sendWithDeferredResult(createStorageDescOp,
                            StorageDescription.class);
                })
                .thenCompose(storageDescription -> {
                    ctx.storageDescription = storageDescription;
                    ctx.bootDiskState.storageDescriptionLink = storageDescription.documentSelfLink;

                    return DeferredResult.completed(ctx.storageDescription);
                });
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

            AzureInstanceService.this.logFine(this.msg + ": STARTED");
        }

        @Override
        public final void onError(Throwable e) {

            if (this.callCtx.failOnError) {

                if (this.callCtx.hasAnyFailed.compareAndSet(false, true)) {
                    // Check whether this is the first failure and proceed to next stage.
                    // i.e. fail-fast on batch operations.
                    AzureInstanceService.this.handleCloudError(
                            String.format("%s: FAILED. Details:", this.msg), this.ctx,
                            COMPUTE_NAMESPACE, e);
                } else {
                    e = new IllegalStateException(
                            this.msg + ": FAILED. Details: " + e.getMessage(),
                            e);
                    // Any subsequent failure is just logged.
                    AzureInstanceService.this.logSevere(e);
                }
            } else {
                final String finalMsg = e.getMessage();
                AzureInstanceService.this.logFine(() -> String.format("%s: SUCCESS with error."
                        + " Details: %s", this.msg, finalMsg));

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
        public final void onSuccess(T result) {

            if (this.callCtx.hasAnyFailed.get()) {
                AzureInstanceService.this
                        .logFine(this.msg + ": SUCCESS. Still batch calls have "
                                + "failed so SKIP this result.");
                return;
            }

            AzureInstanceService.this.logFine(this.msg + ": SUCCESS");

            // First delegate to descendants to process result body
            CompletionStage<T> handleSuccess = handleSuccess(result);

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
