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

import static com.vmware.photon.controller.model.ComputeProperties.CUSTOM_PROP_STORAGE_SHARED;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.STORAGE_AVAILABLE_BYTES;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.STORAGE_USED_BYTES;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService.ResourceMetrics;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.StatsUtil;
import com.vmware.photon.controller.model.util.ClusterUtil;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;
import com.vmware.photon.controller.model.util.PhotonModelUriUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

public class VsphereDatastoreEnumerationHelper {

    static QueryTask queryForStorage(EnumerationProgress ctx, String name, String groupLink) {
        Builder builder = Builder.create()
                .addKindFieldClause(StorageDescription.class)
                .addFieldClause(StorageDescription.FIELD_NAME_ADAPTER_REFERENCE,
                        ctx.getRequest().adapterManagementReference.toString())
                .addFieldClause(StorageDescription.FIELD_NAME_REGION_ID, ctx.getRegionId());

        if (name != null) {
            builder.addCaseInsensitiveFieldClause(StorageDescription.FIELD_NAME_NAME, name,
                    MatchType.TERM, Occurance.MUST_OCCUR);
        }
        if (groupLink != null) {
            builder.addCollectionItemClause(ResourceState.FIELD_NAME_GROUP_LINKS, groupLink);
        }
        QueryUtils.addEndpointLink(builder, StorageDescription.class,
                ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());

        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();
    }

    static StorageDescription makeStorageFromResults(ComputeEnumerateResourceRequest request,
                                                     DatastoreOverlay ds, String regionId) {
        StorageDescription res = new StorageDescription();
        res.id = res.name = ds.getName();
        res.type = ds.getType();
        res.resourcePoolLink = request.resourcePoolLink;
        res.endpointLink = request.endpointLink;
        AdapterUtils.addToEndpointLinks(res, request.endpointLink);
        res.adapterManagementReference = request.adapterManagementReference;
        res.capacityBytes = ds.getCapacityBytes();
        res.regionId = regionId;
        CustomProperties.of(res)
                .put(CustomProperties.MOREF, ds.getId())
                .put(STORAGE_USED_BYTES, ds.getCapacityBytes() - ds.getFreeSpaceBytes())
                .put(STORAGE_AVAILABLE_BYTES, ds.getFreeSpaceBytes())
                .put(CUSTOM_PROP_STORAGE_SHARED, ds.isMultipleHostAccess())
                .put(CustomProperties.TYPE, ds.getType())
                .put(CustomProperties.DS_PATH, ds.getPath())
                .put(CustomProperties.PROPERTY_NAME, ds.getName());

        return res;
    }

    static CompletionHandler trackDatastore(EnumerationProgress enumerationProgress,
                                            DatastoreOverlay ds) {
        return (o, e) -> {
            enumerationProgress.touchResource(VsphereEnumerationHelper.getSelfLinkFromOperation(o));
            if (e == null) {
                enumerationProgress.getDatastoreTracker().track(ds.getId(),
                        VsphereEnumerationHelper.getSelfLinkFromOperation(o));
            } else {
                enumerationProgress.getDatastoreTracker().track(ds.getId(), ResourceTracker.ERROR);
            }
        };
    }

    static ResourceGroupState makeStorageGroup(DatastoreOverlay ds, EnumerationProgress ctx) {
        ResourceGroupState res = new ResourceGroupState();
        res.id = ds.getName();
        res.name = "Hosts that can access datastore '" + ds.getName() + "'";
        res.endpointLink = ctx.getRequest().endpointLink;
        AdapterUtils.addToEndpointLinks(res, ctx.getRequest().endpointLink);
        res.tenantLinks = ctx.getTenantLinks();
        CustomProperties.of(res)
                .put(CustomProperties.MOREF, ds.getId())
                .put(CustomProperties.TARGET_LINK, ctx.getDatastoreTracker().getSelfLink(ds.getId
                        ()));
        res.documentSelfLink = VsphereEnumerationHelper.computeGroupStableLink(ds.getId(),
                VSphereIncrementalEnumerationService.PREFIX_DATASTORE, ctx.getRequest()
                        .endpointLink);

        return res;
    }

    static void updateStorageStats(VSphereIncrementalEnumerationService
                                           vSphereIncrementalEnumerationService, DatastoreOverlay
                                           ds, String selfLink) {
        ResourceMetrics metrics = new ResourceMetrics();
        metrics.timestampMicrosUtc = Utils.getNowMicrosUtc();
        metrics.documentSelfLink = StatsUtil.getMetricKey(selfLink, metrics.timestampMicrosUtc);
        metrics.entries = new HashMap<>();
        metrics.entries.put(STORAGE_USED_BYTES, (double) ds.getCapacityBytes() - ds
                .getFreeSpaceBytes());
        metrics.entries.put(STORAGE_AVAILABLE_BYTES, (double) ds.getFreeSpaceBytes());
        metrics.documentExpirationTimeMicros = Utils.getNowMicrosUtc() + TimeUnit.DAYS.toMicros(
                SingleResourceStatsCollectionTaskService.EXPIRATION_INTERVAL);

        metrics.customProperties = new HashMap<>();
        metrics.customProperties
                .put(ResourceMetrics.PROPERTY_RESOURCE_LINK, selfLink);

        Operation.createPost(UriUtils.buildUri(
                ClusterUtil.getClusterUri(vSphereIncrementalEnumerationService.getHost(),
                        ServiceTypeCluster.METRIC_SERVICE),
                ResourceMetricsService.FACTORY_LINK))
                .setBodyNoCloning(metrics)
                .sendWith(vSphereIncrementalEnumerationService);
    }

    static void createNewStorageDescription(VSphereIncrementalEnumerationService service,
                                            EnumerationProgress enumerationProgress,
                                            DatastoreOverlay ds) {
        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();
        String regionId = enumerationProgress.getRegionId();
        StorageDescription desc = makeStorageFromResults(request, ds, regionId);
        desc.tenantLinks = enumerationProgress.getTenantLinks();
        service.logFine(() -> String.format("Found new Datastore %s", ds.getName()));

        VsphereEnumerationHelper.submitWorkToVSpherePool(service, () -> {
            VsphereEnumerationHelper.populateTags(service, enumerationProgress, ds, desc);

            Operation.createPost(
                    PhotonModelUriUtils.createInventoryUri(service.getHost(),
                            StorageDescriptionService.FACTORY_LINK))
                    .setBody(desc)
                    .setCompletion((o, e) -> {
                        trackDatastore(enumerationProgress, ds).handle(o, e);
                        Operation.createPost(PhotonModelUriUtils.createInventoryUri(service
                                        .getHost(),
                                ResourceGroupService.FACTORY_LINK))
                                .setBody(makeStorageGroup(ds, enumerationProgress))
                                .sendWith(service);
                        updateStorageStats(service, ds, o.getBody(ServiceDocument.class)
                                .documentSelfLink);
                    })
                    .sendWith(service);
        });
    }

    static void updateStorageDescription(VSphereIncrementalEnumerationService
                                                 vSphereIncrementalEnumerationService,
                                         StorageDescription oldDocument,
                                         EnumerationProgress enumerationProgress,
                                         DatastoreOverlay ds) {
        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();
        String regionId = enumerationProgress.getRegionId();

        StorageDescription desc = makeStorageFromResults(request, ds, regionId);
        desc.documentSelfLink = oldDocument.documentSelfLink;
        desc.resourcePoolLink = null;

        if (oldDocument.tenantLinks == null) {
            desc.tenantLinks = enumerationProgress.getTenantLinks();
        }

        vSphereIncrementalEnumerationService.logFine(() -> String.format("Syncing Storage %s", ds
                .getName()));
        Operation.createPatch(PhotonModelUriUtils.createInventoryUri
                (vSphereIncrementalEnumerationService.getHost(), desc.documentSelfLink))
                .setBody(desc)
                .setCompletion((o, e) -> {
                    trackDatastore(enumerationProgress, ds).handle(o, e);
                    if (e == null) {
                        VsphereEnumerationHelper.submitWorkToVSpherePool(
                                vSphereIncrementalEnumerationService, () -> {
                                    VsphereEnumerationHelper.updateLocalTags(
                                            vSphereIncrementalEnumerationService, enumerationProgress,
                                            ds, o.getBody(ResourceState.class));
                                    updateStorageStats(vSphereIncrementalEnumerationService, ds, o
                                            .getBody(ServiceDocument.class).documentSelfLink);
                                });
                    }
                }).sendWith(vSphereIncrementalEnumerationService);
    }

    static void processFoundDatastore(VSphereIncrementalEnumerationService
                                              vSphereIncrementalEnumerationService,
                                      EnumerationProgress enumerationProgress, DatastoreOverlay
                                              ds) {
        QueryTask task = queryForStorage(enumerationProgress, ds.getName(), null);

        VsphereEnumerationHelper.withTaskResults(vSphereIncrementalEnumerationService, task,
                result -> {
                    if (result.documentLinks.isEmpty()) {
                        createNewStorageDescription(vSphereIncrementalEnumerationService,
                                enumerationProgress, ds);
                    } else {
                        StorageDescription oldDocument = VsphereEnumerationHelper
                                .convertOnlyResultToDocument(result,
                                        StorageDescription.class);
                        updateStorageDescription(vSphereIncrementalEnumerationService, oldDocument,
                                enumerationProgress, ds);
                    }
                });
    }
}
