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
 * The model of network interface of an instance on GCP.
 * It specifies how this interface is configured to interact
 * with other network services, such as connecting to the
 * internet. Only one interface is supported per instance.
 * For more information, please see the following url:
 * https://cloud.google.com/compute/docs/reference/beta/instances#resource-representations
 */
public class GCPNetworkInterface {
    public String network;
    public String subnetwork;
    public String networkIP;
    public String name;
    // An array of configurations for this interface.
    public List<GCPAccessConfig> accessConfigs;
}
