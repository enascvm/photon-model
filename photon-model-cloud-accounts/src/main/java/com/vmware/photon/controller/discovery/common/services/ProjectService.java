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

import static com.vmware.photon.controller.model.UriPaths.PROJECT_SERVICE;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.DocumentIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Service to represent an project. References to this service
 * will be used as tenantLinks in other services to regulate access.
 */
public class ProjectService  extends StatefulService {
    public static final String FACTORY_LINK = PROJECT_SERVICE;

    /**
     * Enum representing the state of a project. A project can be in one of four stages
     * A project is always created in a DRAFT stage. It can then move into an ACTIVE or RETIRED stage
     * A typical project will spend most of its life cycle in an ACTIVE phase. Once a project has served
     * its purpose it should be moved to the INACTIVE phase
     * A project in an INACTIVE phase can be activated again or RETIRED
     */
    public enum ProjectStatus {
        DRAFT, ACTIVE, INACTIVE, RETIRED
    }

    /**
     * Utility method to check is a project status transition is valid
     * @param currentStatus The current status
     * @param newStatus The status to update to
     * @return
     */
    public static boolean isValidStatusUpdate(ProjectStatus currentStatus, ProjectStatus newStatus) {
        if (currentStatus.equals(newStatus)) {
            return true;
        }

        // check for valid status transitions
        boolean validTransition = false;
        switch (currentStatus) {
        case DRAFT:
            if (newStatus.equals(ProjectStatus.ACTIVE) || newStatus.equals(ProjectStatus.RETIRED)) {
                validTransition = true;
            }
            break;
        case ACTIVE:
            if (newStatus.equals(ProjectStatus.INACTIVE)) {
                validTransition = true;
            }
            break;
        case INACTIVE:
            if (newStatus.equals(ProjectStatus.ACTIVE) || newStatus.equals(ProjectStatus.RETIRED)) {
                validTransition = true;
            }
            break;
        case RETIRED:
        default:
            break;
        }
        return validTransition;
    }

    public static class ProjectState extends ServiceDocument {

        @Documentation(description = "Name of the project")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @PropertyOptions(indexing = { PropertyIndexingOption.SORT })
        public String name;

        @Documentation(description = "Project budget")
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public BigDecimal budget;

        @Documentation(description = "Project description")
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String description;

        @Documentation(description = "Project status")
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public ProjectStatus status;

        @Documentation(description = "auth links to control access to the project")
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @UsageOption(option = PropertyUsageOption.LINKS)
        public Set<String> tenantLinks;

        @Documentation(description = "Project Utilization thresholds by photon-model metrics")
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Map<String, UtilizationThreshold> utilizationThresholds;

    }

    public ProjectService() {
        super(ProjectState.class);
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
            ProjectState newState = start.getBody(ProjectState.class);
            Utils.validateState(getStateDescription(), newState);
            start.setBody(newState).complete();
        } catch (Exception e) {
            start.fail(e);
            return;
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        ProjectState currentState = getState(patch);
        ProjectState newState = getBody(patch);
        Utils.mergeWithState(getStateDescription(), currentState, newState);
        patch.complete();
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        // enable metadata indexing
        td.documentDescription.documentIndexingOptions =
                EnumSet.of(DocumentIndexingOption.INDEX_METADATA);

        ProjectState template = (ProjectState) td;
        return template;
    }
}
