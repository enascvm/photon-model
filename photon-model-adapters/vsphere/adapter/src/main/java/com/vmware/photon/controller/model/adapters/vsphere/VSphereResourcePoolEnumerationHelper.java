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

import static com.vmware.photon.controller.model.adapters.vsphere.VsphereEnumerationHelper.convertOnlyResultToDocument;
import static com.vmware.photon.controller.model.adapters.vsphere.VsphereEnumerationHelper.withTaskResults;
import static com.vmware.xenon.common.UriUtils.buildUriPath;

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.util.PhotonModelUriUtils;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.ObjectUpdateKind;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

public class VSphereResourcePoolEnumerationHelper {
    private static String buildStableResourcePoolLink(ManagedObjectReference ref, String adapterManagementReference) {
        return ComputeService.FACTORY_LINK + "/"
                + VimUtils.buildStableManagedObjectId(ref, adapterManagementReference);
    }

    private static ComputeState makeResourcePoolFromResults(
            EnumerationProgress enumerationProgress, ResourcePoolOverlay rp, String selfLink, EnumerationClient client)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();

        ComputeState state = new ComputeState();
        state.documentSelfLink = selfLink;
        state.name = rp.getName();
        state.id = rp.getId().getValue();
        state.type = ComputeType.VM_HOST;
        state.powerState = PowerState.ON;
        state.endpointLink = request.endpointLink;
        AdapterUtils.addToEndpointLinks(state, request.endpointLink);
        state.regionId = enumerationProgress.getRegionId();
        state.parentLink = enumerationProgress.getRequest().resourceLink();
        state.resourcePoolLink = request.resourcePoolLink;
        state.adapterManagementReference = request.adapterManagementReference;

        ManagedObjectReference owner = rp.getOwner();
        AbstractOverlay ov = enumerationProgress.getOverlay(owner);
        if (ov instanceof ComputeResourceOverlay) {
            ComputeResourceOverlay cr = (ComputeResourceOverlay) ov;
            state.groupLinks = VsphereEnumerationHelper
                    .getConnectedDatastoresAndNetworks(enumerationProgress, cr.getDatastore(), cr.getNetwork(), client);
        } else if (ov instanceof HostSystemOverlay) {
            HostSystemOverlay cr = (HostSystemOverlay) ov;
            state.groupLinks = VsphereEnumerationHelper
                    .getConnectedDatastoresAndNetworks(enumerationProgress,
                            cr.getDatastore(), cr.getNetwork(), client);
        }

        CustomProperties.of(state)
                .put(CustomProperties.MOREF, rp.getId())
                .put(CustomProperties.DATACENTER_SELF_LINK, enumerationProgress.getDcLink())
                .put(CustomProperties.TYPE, VimNames.TYPE_RESOURCE_POOL);
        VsphereEnumerationHelper.populateResourceStateWithAdditionalProps(state, enumerationProgress.getVcUuid());
        return state;
    }

    private static ComputeDescription makeDescriptionFromChanges(
            ResourcePoolOverlay rp, String selfLink, String ownerName) {
        ComputeDescription res = new ComputeDescription();
        res.name = rp.getNameOrNull();
        res.documentSelfLink =
                buildUriPath(ComputeDescriptionService.FACTORY_LINK, UriUtils.getLastPathSegment(selfLink));

        res.totalMemoryBytes = rp.getMemoryReservationBytes();
        return res;
    }

    private static ComputeDescription makeDescriptionForResourcePool(EnumerationProgress enumerationProgress,
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
        AdapterUtils.addToEndpointLinks(res, enumerationProgress.getRequest().endpointLink);
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

    private static CompletionHandler trackResourcePool(EnumerationProgress enumerationProgress, ResourcePoolOverlay rp) {
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

    private static void updateResourcePool(
            VSphereIncrementalEnumerationService service, EnumerationProgress enumerationProgress,
            String ownerName, String selfLink, ResourcePoolOverlay rp, boolean fullUpdate,
            EnumerationClient client) throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        ComputeState state;
        ComputeDescription desc;
        if (fullUpdate) {
            state = makeResourcePoolFromResults(enumerationProgress, rp, selfLink, client);
            state.name = rp.makeUserFriendlyName(ownerName);
            state.tenantLinks = enumerationProgress.getTenantLinks();
            state.resourcePoolLink = null;
            desc = makeDescriptionForResourcePool(enumerationProgress, rp, selfLink);
        } else {
            state = makeResourcePoolFromChanges(rp, selfLink, ownerName);
            desc = makeDescriptionFromChanges(rp, selfLink, ownerName);
        }

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

    private static ComputeState makeResourcePoolFromChanges(
            ResourcePoolOverlay rp, String selfLink, String ownerName) {
        ComputeState state = new ComputeState();
        state.documentSelfLink = selfLink;
        state.totalMemoryBytes = rp.getMemoryReservationBytes();

        if (null != rp.getNameOrNull()) {
            state.name = rp.makeUserFriendlyName(ownerName);
        }
        return state;
    }

    private static void createNewResourcePool(
            VSphereIncrementalEnumerationService service, EnumerationProgress enumerationProgress,
            String ownerName, String selfLink, ResourcePoolOverlay rp, EnumerationClient client)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        ComputeState state = makeResourcePoolFromResults(enumerationProgress, rp, selfLink, client);
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

    public static void processFoundResourcePool(VSphereIncrementalEnumerationService service,
                                                EnumerationProgress enumerationProgress, ResourcePoolOverlay rp,
                                                String ownerName, EnumerationClient client) {
        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();
        String selfLink = buildStableResourcePoolLink(rp.getId(), request.endpointLink);

        Operation.createGet(PhotonModelUriUtils.createInventoryUri(service.getHost(), selfLink))
                .setCompletion((o, e) -> {
                    try {
                        // The first condition is just to check if there is a service not found or 404 response,
                        // in case of which we will create a new resource pool.
                        // The below logic is ordinal because with adapter running on vsphere agent host,
                        // the exception object comes in as null. Tracked with VCOM-4539.
                        if ((e != null && e instanceof ServiceNotFoundException) || o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                            // create
                            Utils.log(VsphereEnumerationHelper.class, service.getUri().toString(), Level.INFO, "Document at [%s] was not found, a new resource pool will be created.", selfLink);
                            createNewResourcePool(service, enumerationProgress, ownerName, selfLink, rp, client);
                        } else if (e != null) {
                            // track
                            Utils.log(VsphereEnumerationHelper.class, service.getUri().toString(), Level.WARNING, "Could not create or update resource pool. Get operation to [%s] failed with exception [%s]", selfLink, Utils.toString(e));
                            enumerationProgress.getResourcePoolTracker().track();
                        } else {
                            // update
                            Utils.log(VsphereEnumerationHelper.class, service.getUri().toString(), Level.FINE, "Found resource pool at [%s]. Updating resource pool...", selfLink);
                            updateResourcePool(service, enumerationProgress, ownerName, selfLink, rp, true, client);
                        }
                    } catch (Exception ex) {
                        service.logSevere("Error occurred while processing update of resource pool: %s", Utils.toString(e));
                        enumerationProgress.getResourcePoolTracker().track(rp.getId(), ResourceTracker.ERROR);
                    }
                })
                .sendWith(service);
    }

    public static void handleResourcePoolChanges(
            VSphereIncrementalEnumerationService service, List<ResourcePoolOverlay> resourcePools,
            EnumerationProgress enumerationProgress, EnumerationClient client) {
        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();
        enumerationProgress.expectResourcePoolCount(resourcePools.size());

        for (ResourcePoolOverlay resourcePool : resourcePools) {
            // exclude all root resource pools
            // no need to collect the root resource pool
            try {
                if (ObjectUpdateKind.ENTER.equals(resourcePool.getObjectUpdateKind())
                        && VimNames.TYPE_RESOURCE_POOL.equals(resourcePool.getParent().getType())) {

                    String ownerMoRefId = resourcePool.getOwner().getValue();

                    QueryTask task = queryForRPOwner(ownerMoRefId, enumerationProgress);
                    String selfLink = buildStableResourcePoolLink(resourcePool.getId(), request.endpointLink);
                    withTaskResults(service, task, enumerationProgress.getResourcePoolTracker(), result -> {
                        try {
                            if (!result.documentLinks.isEmpty()) {
                                ComputeState ownerDocument = convertOnlyResultToDocument(result, ComputeState.class);
                                createNewResourcePool(
                                        service, enumerationProgress, ownerDocument.name, selfLink, resourcePool, client);
                            } else {
                                // This happens for the resource pools within Host. The owner is a ComputeResource and
                                // is not currently enumerated in photon
                                createNewResourcePool(
                                        service, enumerationProgress, null, selfLink, resourcePool, client);
                            }
                        } catch (Exception e) {
                            service.logSevere("Error occurred while processing update of resource pool: %s", Utils.toString(e));
                            enumerationProgress.getResourcePoolTracker().track();
                        }
                    });
                } else {
                    String rpSelfLink = buildStableResourcePoolLink(resourcePool.getId(), request.endpointLink);
                    Operation.createGet(PhotonModelUriUtils.createInventoryUri(service.getHost(), rpSelfLink))
                            .setCompletion((o, e) -> {
                                try {
                                    if (e == null) {
                                        ComputeState oldState = o.getBody(ComputeState.class);
                                        String existingOwnerName = getOwnerNameFromResourcePoolName(oldState.name);
                                        if (ObjectUpdateKind.MODIFY.equals(resourcePool.getObjectUpdateKind())) {
                                            updateResourcePool(
                                                    service, enumerationProgress, existingOwnerName, oldState.documentSelfLink, resourcePool, false, client);
                                        } else {
                                            Operation.createDelete(
                                                    PhotonModelUriUtils.createInventoryUri(service.getHost(), rpSelfLink))
                                                    .setCompletion(trackResourcePool(enumerationProgress, resourcePool))
                                                    .sendWith(service);
                                        }
                                    } else {
                                        enumerationProgress.getResourcePoolTracker().track();
                                    }
                                } catch (Exception ex) {
                                    enumerationProgress.getResourcePoolTracker().track();
                                }
                            })
                            .sendWith(service);
                }
            } catch (Exception e) {
                service.logSevere("Error occurred while processing incremental update of resource pool: %s", Utils.toString(e));
                enumerationProgress.getResourcePoolTracker().track(resourcePool.getId(), ResourceTracker.ERROR);
            }
        }

        try {
            enumerationProgress.getResourcePoolTracker().await();
        } catch (InterruptedException e) {
            service.logSevere("Interrupted during incremental enumeration for resource pools: %s", Utils.toString(e));
        }
    }

    public static String getOwnerNameFromResourcePoolName(String name) {
        String owner = null;
        int index = name.indexOf("/"); //this finds the first occurrence of "/"
        if (index > 0) {
            owner = name.substring(0, index - 1);
        }
        return owner;
    }

    private static QueryTask queryForRPOwner(String moRefId, EnumerationProgress ctx) {
        QueryTask.Query.Builder builder = QueryTask.Query.Builder.create()
                .addKindFieldClause(ComputeService.ComputeState.class)
                .addFieldClause(ComputeService.ComputeState.FIELD_NAME_ID, moRefId);
        QueryUtils.addEndpointLink(builder, ComputeService.ComputeState.class, ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());

        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
                .build();
    }
}
