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

import org.apache.commons.lang3.StringUtils;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.vsphere.util.finders.Element;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.util.PhotonModelUriUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.services.common.QueryTask;

public class VsphereDatacenterEnumerationHelper {
    static void processDatacenterInfo(VSphereIncrementalEnumerationService service, Element element, EnumerationProgress ctx) {
        QueryTask task = queryForDatacenter(ctx, VimUtils.convertMoRefToString(element.object));
        withTaskResults(service, task, (ServiceDocumentQueryResult result) -> {
            if (result.documentLinks.isEmpty()) {
                createDatacenter(service, ctx, element);
            } else {
                ResourceGroupService.ResourceGroupState oldDocument = convertOnlyResultToDocument(result, ResourceGroupService.ResourceGroupState.class);
                updateDatacenter(service, ctx, element, oldDocument);
            }
        });
    }

    private static ResourceGroupService.ResourceGroupState makeDatacenterFromResults(EnumerationProgress ctx, Element element) {
        ComputeEnumerateResourceRequest request = ctx.getRequest();
        String dcName = StringUtils.substringAfterLast(element.path, "/");
        String moref = VimUtils.convertMoRefToString(element.object);
        String type = element.object.getType();

        ResourceGroupService.ResourceGroupState state = new ResourceGroupService.ResourceGroupState();
        state.name = dcName;
        state.endpointLink = request.endpointLink;
        state.tenantLinks = ctx.getTenantLinks();
        AdapterUtils.addToEndpointLinks(state, request.endpointLink);

        CustomProperties.of(state)
                .put(CustomProperties.MOREF, moref)
                .put(CustomProperties.TYPE, type)
                .put(ComputeProperties.ENDPOINT_LINK_PROP_NAME, request.endpointLink);

        return state;
    }

    private static void createDatacenter(VSphereIncrementalEnumerationService service, EnumerationProgress ctx, Element element) {
        ResourceGroupService.ResourceGroupState state = makeDatacenterFromResults(ctx, element);

        Operation.createPost(PhotonModelUriUtils.createInventoryUri(service.getHost(), ResourceGroupService.FACTORY_LINK))
                .setBody(state)
                .setCompletion((o, e) -> {
                    ctx.touchResource(getSelfLinkFromOperation(o));
                    service.logInfo("Creating Document for datacenter: %s  ", state.name);
                })
                .sendWith(service);
    }

    private static void updateDatacenter(VSphereIncrementalEnumerationService service, EnumerationProgress ctx, Element element, ResourceGroupService.ResourceGroupState oldDocument) {
        ResourceGroupService.ResourceGroupState state =  makeDatacenterFromResults(ctx, element);
        state.documentSelfLink = oldDocument.documentSelfLink;
        Operation.createPatch(PhotonModelUriUtils.createInventoryUri(service.getHost(), oldDocument.documentSelfLink))
                .setBody(state)
                .setCompletion((o,e) -> {
                    ctx.touchResource(getSelfLinkFromOperation(o));
                    service.logInfo("Syncing document for datacenter: %s  ", state.name);
                })
                .sendWith(service);
    }

    private static QueryTask queryForDatacenter(EnumerationProgress ctx, String moref) {
        QueryTask.Query.Builder builder = QueryTask.Query.Builder.create()
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
}
