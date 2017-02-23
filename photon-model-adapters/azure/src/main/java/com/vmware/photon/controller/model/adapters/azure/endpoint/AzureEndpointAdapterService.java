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
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.USER_LINK_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.ZONE_KEY;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_TENANT_ID;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.cleanUpHttpClient;
import static com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.getAzureConfig;
import static com.vmware.xenon.common.Operation.STATUS_CODE_UNAUTHORIZED;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

import com.microsoft.azure.management.resources.SubscriptionClient;
import com.microsoft.azure.management.resources.SubscriptionClientImpl;
import com.microsoft.azure.management.resources.models.Subscription;
import com.microsoft.rest.ServiceCallback;
import com.microsoft.rest.ServiceResponse;

import okhttp3.OkHttpClient;

import retrofit2.Retrofit;

import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.EndpointAdapterUtils;
import com.vmware.photon.controller.model.adapters.util.EndpointAdapterUtils.Retriever;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
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

        EndpointAdapterUtils.handleEndpointRequest(this, op, body, credentials(),
                computeDesc(), compute(), endpoint(), validate(body));
    }

    private BiConsumer<EndpointService.EndpointState,Retriever> endpoint() {
        return (e , r) -> {
            e.endpointProperties.put(PRIVATE_KEYID_KEY, r.getRequired(PRIVATE_KEYID_KEY));
            e.endpointProperties.put(USER_LINK_KEY, r.getRequired(USER_LINK_KEY));
            e.endpointProperties.put(AZURE_TENANT_ID, r.getRequired(AZURE_TENANT_ID));

            r.get(REGION_KEY).ifPresent(rk -> e.endpointProperties.put(REGION_KEY, rk));
            r.get(ZONE_KEY).ifPresent(zk -> e.endpointProperties.put(ZONE_KEY, zk));
        };
    }

    private BiConsumer<AuthCredentialsServiceState, BiConsumer<ServiceErrorResponse, Throwable>> validate(
            EndpointConfigRequest body) {
        return (credentials, callback) -> {
            OkHttpClient httpClient = new OkHttpClient();
            try {
                SubscriptionClient subscriptionClient = new SubscriptionClientImpl(
                        AzureConstants.BASE_URI, getAzureConfig(credentials),
                        httpClient.newBuilder(),
                        getRetrofitBuilder());

                subscriptionClient.getSubscriptionsOperations().getAsync(
                        credentials.userLink, new ServiceCallback<Subscription>() {
                            @Override
                            public void failure(Throwable e) {
                                // Azure doesn't send us any meaningful status code to work with
                                ServiceErrorResponse rsp = new ServiceErrorResponse();
                                rsp.message = "Invalid Azure credentials";
                                rsp.statusCode = STATUS_CODE_UNAUTHORIZED;
                                callback.accept(rsp, e);
                            }

                            @Override
                            public void success(ServiceResponse<Subscription> result) {
                                Subscription subscription = result.getBody();
                                logFine(() -> String.format("Got subscription %s with id %s",
                                        subscription.getDisplayName(),
                                        subscription.getId()));
                                callback.accept(null, null);
                            }
                        });
            } catch (Throwable e) {
                logSevere(e);
                ServiceErrorResponse rsp = new ServiceErrorResponse();
                rsp.message = "Invalid Azure credentials";
                rsp.statusCode = STATUS_CODE_UNAUTHORIZED;
                callback.accept(rsp, e);
            } finally {
                cleanUpHttpClient(this, httpClient);
            }
        };
    }

    private BiConsumer<AuthCredentialsServiceState, Retriever> credentials() {
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
        };
    }

    private BiConsumer<ComputeState, Retriever> compute() {
        return (c, r) -> {
            c.type = ComputeType.VM_HOST;
            c.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;
            c.adapterManagementReference = UriUtils.buildUri("https://management.azure.com");
        };
    }

    private Retrofit.Builder getRetrofitBuilder() {
        Retrofit.Builder builder = new Retrofit.Builder();
        builder.callbackExecutor(this.executorService);
        return builder;
    }
}
