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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapterapi.NetworkInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.adapters.vsphere.network.DvsNetworkService;
import com.vmware.photon.controller.model.adapters.vsphere.network.DvsProperties;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.tasks.ProvisionNetworkTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionNetworkTaskService.ProvisionNetworkTaskState;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService.ResourceEnumerationTaskState;
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

        NetworkState networkState = new NetworkState();
        networkState.subnetCIDR = "0.0.0.0/0";
        networkState.regionId = datacenterId;
        networkState.instanceAdapterReference = UriUtils
                .buildUri(this.host, DvsNetworkService.SELF_LINK);
        networkState.authCredentialsLink = this.auth.documentSelfLink;
        networkState.adapterManagementReference = getAdapterManagementReference();
        networkState.resourcePoolLink = this.resourcePool.documentSelfLink;
        networkState.name = nextName("pg");

        CustomProperties.of(networkState)
                .put(DvsProperties.PARENT_DVS_LINK, dvsSwitch.documentSelfLink);

        networkState = TestUtils.doPost(this.host, networkState, NetworkState.class,
                UriUtils.buildUri(this.host, NetworkService.FACTORY_LINK));

        ProvisionNetworkTaskState createTask = new ProvisionNetworkTaskState();
        createTask.isMockRequest = isMock();
        createTask.requestType = InstanceRequestType.CREATE;
        createTask.networkDescriptionLink = networkState.documentSelfLink;

        createTask = TestUtils.doPost(this.host,
                createTask,
                ProvisionNetworkTaskState.class,
                UriUtils.buildUri(this.host,
                        ProvisionNetworkTaskService.FACTORY_LINK));

        awaitTaskEnd(createTask);

        // refresh state
        networkState = getNetworkState(networkState);

        // delete the portgroup
        deleteThePortgroup(networkState);
    }

    protected void deleteThePortgroup(NetworkState networkState) throws Throwable {
        ProvisionNetworkTaskState removalTask = new ProvisionNetworkTaskState();
        removalTask.isMockRequest = isMock();
        removalTask.requestType = InstanceRequestType.DELETE;
        removalTask.networkDescriptionLink = networkState.documentSelfLink;

        removalTask = TestUtils.doPost(this.host,
                removalTask,
                ProvisionNetworkTaskState.class,
                UriUtils.buildUri(this.host,
                        ProvisionNetworkTaskService.FACTORY_LINK));
        awaitTaskEnd(removalTask);
    }

    private NetworkState getNetwork(NetworkState networkState) {
        return this.host.getServiceState(null, NetworkState.class,
                UriUtils.buildUri(this.host, networkState.documentSelfLink));

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
        ResourceEnumerationTaskState task = new ResourceEnumerationTaskState();
        task.adapterManagementReference = this.computeHost.adapterManagementReference;

        if (isMock()) {
            task.options = EnumSet.of(TaskOption.IS_MOCK);
        }

        task.enumerationAction = EnumerationAction.REFRESH;
        task.parentComputeLink = this.computeHost.documentSelfLink;
        task.resourcePoolLink = this.resourcePool.documentSelfLink;

        ResourceEnumerationTaskState outTask = TestUtils.doPost(this.host,
                task,
                ResourceEnumerationTaskState.class,
                UriUtils.buildUri(this.host,
                        ResourceEnumerationTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(ResourceEnumerationTaskState.class, outTask.documentSelfLink);
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