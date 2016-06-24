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
 * The metadata key/value pairs assigned to this instance.
 * This includes custom metadata and predefined keys.
 * For more information, please see the following url:
 * https://cloud.google.com/compute/docs/reference/beta/instances#resource-representations
 */
public class GCPMetadata {
    // Constant: "compute#metadata".
    public String kind;
    public byte[] fingerprint;
    // Array of key/value pairs. The total size of all
    // keys and values must be less than 512 KB.
    public List<GCPItem> items;
}
