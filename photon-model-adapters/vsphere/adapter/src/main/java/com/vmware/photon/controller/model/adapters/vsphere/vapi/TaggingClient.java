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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.http.client.HttpClient;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ObjectNode;

import com.vmware.photon.controller.model.adapters.vsphere.VimUtils;
import com.vmware.vim25.ManagedObjectReference;

/**
 * Talks to tagging service.
 */
public class TaggingClient extends VapiClient {
    private final String sessionId;

    TaggingClient(URI uri, HttpClient client, String sessionId) {
        super(uri, client);
        this.sessionId = sessionId;
    }

    public List<String> getAttachedTags(ManagedObjectReference ref) throws IOException,
            RpcException {
        RpcRequest call = newCall("com.vmware.cis.tagging.tag_association", "list_attached_tags");
        bindToSession(call, this.sessionId);

        call.params.input = newNode();
        call.params.input
                .putObject("STRUCTURE")
                .putObject("operation-input")
                .put("object_id", newDynamicId(ref));

        RpcResponse resp = rpc(call);
        throwIfError("Cannot get tags for object " + VimUtils.convertMoRefToString(ref), resp);

        ObjectNode result = resp.result;
        if (result == null) {
            return Collections.emptyList();
        }
        JsonNode jsonNode = result.get("output");
        if (jsonNode == null) {
            return Collections.emptyList();
        }
        return StreamSupport.stream(jsonNode.spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toList());
    }

    public ObjectNode getTagModel(String tagId) throws IOException, RpcException {
        RpcRequest call = newCall("com.vmware.cis.tagging.tag", "get");
        bindToSession(call, this.sessionId);

        call.params.input = newNode();
        call.params.input
                .putObject("STRUCTURE")
                .putObject("operation-input")
                .put("tag_id", tagId);

        RpcResponse resp = rpc(call);
        throwIfError("Cannot get model for tag " + tagId, resp);

        return (ObjectNode) resp.result
                .get("output")
                .get("STRUCTURE")
                .get("com.vmware.cis.tagging.tag_model");
    }

    public String getCategoryName(String catId) throws IOException, RpcException {
        RpcRequest call = newCall("com.vmware.cis.tagging.category", "get");
        bindToSession(call, this.sessionId);

        call.params.input = newNode();
        call.params.input
                .putObject("STRUCTURE")
                .putObject("operation-input")
                .put("category_id", catId);

        RpcResponse resp = rpc(call);
        throwIfError("Cannot get model for category " + catId, resp);

        return resp.result
                .get("output")
                .get("STRUCTURE")
                .get("com.vmware.cis.tagging.category_model")
                .get("name")
                .asText();
    }
}
