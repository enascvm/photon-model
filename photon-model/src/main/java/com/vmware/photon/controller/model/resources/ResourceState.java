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

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.constants.ReleaseConstants;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;

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
    public static final String FIELD_NAME_EXPANDED_TAGS = "expandedTags";

    /**
     * Contains information about an assigned tag.
     *
     * <p>Only the tag itself is currently included but in the future metadata about the tag
     * and/or this particular assignment may be added (e.g. managed vs. discovered tags, etc.).</p>
     */
    public static class TagInfo {
        public static final String FIELD_NAME_TAG = "tag";
        public static final String COMPOSITE_FIELD_NAME_TAG =
                QuerySpecification.buildCompositeFieldName(
                        QuerySpecification.buildCollectionItemName(FIELD_NAME_EXPANDED_TAGS),
                        FIELD_NAME_TAG);

        public static final String KEY_VALUE_SEPARATOR = "\n";

        /**
         * String representation of the tag built by concatenating its key and value with
         * {@link #KEY_VALUE_SEPARATOR} between them. The key is required while the value is
         * optional; the separator is always included, even if the value is empty.
         *
         * <p>Note: Since {@link #expandedTags} is a collection of PODOs, having the key and value
         * in separate fields in the PODO would not allow matching the key and the value of a single
         * tag, e.g. "expandedTags.key eq location and expandedTags.value eq london" will not
         * guarantee 'location' and 'longon' will be matched in the same tag; a match will be
         * found even if the resource has two tags like 'location:manchester' and 'mgmt:london'.
         * That's why the key and the value are stored in the same field, and search such as
         * "expandedTags.tag eq location\nlondon" will match the correct tag.</p>
         */
        @PropertyOptions(indexing = { PropertyIndexingOption.CASE_INSENSITIVE })
        public String tag;
    }

    /**
     * Deserialize a ResourceState type, using documentKind as a hint to instantiate the resulting
     * object as derived class (i.e. deserialize a type with a field of type ResourceState will
     * instantiate an object of the derived type instead of ResourceState).
     */
    static final class ResourceStateDeserializer implements JsonDeserializer<ResourceState> {
        @Override
        public ResourceState deserialize(JsonElement json, Type typeOfT,
                JsonDeserializationContext context) throws JsonParseException {
            JsonElement documentKind = json.getAsJsonObject().get("documentKind");
            if (documentKind == null) {
                // deserialize as ResourceState if documentKink is not available
                Utils.log(ResourceStateDeserializer.class, "deserialize", Level.WARNING,
                        "Json document of type %s does not have a documentKind field",
                        typeOfT.getTypeName());
                return Utils.fromJson(json, typeOfT);
            }

            Class<?> clazz = Utils.getTypeFromKind(documentKind.getAsString());
            if (clazz != null) {
                return context.deserialize(json, clazz);
            } else {
                // deserialize as ResourceState if type is not registered
                Utils.log(ResourceStateDeserializer.class, "deserialize", Level.WARNING,
                        "Json document of type %s and kind %s does not declare derived deserializer",
                        typeOfT, documentKind.getAsString());
                return Utils.fromJson(json, typeOfT);
            }
        }
    }

    /**
     * Deserialize a ResourceState type as a derived type. The resulting object is an object
     * of the derived class (i.e. ComputeState).
     */
    static final class DerivedResourceStateDeserializer implements JsonDeserializer<ResourceState> {
        @Override
        public ResourceState deserialize(JsonElement json, Type typeOfT,
                JsonDeserializationContext context) throws JsonParseException {
            return Utils.fromJson(json, typeOfT);
        }
    }

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
    @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
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
    @Since(ReleaseConstants.RELEASE_VERSION_0_6_44)
    public String computeHostLink;

    /**
     * Read-only field containing information about the assigned tags. Automatically populated
     * on {@link #tagLinks} change.
     */
    @PropertyOptions(indexing = {
            PropertyIndexingOption.EXPAND,
            PropertyIndexingOption.CASE_INSENSITIVE,
            PropertyIndexingOption.EXCLUDE_FROM_SIGNATURE})
    @Since(ReleaseConstants.RELEASE_VERSION_0_6_49)
    public List<TagInfo> expandedTags;

    /**
     * Document creation time in micros.
     */
    @UsageOption(option = PropertyUsageOption.OPTIONAL)
    @UsageOption(option = PropertyUsageOption.SINGLE_ASSIGNMENT)
    @Since(ReleaseConstants.RELEASE_VERSION_0_6_49)
    public Long documentCreationTimeMicros;

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
        target.documentCreationTimeMicros = this.documentCreationTimeMicros;
    }
}
