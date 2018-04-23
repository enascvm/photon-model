/*
 * Copyright (c) 2018-2019 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.adapters.util.enums;

import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.xenon.common.LocalizableValidationException;

public class SpecificComputeHandler {

    private static final String OVA_REGEX = "^(https?|file)://\\S+(\\.ova|\\.ovf)$";
    private static final String AWS_IMAGE_REGEX = "^ami-\\S+";
    private static final String AZURE_PUBLIC_IMAGE_REGEX = "^\\S+:\\S+:\\S+:\\S+$";
    private static final String AZURE_PRIVATE_IMAGE_REGEX = "^/subscriptions/\\S+/resourceGroups/\\S+" +
            "/providers/Microsoft.Compute/images/\\S+$";
    private static final String LIBRARY_ITEM_REGEX = "^.+/.+$";
    private static final String CUSTOM_IMAGE_TYPE = "__customImageType";
    private static final String COMPUTE_COMPONENT_TYPE_ID = "__component_type_id";

    public enum ResourceType {
        COMPUTE_TYPE("COMPUTE", "Compute", "Cloud.Machine"),
        COMPUTE_VSPHERE_TYPE("COMPUTE.VSPHERE", "Compute.vSphere", "Cloud.vSphere.Machine"),
        COMPUTE_AWS_TYPE("COMPUTE.AWS", "Compute.AWS", "Cloud.AWS.EC2.Instance"),
        COMPUTE_AZURE_TYPE("COMPUTE.AZURE", "Compute.Azure", "Cloud.Azure.Machine"),
        COMPUTE_NETWORK_TYPE("COMPUTE_NETWORK", "Compute.Network", "Cloud.Network"),
        COMPUTE_STORAGE_TYPE("COMPUTE_STORAGE", "Compute.Storage", "Cloud.Volume"),
        COMPUTE_STORAGE_VSPHERE_TYPE("COMPUTE_STORAGE.VSPHERE", "Compute.Storage.vSphere",
                "Cloud.vSphere.Disk"),
        COMPUTE_STORAGE_AWS_TYPE("COMPUTE_STORAGE.AWS", "Compute.Storage.AWS", "Cloud.AWS.Volume"),
        COMPUTE_STORAGE_AZURE_TYPE("COMPUTE_STORAGE.AZURE", "Compute.Storage.Azure", "Cloud.Azure.Disk");

        private final String name;
        private final String contentType;
        private final String displayName;

        private ResourceType(String name, String contentType) {
            this.name = name;
            this.contentType = contentType;
            this.displayName = contentType;
        }

        private ResourceType(String name, String contentType, String displayName) {
            this.name = name;
            this.contentType = contentType;
            this.displayName = displayName;
        }

        public String getName() {
            return this.name;
        }

        public String getContentType() {
            return this.contentType;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public static ResourceType fromName(String name) {
            if (name == null || "".equals(name)) {
                throw new LocalizableValidationException("Name cannot be null or empty!",
                        "common.resource-type.name.empty");
            }
            for (ResourceType r : ResourceType.values()) {
                if (r.name.equals(name)) {
                    return r;
                }
            }
            throw new LocalizableValidationException("No matching type for:" + name,
                    "common.resource-type.name.mismatch", name);
        }

        public static ResourceType fromDisplayName(String displayName) {
            if (displayName == null || "".equals(displayName)) {
                throw new LocalizableValidationException("Display Name cannot be null or empty!",
                        "common.resource-type.name.empty");
            }
            for (ResourceType r : ResourceType.values()) {
                if (r.displayName.equals(displayName)) {
                    return r;
                }
            }
            throw new LocalizableValidationException("No matching type for:" + displayName,
                    "common.resource-type.name.mismatch", displayName);
        }

        public static ResourceType fromContentType(String contentType) {
            if (contentType == null || "".equals(contentType)) {
                throw new LocalizableValidationException("ContentType cannot be null or empty!",
                        "common.resource-type.content-type.empty");
            }
            for (ResourceType r : ResourceType.values()) {
                if (r.contentType.equals(contentType)) {
                    return r;
                }
            }
            throw new LocalizableValidationException("No matching type for:" + contentType,
                    "common.resource-type.content-type.mismatch", contentType);
        }

        public static String getAllTypesAsString() {
            return Arrays.stream(ResourceType.values()).map(Object::toString)
                    .collect(Collectors.joining(", "));
        }
    }

    public enum ImageType {
        TEMPLATE("Template"),
        SNAPSHOT("Snapshot"),
        OVA("ova"),
        AWS("aws"),
        AZURE("azure"),
        LIBRARY_ITEM("LibraryItem");

        public final String value;

        private ImageType(String value) {
            this.value = value;
        }

        public String getValue() {
            return this.value;
        }

        public static ImageType getImageType(String imageType) {
            return Arrays.stream(ImageType.values())
                    .filter(p -> p.value.equals(imageType))
                    .findFirst()
                    .orElse(null);
        }
    }

    public enum CloudSpecificResourceType {
        COMPUTE_VSPHERE_TYPE(ResourceType.COMPUTE_VSPHERE_TYPE, ResourceType.COMPUTE_TYPE,
                PhotonModelConstants.EndpointType.vsphere),
        COMPUTE_AWS_TYPE(ResourceType.COMPUTE_AWS_TYPE, ResourceType.COMPUTE_TYPE,
                PhotonModelConstants.EndpointType.aws),
        COMPUTE_AZURE_TYPE(ResourceType.COMPUTE_AZURE_TYPE, ResourceType.COMPUTE_TYPE,
                PhotonModelConstants.EndpointType.azure),
        COMPUTE_STORAGE_VSPHERE_TYPE(ResourceType.COMPUTE_STORAGE_VSPHERE_TYPE, ResourceType.COMPUTE_STORAGE_TYPE,
                PhotonModelConstants.EndpointType.vsphere),
        COMPUTE_STORAGE_AWS_TYPE(ResourceType.COMPUTE_STORAGE_AWS_TYPE, ResourceType.COMPUTE_STORAGE_TYPE,
                PhotonModelConstants.EndpointType.aws),
        COMPUTE_STORAGE_AZURE_TYPE(ResourceType.COMPUTE_STORAGE_AZURE_TYPE, ResourceType.COMPUTE_STORAGE_TYPE,
                PhotonModelConstants.EndpointType.azure);

        private final ResourceType resourceType;
        private final ResourceType agnosticResourceType;
        private final PhotonModelConstants.EndpointType endpointType;
        private final String componentType;

        CloudSpecificResourceType(ResourceType resourceType, ResourceType agnosticResourceType,
                                  PhotonModelConstants.EndpointType endpointType) {
            this.resourceType = resourceType;
            this.agnosticResourceType = agnosticResourceType;
            this.endpointType = endpointType;
            this.componentType = resourceType.getContentType();
        }

        public PhotonModelConstants.EndpointType getEndpointType() {
            return this.endpointType;
        }

        public ResourceType getAgnosticResourceType() {
            return this.agnosticResourceType;
        }

        public static CloudSpecificResourceType fromComponentType(String componentType) {
            if (componentType == null || "".equals(componentType)) {
                throw new LocalizableValidationException("ComponentType cannot be null or empty!",
                        "common.resource-type.content-type.empty");
            }
            for (CloudSpecificResourceType r : CloudSpecificResourceType.values()) {
                if (r.componentType.equals(componentType)) {
                    return r;
                }
            }
            throw new LocalizableValidationException("No matching type for:" + componentType,
                    "common.resource-type.content-type.mismatch", componentType);
        }

        public String getComponentType() {
            return this.componentType;
        }
    }

    public static String getEndpointTypeAndUpdateComputeDescription(String componentTypeId,
                                                                     String imageRef,
                                                                     ComputeDescription cd) {
        // For templates and snapshots imageType will be null and should be figured runtime
        String componentType = null;
        String imageType = null;


        if (null != imageRef) {
            imageRef = imageRef.trim();
            if (imageRef.matches(OVA_REGEX)) {
                // OVF or ova files are from vsphere
                componentType = (null != componentTypeId) ? componentTypeId :
                        ResourceType.COMPUTE_VSPHERE_TYPE.getContentType();
                imageType = ImageType.OVA.getValue();
            } else if (imageRef.matches(AWS_IMAGE_REGEX)) {
                // All AWS AMIs
                componentType = (null != componentTypeId) ? componentTypeId :
                        ResourceType.COMPUTE_AWS_TYPE.getContentType();
                imageType = ImageType.AWS.getValue();
            } else if (imageRef.matches(AZURE_PUBLIC_IMAGE_REGEX)) {
                // Azure public images
                componentType = (null != componentTypeId) ? componentTypeId :
                        ResourceType.COMPUTE_AZURE_TYPE.getContentType();
                imageType = ImageType.AZURE.getValue();
            } else if (imageRef.matches(AZURE_PRIVATE_IMAGE_REGEX)) {
                // Azure Private Images
                componentType = (null != componentTypeId) ? componentTypeId :
                        ResourceType.COMPUTE_AZURE_TYPE.getContentType();
                imageType = ImageType.AZURE.getValue();
            } else if (imageRef.matches(LIBRARY_ITEM_REGEX)) {
                // vSphere content libraries. These should be checked at last
                // as the regex may match the azure private images
                componentType = (null != componentTypeId) ? componentTypeId :
                        ResourceType.COMPUTE_VSPHERE_TYPE.getContentType();
                imageType = ImageType.LIBRARY_ITEM.getValue();
            }
        }

        if (null != cd) {
            if (null == cd.customProperties) {
                cd.customProperties = new HashMap<>();
            }

            if (null != imageType) {
                cd.customProperties
                        .put(CUSTOM_IMAGE_TYPE, imageType);
            }

            if (null != componentType) {
                cd.customProperties
                        .put(COMPUTE_COMPONENT_TYPE_ID, componentType);
            }
        }

        return null == componentType ? null : CloudSpecificResourceType
                .fromComponentType(componentType).getEndpointType().toString().toLowerCase();
    }
}