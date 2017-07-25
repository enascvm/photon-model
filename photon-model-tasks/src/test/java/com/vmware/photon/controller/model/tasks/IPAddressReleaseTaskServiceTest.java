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

import static org.junit.Assert.fail;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.IPAddressService;
import com.vmware.photon.controller.model.resources.IPAddressService.IPAddressState;
import com.vmware.photon.controller.model.resources.IPAddressService.IPAddressState.IPAddressStatus;

import com.vmware.xenon.common.Operation;

@RunWith(IPAddressReleaseTaskServiceTest.class)
@Suite.SuiteClasses({ IPAddressReleaseTaskServiceTest.EndToEndTest.class})
public class IPAddressReleaseTaskServiceTest extends Suite {

    public IPAddressReleaseTaskServiceTest(Class<?> klass,
                                           RunnerBuilder builder) throws InitializationError {
        super(klass, builder);
    }

    private static void startFactoryServices(BaseModelTest test) throws Throwable {
        PhotonModelTaskServices.startServices(test.getHost());
        MockAdapter.startFactories(test);

        test.getHost().startFactory(new IPAddressService());
    }

    /**
     * This class implements EndToEnd tests for {@link IPAddressAllocationTaskService}.
     */
    public static class EndToEndTest extends BaseModelTest {

        private static final String IP_ADDRESS_1 = "1.2.3.4";
        private static final String IP_ADDRESS_2 = "1.2.3.5";
        private static final int RELEASE_INTERVAL_SECONDS = 1;

        private static final long TASK_RETRY_INTERVAL_MS = 200;
        private static final long TASK_RETRY_TIMEOUT_MS = 2000;

        IPAddressReleaseTaskService ipAddressReleaseTaskService;

        @Before
        public void setUp() throws Throwable {
            super.setUp();
            startFactoryServices(this);

            this.ipAddressReleaseTaskService = new IPAddressReleaseTaskService();
            getHost().startService(this.ipAddressReleaseTaskService);
        }

        @After
        public void cleanup() throws Throwable {
            getHost().stopService(this.ipAddressReleaseTaskService);
        }

        @Test
        public void testReleaseSingleReleasedIPAddress() {
            setIPAddressReleasePeriod(RELEASE_INTERVAL_SECONDS);

            IPAddressState ipAddressState = createIPAddressInState(IP_ADDRESS_1, IPAddressStatus.RELEASED);
            waitForIPAddressRelease();

            performMaintenanceTask();

            boolean taskCompleted = waitForTaskCompletion(
                    () -> IPAddressStatus.AVAILABLE == getIPAddressStatus(ipAddressState.documentSelfLink));
            if (!taskCompleted) {
                fail(String.format("IP address state for %s did not change within timeout period", IP_ADDRESS_1));
            }
        }

        @Test
        public void testMultipleReleasedIPAddresses() {
            setIPAddressReleasePeriod(RELEASE_INTERVAL_SECONDS);

            IPAddressState ipAddressState1 = createIPAddressInState(IP_ADDRESS_1, IPAddressStatus.RELEASED);
            IPAddressState ipAddressState2 = createIPAddressInState(IP_ADDRESS_2, IPAddressStatus.RELEASED);

            waitForIPAddressRelease();

            performMaintenanceTask();

            boolean taskCompleted = waitForTaskCompletion(
                    () -> IPAddressStatus.AVAILABLE == getIPAddressStatus(ipAddressState1.documentSelfLink) &&
                            IPAddressStatus.AVAILABLE == getIPAddressStatus(ipAddressState2.documentSelfLink));
            if (!taskCompleted) {
                fail("IP address state did not change within timeout period");
            }
        }

        @Test
        public void testReleaseIPAddressesNotInReleasedState() {
            setIPAddressReleasePeriod(RELEASE_INTERVAL_SECONDS);

            IPAddressState ipAddressState1 = createIPAddressInState(IP_ADDRESS_1, IPAddressStatus.ALLOCATED);
            IPAddressState ipAddressState2 = createIPAddressInState(IP_ADDRESS_2, IPAddressStatus.RELEASED);

            waitForIPAddressRelease();

            performMaintenanceTask();

            boolean taskCompleted = waitForTaskCompletion(
                    () -> IPAddressStatus.ALLOCATED == getIPAddressStatus(ipAddressState1.documentSelfLink) &&
                            IPAddressStatus.AVAILABLE == getIPAddressStatus(ipAddressState2.documentSelfLink));
            if (!taskCompleted) {
                fail("IP address state did not change within timeout period");
            }
        }

        @Test
        public void testNewlyReleasedIPAddresses() throws Exception {
            setIPAddressReleasePeriod(100000);

            IPAddressState ipAddressState = createIPAddressInState(IP_ADDRESS_1, IPAddressStatus.RELEASED);
            waitForIPAddressRelease();

            performMaintenanceTask();

            boolean taskCompleted = waitForTaskCompletion(
                    () -> IPAddressStatus.AVAILABLE == getIPAddressStatus(ipAddressState.documentSelfLink));
            if (taskCompleted) {
                fail(String.format("IP address %s changed its state to available, though the IP address state is changed to released before timeout", IP_ADDRESS_1));
            }
        }

        /**
         * Creates an IP address with a specific status.
         * @param ipAddress IP address
         * @param status Status of the IP address
         * @return Created IP address instance
         */
        private IPAddressState createIPAddressInState(String ipAddress, IPAddressStatus status) {
            IPAddressState state = new IPAddressState();
            state.ipAddress = ipAddress;
            state.ipAddressStatus = status;
            state.subnetRangeLink = UUID.randomUUID().toString();

            if (status == IPAddressStatus.ALLOCATED) {
                state.connectedResourceLink = ComputeService.FACTORY_LINK + "/machine-1";
            }

            try {
                return postServiceSynchronously(IPAddressService.FACTORY_LINK, state,
                        IPAddressState.class);
            } catch (Throwable throwable) {
                String message = String.format("Failed to create ip address %s. Exception: %s",
                        ipAddress, throwable.getMessage());
                fail(message);
                return null;
            }
        }

        /**
         * Performs maintenance task on IPAddressReleaseTaskService service
         */
        private void performMaintenanceTask() {
            try {
                this.ipAddressReleaseTaskService.handlePeriodicMaintenance(new Operation());
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                fail(String.format(
                        "Failed to send post to IPAddressReleaseTaskService due to error %s", throwable.getMessage()));
            }

        }

        /**
         * Retrieves IP address resource status of a specific IP address
         * @param ipAddressResourcePath Resource path of the IP address
         * @return Status of the IP address
         */
        private IPAddressStatus getIPAddressStatus(String ipAddressResourcePath) {
            try {
                IPAddressState state = getServiceSynchronously(ipAddressResourcePath, IPAddressState.class);
                return state.ipAddressStatus;
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                fail(String.format(
                        "Failed to get IP address resource at path %s", ipAddressResourcePath));
                return null;
            }
        }

        /**
         * Waits until IP Address release period elapses
         */
        private void waitForIPAddressRelease() {
            long timeInMS = TimeUnit.SECONDS.toMillis(RELEASE_INTERVAL_SECONDS);

            try {
                Thread.sleep(timeInMS);
            } catch (InterruptedException e) {
                e.printStackTrace();
                fail(String.format("Thread failed to sleep for %d milli seconds", timeInMS));
            }
        }

        private boolean waitForTaskCompletion(BooleanSupplier predicate) {
            for (int i = 0; i < TASK_RETRY_TIMEOUT_MS / TASK_RETRY_INTERVAL_MS; i++) {
                try {
                    Thread.sleep(TASK_RETRY_INTERVAL_MS);
                    if (predicate.getAsBoolean()) {
                        return true;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    fail(String.format("Thread failed to sleep for %d milli seconds", TASK_RETRY_INTERVAL_MS));
                    return false;
                }
            }

            return false;
        }

        private void setIPAddressReleasePeriod(long seconds) {
            try {
                TestUtils.setFinalStatic(
                        IPAddressReleaseTaskService.class.getField("IP_ADDRESS_RELEASE_PERIOD_SECONDS"), seconds);
            } catch (Exception e) {
                e.printStackTrace();
                fail(String.format("Failed to change IP_ADDRESS_RELEASE_PERIOD_SECONDS field due to exception: %s", e.getMessage()));
            }
        }
    }
}
