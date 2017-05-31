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

package com.vmware.photon.controller.model.resources;

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.LINK;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.util.Map;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Represents a endpoint resource.
 */
public class EndpointService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES + "/endpoints";

    /**
     * This class represents the document state associated with a {@link EndpointService} task.
     */
    public static class EndpointState extends ResourceState {

        public static final String FIELD_NAME_ENDPOINT_TYPE = "endpointType";
        public static final String FIELD_NAME_AUTH_CREDENTIALS_LINK = "authCredentialsLink";
        public static final String FIELD_NAME_ENDPOINT_PROPERTIES = "endpointProperties";

        @Documentation(description = "Endpoint type of the endpoint instance,e.g. aws,azure,...")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, REQUIRED },
                indexing = { PropertyIndexingOption.CASE_INSENSITIVE, PropertyIndexingOption.SORT })
        public String endpointType;

        @Documentation(description = "The link to the credentials to authenticate against this endpoint.")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, LINK })
        public String authCredentialsLink;

        @Documentation(description = "The link to the compute that represents this endpoint.")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, LINK })
        public String computeLink;

        @Documentation(description = "The link to the compute description that represents this endpoint.")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, LINK })
        public String computeDescriptionLink;

        @Documentation(description = "The link to the resource pool that is default for an endpoint.")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, LINK })
        public String resourcePoolLink;

        @Documentation(description = "Endpoint specific properties. The specific endpoint adapter"
                + " will extract them and enhance the linked Credentials,Compute and"
                + " ComputeDescription.")
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL }, indexing = {
                PropertyIndexingOption.CASE_INSENSITIVE,
                PropertyIndexingOption.EXPAND,
                PropertyIndexingOption.FIXED_ITEM_NAME })
        public Map<String, String> endpointProperties;

    }

    public static class EndpointStateWithCredentials extends EndpointState {
        public Map<String, String> credentials;
    }

    public EndpointService() {
        super(EndpointState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleDelete(Operation delete) {
        logInfo("Deleting Endpoint, Path: %s, Operation ID: %d, Referrer: %s",
                delete.getUri().getPath(), delete.getId(),
                delete.getRefererAsString());
        super.handleDelete(delete);
    }

    @Override
    public void handleStart(Operation start) {
        try {
            processInput(start);
            start.complete();
        } catch (Throwable t) {
            start.fail(t);
        }
    }

    @Override
    public void handlePut(Operation put) {
        put.fail(new UnsupportedOperationException(
                "PUT operation not supported on the Endpoint Service API."));
    }

    @Override
    public void handlePatch(Operation patch) {
        EndpointState currentState = getState(patch);
        EndpointState newState = patch.getBody(EndpointState.class);
        validateUpdates(currentState, newState);
        ResourceUtils.handlePatch(patch, currentState, getStateDescription(),
                EndpointState.class, null);
    }

    private EndpointState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        EndpointState state = op.getBody(EndpointState.class);
        Utils.validateState(getStateDescription(), state);
        if (state.name == null) {
            throw new IllegalArgumentException("name is required.");
        }
        return state;
    }

    private void validateUpdates(EndpointState currentState, EndpointState newState) {
        if (currentState == null || newState == null) {
            return;
        }
        if (currentState.endpointProperties == null || newState.endpointProperties == null) {
            return;
        }
        if (!currentState.endpointProperties.containsKey(EndpointConfigRequest.REGION_KEY)
                || !newState.endpointProperties.containsKey(EndpointConfigRequest.REGION_KEY)) {
            return;
        } else if (!currentState.endpointProperties.get(EndpointConfigRequest.REGION_KEY)
                .equalsIgnoreCase(
                        newState.endpointProperties.get(EndpointConfigRequest.REGION_KEY))) {
            throw new UnsupportedOperationException(
                    "Updates to regionId of existing endpoints is not a supported operation");
        }

    }
}
