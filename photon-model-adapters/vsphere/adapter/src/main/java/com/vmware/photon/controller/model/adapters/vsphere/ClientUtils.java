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

import java.util.ArrayList;
import java.util.List;

import com.vmware.pbm.PbmFaultFaultMsg;
import com.vmware.pbm.PbmPlacementHub;
import com.vmware.pbm.PbmProfileId;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;

/**
 * Utility methods that are common for all the clients like InstanceClient, EnumerationClient etc,
 */
public class ClientUtils {

    /**
     * Retrieves list of datastore names that are compatible with the storage policy.
     */
    public static List<String> getDatastores(final Connection connection,
            final PbmProfileId pbmProfileId)
            throws com.vmware.pbm.RuntimeFaultFaultMsg, PbmFaultFaultMsg {
        List<PbmPlacementHub> hubs = connection.getPbmPort().pbmQueryMatchingHub(
                connection.getPbmServiceInstanceContent().getPlacementSolver(), null,
                pbmProfileId);
        List<String> dataStoreNames = new ArrayList<>();
        if (hubs != null && !hubs.isEmpty()) {
            hubs.stream().filter(hub -> hub.getHubType().equals(VimNames.TYPE_DATASTORE))
                    .forEach(hub -> dataStoreNames.add(hub.getHubId()));
        }
        return dataStoreNames;
    }
}
