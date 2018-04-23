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

package com.vmware.photon.controller.discovery.common.utils;

import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.normalizeLink;
import static com.vmware.xenon.common.UriUtils.getLastPathSegment;

import java.util.ArrayList;
import java.util.EnumSet;

import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService.StatsCollectionTaskState;

/**
 * Utility class to hold different data collection tasks we run in symphony.
 */
public class DataCollectionTaskUtil {

    /**
     * Returns stats collection task for a resource pool.
     */
    public static StatsCollectionTaskState createStatsCollectionTask(String resourcePoolLink) {
        StatsCollectionTaskState taskState = new StatsCollectionTaskState();
        taskState.resourcePoolLink = resourcePoolLink;
        taskState.options = EnumSet.of(TaskOption.SELF_DELETE_ON_COMPLETION);
        taskState.documentSelfLink = normalizeLink(
                StatsCollectionTaskService.FACTORY_LINK, getLastPathSegment(resourcePoolLink));
        taskState.customizationClauses = new ArrayList<>();
        taskState.customizationClauses.add(DataCollectionTaskHelper.getStatsCollectionTaskQuery());
        return taskState;
    }
}
