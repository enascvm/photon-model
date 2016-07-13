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

import java.util.Map;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.resources.ResourceState;

import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceStats.TimeSeriesStats.DataPoint;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

public class ResourceAggregateMetricsService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.MONITORING + "/resource-aggregate-metrics";

    public static FactoryService createFactory() {
        return FactoryService.createIdempotent(ResourceAggregateMetricsService.class);
    }

    public ResourceAggregateMetricsService() {
        super(ResourceAggregateMetricsState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.ON_DEMAND_LOAD, true);
    }

    public static class ResourceAggregateMetricsState extends  ResourceState {

        @Documentation(description = "Aggregate stats for a resource")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public Map<String, DataPoint> aggregations;

        @Documentation(description = "The compute service instance this state belongs to")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String computeServiceLink;

        @Documentation(description = "The timestamp this stat belongs to")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public long sourceTimeMicrosUtc;
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
        try {
            ResourceAggregateMetricsState returnState = processInput(put);
            setState(put, returnState);
            put.setBody(null).complete();
        } catch (Throwable t) {
            put.fail(t);
        }
    }

    private ResourceAggregateMetricsState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        ResourceAggregateMetricsState state = op.getBody(ResourceAggregateMetricsState.class);
        Utils.validateState(getStateDescription(), state);
        return state;
    }
}
