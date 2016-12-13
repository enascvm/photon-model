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

package com.vmware.photon.controller.model.adapters.vsphere;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BasicConnection;
import com.vmware.photon.controller.model.adapters.vsphere.util.finders.DatacenterLister;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

public class DatacenterEnumeratorService extends StatelessService {
    public static final String SELF_LINK = VSphereUriPaths.DC_ENUMERATOR_SERVICE;

    public static class EnumerateDatacentersRequest {
        public String host;
        public String username;
        public String password;
        public boolean isMock;
    }

    public static class EnumerateDatacentersResponse {
        public List<String> datacenters;
    }

    @Override
    public void handlePatch(Operation patch) {
        if (!patch.hasBody()) {
            patch.fail(new IllegalArgumentException("body is required"));
            return;
        }

        EnumerateDatacentersRequest req = patch.getBody(EnumerateDatacentersRequest.class);

        if (req.isMock) {
            patch.setBody(Collections.singletonList("dc-1"));
            patch.complete();
            return;
        }

        try {
            BasicConnection connection = new BasicConnection();
            connection.setURI(URI.create("https://" + req.host + "/sdk"));
            connection.setUsername(req.username);
            connection.setPassword(req.password);
            connection.setIgnoreSslErrors(true);
            connection.connect();

            DatacenterLister lister = new DatacenterLister(connection);

            List<String> dcs = lister.listAllDatacenters().stream()
                    .map(el -> el.path)
                    .collect(Collectors.toList());

            EnumerateDatacentersResponse res = new EnumerateDatacentersResponse();
            res.datacenters = dcs;
            patch.setBody(res);
            patch.complete();
        } catch (Exception e) {
            patch.fail(e);
        }
    }
}
