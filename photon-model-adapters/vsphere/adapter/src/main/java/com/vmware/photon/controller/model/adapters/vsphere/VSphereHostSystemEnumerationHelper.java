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

import static com.vmware.xenon.common.UriUtils.buildUriPath;

import java.util.Collections;

import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.util.PhotonModelUriUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

public class VSphereHostSystemEnumerationHelper {
    /**
     * Builds a query for finding a HostSystems by its manage object reference.
     */
    static QueryTask queryForHostSystem(EnumerationProgress ctx, String parentComputeLink, String moRefId) {
        Builder builder = Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_ID, moRefId)
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK, parentComputeLink);
        QueryUtils.addEndpointLink(builder, ComputeState.class, ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());

        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .addOption(QueryOption.EXPAND_CONTENT)
                .build();
    }

    static ComputeDescription makeDescriptionForHost(VSphereIncrementalEnumerationService vSphereIncrementalEnumerationService, EnumerationProgress enumerationProgress,
                                                     HostSystemOverlay hs) {
        ComputeDescription res = new ComputeDescription();
        res.name = hs.getName();
        res.documentSelfLink =
                buildUriPath(ComputeDescriptionService.FACTORY_LINK, vSphereIncrementalEnumerationService.getHost().nextUUID());
        res.cpuCount = hs.getCoreCount();
        res.endpointLink = enumerationProgress.getRequest().endpointLink;
        res.cpuMhzPerCore = hs.getCpuMhz();
        res.totalMemoryBytes = hs.getTotalMemoryBytes();
        res.supportedChildren = Collections.singletonList(ComputeType.VM_GUEST.name());
        res.instanceAdapterReference = enumerationProgress
                .getParent().description.instanceAdapterReference;
        res.enumerationAdapterReference = enumerationProgress
                .getParent().description.enumerationAdapterReference;
        res.statsAdapterReference = enumerationProgress
                .getParent().description.statsAdapterReference;
        res.diskAdapterReference = enumerationProgress
                .getParent().description.diskAdapterReference;
        res.regionId = enumerationProgress.getRegionId();

        return res;
    }

    static ComputeState makeHostSystemFromResults(EnumerationProgress enumerationProgress,
                                                  HostSystemOverlay hs) {
        ComputeState state = new ComputeState();
        state.type = hs.isClusterHost() ? ComputeType.CLUSTER_HOST : ComputeType.VM_HOST;
        state.environmentName = ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;
        state.endpointLink = enumerationProgress.getRequest().endpointLink;
        state.regionId = enumerationProgress.getRegionId();
        state.id = hs.getId().getValue();
        state.adapterManagementReference = enumerationProgress
                .getRequest().adapterManagementReference;
        state.parentLink = enumerationProgress.getRequest().resourceLink();
        state.resourcePoolLink = enumerationProgress.getRequest().resourcePoolLink;
        state.groupLinks = VsphereComputeResourceEnumerationHelper.getConnectedDatastoresAndNetworks(enumerationProgress, hs.getDatastore(), hs.getNetwork());

        state.name = hs.getName();
        // TODO: retrieve host power state
        state.powerState = PowerState.ON;
        if (hs.isInMaintenanceMode()) {
            state.powerState = PowerState.SUSPEND;
        }
        CustomProperties.of(state)
                .put(CustomProperties.MOREF, hs.getId())
                .put(CustomProperties.TYPE, hs.getId().getType());
        if (hs.isClusterHost()) {
            CustomProperties.of(state)
                    .put(CustomProperties.CLUSTER_LINK, enumerationProgress
                            .getComputeResourceTracker().getSelfLink(hs.getParentMoref()));
        }
        return state;
    }

    static CompletionHandler trackHostSystem(EnumerationProgress enumerationProgress,
                                             HostSystemOverlay hs) {
        return (o, e) -> {
            enumerationProgress.touchResource(VsphereEnumerationHelper.getSelfLinkFromOperation(o));
            if (e == null) {
                enumerationProgress.getHostSystemTracker()
                        .track(hs.getId(), VsphereEnumerationHelper.getSelfLinkFromOperation(o));
            } else {
                enumerationProgress.getHostSystemTracker()
                        .track(hs.getParent(), ResourceTracker.ERROR);
            }
        };
    }

    static void updateHostSystem(VSphereIncrementalEnumerationService vSphereIncrementalEnumerationService, ComputeState oldDocument, EnumerationProgress enumerationProgress,
                                 HostSystemOverlay hs) {
        ComputeState state = makeHostSystemFromResults(enumerationProgress, hs);
        state.documentSelfLink = oldDocument.documentSelfLink;
        state.resourcePoolLink = null;

        if (oldDocument.tenantLinks == null) {
            state.tenantLinks = enumerationProgress.getTenantLinks();
        }

        vSphereIncrementalEnumerationService.logFine(() -> String.format("Syncing HostSystem %s", oldDocument.documentSelfLink));
        Operation.createPatch(PhotonModelUriUtils.createInventoryUri(vSphereIncrementalEnumerationService.getHost(), state.documentSelfLink))
                .setBody(state)
                .setCompletion((o, e) -> {
                    trackHostSystem(enumerationProgress, hs).handle(o, e);
                    if (e == null) {
                        VsphereEnumerationHelper.submitWorkToVSpherePool(vSphereIncrementalEnumerationService, ()
                                -> VsphereEnumerationHelper.updateLocalTags(vSphereIncrementalEnumerationService, enumerationProgress, hs, o.getBody(ResourceState.class)));
                    }
                })
                .sendWith(vSphereIncrementalEnumerationService);

        ComputeDescription desc = makeDescriptionForHost(vSphereIncrementalEnumerationService, enumerationProgress, hs);
        desc.documentSelfLink = oldDocument.descriptionLink;
        Operation.createPatch(PhotonModelUriUtils.createInventoryUri(vSphereIncrementalEnumerationService.getHost(), desc.documentSelfLink))
                .setBody(desc)
                .sendWith(vSphereIncrementalEnumerationService);
    }

    static void createNewHostSystem(VSphereIncrementalEnumerationService vSphereIncrementalEnumerationService, EnumerationProgress enumerationProgress, HostSystemOverlay hs) {
        ComputeDescription desc = makeDescriptionForHost(vSphereIncrementalEnumerationService, enumerationProgress, hs);
        desc.tenantLinks = enumerationProgress.getTenantLinks();
        Operation.createPost(
                PhotonModelUriUtils.createInventoryUri(vSphereIncrementalEnumerationService.getHost(), ComputeDescriptionService.FACTORY_LINK))
                .setBody(desc)
                .sendWith(vSphereIncrementalEnumerationService);

        ComputeState state = makeHostSystemFromResults(enumerationProgress, hs);
        state.descriptionLink = desc.documentSelfLink;
        state.tenantLinks = enumerationProgress.getTenantLinks();

        VsphereEnumerationHelper.submitWorkToVSpherePool(vSphereIncrementalEnumerationService, () -> {
            VsphereEnumerationHelper.populateTags(vSphereIncrementalEnumerationService, enumerationProgress, hs, state);

            vSphereIncrementalEnumerationService.logFine(() -> String.format("Found new HostSystem %s", hs.getName()));
            Operation.createPost(PhotonModelUriUtils.createInventoryUri(vSphereIncrementalEnumerationService.getHost(), ComputeService.FACTORY_LINK))
                    .setBody(state)
                    .setCompletion(trackHostSystem(enumerationProgress, hs))
                    .sendWith(vSphereIncrementalEnumerationService);
        });
    }

    /**
     * Process all the host system retrieved from the endpoint.
     */
    static void processFoundHostSystem(VSphereIncrementalEnumerationService service,
                                       EnumerationProgress enumerationProgress, HostSystemOverlay hs) {
        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();
        QueryTask task = queryForHostSystem(enumerationProgress, request.resourceLink(), hs.getId().getValue());

        VsphereEnumerationHelper.withTaskResults(service, task, result -> {
            if (result.documentLinks.isEmpty()) {
                createNewHostSystem(service, enumerationProgress, hs);
            } else {
                ComputeState oldDocument = VsphereEnumerationHelper.convertOnlyResultToDocument(result, ComputeState.class);
                updateHostSystem(service, oldDocument, enumerationProgress, hs);
            }
        });
    }
}
