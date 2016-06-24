/*
 * Copyright (c) 2015-2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.adapters.gcp.utils;

import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.ONE_HOUR_IN_SECOND;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.TOKEN_REQUEST_URI;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.json.webtoken.JsonWebSignature.Header;
import com.google.api.client.json.webtoken.JsonWebToken.Payload;
import com.google.api.client.util.Joiner;

import com.vmware.xenon.common.Utils;

/**
 * JSON Web Token, which includes a header, a claim set, and a signature.
 * It is used to request the access token for GCP API calls.
 */
public class JSONWebToken {
    private static final String RSA_256 = "RS256";
    private static final String JSON_WEB_TOKEN = "JSONWebToken";
    private static final String SCOPE = "scope";

    private static final Header HEADER = new Header().setAlgorithm(RSA_256).setType(JSON_WEB_TOKEN);
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private String assertion;

    public JSONWebToken(String emailAddress, Collection<String> scopes, PrivateKey privateKey)
            throws GeneralSecurityException, IOException {
        Payload payload = new Payload();
        // Get current time in second.
        long currentTime = TimeUnit.SECONDS.convert(Utils.getNowMicrosUtc(), TimeUnit.MICROSECONDS);
        payload.setIssuer(emailAddress);
        payload.setAudience(TOKEN_REQUEST_URI);
        payload.setIssuedAtTimeSeconds(currentTime);
        // Access token will expire in one hour.
        payload.setExpirationTimeSeconds(currentTime + ONE_HOUR_IN_SECOND);
        payload.put(SCOPE, Joiner.on(' ').join(scopes));
        this.assertion = JsonWebSignature.signUsingRsaSha256(privateKey, JSON_FACTORY, HEADER, payload);
    }

    public String getAssertion() {
        return this.assertion;
    }
}
