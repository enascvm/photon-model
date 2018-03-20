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

import static com.vmware.photon.controller.model.adapters.vsphere.util.VimNames.TYPE_PORTGROUP;
import static com.vmware.photon.controller.model.adapters.vsphere.util.VimNames.TYPE_SERVER_DISK;
import static com.vmware.xenon.common.UriUtils.buildUriPath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.util.PhotonModelUriUtils;
import com.vmware.vim25.HostScsiDisk;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.VsanHostConfigInfo;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;

public class VsphereComputeResourceEnumerationHelper {
    static QueryTask queryForCluster(EnumerationProgress ctx, String parentComputeLink,
                                     String moRefId) {
        Builder builder = Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK, parentComputeLink)
                .addFieldClause(ComputeState.FIELD_NAME_ID, moRefId);

        QueryUtils.addEndpointLink(builder, ComputeState.class, ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());

        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();
    }

    static Set<String> getConnectedDatastoresAndNetworks(EnumerationProgress ctx, List<ManagedObjectReference> datastores,
                                                         List<ManagedObjectReference> networks) {
        Set<String> res = new TreeSet<>();

        for (ManagedObjectReference ref : datastores) {
            res.add(VsphereEnumerationHelper.computeGroupStableLink(ref,
                    VSphereIncrementalEnumerationService.PREFIX_DATASTORE, ctx.getRequest().endpointLink));
        }

        for (ManagedObjectReference ref : networks) {
            NetworkOverlay ov = (NetworkOverlay) ctx.getOverlay(ref);
            if (ov.getParentSwitch() != null) {
                // instead of a portgroup add the switch
                res.add(VsphereEnumerationHelper.computeGroupStableLink(ov.getParentSwitch(),
                        VSphereIncrementalEnumerationService.PREFIX_NETWORK, ctx.getRequest().endpointLink));
            } else if (!TYPE_PORTGROUP.equals(ov.getId().getType())) {
                // skip portgroups and care only about opaque nets and standard swtiches
                res.add(VsphereEnumerationHelper.computeGroupStableLink(ov.getId(),
                        VSphereIncrementalEnumerationService.PREFIX_NETWORK, ctx.getRequest().endpointLink));
            }
        }

        return res;
    }

    static ComputeState makeComputeResourceFromResults(EnumerationProgress enumerationProgress,
                                                       ComputeResourceOverlay cr) {
        ComputeState state = new ComputeState();
        state.id = cr.getId().getValue();
        state.type = cr.isDrsEnabled() ? ComputeType.ZONE : ComputeType.VM_HOST;
        state.endpointLink = enumerationProgress.getRequest().endpointLink;
        AdapterUtils.addToEndpointLinks(state, enumerationProgress.getRequest().endpointLink);
        state.regionId = enumerationProgress.getRegionId();
        state.environmentName = ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;
        state.adapterManagementReference = enumerationProgress
                .getRequest().adapterManagementReference;
        state.parentLink = enumerationProgress.getRequest().resourceLink();
        state.resourcePoolLink = enumerationProgress.getRequest().resourcePoolLink;
        state.groupLinks = getConnectedDatastoresAndNetworks(enumerationProgress,
                cr.getDatastore(), cr.getNetwork());

        state.name = cr.getName();
        state.powerState = PowerState.ON;
        CustomProperties.of(state)
                .put(CustomProperties.MOREF, cr.getId())
                .put(CustomProperties.TYPE, cr.getId().getType())
                .put(CustomProperties.CR_IS_VSAN_ENABLED, cr.isVsanEnabled())
                .put(CustomProperties.CR_VSAN_CONFIG_ID, cr.getVsanConfigId())
                .put(CustomProperties.DATACENTER, enumerationProgress.getRegionId());
        return state;
    }

    static CompletionHandler trackComputeResource(EnumerationProgress enumerationProgress,
                                                  ComputeResourceOverlay cr) {
        return (o, e) -> {
            enumerationProgress.touchResource(VsphereEnumerationHelper.getSelfLinkFromOperation(o));
            if (e == null) {
                enumerationProgress.getComputeResourceTracker().track(cr.getId(),
                        VsphereEnumerationHelper.getSelfLinkFromOperation(o));
            } else {
                enumerationProgress.getComputeResourceTracker().track(cr.getId(), ResourceTracker.ERROR);
            }
        };
    }

    static ComputeDescription makeDescriptionForCluster(VSphereIncrementalEnumerationService service,
                                                        EnumerationProgress enumerationProgress, ComputeResourceOverlay cr) {
        ComputeDescription res = new ComputeDescription();
        res.name = cr.getName();
        res.documentSelfLink =
                buildUriPath(ComputeDescriptionService.FACTORY_LINK, service.getHost().nextUUID());
        res.cpuCount = cr.getTotalCpuCores();
        if (cr.getTotalCpuCores() != 0) {
            res.cpuMhzPerCore = cr.getTotalCpuMhz() / cr.getTotalCpuCores();
        }
        res.totalMemoryBytes = cr.getTotalMemoryBytes();
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

    static void createNewComputeResource(VSphereIncrementalEnumerationService service,
                                         EnumerationProgress enumerationProgress, ComputeResourceOverlay cr) {
        ComputeDescription desc = makeDescriptionForCluster(service, enumerationProgress, cr);
        desc.tenantLinks = enumerationProgress.getTenantLinks();
        Operation.createPost(
                PhotonModelUriUtils.createInventoryUri(service.getHost(), ComputeDescriptionService.FACTORY_LINK))
                .setBody(desc)
                .sendWith(service);

        ComputeState state = makeComputeResourceFromResults(enumerationProgress, cr);
        state.tenantLinks = enumerationProgress.getTenantLinks();
        state.descriptionLink = desc.documentSelfLink;

        VsphereEnumerationHelper.submitWorkToVSpherePool(service, () -> {
            VsphereEnumerationHelper.populateTags(service, enumerationProgress, cr, state);

            service.logFine(() -> String.format("Found new ComputeResource %s", cr.getId().getValue()));
            Operation.createPost(PhotonModelUriUtils.createInventoryUri(service.getHost(), ComputeService.FACTORY_LINK))
                    .setBody(state)
                    .setCompletion(trackComputeResource(enumerationProgress, cr))
                    .sendWith(service);
        });
    }

    static void updateCluster(VSphereIncrementalEnumerationService service, ComputeState oldDocument,
                              EnumerationProgress enumerationProgress, ComputeResourceOverlay cr) {
        ComputeState state = makeComputeResourceFromResults(enumerationProgress, cr);
        state.documentSelfLink = oldDocument.documentSelfLink;
        state.resourcePoolLink = null;

        if (oldDocument.tenantLinks == null) {
            state.tenantLinks = enumerationProgress.getTenantLinks();
        }

        service.logFine(() -> String.format("Syncing ComputeResource %s", oldDocument.documentSelfLink));
        Operation.createPatch(PhotonModelUriUtils.createInventoryUri(service.getHost(), oldDocument.documentSelfLink))
                .setBody(state)
                .setCompletion((o, e) -> {
                    trackComputeResource(enumerationProgress, cr).handle(o, e);
                    if (e == null) {
                        VsphereEnumerationHelper.submitWorkToVSpherePool(service, ()
                                -> VsphereEnumerationHelper.updateLocalTags(service,
                                enumerationProgress, cr, o.getBody(ResourceState.class)));
                    }
                })
                .sendWith(service);

        ComputeDescription desc = makeDescriptionForCluster(service, enumerationProgress, cr);
        desc.documentSelfLink = oldDocument.descriptionLink;
        Operation.createPatch(PhotonModelUriUtils.createInventoryUri(service.getHost(), desc.documentSelfLink))
                .setBody(desc)
                .sendWith(service);
    }

    /**
     * Either creates a new Compute or update an already existing one. Existence is checked by
     * querying for a compute with id equals to moref value of a cluster whose parent is the Compute
     * from the request.
     */
    static void processFoundComputeResource(VSphereIncrementalEnumerationService service, EnumerationProgress enumerationProgress,
                                            ComputeResourceOverlay cr) {
        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();
        QueryTask task = queryForCluster(enumerationProgress, request.resourceLink(), cr.getId().getValue());

        VsphereEnumerationHelper.withTaskResults(service, task, result -> {
            if (result.documentLinks.isEmpty()) {
                createNewComputeResource(service, enumerationProgress, cr);
            } else {
                ComputeState oldDocument = VsphereEnumerationHelper.convertOnlyResultToDocument(result, ComputeState.class);
                updateCluster(service, oldDocument, enumerationProgress, cr);
            }
            if (!cr.getVsanHostConfig().isEmpty()) {
                processServerDisks(service, cr.getVsanHostConfig(), enumerationProgress);
            }
        });
    }

    private static void processServerDisks(VSphereIncrementalEnumerationService service, List<VsanHostConfigInfo> vsanHostConfigInfo,
                                           EnumerationProgress ctx) {
        // Server disks are two types - SSD and Non SSD. Vsphere API returns SSD as an HostScsiDisk Object and non SSD
        // as a list of HostScsiDisk objects.
        // getDiskMapping never returns a null. So NPE check not necessary here
        vsanHostConfigInfo.forEach(config -> config.getStorageInfo().getDiskMapping().forEach(vsanHostDiskMapping -> {
            // Process SSD and non SSD
            List<HostScsiDisk> disks = new ArrayList<>(vsanHostDiskMapping.getNonSsd());
            disks.add(vsanHostDiskMapping.getSsd());
            disks.forEach(disk -> {
                QueryTask task = queryForServerDisks(ctx, disk);
                VsphereEnumerationHelper.withTaskResults(service, task, result -> {
                    if (result.documentLinks.isEmpty()) {
                        createServerDisk(service, disk, ctx, config.getHostSystem().getValue());
                    } else {
                        DiskService.DiskState oldDocument = VsphereEnumerationHelper.convertOnlyResultToDocument(result, DiskService.DiskState.class);
                        updateServerDisk(service, disk, oldDocument, ctx, config.getHostSystem().getValue());
                    }
                });
            });
        }));
    }

    private static void createServerDisk(VSphereIncrementalEnumerationService service, HostScsiDisk serverDisk,
                                         EnumerationProgress ctx, String server) {
        DiskService.DiskState ds = makeServerDisksFromResults(serverDisk, ctx, server);
        Operation.createPost(PhotonModelUriUtils.createInventoryUri(service.getHost(), DiskService.FACTORY_LINK))
                .setBody(ds)
                .setCompletion((op, ex) -> ctx.touchResource(VsphereEnumerationHelper.getSelfLinkFromOperation(op)))
                .sendWith(service);
    }

    private static void updateServerDisk(VSphereIncrementalEnumerationService service, HostScsiDisk serverDisk,
                                         DiskService.DiskState oldDocument,
                                         EnumerationProgress ctx, String server) {
        DiskService.DiskState ds = makeServerDisksFromResults(serverDisk, ctx, server);
        ds.documentSelfLink = oldDocument.documentSelfLink;
        Operation.createPatch(PhotonModelUriUtils.createInventoryUri(service.getHost(), oldDocument.documentSelfLink))
                .setBody(ds)
                .setCompletion((op, ex) -> ctx.touchResource(VsphereEnumerationHelper.getSelfLinkFromOperation(op)))
                .sendWith(service);
    }

    private static DiskService.DiskState makeServerDisksFromResults(HostScsiDisk serverDisk, EnumerationProgress ctx,
                                                                    String server) {
        DiskService.DiskState ds =  new DiskService.DiskState();
        ds.name = serverDisk.getDisplayName();
        // block size is in Bytes - converting to GigaBytes
        long capacityInGB = serverDisk.getCapacity().getBlockSize() * serverDisk.getCapacity().getBlock() / (1024L * 1024L * 1024L);
        ds.tenantLinks = ctx.getTenantLinks();
        ds.endpointLink = ctx.getRequest().endpointLink;
        AdapterUtils.addToEndpointLinks(ds, ctx.getRequest().endpointLink);
        CustomProperties.of(ds)
                .put(ComputeProperties.ENDPOINT_LINK_PROP_NAME, ctx.getRequest().endpointLink)
                .put(CustomProperties.TYPE, TYPE_SERVER_DISK)
                .put(CustomProperties.MODEL, serverDisk.getModel())
                .put(CustomProperties.VENDOR, serverDisk.getVendor())
                .put(CustomProperties.CAPACITY_IN_GB, capacityInGB)
                .put(CustomProperties.SERVER, server)
                .put(CustomProperties.SERVER_DISK_TYPE, serverDisk.getScsiDiskType());
        return ds;
    }

    private static QueryTask queryForServerDisks(EnumerationProgress ctx, HostScsiDisk serverDisk) {
        QueryTask.Query.Builder builder = QueryTask.Query.Builder.create()
                .addKindFieldClause(DiskService.DiskState.class)
                .addFieldClause(ResourceState.FIELD_NAME_NAME, serverDisk.getDisplayName())
                .addCompositeFieldClause(ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                        CustomProperties.TYPE, TYPE_SERVER_DISK);
        QueryUtils.addEndpointLink(builder, DiskService.DiskState.class,
                ctx.getRequest().endpointLink);

        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());
        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();
    }
}