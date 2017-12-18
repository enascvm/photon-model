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
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.EnumSet;
import java.util.UUID;

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
import com.vmware.xenon.common.Utils;

/**
 * This class implements tests for the {@link SessionService} class.
 */
@RunWith(SessionServiceTest.class)
@SuiteClasses({ SessionServiceTest.ConstructorTest.class,
        SessionServiceTest.HandleStartTest.class })
public class SessionServiceTest extends Suite {

    public SessionServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static SessionService.SessionState buildState() {
        SessionService.SessionState state = new SessionService.SessionState();
        state.localToken = UUID.randomUUID().toString();
        state.localTokenExpiry = Utils.getSystemNowMicrosUtc();
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
                    Service.ServiceOption.OWNER_SELECTION);
            assertThat(this.sessionService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Test
        public void testMissingLocalToken() throws Throwable {
            SessionService.SessionState state = buildState();
            state.localToken = null;
            postServiceSynchronously(SessionService.FACTORY_LINK, state,
                    SessionService.SessionState.class, IllegalArgumentException.class);
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
            startState.documentSelfLink = SessionService.FACTORY_LINK + "/" + startState.localToken;

            SessionService.SessionState returnState = postServiceSynchronously(
                    SessionService.FACTORY_LINK, startState, SessionService.SessionState.class);

            assertNotNull(returnState);
            assertThat(startState.externalToken, is(returnState.externalToken));
            assertThat(startState.localTokenExpiry, is(returnState.localTokenExpiry));
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

}