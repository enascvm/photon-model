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

package com.vmware.photon.controller.discovery.cloudaccount;

import com.vmware.photon.controller.discovery.common.ErrorCode;

/**
 * Defines common error codes and associated messages for errors raised by the symphony
 * onboarding services.
 */
public enum CloudAccountErrorCode implements ErrorCode {
    DUPLICATE_PROJECT_NAME(40001, "Project already exists."),
    ORG_NAME_NULL(40002, "'organizationName' is required"),
    ENDPOINT_NAME_REQUIRED(40005, "'name' is required"),
    ENDPOINT_ORG_LINK_REQUIRED(40010, "'orgLink' is required"),
    ENDPOINT_TYPE_REQUIRED(40015, "'endpointType' is required"),
    INVALID_ENDPOINT_TYPE(40016, "'endpointType' is invalid"),
    CREDENTIALS_REQUIRED(40020, "'credentials' are required"),
    INVALID_ORG_LINK(40025, "User does not belong to org %s"),
    ENDPOINT_LINK_REQUIRED(40030, "'endpointLink' is required"),
    ENDPOINT_ID_REQUIRED(40035, "'id' is required in the path"),
    ENDPOINT_NOTHING_TO_UPDATE(40040, "Request doesn't have anything to update on endpoint"),
    ENDPOINT_GET_RETURNED_NON_SINGULAR_RESULT(40045,
            "Return non-singular result when attempting to get endpoint %s"),
    RESOURCE_GROUP_ID_REQUIRED(40050,"'id' is required in the path"),
    RESOURCE_GROUP_GET_RETURNED_NON_SINGULAR_RESULT(40055,
                    "Return non-singular result when attempting to get resource group %s"),
    ENDPOINT_INVALID_UPDATE_ACTION(40060, "Invalid update action on field '%s.%s'"),
    ENDPOINT_ALREADY_EXISTS(40065, "Endpoint already exists"),
    ENDPOINT_WITH_S3BUCKETNAME_ALREADY_EXISTS(40070, "Endpoint with AWS S3 bucket already exists " +
                                                      "for user"),
    INVALID_S3_BUCKETNAME(40075, "Invalid S3 bucket name %s"),
    S3_BUCKET_PERMISSIONS_ERROR(40080, "Access Key does not have permissions on S3 bucket %s"),
    S3_BUCKET_PUT_PERMISSIONS_ERROR(40155, "Access Key does not have PUT permissions on S3 bucket %s"),
    DUPLICATE_COST_USAGE_REPORT_DIFFERENT_PREFIX_ERROR(40160, "The provided Report name %s " +
            "already exists in S3 Bucket %s with a different prefix"),
    S3_COST_USAGE_EXCEPTION(40165, "Unable to create Cost And Usage Report with provided " +
            "S3 bucket %s , Prefix %s and Report name %s"),
    INVALID_S3_BUCKET_PREFIX(40170, "Invalid S3 bucket prefix %s"),
    INVALID_COST_AND_USAGE_REPORT_NAME(40175, "Invalid Cost and Usage report name %s"),
    INVALID_COST_USAGE_PARAMS_ERROR(40180, "S3 Bucket Prefix and Report name are required " +
            "to create Cost and Usage Report!"),
    INVALID_ENDPOINT_OWNER(40085, "Invalid endpoint owners"),
    ENDPOINT_OWNER_ADDITIONAL_FAILED(40090, "Addition of endpoint owner failed"),
    INVALID_USER_FOR_OWNERS_UPDATE(40095, "Invalid user in ownerUpdates"),
    OWNERS_UPDATE_FAILED(40100, "Endpoint owner update failed"),
    VSPHERE_HOSTNAME_REQUIRED(40105, "'Hostname' is required"),
    VSPHERE_DCID_REQUIRED(40110, "'Data Collector id' is required"),
    INVALID_DCID(40111, "Data collector id %s not found"),
    CANNOT_REMOVE_CREATOR_FROM_OWNERS(40115, "Endpoint creator cannot be removed from owners."),
    UNAUTHENTICATED_USER_IMPROPER_ORG_ERROR(40125, "Could not access user's organization"),
    INVALID_CONTENT_REQUEST_TYPE(40130, "Invalid content request type. Expecting %s"),
    INVALID_CSV_FILE_ERROR(40135, "Invalid CSV file for bulk import"),
    ENDPOINT_DOES_NOT_EXIST(40140, "Endpoint '%s' does not exist"),
    BULK_IMPORT_TASK_NOT_FOUND_ERROR(40145, "Bulk Import Task '%s' not found!"),
    INTERNAL_ERROR_OCCURRED(40150, "An internal error occurred."),
    ENDPOINT_TAG_NULL_EMPTY(40155, "Tag key and value cannot be null or empty. Tag: %s - %s"),
    MISSING_FIELD_IN_FORM(40200, "Missing '%s' field in form data"),
    TOO_MANY_FILES_UPLOADED(40205, "Too many files uploaded"),
    FILENAME_MUST_END_WITH_CSV(40210, "Filename must end with '.csv'"),
    MULTIPART_FORM_DATA_PARSING_ERROR(40215, "Error parsing multipart/form-data body: %s"),
    REQUEST_BODY_TOO_LARGE(40220, "Request is too large. Request must not exceed %s bytes, but request was %s bytes.");

    private int errorCode;
    private String message;

    CloudAccountErrorCode(int errorCode, String message) {
        this.errorCode = errorCode;
        this.message = message;
    }

    @Override
    public int getErrorCode() {
        return this.errorCode;
    }

    @Override
    public String getMessage() {
        return this.message;
    }
}
