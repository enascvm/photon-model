/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.adapters.vsphere;

import com.vmware.photon.controller.model.adapters.vsphere.util.connection.GetMoRef;
import com.vmware.vim25.ConfigTarget;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.VimPortType;

/**
 * Querying {@link ConfigTarget} from vSphere.
 */
public class QueryConfigTargetRequest {

    private VimPortType vimPortType;
    private ManagedObjectReference computeResourceReference;
    private GetMoRef getMoRefUtil;

    public QueryConfigTargetRequest(GetMoRef getMoRef, VimPortType vimPortType,
            ManagedObjectReference computeResourceReference) {
        this.getMoRefUtil = getMoRef;
        this.vimPortType = vimPortType;
        this.computeResourceReference = computeResourceReference;
    }

    /**
     * Get {@link ConfigTarget} aka information about physical devices that are used to back
     * virtual device.
     */
    public ConfigTarget getConfigTarget() throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        return this.vimPortType.queryConfigTarget(this.getEnvironmentBrowserReference(), null);
    }

    /**
     * Get environment browser aka an environment that a compute resource represents
     */
    private ManagedObjectReference getEnvironmentBrowserReference()
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        return this.getMoRefUtil.entityProp(this
                        .computeResourceReference,
                "environmentBrowser");
    }
}
