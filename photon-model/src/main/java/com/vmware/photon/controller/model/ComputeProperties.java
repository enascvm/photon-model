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

package com.vmware.photon.controller.model;

/**
 * Common infrastructure provider properties for compute resources manipulated by adapters.
 */
public class ComputeProperties {

    /**
     * The display name of the compute resource.
     */
    public static final String CUSTOM_DISPLAY_NAME = "displayName";

    /**
     * The resource group name to use to group the resources. E.g. on vSpehere this can be the
     * folder name, on Azure this is the resourceGroupName, on AWS this value can be used to tag the
     * resources.
     */
    public static final String RESOURCE_GROUP_NAME = "resourceGroupName";

    /**
     * Custom property to hold the link to the endpoint.
     */
    public static final String ENDPOINT_LINK_PROP_NAME = "__endpointLink";

    /**
     * The normalized OS type of the compute resource.
     * See {@link OSType} for a list of possible values.
     */
    public static final String CUSTOM_OS_TYPE = "osType";

    /**
     * A link to a compute resource where to deploy this compute.
     */
    public static final String PLACEMENT_LINK = "__placementLink";

    /**
     * A key for the custom properties property which value stores the specific type of the
     * resource state.
     * <p>
     * Useful when one resource state class can represent more than one target system type
     * (e.g. Both Azure Resource Groups and Storage containers are represented by
     * {@link com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState}
     */
    public static final String RESOURCE_TYPE_KEY = "__type";

    /**
     * A key for the custom properties property which value stores the parent compute host link.
     */
    public static final String COMPUTE_HOST_LINK_PROP_NAME = "computeHostLink";

    /**
     * A key for the custom properties property which value stores compute that has created the
     * object.
     */
    public static final String CREATE_CONTEXT_PROP_NAME = "__createContext";

    public static final String FIELD_VIRTUAL_GATEWAY = "__virtualGateway";

    /**
     * A key for the custom properties which indicates if the resource is for infrastructure use
     * only (the value is set to "true" in this case).
     */
    public static final String INFRASTRUCTURE_USE_PROP_NAME = "__infrastructureUse";

    /**
     * A key for a linked endpoint link, used to link two accounts.
     */
    public static final String LINKED_ENDPOINT_PROP_NAME = "linkedEndpointLink";

    /**
     * A key for the custom properties in compute which stores flag as whether a snapshot exists
     * for the given compute.
     */
    public static final String CUSTOM_PROP_COMPUTE_HAS_SNAPSHOTS = "__hasSnapshot";

    /**
     * A key for the custom properties in compute which stores flag as whether the adapter should
     * wait for IP address assignment before reporting success.
     */
    public static final String CUSTOM_PROP_COMPUTE_AWAIT_IP = "awaitIp";

    /**
     * Marks whether storage resource is local to the host or shared storage.
     */
    public static final String CUSTOM_PROP_STORAGE_SHARED = "__isShared";

    public enum OSType {
        WINDOWS, LINUX;
    }
}
