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

import static com.vmware.photon.controller.discovery.onboarding.OnboardingServices.createResourceGroupForDefaultStatelessServices;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.vmware.photon.controller.discovery.common.CloudAccountConstants;
import com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils;
import com.vmware.photon.controller.discovery.common.SymphonyCommonTestUtils;
import com.vmware.photon.controller.discovery.common.UtilizationThreshold;
import com.vmware.photon.controller.discovery.common.authn.SymphonyBasicAuthenticationService;
import com.vmware.photon.controller.discovery.common.services.OrganizationService;
import com.vmware.photon.controller.discovery.common.services.ProjectService;
import com.vmware.photon.controller.discovery.common.services.ProjectService.ProjectStatus;
import com.vmware.photon.controller.discovery.common.services.ResourceEnumerationService;
import com.vmware.photon.controller.discovery.common.services.ResourcePoolConfigurationService;
import com.vmware.photon.controller.discovery.common.services.UserContextQueryService;
import com.vmware.photon.controller.discovery.common.services.UserService;
import com.vmware.photon.controller.discovery.onboarding.organization.OrganizationCreationService;
import com.vmware.photon.controller.discovery.onboarding.organization.OrganizationCreationService.OrganizationCreationRequest;
import com.vmware.photon.controller.discovery.onboarding.organization.StatelessServiceAccessSetupService;
import com.vmware.photon.controller.discovery.onboarding.project.ProjectCreationTaskService;
import com.vmware.photon.controller.discovery.onboarding.project.ProjectCreationTaskService.ProjectCreationTaskState;
import com.vmware.photon.controller.discovery.onboarding.project.ProjectDeletionTaskService;
import com.vmware.photon.controller.discovery.onboarding.project.ProjectUpdateTaskService;
import com.vmware.photon.controller.discovery.onboarding.project.ProjectUpdateTaskService.ProjectUpdateTaskState;
import com.vmware.photon.controller.discovery.onboarding.user.UserCreationService;
import com.vmware.photon.controller.discovery.onboarding.user.UserCreationService.UserCreationRequest;
import com.vmware.photon.controller.discovery.onboarding.user.UserUpdateService;
import com.vmware.photon.controller.discovery.onboarding.user.UserUpdateService.UserUpdateRequest;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.ResourceGroupService;
import com.vmware.xenon.services.common.RoleService;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.UserGroupService;

public class OnBoardingTestUtils {

    public static void startCommonServices(VerificationHost host) throws Throwable {
        host.setSystemAuthorizationContext();
        host.addPrivilegedService(SymphonyBasicAuthenticationService.class);
        host.startService(new SymphonyBasicAuthenticationService());
        host.waitForServiceAvailable(SymphonyBasicAuthenticationService.SELF_LINK);
        host.addPrivilegedService(UserUpdateService.class);
        host.startService(new UserUpdateService());
        host.waitForServiceAvailable(UserUpdateService.SELF_LINK);
        host.addPrivilegedService(UserCreationService.class);
        host.startService(new UserCreationService());
        host.waitForServiceAvailable(UserCreationService.SELF_LINK);
        host.addPrivilegedService(OrganizationCreationService.class);
        host.startService(new OrganizationCreationService());
        host.waitForServiceAvailable(OrganizationCreationService.SELF_LINK);
        host.startService(new ResourcePoolConfigurationService());
        host.waitForServiceAvailable(ResourcePoolConfigurationService.SELF_LINK);
        host.startService(new ResourceEnumerationService());
        host.waitForServiceAvailable(ResourceEnumerationService.SELF_LINK);
        host.addPrivilegedService(UserContextQueryService.class);
        host.startService(new UserContextQueryService());
        host.waitForServiceAvailable(UserContextQueryService.SELF_LINK);
        host.addPrivilegedService(StatelessServiceAccessSetupService.class);
        host.startService(new StatelessServiceAccessSetupService(op ->
                createResourceGroupForDefaultStatelessServices(host, op)));
        host.waitForServiceAvailable(StatelessServiceAccessSetupService.SELF_LINK);
        // start stateful service factories
        host.addPrivilegedService(UserService.class);
        host.startFactory(new UserService());
        host.addPrivilegedService(ProjectCreationTaskService.class);
        host.startFactory(ProjectCreationTaskService.class,
                () -> TaskFactoryService.create(ProjectCreationTaskService.class));
        host.addPrivilegedService(ProjectUpdateTaskService.class);
        host.startFactory(ProjectUpdateTaskService.class,
                () -> TaskFactoryService.create(ProjectUpdateTaskService.class));
        host.addPrivilegedService(ProjectDeletionTaskService.class);
        host.startFactory(ProjectDeletionTaskService.class,
                () -> TaskFactoryService.create(ProjectDeletionTaskService.class));
        host.startFactory(new OrganizationService());
        host.startFactory(new ProjectService());
        host.resetSystemAuthorizationContext();
    }

    public static void waitForCommonServicesAvailability(VerificationHost host, URI targetUri) throws Throwable {
        List<String> factories = new ArrayList<>();
        factories.add(UserService.FACTORY_LINK);
        factories.add(OrganizationService.FACTORY_LINK);
        factories.add(ProjectCreationTaskService.FACTORY_LINK);
        factories.add(ProjectUpdateTaskService.FACTORY_LINK);
        factories.add(ProjectDeletionTaskService.FACTORY_LINK);
        factories.add(UserGroupService.FACTORY_LINK);
        factories.add(ResourceGroupService.FACTORY_LINK);
        factories.add(RoleService.FACTORY_LINK);
        factories.add(AuthCredentialsService.FACTORY_LINK);
        SymphonyCommonTestUtils.waitForFactoryAvailability(host, targetUri, factories);

    }

    public static VerificationHost setupOnboardingServices(int numNodes) throws Throwable {
        VerificationHost host = VerificationHost.create(0);
        host.setAuthorizationEnabled(true);
        host.start();
        host.setUpPeerHosts(numNodes);
        host.joinNodesAndVerifyConvergence(numNodes, numNodes, true);
        host.setNodeGroupQuorum(numNodes);
        // start provisioning services on all the hosts
        host.setSystemAuthorizationContext();
        for (VerificationHost h : host.getInProcessHostMap().values()) {
            PhotonModelServices.startServices(h);
            PhotonModelTaskServices.startServices(h);
        }
        host.resetSystemAuthorizationContext();
        // start the symphony onboarding services after the provisioning
        // services have been started
        for (VerificationHost h : host.getInProcessHostMap().values()) {
            startCommonServices(h);
        }
        waitForCommonServicesAvailability(host, host.getPeerHostUri());
        return host;
    }

    /**
     * Setup a organization with symphony
     *
     * @param h The Verification host used as the client
     * @param peerUri URI of the symphony server
     * @param orgName org name
     * @param userName user name
     * @param password password
     * @throws Throwable
     */
    public static Operation setupOrganization(VerificationHost h, URI peerUri, String orgName,
            String userName, String password)
            throws Throwable {
        SymphonyCommonTestUtils.authenticate(h, peerUri, userName, password);
        return setupOrganization(h, peerUri, orgName);
    }

    public static Operation setupOrganization(VerificationHost h, URI peerUri, String orgName)
            throws Throwable {
        OrganizationCreationRequest orgData = new OrganizationCreationRequest();
        orgData.orgId = orgName;
        orgData.organizationName = orgName;
        orgData.displayName = orgName + "display name";
        Operation e = h.waitForResponse(Operation
                .createPost(UriUtils.buildUri(peerUri, OrganizationCreationService.SELF_LINK))
                .setBody(orgData)
                .setCompletion(h.getCompletion()));
        return e;
    }

    /**
     * Setup a project with symphony
     *
     * @param h The Verification host used as the client
     * @param peerUri URI of the symphony server
     * @param projectName project name
     * @param orgName org name
     * @param userName user name
     * @param password password
     * @throws Throwable
     */
    public static String setupProject(VerificationHost h, URI peerUri,
            String projectName, String orgName,
            String userName, String password,
            Map<String, UtilizationThreshold> utilizationThresholds)
            throws Throwable {
        SymphonyCommonTestUtils.authenticate(h, peerUri, userName, password);
        ProjectCreationTaskState projectState = new ProjectCreationTaskState();
        projectState.projectName = projectName;
        projectState.budget = new BigDecimal(new Random().nextInt(10000));
        projectState.taskInfo = TaskState.createDirect();
        projectState.organizationLink = UriUtils.buildUriPath(OrganizationService.FACTORY_LINK,
                Utils.computeHash(orgName));
        projectState.utilizationThresholds = utilizationThresholds;

        Operation operation = h.waitForResponse(Operation
                .createPost(UriUtils.buildUri(peerUri, ProjectCreationTaskService.FACTORY_LINK))
                .setBody(projectState)
                .setCompletion(h.getCompletion()));
        return operation.getBody(ProjectCreationTaskState.class).projectLink;
    }

    /**
     * Setup a project with symphony
     *
     * @param h The Verification host used as the client
     * @param peerUri URI of the symphony server
     * @param projectName project name
     * @param orgName org name
     * @param userName user name
     * @param password password
     * @throws Throwable
     */
    public static String setupProject(VerificationHost h, URI peerUri,
            String projectName, String orgName,
            String userName, String password)
            throws Throwable {
        return setupProject(h, peerUri, projectName, orgName, userName, password, null);
    }

    /**
     * Setup a project with symphony and return the operation
     *
     * @param h The Verification host used as the client
     * @param peerUri URI of the symphony server
     * @param projectName project name
     * @param orgName org name
     * @param userName user name
     * @param password password
     * @throws Throwable
     */
    public static Operation setupProjectReturnOp(VerificationHost h, URI peerUri,
            String projectName, String orgName,
            String userName, String password)
            throws Throwable {
        SymphonyCommonTestUtils.authenticate(h, peerUri, userName, password);
        return setupProjectReturnOp(h, peerUri, projectName, orgName);
    }

    public static Operation setupProjectReturnOp(VerificationHost h, URI peerUri,
            String projectName, String orgName)
            throws Throwable {
        ProjectCreationTaskState projectState = new ProjectCreationTaskState();
        projectState.projectName = projectName;
        projectState.budget = new BigDecimal(new Random().nextInt(10000));
        projectState.taskInfo = TaskState.createDirect();
        projectState.organizationLink = UriUtils.buildUriPath(OrganizationService.FACTORY_LINK, Utils.computeHash(orgName));
        return h.waitForResponse(Operation
                .createPost(UriUtils.buildUri(peerUri, ProjectCreationTaskService.FACTORY_LINK))
                .setBody(projectState)
                .setCompletion(h.getCompletion()));
    }

    /**
     * Updates a project with threshold values and status
     */
    public static Operation updateProjectReturnOp(VerificationHost h, URI peerUri,
            String projectLink) throws Throwable {
        String cpuMetric = "CPUUtilizationPercent";
        String memoryMetric = "MemoryUsedPercent";
        ProjectUpdateTaskState projectUpdateState = new ProjectUpdateTaskState();
        projectUpdateState.status = ProjectStatus.ACTIVE;
        UtilizationThreshold threshold = new UtilizationThreshold();
        threshold.overLimit = 80;
        threshold.underLimit = 20;
        Map<String, UtilizationThreshold> thresholdMap = new HashMap<>();
        thresholdMap.put(cpuMetric, threshold);
        thresholdMap.put(memoryMetric, threshold);
        projectUpdateState.utilizationThresholds = thresholdMap;
        projectUpdateState.projectLink = projectLink;
        return h.waitForResponse(Operation
                .createPost(UriUtils.buildUri(peerUri, ProjectUpdateTaskService.FACTORY_LINK))
                .setBody(projectUpdateState)
                .setCompletion(h.getCompletion()));
    }

    /**
     * Add a user to an org or a project
     *
     * @param h The Verification host used as the client
     * @param peerUri URI of the symphony server
     * @param entityLink the org or project link
     * @param email user email
     * @param userName user name
     * @param password password
     * @throws Throwable
     */
    public static Operation addUserToOrgOrProject(VerificationHost h, URI peerUri, String entityLink,
            String email,
            String userName, String password)
            throws Throwable {
        return addUserToOrgOrProject(h, peerUri, entityLink, email, userName, password, false);
    }

    /**
     * Add a user to an org or a project
     *
     * @param h The Verification host used as the client
     * @param peerUri URI of the symphony server
     * @param entityLink the org or project link
     * @param email user email
     * @param userName user name
     * @param password password
     * @param isAdmin add as admin user
     * @throws Throwable
     */
    public static Operation addUserToOrgOrProject(VerificationHost h, URI peerUri, String entityLink,
            String email,
            String userName, String password, boolean isAdmin)
            throws Throwable {
        SymphonyCommonTestUtils.authenticate(h, peerUri, userName, password);
        UserUpdateRequest tenantPatch = new UserUpdateRequest();
        tenantPatch.isAdmin = isAdmin;
        tenantPatch.entityLink = entityLink;
        tenantPatch.email = email;
        return h.waitForResponse(Operation
                .createPost(UriUtils.buildUri(peerUri, UserUpdateService.SELF_LINK))
                .setBody(tenantPatch)
                .setCompletion(h.getCompletion()));
    }

    /**
     * setup a user with symphony
     *
     * @param h The Verification host used as the client
     * @param peerUri URI of the symphony server
     * @param userData user info
     * @throws Throwable
     */
    public static Operation setupUser(VerificationHost h, URI peerUri, UserCreationRequest userData)
            throws Throwable {
        Operation e = h.waitForResponse(Operation
                .createPost(UriUtils.buildUri(peerUri, UserCreationService.SELF_LINK))
                .setBody(userData)
                .setCompletion(h.getCompletion()));
        return e;
    }

    /**
     * Assumes user identity in the context of the org.
     * @throws Throwable
     */
    public static void assumeIdentityWithOrgContext(VerificationHost host, String userId, String orgId) throws Throwable {
        String userLink = UriUtils.buildUriPath(UserService.FACTORY_LINK,
                PhotonControllerCloudAccountUtils.computeHashWithSHA256(userId));
        Map<String, String> propMap = new HashMap<>();
        propMap.put(CloudAccountConstants.CSP_ORG_ID, orgId);
        host.assumeIdentity(userLink, propMap);
    }

    /**
     * Create a UserOnboardingData object
     *
     * @param userEmail user email
     * @param password user password
     */
    public static UserCreationRequest createUserData(String userEmail, String password) {
        UserCreationRequest userData = new UserCreationRequest();
        userData.email = userEmail;
        userData.password = password;
        userData.firstName = userEmail;
        userData.lastName = userEmail;
        return userData;
    }

    /**
     * Create a UserOnboardingData object
     */
    public static UserCreationRequest createUserData(String userEmail, String password,
            String firstName, String lastName) {
        UserCreationRequest userData = new UserCreationRequest();
        userData.email = userEmail;
        userData.password = password;
        userData.firstName = firstName;
        userData.lastName = lastName;
        return userData;
    }
}
