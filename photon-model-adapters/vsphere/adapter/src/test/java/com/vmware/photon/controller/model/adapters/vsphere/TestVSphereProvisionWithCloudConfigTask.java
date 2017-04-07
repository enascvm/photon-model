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
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.IOUtils;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
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
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * This test provisions a VM from a template. It populates the OVF environment with a "user-data"
 * property.
 */
@Ignore
public class TestVSphereProvisionWithCloudConfigTask extends BaseVSphereAdapterTest {

    private AuthCredentialsServiceState auth;
    private ComputeDescription computeHostDescription;
    private ComputeState computeHost;

    @Test
    public void deployFromTemplateUsingCloudConfig() throws Throwable {
        // Create a resource pool where the VM will be housed
        this.resourcePool = createResourcePool();
        this.auth = createAuth();

        this.computeHostDescription = createComputeHostDescription();
        this.computeHost = createComputeHost();

        // enumerate all resources hoping to find the template
        doRefresh();

        // find the template by vm name
        // template must have vm-tools and cloud-config installed
        ComputeState template = findTemplate();

        // create instance by cloning
        ComputeDescription vmDescription = createVmDescription();
        ComputeState vm = createVmState(vmDescription, template.documentSelfLink);

        // kick off a provision task to do the actual VM creation
        ProvisionComputeTaskState outTask = createProvisionTask(vm);
        awaitTaskEnd(outTask);

        vm = getComputeState(vm);

        deleteVmAndWait(vm);
    }

    private ComputeState findTemplate()
            throws InterruptedException, ExecutionException, TimeoutException {
        String templateVmName = System.getProperty("vc.templateVmName");

        Query q = Query.Builder.create()
                .addFieldClause(ComputeState.FIELD_NAME_NAME, templateVmName)
                .addKindFieldClause(ComputeState.class)
                .build();

        QueryTask task = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .setQuery(q)
                .build();

        Operation op = Operation
                .createPost(UriUtils.buildUri(this.host, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
                .setBody(task);

        QueryTask result = this.host.waitForResponse(op).getBody(QueryTask.class);

        Object firstResult = result.results.documents.values().iterator().next();
        return Utils.fromJson(firstResult, ComputeState.class);
    }

    private void doRefresh() throws Throwable {
        enumerateComputes(this.computeHost);
    }

    private ComputeState createVmState(ComputeDescription vmDescription,
            String templateComputeLink) throws Throwable {
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
        computeState.diskLinks.add(createBootDisk(TestVSphereOvfProvisionTask.CLOUD_CONFIG_DATA).documentSelfLink);

        Query q = createQueryForComputeResource();
        CustomProperties.of(computeState)
                .put(ComputeProperties.RESOURCE_GROUP_NAME, this.vcFolder)
                .put(ComputeProperties.PLACEMENT_LINK, findFirstMatching(q, ComputeState.class).documentSelfLink)
                .put(CustomProperties.TEMPLATE_LINK, templateComputeLink);

        ComputeState returnState = TestUtils.doPost(this.host, computeState,
                ComputeState.class,
                UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
        return returnState;
    }

    private DiskState createBootDisk(String cloudConfig)
            throws Throwable {
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

    private ComputeDescription createVmDescription() throws Throwable {
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

        return TestUtils.doPost(this.host, computeDesc,
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

    private ComputeDescription createComputeHostDescription() throws Throwable {
        ComputeDescription computeDesc = new ComputeDescription();

        computeDesc.id = nextName("host");
        computeDesc.name = computeDesc.id;
        computeDesc.documentSelfLink = computeDesc.id;
        computeDesc.supportedChildren = new ArrayList<>();
        computeDesc.supportedChildren.add(ComputeType.VM_GUEST.name());
        computeDesc.instanceAdapterReference = UriUtils
                .buildUri(this.host, VSphereUriPaths.INSTANCE_SERVICE);
        computeDesc.authCredentialsLink = this.auth.documentSelfLink;
        computeDesc.enumerationAdapterReference = UriUtils
                .buildUri(this.host, VSphereUriPaths.ENUMERATION_SERVICE);
        computeDesc.regionId = this.datacenterId;

        return TestUtils.doPost(this.host, computeDesc,
                ComputeDescription.class,
                UriUtils.buildUri(this.host, ComputeDescriptionService.FACTORY_LINK));
    }
}