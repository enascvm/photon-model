/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.resources;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.Utils;

/**
 * This class implements tests for the {@link SessionService} class.
 */
@RunWith(SessionServiceTest.class)
@SuiteClasses({ SessionServiceTest.ConstructorTest.class,
        SessionServiceTest.HandleStartTest.class,
        SessionServiceTest.HandlePutTest.class,
        SessionServiceTest.HandleMaintenanceTest.class })
public class SessionServiceTest extends Suite {

    public SessionServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static SessionService.SessionState buildState() {
        SessionService.SessionState state = new SessionService.SessionState();
        state.localToken = UUID.randomUUID().toString();
        state.externalToken = UUID.randomUUID().toString();

        return state;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {
        private SessionService sessionService = new SessionService();

        @Before
        public void setupTest() {
            this.sessionService = new SessionService();
        }

        @Test
        public void testServiceOptions() {
            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.IDEMPOTENT_POST);
            assertThat(this.sessionService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {

        @Test
        public void testSelfLinkContract() throws Throwable {
            SessionService.SessionState startState = buildState();
            SessionService.SessionState returnState = postServiceSynchronously(
                    SessionService.FACTORY_LINK, startState, SessionService.SessionState.class);

            String computedSelfLink = SessionService.FACTORY_LINK + "/" + Utils.computeHash
                    (startState.localToken);
            assertThat(returnState.documentSelfLink, is(computedSelfLink));
        }

        @Test
        public void testSetCustomExpirationTime() throws Throwable {
            SessionService.SessionState startState = buildState();
            startState.documentExpirationTimeMicros = Utils.getSystemNowMicrosUtc() + 1L;
            SessionService.SessionState returnState = postServiceSynchronously(SessionService
                    .FACTORY_LINK, startState,SessionService.SessionState.class);

            assertNotNull(returnState);
            assertThat(startState.documentExpirationTimeMicros, is(returnState
                    .documentExpirationTimeMicros));
        }

        @Test
        public void testDefaultExpirationTime() throws Throwable {
            SessionService.SessionState startState = buildState();
            long t1 = Utils.fromNowMicrosUtc(SessionService.DEFAULT_SESSION_EXPIRATION_MICROS);
            SessionService.SessionState returnState = postServiceSynchronously(SessionService
                    .FACTORY_LINK, startState,SessionService.SessionState.class);
            long t2 = Utils.fromNowMicrosUtc(SessionService.DEFAULT_SESSION_EXPIRATION_MICROS);

            assertNotNull(returnState);
            assertThat(returnState.documentExpirationTimeMicros, greaterThanOrEqualTo(t1));
            assertThat(returnState.documentExpirationTimeMicros, lessThanOrEqualTo(t2));
        }

        @Test
        public void testMissingLocalToken() throws Throwable {
            SessionService.SessionState state = buildState();
            state.localToken = null;
            postServiceSynchronously(SessionService.FACTORY_LINK, state,
                    SessionService.SessionState.class, NullPointerException.class);
        }

        @Test
        public void testMissingExternalToken() throws Throwable {
            SessionService.SessionState state = buildState();
            state.externalToken = null;
            postServiceSynchronously(SessionService.FACTORY_LINK, state,
                    SessionService.SessionState.class, IllegalArgumentException.class);
        }

        @Test
        public void testCreateSessionState() throws Throwable {
            SessionService.SessionState startState = buildState();
            SessionService.SessionState returnState = postServiceSynchronously(
                    SessionService.FACTORY_LINK, startState, SessionService.SessionState.class);

            assertNotNull(returnState);
            assertThat(startState.externalToken, is(returnState.externalToken));
            assertThat(startState.localToken, is(returnState.localToken));
        }

        @Test
        public void testUnauthorizedRequest() {
            host.testStart(1);
            host.sendWithDeferredResult(Operation.createGet(host, SessionService.FACTORY_LINK))
                    .whenComplete((o, e) -> {
                        if (e != null) {
                            host.failIteration(e);
                            return;
                        }
                        host.completeIteration();
                        assertEquals(o.getStatusCode(), Operation.STATUS_CODE_UNAUTHORIZED);
                    });
            host.testWait();
        }

    }

    /**
     * This class implements tests for the handlePut method.
     */
    public static class HandlePutTest extends BaseModelTest {
        @Test
        /*
         * For example when a multithreaded program tries to store same data more than once
         */
        public void testSameDataSameState() throws Throwable {
            SessionService.SessionState state = buildState();
            SessionService.SessionState stateOne = postServiceSynchronously(
                    SessionService.FACTORY_LINK, state, SessionService.SessionState.class);
            SessionService.SessionState stateTwo = postServiceSynchronously(
                    SessionService.FACTORY_LINK, state, SessionService.SessionState.class);

            assertThat(stateOne.localToken, is(stateTwo.localToken));
            assertThat(stateOne.documentExpirationTimeMicros, is(stateTwo
                    .documentExpirationTimeMicros));
            assertThat(stateOne.externalToken, is(stateTwo.externalToken));

            // Since the documentSelfLink is computed, the state itself --must-- be the same, not
            // only the data
            assertThat(stateOne.documentSelfLink, is(stateTwo.documentSelfLink));
        }

        @Test
        public void testStateIsImmutable() throws Throwable {
            SessionService.SessionState startState = postServiceSynchronously(
                    SessionService.FACTORY_LINK, buildState(), SessionService.SessionState.class);
            startState.externalToken = UUID.randomUUID().toString();
            SessionService.SessionState endState = postServiceSynchronously(
                    SessionService.FACTORY_LINK, startState, SessionService.SessionState.class);

            assertThat(startState.externalToken, is(endState.externalToken));
        }

        @Test
        public void testNormalPut() throws Throwable {
            SessionService.SessionState state = postServiceSynchronously(
                    SessionService.FACTORY_LINK, buildState(), SessionService.SessionState.class);

            Operation put = Operation.createPut(host, state.documentSelfLink).setBody(state);

            host.testStart(1);
            host.sendWithDeferredResult(put)
                    .whenComplete((o, e) -> {
                        if (e != null) {
                            host.completeIteration();
                            assertEquals(put.getStatusCode(), Operation.STATUS_CODE_BAD_METHOD);
                            return;
                        }
                        host.failIteration(new IllegalStateException("The PUT operation was "
                                + "expected to fail but was successful"));
                    });
            host.testWait();
        }

    }

    /**
     * This class implements tests for the handleMaintenance method.
     */
    public static class HandleMaintenanceTest extends BaseModelTest {
        private SessionService sessionService = new SessionService();

        @Before
        public void setupTest() {
            this.sessionService = new SessionService();
            this.sessionService.setHost(this.getHost());
        }

        @Test
        public void testRemoveExpiredSessions() throws Throwable {
            SessionService.SessionState state = buildState();
            state.documentExpirationTimeMicros = Utils.getSystemNowMicrosUtc() - TimeUnit.SECONDS
                    .toMicros(1);

            SessionService.SessionState startState = postServiceSynchronously(
                    SessionService.FACTORY_LINK, state, SessionService.SessionState.class);

            // Set the maintenance interval to the minimum allowed by Xenon
            this.sessionService.setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS.toMicros(1L));
            this.sessionService.handlePeriodicMaintenance(new Operation());

            Thread.sleep(TimeUnit.MILLISECONDS.toMicros(2L));

            getServiceSynchronously(startState.documentSelfLink, SessionService.SessionState.class,
                    ServiceNotFoundException.class);
        }

    }

}