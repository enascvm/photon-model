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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import static com.vmware.photon.controller.model.tasks.TestUtils.doPost;

import java.net.URI;
import java.util.ArrayList;
import java.util.UUID;

import org.junit.Test;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BasicConnection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.GetMoRef;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.SnapshotService;
import com.vmware.photon.controller.model.resources.SnapshotService.SnapshotState;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.SnapshotTaskService;
import com.vmware.photon.controller.model.tasks.SnapshotTaskService.SnapshotTaskState;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.xenon.common.UriUtils;

public class TestVSphereProvisionTask extends BaseVSphereAdapterTest {

    public URI cdromUri = getCdromUri();

    private ComputeDescription computeHostDescription;
    private ComputeState computeHost;
    private NetworkState network;

    @Test
    public void createInstanceSnapshotItAndDeleteIt() throws Throwable {
        // Create a resource pool where the VM will be housed
        this.resourcePool = createResourcePool();
        this.auth = createAuth();

        this.computeHostDescription = createComputeDescription();
        this.computeHost = createComputeHost();
        this.network = createNetwork(networkId);

        ComputeDescription vmDescription = createVmDescription();
        ComputeState vm = createVmState(vmDescription);

        // kick off a provision task to do the actual VM creation
        ProvisionComputeTaskState outTask = createProvisionTask(vm);
        awaitTaskEnd(outTask);

        vm = getComputeState(vm);

        createSnapshot(vm);

        deleteVmAndWait(vm);

        if (!isMock()) {
            assertInternalPropertiesSet(vm);
            BasicConnection connection = createConnection();

            GetMoRef get = new GetMoRef(connection);
            ManagedObjectReference moref = CustomProperties.of(vm)
                    .getMoRef(CustomProperties.MOREF);

            // try getting a property of vm: this must fail because vm is deleted
            try {
                get.entityProp(moref, "name");
                fail("VM must have been deleted");
            } catch (Exception e) {
            }
        }
    }

    private void createSnapshot(ComputeState vm) throws Throwable {
        SnapshotState snapshotState = createSnapshotState(vm);

        SnapshotTaskState sts = new SnapshotTaskState();
        sts.isMockRequest = isMock();
        sts.snapshotLink = snapshotState.documentSelfLink;
        sts.snapshotAdapterReference = UriUtils
                .buildUri(this.host, VSphereUriPaths.SNAPSHOT_SERVICE);

        SnapshotTaskState outSts = TestUtils.doPost(this.host,
                sts,
                SnapshotTaskState.class,
                UriUtils.buildUri(this.host,
                        SnapshotTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(SnapshotTaskState.class, outSts.documentSelfLink);

        SnapshotState stateAfterTaskComplete = this.host.getServiceState(null, SnapshotState.class,
                UriUtils.buildUri(this.host, snapshotState.documentSelfLink));

        if (!isMock()) {
            assertNotNull(CustomProperties.of(stateAfterTaskComplete)
                    .getMoRef(CustomProperties.MOREF));

        }
    }

    private SnapshotState createSnapshotState(ComputeState vm) throws Throwable {
        SnapshotState state = new SnapshotState();
        state.documentSelfLink = state.id = "snapshot" + UUID.randomUUID();
        state.name = state.id;
        state.computeLink = vm.documentSelfLink;
        state.description = "description: " + state.name;

        return doPost(this.host, state,
                SnapshotState.class,
                UriUtils.buildUri(this.host, SnapshotService.FACTORY_LINK));

    }

    private ComputeState createVmState(ComputeDescription vmDescription) throws Throwable {
        ComputeState computeState = new ComputeState();
        computeState.id = vmDescription.name;
        computeState.documentSelfLink = computeState.id;
        computeState.descriptionLink = vmDescription.documentSelfLink;
        computeState.resourcePoolLink = this.resourcePool.documentSelfLink;
        computeState.adapterManagementReference = getAdapterManagementReference();
        computeState.name = vmDescription.name;

        computeState.powerState = PowerState.ON;

        computeState.parentLink = this.computeHost.documentSelfLink;

        computeState.diskLinks = new ArrayList<>(1);
        computeState.diskLinks.add(createDisk("boot", DiskType.HDD, getDiskUri()).documentSelfLink);

        computeState.diskLinks.add(createDisk("movies", DiskType.HDD, null).documentSelfLink);
        computeState.diskLinks.add(createDisk("A", DiskType.FLOPPY, null).documentSelfLink);
        computeState.diskLinks
                .add(createDisk("cd", DiskType.CDROM, this.cdromUri).documentSelfLink);

        computeState.networkInterfaceLinks = new ArrayList<>(1);
        computeState.networkInterfaceLinks
                .add(createNic("nic for " + this.networkId, this.network.documentSelfLink));

        CustomProperties.of(computeState)
                .put(ComputeProperties.RESOURCE_GROUP_NAME, this.vcFolder);

        ComputeService.ComputeState returnState = doPost(this.host, computeState,
                ComputeService.ComputeState.class,
                UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
        return returnState;
    }

    private DiskState createDisk(String alias, DiskType type, URI sourceImageReference)
            throws Throwable {
        DiskState res = new DiskState();
        res.capacityMBytes = 2048;
        res.bootOrder = 1;
        res.type = type;
        res.id = res.name = "disk-" + alias;

        res.sourceImageReference = sourceImageReference;
        return doPost(this.host, res,
                DiskState.class,
                UriUtils.buildUri(this.host, DiskService.FACTORY_LINK));
    }

    private String createNic(String name, String networkLink) throws Throwable {
        NetworkInterfaceState nic = new NetworkInterfaceState();
        nic.name = name;
        nic.networkLink = networkLink;

        nic = doPost(this.host, nic,
                NetworkInterfaceState.class,
                UriUtils.buildUri(this.host, NetworkInterfaceService.FACTORY_LINK));

        return nic.documentSelfLink;
    }

    private ComputeDescription createVmDescription() throws Throwable {
        ComputeDescription computeDesc = new ComputeDescription();

        computeDesc.id = getVmName();
        computeDesc.documentSelfLink = computeDesc.id;
        computeDesc.supportedChildren = new ArrayList<>();
        computeDesc.instanceAdapterReference = UriUtils
                .buildUri(this.host, VSphereUriPaths.INSTANCE_SERVICE);
        computeDesc.authCredentialsLink = this.auth.documentSelfLink;
        computeDesc.name = computeDesc.id;
        computeDesc.dataStoreId = this.dataStoreId;

        return doPost(this.host, computeDesc,
                ComputeDescription.class,
                UriUtils.buildUri(this.host, ComputeDescriptionService.FACTORY_LINK));
    }

    /**
     * Create a compute host representing a vcenter server
     */
    private ComputeState createComputeHost() throws Throwable {
        ComputeState computeState = new ComputeState();
        computeState.id = UUID.randomUUID().toString();
        computeState.name = this.computeHostDescription.name;
        computeState.documentSelfLink = computeState.id;
        computeState.descriptionLink = this.computeHostDescription.documentSelfLink;
        computeState.resourcePoolLink = this.resourcePool.documentSelfLink;
        computeState.adapterManagementReference = getAdapterManagementReference();

        ComputeState returnState = TestUtils.doPost(this.host, computeState,
                ComputeState.class,
                UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
        return returnState;
    }

    private ComputeDescription createComputeDescription() throws Throwable {
        ComputeDescription computeDesc = new ComputeDescription();

        computeDesc.id = UUID.randomUUID().toString();
        computeDesc.name = computeDesc.id;
        computeDesc.documentSelfLink = computeDesc.id;
        computeDesc.supportedChildren = new ArrayList<>();
        computeDesc.supportedChildren.add(ComputeType.VM_GUEST.name());
        computeDesc.instanceAdapterReference = UriUtils
                .buildUri(this.host, VSphereUriPaths.INSTANCE_SERVICE);

        computeDesc.enumerationAdapterReference = UriUtils
                .buildUri(this.host, VSphereUriPaths.ENUMERATION_SERVICE);
        computeDesc.authCredentialsLink = this.auth.documentSelfLink;

        computeDesc.zoneId = this.zoneId;
        computeDesc.regionId = this.datacenterId;

        return TestUtils.doPost(this.host, computeDesc,
                ComputeDescription.class,
                UriUtils.buildUri(this.host, ComputeDescriptionService.FACTORY_LINK));
    }

    public URI getCdromUri() {
        String cdromUri = System.getProperty("vc.cdromUri");
        if (cdromUri == null) {
            return null;
        } else {
            return URI.create(cdromUri);
        }
    }

    public URI getDiskUri() {
        String diskUri = System.getProperty("vc.diskUri");
        if (diskUri == null) {
            return null;
        } else {
            return URI.create(diskUri);
        }
    }

    private String getVmName() {
        return "vm-" + String.valueOf(System.currentTimeMillis());
    }
}