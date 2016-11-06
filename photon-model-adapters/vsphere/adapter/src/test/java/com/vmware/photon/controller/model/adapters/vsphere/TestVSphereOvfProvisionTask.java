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

import java.net.URI;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapters.vsphere.ovf.ImportOvfRequest;
import com.vmware.photon.controller.model.adapters.vsphere.ovf.OvfImporterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
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

    /**
     * create a "photon-model" user with password 123456 for quick tests
     */
    private static final String COREOS_CONFIG_DATA =
            "#cloud-config\n"
                    + "\n"
                    + "users:\n"
                    + "  - name: \"photon-model\"\n"
                    + "    passwd: \"$1$NVdKo9MI$PlHuc3YsufCHbP1Hh9TMz/\"\n"
                    + "    groups:\n"
                    + "      - \"sudo\"\n"
                    + "      - \"docker\"";

    private URI ovfUri = getOvfUri();

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
        f.get(30, TimeUnit.SECONDS);

        snapshotFactoryState("ovf", ComputeDescriptionService.class);

        enumerateComputes(this.computeHost);

        String descriptionLink = findFirstOvfDescriptionLink();

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

        QuerySpecification qs = new QuerySpecification();
        qs.query.addBooleanClause(
                Query.Builder.create()
                        .addFieldClause(ComputeState.FIELD_NAME_ID, "ovf-", MatchType.PREFIX)
                        .build());
        QueryTask qt = QueryTask.create(qs).setDirect(true);

        Operation op = Operation.createPost(UriUtils.buildUri(this.host, ServiceUriPaths.CORE_QUERY_TASKS))
                .setBody(qt);

        QueryTask result = this.host.sendWithFuture(op).thenApply(o -> o.getBody(QueryTask.class))
                .get(10, TimeUnit.SECONDS);

        return result.results.documentLinks.get(0);
    }

    private ComputeState createVmState(String descriptionLink) throws Throwable {
        ComputeState computeState = new ComputeState();
        computeState.id = computeState.name = "from-ovf-" + UUID.randomUUID();
        computeState.documentSelfLink = computeState.id;
        computeState.descriptionLink = descriptionLink;
        computeState.resourcePoolLink = this.resourcePool.documentSelfLink;
        computeState.adapterManagementReference = getAdapterManagementReference();

        computeState.powerState = PowerState.ON;

        computeState.parentLink = this.computeHost.documentSelfLink;

        Query q = createQueryForComputeResource();

        CustomProperties.of(computeState)
                .put(ComputeProperties.RESOURCE_GROUP_NAME, this.vcFolder)
                .put(ComputeProperties.PLACEMENT_LINK, findFirstMatching(q, ComputeState.class).documentSelfLink)
                .put("ovf.prop:guestinfo.coreos.config.data", COREOS_CONFIG_DATA);

        ComputeService.ComputeState returnState = TestUtils.doPost(this.host, computeState,
                ComputeService.ComputeState.class,
                UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
        return returnState;
    }

    private ComputeDescription createTemplate() {
        ComputeDescription computeDesc = new ComputeDescription();
        computeDesc.supportedChildren = new ArrayList<>();
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
