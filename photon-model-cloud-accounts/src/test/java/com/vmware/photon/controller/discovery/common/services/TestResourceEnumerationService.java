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

import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.vmware.photon.controller.discovery.common.services.ResourceEnumerationService.ResourceEnumerationRequest;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;


public class TestResourceEnumerationService extends BasicReusableHostTestCase {

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    public static final int pageSize = 2;

    @Before
    public void setUp() throws Throwable {
        try {
            if (this.host.checkServiceAvailable(ResourceEnumerationService.SELF_LINK)) {
                return;
            }
            PhotonModelServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);

            this.host.startService(new ResourceEnumerationService());
            this.host.waitForServiceAvailable(ResourceEnumerationService.SELF_LINK);

            this.host.startService(new MockEnumerationAdapter());
            this.host.waitForServiceAvailable(MockEnumerationAdapter.SELF_LINK);
        } catch (Throwable e) {
            throw e;
        }
    }

    @BeforeClass
    public static void setUpClass() {
        System.setProperty(ResourceEnumerationService.PROPERTY_NAME_ENUM_QUERY_RESULT_LIMIT,
                String.valueOf(pageSize));
    }

    @Test
    public void testInvalidRequest() throws Throwable {
        this.expectedEx.expect(IllegalArgumentException.class);
        this.expectedEx.expectMessage("resourcePoolState is required");
        ResourceEnumerationRequest initialState = new ResourceEnumerationRequest();
        postResourceEnumerationService(initialState);
    }

    @Test
    public void testMissingResourcePoolTenantLinks() throws Throwable {
        this.expectedEx.expect(IllegalArgumentException.class);
        this.expectedEx.expectMessage("resourcePoolState.tenantLinks is required");
        ResourceEnumerationRequest initialState = new ResourceEnumerationRequest();
        initialState.resourcePoolState = new ResourcePoolState();
        postResourceEnumerationService(initialState);
    }

    @Test
    public void testResourceEnumeration() throws Throwable {
        String pageSizeProp = System.getProperty(
                ResourceEnumerationService.PROPERTY_NAME_ENUM_QUERY_RESULT_LIMIT);
        if (pageSizeProp == null || !pageSizeProp.equals(String.valueOf(pageSize))) {
            // sometimes the setUpClass() method doesn't set the system property,
            // in that case we will log a warning.
            Logger.getAnonymousLogger()
                    .warning(String.format("Incorrect page-size. expected=%s, actual=%s",
                            String.valueOf(pageSize), pageSizeProp));
        }

        ResourcePoolState rp = new ResourcePoolState();
        rp.id = rp.name = "rp-1";
        rp.tenantLinks = Collections.singletonList("project1");
        rp.documentSelfLink = UriUtils.buildUriPath(ResourcePoolService.FACTORY_LINK, rp.id);

        this.host.sendAndWaitExpectSuccess(
                Operation.createPost(this.host, ResourcePoolService.FACTORY_LINK)
                        .setBody(rp));

        ComputeDescription cd = new ComputeDescription();
        cd.enumerationAdapterReference = UriUtils
                .buildUri(this.host.getUri(), MockEnumerationAdapter.SELF_LINK);
        cd.tenantLinks = rp.tenantLinks;
        cd.documentSelfLink = UriUtils.buildUriPath(ComputeDescriptionService.FACTORY_LINK, rp.id);

        this.host.sendAndWaitExpectSuccess(
                Operation.createPost(this.host, ComputeDescriptionService.FACTORY_LINK)
                        .setBody(cd));

        // create three EndpointState documents.
        // during enumeration phase expect two endpoints on first page,
        // and one endpoint on second page.
        // total 3 enumeration tasks should be created.
        int numEndpoints = 3;
        for (int i = 0; i < numEndpoints; i++) {
            ComputeState c = new ComputeState();
            c.descriptionLink = cd.documentSelfLink;
            c.adapterManagementReference = UriUtils.buildUri("http://www.foo.com");
            c.resourcePoolLink = rp.documentSelfLink;
            c.documentSelfLink = UriUtils.buildUriPath(ComputeService.FACTORY_LINK, (rp.id + Integer.toString(i)));
            c.tenantLinks = rp.tenantLinks;

            this.host.sendAndWaitExpectSuccess(
                    Operation.createPost(this.host, ComputeService.FACTORY_LINK)
                            .setBody(c));
            EndpointState e = new EndpointState();
            e.resourcePoolLink = rp.documentSelfLink;
            e.name = EndpointType.aws.name();
            e.endpointType = EndpointType.aws.name();
            e.computeLink = c.documentSelfLink;
            e.computeDescriptionLink = cd.documentSelfLink;
            e.tenantLinks = rp.tenantLinks;
            e.documentSelfLink = UriUtils.buildUriPath(EndpointService.FACTORY_LINK,
                    rp.id + "~" + i);

            this.host.sendAndWaitExpectSuccess(
                    Operation.createPost(this.host, EndpointService.FACTORY_LINK)
                            .setBody(e));
        }

        ResourceEnumerationRequest initialState = new ResourceEnumerationRequest();
        initialState.resourcePoolState = rp;
        postResourceEnumerationService(initialState);

        // verify three enumeration tasks are created
        URI mockEnumAdapterUri = UriUtils.buildUri(host, MockEnumerationAdapter.SELF_LINK);
        host.waitFor("Incorrect number of enumeration tasks.", () -> {
            Map<String, ServiceStat> mockEnumStats = host.getServiceStats(mockEnumAdapterUri);
            ServiceStat invStat = mockEnumStats.get(MockEnumerationAdapter.INVOCATION_STAT_NAME);
            return !(invStat == null || invStat.latestValue < numEndpoints ||
                    invStat.version < numEndpoints);
        });
    }

    @Test
    public void testResourceEnumerationMultipleEndpointsInSameComputeHost() throws Throwable {
        ResourcePoolState rp = new ResourcePoolState();
        rp.id = rp.name = "rp-1";
        rp.tenantLinks = Collections.singletonList("project1");
        rp.documentSelfLink = UriUtils.buildUriPath(ResourcePoolService.FACTORY_LINK, rp.id);

        this.host.sendAndWaitExpectSuccess(
                Operation.createPost(this.host, ResourcePoolService.FACTORY_LINK)
                        .setBody(rp));

        ComputeDescription cd = new ComputeDescription();
        cd.enumerationAdapterReference = UriUtils
                .buildUri(this.host.getUri(), MockEnumerationAdapter.SELF_LINK);
        cd.tenantLinks = rp.tenantLinks;
        cd.documentSelfLink = UriUtils.buildUriPath(ComputeDescriptionService.FACTORY_LINK, rp.id);

        this.host.sendAndWaitExpectSuccess(
                Operation.createPost(this.host, ComputeDescriptionService.FACTORY_LINK)
                        .setBody(cd));

        int numEndpoints = 3;
        ComputeState c = new ComputeState();
        c.descriptionLink = cd.documentSelfLink;
        c.adapterManagementReference = UriUtils.buildUri("http://www.foo.com");
        c.resourcePoolLink = rp.documentSelfLink;
        c.documentSelfLink = UriUtils.buildUriPath(ComputeService.FACTORY_LINK,
                (rp.id));
        c.tenantLinks = rp.tenantLinks;

        // Create three EndpointState documents that belong to the same compute host.
        // Enumerations should be triggered for each one of them.

        for (int i = 0; i < numEndpoints; i++) {
            this.host.sendAndWaitExpectSuccess(
                    Operation.createPost(this.host, ComputeService.FACTORY_LINK)
                            .setBody(c));
            EndpointState e = new EndpointState();
            e.resourcePoolLink = rp.documentSelfLink;
            e.name = EndpointType.aws.name();
            e.endpointType = EndpointType.aws.name();
            e.computeLink = c.documentSelfLink;
            e.computeDescriptionLink = cd.documentSelfLink;
            e.tenantLinks = rp.tenantLinks;
            e.documentSelfLink = UriUtils.buildUriPath(EndpointService.FACTORY_LINK,
                    rp.id + "_" + i);

            this.host.sendAndWaitExpectSuccess(
                    Operation.createPost(this.host, EndpointService.FACTORY_LINK)
                            .setBody(e));
        }

        ResourceEnumerationRequest initialState = new ResourceEnumerationRequest();
        initialState.resourcePoolState = rp;
        postResourceEnumerationService(initialState);

        // verify three enumeration tasks are created
        URI mockEnumAdapterUri = UriUtils.buildUri(host, MockEnumerationAdapter.SELF_LINK);
        host.waitFor("Incorrect number of enumeration tasks.", () -> {
            Map<String, ServiceStat> mockEnumStats = host.getServiceStats(mockEnumAdapterUri);
            ServiceStat invStat = mockEnumStats.get(MockEnumerationAdapter.INVOCATION_STAT_NAME);
            return !(invStat == null || invStat.latestValue < numEndpoints ||
                    invStat.version < numEndpoints);
        });
    }

    private void postResourceEnumerationService(ResourceEnumerationRequest request)
            throws Throwable {
        URI uri = UriUtils
                .buildUri(host, ResourceEnumerationService.class);
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

    public static class MockEnumerationAdapter extends StatelessService {
        public static String SELF_LINK = "/mock-enumeration-adapter";

        public static String INVOCATION_STAT_NAME = "invocation";

        public MockEnumerationAdapter() {
            super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        }

        @Override
        public void handlePatch(Operation op) {
            adjustStat(INVOCATION_STAT_NAME, 1.0);
            op.complete();
            ComputeEnumerateResourceRequest request = op
                    .getBody(ComputeEnumerateResourceRequest.class);
            // add some delay before to patch back.
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            TaskManager taskManager = new TaskManager(this, request.taskReference, null);
            taskManager.finishTask();
        }
    }
}
