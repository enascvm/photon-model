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

package com.vmware.photon.controller.model.adapters.gcp.enumeration;

import static com.vmware.photon.controller.model.adapters.gcp.GCPTestUtil.createDefaultComputeHost;
import static com.vmware.photon.controller.model.adapters.gcp.GCPTestUtil.createDefaultResourceGroup;
import static com.vmware.photon.controller.model.adapters.gcp.GCPTestUtil.createDefaultResourcePool;
import static com.vmware.photon.controller.model.adapters.gcp.GCPTestUtil.deleteDocument;
import static com.vmware.photon.controller.model.adapters.gcp.GCPTestUtil.deleteInstances;
import static com.vmware.photon.controller.model.adapters.gcp.GCPTestUtil.enumerateResources;
import static com.vmware.photon.controller.model.adapters.gcp.GCPTestUtil.getGoogleComputeClient;
import static com.vmware.photon.controller.model.adapters.gcp.GCPTestUtil.getInstanceNumber;
import static com.vmware.photon.controller.model.adapters.gcp.GCPTestUtil.provisionInstances;
import static com.vmware.photon.controller.model.adapters.gcp.GCPTestUtil.stopInstances;
import static com.vmware.photon.controller.model.tasks.TestUtils.createResourceEnumerationTask;

import java.util.Collections;
import java.util.List;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeScopes;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapters.gcp.GCPAdapters;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService.ResourceEnumerationTaskState;
import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.CommandLineArgumentParser;

/**
 * Minimally the userEmail, privateKey, projectId and zone for GCP
 * must be specified to run the test and the isMock flag needs to
 * be turned OFF.
 */
public class TestGCPEnumerationAtScale extends BasicReusableHostTestCase {

    private static final String APPLICATION_NAME = "enumeration-scale-test";
    private static final String TEST_CASE_BASELINE_VMS = "Baseline VMs on GCP ";
    private static final String TEST_CASE_INITIAL_RUN_AT_SCALE = "Initial Run at Scale ";
    private static final String TEST_CASE_DISCOVER_UPDATES_AT_SCALE = "Discover Updates at Scale ";
    private static final String TEST_CASE_DISCOVER_DELETES_AT_SCALE = "Discover Deletes at Scale ";
    private static final int TIME_OUT_SECONDS = 1200;
    private static final float HUNDRED = 100.0f;

    public boolean isMock = true;
    public String userEmail = "userEmail";
    public String privateKey = "privateKey";
    public String projectID = "projectID";
    public String zoneID = "zoneID";
    public int gcpAccountLimit = 20;
    public int instanceCountAtScale = 1000;
    public int batchSize = 20;
    public long waitIntervalInMillisecond = 1000;
    public int modifyRate = 10;

    private ResourcePoolState outPool;
    private ComputeState computeHost;
    private Compute compute;
    private List<String> instancesToCleanUp;

    @Before
    public void setUp() throws Throwable {
        CommandLineArgumentParser.parseFromProperties(this);

        // TODO: VSYM-1523 - make this test support multiple nodes and user accounts.
        PhotonModelServices.startServices(this.host);
        PhotonModelTaskServices.startServices(this.host);
        GCPAdapters.startServices(this.host);
        this.host.setTimeoutSeconds(TIME_OUT_SECONDS);
        this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
        this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
        this.host.waitForServiceAvailable(GCPAdapters.LINKS);

        if (!this.isMock) {
            // If you copy and paste private key to the system properties, the new line
            // character will remain plain text.
            this.privateKey = this.privateKey.replaceAll("\\\\n", "\n");
            this.compute = getGoogleComputeClient(this.userEmail, this.privateKey,
                    Collections.singletonList(ComputeScopes.CLOUD_PLATFORM), APPLICATION_NAME);
        }

        // Create a resource pool and compute host to be used in the test.
        createResourcePoolAndComputeHost();
    }

    @After
    public void tearDown() throws Throwable {
        if (this.host == null) {
            return;
        }
        if (this.computeHost != null) {
            deleteDocument(this.host, this.computeHost.documentSelfLink);
        }
        if (this.outPool != null) {
            deleteDocument(this.host, this.outPool.documentSelfLink);
        }
        if (!this.isMock) {
            deleteInstances(this.host, this.compute, this.projectID, this.zoneID, this.instancesToCleanUp,
                    this.batchSize, this.waitIntervalInMillisecond);
        }
    }

    @Test
    public void testEnumerationAtScale() throws Throwable {
        if (!this.isMock) {
            int baseLineInstancesNumber = getInstanceNumber(this.compute, this.projectID, this.zoneID);
            // Check input here.
            if (baseLineInstancesNumber >= this.gcpAccountLimit) {
                this.host.log("The number of existing instances is already larger than or equal to" +
                        "the account limit.");
                return;
            }
            if (this.modifyRate > 100) {
                this.host.log("The modify Rate cannot be over 100 percent");
                return;
            }

            ResourceEnumerationTaskState enumTask = createResourceEnumerationTask(this.outPool.documentSelfLink,
                    this.computeHost.descriptionLink, this.computeHost.documentSelfLink,
                    GCPEnumerationAdapterService.SELF_LINK, this.isMock, this.computeHost.tenantLinks);
            enumerateResources(this.host, null, enumTask, TEST_CASE_BASELINE_VMS);

            if (this.instanceCountAtScale + baseLineInstancesNumber > this.gcpAccountLimit) {
                this.host.log("Requested number of resources will exceed account limit. Reducing" +
                        " number of requested instances");
                this.instanceCountAtScale = this.gcpAccountLimit - baseLineInstancesNumber;
            }

            // Create {instanceCountAtScale} VMs on AWS
            this.host.log("Running scale test by provisioning %d instances", this.instanceCountAtScale);
            this.instancesToCleanUp = provisionInstances(this.host, this.compute, this.userEmail,
                    this.projectID, this.zoneID, this.instanceCountAtScale, this.batchSize,
                    this.waitIntervalInMillisecond);
            // Record the time taken to discover creates of the instances.
            enumerateResources(this.host, null, enumTask, TEST_CASE_INITIAL_RUN_AT_SCALE);

            // UPDATE some percent of the spawned instances to have a tag
            int numberOfInstances = (int) ((this.instanceCountAtScale / HUNDRED) * this.modifyRate);
            this.host.log("Stopping %d instances", numberOfInstances);
            List<String> instanceNames = this.instancesToCleanUp.subList(0, numberOfInstances);
            this.instancesToCleanUp = this.instancesToCleanUp.subList(numberOfInstances,
                    this.instancesToCleanUp.size());
            stopInstances(this.host, this.compute, this.projectID, this.zoneID, instanceNames,
                    this.batchSize, this.waitIntervalInMillisecond);
            // Record the time taken to discover updates to a subset of the instances.
            enumerateResources(this.host, null, enumTask, TEST_CASE_DISCOVER_UPDATES_AT_SCALE);

            // DELETE some percent of the instances
            this.host.log("Deleting %d instances", numberOfInstances);
            List<String> instancesLeft = deleteInstances(this.host, this.compute, this.projectID, this.zoneID,
                    instanceNames, this.batchSize, this.waitIntervalInMillisecond);
            this.instancesToCleanUp.addAll(instancesLeft);
            // Record time spent in enumeration to discover the deleted instances and delete them.
            enumerateResources(this.host, null, enumTask, TEST_CASE_DISCOVER_DELETES_AT_SCALE);
        }
    }

    private void createResourcePoolAndComputeHost() throws Throwable {
        // Create a resource pool where the VM will be housed.
        this.outPool = createDefaultResourcePool(this.host);

        // Create a resource group for the GCP project.
        ResourceGroupState resourceGroup = createDefaultResourceGroup(this.host, this.projectID);

        // Create a compute host for the GCP VM.
        this.computeHost = createDefaultComputeHost(this.host, this.userEmail, this.privateKey, this.zoneID,
                this.outPool.documentSelfLink, resourceGroup.documentSelfLink);
    }
}
