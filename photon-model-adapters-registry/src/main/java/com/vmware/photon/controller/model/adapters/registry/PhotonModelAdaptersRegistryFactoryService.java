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

package com.vmware.photon.controller.model.adapters.registry;

import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService.PhotonModelAdapterConfig;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;

/**
 * The purpose of this custom FactoryService is to enforce uniqueness of
 * {@link PhotonModelAdapterConfig} documents. I.e {@link PhotonModelAdapterConfig} objects with
 * the same {@link PhotonModelAdapterConfig#id} are considered the same.
 */
public class PhotonModelAdaptersRegistryFactoryService extends FactoryService {

    public PhotonModelAdaptersRegistryFactoryService() {
        super(PhotonModelAdapterConfig.class);

        this.setUseBodyForSelfLink(true);
    }

    @Override
    public Service createServiceInstance() throws Throwable {
        return new PhotonModelAdaptersRegistryService();
    }

    @Override
    public void handlePost(Operation post) {
        post.addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE);
        super.handlePost(post);
    }

    /**
     * Override the buildDefaultChildSelfLink method to set the documentSelfLink.
     */
    @Override
    protected String buildDefaultChildSelfLink(ServiceDocument document) {
        PhotonModelAdapterConfig initState = (PhotonModelAdapterConfig) document;
        if (initState.id != null) {
            return initState.id;
        }
        if (initState.documentSelfLink != null) {
            return initState.documentSelfLink;
        }
        return super.buildDefaultChildSelfLink();
    }

}
