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

package com.vmware.photon.controller.model.adapters.gcp.podo.vm;

import java.util.List;

/**
 * Represents an instance resource on GCP.
 * For more information, see the following url:
 * https://cloud.google.com/compute/docs/reference/beta/instances
 */
public class GCPInstance {
    // Constant: "compute#instance".
    public String kind;
    public Long id;
    public String creationTimestamp;
    public String zone;
    public String status;
    public String statusMessage;
    public String name;
    public String description;
    // A list of tags to apply to this instance.
    public GCPTags tags;
    public String machineType;
    public Boolean canIpForward;
    // An array of configurations for this interface.
    public List<GCPNetworkInterface> networkInterfaces;
    // Array of disks associated with this instance.
    public List<GCPDisk> disks;
    public GCPMetadata metadata;
    // A list of service accounts, with their specified
    // scopes, authorized for this instance.
    public List<GCPServiceAccount> serviceAccounts;
    public String selfLink;
    public GCPScheduling scheduling;
    public String cpuPlatform;
}
