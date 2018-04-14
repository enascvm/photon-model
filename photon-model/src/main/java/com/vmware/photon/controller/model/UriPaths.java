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

package com.vmware.photon.controller.model;

import com.vmware.xenon.common.UriUtils;

/**
 * Service paths used by the photon model.
 */
public class UriPaths {

    public static final String PROPERTY_PREFIX = "photon-model.";

    public static final String IAAS_API_ENABLED_PROPERTY_NAME = "iaas.api.enabled";

    public static final String DEFAULT_BASE_URI_PREFIX_PROPERTY_NAME = UriPaths.PROPERTY_PREFIX
            + "default.uri.prefix";

    public static final String RESOURCES_BASE_URI_PREFIX_PROPERTY_NAME = UriPaths.PROPERTY_PREFIX
            + "resources.uri.prefix";
    public static final String PROVISIONING_BASE_URI_PREFIX_PROPERTY_NAME = UriPaths.PROPERTY_PREFIX
            + "provisioning.uri.prefix";
    public static final String CONFIG_BASE_URI_PREFIX_PROPERTY_NAME = UriPaths.PROPERTY_PREFIX
            + "config.uri.prefix";
    public static final String ADAPTER_BASE_URI_PREFIX_PROPERTY_NAME = UriPaths.PROPERTY_PREFIX
            + "adapter.uri.prefix";
    public static final String TASKS_BASE_URI_PREFIX_PROPERTY_NAME = UriPaths.PROPERTY_PREFIX
            + "tasks.uri.prefix";
    public static final String SCHEDULES_BASE_URI_PREFIX_PROPERTY_NAME = UriPaths.PROPERTY_PREFIX
            + "schedules.uri.prefix";
    public static final String MONITORING_BASE_URI_PREFIX_PROPERTY_NAME = UriPaths.PROPERTY_PREFIX
            + "monitoring.uri.prefix";
    public static final String SESSION_SERVICE_BASE_URI_PREFIX_PROPERTY_NAME = UriPaths.PROPERTY_PREFIX
            + "session-service.uri.prefix";

    public static final Boolean IAAS_API_ENABLED = Boolean.getBoolean(IAAS_API_ENABLED_PROPERTY_NAME);

    private static final String DEFAULT_URI_PREFIX = UriUtils.normalizeUriPath(System.getProperty(
            DEFAULT_BASE_URI_PREFIX_PROPERTY_NAME, ""));
    public static final String RESOURCES_BASE_URI_PREFIX = UriUtils.normalizeUriPath(System.getProperty(
            RESOURCES_BASE_URI_PREFIX_PROPERTY_NAME, DEFAULT_URI_PREFIX));
    public static final String PROVISIONING_BASE_URI_PREFIX = UriUtils.normalizeUriPath(System.getProperty(
            PROVISIONING_BASE_URI_PREFIX_PROPERTY_NAME, DEFAULT_URI_PREFIX));
    public static final String CONFIG_BASE_URI_PREFIX = UriUtils.normalizeUriPath(System.getProperty(
            CONFIG_BASE_URI_PREFIX_PROPERTY_NAME, DEFAULT_URI_PREFIX));
    public static final String ADAPTER_BASE_URI_PREFIX = UriUtils.normalizeUriPath(System.getProperty(
            ADAPTER_BASE_URI_PREFIX_PROPERTY_NAME, DEFAULT_URI_PREFIX));
    public static final String TASKS_BASE_URI_PREFIX = UriUtils.normalizeUriPath(System.getProperty(
            TASKS_BASE_URI_PREFIX_PROPERTY_NAME, DEFAULT_URI_PREFIX));
    public static final String SCHEDULES_BASE_URI_PREFIX = UriUtils.normalizeUriPath(System.getProperty(
            SCHEDULES_BASE_URI_PREFIX_PROPERTY_NAME, DEFAULT_URI_PREFIX));
    public static final String MONITORING_BASE_URI_PREFIX = UriUtils.normalizeUriPath(System.getProperty(
            MONITORING_BASE_URI_PREFIX_PROPERTY_NAME, DEFAULT_URI_PREFIX));
    public static final String SESSION_SERVICE_BASE_URI_PREFIX = UriUtils.normalizeUriPath(System
            .getProperty(SESSION_SERVICE_BASE_URI_PREFIX_PROPERTY_NAME, DEFAULT_URI_PREFIX));

    public static final String RESOURCES = RESOURCES_BASE_URI_PREFIX + "/resources";

    public static final String PROVISIONING = PROVISIONING_BASE_URI_PREFIX + "/provisioning";
    public static final String CONFIG = CONFIG_BASE_URI_PREFIX + "/config";
    public static final String ADAPTER = ADAPTER_BASE_URI_PREFIX + "/adapter";
    public static final String TASKS = TASKS_BASE_URI_PREFIX + "/tasks";
    public static final String SCHEDULES = SCHEDULES_BASE_URI_PREFIX + "/schedules";
    public static final String MONITORING = MONITORING_BASE_URI_PREFIX + "/monitoring";
    public static final String SESSION_SERVICE = SESSION_SERVICE_BASE_URI_PREFIX + "/sessions";

    public static final String RESOURCES_NETWORKS = RESOURCES + "/networks";
    public static final String RESOURCES_NETWORK_INTERFACES = RESOURCES + "/network-interfaces";
    public static final String RESOURCES_NETWORK_INTERFACE_DESCRIPTIONS = RESOURCES + "/network-interfaces-descriptions";
    public static final String RESOURCES_SUBNETS = RESOURCES + "/sub-networks";
    public static final String RESOURCES_SECURITY_GROUPS = RESOURCES + "/security-groups";
    public static final String RESOURCES_LOAD_BALANCERS = RESOURCES + "/load-balancers";
    public static final String RESOURCES_LOAD_BALANCER_DESCRIPTIONS = RESOURCES + "/load-balancer-descriptions";

    public static final String RESOURCES_IMAGES = RESOURCES + "/images";

    public static final String RESOURCES_ROUTERS = RESOURCES + "/routers";

    /**
     * Cloud Account API paths
     */
    public static final String API_PREFIX = "/api";
    public static final String QUERY_PREFIX = "/query";

    public static final String RESOURCE_LIST_API_SERVICE = API_PREFIX + "/resources";
    public static final String CLOUD_ACCOUNT_API_SERVICE = API_PREFIX + "/cloud-accounts";

    public static final String RESOURCE_QUERY_TASK_SERVICE_V3 = QUERY_PREFIX
            + "/resource-query-tasks-v3";
    public static final String RESOURCE_QUERY_TASK_SERVICE_V4 = QUERY_PREFIX
            + "/resource-query-tasks-v4";
    public static final String CLOUD_ACCOUNT_QUERY_TASK_SERVICE = QUERY_PREFIX
            + "/cloud-accounts-tasks";
    public static final String CLOUD_ACCOUNT_QUERY_PAGE_SERVICE = QUERY_PREFIX
            + "/cloud-accounts-page";
    public static final String AWS_BULK_IMPORT_TASK_SERVICE = QUERY_PREFIX +
            "/bulk-import-task/aws";
    public static final String CUSTOM_QUERY_PAGE_FORWARDING_SERVICE = QUERY_PREFIX
            + "/page-forwarding";

    public static final String MAINTENANCE_PREFIX = "/mgmt/maintenance";
    public static final String CLOUD_ACCOUNT_MAINTENANCE_SERVICE = MAINTENANCE_PREFIX
            + "/cloud-accounts";

    public static final String CLOUD_METRICS_SERVICE = QUERY_PREFIX
            + "/cloud-metrics";
    public static final String CLOUD_METRICS_QUERY_PAGE_SERVICE = QUERY_PREFIX
            + "/cloud-metrics-query-page";
    public static final String CLOUD_METRICS_QUERY_TASK_SERVICE = QUERY_PREFIX
            + "/cloud-metrics-query-tasks";

    public static final String CLOUD_USAGE_SERVICE = QUERY_PREFIX
            + "/cloud-usage-reports";
    public static final String CLOUD_USAGE_PAGE_SERVICE = QUERY_PREFIX
            + "/cloud-usage-page";
    public static final String CLOUD_USAGE_TASK_SERVICE = QUERY_PREFIX
            + "/cloud-usage-report-tasks";

    public static final String AUTHZ_PREFIX = "/authz";
    public static final String AUTH_CONTEXT_SERVICE = AUTHZ_PREFIX + "/context-service";

    public static final String TENANTS_PREFIX = "/tenants";

    public static final String ORGANIZATION_SERVICE = TENANTS_PREFIX + "/organization";

    public static final String PROJECT_SERVICE = TENANTS_PREFIX + "/project";

    public static final String USER_SERVICE = TENANTS_PREFIX + "/user";

    public static final String PROVISIONING_USER_SERVICE = "/mgmt/users";

    public static final String USER_CONTEXT_QUERY_SERVICE = PROVISIONING_USER_SERVICE
            + "/query-user-context";

    public static final String MGMT_PREFIX = "/mgmt";

    public static final String ENDPOINT_CREATION_TASK_SERVICE = MGMT_PREFIX
            + "/endpoints/creation-tasks";

    public static final String ENDPOINT_DELETION_TASK_SERVICE = MGMT_PREFIX
            + "/endpoints/deletion-tasks";

    public static final String ENDPOINT_UPDATE_TASK_SERVICE = MGMT_PREFIX
            + "/endpoints/update-tasks";

    public static final String ENDPOINT_VALIDATION_TASK_SERVICE = MGMT_PREFIX
            + "/endpoints/validation-tasks";

    public static final String AWS_COST_USAGE_REPORT_TASK_SERVICE = MGMT_PREFIX
            + "/endpoints/cost-usage-tasks";
    public static final String AWS_ENDPOINT_S3_VALIDATION_TASK_SERVICE = MGMT_PREFIX
            + "/endpoints/s3-validation-tasks";

    public static final String GROUPS_API_SERVICE = API_PREFIX + "/resource-groups";

    public static final String GROUP_QUERY_TASK_SERVICE = QUERY_PREFIX + "/resource-groups-tasks";
    public static final String GROUP_QUERY_PAGE_SERVICE = QUERY_PREFIX + "/resource-groups-page";

    public static final String RESOURCE_PROPERTIES_SERVICE = QUERY_PREFIX + "/resource-properties";
    public static final String RESOURCE_PROPERTIES_SERVICE_V2 = QUERY_PREFIX + "/resource-properties-v2";

    public static final String RESOURCE_SUMMARY_API_SERVICE = QUERY_PREFIX + "/resources-summary";
    public static final String RESOURCE_SUMMARY_API_SERVICE_V2 = QUERY_PREFIX + "/resources-summary-v2";
    public static final String RESOURCE_QUERY_PAGE_SERVICE_V3 = QUERY_PREFIX + "/resources-page-v3";
    public static final String RESOURCE_QUERY_PAGE_SERVICE_V4 = QUERY_PREFIX + "/resources-page-v4";

    public static final String DATA_INIT_TASK_SERVICE = MGMT_PREFIX + "/data-init-tasks";

    public static final String OPTIONAL_ADAPTER_SCHEDULER = "/optional-adapter-scheduler";

    public static final String VSPHERE_RDC_SYNC_TASK_PATH = MGMT_PREFIX + "/vsphere-rdc-sync-tasks";

    public static final String VSPHERE_ENDPOINT_ADAPTER_PATH = "/provisioning/vsphere-on-prem/endpoint-config-adapter";

    public static final String SERVICE_QUERY_CONFIG_RULES = MGMT_PREFIX + QUERY_PREFIX +
            "/config/rules";

    public static final String SERVICE_CONFIG_RULES = "/mgmt/config/rules";

    /**
     * This is the CCS Service URI to validate VCenter Account
     */
    public static final String CCS_VALIDATE_SERVICE = "/vrbc/cmd/sync-exec";

    /**
     * User services.
     */
    public static final String USERS_API_SERVICE = API_PREFIX + "/users";
    public static final String USERS_QUERY_TASK_SERVICE = QUERY_PREFIX + "/user-tasks";

    /**
     * Le-Mans paths
     */
    public static final String LEMANS_STREAM = "/le-mans/v1/streams";
    public static final String NOTIFICATION_MGMT = "/mgmt/notification";
    public static final String NOTIFICATION_LOGGING = "/receivers/logging";
    public static final String NOTIFICATION_SERVICE = "/receivers/notification";
    public static final String NOTIFICATION_PREFIX = "/receivers";

    public enum AdapterTypePath {
        INSTANCE_ADAPTER("instanceAdapter", "instance-adapter"),
        NETWORK_ADAPTER("networkAdapter", "network-adapter"),
        DISK_ADAPTER("diskAdapter", "disk-adapter"),
        DISK_DAY2_ADAPTER("diskDay2Adapter", "disk-day2-adapter"),
        SUBNET_ADAPTER("subnetAdapter", "sub-network-adapter"),
        SECURITY_GROUP_ADAPTER("securityGroupAdapter", "security-group-adapter"),
        LOAD_BALANCER_ADAPTER("loadBalancerAdapter", "load-balancer-adapter"),
        STATS_ADAPTER("statsAdapter", "stats-adapter"),
        COST_STATS_ADAPTER("costStatsAdapter", "cost-stats-adapter"),
        BOOT_ADAPTER("bootAdapter", "boot-adapter"),
        POWER_ADAPTER("powerAdapter", "power-adapter"),
        ENDPOINT_CONFIG_ADAPTER("endpointConfigAdapter", "endpoint-config-adapter"),
        ENUMERATION_ADAPTER("enumerationAdapter", "enumeration-adapter"),
        IMAGE_ENUMERATION_ADAPTER("imageEnumerationAdapter", "image-enumeration-adapter"),
        ENUMERATION_CREATION_ADAPTER("enumerationCreationAdapter", "enumeration-creation-adapter"),
        ENUMERATION_DELETION_ADAPTER("enumerationDeletionAdapter", "enumeration-deletion-adapter"),
        COMPUTE_DESCRIPTION_CREATION_ADAPTER("computeDescriptionCreationAdapter", "compute-description-creation-adapter"),
        COMPUTE_STATE_CREATION_ADAPTER("computeStateCreationAdapter", "compute-state-creation-adapter"),
        STATIC_CONTENT_ADAPTER("staticContent", "static-content"),
        NIC_SECURITY_GROUPS_ADAPTER("nicSecurityGroupsAdapter", "network-interface-security-groups-adapter"),
        REGION_ENUMERATION_ADAPTER("regionEnumerationAdapter", "region-enumeration-adapter");

        /**
         * endpoint type agnostic key used for transport purposes to identify concrete
         * AdapterTypePath's
         */
        public final String key;

        private final String path;

        private AdapterTypePath(String key, String path) {
            this.key = key;
            this.path = path;
        }

        public String adapterLink(String endpointType) {
            return UriUtils.buildUriPath(PROVISIONING, endpointType, this.path);
        }

    }
}
