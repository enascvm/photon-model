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
    public static final String RESOURCE_GROOMER_SCHEDULE_DELAY_SECONDS_PROPERTY_NAME = UriPaths.PROPERTY_PREFIX
            + "resource.groomer.schedule.delay.micros";

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
