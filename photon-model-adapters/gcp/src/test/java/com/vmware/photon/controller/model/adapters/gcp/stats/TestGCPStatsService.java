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

package com.vmware.photon.controller.model.adapters.gcp.stats;

import static com.vmware.photon.controller.model.adapters.gcp.GCPTestUtil.createDefaultComputeHost;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapters.gcp.GCPAdapters;
import com.vmware.photon.controller.model.adapters.gcp.GCPTestUtil;
import com.vmware.photon.controller.model.adapters.gcp.GCPUriPaths;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;

/**
 * Initial test to check if flow of GCPStatsService is working
 */
public class TestGCPStatsService {
    VerificationHost host;
    ResourcePoolState outPool;
    ComputeService.ComputeState computeHost;
    ComputeService.ComputeState vmState;

    // Test constants
    String userEmail = "user_email";
    String privateKey = "private_key";
    String projectId = "project_id";
    String instanceName = "instance_name";
    String zoneId = "zone_id";

    @Before
    public void setUp() throws Exception {
        this.host = VerificationHost.create(0);
        // Start the required services
        try {
            this.host.setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS.toMicros(250));
            this.host.start();
            PhotonModelServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            GCPAdapters.startServices(this.host);
            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
            this.host.waitForServiceAvailable(GCPAdapters.LINKS);
        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    @Test
    public void testStatsCollection() throws Throwable {
        // Create resource group, compute host and VM resource
        this.outPool = GCPTestUtil.createDefaultResourcePool(this.host);
        ResourceGroupState rsg = GCPTestUtil.createDefaultResourceGroup(this.host,
                this.projectId);
        this.computeHost = createDefaultComputeHost(this.host, this.userEmail,
                this.privateKey, this.zoneId,
                this.outPool.documentSelfLink, rsg.documentSelfLink);
        this.vmState = GCPTestUtil.createDefaultVMResource(this.host,
                this.userEmail, this.privateKey, this.zoneId, this.instanceName,
                this.computeHost.documentSelfLink, rsg.documentSelfLink);
        this.host.waitFor("Error waiting for stats", () -> {
            try {
                issueStatsRequest(this.vmState);
            } catch (Throwable t) {
                return false;
            }
            return true;
        });
    }

    /**
     * Sends request to GCPStatsService
     * @param vm Compute resource state
     * @throws Throwable
     */
    public void issueStatsRequest(ComputeState vm) throws Throwable {
        // Spin up a stateless service that acts as the parent link to patch back to
        StatelessService parentService = new StatelessService() {
            @Override
            public void handleRequest(Operation op) {
                if (op.getAction() == Action.PATCH) {
                    TestGCPStatsService.this.host.completeIteration();
                }
            }
        };
        String servicePath = UUID.randomUUID().toString();
        Operation startOp = Operation.createPost(UriUtils.buildUri(this.host, servicePath));
        this.host.startService(startOp, parentService);
        ComputeStatsRequest statsRequest = new ComputeStatsRequest();
        statsRequest.resourceReference = UriUtils.buildUri(this.host, vm.documentSelfLink);
        statsRequest.taskReference = UriUtils.buildUri(this.host, servicePath);
        this.host.sendAndWait(Operation.createPatch(UriUtils.buildUri(
                this.host, GCPUriPaths.GCP_STATS_ADAPTER))
                .setBody(statsRequest)
                .setReferer(this.host.getUri()));
    }
}
