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

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import com.vmware.xenon.common.UriUtils;

/**
 * Stats related utils.
 */
public class StatsUtil {

    /**
     * Separator for metric key. To be able parse back and forth between metric key and
     * resourceId/metricName, we assume that metric names don't contain the separator "-".
     */
    public static final String SEPARATOR = "_";

    /**
     * Builds a metric key for given resource link, metric name and timestamp.
     */
    public static String getMetricKey(String resourceLink, String metricName,
            Long sourceTimeMicrosUtc) {
        return UriUtils.getLastPathSegment(resourceLink) + SEPARATOR + metricName + SEPARATOR
                + sourceTimeMicrosUtc;
    }

    /**
     * Builds a metric key for given resource link and timestamp.
     */
    public static String getMetricKey(String resourceLink,
            Long sourceTimeMicrosUtc) {
        return UriUtils.getLastPathSegment(resourceLink) + SEPARATOR +
                + sourceTimeMicrosUtc;
    }

    /**
     * Builds a metric key for given resource link and metric name.
     */
    public static String getMetricKeyPrefix(String resourceLink, String metricName) {
        return UriUtils.getLastPathSegment(resourceLink) + SEPARATOR + metricName;
    }

    /**
     * Returns all the fragments of the metricLink.
     */
    private static MetricKeyComponents getMetricLinkFragments(String metricLink) {
        metricLink = UriUtils.getLastPathSegment(metricLink);

        String[] linkFragments = metricLink.split(SEPARATOR);

        if (linkFragments.length != 3) {
            throw new IllegalArgumentException("Incorrect metric key format "
                    + Arrays.toString(linkFragments)
                    + ". Expected resource-id_metricName_timestamp.");
        }

        MetricKeyComponents metricKeyComponents = new MetricKeyComponents();
        metricKeyComponents.resourceId = linkFragments[0];
        metricKeyComponents.metricName = linkFragments[1];
        metricKeyComponents.timestamp = linkFragments[2];
        return metricKeyComponents;
    }

    /**
     * Returns the metric name for given metric link.
     */
    public static String getMetricName(String metricLink) {
        MetricKeyComponents metricKeyComponents = getMetricLinkFragments(metricLink);

        // Return the second string in the array.
        String metricName = metricKeyComponents.metricName;
        if (metricName.contains(SEPARATOR)) {
            throw new IllegalArgumentException(
                    "Metric name '" + metricName + "'should not contain '" + SEPARATOR + "'");
        }
        return metricName;
    }

    /**
     * Returns the metric key prefix.
     */
    public static String getMetricLinkPrefix(String metricLink) {
        MetricKeyComponents metricKeyComponents = getMetricLinkFragments(metricLink);
        return metricKeyComponents.resourceId + SEPARATOR + metricKeyComponents.metricName;
    }

    /**
     * Returns the resource id for given metric link.
     */
    public static String getResourceId(String metricLink) {
        MetricKeyComponents metricKeyComponents = getMetricLinkFragments(metricLink);
        return metricKeyComponents.resourceId;
    }

    /**
     * Computes the beginning  of the rollup interval.
     */
    public static long computeIntervalBeginMicros(long timestampMicros, long bucketDurationMillis) {
        long bucketDurationMicros = TimeUnit.MILLISECONDS.toMicros(bucketDurationMillis);
        timestampMicros -= (timestampMicros % bucketDurationMicros);
        return timestampMicros;
    }

    /**
     * Computes the end of the rollup interval.
     */
    public static long computeIntervalEndMicros(long timestampMicros, long bucketDurationMillis) {
        timestampMicros = computeIntervalBeginMicros(timestampMicros, bucketDurationMillis);
        return (timestampMicros + TimeUnit.MILLISECONDS.toMicros(bucketDurationMillis));
    }

    private static class MetricKeyComponents {
        public String resourceId;
        public String metricName;
        @SuppressWarnings("unused")
        public String timestamp;
    }
}
