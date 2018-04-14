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

public class CspUriPaths {

    public static final String CSP_BASE_PATH = "/csp/gateway";

    public static final String CSP_DISCOVERY = CSP_BASE_PATH + "/discovery";

    public static final String CSP_USER_TOKEN = CSP_BASE_PATH + "/am/api/auth/token";

    public static final String CSP_AUTHORIZE = CSP_BASE_PATH + "/am/api/auth/authorize";

    public static final String CSP_ORG_SCOPED_REFRESH_TOKEN_AUTHORIZE = CSP_BASE_PATH
            + "/am/api/auth/api-tokens/authorize";

    public static final String CSP_TOKEN_VALIDITY = CSP_BASE_PATH + "/am/api/users/tokens/%s/isValid";

    public static final String CSP_LOGGED_IN_USER = CSP_BASE_PATH + "/am/api/loggedin/user";

    public static final String CSP_LOGOUT_PATH = CSP_BASE_PATH + "/am/api/auth/logout";

    public static final String CSP_LOGGED_IN_USER_ORGS = CSP_LOGGED_IN_USER + "/orgs";

    public static final String CSP_LOGGED_IN_USER_ORG_ROLES = CSP_LOGGED_IN_USER_ORGS + "/%s/roles";

    public static final String CSP_ORG = CSP_BASE_PATH + "/am/api/orgs";

    public static final String CSP_ACCESS_KEY_LOGIN = CSP_BASE_PATH
                                                      + "/am/api/auth/login/accounts/access-keys";

    public static final String CSP_LOGIN = CSP_BASE_PATH + "/am/api/login";

    public static final String DISCOVERY_SERVICE = "/discovery/authn";

    public static final String AUTH_CALLBACK_SERVICE = "/authn/callback";

    public static final String ACCESS_TOKEN_SERVICE = "/access-token";

    public static final String CSP_LOGGED_IN_USER_ORG_SERVICE_ROLES =
            CSP_BASE_PATH + "/am/api/loggedin/user/orgs/%s/service-roles";

    public static final String SESSION_SERVICE = "/authn/session";

    public static final String CSP_PUBLIC_KEY = CSP_BASE_PATH + "/am/api/auth/token-public-key";
}
