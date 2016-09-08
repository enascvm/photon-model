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

import java.util.ArrayList;
import java.util.UUID;

import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;

/**
 * The purpose of this custom FactoryService is to enforce uniqueness of TagState documents. I.e
 * TagState objects with the same field values are considered the same.
 */
public class TagFactoryService extends FactoryService {

    public TagFactoryService() {
        super(TagState.class);
    }

    @Override
    public Service createServiceInstance() throws Throwable {
        return new TagService();
    }

    /**
     * Override the handlePost method to set the documentSelfLink. We don't want to have multiple
     * tags with the same values, so we build the documentSelfLink ourselves taking into account all
     * fields in the TagState @see TagFactoryService#generateSelfLink
     *
     * @param op
     */
    @Override
    public void handlePost(Operation op) {
        if (op.hasBody()) {
            TagState body = (TagState) op.getBody(this.stateType);
            if (body == null) {
                op.fail(new IllegalArgumentException("structured body is required"));
                return;
            }

            if (body.documentSourceLink != null) {
                op.fail(new IllegalArgumentException("clone request not supported"));
                return;
            }

            body.documentSelfLink = generateSelfLink(body);
            op.setBody(body);
            op.complete();
        } else {
            op.fail(new IllegalArgumentException("body is required"));
        }
    }

    public static String generateSelfLink(TagState tagState) {
        String id = generateId(tagState);
        return UriUtils.buildUriPath(TagService.FACTORY_LINK, id);
    }

    private static String generateId(TagState tagState) {
        ArrayList<String> values = new ArrayList<>();
        values.add(tagState.key);
        values.add(tagState.value);
        if (tagState.tenantLinks != null) {
            values.addAll(tagState.tenantLinks);
        }
        return UUID.nameUUIDFromBytes(values.toString().getBytes()).toString();
    }
}
