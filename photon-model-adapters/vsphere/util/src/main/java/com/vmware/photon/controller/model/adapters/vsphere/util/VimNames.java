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

package com.vmware.photon.controller.model.adapters.vsphere.util;

/**
 * Hosts vim-api related strings (types, property names, paths. etc)
 */
public class VimNames {
    public static final String PROPERTY_NAME = "name";
    public static final String PROPERTY_PARENT = "parent";
    public static final String PROPERTY_OWNER = "owner";

    public static final String TYPE_VM = "VirtualMachine";
    public static final String TYPE_HOST = "HostSystem";
    public static final String TYPE_DATACENTER = "Datacenter";
    public static final String TYPE_DATASTORE = "Datastore";
    public static final String TYPE_RESOURCE_POOL = "ResourcePool";
    public static final String TYPE_COMPUTE_RESOURCE = "ComputeResource";
    public static final String TYPE_CLUSTER_COMPUTE_RESOURCE = "ClusterComputeResource";
    public static final String TYPE_VAPP = "VirtualApp";
    public static final String TYPE_FOLDER = "Folder";
    public static final String TYPE_PERFORMANCE_MANAGER = "PerformanceManager";
    public static final String TYPE_NETWORK = "Network";
    public static final String TYPE_PORTGROUP = "DistributedVirtualPortgroup";
    public static final String TYPE_DVS = "VmwareDistributedVirtualSwitch";
    public static final String TYPE_OPAQUE_NETWORK = "OpaqueNetwork";

    public static final String TYPE_SERVER_DISK = "ServerDisk";
}
