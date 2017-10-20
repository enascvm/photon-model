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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.DISK_MODE_PERSISTENT;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.LIMIT_IOPS;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.PROVISION_TYPE;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.SHARES_LEVEL;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapters.vsphere.ovf.ImportOvfRequest;
import com.vmware.photon.controller.model.adapters.vsphere.ovf.OvfImporterService;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BasicConnection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.GetMoRef;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.vim25.SharesLevel;
import com.vmware.vim25.VirtualDisk;
import com.vmware.vim25.VirtualDiskFlatVer2BackingInfo;
import com.vmware.vim25.VirtualDiskType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Base class for deploy from ovf.
 */
public class TestVSphereOvfProvisionTaskBase extends BaseVSphereAdapterTest {
    // fields that are used across method calls, stash them as private fields
    protected ComputeDescriptionService.ComputeDescription computeHostDescription;
    protected ComputeService.ComputeState computeHost;

    protected static final String CLOUD_CONFIG_DATA =
            "#cloud-config\n"
                    + "\n"
                    + "ssh_authorized_keys:\n"
                    + "  - ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDITHua9omXdLCqnU6KVu5D46PQ0CjMTHNGD/yDM"
                    + "Dz3GqcamB8RxwPlMIRVHQQWaHAFRFRTZ7eQt8CJmNM1g3b2zJKuwj6PQ2GnxdfHclN9uDT7KpbjjugWai"
                    + "Filqv6zbFdvBe+jisgCLqc+2512eMpDuLPSobPBplSbAzGLgSKSdEL6biTW/yurer9gG2WIrFl6UN7RXa"
                    + "w5KPCK1N3RIVRQnfmEC6rN4iqa/67QnDBsfpvOkmqpkXDMjCPjuc8umCmUKTGa0DPXNY5VCUOJeCT5Mro"
                    + "roF68IscTCo5+sMETNtA3b59Nj6a8+Rw7oyhCqcxC4LpqdxjSCWalyv+6HjV photon-model/testkey\n"
                    + "\n"
                    + "write_files:\n"
                    + "- path: /tmp/hello.txt\n"
                    + "  content: \"world\"\n";

    protected URI ovfUri = getOvfUri();

    protected DiskService.DiskState bootDisk;

    protected void deployOvf(boolean isStoragePolicyBased) throws Throwable {
        deployOvf(isStoragePolicyBased, false, null);
    }

    protected void deployOvf(boolean isStoragePolicyBased, boolean withAdditionalDisks,
            Map<String, String> customProperties) throws Throwable {
        if (this.ovfUri == null) {
            return;
        }
        ComputeService.ComputeState vm = null;
        try {
            vm = deployOvfAndReturnComputeState(isStoragePolicyBased, withAdditionalDisks,
                    customProperties, false);
        } finally {
            if (vm != null) {
                deleteVmAndWait(vm);
            }
        }
    }

    protected void deployOvfWithSnapshotLimitAndGetState(boolean isStoragePolicyBased,
                                                          boolean withAdditionalDisks,
                                                          Map<String, String> customProperties) throws Throwable {
        if (this.ovfUri == null) {
            return;
        }
        ComputeService.ComputeState vm = null;
        try {
            vm = deployOvfAndReturnComputeState(isStoragePolicyBased, withAdditionalDisks,
                    customProperties, true);
            if (vm == null) {
                return;
            }
            createSnapshotAndWait(vm, false); // 1st snapshot will succeed
            createSnapshotAndWaitFailure(vm); // 2nd snapshot will fail, since snapshot limit is set to "1"
        } finally {
            deleteVmAndWait(vm);
        }
    }

    protected void deployOvfToNewFolder(boolean isStoragePolicyBased,
                                                         boolean withAdditionalDisks,
                                                         Map<String, String> customProperties) throws Throwable {
        if (this.ovfUri == null) {
            return;
        }
        ComputeService.ComputeState vm = null;
        final String vmFolder = "/Datacenters/Datacenter/vm/test-folder";
        this.vcFolder = vmFolder; // setting an explicit folder for this vm, if folder is not present it will  be created
        try {
            vm = deployOvfAndReturnComputeState(isStoragePolicyBased, withAdditionalDisks,
                    customProperties, false);
        } finally {
            if (vm != null) {
                deleteVmAndWait(vm);
            }
        }
    }

    private ComputeService.ComputeState deployOvfAndReturnComputeState(boolean isStoragePolicyBased,
                                                                       boolean withAdditionalDisks,
                                                                       Map<String, String> customProperties,
                                                                       boolean isSnapshotLimitCase) throws Throwable {
        ComputeService.ComputeState vm = null;
        // Create a resource pool where the VM will be housed
        this.resourcePool = createResourcePool();
        this.auth = createAuth();

        this.computeHostDescription = createComputeDescription();
        this.computeHost = createComputeHost(this.computeHostDescription);

        ComputeDescriptionService.ComputeDescription computeDesc = createTemplate();

        ImportOvfRequest req = new ImportOvfRequest();
        req.ovfUri = this.ovfUri;
        req.template = computeDesc;

        Operation op = Operation.createPatch(this.host, OvfImporterService.SELF_LINK)
                .setBody(req)
                .setReferer(this.host.getPublicUri());

        CompletableFuture<Operation> f = this.host.sendWithFuture(op);

        // depending on OVF location you may want to increase the timeout
        f.get(360, TimeUnit.SECONDS);

        snapshotFactoryState("ovf", ComputeDescriptionService.class);

        enumerateComputes(this.computeHost);

        String descriptionLink = findFirstOvfDescriptionLink();

        this.bootDisk = createBootDiskWithCustomProperties(CLOUD_CONFIG_DATA, isStoragePolicyBased, customProperties);
        vm = createVmState(descriptionLink, withAdditionalDisks, customProperties, isSnapshotLimitCase);

        // set timeout for the next step, vmdk upload may take some time
        host.setTimeoutSeconds(60 * 5);

        // provision
        ProvisionComputeTaskService.ProvisionComputeTaskState outTask = createProvisionTask(vm);
        awaitTaskEnd(outTask);
        vm = getComputeState(vm);

        snapshotFactoryState("ovf", ComputeService.class);
        if (!isMock() && withAdditionalDisks || customProperties.get(PROVISION_TYPE) != null) {
            BasicConnection connection = createConnection();
            GetMoRef get = new GetMoRef(connection);
            List<VirtualDisk> virtualDisks = fetchAllVirtualDisks(vm, get);

            if (customProperties.get(PROVISION_TYPE) != null) {
                VirtualDiskType diskProvisionType = VirtualDiskType.fromValue(customProperties.get(PROVISION_TYPE));
                VirtualDisk bootDisk = virtualDisks.stream()
                        .filter(vd -> vd.getUnitNumber() == 0)
                        .findFirst().orElse(null);

                VirtualDiskFlatVer2BackingInfo backing = (VirtualDiskFlatVer2BackingInfo) bootDisk.getBacking();

                if (VirtualDiskType.THIN == diskProvisionType) {
                    assertTrue(backing.isThinProvisioned());
                } else if (VirtualDiskType.THICK == diskProvisionType) {
                    assertFalse(backing.isThinProvisioned());
                } else if (VirtualDiskType.EAGER_ZEROED_THICK == diskProvisionType) {
                    assertFalse(backing.isThinProvisioned());
                    assertTrue(backing.isEagerlyScrub());
                }
            }

            if (withAdditionalDisks) {
                assertEquals(3, virtualDisks.size());
            }
        }
        return vm;
    }

    protected String findFirstOvfDescriptionLink() throws Exception {
        QueryTask.Query q = QueryTask.Query.Builder.create()
                .addFieldClause(
                        ComputeService.ComputeState.FIELD_NAME_ID, "ovf-", QueryTask.QueryTerm.MatchType.PREFIX)
                .build();

        QueryTask qt = QueryTask.Builder.createDirectTask()
                .setQuery(q)
                .build();

        Operation op = Operation.createPost(UriUtils.buildUri(this.host, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
                .setBody(qt);

        QueryTask result = this.host.waitForResponse(op).getBody(QueryTask.class);

        return result.results.documentLinks.get(0);
    }

    protected ComputeService.ComputeState createVmState(String descriptionLink) throws Throwable {
        return createVmState(descriptionLink, false, null, false);
    }

    private ComputeService.ComputeState createVmState(String descriptionLink, boolean withAdditionalDisks,
                                                      Map<String, String> customProperties, boolean isSnapshotLimitCase) throws Throwable {
        ComputeService.ComputeState computeState = new ComputeService.ComputeState();
        computeState.id = computeState.name = nextName("from-ovf");
        computeState.documentSelfLink = computeState.id;
        computeState.descriptionLink = descriptionLink;
        computeState.resourcePoolLink = this.resourcePool.documentSelfLink;
        computeState.adapterManagementReference = getAdapterManagementReference();

        computeState.powerState = ComputeService.PowerState.ON;

        computeState.parentLink = this.computeHost.documentSelfLink;

        computeState.diskLinks = new ArrayList<>();
        computeState.diskLinks.add(this.bootDisk.documentSelfLink);

        if (withAdditionalDisks) {
            computeState.diskLinks.add(createDiskWithDatastore("AdditionalDisk1", DiskService.DiskType.HDD,
                    0, null, ADDITIONAL_DISK_SIZE, buildCustomProperties()).documentSelfLink);
            computeState.diskLinks
                    .add(createDiskWithStoragePolicy("AdditionalDisk2", DiskService.DiskType.HDD,
                            0, null, ADDITIONAL_DISK_SIZE, buildCustomProperties()).documentSelfLink);
        }

        computeState.networkInterfaceLinks = new ArrayList<>(1);

        NetworkService.NetworkState network = createNetwork(networkId);
        computeState.networkInterfaceLinks.add(createNic("nic for " + this.networkId, network.documentSelfLink));

        QueryTask.Query q = createQueryForComputeResource();

        CustomProperties.of(computeState)
                .put(ComputeProperties.RESOURCE_GROUP_NAME, this.vcFolder)
                .put(ComputeProperties.PLACEMENT_LINK, findFirstMatching(q, ComputeService.ComputeState.class).documentSelfLink);

        if (isSnapshotLimitCase) {
            computeState.customProperties.put(CustomProperties.SNAPSHOT_MAXIMUM_LIMIT, "1");
        }

        if (customProperties != null) {
            computeState.customProperties.putAll(customProperties);
        }

        ComputeService.ComputeState returnState = TestUtils.doPost(this.host, computeState,
                ComputeService.ComputeState.class,
                UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
        return returnState;
    }

    private DiskService.DiskState buildBootDisk(String cloudConfig) throws
            Throwable {
        DiskService.DiskState res = new DiskService.DiskState();
        res.bootOrder = 1;
        res.type = DiskService.DiskType.HDD;
        res.id = res.name = "boot-disk";
        res.sourceImageReference = URI.create("file:///dev/null");
        res.capacityMBytes = 10240;

        res.bootConfig = new DiskService.DiskState.BootConfig();
        res.bootConfig.files = new DiskService.DiskState.BootConfig.FileEntry[] {
                new DiskService.DiskState.BootConfig.FileEntry(),
                new DiskService.DiskState.BootConfig.FileEntry() };
        res.bootConfig.files[0].path = "user-data";
        res.bootConfig.files[0].contents = cloudConfig;

        res.bootConfig.files[1].path = "public-keys";
        res.bootConfig.files[1].contents = IOUtils
                .toString(new File("src/test/resources/testkey.pub").toURI());

        return res;
    }

    private DiskService.DiskState buildBootDiskWithCustomProperties(String cloudConfig,
            Map<String, String> customProperties) throws Throwable {
        HashMap<String, String> diskCustomProperties = new HashMap<>();

        VirtualDiskType diskProvisionType = customProperties.get(PROVISION_TYPE) != null ?
                VirtualDiskType.fromValue(customProperties.get(PROVISION_TYPE)) : null;

        if (diskProvisionType == null) {
            diskProvisionType = VirtualDiskType.THIN;
        }

        diskCustomProperties.put(DISK_MODE_PERSISTENT, "true");
        diskCustomProperties.put(PROVISION_TYPE, diskProvisionType.value());
        diskCustomProperties.put(SHARES_LEVEL, SharesLevel.HIGH.value());
        diskCustomProperties.put(LIMIT_IOPS, "100");

        DiskService.DiskState res = buildBootDisk(cloudConfig);
        res.customProperties = diskCustomProperties;

        return res;
    }

    protected DiskService.DiskState createBootDisk(String cloudConfig) throws
            Throwable {
        DiskService.DiskState res = buildBootDisk(cloudConfig);
        return TestUtils.doPost(this.host, res,
                DiskService.DiskState.class,
                UriUtils.buildUri(this.host, DiskService.FACTORY_LINK));
    }

    private DiskService.DiskState createBootDisk(String cloudConfig, boolean isStoragePolicyBased) throws
            Throwable {
        DiskService.DiskState res = buildBootDisk(cloudConfig);
        if (!isStoragePolicyBased) {
            // Create storage description
            StorageDescriptionService.StorageDescription sd = new StorageDescriptionService
                    .StorageDescription();
            sd.id = sd.name = this.dataStoreId;
            sd = TestUtils.doPost(this.host, sd,
                    StorageDescriptionService.StorageDescription.class,
                    UriUtils.buildUri(this.host, StorageDescriptionService.FACTORY_LINK));

            res.storageDescriptionLink = sd.documentSelfLink;
        } else {
            // Create Resource group state
            ResourceGroupService.ResourceGroupState rg = createResourceGroupState();
            res.groupLinks = new HashSet<>();
            res.groupLinks.add(rg.documentSelfLink);
        }

        return TestUtils.doPost(this.host, res,
                DiskService.DiskState.class,
                UriUtils.buildUri(this.host, DiskService.FACTORY_LINK));
    }

    private DiskService.DiskState createBootDiskWithCustomProperties(String cloudConfig,
            boolean isStoragePolicyBased, Map<String, String> customProperties) throws
          Throwable {
        DiskService.DiskState res = buildBootDiskWithCustomProperties(cloudConfig, customProperties);
        if (!isStoragePolicyBased) {
            // Create storage description
            StorageDescriptionService.StorageDescription sd = new StorageDescriptionService
                    .StorageDescription();
            sd.id = sd.name = this.dataStoreId;
            sd = TestUtils.doPost(this.host, sd,
                  StorageDescriptionService.StorageDescription.class,
                  UriUtils.buildUri(this.host, StorageDescriptionService.FACTORY_LINK));

            res.storageDescriptionLink = sd.documentSelfLink;
        } else {
            // Create Resource group state
            ResourceGroupService.ResourceGroupState rg = createResourceGroupState();
            res.groupLinks = new HashSet<>();
            res.groupLinks.add(rg.documentSelfLink);
        }

        return TestUtils.doPost(this.host, res,
              DiskService.DiskState.class,
              UriUtils.buildUri(this.host, DiskService.FACTORY_LINK));
    }

    protected ComputeDescriptionService.ComputeDescription createTemplate() {
        ComputeDescriptionService.ComputeDescription computeDesc = new ComputeDescriptionService.ComputeDescription();
        computeDesc.supportedChildren = new ArrayList<>();
        computeDesc.regionId = this.datacenterId;
        computeDesc.instanceAdapterReference = UriUtils
                .buildUri(this.host, VSphereUriPaths.INSTANCE_SERVICE);
        computeDesc.authCredentialsLink = this.auth.documentSelfLink;
        computeDesc.name = computeDesc.id;
        computeDesc.dataStoreId = this.dataStoreId;

        return computeDesc;
    }

    public URI getOvfUri() {
        String res = System.getProperty("vc.ovfUri");
        if (res == null) {
            return null;
        } else {
            return URI.create(res);
        }
    }
}
