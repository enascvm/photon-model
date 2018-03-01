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
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.util.PhotonModelUriUtils;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.UriUtils;

public class VSphereResourcePoolEnumerationHelper {
    static String buildStableResourcePoolLink(ManagedObjectReference ref, String adapterManagementReference) {
        return ComputeService.FACTORY_LINK + "/"
                + VimUtils.buildStableManagedObjectId(ref, adapterManagementReference);
    }

    static ComputeState makeResourcePoolFromResults(
            EnumerationProgress enumerationProgress, ResourcePoolOverlay rp, String selfLink) {
        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();

        ComputeState state = new ComputeState();
        state.documentSelfLink = selfLink;
        state.name = rp.getName();
        state.id = rp.getId().getValue();
        state.type = ComputeType.VM_HOST;
        state.powerState = PowerState.ON;
        state.endpointLink = request.endpointLink;
        state.regionId = enumerationProgress.getRegionId();
        state.parentLink = enumerationProgress.getRequest().resourceLink();
        state.resourcePoolLink = request.resourcePoolLink;
        state.adapterManagementReference = request.adapterManagementReference;

        ManagedObjectReference owner = rp.getOwner();
        AbstractOverlay ov = enumerationProgress.getOverlay(owner);
        if (ov instanceof ComputeResourceOverlay) {
            ComputeResourceOverlay cr = (ComputeResourceOverlay) ov;
            state.groupLinks = VsphereComputeResourceEnumerationHelper
                    .getConnectedDatastoresAndNetworks(enumerationProgress, cr.getDatastore(), cr.getNetwork());
        } else if (ov instanceof HostSystemOverlay) {
            HostSystemOverlay cr = (HostSystemOverlay) ov;
            state.groupLinks = VsphereComputeResourceEnumerationHelper
                    .getConnectedDatastoresAndNetworks(enumerationProgress,
                            cr.getDatastore(), cr.getNetwork());
        }

        CustomProperties.of(state)
                .put(CustomProperties.MOREF, rp.getId())
                .put(CustomProperties.TYPE, VimNames.TYPE_RESOURCE_POOL);
        return state;
    }

    static ComputeDescription makeDescriptionForResourcePool(EnumerationProgress enumerationProgress,
                                                             ResourcePoolOverlay rp, String rpSelfLink) {
        ComputeDescription res = new ComputeDescription();
        res.name = rp.getName();
        res.documentSelfLink =
                buildUriPath(ComputeDescriptionService.FACTORY_LINK, UriUtils.getLastPathSegment(rpSelfLink));

        res.totalMemoryBytes = rp.getMemoryReservationBytes();
        // resource pools CPU is measured in Mhz
        res.cpuCount = 0;
        res.supportedChildren = Collections.singletonList(ComputeType.VM_GUEST.name());
        res.endpointLink = enumerationProgress.getRequest().endpointLink;
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

    static CompletionHandler trackResourcePool(EnumerationProgress enumerationProgress, ResourcePoolOverlay rp) {
        return (o, e) -> {
            enumerationProgress.touchResource(VsphereEnumerationHelper.getSelfLinkFromOperation(o));
            if (e == null) {
                enumerationProgress.getResourcePoolTracker().track(rp.getId(),
                        VsphereEnumerationHelper.getSelfLinkFromOperation(o));
            } else {
                enumerationProgress.getResourcePoolTracker().track(rp.getId(), ResourceTracker.ERROR);
            }
        };
    }

    static void updateResourcePool(VSphereIncrementalEnumerationService service,
                                   EnumerationProgress enumerationProgress, String ownerName, String selfLink,
                                   ResourcePoolOverlay rp) {
        ComputeState state = makeResourcePoolFromResults(enumerationProgress, rp, selfLink);
        state.name = rp.makeUserFriendlyName(ownerName);
        state.tenantLinks = enumerationProgress.getTenantLinks();
        state.resourcePoolLink = null;

        ComputeDescription desc = makeDescriptionForResourcePool(enumerationProgress, rp, selfLink);
        state.descriptionLink = desc.documentSelfLink;

        service.logFine(() -> String.format("Refreshed ResourcePool %s", state.name));
        Operation.createPatch(PhotonModelUriUtils.createInventoryUri(service.getHost(), selfLink))
                .setBody(state)
                .setCompletion(trackResourcePool(enumerationProgress, rp))
                .sendWith(service);

        Operation.createPatch(PhotonModelUriUtils.createInventoryUri(service.getHost(), desc.documentSelfLink))
                .setBody(desc)
                .sendWith(service);
    }

    static void createNewResourcePool(VSphereIncrementalEnumerationService service,
                                      EnumerationProgress enumerationProgress, String ownerName, String selfLink,
                                      ResourcePoolOverlay rp) {
        ComputeState state = makeResourcePoolFromResults(enumerationProgress, rp, selfLink);
        state.name = rp.makeUserFriendlyName(ownerName);
        state.tenantLinks = enumerationProgress.getTenantLinks();

        ComputeDescription desc = makeDescriptionForResourcePool(enumerationProgress, rp, selfLink);
        desc.tenantLinks = enumerationProgress.getTenantLinks();
        state.descriptionLink = desc.documentSelfLink;

        service.logFine(() -> String.format("Found new ResourcePool %s", state.name));
        Operation.createPost(PhotonModelUriUtils.createInventoryUri(service.getHost(), ComputeService.FACTORY_LINK))
                .setBody(state)
                .setCompletion(trackResourcePool(enumerationProgress, rp))
                .sendWith(service);

        Operation.createPost(
                PhotonModelUriUtils.createInventoryUri(service.getHost(), ComputeDescriptionService.FACTORY_LINK))
                .setBody(desc)
                .sendWith(service);
    }

    static void processFoundResourcePool(VSphereIncrementalEnumerationService service,
                                         EnumerationProgress enumerationProgress, ResourcePoolOverlay rp,
                                         String ownerName) {
        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();
        String selfLink = buildStableResourcePoolLink(rp.getId(), request.endpointLink);

        Operation.createGet(PhotonModelUriUtils.createInventoryUri(service.getHost(), selfLink))
                .setCompletion((o, e) -> {
                    if (e == null) {
                        updateResourcePool(service, enumerationProgress, ownerName, selfLink, rp);
                    } else if (e instanceof ServiceNotFoundException
                            || o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                        createNewResourcePool(service, enumerationProgress, ownerName, selfLink, rp);
                    } else {
                        trackResourcePool(enumerationProgress, rp).handle(o, e);
                    }
                })
                .sendWith(service);
    }
}
