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

package com.vmware.photon.controller.model.adapters.azure.constants;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils;
import com.vmware.photon.controller.model.resources.DiskService;

/**
 * Azure related constants.
 */
public class AzureConstants {
    public static final String AZURE_MOCK_HOST_SYSTEM_PROPERTY = "azureMockHost";

    public static final String AZURE_CORE_MANAGEMENT_URI = "https://management.azure.com/";
    public static final String AZURE_TENANT_ID = "azureTenantId";
    public static final String AZURE_RESOURCE_GROUP_NAME = "azureResourceGroupName";

    public static final String AZURE_OSDISK_CACHING = "azureOsDiskCaching";
    public static final String AZURE_OSDISK_BLOB_URI = "azureOsDiskBlobUri";

    public static final String AZURE_DISK_CACHING = "azureDiskCaching";
    public static final String AZURE_DATA_DISK_CACHING = "azureDataDiskCaching";
    public static final String AZURE_DISK_BLOB_URI = "azureDiskBlobUri";
    public static final String AZURE_DISK_LUN = "azureDiskLun";
    public static final String AZURE_MANAGED_DISK_TYPE = "azureManagedDiskType";

    public static final String AZURE_STORAGE_ACCOUNT_TYPE = "azureStorageAccountType";
    public static final String AZURE_STORAGE_ACCOUNT_NAME = "azureStorageAccountName";
    public static final String AZURE_STORAGE_ACCOUNT_RG_NAME = "azureSAResourceGroupName";
    public static final String AZURE_STORAGE_ACCOUNT_DEFAULT_RG_NAME = "default-rg"; // in case none is specified
    public static final String AZURE_DIAGNOSTIC_STORAGE_ACCOUNT_LINK = "azureDiagnosticStorageAccountLink";
    public static final String AZURE_STORAGE_ACCOUNT_KEY = "azureStorageAccountKey";
    public static final String AZURE_STORAGE_ACCOUNT_KEY1 = "azureStorageAccountKey1";
    public static final String AZURE_STORAGE_ACCOUNT_KEY2 = "azureStorageAccountKey2";
    public static final String AZURE_STORAGE_ACCOUNT_URI = "storageAccountUri";
    public static final String AZURE_STORAGE_TYPE = "storageType";
    public static final String AZURE_PROVISIONING_PERMISSION = "provisioningPermission";

    // Azure EA
    public static final String AZURE_ENROLLMENT_NUMBER_KEY = "enrollmentNumber";
    public static final String AZURE_SUBSCRIPTION_ID_KEY = "subscriptionId";
    public static final String AZURE_ACCOUNT_OWNER_EMAIL_ID = "accountOwnerEmailId";
    public static final String AZURE_ACCOUNT_OWNER_NAME = "accountOwnerName";
    public static final String AZURE_EA_BASE_URI = "https://ea.azure.com";
    public static final String AZURE_EA_USAGE_REPORTS_URI = AzureUtils.getAzureEaBaseUri()
            + "/rest/{enrollmentNumber}/usage-reports";
    //Bearer <apiKey>
    public static final String AZURE_EA_AUTHORIZATION_HEADER_FORMAT = "Bearer %s";

    public static final String COMPUTE_NAME_SEPARATOR = "-";

    // Azure Namespace
    public static final String COMPUTE_NAMESPACE = "Microsoft.Compute";
    public static final String STORAGE_NAMESPACE = "Microsoft.Storage";
    public static final String NETWORK_NAMESPACE = "Microsoft.Network";
    public static final String AUTHORIZATION_NAMESPACE = "Microsoft.Authorization";
    public static final String AZURE_STORAGE_ACCOUNTS = STORAGE_NAMESPACE + "/storageAccounts";
    public static final String AZURE_STORAGE_CONTAINERS = STORAGE_NAMESPACE + "/containers";
    public static final String AZURE_STORAGE_DISKS = STORAGE_NAMESPACE + "/disks";
    public static final String AZURE_STORAGE_BLOBS = STORAGE_NAMESPACE + "/blobs";

    // Azure error code
    // https://docs.microsoft.com/en-us/azure/azure-resource-manager/resource-manager-common-deployment-errors
    public static final String MISSING_SUBSCRIPTION_CODE = "MissingSubscriptionRegistration";
    public static final String RESOURCE_NOT_FOUND = "ResourceNotFound";
    public static final String RESOURCE_GROUP_NOT_FOUND = "ResourceGroupNotFound";
    public static final String NOT_FOUND = "NotFound";
    public static final String INVALID_PARAMETER = "InvalidParameter";
    public static final String INVALID_RESOURCE_GROUP = "InvalidResourceGroup";
    public static final String STORAGE_ACCOUNT_ALREADY_EXIST = "StorageAccountAlreadyExists";

    public static final String COMPUTER_NAME = "computerName";

    // Azure constants
    public static final String AZURE_VM_POWER_STATE_RUNNING = "PowerState/running";
    public static final String AZURE_VM_POWER_STATE_DEALLOCATED = "PowerState/deallocated";
    public static final String AZURE_VM_POWER_STATE_STOPPED = "PowerState/stopped";
    public static final String AZURE_VM_PROVISIONING_STATE_SUCCEEDED = "ProvisioningState/succeeded";
    public static final String PROVISIONING_STATE_SUCCEEDED = "Succeeded";
    public static final String PROVISIONING_STATE_FAILED_NO_SUBNET = "NoSubnetFound";
    public static final String PROVIDER_REGISTRED_STATE = "REGISTERED";
    public static final String AZURE_URN_VERSION_LATEST = "latest";
    public static final String ORDER_BY_VM_IMAGE_RESOURCE_NAME_DESC = "name desc";
    public static final String DEFAULT_ADMIN_USER = "azureuser";
    public static final String DEFAULT_ADMIN_PASSWORD = "Pa$$word1";

    // Azure Generic Security Group constants
    public static final int AZURE_SECURITY_GROUP_PRIORITY = 1000;
    public static final String AZURE_SECURITY_GROUP_ACCESS = "Allow";
    public static final String AZURE_SECURITY_GROUP_SOURCE_ADDRESS_PREFIX = "*";
    public static final String AZURE_SECURITY_GROUP_SOURCE_PORT_RANGE = "*";
    public static final String AZURE_SECURITY_GROUP_DESTINATION_ADDRESS_PREFIX = "*";

    // Azure Linux Security Group constants
    public static final String AZURE_LINUX_SECURITY_GROUP_NAME = "default-allow-all";
    public static final String AZURE_LINUX_SECURITY_GROUP_DESTINATION_PORT_RANGE = "*";

    // Monitoring Constants
    public static final String DIAGNOSTIC_SETTINGS_JSON_FILE_NAME = "diagnosticSettings.json";
    public static final String DIAGNOSTIC_SETTING_AGENT = "agent";
    public static final String DIAGNOSTIC_SETTING_API_VERSION = "2014-04-01";
    public static final String DIAGNOSTIC_SETTING_ENDPOINT = "diagnosticSettings";

    // Request Headers
    public static final String AUTH_HEADER_BEARER_PREFIX = "Bearer ";

    // Stats Constants
    public static final String QUERY_PARAM_API_VERSION = "api-version";
    public static final String QUERY_PARAM_FILTER = "$filter";
    public static final String METRIC_DEFINITIONS_ENDPOINT = "metricDefinitions";
    public static final String METRIC_DEFINITIONS_MEMORY_FILTER = "name.value eq '\\Memory\\AvailableMemory'";
    public static final String METRIC_TIME_GRAIN_1_HOUR = "PT1H";
    public static final int METRIC_COLLECTION_PERIOD = 60;
    public static final String METRIC_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";
    public static final String METRIC_KEY_LAST = "Last";
    public static final String METRIC_KEY_MAXIMUM = "Maximum";
    public static final String METRIC_KEY_MINIMUM = "Minimum";
    public static final String METRIC_KEY_COUNTER_NAME = "CounterName";
    public static final String METRIC_KEY_TIMESTAMP = "TIMESTAMP";
    public static final String METRIC_KEY_TOTAL = "Total";
    public static final String METRIC_KEY_AVERAGE = "Average";
    public static final String METRIC_KEY_COUNT = "Count";

    // Azure Metric related Constants
    public static final String NETWORK_BYTES_IN = "\\NetworkInterface\\BytesReceived";
    public static final String NETWORK_BYTES_OUT = "\\NetworkInterface\\BytesTransmitted";
    public static final String DISK_WRITES_PER_SECOND = "\\PhysicalDisk\\WritesPerSecond";
    public static final String DISK_READS_PER_SECOND = "\\PhysicalDisk\\ReadsPerSecond";
    public static final String CPU_UTILIZATION = "\\Processor\\PercentProcessorTime";
    public static final String MEMORY_AVAILABLE = "\\Memory\\AvailableMemory";
    public static final String MEMORY_USED = "\\Memory\\UsedMemory";
    public static final String PERCENT_MEMORY_AVAILABLE = "\\Memory\\PercentAvailableMemory";
    public static final String PERCENT_MEMORY_USED = "\\Memory\\PercentUsedMemory";
    public static final String DISK_READ_BYTES_PER_SECOND = "\\PhysicalDisk\\ReadBytesPerSecond";
    public static final String DISK_WRITE_BYTES_PER_SECOND = "\\PhysicalDisk\\WriteBytesPerSecond";

    // Storage credentials related constants
    public static final String STORAGE_CONNECTION_STRING = "DefaultEndpointsProtocol=https;" +
                    "AccountName=%s;" +
                    "AccountKey=%s;";

    //Storage accounts REST constants
    public static final String STORAGE_ACCOUNT_REST_API_VERSION = "2016-01-01";
    public static final String LIST_STORAGE_ACCOUNTS = AzureUtils.getAzureBaseUri()
            + "subscriptions/{subscriptionId}/providers/Microsoft.Storage/storageAccounts";

    public static final String UNIT_COST = "USD";
    public static final String UNIT_BYTES = "Bytes";
    public static final String UNIT_COUNT = "Count";
    public static final String UNIT_PERCENT = "Percent";

    // Azure Disk Properties
    public static final long DEFAULT_DISK_CAPACITY = 10000L;
    public static final DiskService.DiskType DEFAULT_DISK_TYPE = DiskService.DiskType.HDD;
    public static final String DISK_CONTROLLER_NUMBER = "__logicalUnitNumber";
    public static final String DISK_STATUS_UNATTACHED = "Unattached";

    // Azure container properties
    public static final String AZURE_STORAGE_CONTAINER_LEASE_STATE = "state";
    public static final String AZURE_STORAGE_CONTAINER_LEASE_STATUS = "status";
    public static final String AZURE_STORAGE_CONTAINER_LEASE_LAST_MODIFIED = "LastModified";

    // Virtual Network REST constants
    public static final String NETWORK_REST_API_VERSION = "2015-06-15";
    public static final String LIST_VIRTUAL_NETWORKS_URI = AzureUtils.getAzureBaseUri()
            + "subscriptions/{subscriptionId}/providers/Microsoft.Network/virtualNetworks";

    // Managed Disk REST constants
    public static final String DISK_REST_API_VERSION = "2016-04-30-preview";
    public static final String LIST_DISKS_URI = AzureUtils.getAzureBaseUri()
            + "subscriptions/{subscriptionId}/providers/Microsoft.Compute/disks";

    // Network Security Groups
    public static final String LIST_NETWORK_SECURITY_GROUP_URI = AzureUtils.getAzureBaseUri()
            + "subscriptions/{subscriptionId}/providers/Microsoft.Network/networkSecurityGroups";

    // Resource Group REST constants
    public static final String RESOURCE_GROUP_REST_API_VERSION = "2015-11-01";
    public static final String LIST_RESOURCE_GROUPS_URI = AzureUtils.getAzureBaseUri()
            + "subscriptions/{subscriptionId}/resourcegroups";

    // Azure network properties
    public static final String DEFAULT_INSTANCE_ADAPTER_REFERENCE = "defaultInstanceAdapterReference";

    // Provider REST constants
    public static final String PROVIDER_REST_API_VERSION = "2016-07-01";
    public static final String PROVIDER_URI = AzureUtils.getAzureBaseUri()
            + "subscriptions/{subscriptionId}/providers/{resourceProviderNamespace}";
    public static final String PROVIDER_PERMISSIONS_URI = PROVIDER_URI + "/permissions";

    public static final String PROPERTY_NAME_AZURE_REST_API_RETRY = UriPaths.PROPERTY_PREFIX
            + AzureConstants.class.getSimpleName() + ".";
    // maximum duration over all retry attempts - default to 10 min.
    public static final int AZURE_REST_API_RETRY_MAX_DURATION = Integer.getInteger
            (PROPERTY_NAME_AZURE_REST_API_RETRY + "REST_API_MAX_DURATION",10);
    // delay between each try attempt - default to 250 ms.
    public static final int AZURE_REST_API_RETRY_DELAY = Integer.getInteger
            (PROPERTY_NAME_AZURE_REST_API_RETRY + "REST_API_RETRY_DELAY",500);

    public static final String STATUS_SUBNET_NOT_VALID = "not valid";
    /**
     * The required name for any gateway subnet.
     *
     * From the docs (https://docs.microsoft.com/en-us/azure/vpn-gateway/vpn-gateway-vpn-faq):
     * "All gateway subnets must be named 'GatewaySubnet' to work properly. Don't name your
     * gateway subnet something else. And don't deploy VMs or anything else to the gateway subnet.
     */
    public static final String GATEWAY_SUBNET_NAME = "GatewaySubnet";

    /**
     * Describes the type of a specific
     * {@link com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState}.
     *
     * {@see com.vmware.photon.controller.model.ComputeProperties#RESOURCE_TYPE_KEY}
     */
    public enum ResourceGroupStateType {
        AzureResourceGroup, AzureStorageContainer
    }

    // Resource Limit Constants
    public static final String PROPERTY_NAME_QUERY_RESULT_LIMIT = UriPaths.PROPERTY_PREFIX
            + AzureConstants.class.getSimpleName() + ".QUERY_RESULT_LIMIT";
    private static int QUERY_RESULT_LIMIT = Integer.getInteger(PROPERTY_NAME_QUERY_RESULT_LIMIT,
            100);

    public static void setQueryResultLimit(int resultLimit) {
        QUERY_RESULT_LIMIT = resultLimit;
    }

    public static int getQueryResultLimit() {
        return QUERY_RESULT_LIMIT;
    }

    public static enum AzureResourceType {
        azure_vm("azure_vm"),
        azure_vhd("azure_vhd"),
        azure_managed_disk("azure_managed_disk"),
        azure_blob("azure_blob"),
        azure_vnet("azure_vnet"),
        azure_subnet("azure_subnet"),
        azure_net_interface("azure_net_interface");

        private final String value;

        private AzureResourceType(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return this.value;
        }
    }
}
