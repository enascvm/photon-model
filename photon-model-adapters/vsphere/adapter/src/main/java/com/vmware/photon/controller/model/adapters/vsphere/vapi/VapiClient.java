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
import java.io.InputStream;
import java.net.URI;
import java.util.UUID;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.codehaus.jackson.map.DeserializationConfig.Feature;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;

import com.vmware.photon.controller.model.adapters.vsphere.vapi.RpcRequest.Params;
import com.vmware.photon.controller.model.adapters.vsphere.vapi.RpcRequest.Params.AppContext;
import com.vmware.photon.controller.model.adapters.vsphere.vapi.RpcRequest.Params.Context;
import com.vmware.photon.controller.model.adapters.vsphere.vapi.RpcRequest.Params.SecurityContext;
import com.vmware.vim25.ManagedObjectReference;

/**
 * Parent of all vapi clients. Defined useful factory methods and handles proper JSON serialization.
 */
public abstract class VapiClient {
    public static final ObjectMapper MAPPER = new ObjectMapper();


    static {
        MAPPER.enable(SerializationConfig.Feature.INDENT_OUTPUT);
        MAPPER.disable(Feature.FAIL_ON_UNKNOWN_PROPERTIES);
        MAPPER.setSerializationInclusion(Inclusion.NON_NULL);
    }

    private static final String METHOD = "invoke";
    private static final String VERSION = "2.0";

    protected final URI uri;

    protected final HttpClient client;

    public VapiClient(URI uri, HttpClient client) {
        this.uri = uri;
        this.client = client;
    }

    protected RpcRequest newCall(String serviceId, String operationId) {
        RpcRequest res = new RpcRequest();
        res.id = newId();
        res.jsonrpc = VERSION;
        res.method = METHOD;

        res.params = new Params();
        res.params.ctx = new Context();
        res.params.ctx.securityCtx = new SecurityContext();
        res.params.ctx.appCtx = new AppContext();
        res.params.ctx.appCtx.opId = newId();

        res.params.serviceId = serviceId;
        res.params.operationId = operationId;
        return res;
    }

    protected void bindToSession(RpcRequest call, String sessionId) {
        call.params.ctx.securityCtx.sessionId = sessionId;
        call.params.ctx.securityCtx.schemeId = "com.vmware.vapi.std.security.session_id";
    }

    protected RpcResponse rpc(RpcRequest request) throws IOException {
        HttpPost post = new HttpPost(this.uri);

        post.setHeader(HttpHeaders.ACCEPT, "application/vnd.vmware.vapi.framed,application/json");
        post.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");

        post.setEntity(new StringEntity(request.toJsonString()));
        HttpResponse httpResponse = this.client.execute(post);

        InputStream stream = httpResponse.getEntity().getContent();
        return MAPPER.readValue(stream, RpcResponse.class);
    }

    protected ObjectNode newNode() {
        return new ObjectNode(JsonNodeFactory.instance);
    }

    protected ObjectNode newEmptyInput() {
        ObjectNode in = newNode();
        in.putObject("STRUCTURE").put("operation-input", newNode());

        return in;
    }

    protected void throwIfError(String msg, RpcResponse resp) throws RpcException {
        if (resp.error != null) {
            throw new RpcException(msg, resp.error);
        }
    }

    protected ObjectNode newDynamicId(ManagedObjectReference ref) {
        ObjectNode res = newNode();

        ObjectNode obj = res
                .putObject("STRUCTURE")
                .putObject("com.vmware.vapi.std.dynamic_ID");

        obj.put("id", ref.getValue());
        obj.put("type", ref.getType());

        return res;
    }

    private String newId() {
        return UUID.randomUUID().toString();
    }

    public static String toJsonString(Object obj) throws IOException {
        return MAPPER.writeValueAsString(obj);
    }
}
