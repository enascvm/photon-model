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

package com.vmware.photon.controller.model.adapters.awsadapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.REGION_KEY;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.zoneId;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.Function;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapterapi.ComputePowerRequest;
import com.vmware.photon.controller.model.adapterapi.ResourceOperationResponse;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService.EndpointAllocationTaskState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService.ResourceRemovalTaskState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;

public class AWSPowerServiceTest extends BasicReusableHostTestCase {
    private static final int DEFAULT_TIMOUT_SECONDS = 200;

    public String secretKey = "test123";
    public String accessKey = "blas123";
    private String regionId = "us-east-1";
    private int timeoutSeconds = DEFAULT_TIMOUT_SECONDS;
    public boolean isMock = true;

    private ArrayList<String> computesToRemove = new ArrayList<>();

    @Before
    public void setUp() throws Throwable {
        CommandLineArgumentParser.parseFromProperties(this);

        try {
            PhotonModelServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            AWSAdapters.startServices(this.host);

            this.host.setTimeoutSeconds(this.timeoutSeconds);

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
            this.host.waitForServiceAvailable(AWSAdapters.LINKS);
        } catch (Throwable e) {
            this.host.log("Error starting up services for the test %s", e.getMessage());
            throw new Exception(e);
        }

    }

    @After
    public void tearDown() throws Throwable {

        Query resourceQuery = Query.Builder.create().addKindFieldClause(ComputeState.class)
                .addInClause(ComputeState.FIELD_NAME_SELF_LINK, this.computesToRemove).build();
        QuerySpecification qSpec = new QuerySpecification();
        qSpec.query = resourceQuery;
        ResourceRemovalTaskState state = new ResourceRemovalTaskState();
        state.isMockRequest = this.isMock;
        state.resourceQuerySpec = qSpec;

        ResourceRemovalTaskState removalTaskState = TestUtils.doPost(this.host, state,
                ResourceRemovalTaskState.class,
                UriUtils.buildUri(this.host, ResourceRemovalTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(ResourceRemovalTaskState.class,
                removalTaskState.documentSelfLink);
    }

    @Test
    public void test() throws Throwable {
        EndpointState endpoint = configureEndpoint();

        assertNotNull(endpoint.resourcePoolLink);

        ComputeDescription cd = this.host.getServiceState(null, ComputeDescription.class,
                UriUtils.buildUri(this.host, endpoint.computeDescriptionLink));
        assertNotNull(cd);
        assertNotNull("Power addpter must be configured", cd.powerAdapterReference);

        boolean addNonExistingSecurityGroup = false;
        ComputeState cs = TestAWSSetupUtils.createAWSVMResource(this.host, endpoint.computeLink,
                endpoint.resourcePoolLink, getClass(),
                "trainingVM",zoneId, zoneId, null, TestAWSSetupUtils.SINGLE_NIC_SPEC, addNonExistingSecurityGroup);

        this.computesToRemove.add(cs.documentSelfLink);
        assertEquals(PowerState.UNKNOWN, cs.powerState);

        ProvisionComputeTaskState state = new ProvisionComputeTaskState();
        state.computeLink = cs.documentSelfLink;
        state.isMockRequest = this.isMock;
        state.taskSubStage = ProvisionComputeTaskState.SubStage.CREATING_HOST;

        state = TestUtils.doPost(this.host, state, ProvisionComputeTaskState.class,
                UriUtils.buildUri(this.host, ProvisionComputeTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(ProvisionComputeTaskState.class, state.documentSelfLink);

        ComputeState compute = this.host.getServiceState(null, ComputeState.class,
                UriUtils.buildUri(this.host, cs.documentSelfLink));

        changePowerState(cd, compute.documentSelfLink, PowerState.OFF);

        changePowerState(cd, compute.documentSelfLink, PowerState.ON);
    }

    private void changePowerState(ComputeDescription cd, String computeLink,
            PowerState powerState) {
        String taskLink = UUID.randomUUID().toString();

        ComputePowerRequest powerRequest = new ComputePowerRequest();
        powerRequest.isMockRequest = this.isMock;
        powerRequest.powerState = powerState;
        powerRequest.resourceReference = UriUtils.buildUri(this.host, computeLink);
        powerRequest.taskReference = UriUtils.buildUri(this.host, taskLink);

        TestContext ctx = this.host.testCreate(2);
        createTaskResultListener(this.host, taskLink, (u) -> {
            if (u.getAction() != Action.PATCH) {
                return false;
            }
            ResourceOperationResponse response = u.getBody(ResourceOperationResponse.class);
            if (TaskState.isFailed(response.taskInfo)) {
                ctx.failIteration(
                        new IllegalStateException(response.taskInfo.failure.message));
            } else {
                ctx.completeIteration();
            }
            return true;
        });
        Operation powerOp = Operation.createPatch(cd.powerAdapterReference)
                .setBody(powerRequest)
                .setReferer("/boza")
                .setCompletion((o, e) -> {
                    if (e != null) {
                        ctx.failIteration(e);
                        return;
                    }
                    ctx.completeIteration();
                });
        this.host.send(powerOp);
        ctx.await();

        ComputeState compute = this.host.getServiceState(null, ComputeState.class,
                UriUtils.buildUri(this.host, computeLink));
        assertEquals(powerState, compute.powerState);
    }

    private EndpointState configureEndpoint() throws Throwable {
        EndpointState ep = createEndpointState();

        EndpointAllocationTaskState configureEndpoint = new EndpointAllocationTaskState();
        configureEndpoint.endpointState = ep;
        configureEndpoint.options = this.isMock ? EnumSet.of(TaskOption.IS_MOCK) : null;
        configureEndpoint.taskInfo = new TaskState();
        configureEndpoint.taskInfo.isDirect = true;

        EndpointAllocationTaskState outTask = TestUtils.doPost(this.host, configureEndpoint,
                EndpointAllocationTaskState.class,
                UriUtils.buildUri(this.host, EndpointAllocationTaskService.FACTORY_LINK));

        // this.host.waitForFinishedTask(EndpointAllocationTaskState.class,
        // outTask.documentSelfLink);
        return outTask.endpointState;
    }

    private EndpointState createEndpointState() {
        EndpointState endpoint = new EndpointState();
        endpoint.endpointType = EndpointType.aws.name();
        endpoint.name = EndpointType.aws.name();
        endpoint.endpointProperties = new HashMap<>();
        endpoint.endpointProperties.put(REGION_KEY, this.regionId);
        endpoint.endpointProperties.put(PRIVATE_KEY_KEY, this.secretKey);
        endpoint.endpointProperties.put(PRIVATE_KEYID_KEY, this.accessKey);
        return endpoint;
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

        Operation startOp = Operation
                .createPost(host, taskLink)
                .setCompletion(this.host.getCompletion())
                .setReferer(this.host.getReferer());
        this.host.startService(startOp, service);

    }
}
