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

package com.vmware.photon.controller.discovery.vsphere;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.esotericsoftware.kryo.NotNull;

public class ExecuteCommandRequest {

    @NotNull
    public String commandDefinitionId;

    public String executionId;

    public Map<String, String> variables;

    public Map<String, String> context;

    @NotNull
    public List<String> targetProxies;

    public String callbackUrl;

    public static ExecuteCommandRequestBuilder newBuilder() {
        return new ExecuteCommandRequestBuilder();
    }

    public static class ExecuteCommandRequestBuilder {
        private String commandDefinitionId;
        private String executionId;
        private Map<String, String> variables = new HashMap<>();
        private Map<String, String> context = new HashMap<>();
        private List<String> proxies = new ArrayList<>();
        private String callbackUrl;

        public ExecuteCommandRequestBuilder withCommand(String commandId) {
            this.commandDefinitionId = commandId;
            return this;
        }

        public ExecuteCommandRequestBuilder withVariable(String variable, String value) {
            this.variables.put(variable, value);
            return this;
        }

        public ExecuteCommandRequestBuilder addContext(String key, String value) {
            this.context.put(key, value);
            return this;
        }

        public ExecuteCommandRequestBuilder withTargetProxy(String proxyId) {
            this.proxies.add(proxyId);
            return this;
        }

        public ExecuteCommandRequestBuilder withCallbackUrl(String callbackUrl) {
            this.callbackUrl = callbackUrl;
            return this;
        }

        public ExecuteCommandRequestBuilder withExecutionId(String executionId) {
            this.executionId = executionId;
            return this;
        }

        public ExecuteCommandRequest build() {
            ExecuteCommandRequest req = new ExecuteCommandRequest();
            if (this.commandDefinitionId == null) {
                throw new IllegalArgumentException("Need a valid command id");
            }
            if (this.proxies.isEmpty()) {
                throw new IllegalArgumentException("Need at least one target proxy in which " +
                        "command needs to be executed");
            }
            req.commandDefinitionId = this.commandDefinitionId;
            req.variables = this.variables;
            req.targetProxies = this.proxies;
            req.callbackUrl = this.callbackUrl;
            req.context = this.context;
            req.executionId = this.executionId;
            return req;
        }
    }
}