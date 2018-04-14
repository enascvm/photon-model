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

import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_AUTH_TYPE;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType.aws;
import static com.vmware.xenon.services.common.QueryTask.QuerySpecification.buildCollectionItemName;
import static com.vmware.xenon.services.common.QueryTask.QuerySpecification.buildCompositeFieldName;

import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountApiService.CloudAccountViewState;
import com.vmware.photon.controller.discovery.endpoints.EndpointUtils;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.TypeName;

/**
 * Defines various properties of cloud accounts
 */
public enum CloudAccountProperty {
    NAME(
            CloudAccountViewState.FIELD_NAME_NAME,
            ResourceState.FIELD_NAME_NAME,
            TypeName.STRING.toString(), true, true, false, true),
    TYPE(
            CloudAccountViewState.FIELD_NAME_TYPE,
            EndpointState.FIELD_NAME_ENDPOINT_TYPE,
            TypeName.STRING.toString(), true, true, false, false),
    CREATED_BY(
            buildCompositeFieldName(
                    CloudAccountViewState.FIELD_NAME_CREATED_BY,
                    UserViewState.FIELD_NAME_EMAIL),
            buildCompositeFieldName(
                    ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                    EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CREATEDBY_EMAIL),
            TypeName.STRING.toString(), true, true, false, true),
    DOCUMENT_SELF_LINK(
            ServiceDocument.FIELD_NAME_SELF_LINK,
            ServiceDocument.FIELD_NAME_SELF_LINK,
            TypeName.STRING.toString(), true, false, false, false),
    ORG_ID(
            buildCompositeFieldName(
                    CloudAccountViewState.FIELD_NAME_ORG,
                    OrganizationViewState.FIELD_NAME_ORG_ID),
            ServiceDocument.FIELD_NAME_SELF_LINK,
            TypeName.STRING.toString(), true, false, false, false),
    ORG_LINK(
            buildCompositeFieldName(
                    CloudAccountViewState.FIELD_NAME_ORG,
                    OrganizationViewState.FIELD_NAME_ORG_LINK),
            buildCompositeFieldName(
                    CloudAccountViewState.FIELD_NAME_ORG,
                    OrganizationViewState.FIELD_NAME_ORG_LINK),
            TypeName.STRING.toString(), true, false, true, false),
    SERVICES(CloudAccountViewState.FIELD_NAME_SERVICES,
            buildCompositeFieldName(
                    ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                    EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_PREFIX_SERVICE_TAG),
            TypeName.STRING.toString(), true, false, true, false),
    AUTH_TYPE(
            buildCompositeFieldName(
                    CloudAccountViewState.FIELD_NAME_CREDENTIALS, aws.name(),
                    ENDPOINT_CUSTOM_PROPERTY_NAME_AUTH_TYPE),
            buildCompositeFieldName(
                    EndpointState.FIELD_NAME_CUSTOM_PROPERTIES, ENDPOINT_CUSTOM_PROPERTY_NAME_AUTH_TYPE),
            TypeName.STRING.toString(), true, false, false, false),
    TAGS(
            CloudAccountViewState.FIELD_NAME_TAGS,
            buildCollectionItemName(ResourceState.FIELD_NAME_TAG_LINKS),
            TypeName.MAP.toString(), true, false, true, false);

    private final String fieldName;
    private final String translatedName;
    private final String type;
    private final boolean isFilterable;
    private final boolean isSortable;
    private final boolean isSpecialHandling;
    private final boolean isCaseInsensitive;

    /**
     * The name of the property in {@link CloudAccountViewState}.
     */
    public String getFieldName() {
        return this.fieldName;
    }

    /**
     * The name of the property in {@link EndpointState} to which {@link #fieldName} translates to.
     */
    public String getTranslatedName() {
        return this.translatedName;
    }

    /**
     * The type of the property.
     */
    public String getType() {
        return this.type;
    }

    /**
     * Flag to determine if the property supports filtering.
     */
    public boolean isFilterable() {
        return this.isFilterable;
    }

    /**
     * Flag to determine if the property supports sorting.
     */
    public boolean isSortable() {
        return this.isSortable;
    }

    /**
     * Flag to determine if the property requires any special handling in {@link CloudAccountQueryTaskService}
     */
    public boolean isSpecialHandling() {
        return this.isSpecialHandling;
    }

    /**
     * Flag to determine if the property requires case insensitive filtering
     */
    public boolean isCaseInsensitive() {
        return this.isCaseInsensitive;
    }

    CloudAccountProperty(String fieldName, String translatedName, String type,
            boolean isFilterable, boolean isSortable, boolean isSpecialHandling, boolean isCaseInsensitive) {
        this.fieldName = fieldName;
        this.translatedName = translatedName;
        this.type = type;
        this.isFilterable = isFilterable;
        this.isSortable = isSortable;
        this.isSpecialHandling = isSpecialHandling;
        this.isCaseInsensitive = isCaseInsensitive;
    }
}
