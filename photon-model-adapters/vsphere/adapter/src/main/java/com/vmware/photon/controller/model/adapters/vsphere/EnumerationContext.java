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

import java.util.List;
import java.util.concurrent.Phaser;

import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapters.vsphere.vapi.VapiConnection;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;

/**
 * Stores state/configuration/progress for an enumeration task.
 *
 */
public class EnumerationContext {
    private final ComputeEnumerateResourceRequest request;
    private final ComputeStateWithDescription parent;
    private final VapiConnection endpoint;
    private ResourceTracker networkTracker;
    private ResourceTracker datastoreTracker;
    private ResourceTracker hostSystemTracker;
    private ResourceTracker computeResourceTracker;
    private ResourceTracker resourcePoolTracker;

    private Phaser vmTracker;

    public EnumerationContext(ComputeEnumerateResourceRequest request,
            ComputeStateWithDescription parent, VapiConnection endpoint) {
        this.request = request;
        this.parent = parent;
        this.endpoint = endpoint;
        this.vmTracker = new Phaser(1);
    }

    public VapiConnection getEndpoint() {
        return this.endpoint;
    }

    public ComputeEnumerateResourceRequest getRequest() {
        return this.request;
    }

    public String getRegionId() {
        return getParent().description.regionId;
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
}
