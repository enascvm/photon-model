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

package com.vmware.photon.controller.discovery.endpoints;

import static com.vmware.photon.controller.discovery.endpoints.Credentials.AwsCredential.AuthType.ARN;
import static com.vmware.photon.controller.discovery.endpoints.Credentials.AwsCredential.AuthType.KEYS;
import static com.vmware.photon.controller.discovery.endpoints.EndpointConstants.DISABLE_ENDPOINT_ADAPTER_URI;
import static com.vmware.photon.controller.discovery.endpoints.EndpointConstants.RESOURCES_ENDPOINTS_AUTHZ_ARTIFACT_OWNERS_PREFIX;
import static com.vmware.photon.controller.discovery.vsphere.VsphereOnPremEndpointAdapterService.DC_ID_KEY;
import static com.vmware.photon.controller.discovery.vsphere.VsphereOnPremEndpointAdapterService.DC_NAME_KEY;
import static com.vmware.photon.controller.discovery.vsphere.VsphereOnPremEndpointAdapterService.HOST_NAME_KEY;
import static com.vmware.photon.controller.discovery.vsphere.VsphereOnPremEndpointAdapterService.PRIVATE_CLOUD_NAME_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.ARN_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.EXTERNAL_ID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.USER_LINK_KEY;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_BILLS_S3_BUCKET_NAME_KEY;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_TENANT_ID;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType.aws;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType.azure;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType.azure_ea;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType.vsphere;
import static com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService.EP_CP_ENUMERATION_TASK_STATE;
import static com.vmware.xenon.common.TaskState.TaskStage.CANCELLED;
import static com.vmware.xenon.common.TaskState.TaskStage.FAILED;
import static com.vmware.xenon.common.TaskState.TaskStage.FINISHED;
import static com.vmware.xenon.common.TaskState.TaskStage.STARTED;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.amazonaws.regions.Regions;

import com.vmware.photon.controller.discovery.common.CloudAccountConstants;
import com.vmware.photon.controller.discovery.common.CloudAccountConstants.UpdateAction;
import com.vmware.photon.controller.discovery.common.CollectionStringFieldUpdate;
import com.vmware.photon.controller.discovery.common.ErrorCode;
import com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils;
import com.vmware.photon.controller.discovery.common.services.UserService.UserState;
import com.vmware.photon.controller.discovery.common.utils.ErrorUtil;
import com.vmware.photon.controller.discovery.endpoints.Credentials.AwsCredential;
import com.vmware.photon.controller.discovery.endpoints.DataInitializationTaskService.DataInitializationState;
import com.vmware.photon.controller.discovery.endpoints.EndpointUpdateTaskService.EndpointUpdateTaskState;
import com.vmware.photon.controller.discovery.endpoints.OptionalAdapterSchedulingService.OptionalAdapterSchedulingRequest;
import com.vmware.photon.controller.discovery.onboarding.OnboardingUtils;
import com.vmware.photon.controller.discovery.vsphere.VsphereOnPremEndpointAdapterService;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.xenon.common.AuthUtils;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskService.TaskServiceState;
import com.vmware.xenon.services.common.UserGroupService;

/**
 * Helper utilities for endpoint management
 */
public class EndpointUtils {

    private static final String DOT = ".";
    public static final String VSPHERE_ON_PREM_ADAPTER = "vsphere-on-prem";
    public static final int MASK_ALL_BUT_LAST_N_DIGITS = 4;
    public static final String SYNC_JOB_WAITING_STATUS = "WAITING";

    /**
     * Endpoint Custom Properties
     */
    public static final String ENDPOINT_CUSTOM_PROPERTY_NAME_CREATEDBY_EMAIL = "createdByEmail";
    public static final String ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET = "cost_insight:s3bucketName";
    public static final String ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BUCKET_PREFIX = "cost_insight:s3bucketPrefix";
    public static final String ENDPOINT_CUSTOM_PROPERTY_NAME_CI_COSTUSAGE_REPORT_NAME = "cost_insight:costAndUsageReport";
    public static final String ENDPOINT_CUSTOM_PROPERTY_NAME_DISCOVERY_MAINT_COMPLETE = "discovery:maintenanceComplete";
    public static final String ENDPOINT_CUSTOM_PROPERTY_NAME_LEGACY_ID = "legacyId";
    public static final String ENDPOINT_CUSTOM_PROPERTY_NAME_STATUS = "status";
    public static final String ENDPOINT_CUSTOM_PROPERTY_NAME_AUTH_TYPE = "authType";
    public static final String ENDPOINT_CUSTOM_PROPERTY_NAME_SYNC_JOB_STATUS = "syncJobStatus";

    public static final String ENDPOINT_COST_USAGE_SCHEMA_ELEMENTS = "RESOURCES";
    public static final String ENDPOINT_COST_USAGE_TIME_UNIT = "HOURLY";
    public static final String ENDPOINT_COST_USAGE_FORMAT = "textORcsv";
    public static final String ENDPOINT_COST_USAGE_COMPRESSION = "GZIP";
    public static final Regions ENDPOINT_COST_USAGE_REPORT_SERVICE_REGION = Regions.US_EAST_1;

    public static final String ENDPOINT_CUSTOM_PROPERTY_NAME_PREFIX_SERVICE_TAG = "service_tag:";
    public static final String ENDPOINT_CUSTOM_PROPERTY_VALUE_SERVICE_TAG = "enabled";

    public static final String ENDPOINT_PROPERTY_VSPHERE_USERNAME = CloudAccountConstants.CREDENTIALS + DOT + Credentials.FIELD_NAME_VSPHERE_CREDENTIAL
            + DOT + Credentials.VsphereCredential.FIELD_NAME_USER_NAME;
    public static final String ENDPOINT_PROPERTY_VSPHERE_PASSWORD = CloudAccountConstants.CREDENTIALS + DOT + Credentials.FIELD_NAME_VSPHERE_CREDENTIAL
            + DOT + Credentials.VsphereCredential.FIELD_NAME_PASSWORD;

    /* For building stale endpoint document deletion task selfLink */
    public static final String SEPARATOR = "-";
    public static final String STALE_ENDPOINT_DOCUMENT_DELETION_TASK_POSTFIX = "document-deletion";

    // Constant for empty string
    public static final String EMPTY_STRING = "";

    // Map values from endpointProperties to customProperties, used in the GET and POST operation.
    public static final Map<String, String> PROPERTIES_COPY_MAP =
            Collections.unmodifiableMap(new HashMap<String, String>() {
                {
                    put(AWS_BILLS_S3_BUCKET_NAME_KEY, ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET);
                    put(DC_ID_KEY, DC_ID_KEY);
                    put(DC_NAME_KEY, DC_NAME_KEY);
                    put(HOST_NAME_KEY, HOST_NAME_KEY);
                    put(PRIVATE_CLOUD_NAME_KEY, PRIVATE_CLOUD_NAME_KEY);
                }
            });

    public enum SyncJobStage {

        /**
         * Status when endpoint is created but not kick off enumeration
         */
        WAITING("0"),

        /**
         * Status when enumeration is kicked off
         */
        RUNNING("1"),

        /**
         * Status when enumeration reach the FAILED stage, specially when a critical problem happens
         */
        FAILED("2"),

        /**
         * Status when there is an noncritical problem happens in enumeration process. Not in use
         */
        WARNING("3"),

        /**
         * Status when enumeration succeed
         */
        DONE("4");

        String statusCode;

        public String getStatusCode() {
            return this.statusCode;
        }

        SyncJobStage(String status) {
            this.statusCode = status;
        }
    }

    // Map values from 'enumerationTaskState' to 'syncJobStatus' for updating cloud account
    // dynamic sync status.
    public static final Map<String, String> MAP_ENUMERATION_STATE_TO_SYNC_JOB_STATUS =
            Collections.unmodifiableMap(new HashMap<String, String>() {
                {
                    put(STARTED.name(), SyncJobStage.RUNNING.statusCode);
                    put(FINISHED.name(), SyncJobStage.DONE.statusCode);
                    put(CANCELLED.name(), SyncJobStage.FAILED.statusCode);
                    put(FAILED.name(), SyncJobStage.FAILED.statusCode);
                    put(SYNC_JOB_WAITING_STATUS, SyncJobStage.WAITING.statusCode);
                }
            });

    /**
     * Helper for {@link EndpointUtils.buildEndpointAuthzArtifactSelfLink}.
     */
    public static String buildEndpointAuthzArtifactSelfLink(String factoryLink, String endpointLink,
            String orgOrProjectLink) {
        return buildEndpointAuthzArtifactSelfLink(factoryLink, endpointLink, Collections.singletonList(orgOrProjectLink));
    }

    /**
     * Helper for {@link EndpointUtils.buildEndpointAuthzArtifactSelfLink}.
     */
    public static String buildEndpointAuthzArtifactSelfLink(String factoryLink, String endpointLink,
            Set<String> tenantLinks) {
        return buildEndpointAuthzArtifactSelfLink(factoryLink, endpointLink, new ArrayList<String>(tenantLinks));
    }

    /**
     * Build a well known authz artifact self link. The method to build self link for user-group,
     * resource-group and role using orgLink and userLink.
     * <p>
     * Format: /factoryLink/resources-endpoints-owners-<endpointId>
     */
    public static String buildEndpointAuthzArtifactSelfLink(String factoryLink, String endpointLink,
            List<String> tenantLinks) {
        String endpointId = getEndpointUniqueId(endpointLink);
        String authzArtifactId = PhotonControllerCloudAccountUtils.getOrgId(tenantLinks) + SEPARATOR
                + RESOURCES_ENDPOINTS_AUTHZ_ARTIFACT_OWNERS_PREFIX + SEPARATOR + endpointId;
        String selfLink = UriUtils.buildUriPath(factoryLink, authzArtifactId);
        return selfLink;
    }

    /**
     * For endpoints created with new cloud accounts API, return UUID (last sub path) of the endpointLink.
     * EndpointLinks are in the format: /resources/endpoints/<orgId>-<projectId>-<userId>-<endpointId>.
     * For endpoints created with the old API whose endpointLinks are in the format: /resources/endpoints/<endpointId>
     * return the last path (endpointId).
     */
    public static String getEndpointUniqueId(String endpointLink) {
        String lastPathSegment = UriUtils.getLastPathSegment(endpointLink);
        String[] subPaths = lastPathSegment.split(SEPARATOR);
        return subPaths.length == 4 ? subPaths[3] : lastPathSegment;
    }

    /**
     * Helper method to fail operation as a result of an {@link ErrorCode}.
     */
    public static void failOperation(ServiceHost host, Operation op, ErrorCode errorCode,
            int statusCode) {
        ServiceErrorResponse e = ErrorUtil.create(errorCode);
        e.statusCode = statusCode;
        host.log(Level.WARNING, e.message);
        op.fail(statusCode, new Exception(e.message), e);
    }

    /**
     * Helper method to fail operation as a result of an {@link ErrorCode}.
     */
    public static void failOperation(ServiceHost host, Operation op, ErrorCode errorCode,
            int statusCode, String... args) {
        ServiceErrorResponse e = ErrorUtil.create(errorCode, args);
        e.statusCode = statusCode;
        host.log(Level.WARNING, e.message);
        op.fail(statusCode, new Exception(e.message), e);
    }

    /**
     * Helper to build specific endpoint adapter URI.
     */
    public static URI buildEndpointAdapterUri(ServiceHost host, String endpointType) {
        if (!DISABLE_ENDPOINT_ADAPTER_URI
                && (EndpointType.vsphere.name().equals(endpointType)
                || VSPHERE_ON_PREM_ADAPTER.equals(endpointType))) {
            return UriUtils.buildUri(host, VsphereOnPremEndpointAdapterService.SELF_LINK);
        }
        return null;
    }


    /**
     * Checks if the task operation is failed and appropriately marks the parent op failed.
     */
    public static boolean handleCompletion(Operation parentOp, Operation completedOp, Throwable e) {
        if (e != null) {
            if (completedOp.hasBody()) {
                ServiceErrorResponse err = completedOp.getBody(ServiceErrorResponse.class);
                if (err.message != null) {
                    parentOp.fail(completedOp.getStatusCode(), e, err);
                    return false;
                }
            }
            parentOp.fail(Operation.STATUS_CODE_INTERNAL_ERROR);
            return false;
        }

        TaskServiceState state = completedOp.getBody(ConcreteTaskServiceState.class);
        TaskState taskInfo = state.taskInfo;

        if (taskInfo != null && taskInfo.stage == FAILED) {
            if (taskInfo.failure != null) {
                parentOp.fail(taskInfo.failure.statusCode,
                        new Exception(taskInfo.failure.message), taskInfo.failure);
                return false;
            }
        }
        return true;
    }

    /**
     * If {@code credentialType} should be masked, then all but the last 4 characters in
     * {@code credentialId} are masked.
     *
     * @param credentialType the type of credential
     * @param credentialId   the id of the credential
     * @return a masked representation of {@code credentialId}
     */
    public static String maskCredentialId(String credentialType, String credentialId) {
        if (credentialId == null) {
            return null;
        }

        String masked = null;
        if (VSPHERE_ON_PREM_ADAPTER.equals(credentialType)) {
            masked = credentialId;
        } else {
            int maskNdx = credentialId.length() - MASK_ALL_BUT_LAST_N_DIGITS;
            maskNdx = maskNdx < 0 ? 0 : maskNdx;
            masked = String.format("***%s", credentialId.substring(maskNdx));
        }
        return masked;
    }

    public static String maskPrivateKey() {
        return "*********";
    }

    /**
     * Dummy task state to deal with generic response.
     */
    private static class ConcreteTaskServiceState extends TaskServiceState {
    }

    /**
     * Helper method to determine if an operation's credentials values have been properly set.
     * Credentials may be specified through two objects - the endpointProperties map, or a credentials
     * object. If credentials are not seemingly sufficiently provided, then the operation should
     * fail.
     * <p>
     * Note: This method extends the ability for backwards-compatibility, where formerly the APIs
     * would accept credentials to be passed via the endpointProperties instead of the more-recent
     * and favoured Credentials object.
     *
     * @param endpointProperties A mapping of properties related to a given endpoint.
     * @param credentials        A credentials object storing credentials pertinent to the type of account
     *                           being added.
     * @return true if there is an indication the credentials have been passed through, else false.
     */
    public static boolean isOperationCredentialsSet(Map<String, String> endpointProperties,
            Credentials credentials) {
        return !((endpointProperties == null || endpointProperties.size() == 0) &&
                (credentials == null || credentials.isEmpty()));
    }

    /**
     * Helper method to determine if an operation's customProperties values (HOST_NAME_KEY)
     * has been properly set for vsphere endpoint type.
     *
     * @param customProperties A mapping of properties related to a given endpoint.
     * @return true if there is an indication the hostname has been passed through, else false.
     */
    public static boolean isValidVsphereHostName(Map<String, String> customProperties) {
        return (customProperties != null && !customProperties.isEmpty() &&
                customProperties.get(HOST_NAME_KEY) != null && !customProperties.get
                (HOST_NAME_KEY).isEmpty());
    }

    /**
     * Helper method to determine if an operation's customProperties values (DC_ID_KEY)
     * has been properly set for vsphere endpoint type.
     *
     * @param customProperties A mapping of properties related to a given endpoint.
     * @return true if there is an indication the datacenter has been passed through, else false.
     */
    public static boolean isValidVsphereDcId(Map<String, String> customProperties) {
        return (customProperties != null && !customProperties.isEmpty() &&
                customProperties.get(DC_ID_KEY) != null && !customProperties.get
                (DC_ID_KEY).isEmpty());
    }

    /**
     * Constructs a set of endpointProperties to be joined with the proper mapping of Credential
     * endpointProperties.
     *
     * @param endpointType       Endpoint type of the endpoint instance, e.g. aws, azure, ...
     * @param credentials        A credentials object storing credentials pertinent to the type of account
     *                           being added.
     * @param endpointProperties A mapping of properties related to a given endpoint.
     * @param customProperties   A mapping of properties related to a given endpoint.
     * @return A mapping of endpoint properties suitable for the Photon Model, joining the passed in
     * endpointProperties with newly-constructed properties for the passed in credentials.
     */
    public static Map<String, String> reconstructEndpointProperties(String endpointType,
            Credentials credentials, Map<String, String> endpointProperties, Map<String, String> customProperties) {
        Map<String, String> reconstructedEndpointProperties = new HashMap<>();

        if (endpointProperties != null) {
            reconstructedEndpointProperties.putAll(endpointProperties);
        }

        if (customProperties != null) {
            copyProperties(reconstructedEndpointProperties, customProperties);
        }

        reconstructedEndpointProperties.putAll(
                convertCredentialsToEndpointPropertyEntries(endpointType, credentials));

        return reconstructedEndpointProperties;
    }

    /**
     * Get privateKeyId of the endpoint based on the endpointType
     *
     * @param endpointType Endpoint type of the endpoint instance, e.g. aws, azure, ...
     * @param credentials  A credentials object storing credentials pertinent to the type of account
     *                     being added.
     * @return privateKeyId of credentials.
     */
    public static String getPrivateKeyIdFromCredentials(String endpointType, Credentials
            credentials) {
        // If no credentials are specified then just return null.
        if (endpointType == null || credentials == null || credentials.isEmpty()) {
            return null;
        }

        // Otherwise, get the access key Id based on the endpointType
        if (endpointType.equals(aws.name()) && credentials.aws != null) {
            if (credentials.aws.arn != null) {
                return credentials.aws.arn;
            }
            return credentials.aws.accessKeyId;
        } else if (endpointType.equals(azure.name()) && credentials.azure != null) {
            return credentials.azure.clientId;
        } else if (endpointType.equals(azure_ea.name()) && credentials.azure_ea != null) {
            return credentials.azure_ea.enrollmentNumber;
        } else if (endpointType.equals(vsphere.name()) &&
                credentials.vsphere != null) {
            return credentials.vsphere.username;
        }
        return null;
    }

    /**
     * Method to build AuthState from credentials object for AWS
     *
     * @param credentials
     * @return AuthCredentialsServiceState
     */
    public static AuthCredentialsServiceState getAwsAuthCredentialsServiceState(Credentials credentials) {
        AuthCredentialsService.AuthCredentialsServiceState authCredentialState =
                new AuthCredentialsService.AuthCredentialsServiceState();

        if (credentials.aws.accessKeyId != null) {
            authCredentialState.privateKeyId = credentials.aws.accessKeyId;
            authCredentialState.privateKey = credentials.aws.secretAccessKey;
        } else {
            if (authCredentialState.customProperties == null) {
                authCredentialState.customProperties = new HashMap<String, String>();
            }
            authCredentialState.customProperties.put(ARN_KEY, credentials.aws.arn);
            authCredentialState.customProperties.put(EXTERNAL_ID_KEY, credentials.aws.externalId);
        }
        return authCredentialState;
    }

    /**
     * This method is to copy the fields from customProperties to endpointProperties. The fields
     * needs to be copied are specified in the {@link PROPERTIES_COPY_MAP} .
     *
     * @param targetMap
     * @param originMap
     */
    private static void copyProperties(Map<String, String> targetMap, Map<String, String> originMap) {
        for (Map.Entry<String, String> entry : PROPERTIES_COPY_MAP.entrySet()) {
            if (originMap.get(entry.getValue()) != null) {
                targetMap.put(entry.getKey(), originMap.get(entry.getValue()));
            }
        }
    }

    /**
     * Helper method to determine the AWS auth type.
     *
     * @param awsCredential A set of AWS credentials.
     * @return The auth type string.
     */
    private static String getAwsAuthType(AwsCredential awsCredential) {
        if (awsCredential.arn != null) {
            return ARN.name();
        }

        return KEYS.name();
    }

    /**
     * Converts a set of credentials to the expected key-value entries for endpointProperties that
     * Photon Model expects.
     *
     * @param endpointType Endpoint type of the endpoint instance, e.g. aws, azure, ...
     * @param credentials  A credentials object storing credentials pertinent to the type of account
     *                     being added.
     * @return A mapping of credentials to Photon-model expected endpointProperties relating to
     * account credentials. If no valid credentials are supplied, an empty map is returned.
     */
    private static Map<String, String> convertCredentialsToEndpointPropertyEntries(String endpointType,
            Credentials credentials) {
        Map<String, String> endpointProperties = new HashMap<>();

        // If no credentials are specified then just return the empty map.
        if (endpointType == null || credentials == null || credentials.isEmpty()) {
            return endpointProperties;
        }

        // Otherwise, construct the proper endpoint properties with the explicit mapping of
        // different credential types.
        if (endpointType.equals(aws.name()) && credentials.aws != null) {

            // If the ARN is non-null, then initialize the private key and secret key to empty
            // strings.
            if (credentials.aws.arn != null) {
                credentials.aws.accessKeyId = "";
                credentials.aws.secretAccessKey = "";
            }

            putIfValueNotNull(endpointProperties, PRIVATE_KEYID_KEY, credentials.aws.accessKeyId);
            putIfValueNotNull(endpointProperties, PRIVATE_KEY_KEY, credentials.aws.secretAccessKey);
            putIfValueNotNull(endpointProperties, ARN_KEY, credentials.aws.arn);
            putIfValueNotNull(endpointProperties, EXTERNAL_ID_KEY, credentials.aws.externalId);
            putIfValueNotNull(endpointProperties, ENDPOINT_CUSTOM_PROPERTY_NAME_AUTH_TYPE,
                    getAwsAuthType(credentials.aws));
        } else if (endpointType.equals(azure.name()) && credentials.azure != null) {
            putIfValueNotNull(endpointProperties, PRIVATE_KEYID_KEY, credentials.azure.clientId);
            putIfValueNotNull(endpointProperties, PRIVATE_KEY_KEY, credentials.azure.clientKey);
            putIfValueNotNull(endpointProperties, AZURE_TENANT_ID, credentials.azure.tenantId);
            putIfValueNotNull(endpointProperties, USER_LINK_KEY, credentials.azure.subscriptionId);
        } else if (endpointType.equals(azure_ea.name()) && credentials.azure_ea != null) {
            putIfValueNotNull(endpointProperties, PRIVATE_KEYID_KEY, credentials.azure_ea.enrollmentNumber);
            putIfValueNotNull(endpointProperties, PRIVATE_KEY_KEY, credentials.azure_ea.accessKey);
        } else if (endpointType.equals(EndpointUtils.VSPHERE_ON_PREM_ADAPTER) &&
                credentials.vsphere != null) {
            putIfValueNotNull(endpointProperties, PRIVATE_KEYID_KEY, credentials.vsphere.username);
            putIfValueNotNull(endpointProperties, PRIVATE_KEY_KEY, credentials.vsphere.password);
        }

        return endpointProperties;
    }

    /**
     * Helper method to put a value in a map if that value is non-null.
     *
     * @param map   The map to place the key-value pair in.
     * @param key   The key to be associated with the value in the map.
     * @param value The value associated with the key in the map.
     */
    public static void putIfValueNotNull(Map<String, String> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    /**
     * Check if an endpoint type is supported by cloud accounts API
     *
     * @param endpointType The endpoint to check
     * @return true if endpoint is supported, false otherwise
     */
    public static boolean isSupportedEndpointType(String endpointType) {
        for (EndpointType supportedEndpointType : EndpointType.values()) {
            if (supportedEndpointType.name().equals(endpointType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Construct a Map of custom properties combining existing properties and
     * enhancing with createBy user and service tags
     *
     * @param customProperties Existing custom properties
     * @param createdByUser    The user creating the endpoint
     * @param services         The set of services using the endpoint (service tags)
     * @return The reconstructed map of custom properties
     */
    public static Map<String, String> reconstructCustomProperties(
            Map<String, String> customProperties, String createdByUser, Set<String> services,
            Credentials credentials) {
        Map<String, String> reconstructedCustomProperties = new HashMap<>();

        if (customProperties != null) {
            reconstructedCustomProperties.putAll(customProperties);
        }

        reconstructedCustomProperties.put(ENDPOINT_CUSTOM_PROPERTY_NAME_CREATEDBY_EMAIL, createdByUser);

        if (services != null && !services.isEmpty()) {
            for (String service : services) {
                String key = ENDPOINT_CUSTOM_PROPERTY_NAME_PREFIX_SERVICE_TAG + service.toLowerCase();
                reconstructedCustomProperties.put(key, ENDPOINT_CUSTOM_PROPERTY_VALUE_SERVICE_TAG);
            }
        }

        if (credentials.aws != null) {
            reconstructedCustomProperties
                    .put(ENDPOINT_CUSTOM_PROPERTY_NAME_AUTH_TYPE, getAwsAuthType(credentials.aws));
        }

        return reconstructedCustomProperties;
    }

    /**
     * Given a Map of custom properties, parse and return the service tags if any
     *
     * @param customProperties The custom properties to examine
     * @return A Set of services identified from the custom properties
     */
    public static Set<String> createServiceTagsFromCustomProperties(Map<String, String> customProperties) {
        Set<String> services = null;

        if (customProperties != null) {
            services = customProperties.entrySet()
                    .stream()
                    .filter(entry -> entry.getKey().startsWith(ENDPOINT_CUSTOM_PROPERTY_NAME_PREFIX_SERVICE_TAG))
                    .map(entry -> entry.getKey().split(ENDPOINT_CUSTOM_PROPERTY_NAME_PREFIX_SERVICE_TAG)[1])
                    .collect(Collectors.toSet());
        }

        return services;
    }

    /**
     * Add any extra custom properties and return the custom properties
     *
     * @param endpointState The endpoint to handle
     * @return The custom properties for the endpoint
     */
    public static Map<String, String> addExtraCustomProperties(EndpointState endpointState) {
        if (endpointState.customProperties == null) {
            endpointState.customProperties = new HashMap<>();
        }
        // For a vsphere endpoint, if it was created used the old API, add the endpoint ID as a custom
        // property called "legacyId"
        if (EndpointUtils.VSPHERE_ON_PREM_ADAPTER.equals(endpointState.endpointType)) {
            String orgId = PhotonControllerCloudAccountUtils.getOrgId(endpointState);
            // For old style endpoint, endpointState.documentSelfLink will not have the org
            if (orgId == null) {
                endpointState.customProperties
                        .put(ENDPOINT_CUSTOM_PROPERTY_NAME_LEGACY_ID, endpointState.id);
            }
        }

        endpointState.customProperties.put(ENDPOINT_CUSTOM_PROPERTY_NAME_SYNC_JOB_STATUS,
                MAP_ENUMERATION_STATE_TO_SYNC_JOB_STATUS.get(
                        endpointState.customProperties.getOrDefault(EP_CP_ENUMERATION_TASK_STATE, SYNC_JOB_WAITING_STATUS)));

        return endpointState.customProperties;
    }

    /**
     * Check if the credential on patch request is valid.
     *
     * @param state : EndpointUpdateTaskState
     * @return
     */
    public static String isPatchCredentialBodyValid(EndpointUpdateTaskState state) {
        Credentials credential = state.credentials;
        Map<String, String> endpointProperties = state.endpointState.endpointProperties;

        // not allow enrollmentId to be changed.
        if (credential.azure_ea != null) {
            if (credential.azure_ea.enrollmentNumber != null && !credential.azure_ea.enrollmentNumber
                    .equals(endpointProperties.get(PRIVATE_KEYID_KEY))) {
                return "'enrollmentId' cannot be updated";
            }
            if (credential.azure_ea.accessKey == null || credential.azure_ea.accessKey.isEmpty()) {
                return "'accessKey' is required";
            }
            return null;
        }

        // not allow subscriptionId and tenantId to be changed.
        if (credential.azure != null) {
            if (credential.azure.subscriptionId != null &&
                    !credential.azure.subscriptionId.equals(endpointProperties.get(USER_LINK_KEY))) {
                return "'subscriptionId' cannot be updated";
            }
            if (credential.azure.tenantId != null &&
                    !credential.azure.tenantId.equals(endpointProperties.get(AZURE_TENANT_ID))) {
                return "'tenantId' cannot be updated";
            }
            if (credential.azure.clientId == null || credential.azure.clientId.isEmpty()) {
                return "'clientId' is required";
            }
            if (credential.azure.clientKey == null || credential.azure.clientKey.isEmpty()) {
                return "'clientKey' is required";
            }
            return null;
        }

        if (credential.aws != null) {

            // Check to see if the endpoint state auth type is ARN, and if so, check if an ARN was
            // provided.
            // TODO: See https://jira.eng.vmware.com/browse/VSYM-12657 for ARN -> IAM & vice-versa updating.
            if (state.endpointState.customProperties != null &&
                    state.endpointState.customProperties
                            .containsKey(ENDPOINT_CUSTOM_PROPERTY_NAME_AUTH_TYPE) &&
                    state.endpointState.customProperties
                            .get(ENDPOINT_CUSTOM_PROPERTY_NAME_AUTH_TYPE).equals(ARN.name())) {

                // If ARN credentials are provided, then accept them immediately
                if (credential.aws.arn == null || credential.aws.arn.isEmpty()) {
                    return "'arn' is required";
                }
                return null;
            }

            // Otherwise, check for access key / secret key (default)
            if (credential.aws.accessKeyId == null || credential.aws.accessKeyId.isEmpty()) {
                return "'accessKeyId' is required";
            }
            if (credential.aws.secretAccessKey == null || credential.aws.secretAccessKey.isEmpty()) {
                return "'secretAccessKey' is required";
            }
            return null;
        }

        if (credential.vsphere != null) {
            if (credential.vsphere.username == null || credential.vsphere.username.isEmpty()) {
                return "'username' is required";
            }
            if (credential.vsphere.password == null || credential.vsphere.password.isEmpty()) {
                return "'password' is required";
            }
            return null;
        }
        return "Invalid credentials";
    }

    public static QueryTask buildTagsQuery(Map<String, TagState> tagsToFind,
            Set<String> tenantLinks) {
        // todo calij add result limit
        QuerySpecification querySpec = new QuerySpecification();
        querySpec.options.addAll(Arrays.asList(
                QuerySpecification.QueryOption.INDEXED_METADATA,
                QuerySpecification.QueryOption.EXPAND_CONTENT));
        querySpec.query.addBooleanClause(
                Query.Builder.create()
                        .addKindFieldClause(TagState.class)
                        .build());
        Query tagQuery = Query.Builder.create()
                .addInClause(TagState.FIELD_NAME_SELF_LINK,
                        tagsToFind.keySet(),
                        Query.Occurance.SHOULD_OCCUR)
                .build();
        querySpec.query.addBooleanClause(tagQuery);

        QueryTask task = QueryTask.create(querySpec).setDirect(true);
        task.tenantLinks = new ArrayList<>(tenantLinks);
        return task;
    }

    /**
     * Validates whether a list of given users can be added as owners for an endpoint.
     *
     * @param orgLink    Org selfLink.
     * @param service    Caller service.
     * @param userEmails List of user emails.
     * @param onSuccess  Success handler.
     * @param onError    Failure handler.
     */
    public static void validateUsersForEndpointOwnership(String orgLink,
            Service service, Set<String> userEmails, Consumer<Operation> onSuccess,
            Consumer<Throwable> onError) {

        if (userEmails == null || userEmails.isEmpty()) {
            onError.accept(new IllegalArgumentException("No user emails provided."));
            return;
        }

        String orgId = UriUtils.getLastPathSegment(orgLink);
        String orgAdminUserGroupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                OnboardingUtils.buildAuthzArtifactLink(orgId,
                        true));
        String orgUserUserGroupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                OnboardingUtils.buildAuthzArtifactLink(orgId,
                        false));

        Query query = Query.Builder.create()
                .addKindFieldClause(UserState.class)
                .addInClause(UserState.FIELD_NAME_EMAIL, userEmails)
                .build();
        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .setQuery(query)
                .build();

        Operation.createPost(service, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(queryTask)
                .setReferer(service.getUri())
                .setAuthorizationContext(service.getSystemAuthorizationContext())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        onError.accept(e);
                        return;
                    }

                    QueryTask response = o.getBody(QueryTask.class);
                    if (response.results != null && response.results.documents != null) {
                        for (Entry entry : response.results.documents.entrySet()) {
                            UserState userState = Utils.fromJson(entry.getValue(),
                                    UserState.class);

                            if (userState.userGroupLinks == null
                                    || (!userState.userGroupLinks.contains(orgUserUserGroupLink)
                                    && !userState.userGroupLinks.contains(orgAdminUserGroupLink))) {
                                onError.accept(new IllegalArgumentException("Cloud "
                                        + "Account owner addition failed. "
                                        + "User is not a part of the current"
                                        + " organization."));
                                return;
                            }
                            userEmails.remove(userState.email);
                        }
                    }
                    if (!userEmails.isEmpty()) {
                        onError.accept(new IllegalArgumentException(String.format("Could not "
                                + "find users %s", userEmails)));
                        return;
                    }
                    onSuccess.accept(o);
                }).sendWith(service);
    }

    /**
     * Updates ownership for the given endpoint by patching userGroupLinks for the list of users.
     * NOTE: This method assumes all requested users are valid users to be added/removed as owners
     * from the given endpoint and that the users belong to the same org as the endpoint.
     * User validation should be done before by calling
     * {@link EndpointUtils.validateUsersForEndpointOwnership}.
     *
     * @param endpointLink                Endpoint selfLink.
     * @param service                     Caller service.
     * @param updateActionByUserSelfLinks List of {@link CollectionStringFieldUpdate} (pair of User
     *                                    selfLink and UpdateAction).
     * @param onSuccess                   Success handler.
     * @param onError                     Failure handler.
     */
    public static void updateOwnersForEndpoint(String endpointLink, List<String> tenantLinks, Service service,
            List<CollectionStringFieldUpdate> updateActionByUserSelfLinks,
            Consumer<Void> onSuccess, Consumer<Throwable> onError) {

        if (updateActionByUserSelfLinks == null || updateActionByUserSelfLinks.isEmpty()) {
            onError.accept(new IllegalArgumentException("No users to be added as owners."));
            return;
        }

        String userGroupLink = buildEndpointAuthzArtifactSelfLink(UserGroupService.FACTORY_LINK,
                endpointLink, tenantLinks);

        Map<String, Collection<Object>> itemsToRemoveOrAdd = Collections
                .singletonMap(UserState.FIELD_NAME_USER_GROUP_LINKS,
                        Collections.singleton(userGroupLink));

        ServiceStateCollectionUpdateRequest groupLinksAddRequest =
                ServiceStateCollectionUpdateRequest.create(itemsToRemoveOrAdd, null);

        ServiceStateCollectionUpdateRequest groupLinksRemoveRequest =
                ServiceStateCollectionUpdateRequest.create(null, itemsToRemoveOrAdd);


        List<Operation> patchUserOps = new ArrayList<>();

        updateActionByUserSelfLinks.stream()
                .forEach(collectionStringFieldUpdate -> {
                    if (collectionStringFieldUpdate.action.equals(UpdateAction.ADD)) {
                        patchUserOps.add(Operation.createPatch(AuthUtils
                                .buildAuthProviderHostUri(service.getHost(),
                                        collectionStringFieldUpdate.value))
                                .setBody(groupLinksAddRequest)
                                .setAuthorizationContext(service.getSystemAuthorizationContext()));
                    } else if (collectionStringFieldUpdate.action.equals(UpdateAction.REMOVE)) {
                        patchUserOps.add(Operation.createPatch(AuthUtils
                                .buildAuthProviderHostUri(service.getHost(),
                                        collectionStringFieldUpdate.value))
                                .setBody(groupLinksRemoveRequest)
                                .setAuthorizationContext(service.getSystemAuthorizationContext()));
                    }
                });

        OperationJoin.create(patchUserOps)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        onError.accept(e.get(0));
                        return;
                    }
                    onSuccess.accept(null);
                }).sendWith(service);

    }

    /**
     * Wrapper over
     * {@link #initializeDataInitializationTaskService(Service, EndpointState, Set, TaskState)},
     * setting the task state to `null` (asynchronous by default).
     *
     * @param sender        The service sending the request.
     * @param endpointState The endpoint to initialize the task on.
     * @param tenantLinks   The tenant links of the endpoint owner.
     * @return The {@link DeferredResult<Operation>} of the {@link DataInitializationTaskService}
     * response.
     */
    public static DeferredResult<Operation> initializeDataInitializationTaskService(Service sender,
            EndpointState endpointState, Set<String> tenantLinks) {
        return initializeDataInitializationTaskService(sender, endpointState, tenantLinks, null);
    }

    /**
     * Helper method to initialize the {@link DataInitializationTaskService}.
     *
     * @param sender        The service sending the request.
     * @param endpointState The endpoint to initialize the task on.
     * @param tenantLinks   The tenant links of the endpoint owner.
     * @param taskInfo      Optional flag to allow consumers to decide how they want the task state to
     *                      run (synchronous, asynchronous, etc.). Defaults to asynch (indirect).
     * @return The {@link DeferredResult<Operation>} of the {@link DataInitializationTaskService}
     * response.
     */
    public static DeferredResult<Operation> initializeDataInitializationTaskService(Service sender,
            EndpointState endpointState, Set<String> tenantLinks, TaskState taskInfo) {

        DataInitializationState state = new DataInitializationState();
        state.endpoint = endpointState;
        state.tenantLinks = tenantLinks;

        if (taskInfo != null) {
            state.taskInfo = taskInfo;
        }

        if (endpointState != null && endpointState.documentSelfLink != null) {
            state.documentSelfLink = UriUtils
                    .getLastPathSegment(endpointState.documentSelfLink);
        }

        return sender.sendWithDeferredResult(
                Operation.createPost(sender, DataInitializationTaskService.FACTORY_LINK)
                        .setBody(state)
                        .setReferer(sender.getUri()))
                .whenComplete((op, ex) -> {
                    if (ex != null) {
                        sender.getHost().log(Level.WARNING,
                                "Error while creating data initialization task: %s",
                                ex.getMessage());
                    }
                });
    }

    /**
     * Helper method to initialize the {@link OptionalAdapterSchedulingService}.
     *
     * @param sender        The service sending the request.
     * @param endpointState The endpoint to initialize the request on.
     * @return The {@link DeferredResult<Operation>} of the
     * {@link OptionalAdapterSchedulingService} response.
     */
    public static DeferredResult<Operation> triggerOptionalAdapterSchedulingService(Service sender,
            EndpointState endpointState) {
        OptionalAdapterSchedulingRequest request = new OptionalAdapterSchedulingRequest();
        request.requestType = OptionalAdapterSchedulingService.RequestType.SCHEDULE;
        request.endpoint = endpointState;

        return sender.sendWithDeferredResult(
                Operation.createPatch(sender, OptionalAdapterSchedulingService.SELF_LINK)
                        .setBody(request)
                        .setReferer(sender.getUri()))
                .whenComplete((op, ex) -> {
                    if (ex != null) {
                        sender.getHost().log(Level.WARNING,
                                "Error while scheduling optional adapter: %s",
                                ex.getMessage());
                    }
                });
    }
}