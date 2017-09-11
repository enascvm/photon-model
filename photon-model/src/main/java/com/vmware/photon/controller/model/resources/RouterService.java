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

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;

import static com.vmware.xenon.common.UriUtils.buildUriPath;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.TenantService;

/**
 * Represents a networking router.
 */
public class RouterService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES_ROUTERS;

    /**
     * Represents the state of a router.
     */
    public static class RouterState extends ResourceState {

        public static final String FIELD_NAME_TYPE = "type";

        /**
         * Link to the endpoint the router belongs to.
         */
        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.REQUIRED)
        public String endpointLink;

        /**
         * Router type defined by adapter.
         */
        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String type;
    }

    public RouterService() {
        super(RouterState.class);

        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleCreate(Operation start) {
        try {
            processInput(start);
            start.complete();
        } catch (Throwable t) {
            start.fail(t);
        }
    }

    @Override
    public void handlePut(Operation put) {
        try {
            RouterState returnState = processInput(put);
            setState(put, returnState);
            put.complete();
        } catch (Throwable t) {
            put.fail(t);
        }
    }

    @Override
    public void handlePatch(Operation patchOp) {
        try {
            ResourceUtils.handlePatch(
                    patchOp, getState(patchOp), getStateDescription(), RouterState.class, null);
        } catch (Throwable t) {
            patchOp.fail(t);
        }
    }

    private RouterState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        RouterState state = op.getBody(RouterState.class);
        validateState(state);
        return state;
    }

    /**
     * Common validation login.
     */
    private void validateState(RouterState routerState) {
        Utils.validateState(getStateDescription(), routerState);
    }

    @Override
    public RouterState getDocumentTemplate() {
        RouterState routerState = (RouterState) super.getDocumentTemplate();
        ServiceUtils.setRetentionLimit(routerState);

        routerState.id = "endpoint-specific-router-id";
        routerState.name = "endpoint-specific-router-name";
        routerState.desc = "user-friendly-router-description";
        routerState.regionId = "endpoint-specific-router-region-id";
        routerState.type = "tier-0-logical-router";

        routerState.endpointLink = buildUriPath(EndpointService.FACTORY_LINK, "the-A-cloud");
        routerState.groupLinks = singleton(
                buildUriPath(ResourceGroupService.FACTORY_LINK, "the-A-folder"));
        routerState.tenantLinks = singletonList(
                buildUriPath(TenantService.FACTORY_LINK, "the-A-tenant"));

        return routerState;
    }
}
