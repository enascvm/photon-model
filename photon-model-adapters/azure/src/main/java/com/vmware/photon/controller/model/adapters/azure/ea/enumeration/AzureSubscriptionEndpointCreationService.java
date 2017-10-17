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

package com.vmware.photon.controller.model.adapters.azure.ea.enumeration;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.adapterapi.ResourceRequest;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.util.ClusterUtil;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 *  Service to create an Azure endpoint representing the subscriptionId passed  in the request.
 *  Along with the subscriptionId, it also needs the resourceReference to the Azure EA account
 *  under which the Azure endpoint representing the subscription must be created.
 */
public class AzureSubscriptionEndpointCreationService extends StatelessService {

    public static final String SELF_LINK = AzureUriPaths.AZURE_SUBSCRIPTION_ENDPOINT_CREATOR;

    //azure-subscriptionId
    private static final String COMPUTES_NAME_FORMAT = "%s-%s";

    public static class AzureSubscriptionEndpointCreationRequest extends ResourceRequest {
        public String subscriptionId;
        public String accountId;
    }

    public AzureSubscriptionEndpointCreationService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        AzureSubscriptionEndpointCreationRequest request =
                op.getBody(AzureSubscriptionEndpointCreationRequest.class);
        if (request.isMockRequest) {
            op.complete();
            return;
        }

        handleSubscriptionEndpointCreationRequest(request, op);
    }

    private void handleSubscriptionEndpointCreationRequest(
            AzureSubscriptionEndpointCreationRequest request, Operation parentOp) {
        // If request.subscriptionId is null, just complete the parentOp
        if (request.subscriptionId == null) {
            parentOp.complete();
            return;
        }

        // Fail the request if resourceReference to Azure EA endpoint is not provided
        if (request.resourceReference == null) {
            parentOp.fail(
                    new IllegalArgumentException("reference to parent endpointLink is required"));
        }

        Operation endpointStateOp = Operation.createGet(request.resourceReference)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        parentOp.fail(e);
                        return;
                    }
                    EndpointState endpointState = o.getBody(EndpointState.class);
                    createSubscriptionEndpoint(endpointState, request, parentOp);
                });
        this.sendRequest(endpointStateOp);
    }

    private void createSubscriptionEndpoint(EndpointState azureEaEndpoint,
            AzureSubscriptionEndpointCreationRequest request, Operation parentOp) {

        Operation authOp = Operation.createPost(UriUtils.extendUri(getInventoryServiceUri(),
                AuthCredentialsService.FACTORY_LINK));
        Operation cdOp = Operation.createPost(UriUtils.extendUri(getInventoryServiceUri(),
                ComputeDescriptionService.FACTORY_LINK));
        Operation csOp = Operation.createPost(UriUtils.extendUri(getInventoryServiceUri(),
                ComputeService.FACTORY_LINK));
        Operation endPointOp = Operation.createPost(UriUtils.extendUri(getInventoryServiceUri(),
                EndpointService.FACTORY_LINK));

        AuthCredentialsServiceState authCredentialsState =
                createAuthCredentialsState(azureEaEndpoint, request);
        EndpointState endpointState = createEndpointState(azureEaEndpoint, request);
        ComputeDescription computeDescState = AzureUtils
                .constructAzureSubscriptionComputeDescription(endpointState.documentSelfLink,
                azureEaEndpoint.tenantLinks, request.subscriptionId, null, null,
                        endpointState.computeLink);

        authOp.setBody(authCredentialsState);

        OperationSequence sequence = OperationSequence.create(authOp)
                .setCompletion((ops, exs) -> {
                    if (exs != null) {
                        handleFailure(exs, parentOp);
                        return;
                    }
                    Operation o = ops.get(authOp.getId());
                    AuthCredentialsServiceState authState = o
                            .getBody(AuthCredentialsServiceState.class);
                    computeDescState.authCredentialsLink = authState.documentSelfLink;
                    endpointState.authCredentialsLink = authState.documentSelfLink;
                    cdOp.setBody(computeDescState);
                })
                .next(cdOp)
                .setCompletion((ops, exs) -> {
                    if (exs != null) {
                        handleFailure(exs, parentOp);
                        return;
                    }
                    Operation o = ops.get(cdOp.getId());
                    ComputeDescription cd = o.getBody(ComputeDescription.class);
                    ComputeState cs = AzureUtils.constructAzureSubscriptionComputeState(
                            endpointState.documentSelfLink, cd.documentSelfLink,
                            azureEaEndpoint.tenantLinks, request.subscriptionId,
                            azureEaEndpoint.resourcePoolLink,
                            getCustomPropertiesMap(endpointState, request), null, endpointState.computeLink);
                    csOp.setBody(cs);
                    endpointState.computeDescriptionLink = cd.documentSelfLink;
                })
                .next(csOp)
                .setCompletion((ops, exs) -> {
                    if (exs != null) {
                        handleFailure(exs, parentOp);
                        return;
                    }
                    Operation o = ops.get(csOp.getId());
                    ComputeState cs = o.getBody(ComputeState.class);
                    endpointState.computeLink = cs.documentSelfLink;
                    endPointOp.setBody(endpointState);
                })
                .next(endPointOp)
                .setCompletion((ops, exs) -> {
                    if (exs != null) {
                        handleFailure(exs, parentOp);
                        return;
                    }
                    Operation o = ops.get(endPointOp.getId());
                    EndpointState es = o.getBody(EndpointState.class);
                    parentOp.setBody(es);
                    parentOp.complete();
                });
        sequence.sendWith(this);
    }

    private AuthCredentialsServiceState createAuthCredentialsState(EndpointState azureEaEndpoint,
               AzureSubscriptionEndpointCreationRequest request) {
        AuthCredentialsServiceState authCredState = new AuthCredentialsServiceState();
        authCredState.userLink = request.subscriptionId;
        authCredState.tenantLinks = azureEaEndpoint.tenantLinks;
        return authCredState;
    }

    private EndpointState createEndpointState(EndpointState azureEaEndpoint,
              AzureSubscriptionEndpointCreationRequest request) {
        EndpointState endpointState = new EndpointState();
        endpointState.documentSelfLink = UriUtils.buildUriPath(EndpointService.FACTORY_LINK,
                this.getHost().nextUUID());
        endpointState.endpointType = EndpointType.azure.name();
        endpointState.resourcePoolLink = azureEaEndpoint.resourcePoolLink;
        endpointState.tenantLinks = azureEaEndpoint.tenantLinks;
        Map<String, String> endpointProperties = new HashMap<>();
        endpointProperties.put(EndpointConfigRequest.USER_LINK_KEY, request.subscriptionId);
        endpointState.endpointProperties = endpointProperties;
        endpointState.parentLink = azureEaEndpoint.documentSelfLink;
        endpointState.name = String.format(COMPUTES_NAME_FORMAT, EndpointType.azure.name(),
                request.subscriptionId);
        return endpointState;
    }

    private Map<String, String> getCustomPropertiesMap(
            EndpointState azureEaEndpoint, AzureSubscriptionEndpointCreationRequest request) {
        Map<String, String> properties = new HashMap<>();
        properties.put(AzureConstants.AZURE_SUBSCRIPTION_ID_KEY, request.subscriptionId);
        properties.put(PhotonModelConstants.CLOUD_ACCOUNT_ID, request.subscriptionId);
        properties.put(AzureConstants.AZURE_ENROLLMENT_NUMBER_KEY,
                azureEaEndpoint.endpointProperties.get(EndpointConfigRequest.PRIVATE_KEYID_KEY));
        if (request.accountId != null) {
            properties.put(AzureConstants.AZURE_ACCOUNT_OWNER_EMAIL_ID, request.accountId);
        }
        return properties;
    }

    private void handleFailure(Map<Long, Throwable> exs,
                                      Operation parentOp) {
        AzureUtils.handleFailure(this, exs, parentOp);
    }

    private URI getInventoryServiceUri() {
        return ClusterUtil.getClusterUri(getHost(), ServiceTypeCluster.INVENTORY_SERVICE);
    }
}
