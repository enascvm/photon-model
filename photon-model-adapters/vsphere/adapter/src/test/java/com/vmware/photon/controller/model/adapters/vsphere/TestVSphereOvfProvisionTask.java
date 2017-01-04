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

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapters.vsphere.ovf.ImportOvfRequest;
import com.vmware.photon.controller.model.adapters.vsphere.ovf.OvfImporterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskState.BootConfig;
import com.vmware.photon.controller.model.resources.DiskService.DiskState.BootConfig.FileEntry;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * This test deploys a CoreOS ova. The location of the OVF must be passed as a vc.ovfUri system
 * property.
 */
public class TestVSphereOvfProvisionTask extends BaseVSphereAdapterTest {

    // fields that are used across method calls, stash them as private fields
    private ComputeDescription computeHostDescription;
    private ComputeState computeHost;

    public static final String CLOUD_CONFIG_DATA =
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

    private URI ovfUri = getOvfUri();

    private DiskState bootDisk;

    @Test
    public void deployOvf() throws Throwable {
        if (this.ovfUri == null) {
            return;
        }

        // Create a resource pool where the VM will be housed
        this.resourcePool = createResourcePool();
        this.auth = createAuth();

        this.computeHostDescription = createComputeDescription();
        this.computeHost = createComputeHost(this.computeHostDescription);

        ComputeDescription computeDesc = createTemplate();

        ImportOvfRequest req = new ImportOvfRequest();
        req.ovfUri = this.ovfUri;
        req.template = computeDesc;

        Operation op = Operation.createPatch(this.host, OvfImporterService.SELF_LINK)
                .setBody(req)
                .setReferer(this.host.getPublicUri());

        CompletableFuture<Operation> f = this.host.sendWithFuture(op);

        // depending on OVF location you may want to increase the timeout
        f.get(300, TimeUnit.SECONDS);

        snapshotFactoryState("ovf", ComputeDescriptionService.class);

        enumerateComputes(this.computeHost);

        String descriptionLink = findFirstOvfDescriptionLink();

        this.bootDisk = createBootDisk(CLOUD_CONFIG_DATA);
        ComputeState vm = createVmState(descriptionLink);

        // set timeout for the next step, vmdk upload may take some time
        host.setTimeoutSeconds(60 * 5);

        // provision
        ProvisionComputeTaskState outTask = createProvisionTask(vm);
        awaitTaskEnd(outTask);

        snapshotFactoryState("ovf", ComputeService.class);

        deleteVmAndWait(vm);
    }

    private String findFirstOvfDescriptionLink() throws Exception {
        Query q = Query.Builder.create()
                .addFieldClause(ComputeState.FIELD_NAME_ID, "ovf-", MatchType.PREFIX)
                .build();

        QueryTask qt = QueryTask.Builder.createDirectTask()
                .setQuery(q)
                .build();

        Operation op = Operation.createPost(UriUtils.buildUri(this.host, ServiceUriPaths.CORE_QUERY_TASKS))
                .setBody(qt);

        QueryTask result = this.host.waitForResponse(op).getBody(QueryTask.class);

        return result.results.documentLinks.get(0);
    }

    private ComputeState createVmState(String descriptionLink) throws Throwable {
        ComputeState computeState = new ComputeState();
        computeState.id = computeState.name = nextName("from-ovf");
        computeState.documentSelfLink = computeState.id;
        computeState.descriptionLink = descriptionLink;
        computeState.resourcePoolLink = this.resourcePool.documentSelfLink;
        computeState.adapterManagementReference = getAdapterManagementReference();

        computeState.powerState = PowerState.ON;

        computeState.parentLink = this.computeHost.documentSelfLink;

        computeState.diskLinks = new ArrayList<>();
        computeState.diskLinks.add(this.bootDisk.documentSelfLink);

        Query q = createQueryForComputeResource();

        CustomProperties.of(computeState)
                .put(ComputeProperties.RESOURCE_GROUP_NAME, this.vcFolder)
                .put(ComputeProperties.PLACEMENT_LINK, findFirstMatching(q, ComputeState.class).documentSelfLink);

        ComputeService.ComputeState returnState = TestUtils.doPost(this.host, computeState,
                ComputeService.ComputeState.class,
                UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
        return returnState;
    }

    private DiskState createBootDisk(String cloudConfig) throws Throwable {
        DiskState res = new DiskState();
        res.bootOrder = 1;
        res.type = DiskType.HDD;
        res.id = res.name = "boot-disk";
        res.sourceImageReference = URI.create("file:///dev/null");

        res.bootConfig = new BootConfig();
        res.bootConfig.files = new FileEntry[] { new FileEntry(), new FileEntry() };
        res.bootConfig.files[0].path = "user-data";
        res.bootConfig.files[0].contents = cloudConfig;

        res.bootConfig.files[1].path = "public-keys";
        res.bootConfig.files[1].contents = IOUtils
                .toString(new File("src/test/resources/testkey.pub").toURI());

        return TestUtils.doPost(this.host, res,
                DiskState.class,
                UriUtils.buildUri(this.host, DiskService.FACTORY_LINK));
    }

    private ComputeDescription createTemplate() {
        ComputeDescription computeDesc = new ComputeDescription();
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
