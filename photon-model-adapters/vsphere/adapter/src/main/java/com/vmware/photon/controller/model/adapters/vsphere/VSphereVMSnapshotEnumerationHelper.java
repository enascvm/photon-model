/*
 * Copyright (c) 2018-2019 VMware, Inc. All Rights Reserved.
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

import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;

import org.apache.commons.collections.CollectionUtils;

import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.SnapshotService.SnapshotState;
import com.vmware.vim25.VirtualMachineSnapshotTree;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;

public class VSphereVMSnapshotEnumerationHelper {

    static QueryTask queryForSnapshot(EnumerationProgress ctx, String id, String vmSelfLink) {
        Builder builder = Builder.create()
                .addKindFieldClause(SnapshotState.class)
                .addFieldClause(SnapshotState.FIELD_NAME_ID, id)
                .addFieldClause(SnapshotState.FIELD_NAME_COMPUTE_LINK, vmSelfLink);

        QueryUtils.addEndpointLink(builder, SnapshotState.class, ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());

        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();
    }

    static SnapshotState constructSnapshot(VSphereIncrementalEnumerationService service,
                                           VirtualMachineSnapshotTree current, String parentLink,
                                           String vmSelfLink, EnumerationProgress enumerationProgress,
                                           VmOverlay vm) {
        SnapshotState snapshot = new SnapshotState();
        snapshot.computeLink = vmSelfLink;
        snapshot.parentLink = parentLink;
        snapshot.description = current.getDescription();
        //TODO how to determine if the snapshot is current
        //snapshot.isCurrent = current.isQuiesced()
        snapshot.creationTimeMicros = current.getCreateTime().toGregorianCalendar().getTimeInMillis();
        //TODO How to fetch custom properties
        //snapshot.customProperties = current.get
        //TODO what are snapshot grouplinks
        //snapshot.groupLinks
        snapshot.name = current.getName();
        snapshot.regionId = enumerationProgress.getRegionId();
        snapshot.id = current.getId().toString();
        VsphereEnumerationHelper.populateTags(service, enumerationProgress, vm, snapshot);
        snapshot.tenantLinks = enumerationProgress.getTenantLinks();
        if (snapshot.endpointLinks == null) {
            snapshot.endpointLinks = new HashSet<>();
        }
        snapshot.endpointLinks.add(enumerationProgress.getRequest().endpointLink);
        CustomProperties.of(snapshot)
                .put(CustomProperties.MOREF, VimUtils.convertMoRefToString(current.getSnapshot()))
                .put(CustomProperties.DATACENTER_SELF_LINK, enumerationProgress.getDcLink())
                .put(CustomProperties.TYPE, current.getSnapshot().getType());
        VsphereEnumerationHelper.populateResourceStateWithAdditionalProps(snapshot, enumerationProgress.getVcUuid());
        return snapshot;
    }

    static DeferredResult<SnapshotState> updateSnapshot(VSphereIncrementalEnumerationService service,
                                                        EnumerationProgress enumerationProgress,
                                                        VmOverlay vm, SnapshotState oldState,
                                                        SnapshotState newState, String id) {
        newState.documentSelfLink = oldState.documentSelfLink;
        newState.id = id;
        newState.regionId = enumerationProgress.getRegionId();

        DeferredResult<SnapshotState> res = new DeferredResult<>();
        VsphereEnumerationHelper.submitWorkToVSpherePool(service, () -> {
            VsphereEnumerationHelper.populateTags(service, enumerationProgress, vm, newState);
            newState.tenantLinks = enumerationProgress.getTenantLinks();
            service.logFine(() -> String.format("Syncing snapshot %s", oldState.name));
            Operation opPatchSnapshot = Operation.createPatch(UriUtils.buildUri(
                    service.getHost(), oldState.documentSelfLink))
                    .setBody(newState);

            service.sendWithDeferredResult(opPatchSnapshot, SnapshotState.class).handle((snap, e) -> {
                if (e != null) {
                    res.fail(e);
                } else {
                    res.complete(snap);
                }
                return null;
            });
        });

        return res;
    }

    static void processSnapshot(VSphereIncrementalEnumerationService service,
                                VirtualMachineSnapshotTree current, String parentLink,
                                EnumerationProgress enumerationProgress,
                                VmOverlay vm, String vmSelfLink) {
        enumerationProgress.getSnapshotTracker().register();
        QueryTask task = queryForSnapshot(enumerationProgress, current.getId().toString(),
                vmSelfLink);
        VsphereEnumerationHelper.withTaskResults(service, task, (ServiceDocumentQueryResult result) -> {
            VsphereEnumerationHelper.submitWorkToVSpherePool(service, () -> {
                SnapshotState snapshotState = constructSnapshot(service, current, parentLink,
                        vmSelfLink, enumerationProgress, vm);
                if (result.documentLinks.isEmpty()) {
                    VSphereVirtualMachineEnumerationHelper.createSnapshot(service, snapshotState)
                            .thenCompose(createdSnapshotState ->
                                    trackAndProcessChildSnapshots(service, current, enumerationProgress, vm, vmSelfLink,
                                            createdSnapshotState))
                            .whenComplete((ss, e) -> {
                                if (e != null) {
                                    service.log(Level.SEVERE, "Creation of snapshot with name {%s} failed.",
                                            snapshotState.name);
                                }
                                enumerationProgress.getSnapshotTracker().arrive();
                            });
                } else {
                    SnapshotState oldState = VsphereEnumerationHelper.convertOnlyResultToDocument(result,
                            SnapshotState.class);
                    updateSnapshot(service, enumerationProgress, vm, oldState, snapshotState,
                            current.getId().toString())
                            .thenCompose(updatedSnapshotState ->
                                    trackAndProcessChildSnapshots(service, current, enumerationProgress, vm,
                                            vmSelfLink, updatedSnapshotState))
                            .whenComplete((ss, e) -> {
                                if (e != null) {
                                    service.logSevere("Updating of snapshot with name {%s}, selfLink {%s} failed",
                                            snapshotState.name, oldState.documentSelfLink);
                                }
                                enumerationProgress.getSnapshotTracker().arrive();
                            });
                }
            });
        });
    }

    private static DeferredResult<Object> trackAndProcessChildSnapshots(
            VSphereIncrementalEnumerationService service, VirtualMachineSnapshotTree current,
            EnumerationProgress enumerationProgress, VmOverlay vm,
            String vmSelfLink, SnapshotState updatedSnapshotState) {
        List<VirtualMachineSnapshotTree> childSnapshotList = current.getChildSnapshotList();
        if (!CollectionUtils.isEmpty(childSnapshotList)) {
            for (VirtualMachineSnapshotTree childSnapshot : childSnapshotList) {
                processSnapshot(service, childSnapshot, updatedSnapshotState.documentSelfLink,
                        enumerationProgress, vm, vmSelfLink);
            }
        }
        //return when all the child snapshots of this 'updatedSnapshotState' is processed
        return DeferredResult.completed(updatedSnapshotState);
    }

    static void processSnapshots(VSphereIncrementalEnumerationService service,
                                 EnumerationProgress enumerationProgress, VmOverlay vm,
                                 String vmSelfLink) {
        if (vmSelfLink != null) {
            List<VirtualMachineSnapshotTree> rootSnapshotList = vm.getRootSnapshotList();
            if (rootSnapshotList != null) {
                for (VirtualMachineSnapshotTree snapshotTree : rootSnapshotList) {
                    processSnapshot(service, snapshotTree, null, enumerationProgress, vm,
                            vmSelfLink);
                }
            }
        }
    }

    static void enumerateSnapshots(VSphereIncrementalEnumerationService service,
                                   EnumerationProgress enumerationProgress, List<VmOverlay> vms) {
        if (CollectionUtils.isEmpty(vms)) {
            return;
        }

        vms.forEach(vm -> {
            //process only those VMs that have snapshots attached.
            if (vm.getRootSnapshotList() != null) {
                ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();

                QueryTask task = VSphereVirtualMachineEnumerationHelper
                        .queryForVm(enumerationProgress, request.resourceLink(),
                                vm.getInstanceUuid(), null);

                VsphereEnumerationHelper.withTaskResults(service, task, result -> {
                    ComputeState computeState = VsphereEnumerationHelper.convertOnlyResultToDocument(result,
                            ComputeState.class);
                    processSnapshots(service, enumerationProgress, vm, computeState.documentSelfLink);
                });
            }
        });
        enumerationProgress.getSnapshotTracker().arriveAndDeregister();
    }
}
