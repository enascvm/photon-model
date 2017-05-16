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
                    PropertyIndexingOption.SORT
            })
    @Since(ReleaseConstants.RELEASE_VERSION_0_6_12)
    public String desc;

    /**
     * Custom property bag that can be used to store resource specific properties.
     *
     * TODO: Add PropertyIndexingOption.SORT when available. This property cannot be added alongside
     * PropertyIndexingOption.FIXED_ITEM_NAME as it causes an indexing issue. See
     * @see <a href="https://www.pivotaltracker.com/story/show/144880451">https://www.pivotaltracker.com/story/show/144880451</a>
     * for more information.
     */
    @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
    @PropertyOptions(indexing = { PropertyIndexingOption.CASE_INSENSITIVE, PropertyIndexingOption.EXPAND,
            PropertyIndexingOption.FIXED_ITEM_NAME })
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
    public Long creationTimeMicros;

    public void copyTo(ResourceState target) {
        super.copyTo(target);

        target.id = this.id;
        target.name = this.name;
        target.customProperties = this.customProperties;
        target.tenantLinks = this.tenantLinks;
        target.groupLinks = this.groupLinks;
        target.tagLinks = this.tagLinks;
        target.creationTimeMicros = this.creationTimeMicros;
    }

}
