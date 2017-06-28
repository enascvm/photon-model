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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.IPAddressService;
import com.vmware.photon.controller.model.resources.IPAddressService.IPAddressState.IPAddressStatus;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.SubnetRangeService;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.support.IPVersion;
import com.vmware.photon.controller.model.tasks.IPAddressAllocationTaskService.IPAddressAllocationTaskResult;
import com.vmware.photon.controller.model.tasks.IPAddressAllocationTaskService.IPAddressAllocationTaskState;

import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

@RunWith(IPAddressAllocationTaskServiceTest.class)
@Suite.SuiteClasses({ IPAddressAllocationTaskServiceTest.ConstructorTest.class,
        IPAddressAllocationTaskServiceTest.HandleStartTest.class,
        IPAddressAllocationTaskServiceTest.EndToEndTest.class})
public class IPAddressAllocationTaskServiceTest extends Suite{

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

    private static IPAddressAllocationTaskState createIpAddressAllocationTask(String subnetLink) {
        IPAddressAllocationTaskState allocationTask = new IPAddressAllocationTaskState();
        allocationTask.subnetLink = subnetLink;
        allocationTask.requestType = IPAddressAllocationTaskState.RequestType.ALLOCATE;
        return allocationTask;
    }

    private static IPAddressAllocationTaskState createIpAddressDeallocationTask(List<String> ipAddressResourceLinks) {

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
            IPAddressAllocationTaskState startState = createIpAddressAllocationTask("whatever");
            startState.requestType = null;

            this.postServiceSynchronously(
                    IPAddressAllocationTaskService.FACTORY_LINK, startState,
                    IPAddressAllocationTaskState.class,
                    IllegalArgumentException.class);
        }

        @Test
        public void testMissingSubnetStateForAllocation() throws Throwable {
            IPAddressAllocationTaskState startState = createIpAddressAllocationTask(null);

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
        private SubnetService.SubnetState subnetState;
        private SubnetRangeService.SubnetRangeState subnetRangeState;

        private static String startIpInRange = "1.2.3.5";
        private static String endIpInRange = "1.2.3.7";
        private static String gatewayIp = "1.2.3.6";

        @Before
        public void setUp() throws Throwable {
            super.setUp();

            startFactoryServices(this);

            createSubnetState();
            createSubnetRangeState();
        }

        @Test
        public void testAllocationLifeCycle() {
            IPAddressAllocationTaskState allocationTask = createIpAddressAllocationTask(this.subnetState.documentSelfLink);
            IPAddressAllocationTaskState allocationTaskResult = performTask(allocationTask);

            assertNotNull(allocationTaskResult);
            assertEquals(startIpInRange, allocationTaskResult.ipAddresses.get(0));
            assertEquals(this.subnetRangeState.documentSelfLink, allocationTaskResult.subnetRangeLinks.get(0));
            assertEquals(this.subnetState.documentSelfLink, allocationTaskResult.subnetLink);

            // Next allocation should give a different ip address
            IPAddressAllocationTaskState secondAllocationTaskResult = performTask(allocationTask);
            assertNotNull(secondAllocationTaskResult);
            assertEquals(endIpInRange, secondAllocationTaskResult.ipAddresses.get(0));
            assertEquals(this.subnetRangeState.documentSelfLink, secondAllocationTaskResult.subnetRangeLinks.get(0));
            assertEquals(this.subnetState.documentSelfLink, secondAllocationTaskResult.subnetLink);

            // At this point, if we de-allocate the first ip address and make the ip address available,
            // allocation should allocate the same ip address
            IPAddressAllocationTaskState deallocationTask = createIpAddressDeallocationTask(allocationTaskResult.ipAddressLinks);
            IPAddressAllocationTaskState deallocationTaskResult = performTask(deallocationTask);
            assertNotNull(deallocationTaskResult);

            changeExistingIpAddressStatus(allocationTaskResult.ipAddressLinks, IPAddressStatus.AVAILABLE);

            IPAddressAllocationTaskState allocationTaskAfterDeallocation = performTask(allocationTask);
            assertNotNull(allocationTaskAfterDeallocation);
            assertEquals(this.subnetRangeState.startIPAddress, allocationTaskAfterDeallocation.ipAddresses.get(0));
            assertEquals(this.subnetRangeState.documentSelfLink, allocationTaskAfterDeallocation.subnetRangeLinks.get(0));
            assertEquals(this.subnetState.documentSelfLink, allocationTaskAfterDeallocation.subnetLink);
        }

        @Test
        public void testConcurrentIPTaskServiceAllocationWithNewIPAddresses() throws Throwable {
            // Now, one of the tasks should use available IP address and the other one should use
            // new IP Address.
            IPAddressAllocationTaskState allocationTask1 = createIpAddressAllocationTask(this.subnetState.documentSelfLink);
            IPAddressAllocationTaskState allocationTask2 = createIpAddressAllocationTask(this.subnetState.documentSelfLink);

            IPAddressAllocationTaskState startedAllocationTask1 = postServiceSynchronously(
                    IPAddressAllocationTaskService.FACTORY_LINK,
                    allocationTask1, IPAddressAllocationTaskState.class);

            IPAddressAllocationTaskState startedAllocationTask2 = postServiceSynchronously(
                    IPAddressAllocationTaskService.FACTORY_LINK,
                    allocationTask2, IPAddressAllocationTaskState.class);

            IPAddressAllocationTaskState completedAllocationTask1 = waitForServiceState(
                    IPAddressAllocationTaskState.class,
                    startedAllocationTask1.documentSelfLink,
                    state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal());
            assertTrue(completedAllocationTask1.taskInfo.stage == TaskState.TaskStage.FINISHED);

            IPAddressAllocationTaskState completedAllocationTask2 = waitForServiceState(
                    IPAddressAllocationTaskState.class,
                    startedAllocationTask2.documentSelfLink,
                    state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal());
            assertTrue(completedAllocationTask2.taskInfo.stage == TaskState.TaskStage.FINISHED);

            assertNotEquals(completedAllocationTask1.ipAddresses.get(0), completedAllocationTask2.ipAddresses.get(0));
        }

        @Test
        public void testConcurrentIPTaskServiceAllocation() throws Throwable {
            // First, make an IP Address available.
            createNewIpAddressResource(startIpInRange, this.subnetRangeState.documentSelfLink);

            // Now, one of the tasks should use available IP address and the other one should use
            // new IP Address.
            IPAddressAllocationTaskState allocationTask1 = createIpAddressAllocationTask(this.subnetState.documentSelfLink);
            IPAddressAllocationTaskState allocationTask2 = createIpAddressAllocationTask(this.subnetState.documentSelfLink);

            IPAddressAllocationTaskState startedAllocationTask1 = postServiceSynchronously(
                    IPAddressAllocationTaskService.FACTORY_LINK,
                    allocationTask1, IPAddressAllocationTaskState.class);

            IPAddressAllocationTaskState startedAllocationTask2 = postServiceSynchronously(
                    IPAddressAllocationTaskService.FACTORY_LINK,
                    allocationTask2, IPAddressAllocationTaskState.class);

            IPAddressAllocationTaskState completedAllocationTask1 = waitForServiceState(
                    IPAddressAllocationTaskState.class,
                    startedAllocationTask1.documentSelfLink,
                    state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal());
            assertTrue(completedAllocationTask1.taskInfo.stage == TaskState.TaskStage.FINISHED);

            IPAddressAllocationTaskState completedAllocationTask2 = waitForServiceState(
                    IPAddressAllocationTaskState.class,
                    startedAllocationTask2.documentSelfLink,
                    state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal());
            assertTrue(completedAllocationTask2.taskInfo.stage == TaskState.TaskStage.FINISHED);

            assertNotEquals(completedAllocationTask1.ipAddresses.get(0), completedAllocationTask2.ipAddresses.get(0));
        }

        @Test
        public void testAllocationTaskServiceWithOneRangeExhausted() throws Throwable {
            SubnetRangeService.SubnetRangeState secondSubnetRange = buildSubnetRangeState("1.2.4.5", "1.2.4.6", IPVersion.IPv4);
            secondSubnetRange.documentSelfLink = UUID.randomUUID().toString();
            secondSubnetRange = postServiceSynchronously(SubnetRangeService.FACTORY_LINK,
                    secondSubnetRange, SubnetRangeService.SubnetRangeState.class);

            HashMap<String, String> expectedIpToRangeMap = new HashMap<>();
            expectedIpToRangeMap.put("1.2.4.5", secondSubnetRange.documentSelfLink);
            expectedIpToRangeMap.put("1.2.4.6", secondSubnetRange.documentSelfLink);
            expectedIpToRangeMap.put(startIpInRange, this.subnetRangeState.documentSelfLink);
            expectedIpToRangeMap.put(endIpInRange, this.subnetRangeState.documentSelfLink);

            IPAddressAllocationTaskState allocationTask = createIpAddressAllocationTask(this.subnetState.documentSelfLink);
            IPAddressAllocationTaskState allocationTaskResult = performTask(allocationTask);
            assertNotNull(allocationTaskResult);
            String expectedSubnetRangeResourceLink = expectedIpToRangeMap.get(allocationTaskResult.ipAddresses.get(0));
            assertEquals(expectedSubnetRangeResourceLink, allocationTaskResult.subnetRangeLinks.get(0));
            expectedIpToRangeMap.remove(allocationTaskResult.ipAddresses.get(0));

            allocationTaskResult = performTask(allocationTask);
            assertNotNull(allocationTaskResult);
            expectedSubnetRangeResourceLink = expectedIpToRangeMap.get(allocationTaskResult.ipAddresses.get(0));
            assertEquals(expectedSubnetRangeResourceLink, allocationTaskResult.subnetRangeLinks.get(0));
            expectedIpToRangeMap.remove(allocationTaskResult.ipAddresses.get(0));

            allocationTaskResult = performTask(allocationTask);
            assertNotNull(allocationTaskResult);
            expectedSubnetRangeResourceLink = expectedIpToRangeMap.get(allocationTaskResult.ipAddresses.get(0));
            assertEquals(expectedSubnetRangeResourceLink, allocationTaskResult.subnetRangeLinks.get(0));
            expectedIpToRangeMap.remove(allocationTaskResult.ipAddresses.get(0));

            allocationTaskResult = performTask(allocationTask);
            assertNotNull(allocationTaskResult);
            expectedSubnetRangeResourceLink = expectedIpToRangeMap.get(allocationTaskResult.ipAddresses.get(0));
            assertEquals(expectedSubnetRangeResourceLink, allocationTaskResult.subnetRangeLinks.get(0));
            expectedIpToRangeMap.remove(allocationTaskResult.ipAddresses.get(0));
        }

        @Test
        public void testAllocationTaskServiceWithAllIpsExhausted() throws Throwable {
            IPAddressAllocationTaskState allocationTask = createIpAddressAllocationTask(this.subnetState.documentSelfLink);
            IPAddressAllocationTaskState allocationTaskResult = performTask(allocationTask);
            assertNotNull(allocationTaskResult);

            allocationTaskResult = performTask(allocationTask);
            assertNotNull(allocationTaskResult);

            // Now, all ip addresses are exhausted
            performTaskExpectFailure(allocationTask, "IP Allocation did not return failure, after al IP Addresses are exhausted.");
        }

        @Test
        public void testAllocationTaskServiceWithDeallocation() throws Throwable {
            IPAddressAllocationTaskState allocationTask = createIpAddressAllocationTask(this.subnetState.documentSelfLink);

            List<String> allocatedIpAddressResourceLinks = new ArrayList<>();

            String allocatedIpAddressResourceLink = performTask(allocationTask).ipAddressLinks.get(0);
            allocatedIpAddressResourceLinks.add(allocatedIpAddressResourceLink);

            allocatedIpAddressResourceLink = performTask(allocationTask).ipAddressLinks.get(0);
            allocatedIpAddressResourceLinks.add(allocatedIpAddressResourceLink);

            // Now, perform de-allocation
            IPAddressAllocationTaskState deallocationTask = createIpAddressDeallocationTask(allocatedIpAddressResourceLinks);
            IPAddressAllocationTaskState deallocationTaskResult = performTask(deallocationTask);
            assertNotNull(deallocationTaskResult);

            changeExistingIpAddressStatus(allocatedIpAddressResourceLinks, IPAddressStatus.AVAILABLE);

            // Allocation should succeed now.
            IPAddressAllocationTaskState allocationTaskResult = performTask(allocationTask);
            assertNotNull(allocationTaskResult);

            allocationTaskResult = performTask(allocationTask);
            assertNotNull(allocationTaskResult);
        }

        @Test
        public void testDeallocateMultipleIPAddresses() {
            List<String> ipAddressResourceLinks = new ArrayList<>();

            IPAddressService.IPAddressState state = createNewIpAddressResource(startIpInRange, this.subnetRangeState.documentSelfLink);
            ipAddressResourceLinks.add(state.documentSelfLink);

            state = createNewIpAddressResource(gatewayIp, this.subnetRangeState.documentSelfLink);
            ipAddressResourceLinks.add(state.documentSelfLink);

            state = createNewIpAddressResource(endIpInRange, this.subnetRangeState.documentSelfLink);
            ipAddressResourceLinks.add(state.documentSelfLink);

            changeExistingIpAddressStatus(ipAddressResourceLinks, IPAddressStatus.ALLOCATED);
            validateIPAddressState(ipAddressResourceLinks.get(0), IPAddressStatus.ALLOCATED);
            validateIPAddressState(ipAddressResourceLinks.get(1), IPAddressStatus.ALLOCATED);
            validateIPAddressState(ipAddressResourceLinks.get(2), IPAddressStatus.ALLOCATED);

            IPAddressAllocationTaskState deallocationTask = createIpAddressDeallocationTask(ipAddressResourceLinks);
            performTask(deallocationTask);

            validateIPAddressState(ipAddressResourceLinks.get(0), IPAddressStatus.RELEASED);
            validateIPAddressState(ipAddressResourceLinks.get(1), IPAddressStatus.RELEASED);
            validateIPAddressState(ipAddressResourceLinks.get(2), IPAddressStatus.RELEASED);
        }

        @Test
        public void testAllocationCallback() throws Throwable {
            DeferredResult<IPAddressAllocationTaskResult> done = new DeferredResult<>();
            this.host.startService(new TestStatelessService(done));
            this.host.waitForServiceAvailable(TestStatelessService.SELF_LINK);

            IPAddressAllocationTaskState allocationTask = createIpAddressAllocationTask(this.subnetState.documentSelfLink);
            allocationTask.serviceTaskCallback = new ServiceTaskCallback<>();
            allocationTask.serviceTaskCallback.serviceURI = new URI(this.host.getReferer() + "/" + TestStatelessService.SELF_LINK);

            postServiceSynchronously(
                    IPAddressAllocationTaskService.FACTORY_LINK,
                    allocationTask, IPAddressAllocationTaskState.class);

            for (int i = 0; i < IP_ALLOCATION_TASK_TIMEOUT_MS / IP_ALLOCATION_TASK_STEP_MS; i++) {
                Thread.sleep(IP_ALLOCATION_TASK_STEP_MS);
                if (done.isDone()) {
                    done.thenAccept(r -> assertEquals(startIpInRange, r.ipAddresses.get(0)));
                    return;
                }
            }

            fail(String.format("IP Address allocation timed out after %d milli seconds", IP_ALLOCATION_TASK_TIMEOUT_MS));

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

            IPAddressAllocationTaskState allocationTask = createIpAddressAllocationTask(this.subnetState.documentSelfLink);
            allocationTask.subnetLink = testSubnetState.documentSelfLink;

            performTaskExpectFailure(allocationTask, "IP Address allocation does not return failure with IPv6 address range.");
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
                        state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal());
                assertTrue(taskState.taskInfo.stage == TaskState.TaskStage.FINISHED);
                return taskState;
            } catch (Throwable throwable) {
                fail(throwable.getMessage());
                return null;
            }
        }

        private void performTaskExpectFailure(IPAddressAllocationTaskState allocationTask, String message) {
            try {
                IPAddressAllocationTaskState taskState = postServiceSynchronously(
                        IPAddressAllocationTaskService.FACTORY_LINK,
                        allocationTask, IPAddressAllocationTaskState.class);

                taskState = waitForServiceState(
                        IPAddressAllocationTaskState.class,
                        taskState.documentSelfLink,
                        state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal());
                assertTrue(message, taskState.taskInfo.stage == TaskState.TaskStage.FAILED);
            } catch (Throwable throwable) {
                fail(throwable.getMessage());
            }
        }

        private SubnetService.SubnetState createSubnetState()
                throws Throwable {
            this.subnetState = new SubnetService.SubnetState();
            this.subnetState.documentSelfLink = "test-subnet-" + UUID.randomUUID().toString();
            this.subnetState.subnetCIDR = "10.10.10.10/24";
            this.subnetState.networkLink = UriUtils.buildUriPath(NetworkService.FACTORY_LINK,  UUID.randomUUID().toString());
            this.subnetState.tenantLinks = new ArrayList<String>();
            this.subnetState.tagLinks = new HashSet<>();
            this.subnetState.gatewayAddress = gatewayIp;
            this.subnetState.documentSelfLink = UUID.randomUUID().toString();
            this.subnetState = postServiceSynchronously(SubnetService.FACTORY_LINK, this.subnetState, SubnetService.SubnetState.class);

            return this.subnetState;
        }

        private SubnetRangeService.SubnetRangeState createSubnetRangeState()
                throws Throwable {
            this.subnetRangeState = buildSubnetRangeState(startIpInRange, endIpInRange, IPVersion.IPv4);
            this.subnetRangeState.documentSelfLink = UUID.randomUUID().toString();
            this.subnetRangeState = postServiceSynchronously(SubnetRangeService.FACTORY_LINK,
                    this.subnetRangeState, SubnetRangeService.SubnetRangeState.class);

            return this.subnetRangeState;
        }

        private SubnetRangeService.SubnetRangeState buildSubnetRangeState(String startIp, String endIp, IPVersion ipVersion) {
            SubnetRangeService.SubnetRangeState subnetRangeState = new SubnetRangeService.SubnetRangeState();
            subnetRangeState.id = UUID.randomUUID().toString();
            subnetRangeState.name = "test-range-1";
            subnetRangeState.startIPAddress = startIp;
            subnetRangeState.endIPAddress = endIp;
            subnetRangeState.ipVersion = ipVersion;
            subnetRangeState.isDHCP = false;
            subnetRangeState.dnsServerAddresses = new HashSet();
            subnetRangeState.dnsServerAddresses.add("dnsServer1.vmware.com");
            subnetRangeState.dnsServerAddresses.add("dnsServer2.vmwew.com");
            subnetRangeState.domain = "vmware.com";
            subnetRangeState.subnetLink = this.subnetState.documentSelfLink;
            subnetRangeState.tenantLinks = new ArrayList<>();
            subnetRangeState.tenantLinks.add("tenant-linkA");

            return subnetRangeState;
        }

        private void changeExistingIpAddressStatus(List<String> ipAddressLinks, IPAddressStatus status) {
            for (int i = 0; i < ipAddressLinks.size(); i++) {
                IPAddressService.IPAddressState ipAddressState = new IPAddressService.IPAddressState();
                ipAddressState.documentSelfLink = ipAddressLinks.get(i);
                ipAddressState.ipAddressStatus = status;
                try {
                    patchServiceSynchronously(ipAddressLinks.get(i), ipAddressState);
                } catch (Throwable throwable) {
                    String message = String.format("Failed to make ip address %s available for allocation due to error %s",
                            ipAddressLinks.get(i), throwable.getMessage());
                    fail(message);
                }
            }
        }

        private IPAddressService.IPAddressState createNewIpAddressResource(String ipAddress, String subnetRangeLink) {
            IPAddressService.IPAddressState ipAddressState = new IPAddressService.IPAddressState();
            ipAddressState.ipAddress = ipAddress;
            ipAddressState.ipAddressStatus = IPAddressService.IPAddressState.IPAddressStatus.AVAILABLE;
            ipAddressState.subnetRangeLink = subnetRangeLink;
            ipAddressState.documentSelfLink = UriUtils.getLastPathSegment(subnetRangeLink) +
                    IPAddressAllocationTaskService.ID_SEPARATOR + ipAddress;
            try {
                return postServiceSynchronously(IPAddressService.FACTORY_LINK, ipAddressState, IPAddressService.IPAddressState.class);
            } catch (Throwable throwable) {
                String message = String.format("Failed to create ip address %s due to error %s",
                        ipAddress, throwable.getMessage());
                fail(message);
                return null;
            }
        }

        private void validateIPAddressState(String ipAddressLink, IPAddressStatus status) {
            try {
                IPAddressService.IPAddressState state = getServiceSynchronously(ipAddressLink, IPAddressService.IPAddressState.class);
                assertEquals(status, state.ipAddressStatus);
            } catch (Throwable throwable) {
                String message = String.format("Failed to retrieve ip address %s due to error %s",
                        ipAddressLink, throwable.getMessage());
                fail(message);
            }
        }
    }

    public static class TestStatelessService extends StatelessService {
        public static String SELF_LINK = TestStatelessService.class.getSimpleName();
        private DeferredResult<IPAddressAllocationTaskResult> allocationResult;

        public TestStatelessService(DeferredResult<IPAddressAllocationTaskResult> allocationResult) {
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
