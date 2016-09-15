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

import org.codehaus.jackson.node.ObjectNode;
import org.junit.Ignore;
import org.junit.Test;

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
        conn.setClient(VapiConnection.newUnsecureClient());

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
        conn.close();
    }
}
