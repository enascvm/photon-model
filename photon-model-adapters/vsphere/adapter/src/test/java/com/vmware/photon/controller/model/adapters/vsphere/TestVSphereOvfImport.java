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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.HashSet;

import org.junit.Ignore;
import org.junit.Test;

import com.vmware.photon.controller.model.adapters.vsphere.ovf.ImportOvfRequest;
import com.vmware.photon.controller.model.adapters.vsphere.ovf.OvfImporterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;

@Ignore
public class TestVSphereOvfImport extends BaseVSphereAdapterTest {

    private ComputeDescription computeHostDescription;

    @Test
    public void importOvfAsDescriptions() throws Throwable {
        this.resourcePool = createResourcePool();
        this.auth = createAuth();

        this.computeHostDescription = createComputeDescription();
        createComputeHost();

        ComputeDescription computeDesc = new ComputeDescription();
        computeDesc.supportedChildren = new HashSet<>();
        computeDesc.instanceAdapterReference = UriUtils
                .buildUri(this.host, VSphereUriPaths.INSTANCE_SERVICE);
        computeDesc.authCredentialsLink = this.auth.documentSelfLink;
        computeDesc.name = computeDesc.id;
        computeDesc.dataStoreId = this.dataStoreId;

        ImportOvfRequest req = new ImportOvfRequest();
        req.ovfUri = new File("src/test/resources/vcenter.ovf").toURI();
        req.template = computeDesc;

        Operation op = Operation.createPatch(this.host, OvfImporterService.SELF_LINK)
                .setBody(req)
                .setReferer(this.host.getPublicUri());

        op = this.host.waitForResponse(op);
        assertEquals(Operation.STATUS_CODE_OK, op.getStatusCode());

        Query q = Query.Builder.create()
                .addFieldClause(ComputeState.FIELD_NAME_ID, "ovf-", MatchType.PREFIX)
                .build();
        QueryTask task = QueryTask.Builder.createDirectTask()
                .setQuery(q)
                .build();

        op = Operation.createPost(UriUtils.buildUri(this.host, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
                .setBody(task);

        task = this.host.waitForResponse(op).getBody(QueryTask.class);

        assertTrue(task.results.documentLinks.size() > 5);

        snapshotFactoryState("ovf", ComputeDescriptionService.class);
    }

    /**
     * Create a compute host representing a vcenter server
     */
    private ComputeState createComputeHost() throws Throwable {
        ComputeState computeState = new ComputeState();
        computeState.id = nextName("vm");
        computeState.documentSelfLink = computeState.id;
        computeState.descriptionLink = this.computeHostDescription.documentSelfLink;
        computeState.resourcePoolLink = this.resourcePool.documentSelfLink;
        computeState.adapterManagementReference = getAdapterManagementReference();

        ComputeState returnState = TestUtils.doPost(this.host, computeState,
                ComputeState.class,
                UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
        return returnState;
    }

}
