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

import java.util.Collections;
import java.util.List;

import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimPath;
import com.vmware.vim25.ArrayOfManagedObjectReference;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.ObjectUpdate;
import com.vmware.vim25.VsanHostConfigInfo;

public class HostSystemOverlay extends AbstractOverlay {
    private boolean clusterHost;
    private ManagedObjectReference parentMoref;
    private static final String HOST_SUMMARY_CONFIG_VSANCONFIG = "summary.config.vsanHostConfig";

    protected HostSystemOverlay(ObjectUpdate cont) {
        super(cont);
        ensureType(VimNames.TYPE_HOST);
    }

    public String getName() {
        return (String) getOrFail(VimNames.PROPERTY_NAME);
    }

    public ManagedObjectReference getParent() {
        return (ManagedObjectReference) getOrFail(VimPath.host_parent);
    }

    public int getCoreCount() {
        return (short) getOrFail(VimPath.host_summary_hardware_numCpuCores);
    }

    public int getCpuMhz() {
        return (int) getOrFail(VimPath.host_summary_hardware_cpuMhz);
    }

    public long getTotalMemoryBytes() {
        return (long) getOrFail(VimPath.host_summary_hardware_memorySize);
    }

    public List<ManagedObjectReference> getDatastore() {
        ArrayOfManagedObjectReference res = (ArrayOfManagedObjectReference) getOrDefault(VimPath.res_datastore, null);
        if (res == null) {
            return Collections.emptyList();
        } else {
            return res.getManagedObjectReference();
        }
    }

    public List<ManagedObjectReference> getNetwork() {
        ArrayOfManagedObjectReference res = (ArrayOfManagedObjectReference) getOrDefault(VimPath.res_network, null);
        if (res == null) {
            return Collections.emptyList();
        } else {
            return res.getManagedObjectReference();
        }
    }

    // If the host is part of a DRS cluster
    public boolean isClusterHost() {
        return this.clusterHost;
    }

    public void setClusterHost(boolean clusterHost) {
        this.clusterHost = clusterHost;
    }

    // Get the parent moref for the host. Used for referencing the cluster for a clustered host.
    public ManagedObjectReference getParentMoref() {
        return this.parentMoref;
    }

    public void setParentMoref(ManagedObjectReference parentMoref) {
        this.parentMoref = parentMoref;
    }

    public boolean isInMaintenanceMode() {
        return (boolean) getOrDefault(VimPath.host_summary_runtime_inMaintenanceMode, false);
    }

    public String getVendor() {
        return (String) getOrDefault(VimPath.host_summary_hardware_vendor, null);
    }

    public String getModel() {
        return (String) getOrDefault(VimPath.host_summary_hardware_model, null);
    }

    public int getNumNics() {
        return (int) getOrDefault(VimPath.host_summary_hardware_numNics, 0);
    }

    public int getNumCpuPkgs() {
        return (short) getOrDefault(VimPath.host_summary_hardware_numCpuPkgs, 0);
    }

    // TODO: Check if this makes more sense processing it from Cluster info rather than host info as
    // we already get VimPath.res_configurationEx for cluster
    public VsanHostConfigInfo getVsanConfigInfo() {
        return (VsanHostConfigInfo) getOrDefault(HOST_SUMMARY_CONFIG_VSANCONFIG, null);
    }

    public String getNameOrNull() {
        return (String) getOrDefault(VimNames.PROPERTY_NAME, null);
    }
}
