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

package com.vmware.photon.controller.model.tasks.monitoring;

import com.vmware.xenon.common.UriUtils;

/**
 * Stats related utils.
 */
public class StatsUtil {

    /**
     * Separator for metric key. To be able parse back and forth between metric key and
     * resourceId/metricName, we assume that metric names don't contain the separator "-".
     */
    public static final String SEPARATOR = "-";

    /**
     * Builds a metric key for given resource link and metric name.
     */
    public static String getMetricKey(String resourceLink, String metricName) {
        return UriUtils.getLastPathSegment(resourceLink) + SEPARATOR + metricName;
    }

    /**
     * Returns the metric name for given metric link.
     */
    public static String getMetricName(String metricLink) {
        metricLink = UriUtils.getLastPathSegment(metricLink);
        int index = metricLink.lastIndexOf(SEPARATOR);
        if (index == -1) {
            return null;
        }

        String metricName = metricLink.substring(index + 1);
        if (metricName.contains(SEPARATOR)) {
            throw new IllegalArgumentException(
                    "Metric name '" + metricName + "'should not contain '" + SEPARATOR + "'");
        }
        return metricLink.substring(index + 1);
    }

    /**
     * Returns the resource id for given metric link.
     */
    public static String getResourceId(String metricLink) {
        metricLink = UriUtils.getLastPathSegment(metricLink);
        int index = metricLink.lastIndexOf(SEPARATOR);
        if (index == -1) {
            return null;
        }
        return metricLink.substring(0, index);
    }
}
