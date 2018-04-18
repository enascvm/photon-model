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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.vmware.pbm.PbmProfile;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.util.PhotonModelUriUtils;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

public class VsphereStoragePolicyEnumerationHelper {


    static QueryTask queryForStoragePolicy(EnumerationProgress ctx, String id, String name) {
        return queryForStoragePolicy(ctx.getTenantLinks(), id, name);
    }

    static QueryTask queryForStoragePolicy(List<String> tenantLinks, String id, String name) {
        Builder builder = Builder.create()
                .addFieldClause(ResourceState.FIELD_NAME_ID, id)
                .addKindFieldClause(ResourceGroupState.class)
                .addCaseInsensitiveFieldClause(ResourceState.FIELD_NAME_NAME, name,
                        MatchType.TERM, Occurance.MUST_OCCUR);
        QueryUtils.addTenantLinks(builder, tenantLinks);
        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();
    }

    static ResourceGroupState makeStoragePolicyFromResults(ComputeEnumerateResourceRequest request,
                                                           StoragePolicyOverlay sp, final String dcLink) {
        ResourceGroupState res = new ResourceGroupState();
        res.id = sp.getProfileId();
        res.name = sp.getName();
        res.desc = sp.getDescription();
        res.endpointLink = request.endpointLink;
        AdapterUtils.addToEndpointLinks(res, request.endpointLink);
        res.customProperties = sp.getCapabilities();
        CustomProperties.of(res)
                .put(ComputeProperties.RESOURCE_TYPE_KEY, sp.getType())
                .put(CustomProperties.DATACENTER_SELF_LINK, dcLink)
                .put(ComputeProperties.ENDPOINT_LINK_PROP_NAME, request.endpointLink);

        return res;
    }

    static CompletionHandler trackStoragePolicy(VSphereIncrementalEnumerationService service,
                                                EnumerationProgress enumerationProgress,
                                                StoragePolicyOverlay sp, EnumerationClient client) {
        return (o, e) -> {
            try {
                List<String> disksAssociatedWithSP = client.getAssociatedDisksForStoragePolicy(sp.getPbmProfile());
                Map<String, List<String>> map = enumerationProgress.getDiskToStoragePolicyAssociationMap();
                if (!map.isEmpty()) {
                    ResourceGroupState resourceGroupState = o.getBody(ResourceGroupState.class);
                    for (String disk : disksAssociatedWithSP) {
                        if (null != map.get(disk)) {
                            List<String> storagePolicies = map.get(disk);
                            storagePolicies.add(resourceGroupState.documentSelfLink);
                        } else {
                            List<String> storagePolicy = new ArrayList<>();
                            storagePolicy.add(resourceGroupState.documentSelfLink);
                            map.put(disk, storagePolicy);
                        }
                    }
                }
            } catch (Exception exception) {
                service.logSevere("Error while retrieving disks associated with storage policy %s", sp.getName());
            }
            enumerationProgress.touchResource(VsphereEnumerationHelper.getSelfLinkFromOperation(o));
            if (e != null) {
                service.logFine(() -> String
                        .format("Error in syncing resource group for Storage Policy %s",
                                sp.getName()));
            }
            enumerationProgress.getStoragePolicyTracker().track();
        };
    }

    static void updateStorageDescription(VSphereIncrementalEnumerationService service,
                                         Stream<Operation> opStream, String spSelfLink,
                                         ServiceDocumentQueryResult result) {
        List<Operation> patchOps = new ArrayList<>();
        List<String> originalLinks = new ArrayList<>();
        if (result.documentLinks != null) {
            originalLinks.addAll(result.documentLinks);
        }

        opStream.forEach(op -> {
            StorageDescription storageDescription = op.getBody
                    (StorageDescription.class);
            if (result.documentLinks != null && result.documentLinks
                    .contains(storageDescription.documentSelfLink)) {
                originalLinks.remove(storageDescription.documentSelfLink);
            } else {
                if (storageDescription.groupLinks == null) {
                    storageDescription.groupLinks = new HashSet<>();
                }
                storageDescription.groupLinks.add(spSelfLink);
                patchOps.add(Operation.createPatch(PhotonModelUriUtils.createInventoryUri(service.getHost(),
                        storageDescription.documentSelfLink))
                        .setBody(storageDescription));
            }
        });

        // In this case, we need to update the datastore by removing the policy group link
        if (!originalLinks.isEmpty()) {
            originalLinks.stream().forEach(link -> {
                Map<String, Collection<Object>> collectionsToRemove = Collections
                        .singletonMap(ResourceState.FIELD_NAME_GROUP_LINKS,
                                Collections.singletonList(spSelfLink));

                ServiceStateCollectionUpdateRequest updateGroupLinksRequest = ServiceStateCollectionUpdateRequest
                        .create(null, collectionsToRemove);

                patchOps.add(Operation.createPatch(
                        PhotonModelUriUtils.createInventoryUri(service.getHost(), link))
                        .setBody(updateGroupLinksRequest));
            });
        }

        if (!patchOps.isEmpty()) {
            OperationJoin.create(patchOps)
                    .setCompletion((ops, exs) -> {
                        if (exs != null) {
                            service.logFine(() -> String.format("Syncing Storage policy failed %s",
                                    Utils.toString(exs)));
                        }
                    }).sendWith(service);
        }
    }

    static void updateDataStoreWithStoragePolicyGroup(
            VSphereIncrementalEnumerationService service, EnumerationProgress ctx,
            StoragePolicyOverlay sp, String selfLink) {
        List<Operation> getOps = new ArrayList<>();
        sp.getDatastoreIds().stream().forEach(id -> {
            if (null != ctx.getDatastoreTracker()) {
                String dataStoreLink = ctx.getDatastoreTracker()
                        .getSelfLink(id, VimNames.TYPE_DATASTORE);
                if (dataStoreLink != null && !ResourceTracker.ERROR.equals(dataStoreLink)) {
                    getOps.add(Operation.createGet(
                            PhotonModelUriUtils.createInventoryUri(service.getHost(), dataStoreLink)));
                }
            } else {
                ManagedObjectReference dataStoreMoref = new ManagedObjectReference();
                dataStoreMoref.setType(VimNames.TYPE_DATASTORE);
                dataStoreMoref.setValue(id);
                QueryTask task = VsphereDatastoreEnumerationHelper.queryForStorage(
                        ctx, null, VimUtils.convertMoRefToString(dataStoreMoref), selfLink);

                VsphereEnumerationHelper.withTaskResults(service, task, result -> {
                    List<Operation> getOperations = new ArrayList<>();
                    if (result.documentLinks.size() > 0) {
                        for (String documentSelfLink : result.documentLinks) {
                            getOperations.add(Operation.createGet(PhotonModelUriUtils.createInventoryUri(service.getHost(), documentSelfLink)));
                        }
                        //TODO : Refactor this to avoid extra call to fetch datastores
                        updateDS(service, ctx, selfLink, getOperations);
                    }
                });
            }
        });

        updateDS(service, ctx, selfLink, getOps);
    }

    private static void updateDS(VSphereIncrementalEnumerationService service, EnumerationProgress ctx, String selfLink, List<Operation> getOps) {
        if (!getOps.isEmpty()) {
            OperationJoin.create(getOps)
                    .setCompletion((ops, exs) -> {
                        if (exs != null) {
                            service.logFine(() -> String.format("Syncing Storage policy failed %s",
                                    Utils.toString(exs)));
                        } else {
                            QueryTask task = VsphereDatastoreEnumerationHelper.queryForStorage(
                                    ctx, null, null, selfLink);
                            VsphereEnumerationHelper.withTaskResults(service, task, result -> {
                                // Call patch on all to update the group links
                                updateStorageDescription(service, ops.values().stream(), selfLink, result);
                            }, 0);
                        }
                    }).sendWith(service);
        }
    }


    static void updateStoragePolicy(VSphereIncrementalEnumerationService service, ResourceGroupState oldDocument,
                                    EnumerationProgress enumerationProgress, StoragePolicyOverlay sp, EnumerationClient client) {

        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();
        ResourceGroupState rgState = makeStoragePolicyFromResults(request, sp, enumerationProgress.getDcLink());
        rgState.documentSelfLink = oldDocument.documentSelfLink;

        if (oldDocument.tenantLinks == null) {
            rgState.tenantLinks = enumerationProgress.getTenantLinks();
        }

        service.logFine(() -> String.format("Syncing Storage %s", sp.getName()));
        Operation.createPatch(PhotonModelUriUtils.createInventoryUri(service.getHost(), rgState.documentSelfLink))
                .setBody(rgState)
                .setCompletion((o, e) -> {
                    trackStoragePolicy(service, enumerationProgress, sp, client).handle(o, e);
                    if (e == null) {
                        // Update all compatible datastores group link with the self link of this
                        // storage policy
                        updateDataStoreWithStoragePolicyGroup(service, enumerationProgress, sp,
                                o.getBody(ResourceGroupState.class).documentSelfLink);
                    }
                }).sendWith(service);
    }

    static void createNewStoragePolicy(VSphereIncrementalEnumerationService service,
                                       EnumerationProgress enumerationProgress, StoragePolicyOverlay sp, EnumerationClient client) {

        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();
        ResourceGroupState rgState = makeStoragePolicyFromResults(request, sp, enumerationProgress.getDcLink());
        rgState.tenantLinks = enumerationProgress.getTenantLinks();
        service.logFine(() -> String.format("Found new Storage Policy %s", sp.getName()));

        Operation.createPost(PhotonModelUriUtils.createInventoryUri(service.getHost(),
                ResourceGroupService.FACTORY_LINK))
                .setBody(rgState)
                .setCompletion((o, e) -> {
                    trackStoragePolicy(service, enumerationProgress, sp, client).handle(o, e);
                    // Update all compatible datastores group link with the self link of this
                    // storage policy
                    updateDataStoreWithStoragePolicyGroup(service, enumerationProgress, sp,
                            o.getBody(ResourceGroupState.class).documentSelfLink);
                })
                .sendWith(service);
    }

    /**
     * Process the found storage policy by creating a new resource group if not found already. If
     * it already present, then update its properties. Process the updates on its compatible
     * datastores.
     */
    static void processFoundStoragePolicy(VSphereIncrementalEnumerationService service, EnumerationProgress enumerationProgress,
                                          StoragePolicyOverlay sp, EnumerationClient client) {
        QueryTask task = queryForStoragePolicy(enumerationProgress, sp.getProfileId(), sp.getName());

        VsphereEnumerationHelper.withTaskResults(service, task, result -> {
            try {
                if (result.documentLinks.isEmpty()) {
                    createNewStoragePolicy(service, enumerationProgress, sp, client);
                } else {
                    ResourceGroupState oldDocument = VsphereEnumerationHelper.convertOnlyResultToDocument(result,
                            ResourceGroupState.class);
                    updateStoragePolicy(service, oldDocument, enumerationProgress, sp, client);
                }
            } catch (Exception e) {
                service.logSevere("Error while processing storage policy: %s", Utils.toString(e));
                enumerationProgress.getStoragePolicyTracker().track();
            }
        });
    }

    static List<StoragePolicyOverlay> createStorageProfileOverlays(
            VSphereIncrementalEnumerationService service, EnumerationClient client, EnumerationProgress ctx) {
        List<StoragePolicyOverlay> storagePolicies = new ArrayList<>();
        try {
            List<PbmProfile> pbmProfiles = client.retrieveStoragePolicies();
            if (!pbmProfiles.isEmpty()) {
                for (PbmProfile profile : pbmProfiles) {
                    List<String> datastoreIds = client.getDatastores(profile.getProfileId());
                    StoragePolicyOverlay spOverlay = new StoragePolicyOverlay(profile, datastoreIds);
                    storagePolicies.add(spOverlay);
                }
            }
        } catch (Exception e) {
            // vSphere throws exception even if there are no storage policies found on the server.
            // Hence we can just log the message and continue, as with the datastore selection
            // still provisioning can proceed. Not marking the task to failure here.
            String msg = "Error processing Storage policy ";
            service.logWarning(() -> msg + ": " + e.toString());
        }
        return storagePolicies;
    }

    public static void syncStorageProfiles(VSphereIncrementalEnumerationService service, EnumerationClient client, EnumerationProgress ctx) {
        List<StoragePolicyOverlay> storagePolicies;

        storagePolicies = createStorageProfileOverlays(service, client, ctx);
        if (storagePolicies.size() > 0) {
            ctx.expectStoragePolicyCount(storagePolicies.size());
            for (StoragePolicyOverlay sp : storagePolicies) {
                processFoundStoragePolicy(service, ctx, sp, client);
            }

            // checkpoint for storage policy
            try {
                ctx.getStoragePolicyTracker().await();
            } catch (InterruptedException e) {
                service.logSevere("Error while syncing storage profiles: %s", Utils.toString(e));
                return;
            }
        }
    }
}
