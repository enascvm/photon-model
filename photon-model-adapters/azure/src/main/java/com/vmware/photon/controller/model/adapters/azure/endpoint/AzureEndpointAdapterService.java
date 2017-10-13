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

package com.vmware.photon.controller.model.adapters.azure.endpoint;

import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.REGION_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.SUPPORT_DATASTORES;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.SUPPORT_PUBLIC_IMAGES;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.USER_LINK_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.ZONE_KEY;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AUTHORIZATION_NAMESPACE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AUTH_HEADER_BEARER_PREFIX;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_PROVISIONING_PERMISSION;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_TENANT_ID;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.PROVIDER_PERMISSIONS_URI;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.PROVIDER_REST_API_VERSION;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.QUERY_PARAM_API_VERSION;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.cleanUpHttpClient;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.getAzureConfig;
import static com.vmware.photon.controller.model.adapters.util.AdapterConstants.PHOTON_MODEL_ADAPTER_UNAUTHORIZED_MESSAGE;
import static com.vmware.photon.controller.model.adapters.util.AdapterConstants.PHOTON_MODEL_ADAPTER_UNAUTHORIZED_MESSAGE_CODE;
import static com.vmware.xenon.common.Operation.STATUS_CODE_UNAUTHORIZED;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

import com.microsoft.azure.management.resources.SubscriptionState;
import com.microsoft.azure.management.resources.implementation.SubscriptionClientImpl;
import com.microsoft.azure.management.resources.implementation.SubscriptionInner;

import com.microsoft.rest.RestClient;

import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.RequestType;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.model.permission.Permission;
import com.vmware.photon.controller.model.adapters.azure.model.permission.PermissionList;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.EndpointAdapterUtils;
import com.vmware.photon.controller.model.adapters.util.EndpointAdapterUtils.Retriever;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Adapter to validate and enhance Azure based endpoints.
 *
 */
public class AzureEndpointAdapterService extends StatelessService {

    public static final String SELF_LINK = AzureUriPaths.AZURE_ENDPOINT_CONFIG_ADAPTER;

    private ExecutorService executorService;

    @Override
    public void handleStart(Operation startPost) {
        this.executorService = getHost().allocateExecutor(this);

        super.handleStart(startPost);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        EndpointConfigRequest body = op.getBody(EndpointConfigRequest.class);

        if (body.requestType == RequestType.CHECK_IF_ACCOUNT_EXISTS) {
            checkIfAccountExistsAndGetExistingDocuments(body, op);
            return;
        }

        EndpointAdapterUtils.handleEndpointRequest(this, op, body, credentials(),
                computeDesc(), compute(), endpoint(), validate(body));
    }

    public static BiConsumer<EndpointService.EndpointState,Retriever> endpoint() {
        return (e , r) -> {
            e.endpointProperties.put(PRIVATE_KEYID_KEY, r.getRequired(PRIVATE_KEYID_KEY));
            e.endpointProperties.put(USER_LINK_KEY, r.getRequired(USER_LINK_KEY));
            e.endpointProperties.put(AZURE_TENANT_ID, r.getRequired(AZURE_TENANT_ID));

            r.get(REGION_KEY).ifPresent(rk -> e.endpointProperties.put(REGION_KEY, rk));
            r.get(ZONE_KEY).ifPresent(zk -> e.endpointProperties.put(ZONE_KEY, zk));
            r.get(AZURE_PROVISIONING_PERMISSION).ifPresent(pr -> e.endpointProperties.put
                    (AZURE_PROVISIONING_PERMISSION, pr));

            // Azure end-point does support public images enumeration
            e.endpointProperties.put(SUPPORT_PUBLIC_IMAGES, Boolean.TRUE.toString());
            // Azure end-point does have the notion of datastores (storage accounts).
            e.endpointProperties.put(SUPPORT_DATASTORES, Boolean.TRUE.toString());
        };
    }

    private BiConsumer<AuthCredentialsServiceState, BiConsumer<ServiceErrorResponse, Throwable>> validate(
            EndpointConfigRequest body) {
        return (credentials, callback) -> {
            RestClient restClient = AzureUtils.buildRestClient(getAzureConfig(credentials), this.executorService);
            try {
                SubscriptionClientImpl subscriptionClient = new SubscriptionClientImpl(restClient);

                String msg = "Getting Azure Subscription [" + credentials.userLink
                        + "] for endpoint validation";

                AzureDeferredResultServiceCallback<SubscriptionInner> handler = new AzureDeferredResultServiceCallback<SubscriptionInner>(
                        this, msg) {
                    @Override
                    protected DeferredResult<SubscriptionInner> consumeSuccess(
                            SubscriptionInner subscription) {
                        logFine(() -> String.format("Got subscription %s with id %s",
                                        subscription.displayName(),
                                        subscription.id()));

                        if (!SubscriptionState.ENABLED.equals(subscription.state())) {
                            logFine(() ->
                                    String.format("Subscription with id %s is not in active"
                                            + " state but in %s",
                                    subscription.id(),
                                    subscription.state()));
                            return DeferredResult.failed(
                                    new IllegalStateException("Subscription is not active"));
                        }
                        return DeferredResult.completed(subscription);
                    }
                };

                subscriptionClient.subscriptions()
                        .getAsync(credentials.userLink, handler);

                String shouldProvision = body.endpointProperties.get(AZURE_PROVISIONING_PERMISSION);

                handler.toDeferredResult()
                        .thenCompose(
                                subscription -> getPermissions(credentials))
                        .thenCompose(permissionList -> verifyPermissions(permissionList,
                                Boolean.parseBoolean(shouldProvision)))
                        .whenComplete((aVoid, e) -> {
                            if (e != null) {
                                if (e instanceof CompletionException) {
                                    e = e.getCause();
                                }
                                // Azure doesn't send us any meaningful status code to work with
                                LocalizableValidationException localizableValidationException = new LocalizableValidationException(
                                        e, PHOTON_MODEL_ADAPTER_UNAUTHORIZED_MESSAGE,
                                        PHOTON_MODEL_ADAPTER_UNAUTHORIZED_MESSAGE_CODE);

                                ServiceErrorResponse rsp = Utils.toServiceErrorResponse
                                        (localizableValidationException);
                                rsp.statusCode = STATUS_CODE_UNAUTHORIZED;
                                callback.accept(rsp, localizableValidationException);
                                return;
                            }
                            callback.accept(null, null);
                        });
            } catch (Throwable e) {
                logSevere(e);
                ServiceErrorResponse rsp = new ServiceErrorResponse();
                rsp.message = "Invalid Azure credentials";
                rsp.statusCode = STATUS_CODE_UNAUTHORIZED;
                callback.accept(rsp, e);
            } finally {
                cleanUpHttpClient(restClient);

            }
        };
    }

    public static BiConsumer<AuthCredentialsServiceState, Retriever> credentials() {
        return (c, r) -> {
            // overwrite fields that are set in endpointProperties, otherwise use the present ones
            if (c.privateKey != null) {
                r.get(PRIVATE_KEY_KEY).ifPresent(pKey -> c.privateKey = pKey);
            } else {
                c.privateKey = r.getRequired(PRIVATE_KEY_KEY);
            }

            if (c.privateKeyId != null) {
                r.get(PRIVATE_KEYID_KEY).ifPresent(pKeyId -> c.privateKeyId = pKeyId);
            } else {
                c.privateKeyId = r.getRequired(PRIVATE_KEYID_KEY);
            }

            if (c.userLink != null) {
                r.get(USER_LINK_KEY).ifPresent(pKeyId -> c.userLink = pKeyId);
            } else {
                c.userLink = r.getRequired(USER_LINK_KEY);
            }

            if (c.customProperties != null && c.customProperties.containsKey(AZURE_TENANT_ID)) {
                r.get(AZURE_TENANT_ID).ifPresent(tenant -> c.customProperties.put(AZURE_TENANT_ID, tenant));
            } else {
                c.customProperties = new HashMap<>();
                c.customProperties.put(AZURE_TENANT_ID, r.getRequired(AZURE_TENANT_ID));
            }
        };
    }

    private BiConsumer<ComputeDescription, Retriever> computeDesc() {
        return (cd, r) -> {
            Optional<String> regionId = r.get(REGION_KEY);
            if (regionId.isPresent()) {
                cd.regionId = regionId.get();
                cd.zoneId = r.get(ZONE_KEY).orElse(cd.regionId);
            }

            cd.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;

            List<String> children = new ArrayList<>();
            children.add(ComputeType.VM_GUEST.toString());
            cd.supportedChildren = children;

            cd.instanceAdapterReference = AdapterUriUtil.buildAdapterUri(this.getHost(),
                    AzureUriPaths.AZURE_INSTANCE_ADAPTER);
            cd.enumerationAdapterReference = AdapterUriUtil.buildAdapterUri(this.getHost(),
                    AzureUriPaths.AZURE_ENUMERATION_ADAPTER);
            cd.statsAdapterReference = AdapterUriUtil.buildAdapterUri(this.getHost(),
                    AzureUriPaths.AZURE_STATS_ADAPTER);
            cd.powerAdapterReference = AdapterUriUtil.buildAdapterUri(this.getHost(),
                    AzureUriPaths.AZURE_POWER_ADAPTER);
            cd.diskAdapterReference = AdapterUriUtil.buildAdapterUri(this.getHost(),
                    AzureUriPaths.AZURE_DISK_ADAPTER);
        };
    }

    private BiConsumer<ComputeState, Retriever> compute() {
        return (c, r) -> {
            c.type = ComputeType.VM_HOST;
            c.regionId = r.get(REGION_KEY).orElse(null);
            c.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;
            c.adapterManagementReference = UriUtils.buildUri("https://management.azure.com");
            if (c.customProperties == null) {
                c.customProperties = new HashMap<>();
            }
            c.customProperties
                    .put(PhotonModelConstants.CLOUD_ACCOUNT_ID, r.getRequired(USER_LINK_KEY));
        };
    }

    private DeferredResult<PermissionList> getPermissions(
            AuthCredentialsServiceState credentials) {

        logFine(() -> String.format("Retrieving permissions for subscription with id [%s]",
                credentials.userLink));

        String uriStr = AdapterUriUtil.expandUriPathTemplate(PROVIDER_PERMISSIONS_URI,
                credentials.userLink, AUTHORIZATION_NAMESPACE);
        URI uri = UriUtils.extendUriWithQuery(UriUtils.buildUri(uriStr),
                QUERY_PARAM_API_VERSION, PROVIDER_REST_API_VERSION);

        Operation operation = Operation.createGet(uri);
        operation.addRequestHeader(Operation.ACCEPT_HEADER, Operation.MEDIA_TYPE_APPLICATION_JSON);
        operation.addRequestHeader(Operation.CONTENT_TYPE_HEADER,
                Operation.MEDIA_TYPE_APPLICATION_JSON);

        try {
            operation.addRequestHeader(Operation.AUTHORIZATION_HEADER,
                    AUTH_HEADER_BEARER_PREFIX + getAzureConfig(credentials).getToken(AzureUtils.getAzureBaseUri()));
        } catch (IOException e) {
            return DeferredResult.failed(e);
        }
        return sendWithDeferredResult(operation, PermissionList.class);
    }

    private DeferredResult<Void> verifyPermissions(PermissionList permissions, boolean
            shouldProvision) {

        if (permissions == null || permissions.value == null) {
            throw new LocalizableValidationException("The account does not have permissions",
                    "adapter.azure.permission.empty");
        }
        if (shouldProvision && permissions.value.stream().noneMatch(this::canProvision)) {
            throw new LocalizableValidationException("The account does not have permissions for "
                    + "provisioning", "adapter.azure.permission.provision");
        }
        if (!shouldProvision && permissions.value.stream().noneMatch(this::canRead)) {
            throw new LocalizableValidationException(
                    "The account does not have permissions for read",
                    "adapter.azure.permission.read");
        }

        return DeferredResult.completed(null);
    }

    private boolean canProvision(Permission permission) {
        return permission.isOwner() || permission.isContributor();
    }

    private boolean canRead(Permission permission) {
        return permission.isOwner() || permission.isReader() || permission.isContributor();
    }

    //TODO https://jira-hzn.eng.vmware.com/browse/VSYM-8582
    private void checkIfAccountExistsAndGetExistingDocuments(EndpointConfigRequest req,
            Operation op) {
        req.accountAlreadyExists = false;
        op.setBody(req);
        op.complete();
    }
}
