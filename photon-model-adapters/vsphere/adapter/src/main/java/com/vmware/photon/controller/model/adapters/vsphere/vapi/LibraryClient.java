/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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
import java.util.List;
import java.util.Map;

import org.apache.http.client.HttpClient;
import org.codehaus.jackson.node.ObjectNode;

import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.VirtualDiskType;
import com.vmware.vim25.VirtualMachineDefinedProfileSpec;

public class LibraryClient extends VapiClient {
    private final String sessionId;

    LibraryClient(URI uri, HttpClient client, String sessionId) {
        super(uri, client);
        this.sessionId = sessionId;
        // this.endpoint = new ServiceEndpoint(uri.toString());
        // this.endpoint.setSessionId(sessionId);
    }

    public List<String> listLibs() throws IOException, RpcException {
        RpcRequest call = newCall("com.vmware.content.library", "list");
        bindToSession(call, this.sessionId);

        call.params.input = newEmptyInput();
        RpcResponse resp = rpc(call);
        throwIfError("Cannot list libraries", resp);

        return toStringList(resp.result);
    }

    public List<String> listItemsInLib(String libId) throws IOException, RpcException {
        RpcRequest call = newCall("com.vmware.content.library.item", "find");
        bindToSession(call, this.sessionId);

        call.params.input = newNode();
        call.params.input
                .putObject(K_STRUCTURE)
                .putObject(K_OPERATION_INPUT)
                .putObject("spec")
                .putObject(K_STRUCTURE)
                .putObject("com.vmware.content.library.item.find_spec")
                .putObject("library_id")
                .put(K_OPTIONAL, libId);

        RpcResponse resp = rpc(call);
        throwIfError("Cannot load get contents of " + libId, resp);

        return toStringList(resp.result);
    }

    public ObjectNode loadLib(String libId) throws IOException, RpcException {
        RpcRequest call = newCall("com.vmware.content.library", "get");
        bindToSession(call, this.sessionId);

        call.params.input = newNode();
        call.params.input
                .putObject(K_STRUCTURE)
                .putObject(K_OPERATION_INPUT)
                .put("library_id", libId);
        RpcResponse resp = rpc(call);
        throwIfError("Cannot load library " + libId, resp);

        ObjectNode result = resp.result;
        if (result == null) {
            return null;
        }

        return (ObjectNode) result
                .get(K_OUTPUT)
                .get(K_STRUCTURE)
                .get("com.vmware.content.library_model");
    }

    public ObjectNode loadItem(String itemId) throws IOException, RpcException {
        RpcRequest call = newCall("com.vmware.content.library.item", "get");
        bindToSession(call, this.sessionId);

        call.params.input = newNode();
        call.params.input
                .putObject(K_STRUCTURE)
                .putObject(K_OPERATION_INPUT)
                .put("library_item_id", itemId);
        RpcResponse resp = rpc(call);
        throwIfError("Cannot load library item " + itemId, resp);

        ObjectNode result = resp.result;
        if (result == null) {
            return null;
        }

        return (ObjectNode) result
                .get(K_OUTPUT)
                .get(K_STRUCTURE)
                .get("com.vmware.content.library.item_model");
    }

    public ObjectNode deployOvfLibItem(
            String itemId,
            String vmName,
            ManagedObjectReference vmFolder,
            ManagedObjectReference datastore,
            VirtualMachineDefinedProfileSpec pbmSpec,
            ManagedObjectReference resourcePool,
            Map<String, String> networkMappings,
            VirtualDiskType diskType) throws IOException, RpcException {

        RpcRequest call = newCall("com.vmware.vcenter.ovf.library_item", "deploy");
        bindToSession(call, this.sessionId);

        call.params.input = newNode();
        ObjectNode opInput = call.params.input
                .putObject(K_STRUCTURE)
                .putObject(K_OPERATION_INPUT);

        ObjectNode target = makeTarget(vmFolder.getValue(), resourcePool.getValue());
        ObjectNode spec = makeSpec(datastore, pbmSpec, vmName, networkMappings, diskType);

        opInput.put("deployment_spec", spec);
        opInput.put("ovf_library_item_id", itemId);
        opInput.put("target", target);

        RpcResponse resp = rpc(call);
        throwIfError("Cannot deploy library item " + itemId, resp);

        ObjectNode result = resp.result;
        if (result == null) {
            return null;
        }

        return (ObjectNode) result
                .get(K_OUTPUT)
                .get(K_STRUCTURE)
                .get("com.vmware.vcenter.ovf.library_item.deployment_result");
    }

    private ObjectNode makeSpec(ManagedObjectReference datastore,
            VirtualMachineDefinedProfileSpec pbmSpec, String vmName,
            Map<String, String> networkMappings,
            VirtualDiskType diskType) {
        ObjectNode res = newNode();
        ObjectNode t = res.putObject(K_STRUCTURE)
                .putObject("com.vmware.vcenter.ovf.library_item.resource_pool_deployment_spec");
        t.put("accept_all_EULA", true);

        if (datastore != null) {
            t.putObject("default_datastore_id").put(K_OPTIONAL, datastore.getValue());
        }
        if (pbmSpec != null) {
            t.putObject("storage_profile_id").put(K_OPTIONAL, pbmSpec.getProfileId());
        }
        if (diskType != null) {
            t.putObject("storage_provisioning").put(K_OPTIONAL, diskType.value());
        }

        t.putObject("name").put(K_OPTIONAL, vmName);
        return res;
    }

    private ObjectNode makeTarget(String folderId, String resourcePoolId) {
        ObjectNode res = newNode();
        ObjectNode t = res.putObject(K_STRUCTURE)
                .putObject("com.vmware.vcenter.ovf.library_item.deployment_target");
        t.putObject("folder_id").put(K_OPTIONAL, folderId);
        t.put("resource_pool_id", resourcePoolId);
        return res;
    }
}
