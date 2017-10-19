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

package com.vmware.photon.controller.model.adapters.azure.ea.endpoint;

import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.REGION_KEY;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.getQueryResultLimit;

import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.function.BiConsumer;

import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.RequestType;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.EndpointAdapterUtils;
import com.vmware.photon.controller.model.adapters.util.EndpointAdapterUtils.Retriever;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Adapter to validate and enhance Azure EA endpoints
 */
public class AzureEaEndpointAdapterService extends StatelessService {
    public static final String SELF_LINK = AzureUriPaths.AZURE_EA_ENDPOINT_CONFIG_ADAPTER;

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
                computeDesc(), compute(), endpoint(), validate());
    }

    private BiConsumer<EndpointState, Retriever> endpoint() {
        return (e, r) -> {
            e.endpointProperties.put(PRIVATE_KEYID_KEY, r.getRequired(PRIVATE_KEYID_KEY));
            r.get(REGION_KEY).ifPresent(rk -> e.endpointProperties.put(REGION_KEY, rk));
            e.endpointProperties
                    .put(PhotonModelConstants.CLOUD_ACCOUNT_ID, r.getRequired(PRIVATE_KEYID_KEY));
        };
    }

    private BiConsumer<ComputeState, Retriever> compute() {
        return (c, r) -> {
            c.type = ComputeType.VM_HOST;
            c.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;
            c.adapterManagementReference = UriUtils.buildUri(AzureUtils.getAzureEaBaseUri());
            addEntryToCustomProperties(c, PhotonModelConstants.CLOUD_ACCOUNT_ID,
                    r.getRequired(PRIVATE_KEYID_KEY));
        };
    }

    private BiConsumer<ComputeDescription, Retriever> computeDesc() {
        return (cd, r) -> {
            cd.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;

            URI costStatsAdapterUri = AdapterUriUtil.buildAdapterUri(this.getHost(),
                    AzureUriPaths.AZURE_COST_STATS_ADAPTER);
            cd.statsAdapterReferences = new LinkedHashSet<>();
            cd.statsAdapterReferences.add(costStatsAdapterUri);
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
        };
    }

    private BiConsumer<AuthCredentialsServiceState,
            BiConsumer<ServiceErrorResponse, Throwable>> validate() {
        return (credentials, callback) -> {
            // Validate EA credentials
            String usageReportsUriStr = AdapterUriUtil
                    .expandUriPathTemplate(AzureConstants.AZURE_EA_USAGE_REPORTS_URI,
                            credentials.privateKeyId);
            Operation usageReportsOp = Operation
                    .createGet(UriUtils.buildUri(usageReportsUriStr))
                    .addRequestHeader(Operation.AUTHORIZATION_HEADER,
                            String.format(AzureConstants.AZURE_EA_AUTHORIZATION_HEADER_FORMAT,
                                    credentials.privateKey))
                    .setCompletion((op, e) -> {
                        if (e != null) {
                            ServiceErrorResponse rsp = new ServiceErrorResponse();
                            rsp.message = e.getMessage();
                            rsp.statusCode = op.getStatusCode();
                            callback.accept(rsp, e);
                            return;
                        }
                        callback.accept(null, null);
                    });
            this.sendRequest(usageReportsOp);
        };
    }

    private void addEntryToCustomProperties(ComputeState c, String key, String value) {
        if (c.customProperties == null) {
            c.customProperties = new HashMap<>();
        }
        c.customProperties.put(key, value);
    }

    private void checkIfAccountExistsAndGetExistingDocuments(EndpointConfigRequest req,
            Operation op) {
        String accountId = req.endpointProperties.get(PRIVATE_KEYID_KEY);
        if (accountId != null && !accountId.isEmpty() && req.tenantLinks != null &&
                !req.tenantLinks.isEmpty()) {
            QueryTask.Query.Builder qBuilder = QueryTask.Query.Builder.create()
                    .addKindFieldClause(ComputeState.class, QueryTask.Query.Occurance.SHOULD_OCCUR)
                    .addFieldClause(ComputeState.FIELD_NAME_TYPE, ComputeType.VM_HOST)
                    .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                            EndpointAllocationTaskService.CUSTOM_PROP_ENPOINT_TYPE,
                            PhotonModelConstants.EndpointType.azure_ea.name())
                    .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                            PhotonModelConstants.CLOUD_ACCOUNT_ID, accountId)
                    .addInCollectionItemClause(ComputeState.FIELD_NAME_TENANT_LINKS,
                            req.tenantLinks);

            QueryTask queryTask = QueryTask.Builder.createDirectTask()
                    .setQuery(qBuilder.build())
                    .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
                    .addOption(QueryTask.QuerySpecification.QueryOption.TOP_RESULTS)
                    .setResultLimit(getQueryResultLimit())
                    .build();

            queryTask.tenantLinks = req.tenantLinks;
            QueryUtils.startQueryTask(this, queryTask)
                    .whenComplete((qrt, e) -> {
                        if (e != null) {
                            logSevere(
                                    () -> String.format(
                                            "Failure retrieving query results for azure ea compute host corresponding to"
                                                    + "the account ID: %s", e.toString()));
                            op.fail(e);
                            return;
                        }
                        if (qrt.results.documentCount > 0) {
                            req.existingDocuments = new HashMap<>();
                            for (Object s : qrt.results.documents.values()) {
                                req.accountAlreadyExists = true;
                                ComputeState computeHost = Utils.fromJson(s,
                                        ComputeState.class);
                                req.existingDocuments.put(computeHost.documentSelfLink,
                                        computeHost);
                                getComputeDescription(req, computeHost.descriptionLink, op);
                            }
                        } else {
                            req.accountAlreadyExists = false;
                            op.setBody(req);
                            op.complete();
                            return;
                        }
                    });
        } else {
            req.accountAlreadyExists = false;
            op.setBody(req);
            op.complete();
        }
    }

    /**
     * Retrieves the compute description corresponding to the compute host for a given account id.
     */
    private void getComputeDescription(EndpointConfigRequest req, String descriptionLink,
            Operation op) {
        Operation.createGet(getHost(), descriptionLink)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logSevere(
                                () -> String.format(
                                        "Failure retrieving the azure compute host description "
                                                + "corresponding to the account ID: %s", ex.toString()));
                        op.fail(ex);
                        return;
                    }
                    ComputeDescription computeHostDescription = o.getBody(ComputeDescription.class);
                    req.existingDocuments.put(computeHostDescription.documentSelfLink,
                            computeHostDescription);
                    op.setBody(req);
                    op.complete();
                    return;
                }).sendWith(this);
    }
}
