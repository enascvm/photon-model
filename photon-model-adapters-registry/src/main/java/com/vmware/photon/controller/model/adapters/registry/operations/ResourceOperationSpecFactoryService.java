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

package com.vmware.photon.controller.model.adapters.registry.operations;

import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceOperationSpec;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;

/**
 * The purpose of this custom FactoryService is to enforce uniqueness of {@link
 * ResourceOperationSpec} documents. I.e {@link ResourceOperationSpec} objects with the same
 * {@link ResourceOperationSpec#endpointType} and {@link ResourceOperationSpec#operation} are
 * considered the same.
 */
public class ResourceOperationSpecFactoryService extends FactoryService {

    public ResourceOperationSpecFactoryService() {
        super(ResourceOperationSpec.class);

        this.setUseBodyForSelfLink(true);
    }

    @Override
    public Service createServiceInstance() throws Throwable {
        return new ResourceOperationSpecService();
    }

    @Override
    public void handlePost(Operation post) {
        post.addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE);
        super.handlePost(post);
    }

    /**
     * Override the buildDefaultChildSelfLink method to set the documentSelfLink. document
     * identifier is combination of operation and endpoint type
     * @see #generateSelfLink(ResourceOperationSpec)
     */
    @Override
    protected String buildDefaultChildSelfLink(ServiceDocument document) {
        ResourceOperationSpec initState = (ResourceOperationSpec) document;
        if (initState.operation != null && initState.endpointType != null) {
            return generateId(initState);
        }
        if (initState.documentSelfLink != null) {
            return initState.documentSelfLink;
        }
        return super.buildDefaultChildSelfLink();
    }

    public static String generateSelfLink(ResourceOperationSpec state) {
        String id = generateId(state);
        return UriUtils.buildUriPath(ResourceOperationSpecService.FACTORY_LINK, id);
    }

    private static String generateId(ResourceOperationSpec state) {
        return state.endpointType.replace("-", "_") + "-" + state.operation;
    }
}
