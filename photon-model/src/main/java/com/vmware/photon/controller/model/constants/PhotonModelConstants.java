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

package com.vmware.photon.controller.model.constants;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PhotonModelConstants {

    // Network resource sub-type constants
    public static final String NETWORK_SUBTYPE_NETWORK_STATE = "NetworkState";
    public static final String NETWORK_SUBTYPE_SUBNET_STATE = "SubnetState";
    public static final String NETWORK_SUBTYPE_NETWORK_INTERFACE_STATE = "NetworkInterfaceState";

    // Photon-Model Metric related Constants
    public static final String CPU_UTILIZATION_PERCENT = "CPUUtilizationPercent";
    public static final String DISK_READ_BYTES = "DiskReadBytes";
    public static final String DISK_WRITE_BYTES = "DiskWriteBytes";
    public static final String NETWORK_IN_BYTES = "NetworkInBytes";
    public static final String NETWORK_OUT_BYTES = "NetworkOutBytes";
    public static final String CPU_CREDIT_USAGE_COUNT = "CPUCreditUsageCount";
    public static final String CPU_CREDIT_BALANCE_COUNT = "CPUCreditBalanceCount";
    public static final String DISK_READ_OPS_COUNT = "DiskReadOperationsCount";
    public static final String DISK_WRITE_OPS_COUNT = "DiskWriteOperationsCount";
    public static final String NETWORK_PACKETS_IN_COUNT = "NetworkPacketsInCount";
    public static final String NETWORK_PACKETS_OUT_COUNT = "NetworkPacketsOutCount";
    public static final String STATUS_CHECK_FAILED_COUNT = "StatusCheckFailedCount";
    public static final String STATUS_CHECK_FAILED_COUNT_INSTANCE = "StatusCheckFailedCount_Instance";
    public static final String STATUS_CHECK_FAILED_COUNT_SYSTEM = "StatusCheckFailedCount_System";
    public static final String ESTIMATED_CHARGES = "EstimatedCharges";
    public static final String CURRENT_BURN_RATE_PER_HOUR = "CurrentBurnRatePerHour";
    public static final String AVERAGE_BURN_RATE_PER_HOUR = "AverageBurnRatePerHour";
    public static final String COST = "Cost";
    public static final String RESERVED_INSTANCE_DURATION = "ReservedInstanceDuration";

    public static final String DISK_WRITE_TIME_SECONDS = "DiskWriteTimeSeconds";
    public static final String DISK_READ_TIME_SECONDS = "DiskReadTimeSeconds";
    public static final String MEMORY_AVAILABLE_BYTES = "MemoryAvailableBytes";
    public static final String MEMORY_USED_BYTES = "MemoryUsedBytes";
    public static final String MEMORY_AVAILABLE_PERCENT = "MemoryAvailablePercent";
    public static final String MEMORY_USED_PERCENT = "MemoryUsedPercent";
    public static final String STORAGE_USED_BYTES = "StorageUsedBytes";
    public static final String STORAGE_AVAILABLE_BYTES = "StorageAvailableBytes";

    public static final String DISK_LINK = "diskLink";
    /**
     * Refers to the details of storage where the compute is provisioned. For ex, In vSphere it
     * could refer to the datastore where the Compute lies. This will be ; separated data if
     * there are multiple items in it. In Azure it could refer to the storage account names.
     */
    public static final String STORAGE_REFERENCE = "__storageReference";
    /**
     * Refers to the disk description link for a given disk.
     */
    public static final String TEMPLATE_DISK_LINK = "__templateDiskLink";

    public static final String SERVICE_RESOURCE_COST = "Service.%s.ResourceCost";
    public static final String SERVICE_OTHER_COST = "Service.%s.OtherCost";

    // Photon-Model Metric Unit related constants
    public static final String UNIT_COUNT = "Count";
    public static final String UNIT_BYTES = "Bytes";
    public static final String UNIT_PERCENT = "Percent";
    public static final String UNIT_SECONDS = "Seconds";
    public static final String UNIT_MILLISECONDS = "MilliSeconds";
    public static final String UNIT_MICROSECONDS = "MicroSeconds";
    public static final String UNIT_HOURS = "Hours";
    public static final String UNIT_COST = "USD";
    public static final String EMPTY_STR = "";

    // Photon-Model specific constants
    public static final String API_CALL_COUNT = "APICallCount";
    public static final String SOURCE_TASK_LINK = "SourceTaskLink";
    public static final String LAST_SUCCESSFUL_STATS_COLLECTION_TIME = "LastSuccessfulCollectionTimeInMicros";
    public static final String DELETED_VM_COUNT = "DeletedVmCount";

    public static final int CLOUD_CONFIG_DEFAULT_FILE_INDEX = 0;

    private static final Map<String, String> METRIC_UNIT_MAP;

    static {
        // Map of Photon-Model stat keys to their respective units
        Map<String, String> statMap = new HashMap<>();
        statMap.put(CPU_UTILIZATION_PERCENT, UNIT_PERCENT);
        statMap.put(DISK_READ_BYTES, UNIT_BYTES);
        statMap.put(DISK_WRITE_BYTES, UNIT_BYTES);
        statMap.put(NETWORK_IN_BYTES, UNIT_BYTES);
        statMap.put(NETWORK_OUT_BYTES, UNIT_BYTES);
        statMap.put(CPU_CREDIT_USAGE_COUNT, UNIT_COUNT);
        statMap.put(CPU_CREDIT_BALANCE_COUNT, UNIT_COUNT);
        statMap.put(DISK_READ_OPS_COUNT, UNIT_COUNT);
        statMap.put(DISK_WRITE_OPS_COUNT, UNIT_COUNT);
        statMap.put(NETWORK_PACKETS_IN_COUNT, UNIT_COUNT);
        statMap.put(NETWORK_PACKETS_OUT_COUNT, UNIT_COUNT);
        statMap.put(STATUS_CHECK_FAILED_COUNT, UNIT_COUNT);
        statMap.put(STATUS_CHECK_FAILED_COUNT_INSTANCE, UNIT_COUNT);
        statMap.put(STATUS_CHECK_FAILED_COUNT_SYSTEM, UNIT_COUNT);
        statMap.put(ESTIMATED_CHARGES, UNIT_COST);
        statMap.put(COST, UNIT_COST);
        statMap.put(CURRENT_BURN_RATE_PER_HOUR, UNIT_COST);
        statMap.put(AVERAGE_BURN_RATE_PER_HOUR, UNIT_COST);
        statMap.put(DISK_WRITE_TIME_SECONDS, UNIT_SECONDS);
        statMap.put(DISK_READ_TIME_SECONDS, UNIT_SECONDS);
        statMap.put(MEMORY_AVAILABLE_BYTES, UNIT_BYTES);
        statMap.put(MEMORY_USED_BYTES, UNIT_BYTES);
        statMap.put(STORAGE_USED_BYTES, UNIT_BYTES);
        statMap.put(STORAGE_AVAILABLE_BYTES, UNIT_BYTES);
        METRIC_UNIT_MAP = Collections.unmodifiableMap(statMap);
    }

    public static String getUnitForMetric(String metricName) {
        return METRIC_UNIT_MAP.get(metricName);
    }

    public enum EndpointType {
        aws("AWS"),
        azure("Azure"),
        gcp("GCP"),
        vsphere("vSphere"),
        azure_ea("Azure-EA"),
        nsxv("NSX-V"),
        nsxt("NSX-T");

        private final String value;

        private EndpointType(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return this.value;
        }
    }

    public static final String FIELD_NAME_ENDPOINT_LINK = "endpointLink";
    public static final String FIELD_NAME_ENDPOINT_LINKS = "endpointLinks";

    public static final String CUSTOM_PROP_ENDPOINT_LINK = "__endpointLink";

    // Authentication specific constants

    public static enum CredentialsScope {
        SYSTEM
    }

    public static final String CUSTOM_PROP_CREDENTIALS_SCOPE = "scope";

    public static final String CUSTOM_PROP_SSH_KEY_NAME = "sshKeyName";

    // Endpoint identifier as provided by the cloud-provider
    public static final String CLOUD_ACCOUNT_ID = "accountId";

    public static final String CLOUD_ACCOUNT_COST_SYNC_MARKER_MILLIS = "BillProcessedTimeMillis";

    // Auto discovered entities will be marked with the following custom property
    public static final String AUTO_DISCOVERED_ENTITY = "autoDiscovered";

    public static final String IS_RESOURCE_AUTO_DISCOVERY_ENABLED = "isAutoDiscoveryEnabled";

    // constant for internal tags where key pattern is vmware.enumeration.<tag>
    public static final String TAG_ENUMERATION_PREFIX = "vmware.enumeration.";
    public static final String TAG_KEY_TYPE = TAG_ENUMERATION_PREFIX + "type";

    public static final String OTHER_CHARGES = "OtherCharges";
    public static final String ACCOUNT_ONE_TIME_CHARGES = "AccountOneTimeCharges";

    // Key to be inserted while persisting service metrics of AWS,Azure
    public static final String DOES_CONTAIN_SERVICE_STATS = "containsServiceStats";

    // Key to identify documents containing the bill processed time
    public static final String CONTAINS_BILL_PROCESSED_TIME_STAT = "containsBillProcessedTimeStat";

    // Set to true then it will insert into the existing CD-ROM drive.
    public static final String INSERT_CDROM = "insertCDRom";

    // Link to a ContentState document that holds the base64 encoded content
    public static final String DISK_CONTENT_LINK = "diskContentBase64Link";

    // Batch size for operation joins.
    public static final int OPERATION_BATCH_SIZE = 100;

    // Default partition ID header.
    public static final String DEFAULT_PARTITION_ID_HEADER = "cb-tenant-id";
}
