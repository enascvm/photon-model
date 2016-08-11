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

import java.util.function.Function;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.Utils;

public class ResourceUtils {

    /**
     * This method handles merging of state for patch requests. It first checks to see if the
     * patch body is for updating collections. If not it invokes the mergeWithState() method.
     * Finally, users can specify a custom callback method to perform service specific merge
     * operations
     * @param op Input PATCH operation
     * @param currentState The current state of the service
     * @param description The service description
     * @param stateClass Service state class
     * @param customPatchHandler custom callback handler
     */
    public static void handlePatch(Operation op, ResourceState currentState, ServiceDocumentDescription description,
            Class<? extends ResourceState> stateClass, Function<Operation, Boolean> customPatchHandler) {
        boolean hasStateChanged = false;
        try {
            if (Utils.mergeWithState(currentState, op)) {
                hasStateChanged = true;
            } else {
                ResourceState patchBody = op.getBody(stateClass);
                hasStateChanged =
                        ResourceUtils.mergeWithState(description, currentState, patchBody) |
                        (customPatchHandler != null ? customPatchHandler.apply(op) : false);
            }
            if (!hasStateChanged) {
                op.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
            }
            op.complete();
            return;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            op.fail(e);
            return;
        }
    }

    /**
     * Update the state of the service based on the input patch
     *
     * @param description service document description
     * @param source currentState of the service
     * @param patch patch state
     * @return
     */
    public static boolean mergeWithState(ServiceDocumentDescription description,
            ResourceState source, ResourceState patch) {
        boolean isChanged = Utils.mergeWithState(description, source, patch);

        if (patch.tenantLinks != null
                && !patch.tenantLinks.isEmpty()) {
            if (source.tenantLinks == null
                    || source.tenantLinks.isEmpty()) {
                source.tenantLinks = patch.tenantLinks;
                isChanged = true;
            } else {
                for (String e : patch.tenantLinks) {
                    if (!source.tenantLinks.contains(e)) {
                        source.tenantLinks.add(e);
                        isChanged = true;
                    }
                }
            }
        }

        if (patch.groupLinks != null
                && !patch.groupLinks.isEmpty()) {
            if (source.groupLinks == null
                    || source.groupLinks.isEmpty()) {
                source.groupLinks = patch.groupLinks;
                isChanged = true;
            } else {
                if (source.groupLinks.addAll(patch.groupLinks)) {
                    isChanged = true;
                }
            }
        }
        return isChanged;
    }
}
