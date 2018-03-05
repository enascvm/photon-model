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

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.util.PhotonModelUriUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;

public class VsphereFolderEnumerationHelper  {

    // This method will also process the root folders - vm, host, network which are not physically present in
    // the vCenter
    static void processFoundFolder(VSphereIncrementalEnumerationService service, EnumerationProgress ctx, FolderOverlay folder) {
        QueryTask task = queryForFolder(ctx, folder);
        withTaskResults(service,task, (ServiceDocumentQueryResult result) -> {
            if (result.documentLinks.isEmpty()) {
                createFolder(service, ctx, folder);
            } else {
                ResourceGroupService.ResourceGroupState oldDocument = convertOnlyResultToDocument(result, ResourceGroupService.ResourceGroupState.class);
                updateFolder(service, ctx, folder, oldDocument);
            }
        });
    }

    private static ResourceGroupService.ResourceGroupState makeFolderFromResults(EnumerationProgress ctx, FolderOverlay folder) {
        ResourceGroupService.ResourceGroupState state =  new ResourceGroupService.ResourceGroupState();
        state.name = state.id = folder.getName();
        state.endpointLink = ctx.getRequest().endpointLink;
        AdapterUtils.addToEndpointLinks(state, ctx.getRequest().endpointLink);
        state.tenantLinks = ctx.getTenantLinks();

        CustomProperties.of(state)
                .put(CustomProperties.MOREF, folder.getId())
                .put(CustomProperties.TYPE, folder.getId().getType())
                .put(ComputeProperties.ENDPOINT_LINK_PROP_NAME, ctx.getRequest().endpointLink)
                .put(CustomProperties.DATACENTER, ctx.getRegionId())
                .put(CustomProperties.PARENT_ID, folder.getParent().getValue())
                .put(CustomProperties.VC_VIEW, folder.getView())
                .put(CustomProperties.FOLDER_TYPE, folder.getFolderType());

        return state;
    }

    private static void createFolder(VSphereIncrementalEnumerationService service, EnumerationProgress ctx, FolderOverlay folder) {
        ResourceGroupService.ResourceGroupState state = makeFolderFromResults(ctx, folder);
        Operation.createPost(PhotonModelUriUtils.createInventoryUri(service.getHost(), ResourceGroupService.FACTORY_LINK))
                .setBody(state)
                .setCompletion((o,e) -> {
                    trackFolder(ctx, folder).handle(o, e);
                    service.logInfo("Creating document for folder with name %s", folder.getName());
                })
                .sendWith(service);

    }

    private static void updateFolder(VSphereIncrementalEnumerationService service, EnumerationProgress ctx, FolderOverlay folder, ResourceGroupService.ResourceGroupState oldDocument) {
        ResourceGroupService.ResourceGroupState state =  makeFolderFromResults(ctx, folder);
        state.documentSelfLink = oldDocument.documentSelfLink;
        Operation.createPatch(PhotonModelUriUtils.createInventoryUri(service.getHost(), state.documentSelfLink))
                .setBody(state)
                .setCompletion((o,e) -> {
                    trackFolder(ctx, folder).handle(o, e);
                    service.logInfo("updating document for folder: %s  ", folder.getName());
                })
                .sendWith(service);

    }

    private static QueryTask queryForFolder(EnumerationProgress ctx, FolderOverlay folder) {
        String moref = VimUtils.convertMoRefToString(folder.getId());
        Builder builder = QueryTask.Query.Builder.create()
                .addKindFieldClause(ResourceGroupService.ResourceGroupState.class)
                .addCompositeFieldClause(ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                        CustomProperties.MOREF, moref)
                .addCompositeFieldClause(ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                        ComputeProperties.ENDPOINT_LINK_PROP_NAME, ctx.getRequest().endpointLink);

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
}
