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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import org.junit.Test;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.UriUtils;

/**
 * Tests for the {@link ResourceUtils} class.
 */
public class ResourceUtilsTest extends BaseModelTest {

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

        boolean changed = handlePatch(current, patch).getStatusCode() == Operation.STATUS_CODE_OK;

        assertFalse(changed);
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

        boolean changed = handlePatch(current, patch).getStatusCode() == Operation.STATUS_CODE_OK;

        assertFalse(changed);
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

        ResourceUtils.handlePatch(patchOperation, current, desc, ResourceState.class, null);

        assertEquals(Operation.STATUS_CODE_NOT_MODIFIED, patchOperation.getStatusCode());
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

    private Operation handlePatch(ResourceState currentState, ResourceState patchState) {
        Operation patchOperation = Operation
                .createPatch(UriUtils.buildUri(this.host, "/resource"))
                .setBody(patchState);

        ServiceDocumentDescription desc = ServiceDocumentDescription.Builder.create()
                .buildDescription(ResourceState.class);

        ResourceUtils.handlePatch(patchOperation, currentState, desc, ResourceState.class, null);

        return patchOperation;
    }
}
