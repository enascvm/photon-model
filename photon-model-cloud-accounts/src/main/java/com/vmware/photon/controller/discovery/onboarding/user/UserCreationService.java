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

package com.vmware.photon.controller.discovery.onboarding.user;

import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.callOperations;
import static com.vmware.photon.controller.discovery.onboarding.OnboardingUtils.createRollBackOperation;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils;
import com.vmware.photon.controller.discovery.common.services.UserService;
import com.vmware.photon.controller.discovery.common.services.UserService.UserState;
import com.vmware.photon.controller.discovery.onboarding.OnboardingUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.RequestRouter.Route;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocument.Documentation;
import com.vmware.xenon.common.ServiceDocument.UsageOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Service to setup a user within symphony
 */
public class UserCreationService extends StatelessService {

    public static final String SELF_LINK = UriPaths.PROVISIONING_USER_SERVICE + "/creation";

    public static final String CSP_URI = "cspUri";

    /**
     * Data object to create a user
     */
    public static class UserCreationRequest {
        @Documentation(description = "User email address")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String email;
        @Documentation(description = "User password")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String password;
        @Documentation(description = "First Name")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String firstName;
        @Documentation(description = "Last Name")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String lastName;
    }

    @Override
    public void handleRequest(Operation op) {
        switch (op.getAction()) {
        case POST:
            handlePost(op);
            break;
        default:
            super.handleRequest(op);
        }
    }

    @Override
    public void authorizeRequest(Operation op) {
        // anyone can POST to this service
        if (op.getAction().equals(Action.POST)) {
            op.complete();
            return;
        }
        op.fail(Operation.STATUS_CODE_FORBIDDEN);
    }

    // the entire set of operations below needs to happen as a transaction
    public void handlePost(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalStateException("No body specified"));
            return;
        }
        UserCreationRequest userData = op.getBody(UserCreationRequest.class);
        String cspUri = System.getProperty(CSP_URI);
        String failureMsg = null;
        if (userData.email == null) {
            failureMsg = "email not specified";
        } else if (cspUri == null && userData.password == null) {
            failureMsg = "password not specified";
        } else if (userData.firstName == null) {
            failureMsg = "firstName not specified";
        } else if (userData.lastName == null) {
            failureMsg = "lastName not specified";
        }
        if (failureMsg != null) {
            op.fail(new IllegalArgumentException(failureMsg));
            return;
        }
        UserState userState = new UserState();
        userState.email = userData.email;
        userState.customProperties = new HashMap<String, String>();
        userState.customProperties.put(UserState.FIRST_NAME_PROPERTY_NAME, userData.firstName);
        userState.customProperties.put(UserState.LAST_NAME_PROPERTY_NAME, userData.lastName);
        userState.documentSelfLink = PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData.email);

        // record list of operation associate to the process of user creation for cache.
        Set<Operation> transactionSet = new HashSet<>();

        Operation userOp = Operation
                .createPost(getHost(), UserService.FACTORY_LINK)
                .setBody(userState)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        op.setStatusCode(o.getStatusCode());
                        op.fail(e);
                        return;
                    }
                    UserState returnState = o.getBody(UserState.class);
                    transactionSet.add(createRollBackOperation(this, returnState.documentSelfLink,
                            null, new UserState()));
                    // TODO: postUserTelemetry(this, returnState);
                    createAuthServicesForUser(op, transactionSet);
                });
        setAuthorizationContext(OnboardingUtils.addReplicationQuorumHeader(userOp),
                getSystemAuthorizationContext());
        sendRequest(userOp);
    }

    private void createAuthServicesForUser(Operation op, Set<Operation> transactionSet) {
        UserCreationRequest userData = op.getBody(UserCreationRequest.class);

        // create an auth credentials service for user - this will go away
        // once we have integrated with vIDM. At that point we will have
        // multiple auth sources managed per tenant by vIDM and symphony
        // will just have a UserService instance
        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.userEmail = userData.email;
        auth.documentSelfLink = PhotonControllerCloudAccountUtils.computeHashWithSHA256(userData.email);
        auth.privateKey = userData.password;
        Operation authOp = Operation
                .createPost(getHost(), AuthCredentialsService.FACTORY_LINK)
                .setBody(auth)
                .setCompletion((authPostOp, authPostEx) -> {
                    if (authPostEx != null) {
                        callOperations(this, transactionSet, (operations, failures) -> {
                            if (failures != null) {
                                logWarning("Failed to roll back document %s",
                                        Utils.toString(failures.values().iterator().next()));
                            }
                        });
                        op.fail(authPostEx);
                        return;
                    }
                    transactionSet.add(createRollBackOperation(this, auth.documentSelfLink,
                            null, new AuthCredentialsServiceState()));
                    op.complete();
                });
        setAuthorizationContext(OnboardingUtils.addReplicationQuorumHeader(authOp), getSystemAuthorizationContext());
        sendRequest(authOp);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument d = super.getDocumentTemplate();
        d.documentDescription.serviceRequestRoutes = new HashMap<>();

        Route route = new Route();
        route.action = Action.POST;
        route.description = "Create a new user and configures the user with relevant auth services";
        route.requestType = UserCreationRequest.class;

        d.documentDescription.serviceRequestRoutes
                .put(route.action, Collections.singletonList(route));
        return d;
    }
}
