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

package com.vmware.photon.controller.model.adapters.azure.d2o;

import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.DEFAULT_NIC_SPEC;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultAuthCredentials;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultComputeHost;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultEndpointState;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultResourcePool;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultVMResource;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.deleteVMs;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.generateName;

import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;

import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;

import com.microsoft.azure.management.compute.implementation.ComputeManagementClientImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapterapi.ResourceOperationResponse;
import com.vmware.photon.controller.model.adapters.azure.AzureAdapters;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperation;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationRequest;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.TestUtils;

import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class AzureLifecycleOperationServiceTest extends BasicReusableHostTestCase {

    // SHARED Compute Host / End-point between test runs. {{
    private static ComputeState computeHost;
    private static EndpointState endpointState;
    // Every test in addition might change it.
    private static String azureVMName = generateName("testPower-");
    // }}

    public String clientID = "clientID";
    public String clientKey = "clientKey";
    public String subscriptionId = "subscriptionId";
    public String tenantId = "tenantId";

    public boolean isMock = true;

    private ComputeManagementClientImpl computeManagementClient;
    private ComputeState vmState;

    @Rule
    public TestName currentTestName = new TestName();

    @Before
    public void setUp() throws Exception {
        try {
            /*
             * Init Class-specific (shared between test runs) vars.
             *
             * NOTE: Ultimately this should go to @BeforeClass, BUT BasicReusableHostTestCase.HOST
             * is not accessible.
             */
            if (computeHost == null) {
                PhotonModelServices.startServices(this.host);
                PhotonModelAdaptersRegistryAdapters.startServices(this.host);
                PhotonModelMetricServices.startServices(this.host);
                PhotonModelTaskServices.startServices(this.host);
                AzureAdapters.startServices(this.host);

                this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
                this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
                this.host.waitForServiceAvailable(PhotonModelAdaptersRegistryAdapters.LINKS);
                this.host.waitForServiceAvailable(AzureAdapters.LINKS);

                // TODO: VSYM-992 - improve test/fix arbitrary timeout
                this.host.setTimeoutSeconds(1200);

                // Create a resource pool where the VM will be housed
                ResourcePoolState resourcePool = createDefaultResourcePool(this.host);

                AuthCredentialsServiceState authCredentials = createDefaultAuthCredentials(
                        this.host,
                        this.clientID,
                        this.clientKey,
                        this.subscriptionId,
                        this.tenantId);

                endpointState = createDefaultEndpointState(
                        this.host, authCredentials.documentSelfLink);

                // create a compute host for the Azure
                computeHost = createDefaultComputeHost(this.host, resourcePool.documentSelfLink,
                        endpointState);
            }

            if (!this.isMock) {
                ApplicationTokenCredentials credentials = new ApplicationTokenCredentials(
                        this.clientID, this.tenantId, this.clientKey, AzureEnvironment.AZURE);
                this.computeManagementClient = new ComputeManagementClientImpl(credentials)
                        .withSubscriptionId(this.subscriptionId);
            }

        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    @After
    public void tearDown() throws Exception {
        if (this.vmState != null) {
            try {
                this.host.log(Level.INFO, "%s: Deleting [%s] VM",
                        this.currentTestName.getMethodName(), this.vmState.name);

                // ONLY computeHost MUST remain!
                int computeStatesToRemain = 1;

                deleteVMs(this.host, this.vmState.documentSelfLink, this.isMock,
                        computeStatesToRemain);

            } catch (Throwable deleteExc) {
                // just log and move on
                this.host.log(Level.WARNING, "%s: Deleting [%s] VM: FAILED. Details: %s",
                        this.currentTestName.getMethodName(), this.vmState.name,
                        deleteExc.getMessage());
            }
        }
    }

    @Test
    @Ignore("Azure provision and decommission is taking significant amount of time"
            + "and could potentially delay the pre-flights or cause timeouts. Suitable for manual execution.")
    public void test() throws Throwable {

        this.vmState = createDefaultVMResource(this.host, azureVMName,
                computeHost, endpointState, DEFAULT_NIC_SPEC);

        kickOffProvisionTask();

        triggerRestart();

        triggerSuspend();
    }

    private void triggerRestart() {
        String taskLink = UUID.randomUUID().toString();

        ResourceOperationRequest request = new ResourceOperationRequest();
        request.isMockRequest = this.isMock;
        request.operation = ResourceOperation.RESTART.operation;
        request.resourceReference = UriUtils.buildUri(this.host, this.vmState.documentSelfLink);
        request.taskReference = UriUtils.buildUri(this.host, taskLink);
        TestContext ctx = this.host.testCreate(2);
        createTaskResultListener(this.host, taskLink, (u) -> {
            if (u.getAction() != Action.PATCH) {
                return false;
            }
            ResourceOperationResponse response = u.getBody(ResourceOperationResponse.class);
            if (TaskState.isFailed(response.taskInfo)) {
                ctx.failIteration(
                        new IllegalStateException(response.taskInfo.failure.message));
            } else if (TaskState.isFinished(response.taskInfo)) {
                ctx.completeIteration();
            }
            return true;
        });

        Operation restartOp = Operation
                .createPatch(UriUtils.buildUri(this.host, AzureLifecycleOperationService.SELF_LINK))
                .setBody(request)
                .setReferer("/tests")
                .setCompletion((o, e) -> {
                    if (e != null) {
                        ctx.failIteration(e);
                        return;
                    }
                    ctx.completeIteration();
                });
        this.host.send(restartOp);
        ctx.await();
    }

    private void triggerSuspend() {
        String taskLink = UUID.randomUUID().toString();

        ResourceOperationRequest request = new ResourceOperationRequest();
        request.isMockRequest = this.isMock;
        request.operation = ResourceOperation.SUSPEND.operation;
        request.resourceReference = UriUtils.buildUri(this.host, this.vmState.documentSelfLink);
        request.taskReference = UriUtils.buildUri(this.host, taskLink);
        TestContext ctx = this.host.testCreate(2);
        createTaskResultListener(this.host, taskLink, (u) -> {
            if (u.getAction() != Action.PATCH) {
                return false;
            }
            ResourceOperationResponse response = u.getBody(ResourceOperationResponse.class);
            if (TaskState.isFailed(response.taskInfo)) {
                ctx.failIteration(
                        new IllegalStateException(response.taskInfo.failure.message));
            } else if (TaskState.isFinished(response.taskInfo)) {
                ctx.completeIteration();
            }
            return true;
        });

        Operation restartOp = Operation
                .createPatch(UriUtils.buildUri(this.host, AzureLifecycleOperationService.SELF_LINK))
                .setBody(request)
                .setReferer("/tests2")
                .setCompletion((o, e) -> {
                    if (e != null) {
                        ctx.failIteration(e);
                        return;
                    }
                    ctx.completeIteration();
                });
        this.host.send(restartOp);
        ctx.await();
    }

    private void kickOffProvisionTask() throws Throwable {

        ProvisionComputeTaskState provisionTask = new ProvisionComputeTaskState();

        provisionTask.computeLink = this.vmState.documentSelfLink;
        provisionTask.isMockRequest = this.isMock;
        provisionTask.taskSubStage = ProvisionComputeTaskState.SubStage.CREATING_HOST;

        provisionTask = TestUtils.doPost(this.host,
                provisionTask,
                ProvisionComputeTaskState.class,
                UriUtils.buildUri(this.host, ProvisionComputeTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(
                ProvisionComputeTaskState.class,
                provisionTask.documentSelfLink);
    }

    private void createTaskResultListener(VerificationHost host, String taskLink,
            Function<Operation, Boolean> h) {
        StatelessService service = new StatelessService() {
            @Override
            public void handleRequest(Operation update) {
                if (!h.apply(update)) {
                    super.handleRequest(update);
                }
            }
        };

        TestContext ctx = this.host.testCreate(1);
        Operation startOp = Operation
                .createPost(host, taskLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        ctx.failIteration(e);
                        return;
                    }
                    ctx.completeIteration();
                })
                .setReferer(this.host.getReferer());
        this.host.startService(startOp, service);
        ctx.await();
    }

}
