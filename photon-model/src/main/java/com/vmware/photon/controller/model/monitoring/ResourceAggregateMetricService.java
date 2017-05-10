/*
 * Copyright (c) 2015-2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.monitoring;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;

import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceStats.TimeSeriesStats.TimeBin;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * This class is used to store aggregated metric values
 */
public class ResourceAggregateMetricService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.MONITORING + "/aggregate-metrics";

    public static FactoryService createFactory() {
        return FactoryService.createIdempotent(ResourceAggregateMetricService.class);
    }

    public ResourceAggregateMetricService() {
        super(ResourceAggregateMetric.class);
        super.toggleOption(ServiceOption.IMMUTABLE, true);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.ON_DEMAND_LOAD, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    /**
     * Service state to store aggregate metric values. The timeBin field
     * is used to store the various metric aggregations and currentIntervalTimeStampMicrosUtc
     * is the timestamp to which the metric values correspond
     */
    public static class ResourceAggregateMetric extends ServiceDocument {
        public static final String FIELD_NAME_TIMESTAMP = "currentIntervalTimeStampMicrosUtc";
        public static final String FIELD_NAME_TIMEBIN = "timeBin";

        @Documentation(description = "TimeBin for the current interval")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public TimeBin timeBin;

        @Documentation(description = "The timestamp for the current interval")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public Long currentIntervalTimeStampMicrosUtc;
    }

    @Override
    public void handleStart(Operation start) {
        try {
            processInput(start);
            start.complete();
        } catch (Throwable t) {
            start.fail(t);
        }
    }

    @Override
    public void handlePut(Operation put) {
        PhotonModelUtils.handleIdempotentPut(this, put);
    }

    private ResourceAggregateMetric processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        ResourceAggregateMetric state = op.getBody(ResourceAggregateMetric.class);
        Utils.validateState(getStateDescription(), state);
        return state;
    }
}
