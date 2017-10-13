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

package com.vmware.photon.controller.model.resources;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.constants.ReleaseConstants;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;

/**
 * Base PODO for all photon model resource services
 */
public class ResourceState extends ServiceDocument {

    public static final String FIELD_NAME_ID = "id";
    public static final String FIELD_NAME_NAME = "name";
    public static final String FIELD_NAME_DESC = "desc";
    public static final String FIELD_NAME_CUSTOM_PROPERTIES = "customProperties";
    public static final String FIELD_NAME_TENANT_LINKS = "tenantLinks";
    public static final String FIELD_NAME_GROUP_LINKS = "groupLinks";
    public static final String FIELD_NAME_TAG_LINKS = "tagLinks";
    public static final String FIELD_NAME_ENDPOINT_LINK = PhotonModelConstants.FIELD_NAME_ENDPOINT_LINK;
    public static final String FIELD_NAME_ENDPOINT_LINKS = "endpointLinks";
    public static final String FIELD_NAME_REGION_ID = "regionId";
    public static final String FIELD_NAME_CREATION_TIME_MICROS = "creationTimeMicros";
    public static final String FIELD_NAME_COMPUTE_HOST_LINK = "computeHostLink";

    /**
     * Identifier of this resource instance
     */
    @UsageOption(option = PropertyUsageOption.ID)
    @UsageOption(option = PropertyUsageOption.REQUIRED)
    @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
    public String id;

    /**
     * Name of the resource instance
     */
    @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
    @PropertyOptions(indexing = { PropertyIndexingOption.CASE_INSENSITIVE, PropertyIndexingOption.SORT })
    public String name;

    /**
     * Description of the resource instance
     */
    @PropertyOptions(
            usage = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL,
            indexing = {
                    PropertyIndexingOption.CASE_INSENSITIVE,
                    PropertyIndexingOption.SORT,
                    PropertyIndexingOption.TEXT
            })
    @Since(ReleaseConstants.RELEASE_VERSION_0_6_12)
    public String desc;

    /**
     * Custom property bag that can be used to store resource specific properties.
     */
    @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
    @PropertyOptions(indexing = { PropertyIndexingOption.CASE_INSENSITIVE, PropertyIndexingOption.EXPAND,
            PropertyIndexingOption.FIXED_ITEM_NAME, PropertyIndexingOption.SORT })
    public Map<String, String> customProperties;

    /**
     * A list of tenant links that can access this resource.
     */
    @UsageOption(option = PropertyUsageOption.LINKS)
    public List<String> tenantLinks;

    /**
     * Set of groups the resource belongs to
     *
     * @see ResourceGroupService
     */
    @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
    @UsageOption(option = PropertyUsageOption.LINKS)
    public Set<String> groupLinks;

    /**
     * Set of tags set on this resource.
     *
     * @see TagService
     */
    @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
    @UsageOption(option = PropertyUsageOption.LINKS)
    public Set<String> tagLinks;

    /**
     * Resource creation time in micros since epoch.
     */
    @UsageOption(option = PropertyUsageOption.OPTIONAL)
    @Since(ReleaseConstants.RELEASE_VERSION_0_6_17)
    @PropertyOptions(indexing = { PropertyIndexingOption.SORT })
    public Long creationTimeMicros;

    /**
     * Identifier of the region associated with this resource instance.
     */
    @UsageOption(option = PropertyUsageOption.OPTIONAL)
    @Since(ReleaseConstants.RELEASE_VERSION_0_6_17)
    @PropertyOptions(indexing = { PropertyIndexingOption.SORT })
    public String regionId;

    /**
     * Set of endpoint links set on this resource.
     *
     * @see EndpointService
     */
    @UsageOption(option = PropertyUsageOption.OPTIONAL)
    @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
    @UsageOption(option = PropertyUsageOption.LINKS)
    @Since(ReleaseConstants.RELEASE_VERSION_0_6_33)
    public Set<String> endpointLinks;

    /**
     * Reference to compute host instance.
     */
    @UsageOption(option = PropertyUsageOption.OPTIONAL)
    @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
    @Since(ReleaseConstants.RELEASE_VERSION_0_6_42)
    public String computeHostLink;

    public void copyTo(ResourceState target) {
        super.copyTo(target);

        target.id = this.id;
        target.name = this.name;
        target.desc = this.desc;
        target.customProperties = this.customProperties;
        target.tenantLinks = this.tenantLinks;
        target.groupLinks = this.groupLinks;
        target.tagLinks = this.tagLinks;
        target.creationTimeMicros = this.creationTimeMicros;
        target.regionId = this.regionId;
        target.endpointLinks = this.endpointLinks;
        target.computeHostLink = this.computeHostLink;
    }
}
