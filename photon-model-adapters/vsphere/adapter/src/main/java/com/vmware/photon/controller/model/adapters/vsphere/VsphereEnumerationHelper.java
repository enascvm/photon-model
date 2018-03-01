/*
 * Copyright (c) 2018-2019 VMware, Inc. All Rights Reserved.
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.node.ObjectNode;

import com.vmware.photon.controller.model.adapters.util.TagsUtil;
import com.vmware.photon.controller.model.adapters.vsphere.vapi.RpcException;
import com.vmware.photon.controller.model.adapters.vsphere.vapi.TaggingClient;
import com.vmware.photon.controller.model.adapters.vsphere.vapi.VapiConnection;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

public class VsphereEnumerationHelper {

    static final long QUERY_TASK_EXPIRY_MICROS = TimeUnit.MINUTES.toMicros(1);

    public static String getSelfLinkFromOperation(Operation o) {
        return o.getBody(ServiceDocument.class).documentSelfLink;
    }

    public static <T> T convertOnlyResultToDocument(ServiceDocumentQueryResult result, Class<T> type) {
        return Utils.fromJson(result.documents.values().iterator().next(), type);
    }

    /**
     * Executes a direct query and invokes the provided handler with the results.
     *
     * @param vSphereIncrementalEnumerationService
     * @param task
     * @param handler
     * @param resultLimit
     */
    static void withTaskResults(VSphereIncrementalEnumerationService vSphereIncrementalEnumerationService, QueryTask task, Consumer<ServiceDocumentQueryResult> handler, int resultLimit) {
        task.querySpec.options = EnumSet.of(
                QueryOption.EXPAND_CONTENT,
                QueryOption.INDEXED_METADATA,
                QueryOption.TOP_RESULTS);
        if (resultLimit > 0) {
            task.querySpec.resultLimit = resultLimit;
        }

        task.documentExpirationTimeMicros = Utils.fromNowMicrosUtc(QUERY_TASK_EXPIRY_MICROS);

        QueryUtils.startInventoryQueryTask(vSphereIncrementalEnumerationService, task)
                .whenComplete((o, e) -> {
                    if (e != null) {
                        vSphereIncrementalEnumerationService.logWarning(() -> String.format("Error processing task %s",
                                task.documentSelfLink));
                        return;
                    }

                    handler.accept(o.results);
                });
    }

    /**
     * Executes a direct query and invokes the provided handler with the results.
     *
     * @param vSphereIncrementalEnumerationService
     * @param task
     * @param handler
     */
    static void withTaskResults(VSphereIncrementalEnumerationService vSphereIncrementalEnumerationService, QueryTask task, Consumer<ServiceDocumentQueryResult> handler) {
        withTaskResults(vSphereIncrementalEnumerationService, task, handler, 1);
    }

    /**
     * Builds a function to retrieve tags given and endpoint.
     *
     * @param client
     * @return
     */
    static Function<String, TagState> newTagRetriever(TaggingClient client) {
        return (tagId) -> {
            try {
                ObjectNode tagModel = client.getTagModel(tagId);
                if (tagModel == null) {
                    return null;
                }

                TagState res = new TagState();
                res.value = tagModel.get("name").asText();
                res.key = client.getCategoryName(tagModel.get("category_id").asText());
                return res;
            } catch (IOException | RpcException e) {
                return null;
            }
        };
    }

    /**
     * Retreives all tags for a MoRef from an endpoint.
     *
     * @param vSphereIncrementalEnumerationService
     * @param endpoint
     * @param ref
     * @param tenantLinks
     * @return empty list if no tags found, never null
     */
    static List<TagState> retrieveAttachedTags(VSphereIncrementalEnumerationService vSphereIncrementalEnumerationService, VapiConnection endpoint,
                                               ManagedObjectReference ref, List<String> tenantLinks) throws IOException, RpcException {
        TaggingClient taggingClient = endpoint.newTaggingClient();
        List<String> tagIds = taggingClient.getAttachedTags(ref);

        List<TagState> res = new ArrayList<>();
        for (String id : tagIds) {
            TagState cached = vSphereIncrementalEnumerationService.getTagCache().get(id, newTagRetriever(taggingClient));
            if (cached != null) {
                TagState tag = TagsUtil.newTagState(cached.key, cached.value, true, tenantLinks);
                res.add(tag);
            }
        }

        return res;
    }

    static Set<String> createTagsAsync(VSphereIncrementalEnumerationService vSphereIncrementalEnumerationService, List<TagState> tags) {
        if (tags == null || tags.isEmpty()) {
            return new HashSet<>();
        }

        Stream<Operation> ops = tags.stream()
                .map(s -> Operation
                        .createPost(UriUtils.buildFactoryUri(vSphereIncrementalEnumerationService.getHost(), TagService.class))
                        .setBody(s));

        OperationJoin.create(ops)
                .sendWith(vSphereIncrementalEnumerationService);

        return tags.stream()
                .map(s -> s.documentSelfLink)
                .collect(Collectors.toSet());
    }

    /**
     * After the tags for the ref are retrieved from the endpoint they are posted to the tag service
     * and the selfLinks are collected ready to be used in a {@link ComputeState#tagLinks}.
     *
     */
    static Set<String> retrieveTagLinksAndCreateTagsAsync(VSphereIncrementalEnumerationService vSphereIncrementalEnumerationService, VapiConnection endpoint,
                                                          ManagedObjectReference ref, List<String> tenantLinks) {
        List<TagState> tags = null;
        try {
            tags = retrieveAttachedTags(vSphereIncrementalEnumerationService, endpoint, ref, tenantLinks);
        } catch (IOException | RpcException ignore) {

        }

        return createTagsAsync(vSphereIncrementalEnumerationService, tags);
    }

    static void populateTags(VSphereIncrementalEnumerationService vSphereIncrementalEnumerationService, EnumerationProgress enumerationProgress, AbstractOverlay obj,
                             ResourceState state) {
        state.tagLinks = retrieveTagLinksAndCreateTagsAsync(vSphereIncrementalEnumerationService, enumerationProgress.getEndpoint(),
                obj.getId(), enumerationProgress.getTenantLinks());
    }

    static void submitWorkToVSpherePool(VSphereIncrementalEnumerationService vSphereIncrementalEnumerationService, Runnable work) {
        // store context at the moment of submission
        OperationContext orig = OperationContext.getOperationContext();
        VSphereIOThreadPool pool = VSphereIOThreadPoolAllocator.getPool(vSphereIncrementalEnumerationService);

        pool.submit(() -> {
            OperationContext old = OperationContext.getOperationContext();

            OperationContext.setFrom(orig);
            try {
                work.run();
            } finally {
                OperationContext.restoreOperationContext(old);
            }
        });
    }

    static void updateLocalTags(VSphereIncrementalEnumerationService vSphereIncrementalEnumerationService, EnumerationProgress enumerationProgress, AbstractOverlay obj,
                                ResourceState patchResponse) {
        List<TagState> tags;
        try {
            tags = retrieveAttachedTags(vSphereIncrementalEnumerationService, enumerationProgress.getEndpoint(),
                    obj.getId(),
                    enumerationProgress.getTenantLinks());
        } catch (IOException | RpcException e) {
            vSphereIncrementalEnumerationService.logWarning("Error updating local tags for %s", patchResponse.documentSelfLink);
            return;
        }

        Map<String, String> remoteTagMap = new HashMap<>();
        for (TagState ts : tags) {
            remoteTagMap.put(ts.key, ts.value);
        }

        TagsUtil.updateLocalTagStates(vSphereIncrementalEnumerationService, patchResponse, remoteTagMap, null);
    }

    static String computeGroupStableLink(ManagedObjectReference ref, String prefix, String endpointLink) {
        return UriUtils.buildUriPath(
                ResourceGroupService.FACTORY_LINK,
                prefix + "-" +
                        VimUtils.buildStableManagedObjectId(ref, endpointLink));
    }
}
