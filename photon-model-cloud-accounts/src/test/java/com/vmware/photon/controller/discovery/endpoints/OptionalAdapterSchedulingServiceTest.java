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

package com.vmware.photon.controller.discovery.endpoints;

import static com.vmware.photon.controller.model.UriPaths.SERVICE_CONFIG_RULES;
import static com.vmware.photon.controller.model.UriPaths.SERVICE_QUERY_CONFIG_RULES;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.discovery.common.services.CommonServices;
import com.vmware.photon.controller.discovery.common.services.rules.ConfigurationRuleService;
import com.vmware.photon.controller.discovery.common.services.rules.ConfigurationRuleService.ConfigurationRuleState;
import com.vmware.photon.controller.discovery.endpoints.OptionalAdapterSchedulingService.OptionalAdapterSchedulingRequest;
import com.vmware.photon.controller.discovery.endpoints.OptionalAdapterSchedulingService.RequestType;
import com.vmware.photon.controller.discovery.onboarding.OnboardingServices;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.ScheduledTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService;
import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.AuthorizationHelper;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.UserService;

/**
 *
 */
public class OptionalAdapterSchedulingServiceTest extends BasicTestCase {

    private static final String USER_LINK = "/core/authz/users/foo@bar.com";

    private class SpyOptionalAdapterSchedulingService extends OptionalAdapterSchedulingService {

        @Override
        public void handleStart(Operation startPost) {
            subscribeToAdapterFeatureFlagNotifications();
            super.handleStart(startPost);
        }
    }

    @Before
    public void setUp() throws Throwable {
        this.host.setSystemAuthorizationContext();
        Consumer<Class<? extends Service>> classConsumer = this.host::addPrivilegedService;
        CommonServices.startServices(this.host, classConsumer);
        OnboardingServices.startServices(this.host, classConsumer);
        this.host.resetAuthorizationContext();

        this.host.startFactory(new EndpointService());
        this.host.startFactory(new ScheduledTaskService());
        this.host.startFactory(new StatsCollectionTaskService());
        this.host.startFactory(new ResourcePoolService());
        createUser(USER_LINK);

        this.host.assumeIdentity(USER_LINK);
        this.host.startService(new SpyOptionalAdapterSchedulingService());
        classConsumer.accept(SpyOptionalAdapterSchedulingService.class);
        this.host.waitForServiceAvailable(
                SpyOptionalAdapterSchedulingService.SELF_LINK,
                EndpointService.FACTORY_LINK,
                ResourcePoolService.FACTORY_LINK,
                ScheduledTaskService.FACTORY_LINK,
                USER_LINK,
                ServiceUriPaths.CORE_LOCAL_QUERY_TASKS);
    }

    @Test
    public void testOptionalAdapterScheduling() throws Exception {

        // Create relevant inventory entities needed for the test case
        ResourcePoolState rpState = createResourcePool("testRp");
        EndpointState endpointState = createEndpoint(rpState, "testEndpoint");

        // Create a non adapter feature flag rule to test negative scenarios
        createRule("test-rule", Boolean.TRUE.toString());

        // Create adapter feature flag which is enabled by default.
        this.host.assumeIdentity(USER_LINK);
        String adapterReference = endpointState.endpointType + "/a/b/c/foo-adapter";
        String featureFlagKey =
                OptionalAdapterSchedulingService.ADAPTER_FEATURE_FLAG_PREFIX + adapterReference
                        .replaceAll("/", ".");
        createRule(featureFlagKey, Boolean.TRUE.toString());

        // Assert the creation of Scheduled task.
        this.host.assumeIdentity(USER_LINK);
        assertAdapterIsScheduled(adapterReference);

        // Set the feature flag to false and see if the task is deleted.
        this.host.assumeIdentity(USER_LINK);
        updateRule(featureFlagKey, Boolean.FALSE.toString());
        assertAdapterIsUnscheduled();

        // Create another feature flag for and assert if it schedules the corresponding adapter.
        String adapterReference2 = endpointState.endpointType + "/a/b/c/bar-adapter-2";
        String featureFlagKey2 = OptionalAdapterSchedulingService.ADAPTER_FEATURE_FLAG_PREFIX +
                adapterReference2.replaceAll("/", ".");
        createRule(featureFlagKey2, Boolean.TRUE.toString());
        assertAdapterIsScheduled(adapterReference2);

        // Trigger immediate run of all adapter for the endpoint under consideration.
        this.host.assumeIdentity(USER_LINK);
        ServiceDocumentQueryResult triggeredAdapters = triggerAdaptersForEndpoint(endpointState);
        Assert.assertEquals(1, triggeredAdapters.documentLinks.size());

        this.host.assumeIdentity(USER_LINK);
        scheduleAdaptersForEndpoint(endpointState);
        assertAdapterIsScheduled(adapterReference2);

        // Fire an explicit unschedule for all adapter of the resourcepool
        unscheduleAdaptersForResourcePool(endpointState.resourcePoolLink);
        assertAdapterIsUnscheduled();

        // Update the second feature flag to false
        updateRule(featureFlagKey2, Boolean.FALSE.toString());
        triggeredAdapters = triggerAdaptersForEndpoint(endpointState);
        Assert.assertEquals(0, triggeredAdapters.documentLinks.size());

    }

    private EndpointState createEndpoint(ResourcePoolState rpState, String endpointName) throws
            GeneralSecurityException {
        this.host.assumeIdentity(USER_LINK);
        EndpointState endpointState = new EndpointState();
        endpointState.resourcePoolLink = rpState.documentSelfLink;
        endpointState.endpointType = EndpointType.aws.name();
        endpointState.name = endpointName;
        endpointState.documentSelfLink = UriUtils
                .buildUriPath(EndpointService.FACTORY_LINK, endpointState.name);

        Operation endpointOp = Operation.createPost(this.host, EndpointService.FACTORY_LINK)
                .setReferer(this.host.getReferer())
                .setBody(endpointState);
        this.host.sendAndWaitExpectSuccess(endpointOp);
        return endpointState;
    }

    private ResourcePoolState createResourcePool(String rpName) {
        ResourcePoolState rpState = new ResourcePoolState();
        rpState.name = rpName;
        rpState.documentSelfLink = ResourcePoolService.FACTORY_LINK + "/" + rpState.name;
        Operation rpOp = Operation.createPost(this.host, ResourcePoolService.FACTORY_LINK)
                .setReferer(this.host.getReferer())
                .setBody(rpState);
        this.host.sendAndWaitExpectSuccess(rpOp);
        return rpState;
    }

    private ServiceDocumentQueryResult triggerAdaptersForEndpoint(EndpointState endpointState) {
        OptionalAdapterSchedulingRequest request = new OptionalAdapterSchedulingRequest();
        request.endpoint = endpointState;
        request.requestType = RequestType.TRIGGER_IMMEDIATE;
        Operation op = Operation.createPatch(host, SpyOptionalAdapterSchedulingService.SELF_LINK)
                .setBody(request);
        Operation result = this.host.waitForResponse(op);
        return result.getBody(ServiceDocumentQueryResult.class);
    }

    private void scheduleAdaptersForEndpoint(EndpointState endpointState) {
        OptionalAdapterSchedulingRequest request = new OptionalAdapterSchedulingRequest();
        request.endpoint = endpointState;
        request.requestType = RequestType.SCHEDULE;
        Operation op = Operation.createPatch(host, SpyOptionalAdapterSchedulingService.SELF_LINK)
                .setBody(request);
        this.host.sendAndWaitExpectSuccess(op);
    }

    private void unscheduleAdaptersForResourcePool(String rpLink) {
        OptionalAdapterSchedulingRequest request = new OptionalAdapterSchedulingRequest();
        request.resourcePoolLink = rpLink;
        request.requestType = RequestType.UNSCHEDULE;
        Operation op = Operation.createPatch(host, SpyOptionalAdapterSchedulingService.SELF_LINK)
                .setBody(request);
        this.host.sendAndWaitExpectSuccess(op);
    }

    private void assertAdapterIsScheduled(String adapterReference) {
        String timeoutMsg = "Adapter was not scheduled for the resourcepool.";
        this.host.waitFor(timeoutMsg, () -> {
            Operation getTasks = Operation.createGet(this.host, ScheduledTaskService.FACTORY_LINK);
            Operation response = this.host.waitForResponse(getTasks);
            ServiceDocumentQueryResult responseBody = response
                    .getBody(ServiceDocumentQueryResult.class);
            if (responseBody.documentLinks.isEmpty()) {
                return false;
            } else {
                String taskLink = responseBody.documentLinks.get(0);
                Assert.assertTrue(taskLink.contains(UriUtils.getLastPathSegment(adapterReference)));
                return true;
            }
        });
    }

    private void assertAdapterIsUnscheduled() {
        String timeoutMsg = "Adapter was not unscheduled for the resourcepool.";
        this.host.waitFor(timeoutMsg, () -> {
            Operation getTasks = Operation.createGet(this.host, ScheduledTaskService.FACTORY_LINK);
            Operation response = this.host.waitForResponse(getTasks);
            ServiceDocumentQueryResult responseBody = response
                    .getBody(ServiceDocumentQueryResult.class);
            return responseBody.documentLinks.isEmpty();
        });
    }

    private void updateRule(String id, String value) {
        ConfigurationRuleState rule = new ConfigurationRuleState();
        rule.id = id;
        rule.value = value;
        URI uri = UriUtils.buildUri(this.host, SERVICE_CONFIG_RULES + "/" + id);
        this.host.waitForResponse(Operation.createPut(uri).setBody(rule));
    }

    private void createRule(String id, String value) {
        ConfigurationRuleState rule = new ConfigurationRuleState();
        rule.id = id;
        rule.value = value;
        URI uri = UriUtils.buildUri(this.host, ConfigurationRuleService.FACTORY_LINK);
        this.host.waitForResponse(Operation.createPost(uri).setBody(rule));
    }

    private String createUser(String userId) throws Throwable {
        this.host.setSystemAuthorizationContext();

        AuthorizationHelper authHelper = new AuthorizationHelper(this.host);
        String userLink = authHelper.createUserService(this.host, userId);

        // Create user group for guest user
        String userGroupLink = authHelper.createUserGroup(this.host,
                "guest-user-group",
                QueryTask.Query.Builder.create()
                        .addFieldClause(
                                ServiceDocument.FIELD_NAME_SELF_LINK,
                                userLink)
                        .build());
        // Create resource group for example service state
        String exampleServiceResourceGroupLink = authHelper.createResourceGroup(
                this.host,
                "guest-resource-group", QueryTask.Query.Builder.create()
                        .addFieldClause(
                                ServiceDocument.FIELD_NAME_SELF_LINK,
                                SERVICE_QUERY_CONFIG_RULES)
                        .build());

        // Create roles tying these together
        authHelper.createRole(this.host, userGroupLink,
                exampleServiceResourceGroupLink,
                new HashSet<>(Arrays.asList(Service.Action.GET)));

        // Tag the user as a member of the group
        UserService.UserState user = new UserService.UserState();
        user.userGroupLinks = new HashSet<>();
        user.userGroupLinks.add(userGroupLink);

        authHelper.patchUserService(this.host, userLink, user);
        this.host.resetAuthorizationContext();
        return userLink;
    }
}
