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

package com.vmware.photon.controller.discovery.common;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.ServiceDocument.Documentation;

public class CloudAccountConstants {

    public static final String GROUP_DESCRIPTION = "description";
    public static final String GROUP_UPDATED_BY = "updatedBy";
    public static final String CREDENTIALS = "credentials";
    public static final String IS_USER_CREATED = "isUserCreated";

    /**
     * Arguments
     */
    public static final String LEMANS_GATEWAY_HOST = "lemansGatewayHost";
    public static final String AWS_MASTER_ACCOUNT_ID = "awsMasterAccountId";
    public static final String CCS_HOST = "ccsHost";

    /**
     * Constants related to stats collection tasks.
     */
    public static final String PROPERTY_NAME_DISABLE_STATS_COLLECTION = UriPaths.PROPERTY_PREFIX +
            "disableStatsCollection";
    public static final boolean DISABLE_STATS_COLLECTION = Boolean.getBoolean(PROPERTY_NAME_DISABLE_STATS_COLLECTION);

    /**
     * CSP Constants
     */
    public static final String CSP_DISCOVERY_USER_ROLE_NAME = "discovery:user";
    public static final String CSP_ORG_ID = "orgID";
    public static final String CSP_URI = "cspUri";
    public static final String SERVICE_AUTH_SECRET = "serviceAuthSecret";
    public static final String CSP_DISCOVERY_INSTANCE_NAME = "discovery";
    public static final String CSP_AUTHENTICATION_SCHEME_NAME = "#CSP#";

    /**
     * Service user.
     */
    public static final String SERVICE_USER = "service-user";
    public static final String SERVICE_USER_PREFIX = SERVICE_USER + "-";
    public static final String SERVICE_USER_EMAIL = SERVICE_USER + "@vmware.com";

    /**
     * autoCloudMetricsQueryPageService user.
     */
    public static final String AUTOMATION_USER_EMAIL = "automation-user@vmware.com";
    public static final String AUTOMATION_USER = "automation-user";

    public static final String OAUTH_CLIENT_IDS = "clientIds";
    public static final String CLIENT_ID_EMAIL_SUFFIX = "@discovery-client.local";

    /**
     * Delimiters
     */
    public static final String SEMICOLON = ";";
    public static final String QUOTE = "\"";
    public static final String COMMA = ",";
    public static final String EQUALS = "=";

    /**
     * Types of Update Actions
     */
    public enum UpdateAction {
        @Documentation(description = "Add Operation.")
        ADD,

        @Documentation(description = "Remove Operation.")
        REMOVE
    }

    /**
     * AWS Bulk Import Task Constants and Properties
     */
    private static final String CLOUD_ACCOUNT_AWS_BULK_IMPORT_DATA_MAX_REQUEST_SIZE_BYTES_PROPERTY =
            UriPaths.PROPERTY_PREFIX + "cloudAccountApiService.awsBulkImport.maxRequestSizeBytes";

    // Set the default to 1 MB.
    private static final Long CLOUD_ACCOUNT_AWS_BULK_IMPORT_DATA_DEFAULT_MAX_REQUEST_SIZE_BYTES =
            (long) (1024 * 1024);
    public static final Long CLOUD_ACCOUNT_AWS_BULK_IMPORT_DATA_MAX_REQUEST_SIZE_BYTES =
            Long.getLong(CLOUD_ACCOUNT_AWS_BULK_IMPORT_DATA_MAX_REQUEST_SIZE_BYTES_PROPERTY,
                    CLOUD_ACCOUNT_AWS_BULK_IMPORT_DATA_DEFAULT_MAX_REQUEST_SIZE_BYTES) > 1 ?
                    Long.getLong(CLOUD_ACCOUNT_AWS_BULK_IMPORT_DATA_MAX_REQUEST_SIZE_BYTES_PROPERTY,
                            CLOUD_ACCOUNT_AWS_BULK_IMPORT_DATA_DEFAULT_MAX_REQUEST_SIZE_BYTES) :
                    CLOUD_ACCOUNT_AWS_BULK_IMPORT_DATA_DEFAULT_MAX_REQUEST_SIZE_BYTES;

    // AWS Bulk Import Task system properties
    public static final String AWS_BULK_IMPORT_TASK_DATA_INITIALIZATION_BATCH_SIZE_PROP =
            UriPaths.PROPERTY_PREFIX + "awsBulkImportTask.dataInitializationBatchSize";
    public static final String AWS_BULK_IMPORT_TASK_EXPIRATION_MINUTES =
            UriPaths.PROPERTY_PREFIX + "awsBulkImportTask.taskExpirationMinutes";

    /**
     * Constants related to service document fields and associated actions.
     */
    public static final String PROPERTY_NAME_CUSTOM_PROP_UPDATE = "customPropertyUpdates";
    public static final String PROPERTY_NAME_PROP_UPDATE = "propertyUpdates";
    public static final String PROPERTY_NAME_CREDENTIAL_UPDATE = "credentials";
    public static final String PROPERTY_NAME_TAG_UPDATES = "tagUpdates";

    /**
     * Cloud Account Tag Constants
     */
    public static final String CLOUD_ACCOUNT_SERVICE_TAG_DISCOVERY = "discovery";
    public static final String CLOUD_ACCOUNT_SERVICE_TAG_COSTINSIGHT = "cost_insight";

    /**
     * Content types
     */
    public static final String CONTENT_TYPE_MULTIPART_FORM_DATA = "multipart/form-data";
    public static final String CONTENT_TYPE_TEXT_CSV = "text/csv";

    /**
     * File type extensions
     */
    public static final String CSV_FILE_EXTENSION = ".csv";

    /**
     * Constant used for passing paging query parameter.
     */
    public static final String PAGE_TOKEN = "pageToken";

}
