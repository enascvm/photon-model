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

import com.google.gson.annotations.SerializedName;

public class CspAccessToken {
    /**
     * The access token.
     */
    @SerializedName("access_token")
    public String cspAuthToken;

    /**
     * The refresh token.
     */
    @SerializedName("refresh_token")
    public String cspRefreshToken;

    /**
     * The id token.
     */
    @SerializedName("id_token")
    public String idToken;

    /**
     * The token type.
     */
    @SerializedName("token_type")
    public String tokenType;

    /**
     * The token expiration.
     */
    @SerializedName("expires_in")
    public Integer expiresIn;

    /**
     * The scope
     */
    public String scope;
}
