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

package com.vmware.photon.controller.model.adapters.vsphere.vapi;

import java.io.IOException;
import java.net.URI;

import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates session in vapi session service. Only user_pass scheme is supported.
 */
public class SessionClient extends VapiClient {
    private static final Logger log = LoggerFactory.getLogger(SessionClient.class);

    public SessionClient(URI uri, HttpClient client) {
        super(uri, client);
    }

    public String login(String username, String password) throws IOException, RpcException {
        RpcRequest call = newCall("com.vmware.cis.session", "create");

        call.params.ctx.securityCtx.password = password;
        call.params.ctx.securityCtx.userName = username;
        call.params.ctx.securityCtx.schemeId = "com.vmware.vapi.std.security.user_pass";
        call.params.input = newEmptyInput();

        RpcResponse resp = rpc(call);
        throwIfError("Error creating session", resp);

        return resp.result.get("output").get("SECRET").asText();
    }

    public void logout(String sessionId) {
        RpcRequest call = newCall("com.vmware.cis.session", "logout");
        bindToSession(call, sessionId);

        call.params.input = newEmptyInput();

        try {
            rpc(call);
        } catch (IOException ignore) {
            log.debug("ignoring error", ignore);
        }
    }
}
