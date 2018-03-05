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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimPath;
import com.vmware.vim25.ArrayOfManagedObjectReference;
import com.vmware.vim25.ClusterConfigInfoEx;
import com.vmware.vim25.ClusterDrsConfigInfo;
import com.vmware.vim25.ComputeResourceConfigInfo;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.ObjectUpdate;
import com.vmware.vim25.VsanClusterConfigInfo;
import com.vmware.vim25.VsanHostConfigInfo;

public class ComputeResourceOverlay extends AbstractOverlay {

    protected ComputeResourceOverlay(ObjectUpdate cont) {
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

    private Collection<ManagedObjectReference> getHosts() {
        ArrayOfManagedObjectReference hosts = (ArrayOfManagedObjectReference) getOrDefault(
                VimPath.res_host, null);
        if (hosts != null) {
            return hosts.getManagedObjectReference();
        } else {
            return Collections.emptyList();
        }
    }

    public boolean isDrsEnabled() {
        ClusterConfigInfoEx info = getClusterConfigInfoEx();

        Boolean isDrsEnabled = false;
        if (null != info) {
            ClusterDrsConfigInfo drsConfig = info.getDrsConfig();
            isDrsEnabled = drsConfig == null || drsConfig.isEnabled();
        }
        return isDrsEnabled;
    }

    /**
     * Marks the HostSytemOverlay as cluster host if they belong this cluster
     */
    public void markHostAsClustered(List<HostSystemOverlay> hosts) {
        getHosts().stream().forEach(hostRef -> {
            HostSystemOverlay hostSystem = hosts.stream()
                    .filter(ho -> Objects.equals(ho.getId().getValue(), hostRef.getValue()))
                    .findFirst().orElse(null);
            if (hostSystem != null) {
                hostSystem.setClusterHost(true);
                hostSystem.setParentMoref(getId());
            }
        });
    }

    public ManagedObjectReference getRootResourcePool() {
        return (ManagedObjectReference) getOrFail(VimPath.res_resourcePool);
    }

    public int getTotalCpuCores() {
        return (short) getOrFail(VimPath.res_summary_numCpuCores);
    }

    public long getTotalMemoryBytes() {
        return (long) getOrDefault(VimPath.res_summary_totalMemory, 0L);
    }

    public int getTotalCpuMhz() {
        return (int) getOrFail(VimPath.res_summary_totalCpu);
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

    public String getVsanConfigId() {
        ClusterConfigInfoEx info = getClusterConfigInfoEx();

        String vsanConfigId = "";
        if (null != info) {
            VsanClusterConfigInfo vsanConfig = info.getVsanConfigInfo();
            vsanConfigId = null == vsanConfig.getDefaultConfig().getUuid() ? "" : vsanConfig.getDefaultConfig().getUuid();
        }
        return vsanConfigId;
    }

    public boolean isVsanEnabled() {
        ClusterConfigInfoEx info = getClusterConfigInfoEx();

        Boolean isVsanEnabled = false;
        if (null != info) {
            VsanClusterConfigInfo vsanConfig = info.getVsanConfigInfo();
            isVsanEnabled = null == vsanConfig ? false : vsanConfig.isEnabled();
        }
        return isVsanEnabled;
    }

    public List<VsanHostConfigInfo> getVsanHostConfig() {

        ClusterConfigInfoEx info = getClusterConfigInfoEx();
        List<VsanHostConfigInfo> hostConfig = new ArrayList<>();
        if (null != info) {
            hostConfig.addAll(info.getVsanHostConfig());
        }
        return hostConfig;
    }

    private ClusterConfigInfoEx getClusterConfigInfoEx() {
        ComputeResourceConfigInfo cfg = ((ComputeResourceConfigInfo) getOrFail(
                VimPath.res_configurationEx));
        ClusterConfigInfoEx info = null;
        if (cfg instanceof ClusterConfigInfoEx) {
            info = (((ClusterConfigInfoEx) cfg));
        }
        return info;
    }
}
