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

package com.vmware.photon.controller.discovery.onboarding.organization;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.vmware.photon.controller.discovery.common.SymphonyCommonTestUtils;
import com.vmware.photon.controller.discovery.onboarding.OnBoardingTestUtils;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSAdapters;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSEnumerationAdapterService;
import com.vmware.photon.controller.model.adapters.azure.AzureAdapters;
import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.ResourceGroupService;
import com.vmware.xenon.services.common.ResourceGroupService.ResourceGroupState;

public class TestStatelessServiceAccessSetupService extends BasicTestCase {

    private static final int NUM_STATELESS_SERVICES = 33;

    @Override
    public void beforeHostStart(VerificationHost host) {
        host.setAuthorizationEnabled(true);
    }

    @Test
    public void testStatelessServiceSetup() throws Throwable {
        OnBoardingTestUtils.startCommonServices(this.host);
        OnBoardingTestUtils.waitForCommonServicesAvailability(this.host, this.host.getUri());
        this.host.setSystemAuthorizationContext();
        // query artifacts to ensure they have been created as expected
        ServiceDocumentQueryResult res = SymphonyCommonTestUtils.queryDocuments(this.host, this.host.getUri(),
                Utils.buildKind(ResourceGroupState.class), 1);
        ResourceGroupState resourceGroupState = Utils.fromJson(res.documents.values().iterator().next(),
                ResourceGroupState.class);

        int statelessServicesCount = AWSAdapters.LINKS.length
                + AWSEnumerationAdapterService.LINKS.length
                + AzureAdapters.LINKS.length
                + AWSEnumerationAdapterService.LINKS.length
                + NUM_STATELESS_SERVICES;

        assertEquals(statelessServicesCount, resourceGroupState.query.booleanClauses.size());
        assertEquals(resourceGroupState.documentSelfLink,
                UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK,
                        StatelessServiceAccessSetupService.STATELESS_SERVICES_FOR_USER_RESOURCE_GROUP));
        this.host.resetSystemAuthorizationContext();
    }

}
