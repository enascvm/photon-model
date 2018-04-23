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

package com.vmware.photon.controller.discovery.common.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.vmware.photon.controller.discovery.common.SymphonyCommonTestUtils;
import com.vmware.photon.controller.discovery.common.services.ResourcePoolConfigurationService.ResourcePoolConfigurationRequest;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ResourceGroomerTaskService;
import com.vmware.photon.controller.model.tasks.ScheduledTaskService.ScheduledTaskState;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService;
import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class TestResourcePoolConfigurationService extends BasicReusableHostTestCase {

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Before
    public void setUp() throws Throwable {
        try {
            if (this.host.checkServiceAvailable(ResourcePoolConfigurationService.SELF_LINK)) {
                return;
            }
            PhotonModelServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);

            host.startService(
                    Operation.createPost(UriUtils.buildUri(host,
                            ResourcePoolConfigurationService.class)),
                    new ResourcePoolConfigurationService());
            this.host.waitForServiceAvailable(ResourcePoolConfigurationService.SELF_LINK);
        } catch (Throwable e) {
            throw e;
        }
    }

    @Test
    public void testMissingResourcePool() throws Throwable {
        this.expectedEx.expect(IllegalArgumentException.class);
        this.expectedEx.expectMessage("projectId is required");
        ResourcePoolConfigurationRequest initialState = new ResourcePoolConfigurationRequest();
        postResourcePoolConfigService(initialState);
    }


    @Test
    public void testResourcePoolCreation() throws Throwable {
        ResourcePoolConfigurationRequest initialState = new ResourcePoolConfigurationRequest();
        initialState.projectId = "rp-name";
        postResourcePoolConfigService(initialState);

        ServiceDocumentQueryResult result = SymphonyCommonTestUtils.queryDocuments(this.host,
                this.host.getUri(), Utils.buildKind(ResourcePoolState.class), 1);
        ResourcePoolState resourcePoolState = Utils
                .fromJson(result.documents.values().iterator().next(),
                        ResourcePoolState.class);
        assertEquals(resourcePoolState.name, initialState.projectId);

        // check if stats collection and aggregation is started.
        result = SymphonyCommonTestUtils.queryDocuments(this.host, this.host.getUri(),
                Utils.buildKind(ScheduledTaskState.class), 3);
        for (Object taskObj : result.documents.values()) {
            ScheduledTaskState statsTaskState = Utils
                    .fromJson(taskObj, ScheduledTaskState.class);
            assertTrue(statsTaskState.factoryLink.equals(StatsCollectionTaskService.FACTORY_LINK) ||
                    statsTaskState.factoryLink.equals(ResourceEnumerationService.SELF_LINK) ||
                    statsTaskState.factoryLink.equals(ResourceGroomerTaskService.FACTORY_LINK));
        }
        initialState.requestType = ResourcePoolConfigurationService.ConfigurationRequestType.TEARDOWN;
        postResourcePoolConfigService(initialState);
        result = SymphonyCommonTestUtils.queryDocuments(this.host,
                this.host.getUri(), Utils.buildKind(ResourcePoolState.class), 1);
        result = SymphonyCommonTestUtils.queryDocuments(this.host, this.host.getUri(),
                Utils.buildKind(ScheduledTaskState.class), 1);
        for (Object taskObj : result.documents.values()) {
            ScheduledTaskState statsTaskState = Utils
                    .fromJson(taskObj, ScheduledTaskState.class);
            assertTrue(statsTaskState.factoryLink.equals(ResourceGroomerTaskService.FACTORY_LINK));
        }
    }

    private void postResourcePoolConfigService(ResourcePoolConfigurationRequest request)
            throws Throwable {
        URI uri = UriUtils
                .buildUri(host, ResourcePoolConfigurationService.class);
        host.sendAndWait(Operation.createPost(uri)
                .setBody(request)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    host.completeIteration();
                }));
    }
}
