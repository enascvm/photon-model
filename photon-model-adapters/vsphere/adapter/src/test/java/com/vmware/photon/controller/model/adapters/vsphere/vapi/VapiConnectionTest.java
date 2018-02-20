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
import java.util.HashMap;
import java.util.List;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.photon.controller.model.adapters.vsphere.VimUtils;
import com.vmware.vim25.ManagedObjectReference;

@Ignore
public class VapiConnectionTest {

    @Test
    public void test() throws IOException, RpcException {
        String url = System.getProperty("vapi.url");
        String username = System.getProperty("vc.username");
        String password = System.getProperty("vc.password");

        VapiConnection conn = new VapiConnection(URI.create(url));
        conn.setUsername(username);
        conn.setPassword(password);
        conn.setClient(VapiConnection.newUnsecureHttpClient());

        conn.login();

        System.out.println(conn.getSessionId());

        TaggingClient client = conn.newTaggingClient();

        ManagedObjectReference ref = new ManagedObjectReference();
        ref.setType("HostSystem");
        ref.setValue("host-4121");

        for (String tid : client.getAttachedTags(ref)) {
            ObjectNode model = client.getTagModel(tid);
            System.out.println(model);
            System.out.println(client.getCategoryName(model.get("category_id").asText()));
        }

        LibraryClient libraryClient = conn.newLibraryClient();
        List<String> libs = libraryClient.listLibs();
        for (String lib : libs) {
            System.out.println(libraryClient.loadLib(lib));
            List<String> items = libraryClient.listItemsInLib(lib);
            for (String it : items) {
                System.out.println(libraryClient.loadItem(it));
            }
        }

        ManagedObjectReference folder = VimUtils.convertStringToMoRef("Folder:group-v3");
        ManagedObjectReference ds = VimUtils.convertStringToMoRef("Datastore:datastore-4125");
        ManagedObjectReference rp = VimUtils.convertStringToMoRef("ResourcePool:resgroup-18");
        ObjectNode result = libraryClient
                .deployOvfLibItem("f2de22cb-ac8e-4fa8-933e-1caa70bed721", "test", folder, ds, null,
                        rp, new HashMap<>(), null);

        System.out.println(result);
    }
}
