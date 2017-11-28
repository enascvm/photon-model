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

package com.vmware.photon.controller.model.tasks;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.ModelUtils;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.IPAddressService;
import com.vmware.photon.controller.model.resources.IPAddressService.IPAddressState.IPAddressStatus;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.SubnetRangeService;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.support.IPVersion;
import com.vmware.photon.controller.model.tasks.IPAddressAllocationTaskService.IPAddressAllocationTaskResult;
import com.vmware.photon.controller.model.tasks.IPAddressAllocationTaskService.IPAddressAllocationTaskState;
import com.vmware.photon.controller.model.util.IpHelper;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

@RunWith(IPAddressAllocationTaskServiceTest.class)
@Suite.SuiteClasses({ IPAddressAllocationTaskServiceTest.ConstructorTest.class,
        IPAddressAllocationTaskServiceTest.HandleStartTest.class,
        IPAddressAllocationTaskServiceTest.EndToEndTest.class })
public class IPAddressAllocationTaskServiceTest extends Suite {

    private static int IP_ALLOCATION_TASK_TIMEOUT_MS = 10000;
    private static int IP_ALLOCATION_TASK_STEP_MS = 500;

    public IPAddressAllocationTaskServiceTest(Class<?> klass,
            RunnerBuilder builder) throws InitializationError {
        super(klass, builder);
    }

    private static void startFactoryServices(BaseModelTest test) throws Throwable {
        PhotonModelTaskServices.startServices(test.getHost());
        MockAdapter.startFactories(test);

        test.getHost().startFactory(new SubnetRangeService());
        test.getHost().startFactory(new IPAddressService());
        test.getHost().startFactory(new IPAddressAllocationTaskService());
    }

    private static IPAddressAllocationTaskState createIpAddressAllocationTask(String subnetLink,
            String connectedResourceLink) {
        IPAddressAllocationTaskState allocationTask = new IPAddressAllocationTaskState();
        allocationTask.subnetLink = subnetLink;
        allocationTask.requestType = IPAddressAllocationTaskState.RequestType.ALLOCATE;
        allocationTask.connectedResourceToRequiredIpCountMap = new HashMap<>();
        allocationTask.connectedResourceToRequiredIpCountMap.put(connectedResourceLink, 1);
        return allocationTask;
    }

    private static IPAddressAllocationTaskState createIpAddressAllocationTask(String subnetLink,
            Map<String, Integer> connectedResourceToRequiredIpCountMap) {
        IPAddressAllocationTaskState allocationTask = new IPAddressAllocationTaskState();
        allocationTask.subnetLink = subnetLink;
        allocationTask.requestType = IPAddressAllocationTaskState.RequestType.ALLOCATE;
        allocationTask.connectedResourceToRequiredIpCountMap = connectedResourceToRequiredIpCountMap;
        return allocationTask;
    }

    private static IPAddressAllocationTaskState createIpAddressDeallocationTask(
            List<String> ipAddressResourceLinks) {

        IPAddressAllocationTaskState deallocationTask = new IPAddressAllocationTaskState();
        deallocationTask.requestType = IPAddressAllocationTaskState.RequestType.DEALLOCATE;
        deallocationTask.ipAddressLinks = ipAddressResourceLinks;
        return deallocationTask;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {

        private IPAddressAllocationTaskService ipAddressAllocationTaskService;

        @Before
        public void setUpTest() {
            this.ipAddressAllocationTaskService = new IPAddressAllocationTaskService();
        }

        @Test
        public void testServiceOptions() {

            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.INSTRUMENTATION);

            assertThat(this.ipAddressAllocationTaskService.getOptions(),
                    is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {

        @Override
        protected void startRequiredServices() throws Throwable {
            IPAddressAllocationTaskServiceTest.startFactoryServices(this);
            super.startRequiredServices();
        }

        @Test
        public void testMissingIPAddressAllocationState() throws Throwable {
            this.postServiceSynchronously(
                    IPAddressAllocationTaskService.FACTORY_LINK, null,
                    IPAddressAllocationTaskState.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testMissingAllocationType() throws Throwable {
            IPAddressAllocationTaskState startState = createIpAddressAllocationTask("whatever",
                    ComputeService.FACTORY_LINK + "/machine-1");
            startState.requestType = null;

            this.postServiceSynchronously(
                    IPAddressAllocationTaskService.FACTORY_LINK, startState,
                    IPAddressAllocationTaskState.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testMissingSubnetStateForAllocation() throws Throwable {
            IPAddressAllocationTaskState startState = createIpAddressAllocationTask(null,
                    ComputeService.FACTORY_LINK + "/machine-1");

            this.postServiceSynchronously(
                    IPAddressAllocationTaskService.FACTORY_LINK, startState,
                    IPAddressAllocationTaskState.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testMissingIPAddressResourceLinkForDeallocation() throws Throwable {
            IPAddressAllocationTaskState startState = createIpAddressDeallocationTask(null);

            this.postServiceSynchronously(
                    IPAddressAllocationTaskService.FACTORY_LINK, startState,
                    IPAddressAllocationTaskState.class,
                    IllegalArgumentException.class);
        }
    }

    /**
     * This class implements EndToEnd tests for {@link IPAddressAllocationTaskService}.
     */
    public static class EndToEndTest extends BaseModelTest {
        private EndpointService.EndpointState endpointState;
        private NetworkService.NetworkState networkState;
        private SubnetService.SubnetState subnetState;
        private SubnetRangeService.SubnetRangeState subnetRangeState1;
        private SubnetRangeService.SubnetRangeState subnetRangeState2;
        private IPAddressService.IPAddressState ipAddressState;
        private NetworkInterfaceService.NetworkInterfaceState networkInterfaceState1;
        private NetworkInterfaceService.NetworkInterfaceState networkInterfaceState2;
        private NetworkInterfaceService.NetworkInterfaceState networkInterfaceState3;

        private static String startIpInRange1 = "12.12.12.2";
        private static String endIpInRange1 = "12.12.12.4";
        private static String startIpInRange2 = "12.12.12.10";
        private static String endIpInRange2 = "12.12.12.12";
        private static String gatewayIp = "12.12.12.1";

        @Before
        public void setUp() throws Throwable {
            super.setUp();

            startFactoryServices(this);
            createValidStates();
        }

        @Test
        public void testAllocationLifeCycle() throws Throwable {

            //This service is used by other tests. Hence stop it first if it's already started.
            try {
                deleteServiceSynchronously(TestStatelessService.SELF_LINK);
            } catch (ServiceNotFoundException exception) {
                //do nothing
            }

            this.host.startService(new TestStatelessService(new DeferredResult<>()));
            this.host.waitForServiceAvailable(TestStatelessService.SELF_LINK);

            IPAddressAllocationTaskState allocationTask = createIpAddressAllocationTask(
                    this.subnetState.documentSelfLink,
                    ComputeService.FACTORY_LINK + "/machine-1");

            addFakeCallBack(allocationTask);

            IPAddressAllocationTaskState allocationTaskResult = performTask(allocationTask);

            assertNotNull(allocationTaskResult);
            String returnIp = allocationTaskResult.ipAddresses.get(0);
            assertTrue(startIpInRange1.equals(returnIp) || startIpInRange2.equals(returnIp));

            String assignedIPSubnetRangeLink = allocationTaskResult.subnetRangeLinks.get(0);
            assertTrue(this.subnetRangeState1.documentSelfLink.equals(assignedIPSubnetRangeLink)
                    || this.subnetRangeState2.documentSelfLink.equals(assignedIPSubnetRangeLink));
            assertEquals(this.subnetState.documentSelfLink, allocationTaskResult.subnetLink);

            // Next allocation should give a different ip address
            IPAddressAllocationTaskState secondAllocationTaskResult = performTask(allocationTask);
            assertNotNull(secondAllocationTaskResult);
            String returnIp2 = secondAllocationTaskResult.ipAddresses.get(0);
            assertFalse(returnIp2.equals(returnIp));
            String assignedIPSubnetRangeLink2 = secondAllocationTaskResult.subnetRangeLinks.get(0);
            assertTrue(this.subnetRangeState1.documentSelfLink.equals(assignedIPSubnetRangeLink2)
                    || this.subnetRangeState2.documentSelfLink.equals(assignedIPSubnetRangeLink2));
            assertEquals(this.subnetState.documentSelfLink, secondAllocationTaskResult.subnetLink);

            // At this point, if we de-allocate the first ip address and make the ip address available,
            // allocation should allocate the same ip address
            IPAddressAllocationTaskState deallocationTask = createIpAddressDeallocationTask(
                    allocationTaskResult.ipAddressLinks);

            addFakeCallBack(deallocationTask);

            IPAddressAllocationTaskState deallocationTaskResult = performTask(deallocationTask);
            assertNotNull(deallocationTaskResult);

            changeExistingIpAddressStatus(allocationTaskResult.ipAddressLinks,
                    IPAddressStatus.AVAILABLE);

            IPAddressAllocationTaskState allocationTaskAfterDeallocation = performTask(
                    allocationTask);
            assertNotNull(allocationTaskAfterDeallocation);
            String returnIp3 = allocationTaskAfterDeallocation.ipAddresses.get(0);
            assertTrue(startIpInRange1.equals(returnIp3) || startIpInRange2.equals(returnIp3));

            String assignedIPSubnetRangeLink3 = allocationTaskAfterDeallocation.subnetRangeLinks
                    .get(0);
            assertTrue(this.subnetRangeState1.documentSelfLink.equals(assignedIPSubnetRangeLink3)
                    || this.subnetRangeState2.documentSelfLink.equals(assignedIPSubnetRangeLink3));
            assertEquals(this.subnetState.documentSelfLink,
                    allocationTaskAfterDeallocation.subnetLink);
        }

        @Test
        public void testConcurrentIPTaskServiceAllocationWithNewIPAddresses() throws Throwable {
            // Now, one of the tasks should use available IP address and the other one should use
            // new IP Address.
            IPAddressAllocationTaskState allocationTask1 = createIpAddressAllocationTask(
                    this.subnetState.documentSelfLink,
                    ComputeService.FACTORY_LINK + "/machine-1");
            addFakeCallBack(allocationTask1);

            IPAddressAllocationTaskState allocationTask2 = createIpAddressAllocationTask(
                    this.subnetState.documentSelfLink,
                    ComputeService.FACTORY_LINK + "/machine-2");
            addFakeCallBack(allocationTask2);

            // postServicesSynchronously waits until post is completed, but not the task itself.
            // performTask waits until the task is completed, hence it was not used.
            IPAddressAllocationTaskState startedAllocationTask1 = postServiceSynchronously(
                    IPAddressAllocationTaskService.FACTORY_LINK,
                    allocationTask1, IPAddressAllocationTaskState.class);

            IPAddressAllocationTaskState startedAllocationTask2 = postServiceSynchronously(
                    IPAddressAllocationTaskService.FACTORY_LINK,
                    allocationTask2, IPAddressAllocationTaskState.class);

            IPAddressAllocationTaskState completedAllocationTask1 = waitForServiceState(
                    IPAddressAllocationTaskState.class,
                    startedAllocationTask1.documentSelfLink,
                    state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                            .ordinal());

            assertTrue(completedAllocationTask1.taskInfo.stage == TaskState.TaskStage.FINISHED);

            IPAddressAllocationTaskState completedAllocationTask2 = waitForServiceState(
                    IPAddressAllocationTaskState.class,
                    startedAllocationTask2.documentSelfLink,
                    state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                            .ordinal());

            assertTrue(completedAllocationTask2.taskInfo.stage == TaskState.TaskStage.FINISHED);

            assertNotEquals(completedAllocationTask1.ipAddresses.get(0),
                    completedAllocationTask2.ipAddresses.get(0));
        }

        @Test
        public void testConcurrentIPTaskServiceAllocation() throws Throwable {
            // First, make an IP Address available.
            createNewIpAddressResourceAsAvailable(startIpInRange1,
                    this.subnetRangeState1.documentSelfLink);

            // Now, one of the tasks should use available IP address and the other one should use
            // new IP Address.
            IPAddressAllocationTaskState allocationTask1 = createIpAddressAllocationTask(
                    this.subnetState.documentSelfLink,
                    ComputeService.FACTORY_LINK + "/machine-1");
            IPAddressAllocationTaskState allocationTask2 = createIpAddressAllocationTask(
                    this.subnetState.documentSelfLink,
                    ComputeService.FACTORY_LINK + "/machine-2");

            addFakeCallBack(allocationTask1);
            addFakeCallBack(allocationTask2);

            IPAddressAllocationTaskState startedAllocationTask1 = postServiceSynchronously(
                    IPAddressAllocationTaskService.FACTORY_LINK,
                    allocationTask1, IPAddressAllocationTaskState.class);

            IPAddressAllocationTaskState startedAllocationTask2 = postServiceSynchronously(
                    IPAddressAllocationTaskService.FACTORY_LINK,
                    allocationTask2, IPAddressAllocationTaskState.class);

            IPAddressAllocationTaskState completedAllocationTask1 = waitForServiceState(
                    IPAddressAllocationTaskState.class,
                    startedAllocationTask1.documentSelfLink,
                    state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                            .ordinal());
            assertTrue(completedAllocationTask1.taskInfo.stage == TaskState.TaskStage.FINISHED);

            IPAddressAllocationTaskState completedAllocationTask2 = waitForServiceState(
                    IPAddressAllocationTaskState.class,
                    startedAllocationTask2.documentSelfLink,
                    state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                            .ordinal());
            assertTrue(completedAllocationTask2.taskInfo.stage == TaskState.TaskStage.FINISHED);

            assertNotEquals(completedAllocationTask1.ipAddresses.get(0),
                    completedAllocationTask2.ipAddresses.get(0));



        }

        @Test
        public void testAllocationTaskServiceWithOneRangeExhausted() throws Throwable {

            /*
            * Two ip ranges 12.12.12.2 - 12.12.12.4  and 12.12.12.10 - 12.12.12.12
            * Manually exhaust the first range.
            * Then try requesting ips
            * */

            //12.12.12.1 is gateway
            createNewIpAddressResourceAsAllocated("12.12.12.2",
                    this.subnetRangeState1.documentSelfLink,
                    ComputeService.FACTORY_LINK + "/machine-A");

            createNewIpAddressResourceAsAllocated("12.12.12.3",
                    this.subnetRangeState1.documentSelfLink,
                    ComputeService.FACTORY_LINK + "/machine-B");

            createNewIpAddressResourceAsAllocated("12.12.12.4",
                    this.subnetRangeState1.documentSelfLink,
                    ComputeService.FACTORY_LINK + "/machine-C");

            String connectedResourceLinkMacD = ComputeService.FACTORY_LINK + "/machine-D";

            IPAddressAllocationTaskState allocationTask = createIpAddressAllocationTask(
                    this.subnetState.documentSelfLink,
                    connectedResourceLinkMacD
            );
            addFakeCallBack(allocationTask);

            IPAddressAllocationTaskState allocationTaskResult = performTask(allocationTask);
            assertNotNull(allocationTaskResult);

            //Assert ip address is the first ip from the next range, that is 12.12.12.10
            List<String> allocatedIpSelfLink = allocationTaskResult.rsrcToAllocatedIpsMap
                    .get(connectedResourceLinkMacD);
            IPAddressService.IPAddressState allocatedIpState = getIpAddressState(
                    allocatedIpSelfLink.get(0));
            assertEquals("12.12.12.10", allocatedIpState.ipAddress);
        }

        @Test
        public void testAllocationTaskServiceWithAllIpsExhausted() throws Throwable {
            IPAddressAllocationTaskState allocationTask = createIpAddressAllocationTask(
                    this.subnetState.documentSelfLink,
                    ComputeService.FACTORY_LINK + "/machine-1");
            IPAddressAllocationTaskState allocationTaskResult;

            addFakeCallBack(allocationTask);

            List<SubnetRangeService.SubnetRangeState> subnetRangeStates = new ArrayList<>();
            subnetRangeStates.add(this.subnetRangeState1);
            subnetRangeStates.add(this.subnetRangeState2);
            int maxIPs = this.getMaxPossibleIpCountInSubnet(subnetRangeStates);

            for (int i = 0; i < maxIPs; i++) {
                allocationTaskResult = performTask(allocationTask);
                assertNotNull(allocationTaskResult);
            }

            // Now, all ip addresses are exhausted
            performTaskExpectFailure(allocationTask,
                    "IP Allocation did not return failure, after al IP Addresses are exhausted.");
        }

        @Test
        public void testAllocationTaskServiceWithNoRanges() throws Throwable {
            SubnetService.SubnetState subnetState = ModelUtils.createSubnet(this, this
                    .networkState, this.endpointState);
            IPAddressAllocationTaskState allocationTask = createIpAddressAllocationTask(
                    subnetState.documentSelfLink,
                    ComputeService.FACTORY_LINK + "/machine-1");
            IPAddressAllocationTaskState allocationTaskResult;

            addFakeCallBack(allocationTask);


            // task should not fail, but no IP addresses are allocated
            allocationTaskResult = performTask(allocationTask);
            assertNotNull(allocationTaskResult);
            assertEquals(0, allocationTaskResult.rsrcToAllocatedIpsMap.size());
            assertEquals(0, allocationTaskResult.subnetRangeLinks.size());
        }

        @Test
        public void testAllocationTaskServiceWithDeallocation() throws Throwable {
            IPAddressAllocationTaskState allocationTask = createIpAddressAllocationTask(
                    this.subnetState.documentSelfLink,
                    ComputeService.FACTORY_LINK + "/machine-1");
            addFakeCallBack(allocationTask);

            List<String> allocatedIpAddressResourceLinks = new ArrayList<>();

            String allocatedIpAddressResourceLink = performTask(allocationTask).ipAddressLinks
                    .get(0);
            allocatedIpAddressResourceLinks.add(allocatedIpAddressResourceLink);

            allocatedIpAddressResourceLink = performTask(allocationTask).ipAddressLinks.get(0);
            allocatedIpAddressResourceLinks.add(allocatedIpAddressResourceLink);

            // Now, perform de-allocation
            IPAddressAllocationTaskState deallocationTask = createIpAddressDeallocationTask(
                    allocatedIpAddressResourceLinks);
            IPAddressAllocationTaskState deallocationTaskResult = performTask(deallocationTask);
            assertNotNull(deallocationTaskResult);

            changeExistingIpAddressStatus(allocatedIpAddressResourceLinks,
                    IPAddressStatus.AVAILABLE);

            // Allocation should succeed now.
            IPAddressAllocationTaskState allocationTaskResult = performTask(allocationTask);
            assertNotNull(allocationTaskResult);

            allocationTaskResult = performTask(allocationTask);
            assertNotNull(allocationTaskResult);
        }

        @Test
        public void testDeallocateMultipleIPAddresses() {
            List<String> ipAddressResourceLinks = new ArrayList<>();

            IPAddressService.IPAddressState state = createNewIpAddressResourceAsAvailable(
                    startIpInRange1, this.subnetRangeState1.documentSelfLink);
            ipAddressResourceLinks.add(state.documentSelfLink);

            state = createNewIpAddressResourceAsAvailable(gatewayIp,
                    this.subnetRangeState1.documentSelfLink);
            ipAddressResourceLinks.add(state.documentSelfLink);

            state = createNewIpAddressResourceAsAvailable(endIpInRange1,
                    this.subnetRangeState1.documentSelfLink);
            ipAddressResourceLinks.add(state.documentSelfLink);

            changeExistingIpAddressStatus(ipAddressResourceLinks, IPAddressStatus.ALLOCATED);
            validateIPAddressState(ipAddressResourceLinks.get(0), IPAddressStatus.ALLOCATED);
            validateIPAddressState(ipAddressResourceLinks.get(1), IPAddressStatus.ALLOCATED);
            validateIPAddressState(ipAddressResourceLinks.get(2), IPAddressStatus.ALLOCATED);

            IPAddressAllocationTaskState deallocationTask = createIpAddressDeallocationTask(
                    ipAddressResourceLinks);
            performTask(deallocationTask);

            validateIPAddressState(ipAddressResourceLinks.get(0), IPAddressStatus.RELEASED);
            validateIPAddressState(ipAddressResourceLinks.get(1), IPAddressStatus.RELEASED);
            validateIPAddressState(ipAddressResourceLinks.get(2), IPAddressStatus.RELEASED);
        }

        @Test
        public void testAllocationCallback() throws Throwable {
            DeferredResult<IPAddressAllocationTaskResult> done = new DeferredResult<>();

            //This service is used by other tests. Hence stop it first if it's already started.
            try {
                deleteServiceSynchronously(TestStatelessService.SELF_LINK);
            } catch (ServiceNotFoundException exception) {
                //do nothing
            }

            TestStatelessService testStatelessService = new TestStatelessService(done);
            this.host.startService(testStatelessService);

            this.host.waitForServiceAvailable(TestStatelessService.SELF_LINK);

            IPAddressAllocationTaskState allocationTask = createIpAddressAllocationTask(
                    this.subnetState.documentSelfLink,
                    ComputeService.FACTORY_LINK + "/machine-1");

            allocationTask.serviceTaskCallback = new ServiceTaskCallback<>();
            try {
                allocationTask.serviceTaskCallback.serviceURI = new URI(
                        this.host.getReferer() + "/" + TestStatelessService.SELF_LINK);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }

            postServiceSynchronously(
                    IPAddressAllocationTaskService.FACTORY_LINK,
                    allocationTask, IPAddressAllocationTaskState.class);

            for (int i = 0; i < IP_ALLOCATION_TASK_TIMEOUT_MS / IP_ALLOCATION_TASK_STEP_MS; i++) {
                Thread.sleep(IP_ALLOCATION_TASK_STEP_MS);
                if (done.isDone()) {
                    done.thenAccept(r -> assertEquals(startIpInRange1, r.ipAddresses.get(0)));
                    return;
                }
            }

            fail(String.format("IP Address allocation timed out after %d milli seconds",
                    IP_ALLOCATION_TASK_TIMEOUT_MS));

        }

        @Test
        @Ignore
        public void testAllocationTaskWithNonIPV4() throws Throwable {
            SubnetService.SubnetState testSubnetState = createSubnetState();
            testSubnetState.gatewayAddress = "fc00:10:118:136:fcd8:d68d:9701:8976";
            testSubnetState.documentSelfLink = UUID.randomUUID().toString();
            testSubnetState = postServiceSynchronously(
                    SubnetService.FACTORY_LINK,
                    testSubnetState, SubnetService.SubnetState.class);

            SubnetRangeService.SubnetRangeState testSubnetRangeState = buildSubnetRangeState(
                    "fc00:10:118:136:fcd8:d68d:9701:8975",
                    "fc00:10:118:136:fcd8:d68d:9701:8977",
                    IPVersion.IPv6);

            testSubnetRangeState.documentSelfLink = UUID.randomUUID().toString();
            testSubnetRangeState.subnetLink = testSubnetState.documentSelfLink;
            postServiceSynchronously(SubnetRangeService.FACTORY_LINK,
                    testSubnetRangeState, SubnetRangeService.SubnetRangeState.class);

            IPAddressAllocationTaskState allocationTask = createIpAddressAllocationTask(
                    this.subnetState.documentSelfLink,
                    ComputeService.FACTORY_LINK + "/machine-1");
            addFakeCallBack(allocationTask);

            allocationTask.subnetLink = testSubnetState.documentSelfLink;

            performTaskExpectFailure(allocationTask,
                    "IP Address allocation does not return failure with IPv6 address range.");
        }

        /**
         * We have two subnet ranges 12.12.12.2 - 12.12.12.4  AND 12.12.12.10 - 12.12.12.12
         * We set (12.12.12.4 and 12.12.12.12) as available.
         * We set 12.12.12.2 and 12.12.12.10 as allocated
         * Then we do an allocation where 4 IPs are requested,
         * Aim here is to test that both allocate from existing IPs and create new IPs work.
         */
        @Test
        public void testPickExistingIpAndCreateNewIp() {

            //Set the available IPs
            createNewIpAddressResourceAsAvailable("12.12.12.4",
                    this.subnetRangeState1.documentSelfLink);

            createNewIpAddressResourceAsAvailable("12.12.12.12",
                    this.subnetRangeState2.documentSelfLink);

            //Set the allocated IPs
            createNewIpAddressResourceAsAllocated("12.12.12.2",
                    this.subnetRangeState1.documentSelfLink,
                    ComputeService.FACTORY_LINK + "/machine-1");

            createNewIpAddressResourceAsAllocated("12.12.12.10",
                    this.subnetRangeState1.documentSelfLink,
                    ComputeService.FACTORY_LINK + "/machine-2");


            //Set the expected IPs
            Set<String> expectedIPs = new HashSet<>();
            expectedIPs.add("12.12.12.4");
            expectedIPs.add("12.12.12.12");
            expectedIPs.add("12.12.12.3");
            expectedIPs.add("12.12.12.11");


            //Set the connected resource that needs IPs
            Map<String, Integer> connectedResourceToRequiredIpCountMap = new HashMap<>();
            String requestingResourceX = ComputeService.FACTORY_LINK + "/machine-X";
            String requestingResourceY = ComputeService.FACTORY_LINK + "/machine-Y";

            connectedResourceToRequiredIpCountMap.put(requestingResourceX, 2);
            connectedResourceToRequiredIpCountMap.put(requestingResourceY, 2);

            //Perform IP address allocation
            IPAddressAllocationTaskState allocationTask = createIpAddressAllocationTask(
                    this.subnetState.documentSelfLink,
                    connectedResourceToRequiredIpCountMap);

            addFakeCallBack(allocationTask);

            IPAddressAllocationTaskState allocationTaskResult;

            allocationTaskResult = performTask(allocationTask);
            assertNotNull(allocationTaskResult);

            //Get the IPs that got allocated to the resources and remove them from expected IPs

            List<String> allocatedIPLinks = allocationTaskResult.rsrcToAllocatedIpsMap.values()
                    .stream().flatMap(Collection::stream).collect(Collectors.toList());

            for (String ipLink : allocatedIPLinks) {
                String allocatedAddress = getIpAddressState(ipLink).ipAddress;
                expectedIPs.remove(allocatedAddress);
            }

            assertTrue(expectedIPs.size() == 0);

        }

        /**
         * We have two subnet ranges 12.12.12.2 - 12.12.12.4  AND 12.12.12.10 - 12.12.12.12.
         * We submit an ip allocation request where the resource requests 6 IPs.
         * So the resulting allocated IPs for the resource should include all these 6 IPs.
         */
        @Test
        public void testAllocationOverMultipleRangesForOneResource() {

            //Set the expected IPs
            Set<String> expectedIPs = new HashSet<>();
            expectedIPs.add("12.12.12.2");
            expectedIPs.add("12.12.12.3");
            expectedIPs.add("12.12.12.4");
            expectedIPs.add("12.12.12.10");
            expectedIPs.add("12.12.12.11");
            expectedIPs.add("12.12.12.12");

            //Set the connected resource
            Map<String, Integer> connectedResourceToRequiredIpCountMap = new HashMap<>();
            String requestingResource = ComputeService.FACTORY_LINK + "/machine-X";

            connectedResourceToRequiredIpCountMap.put(requestingResource, 5);

            //Perform IP allocation
            IPAddressAllocationTaskState allocationTask = createIpAddressAllocationTask(
                    this.subnetState.documentSelfLink,
                    connectedResourceToRequiredIpCountMap);

            IPAddressAllocationTaskState allocationTaskResult;

            addFakeCallBack(allocationTask);

            allocationTaskResult = performTask(allocationTask);
            assertNotNull(allocationTaskResult);

            //Get the IPs that got allocated to the resources and remove them from expected IPs
            List<String> allocatedIPLinks = allocationTaskResult.rsrcToAllocatedIpsMap
                    .get(requestingResource);

            for (String ipLink : allocatedIPLinks) {
                String allocatedAddress = getIpAddressState(ipLink).ipAddress;
                expectedIPs.remove(allocatedAddress);
            }

            assertTrue(expectedIPs.size() == 1);

        }

        /**
         * We have two subnet ranges 12.12.12.2 - 12.12.12.4  AND 12.12.12.10 - 12.12.12.12.
         * We set 3 IPs, 12.12.12.3, 12.12.12.10 and 12.12.12.12 as available. And 12.12.12.2 as
         * allocated
         * Then we submit 3 requests concurrently.
         * The resulting allocatedIPs should be 12.12.12.3, 12.12.12.10 and 12.12.12.12
         */
        @Test
        public void testConcurrentRequestsWithMultipleAvailableIPs() throws Throwable {

            //Set the available IPs
            createNewIpAddressResourceAsAvailable("12.12.12.3",
                    this.subnetRangeState1.documentSelfLink);

            createNewIpAddressResourceAsAvailable("12.12.12.10",
                    this.subnetRangeState2.documentSelfLink);

            createNewIpAddressResourceAsAvailable("12.12.12.12",
                    this.subnetRangeState2.documentSelfLink);

            Set<String> expectedIPs = new HashSet<>();
            expectedIPs.add("12.12.12.3");
            expectedIPs.add("12.12.12.10");
            expectedIPs.add("12.12.12.12");

            //Set the allocated IPs
            createNewIpAddressResourceAsAllocated("12.12.12.2",
                    this.subnetRangeState1.documentSelfLink,
                    ComputeService.FACTORY_LINK + "/machine-1");

            //Set connected resources

            Map<String, Integer> connectedResourceToRequiredIpCountMap1 = new HashMap<>();
            connectedResourceToRequiredIpCountMap1.put(ComputeService.FACTORY_LINK +
                    "/machine-X", 1);

            Map<String, Integer> connectedResourceToRequiredIpCountMap2 = new HashMap<>();
            connectedResourceToRequiredIpCountMap2.put(ComputeService.FACTORY_LINK +
                    "/machine-Y", 1);

            Map<String, Integer> connectedResourceToRequiredIpCountMap3 = new HashMap<>();
            connectedResourceToRequiredIpCountMap3.put(ComputeService.FACTORY_LINK +
                    "/machine-Z", 1);

            //Create the IP address allocation tasks
            IPAddressAllocationTaskState allocationTask1 = createIpAddressAllocationTask(
                    this.subnetState.documentSelfLink,
                    connectedResourceToRequiredIpCountMap1);

            IPAddressAllocationTaskState allocationTask2 = createIpAddressAllocationTask(
                    this.subnetState.documentSelfLink,
                    connectedResourceToRequiredIpCountMap2);

            IPAddressAllocationTaskState allocationTask3 = createIpAddressAllocationTask(
                    this.subnetState.documentSelfLink,
                    connectedResourceToRequiredIpCountMap3);

            //Kick off the tasks

            IPAddressAllocationTaskState startedAllocationTask1 = postServiceSynchronously(
                    IPAddressAllocationTaskService.FACTORY_LINK,
                    allocationTask1, IPAddressAllocationTaskState.class);

            IPAddressAllocationTaskState startedAllocationTask2 = postServiceSynchronously(
                    IPAddressAllocationTaskService.FACTORY_LINK,
                    allocationTask2, IPAddressAllocationTaskState.class);

            IPAddressAllocationTaskState startedAllocationTask3 = postServiceSynchronously(
                    IPAddressAllocationTaskService.FACTORY_LINK,
                    allocationTask3, IPAddressAllocationTaskState.class);

            //Wait for the tasks to complete

            IPAddressAllocationTaskState completedAllocationTask1 = waitForServiceState(
                    IPAddressAllocationTaskState.class,
                    startedAllocationTask1.documentSelfLink,
                    state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                            .ordinal());
            assertTrue(completedAllocationTask1.taskInfo.stage == TaskState.TaskStage.FINISHED);

            IPAddressAllocationTaskState completedAllocationTask2 = waitForServiceState(
                    IPAddressAllocationTaskState.class,
                    startedAllocationTask2.documentSelfLink,
                    state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                            .ordinal());
            assertTrue(completedAllocationTask2.taskInfo.stage == TaskState.TaskStage.FINISHED);

            IPAddressAllocationTaskState completedAllocationTask3 = waitForServiceState(
                    IPAddressAllocationTaskState.class,
                    startedAllocationTask3.documentSelfLink,
                    state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                            .ordinal());
            assertTrue(completedAllocationTask3.taskInfo.stage == TaskState.TaskStage.FINISHED);

            //Get all the allocated IP addresses

            List<String> ipAddresses = new ArrayList<>();
            ipAddresses.addAll(getIpAddressesFromConnectedResourceMap(
                    completedAllocationTask1.rsrcToAllocatedIpsMap));

            ipAddresses.addAll(getIpAddressesFromConnectedResourceMap(
                    completedAllocationTask2.rsrcToAllocatedIpsMap));

            ipAddresses.addAll(getIpAddressesFromConnectedResourceMap(
                    completedAllocationTask3.rsrcToAllocatedIpsMap));

            //Verify that the allocated IP addresses are as expected

            for (String ipAddress : ipAddresses) {
                expectedIPs.remove(ipAddress);
            }

            assertTrue(expectedIPs.size() == 0);

        }

        @Test
        public void testMultipleResourcesInOneRequest() throws Throwable {
            //Set the expected IPs
            Set<String> expectedIPs = new HashSet<>();
            expectedIPs.add("12.12.12.2");
            expectedIPs.add("12.12.12.3");
            expectedIPs.add("12.12.12.4");
            expectedIPs.add("12.12.12.10");
            expectedIPs.add("12.12.12.11");
            expectedIPs.add("12.12.12.12");

            //Set the connected resources
            Map<String, Integer> connectedResourceToRequiredIpCountMap = new HashMap<>();
            String requestingResourceA = ComputeService.FACTORY_LINK + "/machine-A";
            String requestingResourceB = ComputeService.FACTORY_LINK + "/machine-B";
            String requestingResourceC = ComputeService.FACTORY_LINK + "/machine-C";

            connectedResourceToRequiredIpCountMap.put(requestingResourceA, 1);
            connectedResourceToRequiredIpCountMap.put(requestingResourceB, 2);
            connectedResourceToRequiredIpCountMap.put(requestingResourceC, 1);

            //Perform IP allocation
            IPAddressAllocationTaskState allocationTask = createIpAddressAllocationTask(
                    this.subnetState.documentSelfLink,
                    connectedResourceToRequiredIpCountMap);

            IPAddressAllocationTaskState allocationTaskResult;

            addFakeCallBack(allocationTask);

            allocationTaskResult = performTask(allocationTask);
            assertNotNull(allocationTaskResult);

            //Get the IPs that got allocated to the resources and remove them from expected IPs
            List<String> allocatedIPLinks = allocationTaskResult.rsrcToAllocatedIpsMap.values()
                    .stream().flatMap(Collection::stream).collect(Collectors.toList());

            for (String ipLink : allocatedIPLinks) {
                String allocatedAddress = getIpAddressState(ipLink).ipAddress;
                expectedIPs.remove(allocatedAddress);
            }

            assertTrue(expectedIPs.size() == 2);

        }



        private IPAddressAllocationTaskState performTask(
                IPAddressAllocationTaskState allocationTask) {
            try {
                IPAddressAllocationTaskState taskState = postServiceSynchronously(
                        IPAddressAllocationTaskService.FACTORY_LINK,
                        allocationTask, IPAddressAllocationTaskState.class);

                taskState = waitForServiceState(
                        IPAddressAllocationTaskState.class,
                        taskState.documentSelfLink,
                        state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                .ordinal());
                assertTrue(taskState.taskInfo.stage == TaskState.TaskStage.FINISHED);
                return taskState;
            } catch (Throwable throwable) {
                fail(throwable.getMessage());
                return null;
            }
        }

        private void performTaskExpectFailure(IPAddressAllocationTaskState allocationTask,
                String message) {
            try {
                IPAddressAllocationTaskState taskState = postServiceSynchronously(
                        IPAddressAllocationTaskService.FACTORY_LINK,
                        allocationTask, IPAddressAllocationTaskState.class);

                taskState = waitForServiceState(
                        IPAddressAllocationTaskState.class,
                        taskState.documentSelfLink,
                        state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage
                                .ordinal());
                assertTrue(message, taskState.taskInfo.stage == TaskState.TaskStage.FAILED);
            } catch (Throwable throwable) {
                fail(throwable.getMessage());
            }
        }

        private void createValidStates() throws Throwable {
            this.endpointState = ModelUtils.createEndpoint(this);
            this.networkState = ModelUtils.createNetwork(this, this.endpointState);
            this.subnetState = ModelUtils.createSubnet(this, this.networkState, this.endpointState);
            this.subnetRangeState1 = ModelUtils
                    .createSubnetRange(this, this.subnetState, this.startIpInRange1, this.endIpInRange1);
            this.subnetRangeState2 = ModelUtils
                    .createSubnetRange(this, this.subnetState, this.startIpInRange2, this.endIpInRange2);

        }

        private int getMaxPossibleIpCountInSubnet(
                List<SubnetRangeService.SubnetRangeState> subnetRangeStates) {
            int maxIps = 0;

            for (SubnetRangeService.SubnetRangeState subnetRangeState : subnetRangeStates) {
                Long startIP = IpHelper.ipStringToLong(subnetRangeState.startIPAddress);
                Long endIP = IpHelper.ipStringToLong(subnetRangeState.endIPAddress);
                maxIps = maxIps + (int) (endIP - startIP) + 1;
            }

            return maxIps;
        }

        private SubnetService.SubnetState createSubnetState()
                throws Throwable {
            this.subnetState = new SubnetService.SubnetState();
            this.subnetState.subnetCIDR = "1.2.3.0/24";
            this.subnetState.networkLink = UriUtils
                    .buildUriPath(NetworkService.FACTORY_LINK, UUID.randomUUID().toString());
            this.subnetState.tenantLinks = new ArrayList<>();
            this.subnetState.tagLinks = new HashSet<>();
            this.subnetState.gatewayAddress = gatewayIp;
            this.subnetState = postServiceSynchronously(SubnetService.FACTORY_LINK,
                    this.subnetState, SubnetService.SubnetState.class);

            return this.subnetState;
        }

        private SubnetRangeService.SubnetRangeState createSubnetRangeState()
                throws Throwable {
            this.subnetRangeState1 = buildSubnetRangeState(startIpInRange1, endIpInRange1,
                    IPVersion.IPv4);
            this.subnetRangeState1.documentSelfLink = UUID.randomUUID().toString();
            this.subnetRangeState1 = postServiceSynchronously(SubnetRangeService.FACTORY_LINK,
                    this.subnetRangeState1, SubnetRangeService.SubnetRangeState.class);

            return this.subnetRangeState1;
        }

        private SubnetRangeService.SubnetRangeState buildSubnetRangeState(String startIp,
                String endIp, IPVersion ipVersion) {
            SubnetRangeService.SubnetRangeState subnetRangeState = new SubnetRangeService.SubnetRangeState();
            subnetRangeState.id = UUID.randomUUID().toString();
            subnetRangeState.name = "test-range-1";
            subnetRangeState.startIPAddress = startIp;
            subnetRangeState.endIPAddress = endIp;
            subnetRangeState.ipVersion = ipVersion;
            subnetRangeState.isDHCP = false;
            subnetRangeState.dnsServerAddresses = new ArrayList<>();
            subnetRangeState.dnsServerAddresses.add("dnsServer1.vmware.com");
            subnetRangeState.dnsServerAddresses.add("dnsServer2.vmwew.com");
            subnetRangeState.domain = "vmware.com";
            subnetRangeState.subnetLink = this.subnetState.documentSelfLink;
            subnetRangeState.tenantLinks = new ArrayList<>();
            subnetRangeState.tenantLinks.add("tenant-linkA");

            return subnetRangeState;
        }

        private void changeExistingIpAddressStatus(List<String> ipAddressLinks,
                IPAddressStatus status) {
            for (int i = 0; i < ipAddressLinks.size(); i++) {
                IPAddressService.IPAddressState ipAddressState = new IPAddressService.IPAddressState();
                ipAddressState.documentSelfLink = ipAddressLinks.get(i);
                ipAddressState.ipAddressStatus = status;
                if (status == IPAddressStatus.ALLOCATED) {
                    ipAddressState.connectedResourceLink =
                            ComputeService.FACTORY_LINK + "/machine-1";
                } else {
                    ipAddressState.connectedResourceLink = null;
                }
                try {
                    patchServiceSynchronously(ipAddressLinks.get(i), ipAddressState);
                } catch (Throwable throwable) {
                    String message = String
                            .format("Failed to make ip address %s available for allocation due to error %s",
                                    ipAddressLinks.get(i), throwable.getMessage());
                    fail(message);
                }
            }
        }

        private IPAddressService.IPAddressState createNewIpAddressResourceAsAvailable(
                String ipAddress, String subnetRangeLink) {

            return createNewIpAddressResource(ipAddress, subnetRangeLink, IPAddressStatus.AVAILABLE,
                    "");
        }

        private IPAddressService.IPAddressState createNewIpAddressResourceAsAllocated(
                String ipAddress,
                String subnetRangeLink,
                String connectedResourceLink) {
            return createNewIpAddressResource(ipAddress, subnetRangeLink, IPAddressStatus.ALLOCATED,
                    connectedResourceLink);
        }

        private IPAddressService.IPAddressState createNewIpAddressResource(String ipAddress,
                String subnetRangeLink,
                IPAddressService.IPAddressState.IPAddressStatus ipAddressStatus,
                String connectedResourceLink) {
            IPAddressService.IPAddressState ipAddressState = new IPAddressService.IPAddressState();
            ipAddressState.ipAddress = ipAddress;
            ipAddressState.ipAddressStatus = ipAddressStatus;
            if (ipAddressStatus == IPAddressStatus.ALLOCATED) {
                ipAddressState.connectedResourceLink = connectedResourceLink;
            }
            ipAddressState.subnetRangeLink = subnetRangeLink;
            ipAddressState.documentSelfLink = UriUtils.getLastPathSegment(subnetRangeLink) +
                    IPAddressAllocationTaskService.ID_SEPARATOR + ipAddress;
            try {
                return postServiceSynchronously(IPAddressService.FACTORY_LINK, ipAddressState,
                        IPAddressService.IPAddressState.class);
            } catch (Throwable throwable) {
                String message = String.format("Failed to create ip address %s due to error %s",
                        ipAddress, throwable.getMessage());
                fail(message);
                return null;
            }
        }

        private void validateIPAddressState(String ipAddressLink, IPAddressStatus status) {
            IPAddressService.IPAddressState state = getIpAddressState(ipAddressLink);
            assertEquals(status, state.ipAddressStatus);
            if (status == IPAddressStatus.ALLOCATED) {
                assertTrue(state.connectedResourceLink != null);
            } else {
                assertTrue(state.connectedResourceLink == null);
            }
        }

        private IPAddressService.IPAddressState getIpAddressState(String ipAddressLink) {
            IPAddressService.IPAddressState state = null;
            try {
                state = getServiceSynchronously(ipAddressLink,
                        IPAddressService.IPAddressState.class);
            } catch (Throwable throwable) {
                String message = String.format("Failed to retrieve ip address %s due to error %s",
                        ipAddressLink, throwable.getMessage());
                fail(message);
            }

            return state;
        }

        private void addFakeCallBack(IPAddressAllocationTaskState taskState) {
            taskState.serviceTaskCallback = new ServiceTaskCallback<>();
            try {
                taskState.serviceTaskCallback.serviceURI = new URI(
                        this.host.getReferer() + "/" + TestStatelessService.SELF_LINK);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }

        private List<String> getIpAddressesFromConnectedResourceMap(Map<String, List<String>>
                resourceMap) {
            List<String> ipAddresses = new ArrayList<>();

            for (List<String> ipList : resourceMap.values()) {
                for (String ipLink : ipList) {
                    String allocatedAddress = getIpAddressState(ipLink).ipAddress;
                    ipAddresses.add(allocatedAddress);
                }

            }
            return ipAddresses;
        }
    }

    public static class TestStatelessService extends StatelessService {
        public static String SELF_LINK = TestStatelessService.class.getSimpleName();
        private DeferredResult<IPAddressAllocationTaskResult> allocationResult;

        public TestStatelessService(
                DeferredResult<IPAddressAllocationTaskResult> allocationResult) {
            this.allocationResult = allocationResult;
        }

        @Override
        public void handlePatch(Operation patch) {
            IPAddressAllocationTaskResult result = patch.getBody(
                    IPAddressAllocationTaskResult.class);
            patch.complete();
            this.allocationResult.complete(result);
        }
    }

}
