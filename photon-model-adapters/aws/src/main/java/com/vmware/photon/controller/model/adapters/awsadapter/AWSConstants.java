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

package com.vmware.photon.controller.model.adapters.awsadapter;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;

public class AWSConstants {

    public static final String AWS_INSTANCE_ID_PREFIX = "i-";

    public static final String AWS_SECURITY_GROUP = "awsSecurityGroup";
    public static final String AWS_SECURITY_GROUP_ID = "awsSecurityGroupId";

    public static final String AWS_TAG_NAME = "Name";

    public static final String AWS_VPC_ID = "awsVpcId";
    public static final String AWS_VPC_ID_FILTER = "vpc-id";

    public static final String AWS_SUBNET_ID_FILTER = "subnet-id";
    public static final String AWS_SUBNET_CIDR_FILTER = "cidrBlock";

    public static final String AWS_GROUP_ID_FILTER = "group-id";
    public static final String AWS_GROUP_NAME_FILTER = "group-name";

    public static final String AWS_GATEWAY_ID = "awsGatewayID";
    public static final String AWS_VPC_ROUTE_TABLE_ID = "awsMainRouteTableID";
    public static final String AWS_MAIN_ROUTE_ASSOCIATION = "association.main";

    public static final String AWS_IMAGE_NAME_FILTER = "name";
    public static final String AWS_IMAGE_STATE_FILTER = "state";
    public static final String AWS_IMAGE_STATE_AVAILABLE = "available";
    public static final String AWS_IMAGE_IS_PUBLIC_FILTER = "is-public";

    public static final String INSTANCE_STATE = "instance-state-name";
    public static final String INSTANCE_STATE_RUNNING = "running";
    public static final String INSTANCE_STATE_PENDING = "pending";
    public static final String INSTANCE_STATE_STOPPING = "stopping";
    public static final String INSTANCE_STATE_STOPPED = "stopped";
    public static final String INSTANCE_STATE_SHUTTING_DOWN = "shutting-down";
    public static final String VOLUME_TYPE_GENERAL_PURPOSED_SSD = "gp2";
    public static final String VOLUME_TYPE_PROVISIONED_SSD = "io1";
    public static final String VOLUME_TYPE_MAGNETIC = "standard";
    public static final String SNAPSHOT_ID = "snapshotId";
    public static final String DISK_IOPS = "iops";
    public static final String DISK_ENCRYPTED_FLAG = "encrypted";
    public static final String VOLUME_TYPE = "volumeType";
    public static final String AWS_ATTACHMENT_VPC_FILTER = "attachment.vpc-id";
    public static final String AWS_BILLS_S3_BUCKET_NAME_KEY = "billsBucketName";
    public static final String AWS_ACCOUNT_ID_KEY = PhotonModelConstants.CLOUD_ACCOUNT_ID;
    public static final String AWS_LINKED_ACCOUNT_IDS = "linkedAccountIds";
    public static final String ACCOUNT_IS_AUTO_DISCOVERED
            = PhotonModelConstants.AUTO_DISCOVERED_ENTITY;
    public static final int NO_OF_DAYS_MARGIN_FOR_AWS_TO_UPDATE_BILL = 5;
    public static final int DEFAULT_NO_OF_MONTHS_TO_GET_PAST_BILLS = 11;
    public static final String AWS_ACCOUNT_BILL_PROCESSED_TIME_MILLIS
            = PhotonModelConstants.CLOUD_ACCOUNT_COST_SYNC_MARKER_MILLIS;
    public static final String AWS_INVALID_INSTANCE_ID_ERROR_CODE = "InvalidInstanceID.NotFound";
    public static final String PROPERTY_NAME_QUERY_PAGE_SIZE = UriPaths.PROPERTY_PREFIX
            + AWSConstants.class.getSimpleName() + ".QUERY_PAGE_SIZE";
    private static int QUERY_PAGE_SIZE = Integer.getInteger(PROPERTY_NAME_QUERY_PAGE_SIZE, 50);
    public static final String PROPERTY_NAME_QUERY_RESULT_LIMIT = UriPaths.PROPERTY_PREFIX
            + AWSConstants.class.getSimpleName() + ".QUERY_RESULT_LIMIT";
    private static int QUERY_RESULT_LIMIT = Integer.getInteger(PROPERTY_NAME_QUERY_RESULT_LIMIT,
            100);
    public static final String PROPERTY_NAME_CLIENT_CACHE_MAX_SIZE = UriPaths.PROPERTY_PREFIX
            + AWSConstants.class.getSimpleName() + ".CLIENT_CACHE_MAX_SIZE";
    public static int CLIENT_CACHE_MAX_SIZE = Integer.getInteger(
            PROPERTY_NAME_CLIENT_CACHE_MAX_SIZE, 50);
    public static final String PROPERTY_NAME_CLIENT_CACHE_INITIAL_SIZE = UriPaths.PROPERTY_PREFIX
            + AWSConstants.class.getSimpleName() + ".CLIENT_CACHE_INITIAL_SIZE";
    public static int CLIENT_CACHE_INITIAL_SIZE = Integer.getInteger(
            PROPERTY_NAME_CLIENT_CACHE_INITIAL_SIZE, 16);
    public static final String PROPERTY_NAME_THREAD_POOL_CACHE_MAX_SIZE = UriPaths.PROPERTY_PREFIX
            + AWSConstants.class.getSimpleName() + ".THREAD_POOL_CACHE_MAX_SIZE";
    public static int THREAD_POOL_CACHE_MAX_SIZE = Integer.getInteger(
            PROPERTY_NAME_CLIENT_CACHE_MAX_SIZE, 10);
    public static final String PROPERTY_NAME_THREAD_POOL_CACHE_INITIAL_SIZE = UriPaths.PROPERTY_PREFIX
            + AWSConstants.class.getSimpleName() + ".THREAD_POOL_CACHE_INITIAL_SIZE";
    public static int THREAD_POOL_CACHE_INITIAL_SIZE = Integer.getInteger(
            PROPERTY_NAME_CLIENT_CACHE_INITIAL_SIZE, 5);

    // AWS Metric related Constants
    public static final String CPU_UTILIZATION = "CPUUtilization";
    public static final String DISK_READ_BYTES = "DiskReadBytes";
    public static final String DISK_WRITE_BYTES = "DiskWriteBytes";
    public static final String NETWORK_IN = "NetworkIn";
    public static final String NETWORK_OUT = "NetworkOut";
    public static final String CPU_CREDIT_USAGE = "CPUCreditUsage";
    public static final String CPU_CREDIT_BALANCE = "CPUCreditBalance";
    public static final String DISK_READ_OPS = "DiskReadOps";
    public static final String DISK_WRITE_OPS = "DiskWriteOps";
    public static final String NETWORK_PACKETS_IN = "NetworkPacketsIn";
    public static final String NETWORK_PACKETS_OUT = "NetworkPacketsOut";
    public static final String STATUS_CHECK_FAILED = "StatusCheckFailed";
    public static final String STATUS_CHECK_FAILED_INSTANCE = "StatusCheckFailed_Instance";
    public static final String STATUS_CHECK_FAILED_SYSTEM = "StatusCheckFailed_System";
    public static final String ESTIMATED_CHARGES = "EstimatedCharges";
    public static final String CURRENT_BURN_RATE = "CurrentBurnRatePerHour";
    public static final String AVERAGE_BURN_RATE = "AverageBurnRatePerHour";
    public static final String COST = "Cost";
    public static final String RESERVED_INSTANCE_PLAN_DETAILS = "ReservedInstancePlanDetails";
    public static final String RESERVED_INSTANCE_DURATION = "ReservedInstanceDuration";
    public static final String SERVICE_RESOURCE_COST = "Service.%s.ResourceCost";
    public static final String SERVICE_OTHER_COST = "Service.%s.OtherCost";
    public static final String SERVICE_MONTHLY_OTHER_COST = "Service.%s.MonthlyOtherCost";
    public static final String SERVICE_RESERVED_RECURRING_COST = "Service.%s.ReservedRecurringCost";

    //All properties related to the aws storage
    public static final String DEVICE_TYPE = "deviceType";
    public static final String DEVICE_NAME = "deviceName";
    public static final String BUCKET_OWNER_NAME = "ownerName";
    public static final String STORAGE_TYPE_EBS = "EBS";
    public static final String STORAGE_TYPE_S3 = "S3";

    // AWS Metric Unit related constants
    public static final String UNIT_COUNT = "Count";
    public static final String UNIT_BYTES = "Bytes";
    public static final String UNIT_PERCENT = "Percent";
    public static final String UNIT_COST = "USD";
    public static final String UNIT_HOURS = "Hours";

    public static final String WINDOWS_PLATFORM = "windows";

    public static final long AGGREGATION_WINDOW_ALIGNMENT_TIME = TimeUnit.SECONDS.toMillis(1);

    /**
     * Number of operations to send in a batch when using OperationJoin
     */
    public static final int OPERATION_BATCH_SIZE = 50;

    // AWS client types
    public enum AwsClientType {
        EC2, CLOUD_WATCH, S3, S3_TRANSFER_MANAGER, LOAD_BALANCING
    }

    /**
     * supported aws device types.
     */
    public static enum AWSStorageType {
        EBS, EFS, INSTANCE_STORE
    }

    // AWS Error codes {{
    // http://docs.aws.amazon.com/AWSEC2/latest/APIReference/errors-overview.html#api-error-codes-table-client
    public static final String AWS_DEPENDENCY_VIOLATION_ERROR_CODE = "DependencyViolation";
    // }}

    public static void setQueryPageSize(int size) {
        QUERY_PAGE_SIZE = size;
    }

    public static int getQueryPageSize() {
        return QUERY_PAGE_SIZE;
    }

    public static void setQueryResultLimit(int resultLimit) {
        QUERY_RESULT_LIMIT = resultLimit;
    }

    public static int getQueryResultLimit() {
        return QUERY_RESULT_LIMIT;
    }

    public static void setClientCacheMaxSize(int size) {
        CLIENT_CACHE_MAX_SIZE = size;
    }

    public static int getClientCacheMaxSize() {
        return CLIENT_CACHE_MAX_SIZE;
    }

    public static void setClientCacheInitialSize(int size) {
        CLIENT_CACHE_INITIAL_SIZE = size;
    }

    public static int getClientCacheInitialSize() {
        return CLIENT_CACHE_INITIAL_SIZE;
    }

    public static void setThreadPoolCacheMaxSize(int size) {
        THREAD_POOL_CACHE_MAX_SIZE = size;
    }

    public static int getThreadPoolCacheMaxSize() {
        return THREAD_POOL_CACHE_MAX_SIZE;
    }

    public static void setThreadPoolCacheInitialSize(int size) {
        THREAD_POOL_CACHE_INITIAL_SIZE = size;
    }

    public static int getThreadPoolCacheInitialSize() {
        return THREAD_POOL_CACHE_INITIAL_SIZE;
    }

    public static List<String> AWS_DEVICE_NAMES = Arrays.asList(
            "xvdb",
            "xvdc",
            "xvdd",
            "xvde",
            "xvdf",
            "xvdg",
            "xvdh",
            "xvdi",
            "xvdj",
            "xvdk",
            "xvdl",
            "/dev/sdb",
            "/dev/sdc",
            "/dev/sdd",
            "/dev/sde",
            "/dev/sdf",
            "/dev/sdg",
            "/dev/sdh",
            "/dev/sdi",
            "/dev/sdj",
            "/dev/sdk",
            "/dev/sdl"
    );

}
