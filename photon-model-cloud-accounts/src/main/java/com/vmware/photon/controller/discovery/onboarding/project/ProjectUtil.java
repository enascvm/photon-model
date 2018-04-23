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

package com.vmware.photon.controller.discovery.onboarding.project;

import static com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils.getOrgId;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.SEPARATOR;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.vmware.photon.controller.discovery.common.UtilizationThreshold;
import com.vmware.photon.controller.discovery.common.services.OrganizationService;
import com.vmware.photon.controller.discovery.common.services.ProjectService;
import com.vmware.photon.controller.discovery.common.services.ProjectService.ProjectState;
import com.vmware.photon.controller.discovery.onboarding.OnboardingUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;

/**
 * Project utilities
 */
public class ProjectUtil {
    private static final String UTILIZATION_COLLECTION_TASK_SUFFIX = "Utilization";
    private static final String COST_AGGREGATION_TASK_SUFFIX = "Cost";

    public static final String CPU_METRIC = "CPUUtilizationPercent";
    public static final String MEMORY_METRIC = "MemoryUsedPercent";

    private static final Integer CPU_UTILIZATION_OVER_LIMIT = 85;
    private static final Integer CPU_UTILIZATION_UNDER_LIMIT = 10;

    private static final Integer MEMORY_UTILIZATION_OVER_LIMIT = 90;
    private static final Integer MEMORY_UTILIZATION_UNDER_LIMIT = 10;

    /**
     * Returns the project utilization task id.
     */
    public static String getUtilizationTaskId(String projectLink) {
        return UriUtils.getLastPathSegment(projectLink)
                + SEPARATOR + ProjectUtil.UTILIZATION_COLLECTION_TASK_SUFFIX;
    }

    /**
     * Returns the project cost aggregation task id.
     */
    public static String getCostAggregationTaskId(String projectLink) {
        return UriUtils.getLastPathSegment(projectLink)
                + SEPARATOR + ProjectUtil.COST_AGGREGATION_TASK_SUFFIX;
    }

    /**
     * Returns a map of UtilizationThresholds for CPU and Memory
     * with default values for a Project
     */
    public static Map<String, UtilizationThreshold> getProjectUtilizationThreshHolds() {
        UtilizationThreshold cpuUtilization = new UtilizationThreshold();
        cpuUtilization.overLimit = CPU_UTILIZATION_OVER_LIMIT;
        cpuUtilization.underLimit = CPU_UTILIZATION_UNDER_LIMIT;

        UtilizationThreshold memoryUtilization = new UtilizationThreshold();
        memoryUtilization.overLimit = MEMORY_UTILIZATION_OVER_LIMIT;
        memoryUtilization.underLimit = MEMORY_UTILIZATION_UNDER_LIMIT;

        Map<String, UtilizationThreshold> thresholdMap = new HashMap<>();
        thresholdMap.put(CPU_METRIC, cpuUtilization);
        thresholdMap.put(MEMORY_METRIC, memoryUtilization);
        return thresholdMap;
    }

    /**
     * Returns the organizationLink a project is connected to from a projectLink.
     * @param projectLink The project link.
     * @return The organizationLink if present, or null if not.
     */
    public static String extractOrgLinkFromProjectLink(String projectLink) {
        if (projectLink == null || !projectLink.startsWith(ProjectService.FACTORY_LINK)) {
            return null;
        }

        String projectId = UriUtils.getLastPathSegment(projectLink);
        if (projectId.contains(SEPARATOR)) {
            String orgId = projectId.substring(0, projectId.indexOf(SEPARATOR));
            return UriUtils.buildUriPath(OrganizationService.FACTORY_LINK, orgId);
        } else {
            return null;
        }
    }

    /**
     * Creates a consumer that accepts an exception. Checks if the exception is a forbidden exception
     * and then performs an additional check to see if the user in question is an org admin and if so then
     * can perform the operation at hand.
     * @param service The service instance invoking this utility method
     * @param projectLink The project link
     * @param userLink The user link
     * @param onSuccess The success consumer if this method completes successfully
     * @param onFailure The failure consumer in case this method encounters failure
     * @return The consumer that checks for org admin permissions.
     */
    public static Consumer<Throwable> createFailureConsumerToCheckOrgAdminAccess(Service service,
            String projectLink, String userLink, Consumer<Operation> onSuccess,
            Consumer<Throwable> onFailure) {
        return (failureEx) -> {
            if (failureEx instanceof IllegalAccessError
                    && failureEx.getMessage().toLowerCase().contains(OnboardingUtils.FORBIDDEN)) {
                // Check if the user is org admin and can perform project updates
                Operation.createGet(UriUtils.buildUri(service.getHost(), projectLink))
                        .setReferer(service.getHost().getUri())
                        .setCompletion((getOp, getEx) -> {
                            if (getEx != null) {
                                onFailure.accept(getEx);
                                return;
                            }
                            ProjectState projectState = getOp.getBody(ProjectState.class);
                            String organizationLink = getOrgId(projectState.tenantLinks);
                            OnboardingUtils.checkIfUserIsValid(service, userLink,
                                    organizationLink, true, onSuccess,
                                    onFailure);
                        }).sendWith(service);
            } else {
                onFailure.accept(failureEx);
            }
        };
    }
}
