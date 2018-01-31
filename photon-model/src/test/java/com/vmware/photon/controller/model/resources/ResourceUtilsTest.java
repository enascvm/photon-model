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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Test;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourceState.TagInfo;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Tests for the {@link ResourceUtils} class.
 */
public class ResourceUtilsTest extends BaseModelTest {

    public static class ResourceStateWithLinks extends ResourceState {
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String optionalLink;

        public String noAutoMergeLink;
    }

    @Test
    public void testMergeWithNewValues() {
        ResourceState current = new ResourceState();
        current.tenantLinks = new ArrayList<>(Arrays.asList("tenant1"));
        current.groupLinks = new HashSet<>(Arrays.asList("groupA"));
        current.tagLinks = new HashSet<>(Arrays.asList("tag1", "tag2"));

        ResourceState patch = new ResourceState();
        patch.tenantLinks = new ArrayList<>(Arrays.asList("tenant2"));
        patch.groupLinks = new HashSet<>(Arrays.asList("groupB"));
        patch.tagLinks = new HashSet<>();

        boolean changed = handlePatch(current, patch).getStatusCode() == Operation.STATUS_CODE_OK;

        assertTrue(changed);
        assertEquals(Arrays.asList("tenant1", "tenant2"), current.tenantLinks);
        assertEquals(new HashSet<>(Arrays.asList("groupA", "groupB")), current.groupLinks);
        assertEquals(new HashSet<>(Arrays.asList("tag1", "tag2")), current.tagLinks);
    }

    @Test
    public void testMergeWithSameValues() {
        ResourceState current = new ResourceState();
        current.tenantLinks = new ArrayList<>(Arrays.asList("tenant1", "tenant2"));
        current.groupLinks = new HashSet<>(Arrays.asList("groupA", "groupB"));
        current.tagLinks = new HashSet<>(Arrays.asList("tag1", "tag2"));
        ResourceState patch = new ResourceState();
        patch.tenantLinks = new ArrayList<>(Arrays.asList("tenant1", "tenant2"));
        patch.groupLinks = new HashSet<>(Arrays.asList("groupA", "groupB"));
        patch.tagLinks = new HashSet<>(Arrays.asList("tag1", "tag2"));

        Operation returnOp = handlePatch(current, patch);
        assertTrue(returnOp.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_STATE_NOT_MODIFIED));
        assertEquals(current.tenantLinks, patch.tenantLinks);
        assertEquals(current.groupLinks, patch.groupLinks);
        assertEquals(current.tagLinks, patch.tagLinks);
    }

    @Test
    public void testMergeWithNullValues() {
        ResourceState current = new ResourceState();
        current.tenantLinks = new ArrayList<>(Arrays.asList("tenant1", "tenant2"));
        current.groupLinks = new HashSet<>(Arrays.asList("groupA", "groupB"));
        current.tagLinks = new HashSet<>(Arrays.asList("tag1", "tag2"));
        ResourceState patch = new ResourceState();

        Operation returnOp = handlePatch(current, patch);
        assertTrue(returnOp.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_STATE_NOT_MODIFIED));

        assertEquals(Arrays.asList("tenant1", "tenant2"), current.tenantLinks);
        assertEquals(new HashSet<>(Arrays.asList("groupA", "groupB")), current.groupLinks);
        assertEquals(new HashSet<>(Arrays.asList("tag1", "tag2")), current.tagLinks);
    }

    @Test
    public void testOperationPatchNoChanges() {
        ResourceState current = new ResourceState();
        current.tenantLinks = new ArrayList<>(Arrays.asList("tenant1", "tenant2"));
        current.groupLinks = new HashSet<>(Arrays.asList("groupA", "groupB"));
        current.tagLinks = new HashSet<>(Arrays.asList("tag1", "tag2"));

        ResourceState patch = new ResourceState();
        Operation patchOperation = Operation
                .createPatch(UriUtils.buildUri(this.host, "/resource"))
                .setBody(patch);

        ServiceDocumentDescription desc = ServiceDocumentDescription.Builder.create()
                .buildDescription(ResourceState.class);

        ResourceUtils.handlePatch(null, patchOperation, current, desc, ResourceState.class, null);
        assertTrue(patchOperation.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_STATE_NOT_MODIFIED));
    }

    @Test
    public void testOperationPatchWithChanges() {
        ResourceState current = new ResourceState();
        current.tenantLinks = new ArrayList<>(Arrays.asList("tenant1", "tenant2"));
        current.groupLinks = new HashSet<>(Arrays.asList("groupA", "groupB"));
        current.tagLinks = new HashSet<>(Arrays.asList("tag1", "tag2"));

        ResourceState patch = new ResourceState();
        patch.tagLinks = new HashSet<>(Arrays.asList("tag3"));

        Operation patchOperation = handlePatch(current, patch);

        assertTrue(patchOperation.hasBody());
        assertEquals(Operation.STATUS_CODE_OK, patchOperation.getStatusCode());
        ResourceState returnedState = patchOperation.getBody(ResourceState.class);
        assertEquals(current.tenantLinks, returnedState.tenantLinks);
        assertEquals(current.groupLinks, returnedState.groupLinks);
        assertEquals(current.tagLinks, returnedState.tagLinks);
    }

    @Test
    public void testNullifyOptionalLink() {
        ResourceStateWithLinks current = new ResourceStateWithLinks();
        current.optionalLink = "/some/link";

        ResourceStateWithLinks patch = new ResourceStateWithLinks();
        Operation patchOperation = handlePatch(current, patch, ResourceStateWithLinks.class);
        assertTrue(patchOperation.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_STATE_NOT_MODIFIED));

        patch.optionalLink = "/some/other";
        patch.noAutoMergeLink = "/link";
        patchOperation = handlePatch(current, patch, ResourceStateWithLinks.class);
        assertEquals(Operation.STATUS_CODE_OK, patchOperation.getStatusCode());
        assertEquals(patch.optionalLink, current.optionalLink);
        assertNull(current.noAutoMergeLink);

        patch.optionalLink = ResourceUtils.NULL_LINK_VALUE;
        patchOperation = handlePatch(current, patch, ResourceStateWithLinks.class);
        assertEquals(Operation.STATUS_CODE_OK, patchOperation.getStatusCode());
        assertEquals(null, current.optionalLink);

        patchOperation = handlePatch(current, patch, ResourceStateWithLinks.class);
        assertEquals(Operation.STATUS_CODE_OK, patchOperation.getStatusCode());
        assertEquals(null, current.optionalLink);
    }

    @Test
    public void testExpandTags() throws Throwable {
        TagState tag1 = new TagState();
        tag1.key = "A";
        tag1.value = "1";
        tag1 = postServiceSynchronously(TagService.FACTORY_LINK, tag1, TagState.class);
        TagState tag2 = new TagState();
        tag2.key = "A";
        tag2.value = "2";
        tag2 = postServiceSynchronously(TagService.FACTORY_LINK, tag2, TagState.class);
        TagState tag3 = new TagState();
        tag3.key = "A";
        tag3.value = "3";
        tag3 = postServiceSynchronously(TagService.FACTORY_LINK, tag3, TagState.class);

        // validate expansion on POST
        ComputeState compute = new ComputeState();
        compute.descriptionLink = "cdLink";
        compute.tagLinks = new HashSet<>();
        compute.tagLinks.add(tag1.documentSelfLink);
        compute.tagLinks.add(tag2.documentSelfLink);
        compute = postServiceSynchronously(ComputeService.FACTORY_LINK, compute, ComputeState
                .class);

        Collection<String> tags = compute.expandedTags.stream().map(t -> t.tag).collect(Collectors.toList());
        assertEquals(2, tags.size());
        assertTrue(tags.containsAll(Arrays.asList("A\n1", "A\n2")));

        // validate tags cannot be modified directly
        compute.expandedTags.remove(1);
        assertEquals(1, compute.expandedTags.size());
        putServiceSynchronously(compute.documentSelfLink, compute);
        compute = getServiceSynchronously(compute.documentSelfLink, ComputeState.class);
        tags = compute.expandedTags.stream().map(t -> t.tag).collect(Collectors.toList());
        assertEquals(2, tags.size());
        assertTrue(tags.containsAll(Arrays.asList("A\n1", "A\n2")));

        // validate expansion on PUT
        compute.tagLinks.remove(tag2.documentSelfLink);
        compute.tagLinks.add(tag3.documentSelfLink);
        putServiceSynchronously(compute.documentSelfLink, compute);
        compute = getServiceSynchronously(compute.documentSelfLink, ComputeState.class);
        tags = compute.expandedTags.stream().map(t -> t.tag).collect(Collectors.toList());
        assertEquals(2, tags.size());
        assertTrue(tags.containsAll(Arrays.asList("A\n1", "A\n3")));

        // validate expansion on PATCH
        ComputeState patchState = new ComputeState();
        patchState.tagLinks = new HashSet<>();
        patchState.tagLinks.add(tag2.documentSelfLink);
        compute = patchServiceSynchronously(compute.documentSelfLink, patchState,
                ComputeState.class);
        tags = compute.expandedTags.stream().map(t -> t.tag).collect(Collectors.toList());
        assertEquals(3, tags.size());
        assertTrue(tags.containsAll(Arrays.asList("A\n1", "A\n2", "A\n3")));

        // validate expansion through custom PATCH body
        Map<String, Collection<Object>> itemsToRemove = new HashMap<>();
        itemsToRemove.put(ResourceState.FIELD_NAME_TAG_LINKS, Arrays.asList
                (tag2.documentSelfLink, tag3.documentSelfLink));
        patchServiceSynchronously(compute.documentSelfLink,
                ServiceStateCollectionUpdateRequest.create(null, itemsToRemove));
        compute = getServiceSynchronously(compute.documentSelfLink, ComputeState.class);
        tags = compute.expandedTags.stream().map(t -> t.tag).collect(Collectors.toList());
        assertEquals(1, tags.size());
        assertTrue(tags.containsAll(Arrays.asList("A\n1")));

        // validate query (case-insensitive) (Note: only 1 tag can be found with Xenon 1.6.1)
        Query tagQuery = Query.Builder.create()
                .addFieldClause(TagInfo.COMPOSITE_FIELD_NAME_TAG, "a*", MatchType.WILDCARD)
                .build();
        QueryTask tagQueryTask = QueryTask.Builder.createDirectTask()
                .setQuery(tagQuery)
                .addOption(QueryOption.EXPAND_CONTENT)
                .build();
        tagQueryTask = postServiceSynchronously(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS,
                tagQueryTask, QueryTask.class);
        assertEquals(1, tagQueryTask.results.documentLinks.size());
        assertEquals(1, tagQueryTask.results.documents.size());
        assertEquals(compute.documentSelfLink, tagQueryTask.results.documentLinks.get(0));
        ComputeState foundCompute = Utils.fromJson(tagQueryTask.results.documents.values().iterator()
                .next(), ComputeState.class);
        assertEquals(1, foundCompute.expandedTags.size());
        assertEquals("A\n1", foundCompute.expandedTags.get(0).tag);
    }

    private Operation handlePatch(ResourceState currentState, ResourceState patchState) {
        return handlePatch(currentState, patchState, ResourceState.class);
    }

    private <T extends ResourceState> Operation handlePatch(T currentState, T patchState, Class<T> type) {
        Operation patchOperation = Operation
                .createPatch(UriUtils.buildUri(this.host, "/resource"))
                .setBody(patchState);

        ServiceDocumentDescription desc = ServiceDocumentDescription.Builder.create()
                .buildDescription(type);

        ResourceUtils.handlePatch(null, patchOperation, currentState, desc, type, null);

        return patchOperation;
    }
}
