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
 * Service accounts generate access tokens that can be accessed
 * through the metadata server and used to authenticate applications
 * on the instance. See the following link for more information:
 * https://cloud.google.com/compute/docs/access/service-accounts
 */
public class GCPServiceAccount {
    public String email;
    public List<String> scopes;
}
