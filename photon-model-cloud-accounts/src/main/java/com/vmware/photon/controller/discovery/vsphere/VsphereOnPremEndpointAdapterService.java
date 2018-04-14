/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.discovery.vsphere;

import static com.vmware.photon.controller.model.UriPaths.CCS_VALIDATE_SERVICE;
import static com.vmware.photon.controller.model.UriPaths.VSPHERE_ENDPOINT_ADAPTER_PATH;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import com.vmware.photon.controller.discovery.vsphere.ExecuteCommandRequest.ExecuteCommandRequestBuilder;
import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.adapters.util.EndpointAdapterUtils;
import com.vmware.photon.controller.model.adapters.util.EndpointAdapterUtils.Retriever;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;

/**
 * Adapter for on prem vsphere endpoint
 */
public class VsphereOnPremEndpointAdapterService extends StatelessService {
    public static final String SELF_LINK = VSPHERE_ENDPOINT_ADAPTER_PATH;

    public String ccsHost;

    public static final String HOST_NAME_KEY = "hostName";
    public static final String DC_ID_KEY = "dcId";
    public static final String DEFAULT_DC_ID = "default-dc-id";
    public static final String DC_NAME_KEY = "dcName";
    public static final String DEFAULT_DC_NAME = "RDC-1";
    public static final String PRIVATE_CLOUD_NAME_KEY = "privateCloudName";
    public static final String ENDPOINT_PROPERTIES_KEY = "endpointProperties";

    // This id should be fixed for this specific command.
    private static final String COMMAND_DEFINITION_ID = "f093a5786f24688d";
    private static final String ENDPOINT_KEY = "endpoint";
    private static final String USERNAME_TYPE = "Username";

    public VsphereOnPremEndpointAdapterService(String ccsHost) throws Exception {
        if (ccsHost == null) {
            logWarning("Validator service URI not provided. vSphere validation will default to TRUE.");
        }
        this.ccsHost = ccsHost;
    }

    public VsphereOnPremEndpointAdapterService() throws Exception {
        this(null);
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

    private BiConsumer<AuthCredentialsService.AuthCredentialsServiceState, Retriever> credentials() {
        return (c, r) -> {
            c.privateKey = r.getRequired(PRIVATE_KEY_KEY);
            c.privateKeyId = r.getRequired(PRIVATE_KEYID_KEY);
            c.type = USERNAME_TYPE;
        };
    }

    private BiConsumer<ComputeDescriptionService.ComputeDescription, Retriever> computeDesc() {
        return (cd, r) -> {
            AuthCredentialsService.AuthCredentialsServiceState ac = new AuthCredentialsService.AuthCredentialsServiceState();
            credentials().accept(ac, r);
        };
    }

    private BiConsumer<ComputeService.ComputeState, Retriever> compute() {
        return (c, r) -> {

            // Set the type as ENDPOINT_HOST for private clouds too
            c.type = ComputeDescriptionService.ComputeDescription.ComputeType.ENDPOINT_HOST;

            c.address = r.getRequired(HOST_NAME_KEY);
            c.customProperties = new HashMap<String, String>();
            c.customProperties.put(PRIVATE_CLOUD_NAME_KEY, r.getRequired(PRIVATE_CLOUD_NAME_KEY));
            c.customProperties.put(DC_ID_KEY, r.get(DC_ID_KEY).orElse(DEFAULT_DC_ID));
            c.customProperties.put(DC_NAME_KEY, r.get(DC_NAME_KEY).orElse(DEFAULT_DC_NAME));
        };
    }

    private BiConsumer<EndpointService.EndpointState,Retriever> endpoint() {
        return (e , r) -> {
            e.endpointProperties = new HashMap<>();
            e.endpointProperties.put(HOST_NAME_KEY, r.getRequired(HOST_NAME_KEY));
            e.endpointProperties.put(PRIVATE_CLOUD_NAME_KEY, r.getRequired(PRIVATE_CLOUD_NAME_KEY));
            e.endpointProperties.put(DC_ID_KEY, r.get(DC_ID_KEY).orElse(DEFAULT_DC_ID));
            e.endpointProperties.put(DC_NAME_KEY, r.get(DC_NAME_KEY).orElse(DEFAULT_DC_NAME));
        };
    }

    private BiConsumer<AuthCredentialsService.AuthCredentialsServiceState,
            BiConsumer<ServiceErrorResponse, Throwable>> validate ( EndpointConfigRequest body) {
        return (credentials, callback) -> {

            if (this.ccsHost == null) {
                // Accept the validation if ccsHost is null, because some envs don't have the
                // service running.
                callback.accept(null, null);
                return;
            }
            String privateKeyId = body.endpointProperties.get(PRIVATE_KEYID_KEY);
            String privateKey = body.endpointProperties.get(PRIVATE_KEY_KEY);
            String dcId = body.endpointProperties.get(DC_ID_KEY);
            String hostName = body.endpointProperties.get(HOST_NAME_KEY);

            ExecuteCommandRequestBuilder executeCommandRequestBuilder = new ExecuteCommandRequestBuilder();

            String validatePayload = String.format("{\"%s\":{\"%s\":\"%s\",\"%s\":\"%s\"," +
                                    "\"%s\":\"%s\"}}", ENDPOINT_PROPERTIES_KEY, HOST_NAME_KEY, hostName,
                            PRIVATE_KEYID_KEY, privateKeyId, PRIVATE_KEY_KEY, privateKey);

            ExecuteCommandRequest executeCommandRequest = executeCommandRequestBuilder.withCommand
                    (COMMAND_DEFINITION_ID).withTargetProxy(dcId).withVariable(ENDPOINT_KEY,
                    validatePayload).build();

            try {
                Operation sendOp = Operation.createPost(UriUtils.buildUri(new URI(this.ccsHost),
                        CCS_VALIDATE_SERVICE))
                        .setBody(executeCommandRequest)
                        .setContentType(Operation.MEDIA_TYPE_APPLICATION_JSON)
                        .setCompletion((o, e) -> {
                            if (e != null) {
                                callback.accept(o.getErrorResponseBody(), e);
                                return;
                            }
                            try {
                                CommandResponse commandResponse = o.getBody(CommandResponse.class);

                                RestResponse restResponse = commandResponse.restResponses.get(dcId);

                                if (restResponse.responseCode == Operation.STATUS_CODE_OK) {
                                    callback.accept(null, null);
                                    return;
                                }

                                handleError(callback, restResponse.responseBodyRaw);

                            } catch (Exception exception) {
                                handleError(callback, exception.getMessage());
                            }
                        });

                sendRequest(sendOp);
            } catch (URISyntaxException e) {
                callback.accept(null, e);
            }
        };
    }

    private void handleError(BiConsumer<ServiceErrorResponse, Throwable> callback, String message) {
        Exception exception = new Exception(message);
        ServiceErrorResponse r = ServiceErrorResponse.create(exception,
                Operation.STATUS_CODE_BAD_REQUEST);
        callback.accept(r, exception);
    }

    /**
     * From CCS Team.
     * Based on type of command, read the response
     * All responses are stored against the proxy id
     */
    public static class CommandResponse {
        /**
         * Holds response if command is of type OS
         */
        public Map<String, OSResponse> osResponses = new HashMap<>();
        /**
         * Holds response if command is of type REST
         */
        public Map<String, RestResponse> restResponses = new HashMap<>();
        /**
         * Holds context object which was passed in during command execution
         */
        public Map<String, String> context = new HashMap<>();
        public Status status;
    }

    public enum Status {
        // Used in user command/ exposed outside module are START, PREPARED, IN_PROGRESS, FAILED, COMPLETED
        // used in command instance are START, SCHEDULED, ACCEPTED, IN_PROGRESS, EXECUTED, COMPLETED, FAILED, ABORTED
        START, PREPARED, SCHEDULED, ACCEPTED,  IN_PROGRESS, EXECUTED, FAILED, COMPLETED, ABORTED, CANCEL_SCHEDULED
    }

    public static class OSResponse extends CommandInstanceResponse {

        public OSResponse() {
            this.type =  ActionType.OS.name();
        }

        public int status;
        public String output;
        public String error;
    }

    public static class CommandInstanceResponse {
        public String type;
        public String response;
        // final response of an command instance, will be useful for periodic and group commands
        public Boolean isFinal;
    }

    public static class RestResponse extends CommandInstanceResponse {

        public RestResponse() {
            this.type = ActionType.REST.name();
        }

        public int responseCode;
        public Map<String, String> responseHeaders;
        public String responseBodyRaw;
        public String errorRaw;
    }

    public enum ActionType {
        REST, OS
    }
}
