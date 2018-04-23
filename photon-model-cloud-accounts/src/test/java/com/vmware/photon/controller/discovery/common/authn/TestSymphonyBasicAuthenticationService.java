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

package com.vmware.photon.controller.discovery.common.authn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.URI;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.discovery.common.services.UserService;
import com.vmware.photon.controller.discovery.common.services.UserService.UserState;
import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.Claims;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.http.netty.CookieJar;
import com.vmware.xenon.common.test.TestRequestSender;
import com.vmware.xenon.common.test.TestRequestSender.FailureResponse;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.authn.AuthenticationConstants;
import com.vmware.xenon.services.common.authn.AuthenticationRequest;
import com.vmware.xenon.services.common.authn.AuthenticationRequest.AuthenticationRequestType;
import com.vmware.xenon.services.common.authn.BasicAuthenticationUtils;

public class TestSymphonyBasicAuthenticationService extends BasicTestCase {
    private static final String USER = "jane@doe.com";
    private static final String INVALID_USER = "janedoe@doe.com";
    private static final String PASSWORD = "password-for-jane";
    private static final String INVALID_PASSWORD = "invalid-password";
    private static final String BASIC_AUTH_PREFIX = "Basic ";
    private static final String BASIC_AUTH_USER_SEPARATOR = ":";
    private static final String SET_COOKIE_HEADER = "Set-Cookie";

    private String credentialsServiceStateSelfLink;

    @Override
    public void beforeHostStart(VerificationHost h) {
        h.setAuthorizationEnabled(true);
    }

    public static String createUser(VerificationHost host, String userEmail, String privateKey) throws Throwable {
        String[] credentialsServiceStateSelfLink = new String[1];

        // initialize users
        UserState state = new UserState();
        state.email = userEmail;
        AuthCredentialsServiceState authServiceState = new AuthCredentialsServiceState();
        authServiceState.userEmail = userEmail;
        authServiceState.privateKey = privateKey;

        URI userUri = UriUtils.buildUri(host, UserService.FACTORY_LINK);
        Operation userOp = Operation.createPost(userUri)
                .setBody(state)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    host.completeIteration();
                });
        URI authUri = UriUtils.buildUri(host, AuthCredentialsService.FACTORY_LINK);
        Operation authOp = Operation.createPost(authUri)
                .setBody(authServiceState)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    credentialsServiceStateSelfLink[0] = o.getBody(AuthCredentialsServiceState.class).documentSelfLink;
                    host.completeIteration();
                });
        host.testStart(2);
        host.send(userOp);
        host.send(authOp);
        host.testWait();
        return credentialsServiceStateSelfLink[0];
    }

    @Before
    public void setUp() throws Exception {
        try {
            this.host.setSystemAuthorizationContext();
            this.host.addPrivilegedService(SymphonyBasicAuthenticationService.class);
            this.host.startService(new SymphonyBasicAuthenticationService());
            this.host.waitForServiceAvailable(SymphonyBasicAuthenticationService.SELF_LINK);
            host.addPrivilegedService(UserService.class);
            host.startFactory(new UserService());
            this.host.waitForReplicatedFactoryServiceAvailable(UriUtils.buildUri(this.host, UserService.FACTORY_LINK));
            this.host.waitForReplicatedFactoryServiceAvailable(UriUtils.buildUri(this.host, AuthCredentialsService.FACTORY_LINK));

            // initialize users
            this.credentialsServiceStateSelfLink = createUser(this.host, USER, PASSWORD);
        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    @Test
    public void testAuth() throws Throwable {
        this.host.resetAuthorizationContext();
        URI authServiceUri = UriUtils.buildUri(this.host, SymphonyBasicAuthenticationService.SELF_LINK);
        // send a request with no authentication header
        this.host.testStart(1);
        this.host
                .send(Operation
                        .createPost(authServiceUri)
                        .setBody(new Object())
                        .setCompletion(
                                (o, e) -> {
                                    if (e == null) {
                                        this.host.failIteration(new IllegalStateException(
                                                "request should have failed"));
                                        return;
                                    }
                                    if (o.getStatusCode() != Operation.STATUS_CODE_UNAUTHORIZED) {
                                        this.host.failIteration(new IllegalStateException(
                                                "Invalid status code returned"));
                                        return;
                                    }
                                    String authHeader = o
                                            .getResponseHeader(
                                                    BasicAuthenticationUtils.WWW_AUTHENTICATE_HEADER_NAME);
                                    if (authHeader == null
                                            || !authHeader
                                                    .equals(BasicAuthenticationUtils.WWW_AUTHENTICATE_HEADER_VALUE)) {
                                        this.host.failIteration(new IllegalStateException(
                                                "Invalid status code returned"));
                                        return;
                                    }
                                    this.host.completeIteration();
                                }));
        this.host.testWait();

        // send a request with an authentication header for an invalid user
        String userPassStr = new String(Base64.getEncoder().encode(
                new StringBuffer(INVALID_USER).append(BASIC_AUTH_USER_SEPARATOR).append(PASSWORD)
                        .toString().getBytes()));
        String headerVal = new StringBuffer("Basic ").append(userPassStr).toString();
        this.host.testStart(1);
        this.host.send(Operation
                .createPost(authServiceUri)
                .setBody(new Object())
                .addRequestHeader(Operation.AUTHORIZATION_HEADER, headerVal)
                .setCompletion(
                        (o, e) -> {
                            if (e == null) {
                                this.host.failIteration(
                                        new IllegalStateException("request should have failed"));
                                return;
                            }
                            if (o.getStatusCode() != Operation.STATUS_CODE_FORBIDDEN) {
                                this.host.failIteration(new IllegalStateException(
                                        "Invalid status code returned"));
                                return;
                            }
                            this.host.completeIteration();
                        }));
        this.host.testWait();

        // send a request with a malformed authentication header
        userPassStr = new String(Base64.getEncoder().encode(
                new StringBuffer(USER).toString().getBytes()));
        headerVal = new StringBuffer(BASIC_AUTH_PREFIX).append(userPassStr).toString();
        this.host.testStart(1);
        this.host.send(Operation
                .createPost(authServiceUri)
                .setBody(new Object())
                .addRequestHeader(Operation.AUTHORIZATION_HEADER, headerVal)
                .setCompletion(
                        (o, e) -> {
                            if (e == null) {
                                this.host.failIteration(
                                        new IllegalStateException("request should have failed"));
                                return;
                            }
                            if (o.getStatusCode() != Operation.STATUS_CODE_BAD_REQUEST) {
                                this.host.failIteration(new IllegalStateException(
                                        "Invalid status code returned"));
                                return;
                            }
                            this.host.completeIteration();
                        }));
        this.host.testWait();

        // send a request with an invalid password
        userPassStr = new String(Base64.getEncoder().encode(
                new StringBuffer(USER).append(BASIC_AUTH_USER_SEPARATOR).append(INVALID_PASSWORD)
                        .toString().getBytes()));
        headerVal = new StringBuffer(BASIC_AUTH_PREFIX).append(userPassStr).toString();
        this.host.testStart(1);
        this.host.send(Operation
                .createPost(authServiceUri)
                .setBody(new Object())
                .addRequestHeader(Operation.AUTHORIZATION_HEADER, headerVal)
                .setCompletion(
                        (o, e) -> {
                            if (e == null) {
                                this.host.failIteration(
                                        new IllegalStateException("request should have failed"));
                                return;
                            }
                            if (o.getStatusCode() != Operation.STATUS_CODE_FORBIDDEN) {
                                this.host.failIteration(new IllegalStateException(
                                        "Invalid status code returned"));
                                return;
                            }
                            this.host.completeIteration();
                        }));
        this.host.testWait();

        // Next send a valid request
        userPassStr = new String(Base64.getEncoder().encode(
                new StringBuffer(USER).append(BASIC_AUTH_USER_SEPARATOR).append(PASSWORD)
                        .toString().getBytes()));
        headerVal = new StringBuffer(BASIC_AUTH_PREFIX).append(userPassStr).toString();
        this.host.testStart(1);
        this.host.send(Operation
                .createPost(authServiceUri)
                .setBody(new Object())
                .addRequestHeader(Operation.AUTHORIZATION_HEADER, headerVal)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                this.host.failIteration(e);
                                return;
                            }
                            if (o.getStatusCode() != Operation.STATUS_CODE_OK) {
                                this.host.failIteration(new IllegalStateException(
                                        "Invalid status code returned"));
                                return;
                            }
                            if (o.getAuthorizationContext() == null) {
                                this.host.failIteration(new IllegalStateException(
                                        "Authorization context not set"));
                                return;
                            }
                            // now issue a logout
                        AuthenticationRequest request = new AuthenticationRequest();
                        request.requestType = AuthenticationRequestType.LOGOUT;
                        Operation logoutOp = Operation
                                .createPost(authServiceUri)
                                .setBody(request)
                                .forceRemote()
                                .setCompletion(
                                        (oo, ee) -> {
                                            if (ee != null) {
                                                this.host.failIteration(ee);
                                                return;
                                            }
                                            if (oo.getStatusCode() != Operation.STATUS_CODE_OK) {
                                                this.host.failIteration(new IllegalStateException(
                                                        "Invalid status code returned"));
                                                return;
                                            }
                                            this.host.resetAuthorizationContext();
                                            this.host.completeIteration();
                                        });
                        this.host.setAuthorizationContext(o.getAuthorizationContext());
                        this.host.send(logoutOp);
                    }));
        this.host.testWait();

        // Finally, send a valid remote request, and validate the cookie & auth token
        this.host.testStart(1);
        this.host.send(Operation
                .createPost(authServiceUri)
                .setBody(new Object())
                .forceRemote()
                .addRequestHeader(Operation.AUTHORIZATION_HEADER, headerVal)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                this.host.failIteration(e);
                                return;
                            }
                            if (o.getStatusCode() != Operation.STATUS_CODE_OK) {
                                this.host.failIteration(new IllegalStateException(
                                        "Invalid status code returned"));
                                return;
                            }
                            if (!validateAuthToken(o)) {
                                return;
                            }
                            this.host.completeIteration();
                        }));
        this.host.testWait();

    }

    @Test
    public void testAuthExpiration() throws Throwable {
        this.host.resetAuthorizationContext();
        URI authServiceUri = UriUtils.buildUri(this.host, SymphonyBasicAuthenticationService.SELF_LINK);

        // Next send a valid request
        String userPassStr = Base64.getEncoder().encodeToString(
                (USER + BASIC_AUTH_USER_SEPARATOR + PASSWORD).getBytes());
        String headerVal = BASIC_AUTH_PREFIX + userPassStr;

        long oneHourFromNowBeforeAuth =
                TimeUnit.MICROSECONDS.toSeconds(Utils.getSystemNowMicrosUtc())
                        + TimeUnit.HOURS.toSeconds(1);

        // do not specify expiration
        AuthenticationRequest authReq = new AuthenticationRequest();

        this.host.testStart(1);
        this.host.send(Operation
                .createPost(authServiceUri)
                .setBody(authReq)
                .addRequestHeader(Operation.AUTHORIZATION_HEADER, headerVal)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                this.host.failIteration(e);
                                return;
                            }

                            long oneHourFromNowAfterAuth =
                                    TimeUnit.MICROSECONDS.toSeconds(Utils.getSystemNowMicrosUtc())
                                            + TimeUnit.HOURS.toSeconds(1);

                            // default expiration(1hour) must be used
                            validateExpirationTimeRange(o.getAuthorizationContext(),
                                    oneHourFromNowBeforeAuth, oneHourFromNowAfterAuth);

                            this.host.completeIteration();
                        }));
        this.host.testWait();

        // set expiration 1min
        authReq = new AuthenticationRequest();
        authReq.sessionExpirationSeconds = 60L;

        this.host.testStart(1);
        this.host.send(Operation
                .createPost(authServiceUri)
                .setBody(authReq)
                .addRequestHeader(Operation.AUTHORIZATION_HEADER, headerVal)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                this.host.failIteration(e);
                                return;
                            }

                            long tenMinAfterNowInMicro =
                                    TimeUnit.MICROSECONDS.toSeconds(Utils.getSystemNowMicrosUtc())
                                            + TimeUnit.MINUTES.toSeconds(10);

                            // expiration has set to 1min, so it must be before now + 10min
                            validateExpirationTimeRange(o.getAuthorizationContext(),
                                    null, tenMinAfterNowInMicro);

                            this.host.completeIteration();
                        }));
        this.host.testWait();

        // with negative sec
        authReq = new AuthenticationRequest();
        authReq.sessionExpirationSeconds = -1L;

        this.host.testStart(1);
        this.host.send(Operation
                .createPost(authServiceUri)
                .setBody(authReq)
                .addRequestHeader(Operation.AUTHORIZATION_HEADER, headerVal)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                this.host.failIteration(e);
                                return;
                            }

                            // must be before now
                            validateExpirationTimeRange(o.getAuthorizationContext(), null,
                                    TimeUnit.MICROSECONDS.toSeconds(Utils.getSystemNowMicrosUtc()));

                            this.host.completeIteration();
                        }));
        this.host.testWait();

        // with 0
        authReq = new AuthenticationRequest();
        authReq.sessionExpirationSeconds = 0L;

        this.host.testStart(1);
        this.host.send(Operation
                .createPost(authServiceUri)
                .setBody(authReq)
                .addRequestHeader(Operation.AUTHORIZATION_HEADER, headerVal)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                this.host.failIteration(e);
                                return;
                            }

                            // must be before now
                            validateExpirationTimeRange(o.getAuthorizationContext(), null,
                                    TimeUnit.MICROSECONDS.toSeconds(Utils.getSystemNowMicrosUtc()));

                            this.host.completeIteration();
                        }));
        this.host.testWait();

    }

    private void validateExpirationTimeRange(AuthorizationContext authContext, Long fromInMicro,
            Long toInMicro) {
        assertNotNull(authContext);
        assertNotNull(authContext.getClaims());
        assertNotNull(authContext.getClaims().getExpirationTime());
        long expirationInMicro = authContext.getClaims().getExpirationTime();

        if (fromInMicro != null && expirationInMicro < fromInMicro) {
            String msg = String.format("expiration must be greater than %d but was %d", fromInMicro,
                    expirationInMicro);
            this.host.failIteration(new IllegalStateException(msg));
        }

        if (toInMicro != null && toInMicro < expirationInMicro) {
            String msg = String.format("expiration must be less than %d but was %d", toInMicro,
                    expirationInMicro);
            this.host.failIteration(new IllegalStateException(msg));
        }

    }

    @Test
    public void testCustomProperties() throws Throwable {
        String firstProperty = "Property1";
        String firstValue = "Value1";
        String secondProperty = "Property2";
        String secondValue = "Value2";
        String updatedValue = "UpdatedValue";

        // add custom property
        URI authUri = UriUtils.buildUri(this.host, this.credentialsServiceStateSelfLink);
        AuthCredentialsServiceState authServiceState = new AuthCredentialsServiceState();
        Map<String, String> customProperties = new HashMap<String, String>();
        customProperties.put(firstProperty, firstValue);
        authServiceState.customProperties = customProperties;
        Operation addProperty = Operation.createPatch(authUri)
                .setBody(authServiceState)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        this.host.failIteration(e);
                        return;
                    }
                    AuthCredentialsServiceState state = o.getBody(AuthCredentialsServiceState.class);
                    assertEquals("There should be only one custom property", state.customProperties.size(), 1);
                    assertEquals(state.customProperties.get(firstProperty), firstValue);
                    this.host.completeIteration();
                });
        this.host.testStart(1);
        this.host.send(addProperty);
        this.host.testWait();

        // update custom properties

        customProperties.put(firstProperty, updatedValue);
        customProperties.put(secondProperty, secondValue);
        authServiceState.customProperties = customProperties;
        Operation updateProperies = Operation.createPatch(authUri)
                .setBody(authServiceState)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        this.host.failIteration(e);
                        return;
                    }
                    AuthCredentialsServiceState state = o.getBody(AuthCredentialsServiceState.class);
                    assertEquals("There should be two custom properties", state.customProperties.size(), 2);
                    assertEquals(state.customProperties.get(firstProperty), updatedValue);
                    assertEquals(state.customProperties.get(secondProperty), secondValue);
                    this.host.completeIteration();
                });
        this.host.testStart(1);
        this.host.send(updateProperies);
        this.host.testWait();
    }

    @Test
    public void testAuthWithUserInfo() throws Throwable {
        doTestAuthWithUserInfo(false);
        doTestAuthWithUserInfo(true);
    }

    private void doTestAuthWithUserInfo(boolean remote) throws Throwable {
        this.host.resetAuthorizationContext();
        String userPassStr = new StringBuilder(USER).append(BASIC_AUTH_USER_SEPARATOR).append(PASSWORD)
                .toString();
        URI authServiceUri = UriUtils.buildUri(this.host, SymphonyBasicAuthenticationService.SELF_LINK, null, userPassStr);

        this.host.testStart(1);
        Operation post = Operation.createPost(authServiceUri)
                .setBody(new Object())
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                this.host.failIteration(e);
                                return;
                            }
                            if (o.getStatusCode() != Operation.STATUS_CODE_OK) {
                                this.host.failIteration(new IllegalStateException(
                                        "Invalid status code returned"));
                                return;
                            }
                            if (!o.isRemote() && o.getAuthorizationContext() == null) {
                                this.host.failIteration(new IllegalStateException(
                                        "Authorization context not set"));
                                return;
                            }
                            // now issue a logout
                            AuthenticationRequest request = new AuthenticationRequest();
                            request.requestType = AuthenticationRequestType.LOGOUT;
                            Operation logoutOp = Operation
                                    .createPost(authServiceUri)
                                    .setBody(request)
                                    .forceRemote()
                                    .setCompletion(
                                            (oo, ee) -> {
                                                if (ee != null) {
                                                    this.host.failIteration(ee);
                                                    return;
                                                }
                                                if (oo.getStatusCode() != Operation.STATUS_CODE_OK) {
                                                    this.host.failIteration(new IllegalStateException(
                                                            "Invalid status code returned"));
                                                    return;
                                                }
                                                this.host.resetAuthorizationContext();
                                                this.host.completeIteration();
                                            });
                            this.host.setAuthorizationContext(o.getAuthorizationContext());
                            this.host.send(logoutOp);
                        });

        if (remote) {
            post.forceRemote();
        }

        this.host.send(post);
        this.host.testWait();
    }

    private boolean validateAuthToken(Operation op) {
        String cookieHeader = op.getResponseHeader(SET_COOKIE_HEADER);
        if (cookieHeader == null) {
            this.host.failIteration(new IllegalStateException("Missing cookie header"));
            return false;
        }

        Map<String, String> cookieElements = CookieJar.decodeCookies(cookieHeader);
        if (!cookieElements.containsKey(AuthenticationConstants.REQUEST_AUTH_TOKEN_COOKIE)) {
            this.host.failIteration(new IllegalStateException("Missing auth cookie"));
            return false;
        }

        if (op.getResponseHeader(Operation.REQUEST_AUTH_TOKEN_HEADER) == null) {
            this.host.failIteration(new IllegalStateException("Missing auth token"));
            return false;
        }

        String authCookie = cookieElements.get(AuthenticationConstants.REQUEST_AUTH_TOKEN_COOKIE);
        String authToken = op.getResponseHeader(Operation.REQUEST_AUTH_TOKEN_HEADER);

        if (!authCookie.equals(authToken)) {
            this.host.failIteration(new IllegalStateException("Auth token and auth cookie don't match"));
            return false;
        }
        return true;
    }

    @Test
    public void testVerificationInvalidBasicAuthAccessToken() throws Throwable {
        // invalid accesstoken
        String invalidAccessToken = "aasfsfsf";
        TestRequestSender.setAuthToken(invalidAccessToken);
        TestRequestSender sender = new TestRequestSender(this.host);

        // make a request to verification service
        Operation requestOp = Operation.createPost(this.host, SymphonyBasicAuthenticationService.SELF_LINK)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_VERIFY_TOKEN);

        FailureResponse failureResponse = sender.sendAndWaitFailure(requestOp);
        assertNotNull(failureResponse.failure);

        TestRequestSender.clearAuthToken();
    }

    @Test
    public void testVerificationValidAuthServiceToken() throws Throwable {

        TestRequestSender sender = new TestRequestSender(this.host);
        this.host.testStart(1);
        String userPassStr = new String(Base64.getEncoder().encode(
                new StringBuffer(USER).append(BASIC_AUTH_USER_SEPARATOR).append(PASSWORD)
                        .toString().getBytes()));
        String headerVal = new StringBuffer(BASIC_AUTH_PREFIX).append(userPassStr).toString();
        URI authServiceUri = UriUtils.buildUri(this.host, SymphonyBasicAuthenticationService.SELF_LINK);
        sender.sendAndWait(Operation
                .createPost(authServiceUri)
                .setBody(new Object())
                .forceRemote()
                .addRequestHeader(Operation.AUTHORIZATION_HEADER, headerVal));
        // make a request to verification service
        Operation requestOp = Operation.createPost(this.host, SymphonyBasicAuthenticationService.SELF_LINK)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_VERIFY_TOKEN);

        Operation responseOp = sender.sendAndWait(requestOp);
        Claims claims = responseOp.getBody(Claims.class);
        assertNotNull(claims);

        TestRequestSender.clearAuthToken();
    }
}
