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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.NIC_MAC_ADDRESS;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapters.vsphere.ProvisionContext.NetworkInterfaceStateWithDetails;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BasicConnection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.GetMoRef;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.SnapshotService;
import com.vmware.photon.controller.model.resources.SnapshotService.SnapshotState;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.SnapshotTaskService;
import com.vmware.photon.controller.model.tasks.SnapshotTaskService.SnapshotTaskState;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask.Query;

@RunWith(Suite.class)
@SuiteClasses({
        VSphereProvisioningTestSuite.TestVSphereProvisionTask.class,
        VSphereProvisioningTestSuite.ProvisionToClusteredHostTest.class,
        VSphereProvisioningTestSuite.ProvisionToStandaloneHostTest.class })
public class VSphereProvisioningTestSuite {

    public abstract static class BaseVSphereAdapterProvisionTest extends BaseVSphereAdapterTest {
        protected final String placementTargetVimType;

        protected URI cdromUri = getCdromUri();

        protected ComputeDescription computeHostDescription;
        protected ComputeState computeHost;
        protected NetworkState network;

        protected BaseVSphereAdapterProvisionTest(String placementTargetVimType) {
            this.placementTargetVimType = placementTargetVimType;
        }

        protected ComputeState provisionVm() throws Throwable {
            // Create a resource pool where the VM will be housed
            this.resourcePool = createResourcePool();
            this.auth = createAuth();

            this.computeHostDescription = createComputeDescription();
            this.computeHost = createComputeHost(this.computeHostDescription);
            this.network = createNetwork(networkId);

            EndpointService.EndpointState ep = createEndpointState(this.computeHost, this.computeHostDescription);
            EndpointState endpoint = TestUtils.doPost(this.host, ep, EndpointService.EndpointState
                            .class,
                    UriUtils.buildUri(this.host, EndpointService.FACTORY_LINK));

            enumerateComputes(this.computeHost, endpoint);

            ComputeDescription vmDescription = createVmDescription();
            ComputeState vm = createVmState(vmDescription);

            // kick off a provision task to do the actual VM creation
            ProvisionComputeTaskState outTask = createProvisionTask(vm);
            awaitTaskEnd(outTask);

            return getComputeState(vm);
        }

        protected void verifyVmIsDeleted(ComputeState vm) {
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

        protected ComputeState createVmState(ComputeDescription vmDescription) throws Throwable {
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

            // find a random compute of the requested type that has a resource pool
            String placementLink = "/mock/link";
            if (!isMock()) {
                Query q = createQueryForComputeResource(this.placementTargetVimType);
                placementLink = findFirstMatching(q, ComputeState.class).documentSelfLink;
            }

            CustomProperties.of(computeState)
                    .put(ComputeProperties.RESOURCE_GROUP_NAME, this.vcFolder)
                    .put(ComputeProperties.PLACEMENT_LINK, placementLink);

            ComputeService.ComputeState returnState = TestUtils.doPost(this.host, computeState,
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
            return TestUtils.doPost(this.host, res,
                    DiskState.class,
                    UriUtils.buildUri(this.host, DiskService.FACTORY_LINK));
        }

        protected ComputeDescription createVmDescription() throws Throwable {
            ComputeDescription computeDesc = new ComputeDescription();

            computeDesc.id = nextName("vm");
            computeDesc.regionId = this.datacenterId;
            computeDesc.documentSelfLink = computeDesc.id;
            computeDesc.supportedChildren = new ArrayList<>();
            computeDesc.instanceAdapterReference = UriUtils
                    .buildUri(this.host, VSphereUriPaths.INSTANCE_SERVICE);
            computeDesc.authCredentialsLink = this.auth.documentSelfLink;
            computeDesc.name = computeDesc.id;
            computeDesc.dataStoreId = this.dataStoreId;

            // set default cpu and memory
            computeDesc.cpuCount = 1;
            computeDesc.totalMemoryBytes = 1024 * 1024 * 1024; // 1GB

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
    }

    public static class TestVSphereProvisionTask extends BaseVSphereAdapterProvisionTest {

        public TestVSphereProvisionTask() {
            super(VimNames.TYPE_CLUSTER_COMPUTE_RESOURCE);
        }

        @Test
        public void createInstanceSnapshotItAndDeleteIt() throws Throwable {
            ComputeState vm = null;
            try {
                vm = provisionVm();
                createSnapshot(vm);
            } finally {
                if (vm != null) {
                    deleteVmAndWait(vm);
                    verifyVmIsDeleted(vm);
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

        @Test
        public void createComputeWithIps() throws Throwable {
            CountDownLatch countDownLatch = new CountDownLatch(2);
            // Create a resource pool where the VM will be housed
            this.resourcePool = createResourcePool();
            this.auth = createAuth();

            this.computeHostDescription = createComputeDescription();
            this.computeHost = createComputeHost(this.computeHostDescription);
            this.network = createNetwork(networkId);

            EndpointService.EndpointState ep = createEndpointState(this.computeHost, this.computeHostDescription);
            EndpointState endpoint = TestUtils.doPost(this.host, ep, EndpointService.EndpointState
                            .class,
                    UriUtils.buildUri(this.host, EndpointService.FACTORY_LINK));

            enumerateComputes(this.computeHost, endpoint);

            ComputeDescription vmDescription = createVmDescription();
            ComputeState vm = createVmState(vmDescription);

            NetworkInterfaceStateWithDetails n1 = new NetworkInterfaceStateWithDetails();

            String mac1Address = "00:50:56:8b:54:bd";
            String ip1 = "10.10.10.10";
            Map<String, List<String>> ipMaps = new HashMap<>();
            List<String> ipList = new ArrayList();
            ipList.add(ip1);
            ipMaps.put(mac1Address, ipList);

            List<NetworkInterfaceStateWithDetails> nics = new ArrayList();

            n1.customProperties = new HashMap<>();
            n1.customProperties.put(NIC_MAC_ADDRESS, mac1Address);

            // test with assignPublicIpAddress
            n1.description = new NetworkInterfaceDescription();
            n1.description.assignPublicIpAddress = true;
            n1.subnetLink = "/dummy/subnetLink";

            NetworkInterfaceStateWithDetails networkInterfaceState1 = TestUtils.doPost(this.host,
                    n1, NetworkInterfaceStateWithDetails.class,
                    UriUtils.buildUri(this.host, NetworkInterfaceService.FACTORY_LINK));

            nics.add(networkInterfaceState1);

            VSphereAdapterInstanceUpdateIpHelper.updateIPForCompute(this.host,vm.documentSelfLink, ip1).thenApply(c -> {
                this.host.log(Level.INFO, "Update compute IP [%s] for computeLink [%s] ", ip1, vm.documentSelfLink);
                VSphereAdapterInstanceUpdateIpHelper.updateIPForNics(this.host, ipMaps, nics, () -> {
                    return String.format("Update networkInterfaces IP [%s] for computeLink [%s]", ipList, vm.documentSelfLink);
                }, () -> {
                        return String.format("Error updating networkInterfaces IP [%s] for computeLink [%s]: ",
                            ipList,
                            vm.documentSelfLink);
                    }, (s) -> countDownLatch.countDown());
                countDownLatch.countDown();
                return null;
            })
                    .exceptionally(e -> {
                        if (e != null) {
                            this.host.log(Level.SEVERE, String.format("Error updating compute IP [%s] for computeLink [%s]: %s", ip1, vm.documentSelfLink, e));
                        }
                        countDownLatch.countDown();
                        assertTrue("Error updating compute IP", false);
                        return null;
                    });
            countDownLatch.await(60, TimeUnit.SECONDS);
        }

        private SnapshotState createSnapshotState(ComputeState vm) throws Throwable {
            SnapshotState state = new SnapshotState();
            state.documentSelfLink = state.id = "snapshot" + UUID.randomUUID();
            state.name = state.id;
            state.computeLink = vm.documentSelfLink;
            state.description = "description: " + state.name;

            return TestUtils.doPost(this.host, state,
                    SnapshotState.class,
                    UriUtils.buildUri(this.host, SnapshotService.FACTORY_LINK));
        }
    }

    public static class ProvisionToClusteredHostTest extends BaseVSphereAdapterProvisionTest {
        public ProvisionToClusteredHostTest() {
            super(VimNames.TYPE_HOST);
            this.dataStoreId = null;
        }

        @Test
        public void test() throws Throwable {
            ComputeState vm = null;
            try {
                vm = provisionVm();
            } finally {
                if (vm != null) {
                    deleteVmAndWait(vm);
                    verifyVmIsDeleted(vm);
                }
            }
        }

        @Override
        public URI getDiskUri() {
            return null;
        }

        @Override
        public URI getCdromUri() {
            return null;
        }
    }

    public static class ProvisionToStandaloneHostTest extends BaseVSphereAdapterProvisionTest {
        public ProvisionToStandaloneHostTest() {
            super(VimNames.TYPE_COMPUTE_RESOURCE);
            this.dataStoreId = null;
        }

        @Test
        public void test() throws Throwable {
            ComputeState vm = null;
            try {
                vm = provisionVm();
            } finally {
                if (vm != null) {
                    deleteVmAndWait(vm);
                    verifyVmIsDeleted(vm);
                }
            }
        }

        @Override
        public URI getDiskUri() {
            return null;
        }

        @Override
        public URI getCdromUri() {
            return null;
        }
    }
}
