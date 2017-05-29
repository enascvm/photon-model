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

package com.vmware.photon.controller.model.adapters.azure.ea.enumeration;

import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.ea.AzureEaAdapters;
import com.vmware.photon.controller.model.adapters.azure.ea.enumeration.AzureSubscriptionEndpointCreationService.AzureSubscriptionEndpointCreationRequest;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService.EndpointAllocationTaskState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestRequestSender;


public class AzureSubscriptionEndpointCreationServiceTest extends BasicReusableHostTestCase {

    // Azure EA account enrollment number
    private static final String ENROLLMENT_NUMNBER = "100";
    private static final String API_KEY = "clientKey";
    private final boolean isMock = true;
    private static final String TENANT_ID = "tenantId";
    private static final String ACCOUNT_ID = "accountId";
    private static final String SUBSCRIPTION_ID = "subscriptionId";
    private String eaEndPointLink;

    @Before
    public void setUp() throws Exception {
        try {
            this.host.setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS.toMicros(10));
            PhotonModelServices.startServices(this.host);
            PhotonModelAdaptersRegistryAdapters.startServices(this.host);
            PhotonModelMetricServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            AzureEaAdapters.startServices(this.host);
            this.host.startService(new AzureSubscriptionEndpointCreationService());

            this.host.setTimeoutSeconds(300);

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelAdaptersRegistryAdapters.LINKS);
            this.host.waitForServiceAvailable(AzureEaAdapters.LINKS);
            this.host.waitForServiceAvailable(AzureSubscriptionEndpointCreationService.SELF_LINK);

            EndpointState ep = createEndpointState();

            // Create the Azure Ea Endpoint for the test
            EndpointAllocationTaskState validateEndpoint = new EndpointAllocationTaskState();
            validateEndpoint.endpointState = ep;
            validateEndpoint.options = this.isMock ? EnumSet.of(TaskOption.IS_MOCK) : null;
            validateEndpoint.tenantLinks = Collections.singletonList(TENANT_ID);
            EndpointAllocationTaskState outTask = TestUtils
                    .doPost(this.host, validateEndpoint,
                            EndpointAllocationTaskState.class,
                            UriUtils.buildUri(this.host,
                                    EndpointAllocationTaskService.FACTORY_LINK));

            this.host.waitForFinishedTask(
                    EndpointAllocationTaskState.class,
                    outTask.documentSelfLink);

            EndpointAllocationTaskState taskState = getServiceSynchronously(
                    outTask.documentSelfLink,
                    EndpointAllocationTaskState.class);

            this.eaEndPointLink = taskState.endpointState.documentSelfLink;

        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    @Test
    public void testSubscriptionEndpointCreation() throws Throwable {

        AzureSubscriptionEndpointCreationRequest request = new
                AzureSubscriptionEndpointCreationRequest();
        request.resourceReference = UriUtils.buildUri(this.host, this.eaEndPointLink);
        request.subscriptionId = SUBSCRIPTION_ID;
        request.accountId = ACCOUNT_ID;

        Operation endPointCreationOp = Operation.createPatch(this.host,
                AzureSubscriptionEndpointCreationService.SELF_LINK);
        endPointCreationOp.setBody(request);
        TestRequestSender sender = new TestRequestSender(this.host);
        EndpointState subscriptionEndpoint = sender.sendAndWait(endPointCreationOp,
                EndpointState.class);

        //Assert the subscriptionEndpoint created
        Assert.assertEquals(this.eaEndPointLink, subscriptionEndpoint.parentLink);
        Assert.assertNotNull(subscriptionEndpoint
                .endpointProperties.get(EndpointConfigRequest.USER_LINK_KEY));
        Assert.assertEquals(SUBSCRIPTION_ID, subscriptionEndpoint
                .endpointProperties.get(EndpointConfigRequest.USER_LINK_KEY));

        ComputeState cs = getServiceSynchronously(subscriptionEndpoint.computeLink,
                ComputeState.class);
        Assert.assertNotNull(cs.customProperties.get(AzureConstants.AZURE_ACCOUNT_ID));
        Assert.assertEquals(ACCOUNT_ID, cs.customProperties.get(AzureConstants.AZURE_ACCOUNT_ID));
        Assert.assertNotNull(cs.customProperties.get(AzureConstants.AZURE_SUBSCRIPTION_ID_KEY));
        Assert.assertEquals(SUBSCRIPTION_ID,
                cs.customProperties.get(AzureConstants.AZURE_SUBSCRIPTION_ID_KEY));

    }


    protected <T extends ServiceDocument> T getServiceSynchronously(
            String serviceLink, Class<T> type) throws Throwable {
        return this.host.getServiceState(null, type,
                UriUtils.buildUri(this.host, serviceLink));
    }

    private EndpointService.EndpointState createEndpointState() {
        EndpointService.EndpointState endpoint = new EndpointService.EndpointState();
        endpoint.endpointType = EndpointType.azure_ea.name();
        endpoint.name = EndpointType.azure_ea.name();

        endpoint.endpointProperties = new HashMap<>();
        endpoint.endpointProperties.put(PRIVATE_KEYID_KEY, ENROLLMENT_NUMNBER);
        endpoint.endpointProperties.put(PRIVATE_KEY_KEY, API_KEY);
        return endpoint;
    }

}
