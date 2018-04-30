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
    public static final String SEPARATOR = "-";
    public static final String CREDENTIALS = "credentials";
    public static final String IS_USER_CREATED = "isUserCreated";

    /**
     * Arguments
     */
    public static final String LEMANS_GATEWAY_HOST = "lemansGatewayHost";
    public static final String AWS_MASTER_ACCOUNT_ID = "awsMasterAccountId";
    public static final String CCS_HOST = "ccsHost";

    /**
     * Service user.
     */
    public static final String SERVICE_USER = "service-user";
    public static final String SERVICE_USER_PREFIX = SERVICE_USER + "-";
    public static final String SERVICE_USER_EMAIL = SERVICE_USER + "@vmware.com";
    public static final String SERVICE_USER_RESOURCE_GROUP_NAME = SERVICE_USER_PREFIX + "resource-group";
    public static final String SERVICE_USER_GROUP_NAME = SERVICE_USER_PREFIX + "group";
    public static final String SERVICE_USER_ROLE_NAME = SERVICE_USER_PREFIX + "role";
    public static final String SERVICE_USER_PASSWORD_PROPERTY =
            UriPaths.SYMPHONY_PROPERTY_PREFIX + "ServiceUserSetupService.userPassword";
    public static final String SERVICE_USER_DEFAULT_PASSWORD = "symphonyservice";

    /**
     * Constants related to stats collection tasks.
     */
    public static final String PROPERTY_NAME_DISABLE_STATS_COLLECTION = UriPaths.SYMPHONY_PROPERTY_PREFIX +
            "disableStatsCollection";
    public static final boolean DISABLE_STATS_COLLECTION = Boolean.getBoolean(PROPERTY_NAME_DISABLE_STATS_COLLECTION);

    /*
     * ScheduledTask related constants
     */
    public static final String ENUMERATION_TASK_SUFFIX = "Enumeration";
    public static final String STATS_COLLECTION_TASK_SUFFIX = "Collection";
    public static final String GROOMER_TASK_SUFFIX = "Groomer";

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
     * automation user.
     */
    public static final String AUTOMATION_USER_EMAIL = "automation-user@vmware.com";
    public static final String AUTOMATION_USER = "automation-user";

    private static final String PREFIX = "automation-user-";
    public static final String AUTOMATION_USER_RESOURCE_GROUP_NAME = PREFIX + "resource-group";
    public static final String AUTOMATION_USER_GROUP_NAME = PREFIX + "group";
    public static final String AUTOMATION_USER_ROLE_NAME = PREFIX + "role";

    public static final String OAUTH_CLIENT_IDS = "clientIds";
    public static final String CLIENT_ID_EMAIL_SUFFIX = "@discovery-client.local";

    /**
     * Delimiters
     */
    public static final String SEMICOLON = ";";
    public static final String QUOTE = "\"";
    public static final String COMMA = ",";
    public static final String EQUALS = "=";

    /*
     * Query result limit and operation batch size related constants.
     */
    public static final String PROPERTY_NAME_QUERY_RESULT_LIMIT = "symphony.queryResultLimit";
    public static final int DEFAULT_QUERY_RESULT_LIMIT = 10000;
    public static final int QUERY_RESULT_LIMIT = Integer.getInteger(PROPERTY_NAME_QUERY_RESULT_LIMIT,
            DEFAULT_QUERY_RESULT_LIMIT);
    public static final String PROPERTY_NAME_OPERATION_BATCH_SIZE = "symphony.operationBatchSize";
    public static final int DEFAULT_OPERATION_BATCH_SIZE = 100;
    public static final int OPERATION_BATCH_SIZE = Integer.getInteger(PROPERTY_NAME_OPERATION_BATCH_SIZE,
            DEFAULT_OPERATION_BATCH_SIZE);

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
            UriPaths.SYMPHONY_PROPERTY_PREFIX + "cloudAccountApiService.awsBulkImport.maxRequestSizeBytes";

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
            UriPaths.SYMPHONY_PROPERTY_PREFIX + "awsBulkImportTask.dataInitializationBatchSize";
    public static final String AWS_BULK_IMPORT_TASK_EXPIRATION_MINUTES =
            UriPaths.SYMPHONY_PROPERTY_PREFIX + "awsBulkImportTask.taskExpirationMinutes";

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
