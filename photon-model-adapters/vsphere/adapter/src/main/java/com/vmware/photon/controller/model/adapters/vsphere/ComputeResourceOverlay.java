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

package com.vmware.photon.controller.model.adapters.vsphere;

import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimPath;
import com.vmware.vim25.ObjectContent;

public class ComputeResourceOverlay extends AbstractOverlay {

    protected ComputeResourceOverlay(ObjectContent cont) {
        super(cont);
        String type = getId().getType();
        if (!type.equals(VimNames.TYPE_COMPUTE_RESOURCE) &&
                !type.equals(VimNames.TYPE_CLUSTER_COMPUTE_RESOURCE)) {
            String msg = String.format("Cannot overlay type '%s' on top of %s", type, VimUtils
                    .convertMoRefToString(getId()));
            throw new IllegalArgumentException(msg);
        }
    }

    public String getName() {
        return (String) getOrFail(VimNames.PROPERTY_NAME);
    }

    public int getTotalCpuCores() {
        return (short) getOrFail(VimPath.res_summary_numCpuCores);
    }

    public long getEffectiveMemoryBytes() {
        return (long) getOrFail(VimPath.res_summary_effectiveMemory) * MB_to_bytes;
    }

    public int getTotalCpuMhz() {
        return (int) getOrFail(VimPath.res_summary_totalCpu);
    }
}
