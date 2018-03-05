/*
 * Copyright (c) 2018-2019 VMware, Inc. All Rights Reserved.
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
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.ObjectUpdate;

public class FolderOverlay extends AbstractOverlay {

    FolderOverlay(ObjectUpdate cont) {
        super(cont);
    }

    public String getName() {
        return (String) getOrFail(VimNames.PROPERTY_NAME);
    }

    public ManagedObjectReference getParent() {
        return (ManagedObjectReference) getOrFail(VimNames.PROPERTY_PARENT);
    }

    public String getView() {
        EntityUtils.EntityType type = EntityUtils.getObjectTypeFromManagedObjectId(getMoRefValue());
        return null == type ? null : VcView.getFolderView(type).getName();
    }

    public Integer getFolderType() {
        EntityUtils.EntityType type = EntityUtils.getObjectTypeFromManagedObjectId(getMoRefValue());
        return null == type ? null : FolderType.getFolderType(type).getId();
    }
}