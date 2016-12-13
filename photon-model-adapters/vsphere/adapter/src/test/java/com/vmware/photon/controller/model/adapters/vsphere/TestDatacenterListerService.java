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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;

import org.junit.Test;

import com.vmware.photon.controller.model.adapters.vsphere.DatacenterEnumeratorService.EnumerateDatacentersRequest;
import com.vmware.photon.controller.model.adapters.vsphere.DatacenterEnumeratorService.EnumerateDatacentersResponse;
import com.vmware.xenon.common.Operation;

public class TestDatacenterListerService extends BaseVSphereAdapterTest {

    @Test
    public void listDatacenters() {
        EnumerateDatacentersRequest req = new EnumerateDatacentersRequest();
        req.host = URI.create(vcUrl).getHost();
        req.username = vcUsername;
        req.password = vcPassword;
        req.isMock = isMock();

        Operation op = Operation.createPatch(this.host, VSphereUriPaths.DC_ENUMERATOR_SERVICE)
                .setBody(req);

        op = this.host.waitForResponse(op);
        assertEquals(Operation.STATUS_CODE_OK, op.getStatusCode());
        assertNotNull(op.getBodyRaw());

        if (!isMock()) {
            EnumerateDatacentersResponse resp = op.getBody(EnumerateDatacentersResponse.class);
            assertTrue(resp.datacenters.contains(datacenterId));
        }
    }
}
