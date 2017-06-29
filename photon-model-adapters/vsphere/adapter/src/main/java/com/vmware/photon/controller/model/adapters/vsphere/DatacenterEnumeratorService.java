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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        public List<String> moRefs;
    }

    @Override
    public void handlePatch(Operation patch) {
        if (!patch.hasBody()) {
            patch.fail(new IllegalArgumentException("body is required"));
            return;
        }

        EnumerateDatacentersRequest req = patch.getBody(EnumerateDatacentersRequest.class);

        BasicConnection connection = new BasicConnection();
        try {
            EnumerateDatacentersResponse res = new EnumerateDatacentersResponse();

            if (req.isMock) {
                res.datacenters = Collections.singletonList("dc-1");
                res.moRefs = Collections.singletonList("Datacenter:dc-1");
            } else {
                connection.setURI(URI.create("https://" + req.host + "/sdk"));
                connection.setUsername(req.username);
                connection.setPassword(req.password);
                connection.setIgnoreSslErrors(true);
                connection.connect();

                DatacenterLister lister = new DatacenterLister(connection);
                res.moRefs = new ArrayList<>();
                res.datacenters = new ArrayList<>();

                lister.listAllDatacenters().stream()
                        .forEach(el -> {
                            res.datacenters.add(el.path);
                            res.moRefs.add(VimUtils.convertMoRefToString(el.object));
                        });

            }

            patch.setBody(res);
            patch.complete();
        } catch (Exception e) {
            patch.fail(e);
        } finally {
            connection.closeQuietly();
        }
    }
}
