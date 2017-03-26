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

import java.util.UUID;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

public class TestAWSReservedInstancePlansService extends BasicTestCase {

    public static String COMPUTE_SELF_LINK = UUID.randomUUID().toString();

    @Before
    public void setUp() {
        host.startService(new MockAWSReservedInstancePlansService());
    }

    @After
    public void tearDown() throws Exception {
        if (this.host == null) {
            return;
        }
        this.host.tearDownInProcessPeers();
        this.host.toggleNegativeTestMode(false);
        this.host.tearDown();
    }

    @Test
    public void test() throws Throwable {
        StatelessService parentService = new StatelessService() {
            @Override
            public void handleRequest(Operation op) {
                if (op.getAction() == Action.PATCH) {
                    ComputeService.ComputeState state = op
                            .getBody(ComputeService.ComputeState.class);
                    Assert.assertNotNull(state.customProperties);
                    Assert.assertNotNull(
                            state.customProperties.get(AWSConstants.RESERVED_INSTANCE_PLAN_DETAILS));
                    TestAWSReservedInstancePlansService.this.host.completeIteration();
                }
            }
        };
        String servicePath = COMPUTE_SELF_LINK;
        Operation startOp = Operation.createPost(UriUtils.buildUri(this.host, servicePath));
        this.host.startService(startOp, parentService);

        this.host.sendAndWait(Operation.createPost(UriUtils.buildUri(
                this.host, MockAWSReservedInstancePlansService.SELF_LINK))
                .setBody(COMPUTE_SELF_LINK)
                .setReferer(this.host.getUri()));
    }
}
