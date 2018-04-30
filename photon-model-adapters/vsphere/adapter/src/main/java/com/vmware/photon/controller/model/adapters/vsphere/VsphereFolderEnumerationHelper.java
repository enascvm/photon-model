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
import static com.vmware.photon.controller.model.adapters.vsphere.VsphereEnumerationHelper.getSelfLinkFromOperation;
import static com.vmware.photon.controller.model.adapters.vsphere.VsphereEnumerationHelper.withTaskResults;

import java.util.Collections;
import java.util.List;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.util.PhotonModelUriUtils;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.ObjectUpdateKind;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;

public class VsphereFolderEnumerationHelper {

    static void processFoundFolder(VSphereIncrementalEnumerationService service, EnumerationProgress ctx,
                                   FolderOverlay folder, List<FolderOverlay> rootFolders, EnumerationClient client) {
        QueryTask task = queryForFolder(ctx, folder);
        withTaskResults(service, task, ctx.getFolderTracker(), (ServiceDocumentQueryResult result) -> {
            try {
                if (result.documentLinks.isEmpty()) {
                    createFolder(service, ctx, folder, rootFolders, client);
                } else {
                    ResourceGroupState oldDocument = convertOnlyResultToDocument(result, ResourceGroupState.class);
                    updateFolder(service, ctx, folder, oldDocument, rootFolders, client, true);
                }
            } catch (Exception e) {
                service.logSevere("Error occurred while processing folder!", Utils.toString(e));
                ctx.getFolderTracker().track(folder.getId(), ResourceTracker.ERROR);
            }
        });
    }

    private static ResourceGroupState makeFolderFromChanges(
            FolderOverlay folder,
            EnumerationClient client) throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        ResourceGroupState state = new ResourceGroupState();
        // Folder name change
        state.name = folder.getNameOrNull();
        // Folder parent change
        if (null != folder.getParentOrNull()) {
            // retrieve parent folder Moref
            ManagedObjectReference parent = folder.getParentOrNull();
            // retrieve parent of parent folder
            ManagedObjectReference parentOfParentFolder = client.getParentOfFolder(parent);
            // if the parent of parent folder is DC, then the parent is root folder. Add parent of parent during this scenario.
            if (parentOfParentFolder.getType().equals(VimNames.TYPE_DATACENTER)) {
                CustomProperties.of(state)
                        .put(CustomProperties.PARENT_ID, VimUtils.convertMoRefToString(parentOfParentFolder));
            } else {
                CustomProperties.of(state)
                        .put(CustomProperties.PARENT_ID, VimUtils.convertMoRefToString(parent));
            }
        }
        return state;
    }

    private static ResourceGroupState makeFolderFromResults(EnumerationProgress ctx, FolderOverlay folder,
                                                            List<FolderOverlay> rootFolders, EnumerationClient client) throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        ResourceGroupState state = new ResourceGroupState();
        state.name = state.id = folder.getName();
        state.endpointLink = ctx.getRequest().endpointLink;
        AdapterUtils.addToEndpointLinks(state, ctx.getRequest().endpointLink);
        state.tenantLinks = ctx.getTenantLinks();

        // Get the parent of the folder.
        // If the parent is one of the root folder, set the parent as root folders parent
        String parent = null;
        if (!rootFolders.isEmpty()) {
            for (FolderOverlay rootFolderOverlay : rootFolders) {
                if (rootFolderOverlay.getMoRefValue().equals(folder.getParent().getValue())) {
                    parent = VimUtils.convertMoRefToString(rootFolderOverlay.getParent());
                    break;
                }
            }
        } else {
            // retrieve parent folder Moref
            ManagedObjectReference parentFolder = folder.getParent();
            // retrieve parent of parent folder
            ManagedObjectReference parentOfParentFolder = client.getParentOfFolder(parentFolder);
            if (parentOfParentFolder.getType().equals(VimNames.TYPE_DATACENTER)) {
                parent = VimUtils.convertMoRefToString(parentOfParentFolder);
            } else {
                parent = VimUtils.convertMoRefToString(parentFolder);
            }
        }

        CustomProperties.of(state)
                .put(CustomProperties.MOREF, folder.getId())
                .put(CustomProperties.TYPE, folder.getId().getType())
                .put(CustomProperties.DATACENTER_SELF_LINK, ctx.getDcLink())
                .put(ComputeProperties.ENDPOINT_LINK_PROP_NAME, ctx.getRequest().endpointLink)
                .put(CustomProperties.DATACENTER, ctx.getRegionId())
                .put(CustomProperties.PARENT_ID, parent)
                .put(CustomProperties.VC_VIEW, folder.getView())
                .put(CustomProperties.FOLDER_TYPE, folder.getFolderType());
        VsphereEnumerationHelper.populateResourceStateWithAdditionalProps(state, ctx.getVcUuid());
        return state;
    }

    private static void createFolder(VSphereIncrementalEnumerationService service, EnumerationProgress ctx,
                                     FolderOverlay folder, List<FolderOverlay> rootFolders, EnumerationClient client)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        ResourceGroupState state = makeFolderFromResults(ctx, folder, rootFolders, client);
        Operation.createPost(PhotonModelUriUtils.createInventoryUri(service.getHost(), ResourceGroupService.FACTORY_LINK))
                .setBody(state)
                .setCompletion((o, e) -> {
                    trackFolder(ctx, folder).handle(o, e);
                    service.logInfo("Creating document for folder with name %s", folder.getName());
                })
                .sendWith(service);
    }

    private static void updateFolder(VSphereIncrementalEnumerationService service, EnumerationProgress ctx,
                                     FolderOverlay folder, ResourceGroupState oldDocument,
                                     List<FolderOverlay> rootFolders, EnumerationClient client, boolean fullUpdate)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        ResourceGroupState state;
        if (fullUpdate) {
            state = makeFolderFromResults(ctx, folder, rootFolders, client);
        } else {
            state = makeFolderFromChanges(folder, client);
        }

        state.documentSelfLink = oldDocument.documentSelfLink;
        Operation.createPatch(PhotonModelUriUtils.createInventoryUri(service.getHost(), state.documentSelfLink))
                .setBody(state)
                .setCompletion((o, e) -> {
                    trackFolder(ctx, folder).handle(o, e);
                    service.logInfo("updating document for folder: %s  ", folder.getId());
                })
                .sendWith(service);
    }

    private static QueryTask queryForFolder(EnumerationProgress ctx, FolderOverlay folder) {
        String moref = VimUtils.convertMoRefToString(folder.getId());
        Builder builder = QueryTask.Query.Builder.create()
                .addKindFieldClause(ResourceGroupState.class)
                .addCompositeFieldClause(ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                        CustomProperties.MOREF, moref);

        QueryUtils.addEndpointLink(builder, ResourceGroupService.ResourceGroupState.class,
                ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());

        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();
    }

    private static CompletionHandler trackFolder(EnumerationProgress enumerationProgress,
                                                 FolderOverlay folder) {
        return (o, e) -> {
            enumerationProgress.touchResource(getSelfLinkFromOperation(o));
            if (e == null) {
                enumerationProgress.getFolderTracker().track(folder.getId(), getSelfLinkFromOperation(o));
            } else {
                enumerationProgress.getFolderTracker().track(folder.getId(), ResourceTracker.ERROR);
            }
        };
    }

    public static void handleFolderChanges(
            VSphereIncrementalEnumerationService service, List<FolderOverlay> folderOverlays,
            EnumerationProgress enumerationProgress, EnumerationClient client) {
        enumerationProgress.expectFolderCount(folderOverlays.size());
        for (FolderOverlay folderOverlay : folderOverlays) {
            try {
                if (ObjectUpdateKind.ENTER == folderOverlay.getObjectUpdateKind()) {

                    createFolder(service, enumerationProgress, folderOverlay, Collections.emptyList(), client);
                } else {
                    QueryTask task = queryForFolder(enumerationProgress, folderOverlay);
                    withTaskResults(service, task, enumerationProgress.getFolderTracker(), result -> {
                        try {
                            if (!result.documentLinks.isEmpty()) {
                                ResourceGroupState oldDocument = convertOnlyResultToDocument(result, ResourceGroupState.class);
                                if (ObjectUpdateKind.MODIFY.equals(folderOverlay.getObjectUpdateKind())) {
                                    try {
                                        updateFolder(
                                                service, enumerationProgress, folderOverlay, oldDocument, Collections.emptyList(), client, false);
                                    } catch (Exception e) {
                                        service.logSevere("Error occurred while processing folder: %s", Utils.toString(e));
                                        enumerationProgress.getFolderTracker().track(folderOverlay.getId(), ResourceTracker.ERROR);
                                    }
                                } else {
                                    Operation.createDelete(
                                            PhotonModelUriUtils.createInventoryUri(service.getHost(), oldDocument.documentSelfLink))
                                            .setCompletion((o, e) -> {
                                                enumerationProgress.getFolderTracker().track();
                                            }).sendWith(service);
                                }
                            } else {
                                enumerationProgress.getFolderTracker().track();
                            }
                        } catch (Exception e) {
                            service.logSevere("Error occurred while processing folder: %s", Utils.toString(e));
                            enumerationProgress.getFolderTracker().track(folderOverlay.getId(), ResourceTracker.ERROR);
                        }
                    });
                }
            } catch (Exception e) {
                service.logSevere("Error occurred while creating folder: %s", Utils.toString(e));
                enumerationProgress.getFolderTracker().track(folderOverlay.getId(), ResourceTracker.ERROR);
            }
        }

        try {
            enumerationProgress.getFolderTracker().await();
        } catch (InterruptedException e) {
            service.logSevere("Interrupted during incremental enumeration for folders: %s", Utils.toString(e));
        }
    }
}