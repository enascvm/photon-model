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
import static com.vmware.photon.controller.model.adapters.gcp.GCPTestUtil.createDefaultResourceGroup;
import static com.vmware.photon.controller.model.adapters.gcp.GCPTestUtil.createDefaultResourcePool;
import static com.vmware.photon.controller.model.adapters.gcp.GCPTestUtil.deleteDocument;
import static com.vmware.photon.controller.model.adapters.gcp.GCPTestUtil.performResourceEnumeration;
import static com.vmware.photon.controller.model.tasks.TestUtils.createResourceEnumerationTask;

import java.util.Map.Entry;
import java.util.UUID;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse;
import com.vmware.photon.controller.model.adapters.gcp.GCPAdapters;
import com.vmware.photon.controller.model.adapters.gcp.GCPUriPaths;
import com.vmware.photon.controller.model.adapters.gcp.enumeration.GCPEnumerationAdapterService;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService.ResourceEnumerationTaskState;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Test to check stats collection on GCP VMs.
 * Due to latency involved in metrics becoming available after creation of a VM, a default test
 * VM has been created. The test first does enumeration. After that, it collects stats on the
 * enumerated test VM.
 * The test will by default run in mock mode.
 *
 * To actually run the test, please change the field isMock to false and specify the credentials
 * in system properties in command line. Here is an example:
 * mvn test -pl photon-model-adapters/gcp -Dtest=TestGCPStatsCollection -Dxenon.userEmail="xxx"
 * -Dxenon.privateKey="yyy" -Dxenon.projectID="zzz" -Dxenon.zoneID="ooo" -Dxenon.instanceID="www"
 * -Dxenon.initialNumberOfVms=123
 *
 * Ensure that the zoneID provided and the zone ID for the test VM (corresponding to the
 * instanceID provided) are same, as the zone ID for default compute host being created must be
 * the same as zone ID of the VM for the enumeration to be successful.
 *
 * The value of initialNumberOfVms parameter must be number of VMs existing in the given
 * project + 1.
 */
public class TestGCPStatsCollection extends BaseModelTest {
    public boolean isMock = true;
    public String userEmail = "userEmail";
    public String privateKey = "privateKey";
    public String projectID = "projectID";
    public String zoneID = "zoneID";
    public String instanceID = "instanceID";
    public int initialNumberOfVms = 0;

    // Fields that are used across method calls, stash them as private fields
    private ResourcePoolState outPool;
    private ComputeService.ComputeState computeHost;
    private ComputeService.ComputeState vmState;
    private String enumeratedComputeLink;
    private String enumeratedComputeParentLink;

    /**
     * Do some preparation before running the test. It will generate a random default VM name,
     * start the required services, create a default resource pool, compute host and VM. Then it
     * will wait until every service is ready to start.
     * @throws Throwable Exception during preparation.
     */
    @Before
    public void setUp() throws Throwable {
        CommandLineArgumentParser.parseFromProperties(this.host);
        PhotonModelServices.startServices(this.host);
        PhotonModelTaskServices.startServices(this.host);
        GCPAdapters.startServices(this.host);
        this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
        this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
        this.host.waitForServiceAvailable(GCPAdapters.LINKS);

        if (!this.isMock) {
            this.privateKey = this.privateKey.replaceAll("\\\\n", "\n");
        }

        // Create the compute host and resource pool state to be used in the test.
        createResourcePoolAndComputeHostState();
    }

    /**
     * Do some clean up after enumeration test. It will delete the default VM
     * as well as compute host.
     * @throws Throwable Exception during clean up.
     */
    @After
    public void tearDown() throws Throwable {
        // try to delete the VM
        if (this.vmState != null) {
            try {
                deleteDocument(this.host, this.vmState.documentSelfLink);
            } catch (Throwable deleteEx) {
                // just log and move on
                host.log(Level.WARNING, "Exception deleting VM - %s", deleteEx.getMessage());
            }
        }

        if (this.computeHost != null) {
            try {
                deleteDocument(this.host, this.computeHost.documentSelfLink);
            } catch (Throwable deleteEx) {
                host.log(Level.WARNING, "Exception deleting VM - %s", deleteEx.getMessage());
            }
        }
    }

    /**
     * The main flow of the test case. It will first run enumeration. After that, instance level
     * stats collection is done on the test VM, whose instance ID is specified as a constant.
     * Then, project level stats collection is done on the project to which the test VM belongs.
     * @throws Throwable
     */
    @Test
    public void testStatsCollection() throws Throwable {
        if (this.isMock) {
            this.host.waitFor("Error waiting for stats", () -> {
                try {
                    issueStatsRequest(this.computeHost.documentSelfLink);
                    return true;
                } catch (Throwable t) {
                    return false;
                }
            });
            return;
        }

        // Run enumeration
        runEnumeration();

        ServiceDocumentQueryResult result = ProvisioningUtils.queryComputeInstances(host,
                this.initialNumberOfVms);

        for (Entry<String, Object> key : result.documents.entrySet()) {
            ComputeState document = Utils.fromJson(key.getValue(), ComputeState.class);
            if (document.id.equals(this.instanceID)) {
                this.enumeratedComputeLink = document.documentSelfLink;
                this.enumeratedComputeParentLink = document.parentLink;
                break;
            }
        }

        // Run VM level stats collection
        host.log(Level.INFO, "Retrieveing VM level stats...");
        this.host.waitFor("Error waiting for stats", () -> {
            try {
                issueStatsRequest(this.enumeratedComputeLink);
                return true;
            } catch (Throwable t) {
                return false;
            }
        });

        // Run project level stats collection
        host.log(Level.INFO, "Retrieveing host level stats...");
        this.host.waitFor("Error waiting for stats", () -> {
            try {
                issueStatsRequest(this.enumeratedComputeParentLink);
                return true;
            } catch (Throwable t) {
                return false;
            }
        });
    }

    /**
     * Creates a stateless service which will call GCPStatsService to collect stats of the
     * resource specified by the argument.
     * Receives response back and patches it to the caller service.
     * @param selfLink The self link to the document of enumerated resource.
     * @throws Throwable
     */
    public void issueStatsRequest(String selfLink) throws Throwable {
        // Spin up a stateless service that acts as the parent link to patch back to
        StatelessService parentService = new StatelessService() {
            @Override
            public void handleRequest(Operation op) {
                if (op.getAction() == Action.PATCH) {
                    if (!TestGCPStatsCollection.this.isMock) {
                        ComputeStatsResponse resp = op.getBody(ComputeStatsResponse.class);
                        if (resp == null) {
                            TestGCPStatsCollection.this.host.failIteration(
                                    new IllegalStateException("response was null."));
                            return;
                        }
                        if (resp.statsList.size() != 1) {
                            TestGCPStatsCollection.this.host.failIteration(
                                    new IllegalStateException("response size was incorrect."));
                            return;
                        }
                        if (resp.statsList.get(0).statValues.size() != 9) {
                            TestGCPStatsCollection.this.host
                                    .failIteration(new IllegalStateException(
                                            "incorrect number of metrics received."));
                            return;
                        }
                        if (!resp.statsList.get(0).computeLink.equals(selfLink)) {
                            TestGCPStatsCollection.this.host
                                    .failIteration(new IllegalStateException(
                                            "Incorrect resourceReference returned."));
                            return;
                        }
                    }
                    TestGCPStatsCollection.this.host.completeIteration();
                }
            }
        };
        String servicePath = UUID.randomUUID().toString();
        Operation startOp = Operation.createPost(UriUtils.buildUri(this.host, servicePath));
        this.host.startService(startOp, parentService);
        ComputeStatsRequest statsRequest = new ComputeStatsRequest();
        statsRequest.resourceReference = UriUtils.buildUri(this.host, selfLink);
        statsRequest.isMockRequest = this.isMock;
        statsRequest.taskReference = UriUtils.buildUri(this.host, servicePath);
        this.host.sendAndWait(Operation.createPatch(UriUtils.buildUri(
                this.host, GCPUriPaths.GCP_STATS_ADAPTER))
                .setBody(statsRequest)
                .setReferer(this.host.getUri()));
    }

    /**
     * Run the enumeration and wait until it ends.
     * @throws Throwable Exception during running and waiting enumeration.
     */
    private void runEnumeration() throws Throwable {
        ResourceEnumerationTaskState enumTask = createResourceEnumerationTask(this.outPool.documentSelfLink,
                this.computeHost.documentSelfLink, GCPEnumerationAdapterService.SELF_LINK,
                this.isMock, this.computeHost.tenantLinks);
        ResourceEnumerationTaskState enumTaskState = performResourceEnumeration(this.host, null, enumTask);
        this.host.waitForFinishedTask(ResourceEnumerationTaskState.class, enumTaskState.documentSelfLink);
    }

    /**
     * Creates the state associated with the resource pool and compute host.
     * @throws Throwable Exception during creation of default resource pool and compute host.
     */
    private void createResourcePoolAndComputeHostState() throws Throwable {
        // Create a resource pool where the VM will be housed.
        this.outPool = createDefaultResourcePool(this.host);

        // Create a resource group for the GCP project.
        ResourceGroupState resourceGroup = createDefaultResourceGroup(this.host, this.projectID);

        // Create a compute host for the GCP VM.
        this.computeHost = createDefaultComputeHost(this.host, this.userEmail, this.privateKey, this.zoneID,
                this.outPool.documentSelfLink, resourceGroup.documentSelfLink);
    }
}