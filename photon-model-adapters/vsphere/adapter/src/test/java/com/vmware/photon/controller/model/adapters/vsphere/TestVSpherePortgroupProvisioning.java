/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

import java.util.EnumSet;
import java.util.HashSet;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.photon.controller.model.adapterapi.SubnetInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.adapters.vsphere.network.DvsNetworkService;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.tasks.ProvisionSubnetTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionSubnetTaskService.ProvisionSubnetTaskState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * This test provisions a VM from a template. It populates the OVF environment with a "user-data"
 * property.
 */
@Ignore
public class TestVSpherePortgroupProvisioning extends BaseVSphereAdapterTest {
    private ComputeDescription computeHostDescription;

    private ComputeState computeHost;

    @Test
    public void createPortgroup() throws Throwable {

        // Create a resource pool where the VM will be housed
        this.resourcePool = createResourcePool();
        this.auth = createAuth();

        this.computeHostDescription = createComputeHostDescription();
        this.computeHost = createComputeHost();

        // enumerate all resources hoping to find the template
        doRefresh();
        snapshotFactoryState("networks", NetworkService.class);

        NetworkState dvsSwitch = fetchServiceState(NetworkState.class, findDvs(networkId));

        SubnetState subnet = new SubnetState();
        subnet.subnetCIDR = "0.0.0.0/0";
        subnet.networkLink = dvsSwitch.documentSelfLink;
        subnet.name = nextName("pg");

        subnet.instanceAdapterReference = UriUtils.buildUri(this.host, DvsNetworkService.SELF_LINK);

        subnet = TestUtils.doPost(this.host, subnet, SubnetState.class,
                UriUtils.buildUri(this.host, SubnetService.FACTORY_LINK));

        ProvisionSubnetTaskState createTask = new ProvisionSubnetTaskState();
        createTask.options = createOptions();
        createTask.requestType = InstanceRequestType.CREATE;
        createTask.subnetDescriptionLink = subnet.documentSelfLink;

        createTask = TestUtils.doPost(this.host,
                createTask,
                ProvisionSubnetTaskState.class,
                UriUtils.buildUri(this.host,
                        ProvisionSubnetTaskService.FACTORY_LINK));

        awaitTaskEnd(createTask);

        // refresh state
        subnet = getSubnetState(subnet);

        // delete the portgroup
        deleteThePortgroup(subnet);
    }

    private EnumSet<TaskOption> createOptions() {
        if (isMock()) {
            return EnumSet.of(TaskOption.IS_MOCK);
        }

        return EnumSet.noneOf(TaskOption.class);
    }

    protected void deleteThePortgroup(SubnetState subnet) throws Throwable {
        ProvisionSubnetTaskState removalTask = new ProvisionSubnetTaskState();
        removalTask.options = createOptions();
        removalTask.requestType = InstanceRequestType.DELETE;
        removalTask.subnetDescriptionLink = subnet.documentSelfLink;

        removalTask = TestUtils.doPost(this.host,
                removalTask,
                ProvisionSubnetTaskState.class,
                UriUtils.buildUri(this.host,
                        ProvisionSubnetTaskService.FACTORY_LINK));
        awaitTaskEnd(removalTask);
    }


    private <T extends ServiceDocument> T fetchServiceState(Class<T> bodyType, String networkLink) {
        Operation op = Operation.createGet(this.host, networkLink);
        op = this.host.waitForResponse(op);
        return op.getBody(bodyType);
    }

    protected String findDvs(String name) throws Throwable {
        Query q = Query.Builder.create()
                .addKindFieldClause(NetworkState.class)
                .addCompositeFieldClause(NetworkState.FIELD_NAME_CUSTOM_PROPERTIES, CustomProperties.TYPE,
                        VimNames.TYPE_DVS)
                .addFieldClause(NetworkState.FIELD_NAME_NAME, name)
                .build();

        QueryTask task = QueryTask.Builder.createDirectTask()
                .setQuery(q)
                .build();

        Operation op = Operation
                .createPost(this.host, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS)
                .setBody(task);

        Operation result = this.host.waitForResponse(op);

        try {
            return result.getBody(QueryTask.class).results.documentLinks.get(0);
        } catch (Exception e) {
            Assert.fail(e.getMessage());
            return null;
        }
    }

    private void doRefresh() throws Throwable {
        enumerateComputes(this.computeHost);
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

        computeDesc.id = UUID.randomUUID().toString();
        computeDesc.name = computeDesc.id;
        computeDesc.documentSelfLink = computeDesc.id;
        computeDesc.supportedChildren = new HashSet<>();
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