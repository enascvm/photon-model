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

package com.vmware.photon.controller.model.adapters.vsphere;

import static com.vmware.xenon.common.UriUtils.buildFactoryUri;
import static com.vmware.xenon.common.UriUtils.buildUri;

import java.net.URI;

import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.adapters.vsphere.VSphereIncrementalEnumerationService.VSphereIncrementalEnumerationRequest;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Handles enumeration for vsphere endpoints.
 * vSphere enumeration can be requested with either START or REFRESH enumeration action.
 * <p>
 * When START is specified, this service performs a full enumeration and then starts collecting
 * incremental changes from endpoint by maintaining a session.
 * To Stop the enumeration, client can issue a request with Enumeration.STOP action.
 * </p>
 * <p>
 * When REFRESH is specified, this service performs a full enumeration. No session is maintained after enumeration.
 * </p>
 */
public class VSphereAdapterResourceEnumerationService extends StatelessService {
    public static final String SELF_LINK = VSphereUriPaths.ENUMERATION_SERVICE;

    /**
     * On every enumeration request, we check if there's an existing stateless service and delete it.
     * This is needed to ensure no two enumeration processes are happening for same endpoint.
     * After deleting the service, we create a new stateless service by issuing POST on the factory of
     * stateless service and passing the last path segment of endpoint document selflink.
     *
     * @param op the enumeration operation request
     */
    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        ComputeEnumerateResourceRequest request = op.getBody(ComputeEnumerateResourceRequest.class);

        validate(request);

        op.setStatusCode(Operation.STATUS_CODE_CREATED);
        op.complete();
        TaskManager mgr = new TaskManager(this, request.taskReference, request.resourceLink());

        if (request.isMockRequest) {
            // just finish the mock request
            mgr.patchTask(TaskState.TaskStage.FINISHED);
            return;
        }

        String eventBasedEnumerationServiceURI = UriUtils.buildUriPath(VSphereIncrementalEnumerationService.FACTORY_LINK, UriUtils
                .getLastPathSegment(request.endpointLink));
        URI uri = buildUri(this.getHost(), eventBasedEnumerationServiceURI);
        VSphereIncrementalEnumerationService.VSphereIncrementalEnumerationRequest enumerationRequest =
                new VSphereIncrementalEnumerationService.VSphereIncrementalEnumerationRequest();
        enumerationRequest.documentSelfLink = UriUtils.getLastPathSegment(eventBasedEnumerationServiceURI);
        enumerationRequest.request = request;
        // if we receive START action, check if there is a stateless for endpoint,
        // if yes, patch it. If no, create new one via post to factory.
        if (EnumerationAction.START.equals(request.enumerationAction)) {
            Operation.createGet(uri).setCompletion((o, e) -> {
                if (null != e || o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                    // service not found, create a new one
                    createNewEnumerationService(enumerationRequest, mgr);
                } else {
                    logInfo("Patching incremental service for incremental enumeration for %s",
                            enumerationRequest.documentSelfLink);
                    Operation patchRequest = Operation
                            .createPatch(uri)
                            .setBody(enumerationRequest)
                            .setCompletion((operation, err) -> {
                                if (err != null) {
                                    logSevere("Unable to send enumeration request to enumeration"
                                                    + " service for endpoint %s",
                                            enumerationRequest.documentSelfLink);
                                    mgr.patchTaskToFailure(err);
                                }
                            });
                    patchRequest.sendWith(this);
                }
            }).sendWith(this);
        } else {
            // Stop any existing enumeration process when REFRESH is received.
            logInfo("Deleting the incremental enumeration service.");
            Operation deleteRequest = Operation.createDelete(uri)
                    .setCompletion((o, e) -> {
                        if (null != e) {
                            logWarning("Delete of enumeration service failed for endpoint "
                                    + enumerationRequest.documentSelfLink + " Message: %s", e.getMessage());
                        }
                        // patch the task to finished if the enumeration action is STOP.
                        // The delete service call will take care of stopping any running enumeration.
                        if (request.enumerationAction == EnumerationAction.STOP) {
                            logInfo("Successfully stopped enumeration for endpoint "
                                    + request.endpointLink);
                            mgr.patchTask(TaskState.TaskStage.FINISHED);
                        } else {
                            logInfo("Creating the enumeration service for endpoint %s", request.endpointLink);
                            createNewEnumerationService(enumerationRequest, mgr);
                        }
                    });
            deleteRequest.sendWith(this);
        }
    }

    private void createNewEnumerationService(
            VSphereIncrementalEnumerationRequest enumerationRequest, TaskManager mgr) {
        Operation createRequest = Operation
                .createPost(buildFactoryUri(this.getHost(),
                        VSphereIncrementalEnumerationService.class))
                .setBody(Utils.toJson(enumerationRequest))
                .setCompletion((op, err) -> {
                    if (err != null) {
                        logSevere("Unable to start enumeration service for endpoint %s",
                                enumerationRequest.documentSelfLink);
                        mgr.patchTaskToFailure(err);
                    }
                });
        createRequest.addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE);
        createRequest.sendWith(this);
    }

    private void validate(ComputeEnumerateResourceRequest request) {
        // assume all request are REFRESH requests
        if (request.enumerationAction == null) {
            request.enumerationAction = EnumerationAction.START;
        }
    }
}
