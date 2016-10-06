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
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Test;

import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService.ResourceEnumerationTaskState;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 *
 */
public class TestVSphereEnumerationTask extends BaseVSphereAdapterTest {

    // fields that are used across method calls, stash them as private fields
    private ResourcePoolState resourcePool;

    private AuthCredentialsServiceState auth;
    private ComputeDescription computeHostDescription;
    private ComputeState computeHost;

    @Test
    public void testRefresh() throws Throwable {
        // Create a resource pool where the VM will be housed
        this.resourcePool = createResourcePool();

        this.auth = createAuth();

        this.computeHostDescription = createComputeDescription();
        this.computeHost = createComputeHost();

        doRefresh();

        if (!isMock()) {
            ComputeState vm = findVm(5, 5000);
            assertInternalPropertiesSet(vm);
        }

        snapshotFactoryState("refresh", ComputeService.class);
        snapshotFactoryState("refresh", ComputeDescriptionService.class);
        snapshotFactoryState("refresh", ResourcePoolService.class);
        snapshotFactoryState("refresh", StorageDescriptionService.class);
        snapshotFactoryState("refresh", NetworkService.class);
        snapshotFactoryState("refresh", TagService.class);

        // do a second refresh to test update path
        doRefresh();
    }

    private ComputeState findVm(int retriesLeft, long sleepMillis)
            throws InterruptedException, TimeoutException, ExecutionException {
        QuerySpecification qs = new QuerySpecification();
        qs.options.add(QueryOption.EXPAND_CONTENT);

        qs.query.addBooleanClause(
                Query.Builder.create()
                        .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                                CustomProperties.TYPE, VimNames.TYPE_VM)
                        .addFieldClause(ServiceDocument.FIELD_NAME_KIND,
                                Utils.buildKind(ComputeState.class))
                        .build());
        QueryTask qt = QueryTask.create(qs).setDirect(true);

        Operation op = Operation
                .createPost(UriUtils.buildUri(this.host, ServiceUriPaths.CORE_QUERY_TASKS))
                .setBody(qt);

        QueryTask result = this.host.sendWithFuture(op).thenApply(o -> o.getBody(QueryTask.class))
                .get(10, TimeUnit.SECONDS);

        if (result.results.documents.isEmpty()) {
            if (retriesLeft == 0) {
                throw new IllegalStateException("Cannot find template within configured timeout");
            }
            Thread.sleep(sleepMillis);
            return findVm(retriesLeft - 1, sleepMillis);
        }

        return Utils
                .fromJson(result.results.documents.values().iterator().next(), ComputeState.class);
    }

    private void doRefresh() throws Throwable {
        ResourceEnumerationTaskState task = new ResourceEnumerationTaskState();
        task.adapterManagementReference = this.computeHost.adapterManagementReference;

        task.isMockRequest = isMock();
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
        computeState.adapterManagementReference = UriUtils.buildUri(this.vcUrl);

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
}
