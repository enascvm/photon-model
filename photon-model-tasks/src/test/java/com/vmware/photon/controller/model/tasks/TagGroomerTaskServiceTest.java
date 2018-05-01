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

package com.vmware.photon.controller.model.tasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.model.resources.TagService.TagState.TagOrigin.DISCOVERED;
import static com.vmware.photon.controller.model.resources.TagService.TagState.TagOrigin.SYSTEM;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.resources.TagService.TagState.TagOrigin;
import com.vmware.photon.controller.model.tasks.TagGroomerTaskService.TagDeletionRequest;

import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Tests to validate tag groomer task.
 */
public class TagGroomerTaskServiceTest extends BasicTestCase {
    public static final int TAG_COUNT_15 = 15;
    public static final int TAG_COUNT_200 = 200;

    @Before
    public void setUp() throws Throwable {
        try {
            PhotonModelServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);

            this.host.setTimeoutSeconds(60);

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
        } catch (Throwable e) {
            this.host.log("Error starting up services for the test %s", e.getMessage());
            throw new Exception(e);
        }
    }

    @After
    public void tearDown() throws Throwable {
        if (this.host == null) {
            return;
        }
        this.host.tearDown();
    }

    /**
     * 1. Create multiple tagStates both internal and external
     * 2. create different resources some with above tagLinks and some without
     * 3. validate groomer task only marks the external tagStates as deleted
     */
    @Test
    public void testTagStateGroomerOnePageOfResults() throws Throwable {
        List<String> usedTags = createTagStates("usedExt-", TAG_COUNT_15, true,
                EnumSet.of(DISCOVERED), false, false);
        usedTags.addAll(createTagStates("usedInt-", TAG_COUNT_15, false,
                EnumSet.of(SYSTEM), false, false));

        List<String> unusedTagsExt = createTagStates("unusedExt-",10, true,
                EnumSet.of(DISCOVERED), false, false);
        List<String> unusedTagsInt = createTagStates("unusedInt-",7, false,
                EnumSet.of(SYSTEM), false, false);
        int totalTagsCreated = usedTags.size() + unusedTagsExt.size() + unusedTagsInt.size();

        createComputesWithTags(30, usedTags);
        createDisksWithTags(30, usedTags);
        executeTagsGroomerTask();

        QueryTask tagsQT = createTagsQueryTask(false);
        List<TagState> tagsAfterGrooming = getTags(tagsQT);
        // the only tags returned should be the ones not marked as deleted
        assertEquals(totalTagsCreated - unusedTagsExt.size(), tagsAfterGrooming.size());
        // assert used tags are marked as deleted
        assertUsedTags(tagsAfterGrooming);

        tagsQT = createTagsQueryTask(true);
        tagsAfterGrooming = getTags(tagsQT);
        // the only tags returned should be the 10 external tags that are marked as deleted
        assertEquals(unusedTagsExt.size(), tagsAfterGrooming.size());

        // assert unused tags are marked as deleted
        assertUnusedTags(tagsAfterGrooming);
    }

    @Test
    public void testTagStateGroomerMultiplePagesOfResults() throws Throwable {
        List<String> usedTags = createTagStates("usedExt-", TAG_COUNT_200, true,
                EnumSet.of(DISCOVERED), false, false);
        usedTags.addAll(createTagStates("usedInt-", TAG_COUNT_200, false,
                EnumSet.of(SYSTEM), false, false));

        List<String> unusedTagsExt = createTagStates("unusedExt-",10, true,
                EnumSet.of(DISCOVERED), false, false);
        List<String> unusedTagsInt = createTagStates("unusedInt-",8, false,
                EnumSet.of(SYSTEM), false, false);
        int totalTagsCreated = usedTags.size() + unusedTagsExt.size() + unusedTagsInt.size();

        createComputesWithTags(400, usedTags);
        createDisksWithTags(400, usedTags);
        executeTagsGroomerTask();

        QueryTask tagsQT = createTagsQueryTask(false);
        List<TagState> tagsAfterGrooming = getTags(tagsQT);
        // the only tags returned should the ones not marked as deleted
        assertEquals(totalTagsCreated - unusedTagsExt.size(), tagsAfterGrooming.size());
        // assert used tags are marked as deleted
        assertUsedTags(tagsAfterGrooming);

        tagsQT = createTagsQueryTask(true);
        tagsAfterGrooming = getTags(tagsQT);
        // the only tags returned should be the 10 external tags that are marked as deleted
        assertEquals(unusedTagsExt.size(), tagsAfterGrooming.size());

        // assert unused tags are marked as deleted
        assertUnusedTags(tagsAfterGrooming);
    }

    @Test
    public void testTagStateGroomerOnePageOfResultsAndExpiredTags() throws Throwable {
        List<String> usedTags = createTagStates("usedExt-", TAG_COUNT_15, true,
                EnumSet.of(DISCOVERED), false, false);
        usedTags.addAll(createTagStates("usedInt-", TAG_COUNT_15, false,
                EnumSet.of(SYSTEM), false, false));

        List<String> unusedTagsExt = createTagStates("unusedExt-",10, true,
                EnumSet.of(DISCOVERED), false, false);
        List<String> unusedTagsInt = createTagStates("unusedInt-",8, false,
                EnumSet.of(SYSTEM), false, false);
        List<String> expiredExtTags = createTagStates("expiredExt-", TAG_COUNT_15, true,
                EnumSet.of(DISCOVERED), true, true);
        int totalTagsCreated = usedTags.size() + unusedTagsExt.size() + unusedTagsInt.size()
                + expiredExtTags.size();

        createComputesWithTags(30, usedTags);
        createDisksWithTags(30, usedTags);

        QueryTask tagsQT = createTagsQueryTask(true);
        List<TagState> expiredTagsBeforeGrooming = getTags(tagsQT);
        // the only tags returned should be the 200 external tags that are marked as deleted
        assertEquals(expiredExtTags.size(), expiredTagsBeforeGrooming.size());

        executeTagsGroomerTask();

        tagsQT = createTagsQueryTask(false);
        List<TagState> tagsAfterGrooming = getTags(tagsQT);
        // the only tags returned should the ones not marked as deleted
        assertEquals(totalTagsCreated - unusedTagsExt.size() - expiredExtTags.size(),
                tagsAfterGrooming.size());
        // assert used tags are marked as deleted
        assertUsedTags(tagsAfterGrooming);

        tagsQT = createTagsQueryTask(true);
        tagsAfterGrooming = getTags(tagsQT);
        // tags returned should be: 10 unused soft-deleted external ones + 15 already expired ones
        assertEquals(unusedTagsExt.size() + expiredExtTags.size(), tagsAfterGrooming.size());

        // assert unused tags are marked as deleted
        assertUnusedTags(tagsAfterGrooming);

        // wait and rerun groomer to validate hard delete
        TimeUnit.SECONDS.sleep(5);
        executeTagsGroomerTask();

        tagsQT = createTagsQueryTask(false);
        tagsAfterGrooming = getTags(tagsQT);
        // the only tags returned should the ones not marked as deleted
        assertEquals(totalTagsCreated - unusedTagsExt.size() - expiredExtTags.size(),
                tagsAfterGrooming.size());
        // assert used tags are marked as deleted
        assertUsedTags(tagsAfterGrooming);

        tagsQT = createTagsQueryTask(true);
        tagsAfterGrooming = getTags(tagsQT);
        // tags returned should be: 10 unused soft-deleted external ones
        assertEquals(unusedTagsExt.size(), tagsAfterGrooming.size());

        // assert unused tags are marked as deleted
        assertUnusedTags(tagsAfterGrooming);
    }

    /**
     * Create n tagLinks
     */
    private List<String> createTagStates(String prefix, int count, boolean external,
            EnumSet<TagOrigin> origin, boolean deleted, boolean expired) throws Throwable {
        List<String> tags = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            TagState tag = buildTagState(prefix, i, i, external, origin, deleted, expired);
            Operation op = Operation
                    .createPost(UriUtils.buildUri(this.host, TagService.FACTORY_LINK))
                    .setBody(tag);

            Operation response = this.host.waitForResponse(op);

            if (response.getStatusCode() == Operation.STATUS_CODE_OK) {
                tags.add(response.getBody(TagState.class).documentSelfLink);
            }
        }

        return tags;
    }

    /**
     * Create n computes associated with specific tags
     */
    private void createComputesWithTags(int count, List<String> tagLinks) {
        ComputeState computeState = new ComputeState();
        computeState.descriptionLink = "description-link";
        computeState.id = UUID.randomUUID().toString();
        computeState.name = computeState.id;
        computeState.tagLinks = new HashSet<>();

        for (int i = 0; i < count; i++) {
            computeState.tagLinks.add(tagLinks.get(i));
            Operation op = Operation
                    .createPost(UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK))
                    .setBody(computeState);
            this.host.waitForResponse(op);
            // clear tagLinks and assign new one on the next round
            computeState.tagLinks.clear();
        }
    }

    /**
     * Create n computes associated with specific tags
     */
    private void createDisksWithTags(int count, List<String> tagLinks) {
        DiskState diskState = new DiskState();
        diskState.descriptionLink = "description-link";
        diskState.id = UUID.randomUUID().toString();
        diskState.name = diskState.id;
        diskState.tagLinks = new HashSet<>();

        for (int i = 0; i < count; i++) {
            diskState.tagLinks.add(tagLinks.get(i));
            Operation op = Operation
                    .createPost(UriUtils.buildUri(this.host, DiskService.FACTORY_LINK))
                    .setBody(diskState);
            this.host.waitForResponse(op);
            // clear tagLinks and assign new one on the next round
            diskState.tagLinks.clear();
        }
    }

    /**
     * Query for tagLinks
     */
    public List<TagState> getTags(QueryTask queryTask) {
        Operation postQuery = Operation
                .createPost(UriUtils.buildUri(this.host, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
                .setBody(queryTask);

        Operation queryResponse = this.host.waitForResponse(postQuery);

        if (queryResponse.getStatusCode() != 200) {
            return null;
        }

        QueryTask response = queryResponse.getBody(QueryTask.class);
        List<TagState> tagList = new ArrayList<>();
        response.results.documents.values().forEach(tagState -> {
            TagState ts = Utils.fromJson(tagState, TagState.class);
            tagList.add(ts);
        });
        return tagList;
    }

    public QueryTask createTagsQueryTask(boolean deletedTags) {
        Query.Builder query = Query.Builder.create()
                .addKindFieldClause(TagState.class);

        if (deletedTags) {
            query.addFieldClause(TagState.FIELD_NAME_DELETED, Boolean.TRUE);
        } else {
            query.addFieldClause(TagState.FIELD_NAME_DELETED, Boolean.FALSE);
        }

        return QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .setQuery(query.build())
                .build();
    }

    /**
     * Create tagState object
     */
    private static TagState buildTagState(String prefix, int k, int v, boolean external,
            EnumSet<TagOrigin> origin, boolean deleted, boolean expiredTag) throws Throwable {
        TagService.TagState tag = new TagService.TagState();
        tag.key = prefix + Integer.toString(k);
        tag.value = prefix + Integer.toString(v);
        tag.external = external;
        tag.origins.addAll(origin);
        tag.deleted = deleted;
        tag.documentExpirationTimeMicros = expiredTag ?
                (Utils.getNowMicrosUtc() + TimeUnit.SECONDS.toMicros(4)) : 0L;
        return tag;
    }

    /**
     * Run groomer task and wait for completion
     */
    private void executeTagsGroomerTask() {
        TagDeletionRequest state = new TagDeletionRequest();
        state.documentSelfLink = UriUtils.buildUriPath(TagGroomerTaskService.FACTORY_LINK,
                UUID.randomUUID().toString());

        Operation postOp = Operation.createPost(UriUtils.buildUri(this.host,
                TagGroomerTaskService.FACTORY_LINK))
                .setBody(state);
        this.host.waitForResponse(postOp);
        this.host.waitForFinishedTask(TagDeletionRequest.class, state.documentSelfLink);
    }

    // unused tags should be external and marked as deleted
    private void assertUnusedTags(List<TagState> tags) {
        for (TagState unusedTag : tags) {
            assertTrue(unusedTag.external);
            assertFalse(unusedTag.origins.contains(DISCOVERED.toString()));
            assertTrue(unusedTag.deleted);
            assertTrue(unusedTag.documentExpirationTimeMicros > 0);
        }
    }

    // used tags shouldn't be marked as deleted
    private void assertUsedTags(List<TagState> tags) {
        for (TagState unusedTag : tags) {
            assertFalse(unusedTag.deleted);
            assertTrue(unusedTag.documentExpirationTimeMicros == 0);
        }
    }
}
