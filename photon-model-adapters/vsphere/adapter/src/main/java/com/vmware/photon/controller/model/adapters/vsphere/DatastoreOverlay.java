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
import com.vmware.vim25.ObjectUpdate;

public class DatastoreOverlay extends AbstractOverlay {
    private boolean multipleHostAccess;

    protected DatastoreOverlay(ObjectUpdate cont) {
        super(cont);
    }

    public String getName() {
        return (String) getOrFail(VimNames.PROPERTY_NAME);
    }

    public String getType() {
        return (String) getOrFail(VimPath.ds_summary_type);
    }

    public long getCapacityBytes() {
        return (long) getOrFail(VimPath.ds_summary_capacity);
    }

    public long getFreeSpaceBytes() {
        return (long) getOrDefault(VimPath.ds_summary_freeSpace, 0L);
    }

    public boolean isMultipleHostAccess() {
        return this.multipleHostAccess;
    }

    public void setMultipleHostAccess(boolean multipleHostAccess) {
        this.multipleHostAccess = multipleHostAccess;
    }

    public String getPath() {
        return (String) getOrDefault(VimPath.ds_summary_url, null);
    }

    public String getNameOrNull() {
        return (String) getOrDefault(VimNames.PROPERTY_NAME, null);
    }

    public long getCapacityBytesOrZero() {
        return (long) getOrDefault(VimPath.ds_summary_capacity, 0L);
    }
}
