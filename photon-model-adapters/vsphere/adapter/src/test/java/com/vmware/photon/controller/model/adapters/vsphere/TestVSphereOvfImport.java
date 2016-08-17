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

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.vmware.photon.controller.model.adapters.vsphere.ovf.ImportOvfRequest;
import com.vmware.photon.controller.model.adapters.vsphere.ovf.OvfImporterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;

public class TestVSphereOvfImport extends BaseVSphereAdapterTest {

    // fields that are used across method calls, stash them as private fields
    private ResourcePoolState resourcePool;

    private AuthCredentialsServiceState auth;
    private ComputeDescription computeHostDescription;
    private ComputeState computeHost;

    @Test
    public void importOvfAsDescriptions() throws Throwable {
        this.resourcePool = createResourcePool();
        this.auth = createAuth();

        this.computeHostDescription = createComputeDescription();
        this.computeHost = createComputeHost();

        ComputeDescription computeDesc = new ComputeDescription();
        computeDesc.supportedChildren = new ArrayList<>();
        computeDesc.instanceAdapterReference = UriUtils
                .buildUri(this.host, VSphereUriPaths.INSTANCE_SERVICE);
        computeDesc.authCredentialsLink = this.auth.documentSelfLink;
        computeDesc.name = computeDesc.id;
        computeDesc.dataStoreId = this.dataStoreId;
        computeDesc.networkId = this.networkId;

        ImportOvfRequest req = new ImportOvfRequest();
        req.ovfUri = new File("src/test/resources/vcenter.ovf").toURI();
        req.template = computeDesc;

        Operation op = Operation.createPatch(this.host, OvfImporterService.SELF_LINK)
                .setBody(req)
                .setReferer(this.host.getPublicUri());

        CompletableFuture<Operation> f = this.host.sendWithFuture(op);
        f.get(30, TimeUnit.SECONDS);

        QuerySpecification qs = new QuerySpecification();
        qs.query.addBooleanClause(
                Query.Builder.create()
                        .addFieldClause(ComputeState.FIELD_NAME_ID, "ovf-*", MatchType.WILDCARD)
                        .build());
        QueryTask qt = QueryTask.create(qs).setDirect(true);

        op = Operation.createPost(UriUtils.buildUri(this.host, ServiceUriPaths.CORE_QUERY_TASKS))
                .setBody(qt);

        QueryTask result = this.host.sendWithFuture(op).thenApply(o -> o.getBody(QueryTask.class))
                .get(10, TimeUnit.SECONDS);

        assertTrue(result.results.documentLinks.size() > 5);

        snapshotFactoryState("ovf", ComputeDescriptionService.class);
    }

    private ComputeDescription createComputeDescription() throws Throwable {
        ComputeDescription computeDesc = new ComputeDescription();

        computeDesc.id = UUID.randomUUID().toString();
        computeDesc.documentSelfLink = computeDesc.id;
        computeDesc.supportedChildren = new ArrayList<>();
        computeDesc.supportedChildren.add(ComputeType.VM_GUEST.name());
        computeDesc.instanceAdapterReference = UriUtils
                .buildUri(this.host, VSphereUriPaths.INSTANCE_SERVICE);
        computeDesc.authCredentialsLink = this.auth.documentSelfLink;

        computeDesc.zoneId = this.zoneId;

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
        computeState.documentSelfLink = computeState.id;
        computeState.descriptionLink = this.computeHostDescription.documentSelfLink;
        computeState.resourcePoolLink = this.resourcePool.documentSelfLink;
        computeState.adapterManagementReference = UriUtils.buildUri(this.vcUrl);

        ComputeState returnState = TestUtils.doPost(this.host, computeState,
                ComputeState.class,
                UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
        return returnState;
    }

}