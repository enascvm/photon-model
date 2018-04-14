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

package com.vmware.photon.controller.discovery.csp;

import static com.vmware.photon.controller.discovery.csp.CspConstants.CSP_AUTH_TOKEN;
import static com.vmware.photon.controller.discovery.csp.CspConstants.DEFAULT_CONTEXT_NAME;
import static com.vmware.photon.controller.discovery.csp.CspConstants.HTTP_11_PROTOCOL;
import static com.vmware.photon.controller.discovery.csp.CspConstants.HTTP_2_PROTOCOL;
import static com.vmware.photon.controller.discovery.csp.CspConstants.ORG_LINK_DEPRECATED_PARAM;
import static com.vmware.photon.controller.discovery.csp.CspConstants.REFRESH_TOKEN_PARAM;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import com.google.gson.reflect.TypeToken;

import org.apache.commons.lang3.StringUtils;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.http.netty.NettyChannelContext;
import com.vmware.xenon.common.serialization.JsonMapper;
import com.vmware.xenon.services.common.authn.BasicAuthenticationUtils;

/**
 * Utility class for CSP auth adapter
 */
public class CspAuthenticationUtils {

    private static final String JWT_SEPARATOR = ".";
    public static final String BEARER_AUTH_NAME = "Bearer";
    private static final String BEARER_AUTH_SEPARATOR = " ";
    static final String UNAUTHORIZED_ERROR_MSG = "You are not authorized to make this request";

    private static boolean isSetOrStringDeserializerRegistered = false;

    /**
     * Decodes the payload of the JWT token from CSP
     * @param jwt
     * @return {@code CspTokenData}
     * @throws UnsupportedEncodingException
     */
    public static CspTokenData decodeCspJwtToken(String jwt) throws InvalidTokenException,
            UnsupportedEncodingException {
        int headerIndex = jwt.indexOf(JWT_SEPARATOR, 0);
        if (headerIndex == -1 || headerIndex == 0) {
            throw new InvalidTokenException("Separator for header not found");
        }

        int payloadIndex = jwt.indexOf(JWT_SEPARATOR, headerIndex + 1);
        if (payloadIndex == -1 || payloadIndex == headerIndex + 1) {
            throw new InvalidTokenException("Separator for payload not found");
        }

        // just extract the payload and decode it
        String encodedPayload = jwt.substring(headerIndex + 1, payloadIndex);
        byte[] decodedPayloadBytes = Base64.getDecoder().decode(encodedPayload);
        String decodedPayload = new String(decodedPayloadBytes, Utils.CHARSET);

        if (!isSetOrStringDeserializerRegistered) {
            Utils.registerCustomJsonMapper(CspTokenData.class,
                    new JsonMapper((b) -> b.registerTypeAdapter(
                            SetOrStringDeserializer.SetOrString.class,
                            SetOrStringDeserializer.INSTANCE)));
            isSetOrStringDeserializerRegistered = true;
        }
        return Utils.fromJson(decodedPayload, CspTokenData.class);
    }

    public static Collection<String> parseDocumentRefLinksFromJson(String refLinksJson) {
        Collection<String> refLinks = Utils.getJsonMapValue(refLinksJson, CspConstants.REF_LINKS,
                new TypeToken<List<String>>() {
                }.getType());
        return refLinks;
    }

    public static class InvalidTokenException extends Exception {
        private static final long serialVersionUID = 1L;

        InvalidTokenException(String message) {
            super(message);
        }
    }

    /**
     * Takes in the CSPAuthData and uses it to get a CSP token for the auth details. All responses
     * will have a body object of {@link CspAccessToken}. In the case of username/password or access
     * key login, only the `cspAuthToken` field will be set.
     */
    public static void getCspToken(Service service, CspAuthData cspAuthData, URI cspUri,
            CompletionHandler completionHandler) {

        // If a refresh token is passed in, attempt authentication with that first.
        if (cspAuthData.refreshToken != null) {
            URI uri = UriUtils.buildUri(cspUri, CspUriPaths.CSP_ORG_SCOPED_REFRESH_TOKEN_AUTHORIZE);
            String formBody = String.format("%s=%s", REFRESH_TOKEN_PARAM, cspAuthData.refreshToken);
            Operation.createPost(uri)
                    .setBody(formBody)
                    .setContentType(Operation.MEDIA_TYPE_APPLICATION_X_WWW_FORM_ENCODED)
                    .setCompletion(completionHandler)
                    .sendWith(service);
            return;
        }

        // If not, try the username/password method.
        String orgLink = UriUtils.buildUriPath(CspUriPaths.CSP_ORG, cspAuthData.orgId);
        // Support for explicit org link based login ?org_link=/csp/gateway/am/api/orgs/<orgid>
        URI loginURI = StringUtils.isEmpty(cspAuthData.orgId) ?
                UriUtils.buildUri(cspUri, CspUriPaths.CSP_LOGIN) :
                UriUtils.extendUriWithQuery(UriUtils.buildUri(cspUri, CspUriPaths.CSP_LOGIN),
                        ORG_LINK_DEPRECATED_PARAM, orgLink);
        Operation.createPost(loginURI)
                .setBody(cspAuthData)
                .setContentType(Operation.MEDIA_TYPE_APPLICATION_JSON)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        completionHandler.handle(o, e);
                        return;
                    }

                    // Transform the response to use the CspAccessToken data structure for a common
                    // response.
                    CspAccessToken cspAccessToken = new CspAccessToken();
                    Object tokenData = o.getBodyRaw();
                    cspAccessToken.cspAuthToken = Utils.getJsonMapValue(tokenData,
                            CspConstants.CSP_AUTH_TOKEN_KEY, String.class);

                    o.setBody(cspAccessToken);
                    completionHandler.handle(o, null);
                })
                .sendWith(service);
    }

    /**
     * Retrieves an orgId if one is given in the decoded CSP token. If the orgId given is null or
     * "default", then null is returned.
     * @param cspToken The decoded CSP token data.
     * @return CSP orgId if present. If null or "default", then null will be returned.
     */
    public static String getOrgId(CspTokenData cspToken) {
        if (cspToken.context_name == null || cspToken.context_name.equals(DEFAULT_CONTEXT_NAME)) {
            return null;
        }
        return cspToken.context_name;
    }

    /**
     * Returns the client id, if the given token is a client credentials token.
     */
    public static String getClientId(CspTokenData tokenData) {
        if (isClientCredentials(tokenData)) {
            if (tokenData.azp == null || tokenData.azp.isEmpty()) {
                return tokenData.cid;
            }
            return tokenData.azp;
        }
        return null;
    }

    /**
     * Client credentials have the following characteristics.
     * https://confluence.eng.vmware.com/x/zhlOD
     *
     * sub: clientId (":<client id>")
     * username: In case of client_credentials grant, no username will be defined.
     * domain: In case of client_credentials grant, no domain will be defined.
     * azp: the client id
     *
     * @return true if the token's username field is undefined, and the azp field is defined.
     */
    public static boolean isClientCredentials(CspTokenData tokenData) {
        if (tokenData == null) {
            return false;
        }

        // This is for client credentials to work in DI ("Default Implementation") and Non-DI mode.
        // Bug: https://jira-hzn.eng.vmware.com/browse/CSP-3255
        String clientId = tokenData.cid;
        if (tokenData.cid == null) {
            clientId = tokenData.azp;
        }

        if ((tokenData.username == null || tokenData.username.isEmpty())
                && (clientId != null && !clientId.isEmpty())) {
            return true;
        }

        return false;
    }

    /**
     * Extracts the auth token from the request headers only.
     *
     * @param op Operation context of the request
     * @return auth token for the request
     */
    public static String getAuthToken(Operation op) {
        return getAuthToken(op, false);
    }

    /**
     * Extracts the auth token from the request.
     *
     * @param op Operation context of the request
     * @param lookupCookies Indicates whether cookie values needs to be looked up
     * @return auth token for the request
     */
    public static String getAuthToken(Operation op, boolean lookupCookies) {
        if (lookupCookies) {
            return getAuthTokenFromRequest(op);
        }

        return getAuthTokenFromHeaders(op);
    }

    /**
     * Extracts the auth token from the request. It checks for CSP specific token first and then
     * fallback to xenon auth token.
     *
     * @param op Operation context of the request
     * @return auth token for the request
     */
    private static String getAuthTokenFromHeaders(Operation op) {
        String authHeader = getTokenFromAuthHeader(op);

        if (authHeader != null && !authHeader.isEmpty()) {
            return authHeader;
        }

        String cspAuthTokenHeader = op.getRequestHeader(CSP_AUTH_TOKEN);
        if (cspAuthTokenHeader != null) {
            return cspAuthTokenHeader;
        }

        return op.getRequestHeader(Operation.REQUEST_AUTH_TOKEN_HEADER);
    }

    private static String getTokenFromAuthHeader(Operation op) {
        String authHeader = op.getRequestHeader(Operation.AUTHORIZATION_HEADER);

        if (authHeader == null || authHeader.isEmpty()) {
            return null;
        }

        String[] authHeaderParts = authHeader.split(BEARER_AUTH_SEPARATOR);
        // malformed header
        if (authHeaderParts.length != 2 || !authHeaderParts[0].equalsIgnoreCase(BEARER_AUTH_NAME)) {
            Utils.log(CspAuthenticationUtils.class, CspAuthenticationUtils.class.getSimpleName(),
                    Level.WARNING,
                    "Invalid authorization header format. Expected Authorization: Bearer <token>");
            return null;
        }

        return authHeaderParts[1];
    }

    /**
     * Extracts the auth token from the request. It checks for CSP specific token first and then
     * fallback to xenon auth token. This method additionally looks at the cookie values as well.
     *
     * @param op Operation context of the request
     * @return auth token for the request
     */
    private static String getAuthTokenFromRequest(Operation op) {
        String authHeader = getTokenFromAuthHeader(op);

        if (authHeader != null && !authHeader.isEmpty()) {
            return authHeader;
        }

        Map<String, String> cookies = op.getCookies();
        if (cookies == null) {
            return BasicAuthenticationUtils.getAuthToken(op);
        }

        String token = cookies.get(CSP_AUTH_TOKEN);
        if (token != null && !token.isEmpty()) {
            return token;
        }

        return BasicAuthenticationUtils.getAuthToken(op);
    }

    /**
     * Utility method for constructing a authentication info header from the provided token.
     *
     * See: https://tools.ietf.org/html/rfc2617#section-3.2.3
     *
     * @param token the bearer token
     * @return the authentication info header value;
     */
    public static String constructAuthHeader(String token) {
        if (token == null || token.isEmpty()) {
            Utils.logWarning("Token not specified");
            token = "";
        }
        return "nextnonce=" + token;
    }

    /**
     * Get a text representation of the
     * {@link NettyChannelContext.Protocol} an {@link Operation}
     * is using.
     *
     * @param op An operation.
     */
    static String getOperationHttpProtocol(Operation op) {
        String protocol = null;
        if (op != null) {
            NettyChannelContext nettyChannelContext = (NettyChannelContext) op.getSocketContext();
            if (nettyChannelContext != null && nettyChannelContext.getProtocol() != null) {
                switch (nettyChannelContext.getProtocol()) {
                case HTTP11:
                    protocol = HTTP_11_PROTOCOL;
                    break;
                case HTTP2:
                    protocol = HTTP_2_PROTOCOL;
                    break;
                default:
                    break;
                }
            }
        }
        return protocol;
    }

    /**
     * Common failure response of unauthorized operations. Provides a redirect header to redirect to
     * some redirectUri.
     *
     * @param op The operation to fail.
     * @param redirectUri The redirect URI to send in the www-authenticate response header.
     */
    static void failUnauthorizedOperation(Operation op, String redirectUri) {
        op.addResponseHeader(BasicAuthenticationUtils.WWW_AUTHENTICATE_HEADER_NAME,
                redirectUri);
        ServiceErrorResponse errorResponse = new ServiceErrorResponse();
        errorResponse.message = UNAUTHORIZED_ERROR_MSG;
        errorResponse.statusCode = Operation.STATUS_CODE_UNAUTHORIZED;
        op.fail(errorResponse.statusCode, new Exception(errorResponse.message), errorResponse);
    }
}
