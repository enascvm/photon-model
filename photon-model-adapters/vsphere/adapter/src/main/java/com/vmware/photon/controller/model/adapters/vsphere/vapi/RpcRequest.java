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

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A bean that mimics the a json-repc 2.0 request.
 */
public class RpcRequest {
    public static class Params {
        public static class Context {
            public SecurityContext securityCtx;
            public AppContext appCtx;
        }

        public static class SecurityContext {
            public String schemeId;
            public String sessionId;
            public String userName;
            public String password;
        }

        public static class AppContext {
            public String opId;
        }

        public Context ctx;
        public ObjectNode input;
        public String operationId;
        public String serviceId;
    }

    public String id;
    public String jsonrpc;
    public String method;
    public Params params;

    public String toJsonString() throws IOException {
        return VapiClient.toJsonString(this);
    }

    @Override
    public String toString() {
        try {
            return toJsonString();
        } catch (IOException e) {
            return super.toString();
        }
    }
}
