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

package com.vmware.photon.controller.model.adapters.util;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.query.QueryStrategy;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTop;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.TagFactoryService;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 * Tags manipulation utility methods. Use to create or update tag states so that resource states'
 * tag links list corresponds to the remote resource's tags.
 */
public class TagsUtil {

    /**
     * Create or update the tag states of the provided ResourceState. In case there is an existing
     * state (currentState), update the resource's links, returning the updated links set.
     * In case of update, the resource already has the updated links caller is not responsible for this.
     *
     * In case of create, create the tag states for the state currently in process of creation (localState).
     * Returned are the new tag links, caller is responsible to update the processed resource state.
     */
    public static DeferredResult<Set<String>> createOrUpdateTagStates(
            StatelessService service,
            ResourceState localState,
            ResourceState currentState,
            Map<String, String> remoteTagsMap) {

        final DeferredResult<Set<String>> tagsDR;

        if (currentState == null
                || currentState.tagLinks == null
                || currentState.tagLinks.isEmpty()) {
            tagsDR = createLocalTagStates(service, localState, remoteTagsMap);
        } else {
            tagsDR = updateLocalTagStates(service, currentState, remoteTagsMap);
        }

        return tagsDR;
    }

    /**
     * For a newly created local ResourceState, create tag states and return the new links.
     * Caller of the method is responsible to add the links to the processed resource state.
     */
    public static DeferredResult<Set<String>> createLocalTagStates(
            StatelessService service,
            ResourceState localState,
            Map<String, String> remoteTagsMap) {

        if (remoteTagsMap == null || remoteTagsMap.isEmpty()) {
            return DeferredResult.completed(Collections.emptySet());
        }

        String msg = "Create local TagStates (for %s.name=%s) to match %d remote tags: %s";

        service.logFine(
                () -> String.format(msg, localState.getClass().getSimpleName(), localState.name,
                        remoteTagsMap.size(), "STARTING"));

        List<DeferredResult<TagState>> localTagStatesDRs = remoteTagsMap.entrySet().stream()
                .map(tagEntry -> newExternalTagState(tagEntry.getKey(), tagEntry.getValue(),
                        localState.tenantLinks))
                .map(tagState -> Operation
                        .createPost(service, TagService.FACTORY_LINK)
                        .setBody(tagState))
                .map(tagOp -> service.sendWithDeferredResult(tagOp, TagState.class))
                .collect(Collectors.toList());

        return DeferredResult.allOf(localTagStatesDRs).thenApply(tagStatesList -> {

            service.logFine(
                    () -> String.format(msg, localState.getClass().getSimpleName(), localState.name,
                            remoteTagsMap.size(), "COMPLETED"));

            return tagStatesList.stream().map(tagState -> tagState.documentSelfLink).collect(Collectors.toSet());
        });
    }

    /**
     * Compare local with remote tags and identify which local tag links to remove and which to add
     * to the resource state's list, in order the resource state to contain exactly the tag states,
     * corresponding to the remote tags.
     */
    public static DeferredResult<Set<String>> updateLocalTagStates(
            StatelessService service,
            ResourceState localState,
            Map<String, String> remoteTagsMap) {

        return updateLocalTagStates(service, localState, localState.tagLinks, remoteTagsMap);
    }

    /**
     * Compare local with remote tags and identify which local tag links to remove and which to add
     * to the resource state's list, in order the resource state to contain exactly the tag states,
     * corresponding to the remote tags.
     */
    public static DeferredResult<Set<String>> updateLocalTagStates(
            StatelessService service,
            ResourceState localState, Set<String> currentTagLinks,
            Map<String, String> remoteTagsMap) {

        Map<String, TagState> remoteTagStates;

        if (remoteTagsMap == null) {
            remoteTagStates = new HashMap<>();
        } else {
            remoteTagStates = remoteTagsMap.entrySet().stream()
                    .map(tagEntry -> newExternalTagState(
                            tagEntry.getKey(),
                            tagEntry.getValue(),
                            localState.tenantLinks))
                    .collect(Collectors.toMap(
                            tagState -> tagState.documentSelfLink,
                            tagState -> tagState));

            if (remoteTagStates != null && remoteTagStates.containsKey(null)) {
                service.logSevere("Null selfLink found in remoteTagStates for TagState: %s",
                        Utils.toJsonHtml(remoteTagStates.get(null)));
                service.logSevere("Removing null key from remoteTagStates.");
                remoteTagStates.remove(null);
            }
        }
        //the method will return calculated set of tags, which will be updated via the post operation
        Set<String> resultTagLinks = new HashSet<String>();

        final DeferredResult<List<TagState>> createAllLocalTagStatesDR;
        final Collection<String> tagLinksToAdd;
        {
            // the remote tags which do not exist locally will be added to the computeState tagLinks
            // list
            tagLinksToAdd = new HashSet<>(remoteTagStates.keySet());

            if (localState.tagLinks != null) {
                // currently existing links should not be post for adding again
                tagLinksToAdd.removeAll(localState.tagLinks);
                resultTagLinks.addAll(localState.tagLinks);
            }

            // the result list will contain both the currently existing links, and the newly added ones
            resultTagLinks.addAll(tagLinksToAdd);

            // not existing locally tags should be created
            List<DeferredResult<TagState>> localTagStatesDRs = tagLinksToAdd.stream()
                    .map(tagLinkObj -> remoteTagStates.get(tagLinkObj))
                    .map(tagState -> Operation
                            .createPost(service, TagService.FACTORY_LINK)
                            .setBody(tagState))
                    .map(tagOperation -> service.sendWithDeferredResult(
                            tagOperation,
                            TagState.class))
                    .collect(Collectors.toList());

            createAllLocalTagStatesDR = DeferredResult.allOf(localTagStatesDRs);
        }

        final DeferredResult<Collection<String>> removeAllExternalTagLinksDR;
        {
            // all local tag links which do not have remote correspondents and are external will be
            // removed from the currentState's tagLinks list
            Set<String> tagLinksToRemove = new HashSet<>();
            if (localState.tagLinks == null) {
                removeAllExternalTagLinksDR = DeferredResult.completed(tagLinksToRemove);
            } else {
                tagLinksToRemove.addAll(localState.tagLinks);
                tagLinksToRemove.removeAll(remoteTagStates.keySet());

                // the result should not contain the local tags which are removed remotely
                resultTagLinks.removeAll(tagLinksToRemove);

                if (tagLinksToRemove.isEmpty()) {

                    removeAllExternalTagLinksDR = DeferredResult.completed(tagLinksToRemove);

                } else {

                    // identify which of the local tag states to remove are external
                    Query.Builder qBuilder = Query.Builder.create()
                            // Add documents' class
                            .addKindFieldClause(TagState.class)
                            // Add local tag links to remove
                            .addInClause(ServiceDocument.FIELD_NAME_SELF_LINK, tagLinksToRemove)
                            .addFieldClause(TagState.FIELD_NAME_EXTERNAL, Boolean.TRUE.toString());

                    QueryStrategy<TagState> queryLocalStates = new QueryTop<>(
                            service.getHost(),
                            qBuilder.build(),
                            TagState.class,
                            localState.tenantLinks)
                            .setMaxResultsLimit(tagLinksToRemove.size());

                    removeAllExternalTagLinksDR = queryLocalStates.collectLinks(
                            Collectors.toCollection(HashSet::new));
                }
            }
        }

        return createAllLocalTagStatesDR
                .thenCompose(ignore -> removeAllExternalTagLinksDR)
                .thenCompose(removeAllExternalTagLinks -> updateResourceTagLinks(
                        service,
                        removeAllExternalTagLinks,
                        tagLinksToAdd,
                        localState.documentSelfLink))
                .thenCompose(ignore -> {
                    service.logFine(
                            () -> String.format("Update local TagStates (for %s.name=%s) to match %d remote tags: %s;",
                                    localState.getClass().getSimpleName(), localState.name, remoteTagsMap.size(), "COMPLETED"));
                    return DeferredResult.completed(resultTagLinks);
                });
    }

    /**
     * Add the identified tags to the local ResourceState. This method creates tag states in order
     * to generate their unique document self link and adds a list of links to the provided state.
     */
    public static void setTagLinksToResourceState(
            ResourceState resourceState,
            Map<String, String> tags) {

        if (tags == null || tags.isEmpty()) {
            return;
        }

        // we have already made sure that the tags exist and we can build their links ourselves
        resourceState.tagLinks = tags.entrySet().stream()
                .map(t -> newExternalTagState(t.getKey(), t.getValue(), resourceState.tenantLinks))
                .map(TagFactoryService::generateSelfLink)
                .collect(Collectors.toSet());
    }

    /**
     * Generate a new external TagState from provided key and value. TagStates, marked as "external"
     * identify tags which exist on the remote cloud. They will be removed from the local resource's
     * state, in case the corresponding tags is removed from the remote resource. Tags, identified
     * as "local" are not maintained in sync with the tags on the cloud. They are used by the local
     * user to mark local resource states.
     */
    public static TagState newExternalTagState(String key, String value, List<String> tenantLinks) {

        final TagState tagState = new TagState();

        tagState.key = key == null ? "" : key;
        tagState.value = value == null ? "" : value;
        tagState.external = true;

        tagState.tenantLinks = tenantLinks;

        tagState.documentSelfLink = TagFactoryService.generateSelfLink(tagState);

        return tagState;
    }

    /**
     * Private method used to generate structure containing the tag states to add and tag states to
     * remove to the specified resource state (specified by its document self link).
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static DeferredResult<Void> updateResourceTagLinks(
            StatelessService service,
            Collection<String> tagLinksToDelete,
            Collection<String> tagLinksToAdd,
            String currentStateSelfLink) {

        if (tagLinksToAdd != null && tagLinksToAdd.contains(null)) {
            service.logSevere("Null tag link found for ResourceState: %s", currentStateSelfLink);
            service.logSevere("Removing null tag link from tagLinksToAdd.");
            tagLinksToAdd.remove(null);
        }

        // nothing to add/update
        if (tagLinksToDelete.isEmpty() && tagLinksToAdd.isEmpty()) {
            return DeferredResult.completed((Void) null);
        }

        // create patch operation to update tag links of the current resource state
        Map<String, Collection<Object>> collectionsToRemoveMap = Collections.singletonMap(
                ComputeState.FIELD_NAME_TAG_LINKS, (Collection) tagLinksToDelete);

        Map<String, Collection<Object>> collectionsToAddMap = Collections.singletonMap(
                ComputeState.FIELD_NAME_TAG_LINKS, (Collection) tagLinksToAdd);

        ServiceStateCollectionUpdateRequest updateTagLinksRequest = ServiceStateCollectionUpdateRequest
                .create(collectionsToAddMap, collectionsToRemoveMap);

        Operation updatePatch = Operation.createPatch(service, currentStateSelfLink)
                .setBody(updateTagLinksRequest)
                .setReferer(service.getUri());

        return service.sendWithDeferredResult(updatePatch).thenApply(ignore -> (Void) null);
    }

}
