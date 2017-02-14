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

package com.vmware.photon.controller.model.adapters.vsphere.network;

import com.vmware.photon.controller.model.adapterapi.NetworkInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.NetworkInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.adapters.vsphere.VSphereUriPaths;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState.TaskStage;

public class DvsNetworkService extends StatelessService {
    public static final String SELF_LINK = VSphereUriPaths.DVS_NETWORK_SERVICE;

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        NetworkInstanceRequest req = op.getBody(NetworkInstanceRequest.class);

        if (req.isMockRequest) {
            new TaskManager(this, req.taskReference).patchTask(TaskStage.FINISHED);
            op.complete();
        }

        if (req.requestType == InstanceRequestType.CREATE) {
            op.complete();
            new CreatePortgroupFlow(this, req).provisionAsync();
            return;
        }

        if (req.requestType == InstanceRequestType.DELETE) {
            op.complete();
            new DeletePortgroupFlow(this, req).provisionAsync();
            return;
        }

        op.fail(new IllegalArgumentException("bad request type"));
    }
}
