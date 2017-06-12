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
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_CORE_MANAGEMENT_URI;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_OSDISK_BLOB_URI;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_OSDISK_CACHING;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_DEFAULT_RG_NAME;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_NAME;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_RG_NAME;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.COMPUTE_NAMESPACE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.INVALID_PARAMETER;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.INVALID_RESOURCE_GROUP;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.MISSING_SUBSCRIPTION_CODE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.NETWORK_NAMESPACE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.PROVIDER_REGISTRED_STATE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.PROVISIONING_STATE_SUCCEEDED;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.RESOURCE_GROUP_NOT_FOUND;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.STORAGE_ACCOUNT_ALREADY_EXIST;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.STORAGE_NAMESPACE;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.buildRestClient;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.cleanUpHttpClient;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.getAzureConfig;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.getStorageAccountKeyName;
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
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.microsoft.azure.CloudError;
import com.microsoft.azure.CloudException;
import com.microsoft.azure.SubResource;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.compute.CachingTypes;
import com.microsoft.azure.management.compute.DiskCreateOptionTypes;
import com.microsoft.azure.management.compute.HardwareProfile;
import com.microsoft.azure.management.compute.NetworkProfile;
import com.microsoft.azure.management.compute.OSDisk;
import com.microsoft.azure.management.compute.OSProfile;
import com.microsoft.azure.management.compute.OperatingSystemTypes;
import com.microsoft.azure.management.compute.StorageProfile;
import com.microsoft.azure.management.compute.VirtualHardDisk;
import com.microsoft.azure.management.compute.VirtualMachineSizeTypes;
import com.microsoft.azure.management.compute.implementation.ComputeManagementClientImpl;
import com.microsoft.azure.management.compute.implementation.ImageReferenceInner;
import com.microsoft.azure.management.compute.implementation.NetworkInterfaceReferenceInner;
import com.microsoft.azure.management.compute.implementation.VirtualMachineImageResourceInner;
import com.microsoft.azure.management.compute.implementation.VirtualMachineInner;
import com.microsoft.azure.management.network.AddressSpace;
import com.microsoft.azure.management.network.IPAllocationMethod;
import com.microsoft.azure.management.network.SecurityRuleAccess;
import com.microsoft.azure.management.network.SecurityRuleDirection;
import com.microsoft.azure.management.network.SecurityRuleProtocol;
import com.microsoft.azure.management.network.implementation.NetworkInterfaceIPConfigurationInner;
import com.microsoft.azure.management.network.implementation.NetworkInterfaceInner;
import com.microsoft.azure.management.network.implementation.NetworkInterfacesInner;
import com.microsoft.azure.management.network.implementation.NetworkManagementClientImpl;
import com.microsoft.azure.management.network.implementation.NetworkSecurityGroupInner;
import com.microsoft.azure.management.network.implementation.NetworkSecurityGroupsInner;
import com.microsoft.azure.management.network.implementation.PublicIPAddressInner;
import com.microsoft.azure.management.network.implementation.PublicIPAddressesInner;
import com.microsoft.azure.management.network.implementation.SecurityRuleInner;
import com.microsoft.azure.management.network.implementation.SubnetInner;
import com.microsoft.azure.management.network.implementation.VirtualNetworkInner;
import com.microsoft.azure.management.network.implementation.VirtualNetworksInner;
import com.microsoft.azure.management.resources.implementation.ProviderInner;
import com.microsoft.azure.management.resources.implementation.ResourceGroupInner;
import com.microsoft.azure.management.resources.implementation.ResourceGroupsInner;
import com.microsoft.azure.management.resources.implementation.ResourceManagementClientImpl;
import com.microsoft.azure.management.resources.implementation.SubscriptionClientImpl;
import com.microsoft.azure.management.resources.implementation.SubscriptionInner;
import com.microsoft.azure.management.storage.Kind;
import com.microsoft.azure.management.storage.ProvisioningState;
import com.microsoft.azure.management.storage.Sku;
import com.microsoft.azure.management.storage.SkuName;
import com.microsoft.azure.management.storage.StorageAccountKey;
import com.microsoft.azure.management.storage.implementation.StorageAccountCreateParametersInner;
import com.microsoft.azure.management.storage.implementation.StorageAccountInner;
import com.microsoft.azure.management.storage.implementation.StorageAccountListKeysResultInner;
import com.microsoft.azure.management.storage.implementation.StorageManagementClientImpl;
import com.microsoft.rest.ServiceCallback;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.adapters.azure.AzureAsyncCallback;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureInstanceContext.AzureNicContext;
import com.vmware.photon.controller.model.adapters.azure.model.diagnostics.AzureDiagnosticSettings;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDecommissionCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureProvisioningCallback;
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
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.security.util.EncryptionUtils;
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
    private static final String DEFAULT_GROUP_PREFIX = "group";
    private static final String VHD_URI_FORMAT = "https://%s.blob.core.windows.net/vhds/%s.vhd";
    private static final Pattern VHD_URI_PATTERN = Pattern
            .compile("https://([\\p{Lower}\\p{Digit}]+).blob.core.windows.net/(.+)\\.vhd");
    private static final String BOOT_DISK_SUFFIX = "-boot-disk";

    private static final long DEFAULT_EXPIRATION_INTERVAL_MICROS = TimeUnit.MINUTES.toMicros(5);
    private static final int RETRY_INTERVAL_SECONDS = 30;
    private static final long AZURE_MAXIMUM_OS_DISK_SIZE_MB = 1023 * 1024; // Maximum allowed OS
    // disk size on Azure is 1023 GB

    private ExecutorService executorService;

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
    public void handleStart(Operation startPost) {
        this.executorService = getHost().allocateExecutor(this);

        super.handleStart(startPost);
    }

    @Override
    public void handleStop(Operation delete) {
        this.executorService.shutdown();
        AdapterUtils.awaitTermination(this.executorService);
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
        logFine(() -> String.format("Transition to " + nextStage));
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
        logInfo("Azure instance management at stage %s", ctx.stage);
        try {
            switch (ctx.stage) {
            case CLIENT:
                if (ctx.credentials == null) {
                    ctx.credentials = getAzureConfig(ctx.parentAuth);
                }
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
                createResourceGroup(ctx, AzureInstanceStage.GET_IMAGE);
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

        SubscriptionClientImpl subscriptionClient = new SubscriptionClientImpl(ctx.credentials);

        subscriptionClient.subscriptions().getAsync(
                ctx.parentAuth.userLink, new ServiceCallback<SubscriptionInner>() {
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

    private void deleteVM(AzureInstanceContext ctx) {
        if (ctx.computeRequest.isMockRequest) {
            handleAllocation(ctx, AzureInstanceStage.FINISHED);
            return;
        }

        String rgName = getResourceGroupName(ctx);

        if (rgName == null || rgName.isEmpty()) {
            throw new IllegalArgumentException("Resource group name is required");
        }

        ResourceGroupsInner azureClient = getResourceManagementClientImpl(ctx)
                .resourceGroups();

        String msg = "Deleting resource group [" + rgName + "] for [" + ctx.vmName + "] VM";

        AzureDeferredResultServiceCallback<Void> callback = new AzureDeferredResultServiceCallback<Void>(
                this, msg) {
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

                        IllegalStateException e = new IllegalStateException(invalidParameterMsg,
                                exc);
                        return e;
                    }
                }
                return exc;
            }

            @Override
            protected DeferredResult<Void> consumeSuccess(Void body) {
                return DeferredResult.completed(body);
            }

        };

        azureClient.deleteAsync(rgName, callback);

        callback.toDeferredResult()
                .thenApply(ignore -> ctx)
                .whenComplete(thenAllocation(ctx, AzureInstanceStage.FINISHED));
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
        cleanUpHttpClient(ctx.restClient.httpClient());
    }

    private void finishWithSuccess(AzureInstanceContext ctx) {
        // Report the success back to the caller
        ctx.taskManager.finishTask();
        cleanUpHttpClient(ctx.restClient.httpClient());
    }

    private void createResourceGroup(AzureInstanceContext ctx, AzureInstanceStage nextStage) {

        String resourceGroupName = getResourceGroupName(ctx);

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

    private void createStorageAccount(AzureInstanceContext ctx, AzureInstanceStage nextStage) {

        // First create SA RG (if does not exist), then create the SA itself (if does not exist)
        DeferredResult.completed(ctx)
                .thenCompose(this::createStorageAccountRG)
                .thenCompose(this::createStorageAccount)
                .whenComplete(thenAllocation(ctx, nextStage, STORAGE_NAMESPACE));
    }

    private DeferredResult<AzureInstanceContext> resolveStorageAccountForPrivateImage(
            AzureInstanceContext ctx) {

        final String imageOsDiskUri = ctx.imageOsDisk().properties.get(AZURE_OSDISK_BLOB_URI);

        final Matcher matcher = VHD_URI_PATTERN.matcher(imageOsDiskUri);

        if (!matcher.matches()) {
            return DeferredResult.failed(new IllegalArgumentException(
                    "Invalid VHD URI format of image OS Disk: " + imageOsDiskUri));
        }

        // Override StorageAccount from request with the SA of the private VM image
        ctx.storageAccountName = matcher.group(1);

        String msg = "Getting Resource Group for [" + ctx.storageAccountName + "] SA for ["
                + ctx.vmName + "] VM";

        AzureDeferredResultServiceCallback<List<StorageAccountInner>> handler = new AzureDeferredResultServiceCallback<List<StorageAccountInner>>(
                this, msg) {

            @Override
            protected DeferredResult<List<StorageAccountInner>> consumeSuccess(
                    List<StorageAccountInner> result) {
                // Filter by StorageAccount name
                for (StorageAccountInner sA : result) {
                    if (Objects.equals(ctx.storageAccountName, sA.name())) {
                        ctx.storageAccountRGName = AzureUtils.getResourceGroupName(sA.id());
                        return DeferredResult.completed(result);
                    }
                }
                return DeferredResult.failed(new IllegalArgumentException(
                        "Unable to find SA with name: " + ctx.storageAccountName));
            }
        };

        getStorageManagementClientImpl(ctx).storageAccounts().listAsync(handler);

        return handler.toDeferredResult().thenApply(ignore -> ctx);
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

        if (ctx.imageSource.type == ImageSource.Type.PRIVATE_IMAGE) {
            // Special handling for Private images which implies that the VM storage account
            // must be same as the storage account the Custom/Private VM image resides.
            return DeferredResult.completed(ctx)
                    .thenCompose(this::resolveStorageAccountForPrivateImage);
        }

        // null check in-case storage description is not set
        if (ctx.bootDisk.storageDescription != null) {
            ctx.storageAccountName = ctx.bootDisk.storageDescription.name;
        } else {
            ctx.storageAccountName = ctx.bootDisk.customProperties.get(AZURE_STORAGE_ACCOUNT_NAME);
        }

        ctx.storageAccountRGName = ctx.bootDisk.customProperties
                .getOrDefault(AZURE_STORAGE_ACCOUNT_RG_NAME, AZURE_STORAGE_ACCOUNT_DEFAULT_RG_NAME);

        if (ctx.storageAccountName == null) {
            // In case SA is not provided in the request, use request VA resource group
            ctx.storageAccountName = String.valueOf(System.currentTimeMillis()) + "st";
            ctx.storageAccountRGName = ctx.resourceGroup.name();

            return DeferredResult.completed(ctx);
        }

        String msg = "Create/Update SA Resource Group [" + ctx.storageAccountRGName + "] for ["
                + ctx.vmName + "] VM";

        AzureDeferredResultServiceCallback<ResourceGroupInner> handler = new AzureDeferredResultServiceCallback<ResourceGroupInner>(
                this, msg) {
            @Override
            protected Throwable consumeError(Throwable exc) {
                exc = super.consumeError(exc);

                if (!(exc instanceof CloudException)) {
                    return exc;
                }

                final CloudError body = ((CloudException) exc).body();
                if (body == null) {
                    return exc;
                }

                if (RESOURCE_GROUP_NOT_FOUND.equals(body.code())) {
                    return RECOVERED;
                }
                if (INVALID_RESOURCE_GROUP.equals(body.code())) {
                    String invalidParameterMsg = String.format(
                            "Invalid resource group parameter. %s",
                            body.message());

                    return new IllegalStateException(invalidParameterMsg, exc);
                }

                return exc;
            }

            @Override
            protected DeferredResult<ResourceGroupInner> consumeSuccess(ResourceGroupInner rg) {
                return DeferredResult.completed(rg);
            }
        };

        // Use shared RG. In case not provided in the bootDisk properties, use the default one
        final ResourceGroupInner sharedSARG = new ResourceGroupInner();
        sharedSARG.withLocation(ctx.child.description.regionId);

        getResourceManagementClientImpl(ctx)
                .resourceGroups()
                .createOrUpdateAsync(ctx.storageAccountRGName, sharedSARG, handler);

        return handler.toDeferredResult().thenApply(ignore -> ctx);
    }

    private DeferredResult<AzureInstanceContext> createStorageAccount(AzureInstanceContext ctx) {

        String msg = "Create/Update Azure Storage Account [" + ctx.storageAccountName + "] for ["
                + ctx.vmName + "] VM";

        StorageAccountCreateParametersInner storageParameters = new StorageAccountCreateParametersInner();
        storageParameters.withLocation(ctx.child.description.regionId);
        storageParameters.withSku(new Sku().withName(SkuName.STANDARD_LRS));
        storageParameters.withKind(Kind.STORAGE);

        StorageAccountProvisioningCallback handler = new StorageAccountProvisioningCallback(ctx,
                msg);

        getStorageManagementClientImpl(ctx).storageAccounts().createAsync(
                ctx.storageAccountRGName,
                ctx.storageAccountName,
                storageParameters,
                handler);

        return handler.toDeferredResult().thenApply(ignore -> ctx);
    }

    private void createNetworkIfNotExist(AzureInstanceContext ctx, AzureInstanceStage nextStage) {
        if (ctx.nics.isEmpty()) {
            handleAllocation(ctx, nextStage);
            return;
        }

        // Get NICs with not existing subnets
        List<SubnetInner> subnetsToCreate = ctx.nics.stream()
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

        AzureProvisioningCallback<VirtualNetworkInner> handler = new AzureProvisioningCallback<VirtualNetworkInner>(
                this, msg) {

            @Override
            protected DeferredResult<VirtualNetworkInner> consumeProvisioningSuccess(
                    VirtualNetworkInner vNet) {
                // Populate NICs with Azure Subnet
                for (AzureNicContext nicCtx : ctx.nics) {
                    if (nicCtx.subnet == null) {
                        nicCtx.subnet = vNet.subnets().stream()
                                .filter(subnet -> subnet.name()
                                        .equals(nicCtx.subnetState.name))
                                .findFirst().get();
                    }
                }
                return DeferredResult.completed(vNet);
            }

            @Override
            protected String getProvisioningState(VirtualNetworkInner vNet) {
                // Return first NOT Succeeded state,
                // or PROVISIONING_STATE_SUCCEEDED if all are Succeeded
                String subnetPS = vNet.subnets().stream()
                        .map(SubnetInner::provisioningState)
                        // Get if any is NOT Succeeded...
                        .filter(ps -> !PROVISIONING_STATE_SUCCEEDED.equalsIgnoreCase(ps))
                        // ...and return it.
                        .findFirst()
                        // Otherwise consider all are Succeeded
                        .orElse(PROVISIONING_STATE_SUCCEEDED);

                if (PROVISIONING_STATE_SUCCEEDED.equals(vNet.provisioningState())
                        && PROVISIONING_STATE_SUCCEEDED.equals(subnetPS)) {

                    return PROVISIONING_STATE_SUCCEEDED;
                }
                return vNet.provisioningState() + ":" + subnetPS;
            }

            @Override
            protected Runnable checkProvisioningStateCall(
                    ServiceCallback<VirtualNetworkInner> checkProvisioningStateCallback) {
                return () -> azureClient.getByResourceGroupAsync(
                        vNetRGName,
                        vNetName,
                        null /* expand */,
                        checkProvisioningStateCallback);
            }
        };

        azureClient.createOrUpdateAsync(vNetRGName, vNetName, vNetToCreate, handler);

        handler.toDeferredResult()
                .thenApply(ignore -> ctx)
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

        AddressSpace addressSpace = new AddressSpace()
                .withAddressPrefixes(Collections.singletonList(nicCtx.networkState.subnetCIDR));
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
                nicCtx.publicIPSubResource = new SubResource().withId(nicCtx.publicIP.id());

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

        List<DeferredResult<NetworkSecurityGroupInner>> createSGDR = ctx.nics.stream()
                .filter(nicCtx -> (
                // Security Group is requested but no existing security group is mapped.
                nicCtx.securityGroupStates != null && nicCtx.securityGroupStates.size() == 1
                        && nicCtx.securityGroup == null))
                .map(nicCtx -> {
                    SecurityGroupState sgState = nicCtx.securityGroupStates.get(0);
                    NetworkSecurityGroupInner nsg = newAzureSecurityGroup(ctx, sgState);
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

    private NetworkSecurityGroupInner newAzureSecurityGroup(AzureInstanceContext ctx,
            SecurityGroupState sg) {

        if (sg == null) {
            throw new IllegalStateException("SecurityGroup state should not be null.");
        }

        List<SecurityRuleInner> securityRules = new ArrayList<>();
        final AtomicInteger priority = new AtomicInteger(1000);
        if (sg.ingress != null) {
            sg.ingress.forEach(rule -> securityRules.add(newAzureSecurityRule(rule,
                    SecurityRuleDirection.INBOUND, priority.getAndIncrement())));
        }

        priority.set(1000);
        if (sg.egress != null) {
            sg.egress.forEach(rule -> securityRules.add(newAzureSecurityRule(rule,
                    SecurityRuleDirection.OUTBOUND, priority.getAndIncrement())));
        }

        NetworkSecurityGroupInner nsg = new NetworkSecurityGroupInner();
        nsg.withLocation(ctx.resourceGroup.location());
        nsg.withSecurityRules(securityRules);

        return nsg;
    }

    private SecurityRuleInner newAzureSecurityRule(Rule rule, SecurityRuleDirection direction,
            int priority) {
        SecurityRuleInner sr = new SecurityRuleInner();
        sr.withPriority(priority);
        sr.withAccess(SecurityRuleAccess.ALLOW);
        sr.withDirection(direction);
        if (SecurityRuleDirection.INBOUND.equals(direction)) {
            sr.withSourceAddressPrefix(rule.ipRangeCidr);
            sr.withDestinationAddressPrefix(SecurityGroupService.ANY);

            sr.withSourcePortRange(rule.ports);
            sr.withDestinationPortRange(SecurityGroupService.ANY);
        } else {
            sr.withSourceAddressPrefix(SecurityGroupService.ANY);
            sr.withDestinationAddressPrefix(rule.ipRangeCidr);

            sr.withSourcePortRange(SecurityGroupService.ANY);
            sr.withDestinationPortRange(rule.ports);
        }
        sr.withName(rule.name);
        sr.withProtocol(SecurityRuleProtocol.ASTERISK);

        return sr;
    }

    private DeferredResult<NetworkSecurityGroupInner> createSecurityGroup(
            AzureInstanceContext ctx,
            AzureNicContext nicCtx,
            NetworkSecurityGroupInner securityGroupToCreate) {

        NetworkSecurityGroupsInner azureClient = getNetworkManagementClientImpl(ctx)
                .networkSecurityGroups();

        final String nsgName = nicCtx.securityGroupState().name;

        final String nsgRGName = nicCtx.securityGroupRGState != null
                ? nicCtx.securityGroupRGState.name
                : ctx.resourceGroup.name();

        final String msg = "Create Azure Security Group["
                + nsgRGName + "/" + nsgName
                + "] for [" + nicCtx.nicStateWithDesc.name + "] NIC for ["
                + ctx.vmName
                + "] VM";

        AzureProvisioningCallback<NetworkSecurityGroupInner> handler = new AzureProvisioningCallback<NetworkSecurityGroupInner>(
                this, msg) {
            @Override
            protected DeferredResult<NetworkSecurityGroupInner> consumeProvisioningSuccess(
                    NetworkSecurityGroupInner securityGroup) {

                nicCtx.securityGroup = securityGroup;
                nicCtx.securityGroupSubResource = new SubResource().withId(securityGroup.id());

                return DeferredResult.completed(securityGroup);
            }

            @Override
            protected Runnable checkProvisioningStateCall(
                    ServiceCallback<NetworkSecurityGroupInner> checkProvisioningStateCallback) {
                return () -> azureClient.getByResourceGroupAsync(
                        nsgRGName,
                        nsgName,
                        null /* expand */,
                        checkProvisioningStateCallback);
            }

            @Override
            protected String getProvisioningState(NetworkSecurityGroupInner body) {
                return body.provisioningState();
            }
        };

        azureClient.createOrUpdateAsync(nsgRGName, nsgName, securityGroupToCreate, handler);

        return handler.toDeferredResult();
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
            final NetworkInterfaceInner nic = newAzureNetworkInterface(ctx, nicCtx);

            final String nicName = nicCtx.nicStateWithDesc.name;

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
                ctx.parentAuth.userLink,
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

        NetworkInterfaceIPConfigurationInner ipConfig = new NetworkInterfaceIPConfigurationInner();
        ipConfig.withName(generateName(NICCONFIG_NAME_PREFIX));
        ipConfig.withPrivateIPAllocationMethod(IPAllocationMethod.DYNAMIC);
        ipConfig.withSubnet(nicCtx.subnet);
        ipConfig.withPublicIPAddress(nicCtx.publicIPSubResource);

        NetworkInterfaceInner nic = new NetworkInterfaceInner();
        nic.withLocation(ctx.resourceGroup.location());
        List<NetworkInterfaceIPConfigurationInner> ipConfigurationList = new ArrayList<NetworkInterfaceIPConfigurationInner>();
        ipConfigurationList.add(ipConfig);
        nic.withIpConfigurations(ipConfigurationList);
        nic.withNetworkSecurityGroup(nicCtx.securityGroupSubResource);

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

        VirtualMachineInner request = new VirtualMachineInner();
        request.withLocation(ctx.resourceGroup.location());

        // Set OS profile.
        OSProfile osProfile = new OSProfile();
        String vmName = ctx.vmName;
        osProfile.withComputerName(vmName);
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
                        ? new VirtualMachineSizeTypes(description.instanceType)
                        : VirtualMachineSizeTypes.BASIC_A0);
        request.withHardwareProfile(hardwareProfile);

        // Set storage profile.
        // Create destination OS VHD
        String vhdName = String.format(VHD_URI_FORMAT, ctx.storageAccountName, getVHDName(vmName));

        OSDisk osDisk = new OSDisk();
        osDisk.withName(vmName);
        osDisk.withVhd(new VirtualHardDisk().withUri(vhdName));
        // We don't support Attach option which allows to use a specialized disk to create the
        // virtual machine.
        osDisk.withCreateOption(DiskCreateOptionTypes.FROM_IMAGE);

        if (bootDisk.customProperties != null &&
                bootDisk.customProperties.get(AZURE_OSDISK_CACHING) != null) {

            osDisk.withCaching(CachingTypes.fromString(
                    bootDisk.customProperties.get(AZURE_OSDISK_CACHING)));
        } else {
            // Recommended default caching for OS disk
            osDisk.withCaching(CachingTypes.NONE);
        }

        if (ctx.bootDisk.capacityMBytes > 31744
                && ctx.bootDisk.capacityMBytes < AZURE_MAXIMUM_OS_DISK_SIZE_MB) {
            // In case custom boot disk size is set then use that
            // value. If value more than maximum allowed then proceed with default size.

            // Converting MBs to GBs and casting as int
            int diskSizeInGB = (int) ctx.bootDisk.capacityMBytes / 1024;
            osDisk.withDiskSizeGB(diskSizeInGB);
        } else {
            logInfo(() -> String.format(
                    "Proceeding with Default OS Disk Size defined by VHD %s",
                    vhdName));
        }

        final StorageProfile storageProfile = new StorageProfile();

        // Apply Public/Private images specifics

        if (ctx.imageSource.type == ImageSource.Type.PUBLIC_IMAGE
                || ctx.imageSource.type == ImageSource.Type.IMAGE_REFERENCE) {

            storageProfile.withImageReference(ctx.imageReference);

        } else if (ctx.imageSource.type == ImageSource.Type.PRIVATE_IMAGE) {

            final ImageState privateImage = ctx.imageSource.asImageState();

            // Image OS type
            osDisk.withOsType(OperatingSystemTypes.fromString(privateImage.osFamily));

            final DiskConfiguration osDiskConfig = ctx.imageOsDisk();

            // Ref to OS disk (VHD) of the image
            osDisk.withImage(new VirtualHardDisk().withUri(
                    osDiskConfig.properties.get(AZURE_OSDISK_BLOB_URI)));
        }

        storageProfile.withOsDisk(osDisk);

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

        logFine(() -> String.format("Creating virtual machine with name [%s]", vmName));

        getComputeManagementClientImpl(ctx).virtualMachines().createOrUpdateAsync(
                ctx.resourceGroup.name(), vmName, request,
                new AzureAsyncCallback<VirtualMachineInner>() {
                    @Override
                    public void onError(Throwable e) {
                        handleCloudError(
                                String.format("Provisioning VM %s: FAILED. Details:", vmName), ctx,
                                COMPUTE_NAMESPACE, e);
                    }

                    @Override
                    public void onSuccess(VirtualMachineInner result) {
                        logFine(() -> String.format("Successfully created vm [%s]", result.name()));

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
     * Gets the public IP address from the VM (if exists) and patches the compute state and primary
     * NIC state.
     */
    private void getPublicIpAddress(AzureInstanceContext ctx, AzureInstanceStage nextStage) {
        if (ctx.getPrimaryNic().publicIP == null) {
            // No public IP address created.
            handleAllocation(ctx, nextStage);
            return;
        }

        NetworkManagementClientImpl client = getNetworkManagementClientImpl(ctx);

        client.publicIPAddresses().getByResourceGroupAsync(
                ctx.resourceGroup.name(),
                ctx.getPrimaryNic().publicIP.name(),
                null /* expand */,
                new AzureAsyncCallback<PublicIPAddressInner>() {

                    @Override
                    public void onError(Throwable e) {
                        handleError(ctx, e);
                    }

                    @Override
                    public void onSuccess(PublicIPAddressInner result) {
                        ctx.getPrimaryNic().publicIP = result;

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

                        computeState.address = ctx.getPrimaryNic().publicIP.ipAddress();

                        return Operation.createPatch(ctx.computeRequest.resourceReference)
                                .setBody(computeState)
                                .setCompletion((op, exc) -> {
                                    if (exc == null) {
                                        logFine(() -> String.format("Patching compute state with VM"
                                                + " Public IP address [%s]: SUCCESS",
                                                computeState.address));
                                    }
                                });

                    }

                    private Operation patchNICState(AzureInstanceContext ctx) {

                        NetworkInterfaceState primaryNicState = new NetworkInterfaceState();

                        primaryNicState.address = ctx.getPrimaryNic().publicIP.ipAddress();

                        URI primaryNicUri = UriUtils.buildUri(getHost(),
                                ctx.getPrimaryNic().nicStateWithDesc.documentSelfLink);

                        return Operation.createPatch(primaryNicUri)
                                .setBody(primaryNicState)
                                .setCompletion((op, exc) -> {
                                    if (exc == null) {
                                        logFine(() -> String.format("Patching primary NIC state"
                                                + " with VM Public IP address [%s] : SUCCESS",
                                                primaryNicState.address));
                                    }
                                });

                    }
                });
    }

    /**
     * Gets the storage keys from azure and patches the credential state.
     */
    private void getStorageKeys(AzureInstanceContext ctx, AzureInstanceStage nextStage) {
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
                        logFine(() -> String.format("Retrieved the storage account keys for storage"
                                + " account [%s]", ctx.storageAccountName));

                        AuthCredentialsServiceState storageAuth = new AuthCredentialsServiceState();
                        storageAuth.customProperties = new HashMap<>();
                        for (StorageAccountKey key : result.keys()) {
                            storageAuth.customProperties.put(
                                    getStorageAccountKeyName(storageAuth.customProperties),
                                    key.value());
                        }
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
                                                logFine(() -> "Patched storage description.");
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

    private ImageReferenceInner getImageReference(String imageId) {
        String[] imageIdParts = imageId.split(":");
        if (imageIdParts.length != 4) {
            throw new IllegalArgumentException("Azure image ID should be of the format "
                    + "<publisher>:<offer>:<sku>:<version>");
        }

        ImageReferenceInner imageReference = new ImageReferenceInner();
        imageReference.withPublisher(imageIdParts[0]);
        imageReference.withOffer(imageIdParts[1]);
        imageReference.withSku(imageIdParts[2]);
        imageReference.withVersion(imageIdParts[3]);

        return imageReference;
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
                } else if (INVALID_PARAMETER.equals(code) || INVALID_RESOURCE_GROUP.equals(code)) {
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
                                if (!PROVIDER_REGISTRED_STATE.equalsIgnoreCase(registrationState)) {
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
            ctx.childAuth = op.getBody(AuthCredentialsService.AuthCredentialsServiceState.class);
            handleAllocation(ctx, next);
        };
        AdapterUtils.getServiceState(this, childAuthLink, onSuccess, getFailureConsumer(ctx));
    }

    private Consumer<Throwable> getFailureConsumer(AzureInstanceContext ctx) {
        return (t) -> handleError(ctx, t);
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

    private ResourceManagementClientImpl getResourceManagementClientImpl(AzureInstanceContext ctx) {
        if (ctx.resourceManagementClient == null) {
            if (ctx.restClient == null) {
                ctx.restClient = buildRestClient(ctx.credentials, this.executorService);
            }
            ResourceManagementClientImpl client = new ResourceManagementClientImpl(ctx.restClient)
                    .withSubscriptionId(ctx.parentAuth.userLink);
            ctx.resourceManagementClient = client;
        }
        return ctx.resourceManagementClient;
    }

    public NetworkManagementClientImpl getNetworkManagementClientImpl(AzureInstanceContext ctx) {
        if (ctx.networkManagementClient == null) {
            if (ctx.restClient == null) {
                ctx.restClient = buildRestClient(ctx.credentials, this.executorService);
            }
            NetworkManagementClientImpl client = new NetworkManagementClientImpl(ctx.restClient)
                    .withSubscriptionId(ctx.parentAuth.userLink);
            ctx.networkManagementClient = client;
        }
        return ctx.networkManagementClient;
    }

    private StorageManagementClientImpl getStorageManagementClientImpl(AzureInstanceContext ctx) {
        if (ctx.storageManagementClient == null) {
            if (ctx.restClient == null) {
                ctx.restClient = buildRestClient(ctx.credentials, this.executorService);
            }
            StorageManagementClientImpl client = new StorageManagementClientImpl(ctx.restClient)
                    .withSubscriptionId(ctx.parentAuth.userLink);
            ctx.storageManagementClient = client;
        }
        return ctx.storageManagementClient;
    }

    private ComputeManagementClientImpl getComputeManagementClientImpl(AzureInstanceContext ctx) {
        if (ctx.computeManagementClient == null) {
            if (ctx.restClient == null) {
                ctx.restClient = buildRestClient(ctx.credentials, this.executorService);
            }
            ComputeManagementClientImpl client = new ComputeManagementClientImpl(ctx.restClient)
                    .withSubscriptionId(ctx.parentAuth.userLink);
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
                .map(disk -> Operation.createGet(UriUtils.buildExpandLinksQueryUri(
                        UriUtils.buildUri(this.getHost(), disk))))
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
                                DiskService.DiskStateExpanded disk = op
                                        .getBody(DiskService.DiskStateExpanded.class);

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
    private DeferredResult<AzureInstanceContext> getImageSource(AzureInstanceContext ctx) {

        return ctx.getImageSource(ctx.bootDisk)

                .thenApply(imageSource -> {
                    ctx.imageSource = imageSource;
                    return ctx;
                })

                .thenCompose(context -> {
                    if (context.imageSource.type == ImageSource.Type.PUBLIC_IMAGE) {
                        return handlePublicImage(context);
                    }
                    if (context.imageSource.type == ImageSource.Type.PRIVATE_IMAGE) {
                        return handlePrivateImage(context);
                    }
                    if (context.imageSource.type == ImageSource.Type.IMAGE_REFERENCE) {
                        return handleImageRef(context);
                    }
                    return DeferredResult.failed(
                            new IllegalStateException(
                                    "Unexpected ImageSource.Type: " + context.imageSource.type));
                });
    }

    private DeferredResult<AzureInstanceContext> handlePublicImage(AzureInstanceContext ctx) {

        return DeferredResult.completed(ctx)
                .thenApply(context -> {
                    // Convert Azure 'ImageState.id' string to ImageReferenceInner object
                    context.imageReference = getImageReference(context.imageSource.asNativeId());
                    return context;
                })
                .thenCompose(this::resolveLatestVirtualMachineImage);
    }

    private DeferredResult<AzureInstanceContext> handlePrivateImage(AzureInstanceContext ctx) {

        return DeferredResult.completed(ctx);
    }

    private DeferredResult<AzureInstanceContext> handleImageRef(AzureInstanceContext ctx) {

        return DeferredResult.completed(ctx)
                .thenApply(context -> {
                    // Convert Azure 'source image reference' string to ImageReferenceInner object
                    context.imageReference = getImageReference(context.imageSource.asNativeId());
                    return context;
                })
                .thenCompose(this::resolveLatestVirtualMachineImage);
    }

    /**
     * Get the LATEST VirtualMachineImage using publisher, offer and SKU.
     */
    private DeferredResult<AzureInstanceContext> resolveLatestVirtualMachineImage(
            AzureInstanceContext ctx) {

        if (AzureConstants.AZURE_URN_VERSION_LATEST
                .equalsIgnoreCase(ctx.imageReference.version())) {

            String msg = String.format("Getting latest Azure image by %s:%s:%s",
                    ctx.imageReference.publisher(),
                    ctx.imageReference.offer(),
                    ctx.imageReference.sku());

            AzureDeferredResultServiceCallback<List<VirtualMachineImageResourceInner>> callback = new AzureDeferredResultServiceCallback<List<VirtualMachineImageResourceInner>>(
                    ctx.service, msg) {
                @Override
                protected DeferredResult<List<VirtualMachineImageResourceInner>> consumeSuccess(
                        List<VirtualMachineImageResourceInner> imageResources) {
                    return DeferredResult.completed(imageResources);
                }
            };

            getComputeManagementClientImpl(ctx).virtualMachineImages().listAsync(
                    ctx.resourceGroup.location(),
                    ctx.imageReference.publisher(),
                    ctx.imageReference.offer(),
                    ctx.imageReference.sku(),
                    null,
                    1,
                    AzureConstants.ORDER_BY_VM_IMAGE_RESOURCE_NAME_DESC,
                    callback);

            return callback.toDeferredResult().thenCompose(imageResources -> {

                if (imageResources == null
                        || imageResources.isEmpty()
                        || imageResources.get(0) == null) {
                    return DeferredResult
                            .failed(new IllegalStateException("No latest version found"));
                }

                // Update 'latest'-version with actual version
                ctx.imageReference.withVersion(imageResources.get(0).name());

                return DeferredResult.completed(ctx);
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
                        AzureConstants.AUTH_HEADER_BEARER_PREFIX
                                + credentials.getToken(AZURE_CORE_MANAGEMENT_URI));
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

    private class StorageAccountProvisioningCallback
            extends AzureProvisioningCallback<StorageAccountInner> {

        private final AzureInstanceContext ctx;

        public StorageAccountProvisioningCallback(AzureInstanceContext ctx, String message) {
            super(ctx.service, message);

            this.ctx = ctx;
        }

        @Override
        protected Throwable consumeError(Throwable exc) {
            exc = super.consumeError(exc);
            if (exc instanceof CloudException) {
                CloudException azureExc = (CloudException) exc;
                if (STORAGE_ACCOUNT_ALREADY_EXIST
                        .equalsIgnoreCase(azureExc.body().code())) {
                    return RECOVERED;
                }
            }
            return exc;
        }

        @Override
        protected DeferredResult<StorageAccountInner> consumeProvisioningSuccess(
                StorageAccountInner sa) {

            this.ctx.storage = sa;

            return createStorageDescription(this.ctx)
                    // Start next op, patch boot disk, in the sequence
                    .thenCompose(woid -> {
                        URI uri = this.ctx.computeRequest.buildUri(
                                this.ctx.bootDisk.documentSelfLink);

                        Operation patchBootDiskOp = Operation
                                .createPatch(uri)
                                .setBody(this.ctx.bootDisk);

                        return this.ctx.service.sendWithDeferredResult(patchBootDiskOp)
                                .thenRun(() -> this.ctx.service.logFine(
                                        () -> String.format("Updating boot disk [%s]: SUCCESS",
                                                this.ctx.bootDisk.name)));
                    })
                    // Return processed context with StorageAccount
                    .thenApply(woid -> this.ctx.storage);
        }

        /**
         * Based on the queried result, in case no SA description exists for the given name, create
         * a new one. For this purpose, StorageAccountKeys should be obtained, and with them
         * AuthCredentialsServiceState is created, and a StorageDescription, pointing to that
         * authentication description document.
         */
        private DeferredResult<StorageDescription> createStorageDescription(
                AzureInstanceContext ctx) {

            String msg = "Getting Azure StorageAccountKeys for [" + ctx.storage.name()
                    + "] Storage Account";

            AzureDeferredResultServiceCallback<StorageAccountListKeysResultInner> handler = new AzureDeferredResultServiceCallback<StorageAccountListKeysResultInner>(
                    this.service, msg) {

                @Override
                protected Throwable consumeError(Throwable exc) {
                    return new IllegalStateException(String.format(
                            "Getting Azure StorageAccountKeys for [%s] Storage Account: FAILED. Details: %s",
                            ctx.storage.name(), exc.getMessage()));
                }

                @Override
                protected DeferredResult<StorageAccountListKeysResultInner> consumeSuccess(
                        StorageAccountListKeysResultInner body) {
                    logFine(() -> String.format(
                            "Getting Azure StorageAccountKeys for [%s] Storage Account: SUCCESS",
                            ctx.storage.name()));
                    return DeferredResult.completed(body);
                }
            };

            getStorageManagementClientImpl(ctx)
                    .storageAccounts()
                    .listKeysAsync(ctx.storageAccountRGName, ctx.storage.name(), handler);

            return handler.toDeferredResult()
                    .thenCompose(keys -> {
                        Operation createStorageDescOp = Operation
                                .createPost(getHost(), StorageDescriptionService.FACTORY_LINK)
                                .setBody(AzureUtils.constructStorageDescription(
                                        getHost(), getSelfLink(),
                                        ctx.storage, ctx, keys))
                                .setReferer(getUri());
                        return sendWithDeferredResult(createStorageDescOp,
                                StorageDescription.class);
                    })
                    .thenCompose(storageDescription -> {
                        ctx.storageDescription = storageDescription;
                        ctx.bootDisk.storageDescriptionLink = storageDescription.documentSelfLink;

                        return DeferredResult.completed(ctx.storageDescription);
                    });
        }

        @Override
        protected String getProvisioningState(StorageAccountInner sa) {
            ProvisioningState provisioningState = sa.provisioningState();

            // For some reason SA.provisioningState is null, so consider it CREATING.
            if (provisioningState == null) {
                return ProvisioningState.CREATING.name();
            }

            return provisioningState.name();
        }

        @Override
        protected Runnable checkProvisioningStateCall(
                ServiceCallback<StorageAccountInner> checkProvisioningStateCallback) {
            return () -> getStorageManagementClientImpl(this.ctx)
                    .storageAccounts()
                    .getByResourceGroupAsync(
                            this.ctx.storageAccountRGName,
                            this.ctx.storageAccountName,
                            checkProvisioningStateCallback);
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
                    e = new IllegalStateException(this.msg + ": FAILED. Details: " + e.getMessage(),
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
                AzureInstanceService.this.logFine(this.msg + ": SUCCESS. Still batch calls have "
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
