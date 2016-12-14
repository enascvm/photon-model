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

import java.util.EnumSet;
import java.util.function.Function;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.Utils;

public class ResourceUtils {

    /**
     * This method handles merging of state for patch requests. It first checks to see if the
     * patch body is for updating collections. If not it invokes the mergeWithState() method.
     * Finally, users can specify a custom callback method to perform service specific merge
     * operations.
     *
     * <p>If no changes are made to the current state, a response code {@code NOT_MODIFIED} is
     * returned with no body. If changes are made, the response body contains the full updated
     * state.
     *
     * @param op Input PATCH operation
     * @param currentState The current state of the service
     * @param description The service description
     * @param stateClass Service state class
     * @param customPatchHandler custom callback handler
     */
    public static <T extends ResourceState> void handlePatch(Operation op, T currentState,
            ServiceDocumentDescription description, Class<T> stateClass,
            Function<Operation, Boolean> customPatchHandler) {
        try {
            boolean hasStateChanged;

            // apply standard patch merging
            EnumSet<Utils.MergeResult> mergeResult =
                    Utils.mergeWithStateAdvanced(description, currentState, stateClass, op);
            hasStateChanged = mergeResult.contains(Utils.MergeResult.STATE_CHANGED);

            if (!mergeResult.contains(Utils.MergeResult.SPECIAL_MERGE)) {
                // apply ResourceState-specific merging
                T patchBody = op.getBody(stateClass);
                hasStateChanged |= ResourceUtils.mergeResourceStateWithPatch(currentState,
                        patchBody);

            }

            // apply custom patch handler, if any
            if (customPatchHandler != null) {
                hasStateChanged |= customPatchHandler.apply(op);
            }

            if (!hasStateChanged) {
                op.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
            } else {
                op.setBody(currentState);
            }
            op.complete();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            op.fail(e);
        }
    }

    /**
     * Updates the state of the service based on the input patch.
     *
     * @param description service document description
     * @param source currentState of the service
     * @param patch patch state
     * @return whether the state has changed or not
     */
    private static boolean mergeResourceStateWithPatch(ResourceState source,
            ResourceState patch) {
        boolean isChanged = false;

        // tenantLinks requires special handling so that although it is a list, duplicate items
        // are not allowed (i.e. it should behave as a set)
        if (patch.tenantLinks != null && !patch.tenantLinks.isEmpty()) {
            if (source.tenantLinks == null || source.tenantLinks.isEmpty()) {
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

        return isChanged;
    }
}
