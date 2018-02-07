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

package com.vmware.photon.controller.model.adapters.vsphere;

import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.util.EnumSet;
import java.util.concurrent.ExecutionException;

import org.junit.Test;

import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ImageService;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.photon.controller.model.tasks.ImageEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.ImageEnumerationTaskService.ImageEnumerationTaskState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 *
 */
public class TestVSphereImageEnumerationTask extends BaseVSphereAdapterTest {

    private ComputeDescription computeHostDescription;

    private ComputeState computeHost;
    private EndpointState endpoint;

    @Test
    public void testRefresh() throws Throwable {
        // Create a resource pool where the VM will be housed
        this.resourcePool = createResourcePool();

        this.auth = createAuth();

        this.computeHostDescription = createComputeDescription();
        this.computeHost = createComputeHost(this.computeHostDescription);

        EndpointState ep = createEndpointState(this.computeHost, this.computeHostDescription);

        this.endpoint = TestUtils.doPost(this.host, ep, EndpointState.class,
                UriUtils.buildUri(this.host, EndpointService.FACTORY_LINK));

        doRefresh();

        captureFactoryState("initial");

        Query q = Query.Builder.create()
                .addKindFieldClause(ImageState.class)
                .build();

        ImageState anImage = null;
        try {
            anImage = findFirstMatching(q, ImageState.class);
        } catch (Exception ignore) {

        }

        if (anImage != null) {
            assertFalse(anImage.tenantLinks.isEmpty());
        }

        doRefresh();

        captureFactoryState("updated");
    }

    protected void captureFactoryState(String marker)
            throws ExecutionException, InterruptedException, IOException {
        snapshotFactoryState(marker, ImageService.class);
    }

    private void doRefresh() throws Throwable {
        ImageEnumerationTaskState task = new ImageEnumerationTaskState();

        if (isMock()) {
            task.options = EnumSet.of(TaskOption.IS_MOCK);
        }
        task.enumerationAction = EnumerationAction.REFRESH;
        task.endpointLink = this.endpoint.documentSelfLink;

        ImageEnumerationTaskState outTask = TestUtils.doPost(this.host,
                task,
                ImageEnumerationTaskState.class,
                UriUtils.buildUri(this.host,
                        ImageEnumerationTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(ImageEnumerationTaskState.class, outTask.documentSelfLink);
    }
}
