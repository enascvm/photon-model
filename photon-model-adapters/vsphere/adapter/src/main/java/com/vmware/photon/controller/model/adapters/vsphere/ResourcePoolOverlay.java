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
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.ObjectUpdate;

public class ResourcePoolOverlay extends AbstractOverlay {

    public ResourcePoolOverlay(ObjectUpdate cont) {
        super(cont);
        ensureType(VimNames.TYPE_RESOURCE_POOL);
    }

    public String getName() {
        return (String) getOrFail(VimNames.PROPERTY_NAME);
    }

    public long getMemoryLimitBytes() {
        long value = (long) getOrDefault(VimPath.rp_summary_config_memoryAllocation_limit, 0L);
        if (value < 0) {
            return 0;
        }
        return value;
    }

    public ManagedObjectReference getParent() {
        return (ManagedObjectReference) getOrFail(VimNames.PROPERTY_PARENT);
    }

    public ManagedObjectReference getOwner() {
        return (ManagedObjectReference) getOrFail(VimNames.PROPERTY_OWNER);
    }

    public long getMemoryReservationBytes() {
        long value = (long) getOrDefault(VimPath.rp_runtime_memory_maxUsage, 0L);
        if (value < 0) {
            return 0;
        }
        return value;
    }

    public String makeUserFriendlyName(String context) {
        if (context != null) {
            return context + " / " + getName();
        }
        return getName();
    }

    public String getNameOrNull() {
        return (String) getOrDefault(VimNames.PROPERTY_NAME, null);
    }
}
