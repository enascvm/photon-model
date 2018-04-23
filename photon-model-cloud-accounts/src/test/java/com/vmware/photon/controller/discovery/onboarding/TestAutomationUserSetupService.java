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

package com.vmware.photon.controller.discovery.onboarding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.AUTOMATION_USER_EMAIL;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.AUTOMATION_USER_GROUP_NAME;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.AUTOMATION_USER_RESOURCE_GROUP_NAME;

import java.util.EnumSet;

import org.junit.Test;

import com.vmware.photon.controller.discovery.common.CloudAccountConstants;
import com.vmware.photon.controller.discovery.common.SymphonyCommonTestUtils;
import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.ResourceGroupService;
import com.vmware.xenon.services.common.RoleService.RoleState;
import com.vmware.xenon.services.common.UserGroupService;
import com.vmware.xenon.services.common.UserGroupService.UserGroupState;
import com.vmware.xenon.services.common.UserService.UserState;

public class TestAutomationUserSetupService extends BasicTestCase {

    @Override
    public void beforeHostStart(VerificationHost host) {
        host.setAuthorizationEnabled(true);
    }

    @Test
    public void testAutomationUserSetup() throws Throwable {
        OnBoardingTestUtils.startCommonServices(this.host);
        OnBoardingTestUtils.waitForCommonServicesAvailability(this.host, this.host.getUri());
        this.host.setSystemAuthorizationContext();
        this.host.addPrivilegedService(AutomationUserSetupService.class);
        this.host.startService(new AutomationUserSetupService());
        this.host.waitForServiceAvailable(AutomationUserSetupService.SELF_LINK);

        // query artifacts to ensure they have been created as expected
        ServiceDocumentQueryResult res = SymphonyCommonTestUtils.queryDocuments(this.host,
                this.host.getUri(),
                Utils.buildKind(UserState.class), 1);
        UserState userState = Utils.fromJson(res.documents.values().iterator().next(),
                UserState.class);
        assertEquals(userState.email, AUTOMATION_USER_EMAIL);
        res = SymphonyCommonTestUtils.queryDocuments(this.host, this.host.getUri(),
                Utils.buildKind(AuthCredentialsServiceState.class), 1);
        AuthCredentialsServiceState authCredentials = Utils.fromJson(
                res.documents.values().iterator().next(),
                AuthCredentialsServiceState.class);
        assertEquals(authCredentials.userEmail, AUTOMATION_USER_EMAIL);
        res = SymphonyCommonTestUtils
                .queryDocuments(this.host, this.host.getUri(),
                        Utils.buildKind(RoleState.class), 1);
        RoleState roleState = Utils.fromJson(res.documents.values().iterator().next(),
                RoleState.class);
        for (Action action : EnumSet.allOf(Action.class)) {
            assertTrue(roleState.verbs.contains(action));
        }
        assertEquals(roleState.userGroupLink,
                UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, AUTOMATION_USER_GROUP_NAME));
        assertEquals(roleState.resourceGroupLink,
                UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK,
                        AUTOMATION_USER_RESOURCE_GROUP_NAME));
        res = SymphonyCommonTestUtils.queryDocuments(this.host, this.host.getUri(),
                Utils.buildKind(UserGroupState.class), 1);
        UserGroupState userGroupState = Utils.fromJson(res.documents.values().iterator().next(),
                UserGroupState.class);
        assertTrue(userGroupState.query.booleanClauses.size() == 1);
        assertEquals(QuerySpecification.buildCollectionItemName(UserState.FIELD_NAME_EMAIL),
                userGroupState.query.booleanClauses.get(0).term.propertyName);
        assertEquals(CloudAccountConstants.AUTOMATION_USER_EMAIL,
                userGroupState.query.booleanClauses.get(0).term.matchValue);
        this.host.resetSystemAuthorizationContext();
    }

}
