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

package com.vmware.photon.controller.model.adapterapi;


import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;

import java.util.List;
import java.util.Map;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import com.vmware.photon.controller.model.constants.ReleaseConstants;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;

import com.vmware.xenon.common.ServiceDocument.Documentation;
import com.vmware.xenon.common.ServiceDocument.PropertyOptions;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;

/**
 * Request to validate and/or enhance an Endpoint. The {@link ResourceRequest#resourceReference}
 * field is reference to the Endpoint to enhance.
 * <p>
 * If the value of {@link EndpointConfigRequest#requestType} is {@code RequestType#VALIDATE} then
 * value of {@link ResourceRequest#resourceReference} won't be set.
 */
public class EndpointConfigRequest extends ResourceRequest {

    public static final String USER_LINK_KEY = "userLink";
    public static final String USER_EMAIL_KEY = "userEmail";
    public static final String PRIVATE_KEY_KEY = "privateKey";
    public static final String PRIVATE_KEYID_KEY = "privateKeyId";
    public static final String PUBLIC_KEY_KEY = "publicKey";
    public static final String TOKEN_REFERENCE_KEY = "tokenReference";
    public static final String REGION_KEY = "regionId";
    public static final String ZONE_KEY = "zoneId";

    public static final String ARN_KEY = "arn";
    public static final String SESSION_TOKEN_KEY = "sessionToken";
    public static final String EXTERNAL_ID_KEY = "externalId";
    public static final String SESSION_EXPIRATION_TIME_MICROS_KEY = "sessionTokenExpirationMicros";

    /**
     * A key for the property of {@link #endpointProperties} which specifies trusted certificate
     * for the endpoint
     */
    public static final String CERTIFICATE_PROP_NAME = "certificate";

    /**
     * A key for the property of {@link #endpointProperties} which specifies whether to accept or
     * not the certificate (if self-signed) for the endpoint
     */
    public static final String ACCEPT_SELFSIGNED_CERTIFICATE = "acceptSelfSignedCertificate";

    /**
     * Set this property to true if the end-point support public/global images enumeration.
     */
    public static final String SUPPORT_PUBLIC_IMAGES = "supportPublicImages";

    /**
     * Set this property to true if the end-point supports explicit datastores concept.
     */
    public static final String SUPPORT_DATASTORES = "supportDatastores";

    /**
     * Endpoint request type.
     */
    public enum RequestType {
        VALIDATE,
        ENHANCE,
        CHECK_IF_ACCOUNT_EXISTS
    }

    /**
     * Request type.
     */
    public RequestType requestType;

    /**
     * A map of value to use to validate and enhance Endpoint.
     */
    public Map<String, String> endpointProperties;

    /**
     * A list of tenant links which can access this service.
     */
    @PropertyOptions(indexing = { PropertyIndexingOption.EXPAND })
    @Since(ReleaseConstants.RELEASE_VERSION_0_6_12)
    public List<String> tenantLinks;

    /**
     * If specified an endpoint uniqueness check will be performed.
     */
    @Since(ReleaseConstants.RELEASE_VERSION_0_6_45)
    public Boolean checkForEndpointUniqueness;

    @Documentation(description = "A flag that tracks if the cloud provider account has "
            + "already been configured for this endpoint.")
    @PropertyOptions(usage = { SERVICE_USE })
    public boolean accountAlreadyExists;

    @Documentation(description = "The existing compute host state corresponding to the account. "
            + "This will be updated to reflect the association "
            + "with the new endpoint being configured in the system in case they map back to "
            + "the same cloud provider account. ")
    @PropertyOptions(usage = {SERVICE_USE})
    public ComputeState existingComputeState;

    @Documentation(description = "The existing compute description corresponding to the account. "
            + "This will be updated to reflect the association "
            + "with the new endpoint being configured in the system in case they map back to "
            + "the same cloud provider account. ")
    @PropertyOptions(usage = {SERVICE_USE})
    public ComputeDescription existingComputeDescription;
}
