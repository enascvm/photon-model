/*
 * Copyright (c) 2015-2017 VMware, Inc. All Rights Reserved.
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

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import com.google.gson.reflect.TypeToken;

import org.junit.Assert;
import org.junit.Test;

import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionServiceTest;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeServiceTest;
import com.vmware.photon.controller.model.resources.SnapshotService;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class TestVSphereListComputeSnapshotService extends BaseModelTest {

    @Override
    protected void startRequiredServices() throws Throwable {
        // Start PhotonModelServices
        super.startRequiredServices();
        PhotonModelTaskServices.startServices(getHost());

        getHost().waitForServiceAvailable(PhotonModelTaskServices.LINKS);
        PhotonModelAdaptersRegistryAdapters.startServices(getHost());
        VSphereAdaptersTestUtils.startServicesSynchronously(this.host);
    }

    @Test
    public void testGetSnapshotState() throws Throwable {
        ComputeService.ComputeState computeState = createComputeService();
        SnapshotService.SnapshotState startState = createSnapshotService(computeState.documentSelfLink);
        SnapshotService.SnapshotState newState = getServiceSynchronously(
                startState.documentSelfLink,
                SnapshotService.SnapshotState.class);
        assertEquals(newState.name, startState.name);
    }

    @Test
    public void testGetSnapshotStateByCompute() throws Throwable {
        ComputeService.ComputeState computeState = createComputeService();
        SnapshotService.SnapshotState startState = createSnapshotService(computeState.documentSelfLink);
        URI uri = UriUtils.appendQueryParam(
                UriUtils.buildUri(VSphereListComputeSnapshotService.SELF_LINK),
                VSphereListComputeSnapshotService.QUERY_PARAM_COMPUTE, computeState.documentSelfLink);

        Operation operation = sendOperationSynchronously(Operation.createGet(getHost(), uri.toString())
                .setReferer(this.host.getReferer()));
        Assert.assertNotNull(operation);

        String json = Utils.toJson(operation.getBodyRaw());
        List<SnapshotService.SnapshotState> list = Utils.fromJson(json,
                new TypeToken<List<SnapshotService.SnapshotState>>() {}.getType());

        Assert.assertNotNull(list);
        assertEquals(list.get(0).name, startState.name);
    }

    private SnapshotService.SnapshotState createSnapshotService(String link)
            throws Throwable {
        SnapshotService.SnapshotState startState = buildValidStartState(link);
        return postServiceSynchronously(
                SnapshotService.FACTORY_LINK, startState,
                SnapshotService.SnapshotState.class);
    }

    private ComputeService.ComputeState createComputeService()
            throws Throwable {
        ComputeService.ComputeState computeState = ComputeServiceTest.buildValidStartState(
                ComputeDescriptionServiceTest.createComputeDescription(this), false);
        return postServiceSynchronously(
                ComputeService.FACTORY_LINK, computeState,
                ComputeService.ComputeState.class);
    }

    private static SnapshotService.SnapshotState buildValidStartState(String computeLink)
            throws Throwable {
        SnapshotService.SnapshotState st = new SnapshotService.SnapshotState();
        st.id = UUID.randomUUID().toString();
        st.name = "snapshot-name";
        if (computeLink == null || computeLink.isEmpty()) {
            st.computeLink = "compute-link";
        } else {
            st.computeLink = computeLink;
        }
        st.description = "snapshot-description";
        st.customProperties = new HashMap<String, String>() {
            {
                put("defaultKey", "defaultVal");
            }
        };
        return st;
    }

}