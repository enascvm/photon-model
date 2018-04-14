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

public class CspConstants {

    public static final String REDIRECT_URI_PARAM = "redirect_uri";

    public static final String CLIENT_ID_PARAM = "client_id";

    public static final String CLIENT_SECRET_PARAM = "client_secret";

    public static final String AUTH_CODE_PARAM = "code";

    public static final String CSP_ERROR_CODE = "code";

    public static final String STATE_PARAM = "state";

    public static final String CSP_AUTH_TOKEN = "csp-auth-token";

    public static final String REF_LINKS = "refLinks";

    public static final String CSP_AUTH_TOKEN_KEY = "cspAuthToken";

    public static final String CSP_ORG_ID = "orgID";

    /**
     * Org-specific context parameter for a token.
     */
    public static final String ORG_LINK_PARAM = "orgLink";
    public static final String ORG_LINK_DEPRECATED_PARAM = "org_link";

    /**
     * Grant type parameter.
     */
    public static final String GRANT_TYPE_PARAM = "grant_type";

    /**
     * Refresh token parameter.
     */
    public static final String REFRESH_TOKEN_PARAM = "refresh_token";

    /**
     * Action parameter.
     */
    public static final String ACTION_PARAMETER = "action";

    /**
     * Logout action value.
     */
    public static final String LOGOUT_ACTION_VALUE = "logout";

    /**
     * The authorization code grant type.
     */
    public static final String AUTHZ_CODE_GRANT_TYPE = "authorization_code";

    /**
     * The refresh token grant type.
     */
    public static final String REFRESH_TOKEN_GRANT_TYPE = "refresh_token";

    /**
     * The client credentials grant type.
     */
    public static final String CLIENT_CREDENTIALS_GRANT_TYPE = "client_credentials";

    /**
     * Redirect url parameter for authentication callback.
     */
    public static final String CALLBACK_REDIRECT_URL_PARAM = "redirect_url";

    /**
     * The org owner role.
     */
    public static final String ORG_OWNER_ROLE_NAME = "org_owner";

    /**
     * The org member role.
     */
    public static final String ORG_MEMBER_ROLE_NAME = "org_member";

    /**
     * The platform operator role.
     */
    public static final String PLATFORM_OWNER_ROLE_NAME = "platform_operator";

    /**
     * The default context_name.
     */
    public static final String DEFAULT_CONTEXT_NAME = "default";

    /**
     * The Authentication-Info header.
     */
    public static final String AUTHENTICATION_INFO_HEADER = "authentication-info";

    /**
     * HTTP status code 300
     */
    public static final int STATUS_CODE_MULTIPLE_CHOICES = 300;

    /**
     * HTTP status code 303
     */
    public static final int STATUS_CODE_SEE_OTHER = 303;

    /**
     * HTTP/1.1 Protocol
     */
    public static final String HTTP_11_PROTOCOL = "HTTP/1.1";

    /**
     * HTTP/2 Protocol
     */
    public static final String HTTP_2_PROTOCOL = "HTTP/2";

    /**
     * CSP Redirect Error Code No Organization
     */
    public static final String CSP_ERROR_CODE_NO_ORGANIZATION = "1";

    /**
     * CSP Redirect Error Code No Service Roles
     */
    public static final String CSP_ERROR_CODE_NO_SERVICE_ROLES = "2";

    /**
     * CSP Service Link Param
     */
    public static final String CSP_SERVICE_LINK_PARAM = "service";
}
