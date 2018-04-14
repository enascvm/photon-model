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

package com.vmware.photon.controller.discovery.cloudusage;

import static com.vmware.photon.controller.model.UriPaths.CLOUD_USAGE_SERVICE;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.vmware.photon.controller.discovery.common.services.ProjectService.ProjectState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Service representing the cloud usage information materialized through queries coming through
 * {@link CloudUsageReportTaskService}.
 */
public class CloudUsageReportService extends StatefulService {

    public static final String FACTORY_LINK = CLOUD_USAGE_SERVICE;

    /**
     * State representing public cloud usage for each account.
     */
    public static class CloudUsageReport extends ServiceDocument {

        @UsageOption(option = PropertyUsageOption.ID)
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @Documentation(description = "The unique identifier")
        public String id;

        @Documentation(description = "The cloud account name")
        public String computeName;

        @Documentation(description = "The cloud type for the cloud account")
        public String environmentName;

        @Documentation(description = "The number of compute resources")
        public Long resourceCount;

        @Documentation(description = "List of projects using the cloud account")
        public List<ProjectState> projects;

        @Documentation(description = "The tenant links")
        public List<String> tenantLinks;
    }

    public CloudUsageReportService() {
        super(CloudUsageReport.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
    }

    @Override
    public void handleStart(Operation startPost) {
        if (!checkForBody(startPost)) {
            return;
        }

        CloudUsageReport state = startPost.getBody(CloudUsageReport.class);
        long interval = state.documentExpirationTimeMicros - Utils.getNowMicrosUtc();
        if (interval <= 0) {
            interval = TimeUnit.SECONDS.toMicros(getHost().getMaintenanceIntervalMicros() * 2);
        }

        super.setMaintenanceIntervalMicros(interval);
        startPost.complete();
    }

    @Override
    public void handlePut(Operation put) {
        Operation.failActionNotSupported(put);
    }
}
