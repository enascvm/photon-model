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

package com.vmware.photon.controller.discovery.common;

import static org.junit.Assert.assertNotNull;

import java.net.URI;
import java.util.Base64;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.vmware.photon.controller.discovery.common.authn.SymphonyBasicAuthenticationService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestRequestSender;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.TaskService.TaskServiceState;

public class SymphonyCommonTestUtils {

    /**
     * Authenticate a user against symphony
     * @param h The Verification host used as the client
     * @param peerURI URI of the symphony server
     * @param userName user name
     * @param password password
     * @throws Throwable
     */
    public static void authenticate(VerificationHost h, URI peerURI, String userName, String password)
            throws Throwable {
        // authenticate the user
        String userPassStr = new String(Base64.getEncoder().encode(
                new StringBuffer(userName).append(":").append(password)
                        .toString().getBytes()));
        String headerVal = new StringBuffer("Basic ").append(userPassStr).toString();
        Date expiration = new Date(new Date().getTime()
                + TimeUnit.SECONDS.toMillis(h.getTimeoutSeconds()));
        AtomicBoolean authSucceded = new AtomicBoolean(true);
        do {
            authSucceded.set(true);
            h.sendAndWait(Operation
                    .createPost(UriUtils.buildUri(peerURI, SymphonyBasicAuthenticationService.SELF_LINK))
                    .setBody(new Object())
                    .addRequestHeader(Operation.AUTHORIZATION_HEADER, headerVal)
                    .forceRemote()
                    .setCompletion(
                            (o, e) -> {
                                if (e != null || (o.getStatusCode() != Operation.STATUS_CODE_OK)) {
                                    authSucceded.set(false);
                                }
                                h.completeIteration();
                            }));
            if (authSucceded.get()) {
                return;
            }
        } while (new Date().before(expiration));

        throw new IllegalStateException("Unable to authenticate");
    }

    /**
     * Query for documents of a kind till we have
     * the expected number of documents
     * @param host VerificationHost to use
     * @param remoteUri URI of symphony
     * @param documentKind the documentKnid to query for
     * @param desiredCount expected number of documents
     * @return result of the query
     * @throws Throwable
     */
    public static ServiceDocumentQueryResult queryDocuments(VerificationHost host,
            URI remoteUri, String documentKind, int desiredCount)
            throws Throwable {
        QueryTask.QuerySpecification q = new QueryTask.QuerySpecification();
        q.query.setTermPropertyName(ServiceDocument.FIELD_NAME_KIND).setTermMatchValue(documentKind);
        q.options = EnumSet
                .of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);
        return host.createAndWaitSimpleDirectQuery(remoteUri, q, desiredCount, desiredCount, null);
    }

    /**
     * Wait for factories to be available
     * @param h Verification host
     * @param peerUri uri of the symphony server
     * @param factories factories to wait for
     * @throws Throwable
     */
    public static void waitForFactoryAvailability(VerificationHost h, URI peerUri, List<String> factories) {
        h.setSystemAuthorizationContext();
        for (String factory : factories) {
            h.waitForReplicatedFactoryServiceAvailable(
                    UriUtils.extendUri(peerUri, factory));
        }
        h.resetSystemAuthorizationContext();
    }

    /**
     * Starts and waits for service instance of a given factory to be in FINISHED stage.
     *
     * @param host The verification host
     * @param serviceClass The factory service class
     * @param initialState The initial state of the service instance
     * @param <T> Type of task service state
     */
    public static <T extends TaskServiceState> void startTaskAndWaitForFinishedState(
            VerificationHost host, Class<? extends Service> serviceClass, T initialState)
            throws Throwable {
        TestRequestSender sender = new TestRequestSender(host);

        URI factoryURI = UriUtils.buildFactoryUri(host, serviceClass);
        Operation post = Operation.createPost(factoryURI).setBody(initialState);

        ServiceDocument resultState = sender.sendAndWait(post, ServiceDocument.class);

        assertNotNull(resultState.documentSelfLink);
        host.waitForFinishedTask(initialState.getClass(), resultState.documentSelfLink);
    }

    /**
     * Pulled from {@link com.vmware.xenon.common.TestUtils#isNull(String...)}.
     * @param options
     * @return
     */
    public static boolean isNull(String... options) {
        for (String option : options) {
            if (option == null) {
                return false;
            }
        }
        return true;
    }
}
