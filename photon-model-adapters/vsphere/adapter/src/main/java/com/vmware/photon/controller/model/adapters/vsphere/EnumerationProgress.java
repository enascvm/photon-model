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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Phaser;

import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapters.vsphere.util.MoRefKeyedMap;
import com.vmware.photon.controller.model.adapters.vsphere.vapi.VapiConnection;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.vim25.ManagedObjectReference;

/**
 * Stores state/configuration/progress for an enumeration task.
 *
 */
public class EnumerationProgress {
    private final Set<String> resourceLinks;
    private final ComputeEnumerateResourceRequest request;
    private final ComputeStateWithDescription parent;
    private final VapiConnection endpoint;
    private ResourceTracker networkTracker;
    private ResourceTracker datastoreTracker;
    private ResourceTracker hostSystemTracker;
    private ResourceTracker computeResourceTracker;
    private ResourceTracker resourcePoolTracker;
    private ResourceTracker storagePolicyTracker;
    private ResourceTracker deleteDiskTracker;
    private ResourceTracker folderTracker;
    private ResourceTracker dcTracker;

    private final MoRefKeyedMap<AbstractOverlay> overlays;

    private Map<String, List<String>> disksToStoragePolicyMap;

    private Map<String, List<String>> dataStoresToStoragePolicyMap;

    private Phaser vmTracker;
    private Phaser snapshotTracker;
    private String regionId;
    private String dcLink;
    private String vcUuid;

    public String getVcUuid() {
        return this.vcUuid;
    }

    public EnumerationProgress(Set<String> resourceLinks, ComputeEnumerateResourceRequest request,
            ComputeStateWithDescription parent, VapiConnection endpoint, String regionId, String vcUuid) {
        this.resourceLinks = resourceLinks;
        this.request = request;
        this.parent = parent;
        this.endpoint = endpoint;
        this.vmTracker = new Phaser(1);
        this.snapshotTracker = new Phaser(1);
        this.overlays = new MoRefKeyedMap<>();
        this.disksToStoragePolicyMap = new HashMap<>();
        this.dataStoresToStoragePolicyMap = new HashMap<>();
        this.regionId = regionId;
        this.deleteDiskTracker = new ResourceTracker(1);
        this.vcUuid = vcUuid;
    }

    public VapiConnection getEndpoint() {
        return this.endpoint;
    }

    public ComputeEnumerateResourceRequest getRequest() {
        return this.request;
    }

    public String getRegionId() {
        return this.regionId;
    }

    public ComputeStateWithDescription getParent() {
        return this.parent;
    }

    public void expectNetworkCount(int count) {
        this.networkTracker = new ResourceTracker(count);
    }

    public ResourceTracker getNetworkTracker() {
        return this.networkTracker;
    }

    public Phaser getSnapshotTracker() {
        return this.snapshotTracker;
    }

    public void expectStoragePolicyCount(int count) {
        this.storagePolicyTracker = new ResourceTracker(count);
    }

    public ResourceTracker getStoragePolicyTracker() {
        return this.storagePolicyTracker;
    }

    public void expectDatastoreCount(int count) {
        this.datastoreTracker = new ResourceTracker(count);
    }

    public ResourceTracker getDatastoreTracker() {
        return this.datastoreTracker;
    }

    public void expectHostSystemCount(int count) {
        this.hostSystemTracker = new ResourceTracker(count);
    }

    public ResourceTracker getHostSystemTracker() {
        return this.hostSystemTracker;
    }

    public void expectComputeResourceCount(int count) {
        this.computeResourceTracker = new ResourceTracker(count);
    }

    public ResourceTracker getComputeResourceTracker() {
        return this.computeResourceTracker;
    }

    public void expectFolderCount(int count) {
        this.folderTracker = new ResourceTracker(count);
    }

    public ResourceTracker getFolderTracker() {
        return this.folderTracker;
    }

    public void expectResourcePoolCount(int count) {
        this.resourcePoolTracker = new ResourceTracker(count);
    }

    public ResourceTracker getResourcePoolTracker() {
        return this.resourcePoolTracker;
    }

    public List<String> getTenantLinks() {
        return this.parent.tenantLinks;
    }

    public Phaser getVmTracker() {
        return this.vmTracker;
    }

    public void resetVmTracker() {
        this.vmTracker = new Phaser(1);
    }

    public void resetSnapshotTracker() {
        this.snapshotTracker = new Phaser(1);
    }

    public AbstractOverlay getOverlay(ManagedObjectReference ref) {
        return this.overlays.get(ref);
    }

    public void track(AbstractOverlay overlay) {
        this.overlays.put(overlay.getId(), overlay);
    }

    public String getDcLink() {
        return this.dcLink;
    }

    public void setDcLink(String dcLink) {
        this.dcLink = dcLink;
    }

    public void touchResource(String selfLink) {
        if (selfLink != null) {
            this.resourceLinks.remove(selfLink);
        }
    }

    public ResourceTracker getDcTracker() {
        return this.dcTracker;
    }

    public void expectDatacenterCount(int count) {
        this.dcTracker = new ResourceTracker(count);
    }

    public ResourceTracker getDeleteDiskTracker() {
        return this.deleteDiskTracker;
    }

    public Map<String, List<String>> getDiskToStoragePolicyAssociationMap() {
        return this.disksToStoragePolicyMap;
    }

    public Map<String, List<String>> getDataStoresToStoragePolicyMap() {
        return this.dataStoresToStoragePolicyMap;
    }

    public void setDataStoresToStoragePolicyMap(Map<String, List<String>> dataStoresToStoragePolicyMap) {
        this.dataStoresToStoragePolicyMap = dataStoresToStoragePolicyMap;
    }

    public void setDisksToStoragePolicyMap(Map<String, List<String>> disksToStoragePolicyMap) {
        this.disksToStoragePolicyMap = disksToStoragePolicyMap;
    }

}
