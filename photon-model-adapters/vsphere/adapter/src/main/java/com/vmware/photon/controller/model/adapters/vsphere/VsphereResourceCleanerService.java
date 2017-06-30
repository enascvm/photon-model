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

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.LifecycleState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;

/**
 * This service coordinated non-trivial resource clean-ups for vc. In particular hosts that contain no VMs
 * are always removed regardless if
 * @{{@link com.vmware.photon.controller.model.tasks.TaskOption#PRESERVE_MISSING_RESOUCES}}
 * is set.
 */
public class VsphereResourceCleanerService extends StatelessService {
    public static final String SELF_LINK = VSphereUriPaths.RESOURCE_CLEANER;

    public static class ResourceCleanRequest {
        public String resourceLink;
    }

    @Override
    public void handlePatch(Operation patch) {
        if (!patch.hasBody()) {
            patch.fail(new IllegalArgumentException("body is required"));
            return;
        }

        ResourceCleanRequest req = patch.getBody(ResourceCleanRequest.class);

        if (req.resourceLink == null || req.resourceLink.isEmpty() || !req.resourceLink
                .startsWith(ComputeService.FACTORY_LINK)) {
            patch.complete();
            return;
        }

        Operation.createGet(this, req.resourceLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        patch.complete();
                        return;
                    }
                    ComputeState compute = o.getBody(ComputeState.class);

                    if (VimNames.TYPE_VM.equals(compute.customProperties.get(CustomProperties.TYPE))) {
                        patchToRetired(req.resourceLink)
                                .setCompletion(justComplete(patch))
                                .sendWith(this);
                        return;
                    }

                    deleteComputeIfNoChildren(req.resourceLink, patch);
                })
                .sendWith(this);
    }

    public Operation patchToRetired(String computeLink) {
        ComputeState update = new ComputeState();
        update.powerState = PowerState.OFF;
        update.lifecycleState = LifecycleState.RETIRED;

        return Operation.createPatch(this, computeLink)
                .setBody(update);
    }

    private void deleteComputeIfNoChildren(String computeLink, Operation patch) {
        Query q = Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addCompositeFieldClause(ResourceState.FIELD_NAME_CUSTOM_PROPERTIES, ComputeProperties.PLACEMENT_LINK,
                        computeLink)
                .build();

        QueryTask task = QueryTask.Builder.createDirectTask()
                .setQuery(q)
                .setResultLimit(1)
                .build();

        QueryUtils.startQueryTask(this, task).handle((qt, e) -> {
            if (e != null) {
                patch.complete();
                return null;
            }

            if (qt.results.nextPageLink == null) {
                // no resource placed here, safe to delete
                Operation.createDelete(this, computeLink)
                        .setCompletion(justComplete(patch))
                        .sendWith(this);
            } else {
                patchToRetired(computeLink)
                        .setCompletion(justComplete(patch))
                        .sendWith(this);
            }

            return null;
        });
    }

    private CompletionHandler justComplete(Operation patch) {
        return (o, e) -> patch.complete();
    }
}
