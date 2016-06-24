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
 * The model of responses which consist of a list of instances on GCP.
 * For more information, please see the following url:
 * https://cloud.google.com/compute/docs/reference/beta/instances/list
 */
public class GCPInstancesList {
    public String kind;
    public String selfLink;
    public String id;
    public List<GCPInstance> items;
    // The next page token used to fetch
    // the next page of instances.
    public String nextPageToken;
}
