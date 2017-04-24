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
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_DEFAULT_RG_NAME;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_KEY1;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_KEY2;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_NAME;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_RG_NAME;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_TYPE;
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
import com.microsoft.azure.management.resources.ResourceGroupsOperations;
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
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDecommissionCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureProvisioningCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils;
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

    private static final String PRIVATE_IP_ALLOCATION_METHOD = "Dynamic";

    private static final String DEFAULT_GROUP_PREFIX = "group";

    private static final String DEFAULT_VM_SIZE = "Basic_A0";
    private static final String OS_DISK_CREATION_OPTION = "fromImage";

    private static final AccountType DEFAULT_STORAGE_ACCOUNT_TYPE = AccountType.STANDARD_LRS;
    private static final String VHD_URI_FORMAT = "https://%s.blob.core.windows.net/vhds/%s.vhd";
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

        AzureCallContext withFailOnError(boolean failOnError) {
            this.failOnError = failOnError;
            return this;
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
        logInfo("Azure instance management at stage %s", ctx.stage);
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
                ctx.clientBuilder = ctx.httpClient.newBuilder().connectTimeout(30,
                        TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS);

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
                        logFine(() -> String.format("Got subscription %s with id %s",
                                subscription.getDisplayName(), subscription.getId()));
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

        ResourceGroupsOperations azureClient = getResourceManagementClient(ctx)
                .getResourceGroupsOperations();

        String msg = "Deleting resource group [" + rgName + "] for [" + ctx.vmName + "] VM";

        AzureDeferredResultServiceCallback<Void> callback = new AzureDeferredResultServiceCallback<Void>(
                this, msg) {
            @Override
            protected Throwable consumeError(Throwable exc) {
                exc = super.consumeError(exc);
                if (exc instanceof CloudException) {
                    CloudException azureExc = (CloudException) exc;
                    CloudError body = azureExc.getBody();

                    String code = body.getCode();
                    if (RESOURCE_GROUP_NOT_FOUND.equals(code)) {
                        return RECOVERED;
                    } else if (INVALID_RESOURCE_GROUP.equals(code)) {
                        String invalidParameterMsg = String.format(
                                "Invalid resource group parameter. %s",
                                body.getMessage());

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

        String rgName = ctx.resourceGroup.getName();

        String msg = "Rollback provisioning for [" + ctx.vmName + "] Azure VM";

        ResourceGroupsOperations azureClient = getResourceManagementClient(ctx)
                .getResourceGroupsOperations();

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

        cleanUpHttpClient(this, ctx.httpClient);
    }

    private void finishWithSuccess(AzureInstanceContext ctx) {
        // Report the success back to the caller
        ctx.taskManager.finishTask();

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

        if (ctx.bootDisk.customProperties == null) {
            return DeferredResult.failed(
                    new IllegalArgumentException("Custom properties for boot disk is required"));
        }

        ctx.storageAccountName = ctx.bootDisk.customProperties.get(AZURE_STORAGE_ACCOUNT_NAME);
        ctx.storageAccountRGName = ctx.bootDisk.customProperties
                .getOrDefault(AZURE_STORAGE_ACCOUNT_RG_NAME, AZURE_STORAGE_ACCOUNT_DEFAULT_RG_NAME);

        if (ctx.storageAccountName == null) {
            // In case SA is not provided in the request, use request VA resource group
            ctx.storageAccountName = String.valueOf(System.currentTimeMillis()) + "st";
            ctx.storageAccountRGName = ctx.resourceGroup.getName();

            return DeferredResult.completed(ctx);
        }

        // Use shared RG. In case not provided in the bootDisk properties, use the default one
        final ResourceGroup sharedSARG = new ResourceGroup();
        sharedSARG.setLocation(ctx.child.description.regionId);

        String msg = "Create/Update SA Resource Group [" + ctx.storageAccountRGName + "] for ["
                + ctx.vmName + "] VM";

        AzureDeferredResultServiceCallback<ResourceGroup> handler = new AzureDeferredResultServiceCallback<ResourceGroup>(
                this, msg) {
            @Override
            protected Throwable consumeError(Throwable exc) {
                exc = super.consumeError(exc);
                if (exc instanceof CloudException) {
                    CloudException azureExc = (CloudException) exc;
                    CloudError body = azureExc.getBody();
                    if (body != null) {
                        String code = body.getCode();
                        if (RESOURCE_GROUP_NOT_FOUND.equals(code)) {
                            return RECOVERED;
                        } else if (INVALID_RESOURCE_GROUP.equals(code)) {
                            String invalidParameterMsg = String.format(
                                    "Invalid resource group parameter. %s",
                                    body.getMessage());

                            IllegalStateException e = new IllegalStateException(invalidParameterMsg,
                                    exc);
                            return e;
                        }
                    }
                }
                return exc;
            }

            @Override
            protected DeferredResult<ResourceGroup> consumeSuccess(ResourceGroup rg) {
                return DeferredResult.completed(rg);
            }
        };

        getResourceManagementClient(ctx)
                .getResourceGroupsOperations()
                .createOrUpdateAsync(ctx.storageAccountRGName, sharedSARG, handler);

        return handler.toDeferredResult().thenApply(ignore -> ctx);
    }

    private void createStorageAccount(AzureInstanceContext ctx, AzureInstanceStage nextStage) {

        StorageAccountCreateParameters storageParameters = new StorageAccountCreateParameters();
        storageParameters.setLocation(ctx.child.description.regionId);

        String accountType = ctx.bootDisk.customProperties
                .getOrDefault(AZURE_STORAGE_ACCOUNT_TYPE, DEFAULT_STORAGE_ACCOUNT_TYPE.toValue());
        storageParameters.setAccountType(AccountType.fromValue(accountType));

        String msg = "Creating Azure Storage Account for [" + ctx.vmName + "] VM";

        StorageAccountsOperations azureSAClient = getStorageManagementClient(ctx)
                .getStorageAccountsOperations();

        StorageAccountAsyncHandler handler = new StorageAccountAsyncHandler(ctx, azureSAClient,
                this, msg);

        // First create SA RG (if does not exist), then create the SA itself (if does not exist)
        createStorageAccountRG(ctx)
                .thenCompose(context -> {

                    azureSAClient.createAsync(
                            context.storageAccountRGName,
                            context.storageAccountName,
                            storageParameters,
                            handler);

                    return handler.toDeferredResult().thenApply(ignore -> context);
                })
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

        // All NICs MUST be at the same vNet (no cross vNet VMs),
        // so we select the Primary vNet.
        final AzureNicContext primaryNic = ctx.getPrimaryNic();

        final VirtualNetwork vNetToCreate = newAzureVirtualNetwork(
                ctx, primaryNic, subnetsToCreate);

        final String vNetName = primaryNic.networkState.name;

        final String vNetRGName = primaryNic.networkRGState != null
                ? primaryNic.networkRGState.name
                : ctx.resourceGroup.getName();

        VirtualNetworksOperations azureClient = getNetworkManagementClient(ctx)
                .getVirtualNetworksOperations();

        final String subnetNames = vNetToCreate.getSubnets().stream()
                .map(Subnet::getName)
                .collect(Collectors.joining(","));

        final String msg = "Creating Azure vNet-Subnet [v=" + vNetName + "; s="
                + subnetNames
                + "] for ["
                + ctx.vmName + "] VM";

        AzureProvisioningCallback<VirtualNetwork> handler = new AzureProvisioningCallback<VirtualNetwork>(
                this, msg) {

            @Override
            protected DeferredResult<VirtualNetwork> consumeProvisioningSuccess(
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
            protected String getProvisioningState(VirtualNetwork vNet) {
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
            protected Runnable checkProvisioningStateCall(
                    ServiceCallback<VirtualNetwork> checkProvisioningStateCallback) {
                return () -> azureClient.getAsync(
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

        PublicIPAddressesOperations azureClient = getNetworkManagementClient(ctx)
                .getPublicIPAddressesOperations();

        final PublicIPAddress publicIPAddress = newAzurePublicIPAddress(ctx, nicCtx);

        final String publicIPName = ctx.vmName + "-pip";
        final String publicIPRGName = ctx.resourceGroup.getName();

        String msg = "Creating Azure Public IP [" + publicIPName + "] for [" + ctx.vmName + "] VM";

        AzureProvisioningCallback<PublicIPAddress> handler = new AzureProvisioningCallback<PublicIPAddress>(
                this, msg) {
            @Override
            protected DeferredResult<PublicIPAddress> consumeProvisioningSuccess(
                    PublicIPAddress publicIP) {
                nicCtx.publicIP = publicIP;

                return DeferredResult.completed(publicIP);
            }

            @Override
            protected String getProvisioningState(PublicIPAddress publicIP) {
                return publicIP.getProvisioningState();
            }

            @Override
            protected Runnable checkProvisioningStateCall(
                    ServiceCallback<PublicIPAddress> checkProvisioningStateCallback) {
                return () -> azureClient.getAsync(
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
    private PublicIPAddress newAzurePublicIPAddress(
            AzureInstanceContext ctx,
            AzureNicContext nicCtx) {

        PublicIPAddress publicIPAddress = new PublicIPAddress();
        publicIPAddress.setLocation(ctx.resourceGroup.getLocation());
        publicIPAddress
                .setPublicIPAllocationMethod(nicCtx.nicStateWithDesc.description.assignment.name());

        return publicIPAddress;
    }

    private void createSecurityGroupsIfNotExist(AzureInstanceContext ctx,
            AzureInstanceStage nextStage) {

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

    private NetworkSecurityGroup newAzureSecurityGroup(AzureInstanceContext ctx,
            SecurityGroupState sg) {

        if (sg == null) {
            throw new IllegalStateException("SecurityGroup state should not be null.");
        }

        List<SecurityRule> securityRules = new ArrayList<>();
        final AtomicInteger priority = new AtomicInteger(1000);
        if (sg.ingress != null) {
            sg.ingress.forEach(rule -> securityRules.add(newAzureSecurityRule(rule,
                    AZURE_SECURITY_GROUP_DIRECTION_INBOUND, priority.getAndIncrement())));
        }

        priority.set(1000);
        if (sg.egress != null) {
            sg.egress.forEach(rule -> securityRules.add(newAzureSecurityRule(rule,
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

        final String nsgName = nicCtx.securityGroupState().name;

        final String nsgRGName = nicCtx.securityGroupRGState != null
                ? nicCtx.securityGroupRGState.name
                : ctx.resourceGroup.getName();

        final String msg = "Create Azure Security Group["
                + nsgRGName + "/" + nsgName
                + "] for [" + nicCtx.nicStateWithDesc.name + "] NIC for ["
                + ctx.vmName
                + "] VM";

        AzureProvisioningCallback<NetworkSecurityGroup> handler = new AzureProvisioningCallback<NetworkSecurityGroup>(
                this, msg) {
            @Override
            protected DeferredResult<NetworkSecurityGroup> consumeProvisioningSuccess(
                    NetworkSecurityGroup securityGroup) {

                nicCtx.securityGroup = securityGroup;

                return DeferredResult.completed(securityGroup);
            }

            @Override
            protected Runnable checkProvisioningStateCall(
                    ServiceCallback<NetworkSecurityGroup> checkProvisioningStateCallback) {
                return () -> azureClient.getAsync(
                        nsgRGName,
                        nsgName,
                        null /* expand */,
                        checkProvisioningStateCallback);
            }

            @Override
            protected String getProvisioningState(NetworkSecurityGroup body) {
                return body.getProvisioningState();
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
        NetworkInterfacesOperations azureClient = getNetworkManagementClient(ctx)
                .getNetworkInterfacesOperations();
        // }}

        for (AzureNicContext nicCtx : ctx.nics) {

            final NetworkInterface nic = newAzureNetworkInterface(ctx, nicCtx);

            final String nicName = nicCtx.nicStateWithDesc.name;

            String msg = "Creating Azure NIC [" + nicName + "] for [" + ctx.vmName + "] VM";

            azureClient.createOrUpdateAsync(
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
        if (ctx.childAuth != null) {
            osProfile.setAdminUsername(ctx.childAuth.userEmail);
            osProfile.setAdminPassword(EncryptionUtils.decrypt(ctx.childAuth.privateKey));
        }
        if (cloudConfig != null) {
            try {
                osProfile.setCustomData(Base64.getEncoder()
                        .encodeToString(cloudConfig.getBytes(Utils.CHARSET)));
            } catch (UnsupportedEncodingException e) {
                logWarning(() -> "Error encoding user data");
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
        if (ctx.bootDisk.capacityMBytes > 31744
                && ctx.bootDisk.capacityMBytes < AZURE_MAXIMUM_OS_DISK_SIZE_MB) { // In case
            // custom boot disk size is set then use that value. If value more than maximum
            // allowed then proceed with default size.
            int diskSizeInGB = (int) ctx.bootDisk.capacityMBytes / 1024; // Converting MBs to GBs
                                                                         // and
            // casting as int
            osDisk.setDiskSizeGB(diskSizeInGB);
        } else {
            logInfo(String.format("Proceeding with Default OS Disk Size defined by VHD %s",
                    vhdName));
        }

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

        logFine(() -> String.format("Creating virtual machine with name [%s]", vmName));

        getComputeManagementClient(ctx).getVirtualMachinesOperations().createOrUpdateAsync(
                ctx.resourceGroup.getName(), vmName, request,
                new AzureAsyncCallback<VirtualMachine>() {
                    @Override
                    public void onError(Throwable e) {
                        handleCloudError(
                                String.format("Provisioning VM %s: FAILED. Details:", vmName), ctx,
                                COMPUTE_NAMESPACE, e);
                    }

                    @Override
                    public void onSuccess(ServiceResponse<VirtualMachine> result) {
                        VirtualMachine vm = result.getBody();
                        logFine(() -> String.format("Successfully created vm [%s]", vm.getName()));

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
     * Gets the public IP address from the VM (if exists) and patches the compute state and primary
     * NIC state.
     */
    private void getPublicIpAddress(AzureInstanceContext ctx, AzureInstanceStage nextStage) {
        if (ctx.getPrimaryNic().publicIP == null) {
            // No public IP address created.
            handleAllocation(ctx, nextStage);
            return;
        }

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
                                        logFine(() -> String.format("Patching compute state with VM"
                                                + " Public IP address [%s]: SUCCESS",
                                                computeState.address));
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
        StorageManagementClient client = getStorageManagementClient(ctx);

        client.getStorageAccountsOperations().listKeysAsync(ctx.storageAccountRGName,
                ctx.storageAccountName, new AzureAsyncCallback<StorageAccountKeys>() {
                    @Override
                    public void onError(Throwable e) {
                        handleError(ctx, e);
                    }

                    @Override
                    public void onSuccess(ServiceResponse<StorageAccountKeys> result) {
                        logFine(() -> String.format("Retrieved the storage account keys for storage"
                                + " account [%s].", ctx.storageAccountName));
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
            CloudError body = ce.getBody();
            if (body != null) {
                String code = body.getCode();
                if (MISSING_SUBSCRIPTION_CODE.equals(code)) {
                    registerSubscription(ctx, namespace);
                    return;
                } else if (INVALID_PARAMETER.equals(code) || INVALID_RESOURCE_GROUP.equals(code)) {
                    String invalidParameterMsg = String.format("%s Invalid parameter. %s",
                            msg, body.getMessage());

                    e = new IllegalStateException(invalidParameterMsg, ctx.error);
                    handleError(ctx, e);
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
                            logInfo(() -> String.format("%s namespace registration in %s state",
                                    namespace, registrationState));
                            long retryExpiration = Utils.getNowMicrosUtc()
                                    + DEFAULT_EXPIRATION_INTERVAL_MICROS;
                            getSubscriptionState(ctx, namespace, retryExpiration);
                            return;
                        }
                        logFine(() -> String.format("Successfully registered namespace [%s]",
                                provider.getNamespace()));
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
                                    logInfo(() -> String.format(
                                            "%s namespace registration in %s state",
                                            namespace, registrationState));
                                    getSubscriptionState(ctx, namespace, retryExpiration);
                                    return;
                                }
                                logFine(() -> String.format(
                                        "Successfully registered namespace [%s]",
                                        provider.getNamespace()));
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
            logFine(() -> String.format("Getting the latest version for %s:%s:%s",
                    imageReference.getPublisher(), imageReference.getOffer(),
                    imageReference.getSku()));
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

        logFine(() -> String.format("URN of the OS - %s:%s:%s:%s", imageReference.getPublisher(),
                imageReference.getOffer(), imageReference.getSku(), version));
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
                        logFine(() -> String.format("Retrieved the operating system family - %s",
                                ctx.operatingSystemFamily));
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

    public class StorageAccountAsyncHandler extends AzureProvisioningCallback<StorageAccount> {

        private AzureInstanceContext ctx;
        private StorageAccountsOperations azureSAClient;

        public StorageAccountAsyncHandler(AzureInstanceContext ctx,
                StorageAccountsOperations azureSAClient,
                StatelessService service, String message) {
            super(service, message);
            this.ctx = ctx;
            this.azureSAClient = azureSAClient;
        }

        @Override
        protected Throwable consumeError(Throwable exc) {
            exc = super.consumeError(exc);
            if (exc instanceof CloudException) {
                CloudException azureExc = (CloudException) exc;
                if (STORAGE_ACCOUNT_ALREADY_EXIST
                        .equalsIgnoreCase(azureExc.getBody().getCode())) {
                    return RECOVERED;
                }
            }
            return exc;
        }

        @Override
        protected DeferredResult<StorageAccount> consumeProvisioningSuccess(
                StorageAccount sa) {
            this.ctx.storage = sa;

            return createStorageDescription(this.ctx)
                    // Start next op, patch boot disk, in the sequence
                    .thenCompose((woid) -> {
                        Operation patchBootDiskOp = Operation
                                .createPatch(this.ctx.computeRequest
                                        .buildUri(this.ctx.bootDisk.documentSelfLink))
                                .setBody(this.ctx.bootDisk);
                        return sendWithDeferredResult(patchBootDiskOp).thenRun(() -> {
                            logFine(() -> String.format("Updating boot disk [%s]: SUCCESS",
                                    this.ctx.bootDisk.name));
                        });
                    })
                    // Return processed context with StorageAccount
                    .thenApply((woid) -> {
                        return this.ctx.storage;
                    });
        }

        /**
         * Based on the queried result, in case no SA description exists for the given name, create
         * a new one. For this purpose, StorageAccountKeys should be obtained, and with them
         * AuthCredentialsServiceState is created, and a StorageDescription, pointing to that
         * authentication description document.
         */
        private DeferredResult<StorageDescription> createStorageDescription(
                AzureInstanceContext ctx) {

            String msg = "Getting Azure StorageAccountKeys for [" + ctx.storage.getName()
                    + "] Storage Account";
            AzureDeferredResultServiceCallback<StorageAccountKeys> handler = new AzureDeferredResultServiceCallback<StorageAccountKeys>(
                    this.service, msg) {

                @Override
                protected Throwable consumeError(Throwable exc) {
                    return new IllegalStateException(String.format(
                            "Getting Azure StorageAccountKeys for [%s] Storage Account: FAILED. Details: %s",
                            ctx.storage.getName(), exc.getMessage()));
                }

                @Override
                protected DeferredResult<StorageAccountKeys> consumeSuccess(
                        StorageAccountKeys body) {
                    logFine(() -> String.format(
                            "Getting Azure StorageAccountKeys for [%s] Storage Account: SUCCESS",
                            ctx.storage.getName()));
                    return DeferredResult.completed(body);
                }
            };

            this.azureSAClient.listKeysAsync(ctx.storageAccountRGName, ctx.storage.getName(),
                    handler);

            return handler.toDeferredResult()
                    .thenCompose(keys -> {
                        Operation createStorageDescOp = Operation
                                .createPost(getHost(), StorageDescriptionService.FACTORY_LINK)
                                .setBody(AzureUtils.constructStorageDescription(
                                        getHost(), getSelfLink(),
                                        this.ctx.storage, ctx, keys))
                                .setReferer(getUri());
                        return sendWithDeferredResult(createStorageDescOp,
                                StorageDescription.class);
                    })
                    .thenCompose(storageDescription -> {
                        this.ctx.storageDescription = storageDescription;
                        this.ctx.bootDisk.storageDescriptionLink = storageDescription.documentSelfLink;

                        return DeferredResult.completed(this.ctx.storageDescription);
                    });
        }

        @Override
        protected String getProvisioningState(StorageAccount sa) {
            ProvisioningState provisioningState = sa.getProvisioningState();

            // For some reason SA.provisioningState is null, so consider it CREATING.
            if (provisioningState == null) {
                return ProvisioningState.CREATING.name();
            }

            return provisioningState.name();
        }

        @Override
        protected Runnable checkProvisioningStateCall(
                ServiceCallback<StorageAccount> checkProvisioningStateCallback) {
            return () -> this.azureSAClient.getPropertiesAsync(
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
        public final void onSuccess(ServiceResponse<T> result) {

            if (this.callCtx.hasAnyFailed.get()) {
                AzureInstanceService.this.logFine(this.msg + ": SUCCESS. Still batch calls have "
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
