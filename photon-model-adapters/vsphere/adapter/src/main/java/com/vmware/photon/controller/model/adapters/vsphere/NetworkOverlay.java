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
import com.vmware.vim25.NetworkSummary;
import com.vmware.vim25.ObjectContent;

public class NetworkOverlay extends AbstractOverlay {
    public NetworkOverlay(ObjectContent cont) {
        super(cont);
        String type = getId().getType();
        if (!type.equals(VimNames.TYPE_NETWORK) &&
                !type.equals(VimNames.TYPE_OPAQUE_NETWORK) &&
                !type.equals(VimNames.TYPE_PORTGROUP)) {
            String msg = String.format("Cannot overlay type '%s' on top of %s", type, VimUtils
                    .convertMoRefToString(getId()));
            throw new IllegalArgumentException(msg);
        }
    }

    public NetworkSummary getSummary() {
        return (NetworkSummary) getOrDefault(VimPath.net_summary, null);
    }

    public String getName() {
        return (String) getOrFail(VimNames.PROPERTY_NAME);
    }
}
