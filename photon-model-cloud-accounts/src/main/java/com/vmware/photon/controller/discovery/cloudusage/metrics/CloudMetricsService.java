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

package com.vmware.photon.controller.discovery.cloudusage.metrics;

import static com.vmware.photon.controller.model.UriPaths.CLOUD_METRICS_SERVICE;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument.Documentation;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Stateful Service representing the aggregated metrics (CPU,Memory,Network,Storage etc) for the configured
 * cloud endpoints.The aggregation is performed for endpoints in the same environment (AWS,GCP,Azure).
 * This service is used as the persistent store for metric values that are pulled from different endpoints.
 *
 */
public class CloudMetricsService extends StatefulService {

    public static final String FACTORY_LINK = CLOUD_METRICS_SERVICE;

    /**
     * State representing the metric values that are to be collected for different endpoints.
     */
    public static class CloudMetric {
        @Documentation(description = "The metric name")
        public String metricName;

        @Documentation(description = "The metric value")
        public Double metricValue;

        @Documentation(description = "The unit of the metric")
        public String metricUnit;
    }

    /**
     * State representing the metric values based for a project.
     */
    public static class CloudMetricsState extends ResourceState {

        public CloudMetricsState() {
            this.cloudMetricsByEnvironmentMap = new HashMap<>();
        }

        public CloudMetricsState(String projectName) {
            this.projectName = projectName;
            this.cloudMetricsByEnvironmentMap = new HashMap<>();
        }

        @Documentation(description = "The project name")
        public String projectName;

        @Documentation(description = "The map of cloud metrics for a project aggregated based on environment types."
                + "The key is the environment name. e.g. Azure, AWS etc.")
        public Map<String, List<CloudMetric>> cloudMetricsByEnvironmentMap;

    }

    public CloudMetricsService() {
        super(CloudMetricsState.class);
    }

    @Override
    public void handleStart(Operation startPost) {
        if (!checkForBody(startPost)) {
            return;
        }

        CloudMetricsState state = startPost.getBody(CloudMetricsState.class);
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
