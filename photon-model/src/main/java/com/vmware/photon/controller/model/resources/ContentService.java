/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

import static com.vmware.xenon.common.Operation.MEDIA_TYPE_APPLICATION_OCTET_STREAM;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.StatefulService;

/**
 * A service to store large data objects
 */
public class ContentService extends StatefulService {
    public static final String FACTORY_LINK = UriPaths.RESOURCES + "/content";

    //maximum document size is 16MB
    static final int MAX_SERIALIZED_SIZE_BYTES = 16 * 1024 * 1024;

    public static class ContentState extends ResourceState {
        @PropertyOptions(indexing = { PropertyIndexingOption.STORE_ONLY })
        public byte[] binaryData;
    }

    public ContentService() {
        super(ContentState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleGet(Operation get) {
        if (get.getContentType().equalsIgnoreCase(MEDIA_TYPE_APPLICATION_OCTET_STREAM)) {
            ContentState state = this.getState(get);
            get.setBody(state.binaryData);
            get.complete();
        } else {
            super.handleGet(get);
        }
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ContentState template = (ContentState) super.getDocumentTemplate();

        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(template);
        template.documentDescription.serializedStateSizeLimit = MAX_SERIALIZED_SIZE_BYTES;

        return template;
    }
}
