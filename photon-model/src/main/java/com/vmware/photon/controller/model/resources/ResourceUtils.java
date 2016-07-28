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

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.Utils;

public class ResourceUtils {

    /**
     * Update status code and complete the patch operation
     * @param patch the PATCH operation
     * @param hasStateChanged true if the patch has updated the service state, false otherwise
     */
    public static void completePatchOperation(Operation patch, boolean hasStateChanged) {
        if (!hasStateChanged) {
            patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
        }
        patch.complete();
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

    /**
     * Remove elements from specified collections
     *
     * @param currentState currentState of the service
     * @param removalBody request of removing elements
     * @return Whether current state has been changed
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    public static boolean removeCollections(ResourceState currentState, CollectionRemovalRequest removalBody)
            throws NoSuchFieldException, IllegalAccessException {
        boolean isChanged = false;

        Class<? extends ResourceState> clazz = currentState.getClass();

        for (String collectionName : removalBody.collectionsMap.keySet()) {
            Collection<Object> elementsToBeRemoved = removalBody.collectionsMap.get(collectionName);

            if (elementsToBeRemoved != null && !elementsToBeRemoved.isEmpty()) {
                Field field = clazz.getField(collectionName);

                if (field != null && Collection.class.isAssignableFrom(field.getType())) {
                    // get target collection
                    @SuppressWarnings("rawtypes")
                    Collection collObj = (Collection) field.get(currentState);
                    @SuppressWarnings("rawtypes")
                    Iterator iterator = collObj.iterator();
                    // delete elements from collection
                    while (iterator.hasNext()) {
                        Object currentElement = iterator.next();
                        if (elementsToBeRemoved.contains(currentElement)) {
                            iterator.remove();
                            isChanged = true;
                        }
                    }
                }
            }
        }

        return isChanged;
    }

    /**
     * Handle the request to remove elements of collections.
     *
     * @param currentState currentState of the service
     * @param patch the PATCH Operation
     * @return Whether current state has been changed
     */
    public static boolean handleCollectionRemovalRequest(ResourceState currentState, Operation patch) {
        boolean isChanged = false;
        ResourceUtils.CollectionRemovalRequest removalBody =
                patch.getBody(ResourceUtils.CollectionRemovalRequest.class);

        if (removalBody != null && removalBody.kind != null
                && removalBody.kind.equals(ResourceUtils.CollectionRemovalRequest.KIND)) {

            try {
                isChanged = removeCollections(currentState, removalBody);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                patch.fail(e);
            }

            ResourceUtils.completePatchOperation(patch, isChanged);
        }

        return isChanged;
    }

    /**
     * Request used in patch operation for removing elements of collection type attribute.
     * E.g. remove ComputeState's networkLinks which are no longer needed.
     */
    public static class CollectionRemovalRequest {
        public static final String KIND = Utils.buildKind(CollectionRemovalRequest.class);
        /**
         * Key is the field name of the collection, e.g. "networkLinks", "diskLinks".
         * Value is the elements of the collection that need to be removed.
         */
        public Map<String, Collection<Object>> collectionsMap;

        public String kind;
    }
}
