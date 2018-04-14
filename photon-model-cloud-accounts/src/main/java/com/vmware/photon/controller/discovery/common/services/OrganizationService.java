/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.discovery.common.services;

import static com.vmware.photon.controller.model.UriPaths.ORGANIZATION_SERVICE;

import java.util.EnumSet;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.DocumentIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Service to represent an organization. References to this service
 * will be used as tenantLinks in other services to regulate access.
 * This service will have additional fields like a AD source and URI
 * namespace that are not part of a TenantService instance
 *
 */
public class OrganizationService  extends StatefulService {
    public static final String FACTORY_LINK = ORGANIZATION_SERVICE;

    public static class OrganizationState extends ServiceDocument {
        public static final String FIELD_NAME_ID = "id";
        public static final String FIELD_NAME_NAME = "name";
        public static final String FIELD_NAME_DISPLAY_NAME = "displayName";

        /**
         * Unique identifier for the org. If not specified during creation, a random one is
         * automatically set. This value cannot be changed once set.
         */
        @UsageOption(option = PropertyUsageOption.ID)
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String id;

        /**
         * Name of the organization.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.SINGLE_ASSIGNMENT)
        @PropertyOptions(indexing = { PropertyIndexingOption.SORT })
        public String name;

        /**
         * Display name of the organization.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @PropertyOptions(indexing = { PropertyIndexingOption.SORT })
        public String displayName;
    }

    public OrganizationService() {
        super(OrganizationState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleStart(Operation start) {
        if (!start.hasBody()) {
            start.fail(new IllegalArgumentException("body is required"));
            return;
        }
        try {
            OrganizationState newState = start.getBody(OrganizationState.class);
            Utils.validateState(getStateDescription(), newState);
            start.setBody(newState).complete();
        } catch (Exception e) {
            start.fail(e);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        OrganizationState currentState = getState(patch);
        OrganizationState newState = getBody(patch);
        Utils.mergeWithState(getStateDescription(), currentState, newState);
        patch.complete();
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        // enable metadata indexing
        td.documentDescription.documentIndexingOptions =
                EnumSet.of(DocumentIndexingOption.INDEX_METADATA);

        OrganizationState template = (OrganizationState) td;
        return template;
    }
}
