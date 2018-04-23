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

import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.ENUMERATION_TASK_SUFFIX;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.GROOMER_TASK_SUFFIX;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.SEPARATOR;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.STATS_COLLECTION_TASK_SUFFIX;

import com.vmware.photon.controller.model.tasks.ResourceGroomerTaskService;
import com.vmware.xenon.common.UriUtils;

public class InitializationUtils {
    private static final String SCHEDULED_GROOMER_TASK_SUFFIX = "groomer-task-scheduled";
    /**
     * Returns the project enumeration task id.
     */
    public static String getEnumerationTaskId(String resourceLink) {
        return UriUtils.getLastPathSegment(resourceLink)
                + SEPARATOR + ENUMERATION_TASK_SUFFIX;
    }

    /**
     * Returns the project collection task id.
     */
    public static String getCollectionTaskId(String resourceLink) {
        return UriUtils.getLastPathSegment(resourceLink)
                + SEPARATOR + STATS_COLLECTION_TASK_SUFFIX;
    }

    /**
     * Returns the project groomer task id.
     */
    public static String getGroomerTaskId(String resourceLink) {
        return UriUtils.getLastPathSegment(resourceLink)
                + SEPARATOR + GROOMER_TASK_SUFFIX;
    }

    public static String getGroomerTaskSelfLink(String tenantLink) {
        return UriUtils.buildUriPath(ResourceGroomerTaskService.FACTORY_LINK,
                UriUtils.getLastPathSegment(tenantLink) + SEPARATOR +
                        SCHEDULED_GROOMER_TASK_SUFFIX);
    }
}
