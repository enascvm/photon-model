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

import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.PROVISION_TYPE;

import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.vmware.pbm.PbmFaultFaultMsg;
import com.vmware.pbm.PbmPlacementHub;
import com.vmware.pbm.PbmProfileId;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.adapters.vsphere.util.finders.FinderException;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.VirtualDiskType;
import com.vmware.vim25.VirtualMachineDefinedProfileSpec;

/**
 * Utility methods that are common for all the clients like InstanceClient, EnumerationClient etc,
 */
public class ClientUtils {

    public static final String VM_PATH_FORMAT = "[%s] %s";

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

    public static Long toKb(long mb) {
        return mb * 1024;
    }

    /**
     * Get the provisioning type of the disk
     */
    public static VirtualDiskType getDiskProvisioningType(DiskService.DiskStateExpanded diskState)
            throws IllegalArgumentException {
        try {
            // Return null as default so that what ever defaults picked by vc will be honored
            // instead of we setting to default THIN.
            return diskState.customProperties != null
                    && diskState.customProperties.get(PROVISION_TYPE) != null ?
                    VirtualDiskType.fromValue(diskState.customProperties.get(PROVISION_TYPE)) :
                    null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Construct storage policy profile spec for a profile Id
     */
    public static List<VirtualMachineDefinedProfileSpec> getPbmProfileSpec(
            DiskService.DiskStateExpanded diskState) {
        if (diskState == null || diskState.resourceGroupStates == null
                || diskState.resourceGroupStates.isEmpty()) {
            return null;
        }
        List<VirtualMachineDefinedProfileSpec> profileSpecs = diskState.resourceGroupStates.stream()
                .map(rg -> {
                    VirtualMachineDefinedProfileSpec spbmProfile = new VirtualMachineDefinedProfileSpec();
                    spbmProfile.setProfileId(rg.id);
                    return spbmProfile;
                }).collect(Collectors.toList());
        return profileSpecs;
    }

    /**
     * Make file path to the vmdk file
     */
    public static String makePathToVmdkFile(String name, String dir) {
        String diskName = Paths.get(dir, name).toString();
        if (!diskName.endsWith(".vmdk")) {
            diskName += ".vmdk";
        }
        return diskName;
    }

    /**
     * Get one of the datastore compatible with storage policy
     */
    public static ManagedObjectReference getDatastoreFromStoragePolicy(final Connection connection,
            List<VirtualMachineDefinedProfileSpec> pbmSpec)
            throws FinderException, InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        if (pbmSpec != null) {
            for (VirtualMachineDefinedProfileSpec sp : pbmSpec) {
                try {
                    PbmProfileId pbmProfileId = new PbmProfileId();
                    pbmProfileId.setUniqueId(sp.getProfileId());
                    List<String> datastoreNames = ClientUtils.getDatastores(connection,
                            pbmProfileId);
                    String dsName = datastoreNames.stream().findFirst().orElse(null);
                    if (dsName != null) {
                        ManagedObjectReference dsFromSp = new ManagedObjectReference();
                        dsFromSp.setType(VimNames.TYPE_DATASTORE);
                        dsFromSp.setValue(dsName);
                        return dsFromSp;
                    }
                } catch (Exception runtimeFaultFaultMsg) {
                    // Just ignore. No need to log, as there are alternative paths.
                }
            }
        }
        return null;
    }
}
