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

public class StatsConstants {

    public static final String DAILY_SUFFIX = "(Daily)";
    public static final String HOUR_SUFFIX = "(Hourly)";
    public static final String MIN_SUFFIX = "(Minutes)";

    // number of buckets to keep data for an hour at one minute intervals
    public static final int NUM_BUCKETS_MINUTE_DATA = 60;
    // size of the bucket in milliseconds for maintaining data at a minute granularity
    public static final int BUCKET_SIZE_MINUTES_IN_MILLIS = 1000 * 60;

    // number of buckets to keep data  for a day at one hour intervals
    public static final int NUM_BUCKETS_HOURLY_DATA = 24;
    // number of buckets to keep data for 4 weeks at 1 day interval
    public static final int NUM_BUCKETS_DAILY_DATA = 4 * 7;

    // size of the bucket in milliseconds for maintaining data at the granularity of an hour
    public static final int BUCKET_SIZE_HOURS_IN_MILLS = BUCKET_SIZE_MINUTES_IN_MILLIS * 60;
    // size of the bucket in milliseconds for maintaining data at the granularity of a day
    public static final int BUCKET_SIZE_DAYS_IN_MILLS = BUCKET_SIZE_HOURS_IN_MILLS * 24;
}
