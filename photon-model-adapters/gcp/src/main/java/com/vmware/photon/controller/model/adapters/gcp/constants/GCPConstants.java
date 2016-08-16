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

package com.vmware.photon.controller.model.adapters.gcp.constants;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import com.google.api.services.compute.ComputeScopes;

/**
 * GCP related constants.
 */
public class GCPConstants {
    // GCP API URIs
    private static final String BASE_URI = "https://www.googleapis.com";
    private static final String GCP_API_VERSION = "v1";
    private static final String BASE_COMPUTE_TEMPLATE_URI = BASE_URI + "/compute/"
            + GCP_API_VERSION + "/projects/%s/zones/%s";
    public static final String LIST_VM_TEMPLATE_URI = BASE_COMPUTE_TEMPLATE_URI + "/instances";
    public static final String MONITORING_API_BASE_URI = "https://monitoring.googleapis.com/";
    public static final String MONITORING_API_VERSION = "v3/";
    public static final String MONITORING_API_URI = MONITORING_API_BASE_URI + MONITORING_API_VERSION
            + "projects/";
    // Permission level for retrieving stats, used in authentication.
    public static final Collection<String> SCOPES = Collections.singleton(ComputeScopes.CLOUD_PLATFORM);

    // GCP API Constants
    public static final String MAX_RESULTS = "maxResults";
    public static final String PAGE_TOKEN = "pageToken";

    // GCP Auth URIs
    private static final String OAUTH_API_VERSION = "v4";
    public static final String TOKEN_REQUEST_URI = BASE_URI + "/oauth2/"
            + OAUTH_API_VERSION + "/token";

    // GCP Auth Constants
    // This is the prefix of the request body, which is used to get access token.
    public static final String TOKEN_REQUEST_BODY_TEMPLATE =
            "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=%s";
    // This is the prefix of the authorization header prefix.
    public static final String AUTH_HEADER_BEARER_PREFIX = "Bearer ";
    public static final String PRIVATE_KEY = "PRIVATE KEY";
    public static final String DEFAULT_AUTH_TYPE = "GoogleAuth";

    // GCP Disk Properties
    public static final String DEFAULT_DISK_SOURCE_IMAGE = "defaultDiskSourceImage";
    public static final String DISK_AUTO_DELETE = "autoDelete";
    public static final String DISK_TYPE_PERSISTENT = "PERSISTENT";
    public static final String DEFAULT_DISK_SERVICE_REFERENCE = "defaultDiskServiceReference";
    public static final long DEFAULT_DISK_CAPACITY = 10000L;

    // GCP CPU Properties
    public static final String CPU_PLATFORM = "CPUPlatform";
    public static final String DEFAULT_CPU_PLATFORM = "Ivy Bridge";
    public static final String DEFAULT_IMAGE_REFERENCE = "Canonical:UbuntuServer:14.04.3-LTS:latest";

    // GCP Instance Status Constants
    public static final String INSTANCE_STATUS_PROVISIONING = "PROVISIONING";
    public static final String INSTANCE_STATUS_STAGING = "STAGING";
    public static final String INSTANCE_STATUS_RUNNING = "RUNNING";
    public static final String INSTANCE_STATUS_STOPPING = "STOPPING";
    public static final String INSTANCE_STATUS_SUSPENDED = "SUSPENDED";
    public static final String INSTANCE_STATUS_SUSPENDING = "SUSPENDING";
    public static final String INSTANCE_STATUS_TERMINATED = "TERMINATED";

    // GCP Time Constants
    public static final long ONE_HOUR_IN_SECOND = 3600L;

    // GCP Operation Constants
    public static final String OPERATION_STATUS_DONE = "DONE";

    // GCP Region Constants
    public static final String UNKNOWN_REGION = "Unknown";
    public static final String EASTERN_US = "Eastern US";
    public static final String CENTRAL_US = "Central US";
    public static final String WESTERN_EUROPE = "Western Europe";
    public static final String EAST_ASIA = "East Asia";

    // GCP Zone Constants
    public static final String US_EAST1_B = "us-east1-b";
    public static final String US_EAST1_C = "us-east1-c";
    public static final String US_EAST1_D = "us-east1-d";
    public static final String US_CENTRAL1_A = "us-central1-a";
    public static final String US_CENTRAL1_B = "us-central1-b";
    public static final String US_CENTRAL1_C = "us-central1-c";
    public static final String US_CENTRAL1_F = "us-central1-f";
    public static final String EUROPE_WEST1_B = "europe-west1-b";
    public static final String EUROPE_WEST1_C = "europe-west1-c";
    public static final String EUROPE_WEST1_D = "europe-west1-d";
    public static final String ASIA_EAST1_A = "asia-east1-a";
    public static final String ASIA_EAST1_B = "asia-east1-b";
    public static final String ASIA_EAST1_C = "asia-east1-c";

    // GCP Metric Name Constants
    public static final String METRIC_NAME_PREFIX = "compute.googleapis.com/";
    public static final String CPU_UTILIZATION = "instance/cpu/utilization";
    public static final String DISK_READ_BYTES = "instance/disk/read_bytes_count";
    public static final String DISK_READ_OPERATIONS = "instance/disk/read_ops_count";
    public static final String DISK_WRITE_BYTES = "instance/disk/write_bytes_count";
    public static final String DISK_WRITE_OPERATIONS = "instance/disk/write_ops_count";
    public static final String NETWORK_IN_BYTES = "instance/network/received_bytes_count";
    public static final String NETWORK_IN_PACKETS = "instance/network/received_packets_count";
    public static final String NETWORK_OUT_BYTES = "instance/network/sent_bytes_count";
    public static final String NETWORK_OUT_PACKETS = "instance/network/sent_packets_count";

    // GCP Metric Unit Constants
    public static final String UNIT_COUNT = "Count";
    public static final String UNIT_BYTE = "Bytes";
    public static final String UNIT_PERCENT = "Percent";
    public static final String UTC_TIMEZONE_ID = "UTC";

    // GCP Metric Filter Constants
    public static final String TIMESERIES_PREFIX = "/timeSeries";
    public static final String FILTER_KEY = "filter";
    public static final String INTERVAL_START_TIME = "interval.startTime";
    public static final String INTERVAL_END_TIME = "interval.endTime";
    public static final String METRIC_TYPE_FILTER = "metric.type";
    public static final String INSTANCE_NAME_FILTER = "resource.label.instance_id";
    public static final String RESPONSE_PAGE_SIZE = "pageSize";
    public static final String TIME_INTERVAL_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'";
    // To subtract 4 minutes from the current time and get start time.
    public static final long START_TIME_MILLIS = TimeUnit.MINUTES.toMillis(4);
    // To subtract 3 minutes from the current time and get end time.
    public static final long END_TIME_MILLIS = TimeUnit.MINUTES.toMillis(3);

    // GCP Aggregation Related Metric Filter Constants
    public static final String AGGREGATION_ALIGNMENT_PERIOD = "aggregation.alignmentPeriod";
    public static final String AGGREGATION_PER_SERIES_ALIGNER = "aggregation.perSeriesAligner";
    public static final String AGGREGATION_CROSS_SERIES_REDUCER = "aggregation.crossSeriesReducer";
    // Parameter specifying the duration for aggregation.
    // Value is slightly more than the interval to ensure aggregation results in exactly one data
    // point.
    public static final String AGGREGATION_ALIGNMENT_PERIOD_VALUE = "61s";
    // Aggregation enum values for CPU Utilization
    public static final String CPU_UTIL_PER_SERIES_ALIGNER_VALUE = "ALIGN_MEAN";
    public static final String CPU_UTIL_CROSS_SERIES_REDUCER_VALUE = "REDUCE_MEAN";
    // Aggregation enum values for all other stats.
    public static final String PER_SERIES_ALIGNER_VALUE = "ALIGN_SUM";
    public static final String CROSS_SERIES_REDUCER_VALUE = "REDUCE_SUM";
}