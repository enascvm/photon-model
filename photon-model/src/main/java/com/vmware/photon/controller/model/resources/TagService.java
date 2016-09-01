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

import java.util.List;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Describes a tag instance. Each tag consists of a key and an optional value.
 * Tags are assigned to resources through the {@link ResourceState#tagLinks} field.
 */
public class TagService extends StatefulService {
    public static final String FACTORY_LINK = UriPaths.RESOURCES + "/tags";

    /**
     * This class represents the document state associated with a
     * {@link com.vmware.photon.controller.model.resources.TagService}.
     */
    public static class TagState extends ServiceDocument {
        @Documentation(description = "Tag key")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String key;

        @Documentation(description = "Tag value, empty string used for none (null not accepted)")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String value;

        @Documentation(description = "A list of tenant links that can access this tag")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @PropertyOptions(indexing = { PropertyIndexingOption.EXPAND })
        public List<String> tenantLinks;
    }

    public TagService() {
        super(TagState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleCreate(Operation create) {
        try {
            processInput(create);
            create.complete();
        } catch (Throwable t) {
            create.fail(t);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        TagState currentState = getState(patch);
        TagState patchState = processInput(patch);

        // auto-merge properties
        boolean hasStateChanged = Utils.mergeWithState(
                getStateDescription(), currentState, patchState);

        if (!hasStateChanged) {
            patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
        } else {
            patch.setBody(currentState);
        }
        patch.complete();
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        TagState template = (TagState) td;
        template.key = "key-1";
        template.key = "value-1";
        return template;
    }

    private TagState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        TagState state = op.getBody(TagState.class);
        Utils.validateState(getStateDescription(), state);
        return state;
    }
}
