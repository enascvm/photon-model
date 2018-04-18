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

import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.util.PhotonModelUriUtils;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ObjectUpdateKind;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

public class VSphereHostSystemEnumerationHelper {
    /**
     * Builds a query for finding a HostSystems by its manage object reference.
     */
    private static QueryTask queryForHostSystem(
            EnumerationProgress ctx, String parentComputeLink, String moRefId) {
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

    private static ComputeDescription makeDescriptionForHostFromChange(HostSystemOverlay hs) {
        ComputeDescription res = new ComputeDescription();
        res.name = hs.getNameOrNull();

        return res;
    }

    private static ComputeDescription makeDescriptionForHost(
            VSphereIncrementalEnumerationService service, EnumerationProgress enumerationProgress,
            HostSystemOverlay hs) {
        ComputeDescription res = new ComputeDescription();
        res.name = hs.getName();
        res.documentSelfLink =
                buildUriPath(ComputeDescriptionService.FACTORY_LINK, service.getHost().nextUUID());
        res.cpuCount = hs.getCoreCount();
        res.endpointLink = enumerationProgress.getRequest().endpointLink;
        AdapterUtils.addToEndpointLinks(res, enumerationProgress.getRequest().endpointLink);
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
        VsphereEnumerationHelper.populateResourceStateWithAdditionalProps(res, enumerationProgress.getVcUuid(),
                hs.getId());
        return res;
    }

    private static ComputeState makeHostSystemFromChanges(
            EnumerationProgress enumerationProgress, HostSystemOverlay hs, EnumerationClient client)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        ComputeState state = new ComputeState();
        // add name if its changed.
        state.name = hs.getNameOrNull();
        // if host goes into maintenance mode
        if (hs.isInMaintenanceMode()) {
            state.powerState = PowerState.SUSPEND;
        }
        // update group links if there are any addition/ removal of data stores and networks
        state.groupLinks = VsphereEnumerationHelper.getConnectedDatastoresAndNetworks(
                enumerationProgress, hs.getDatastore(), hs.getNetwork(), client);

        state.type = hs.isClusterHost() ? ComputeType.CLUSTER_HOST : ComputeType.VM_HOST;
        if (hs.isClusterHost()) {
            CustomProperties.of(state)
                    .put(CustomProperties.CLUSTER_LINK, enumerationProgress
                            .getComputeResourceTracker().getSelfLink(hs.getParentMoref()));
        }

        return state;
    }

    private static ComputeState makeHostSystemFromResults(EnumerationProgress enumerationProgress,
                                                          HostSystemOverlay hs, EnumerationClient client)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        ComputeState state = new ComputeState();
        state.type = hs.isClusterHost() ? ComputeType.CLUSTER_HOST : ComputeType.VM_HOST;
        state.environmentName = ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;
        state.endpointLink = enumerationProgress.getRequest().endpointLink;
        AdapterUtils.addToEndpointLinks(state, enumerationProgress.getRequest().endpointLink);
        state.regionId = enumerationProgress.getRegionId();
        state.id = hs.getId().getValue();
        state.adapterManagementReference = enumerationProgress
                .getRequest().adapterManagementReference;
        state.parentLink = enumerationProgress.getRequest().resourceLink();
        state.resourcePoolLink = enumerationProgress.getRequest().resourcePoolLink;
        state.groupLinks = VsphereEnumerationHelper
                .getConnectedDatastoresAndNetworks(enumerationProgress, hs.getDatastore(), hs.getNetwork(), client);

        state.name = hs.getName();
        // TODO: retrieve host power state
        state.powerState = PowerState.ON;
        if (hs.isInMaintenanceMode()) {
            state.powerState = PowerState.SUSPEND;
        }
        CustomProperties.of(state)
                .put(CustomProperties.MOREF, hs.getId())
                .put(CustomProperties.DATACENTER_SELF_LINK, enumerationProgress.getDcLink())
                .put(CustomProperties.TYPE, hs.getId().getType())
                .put(CustomProperties.HS_CPU_GHZ, hs.getCpuMhz() / 1024)
                .put(CustomProperties.MANUFACTURER, hs.getVendor())
                .put(CustomProperties.MODEL_NAME, hs.getModel())
                .put(CustomProperties.HS_CPU_PKG_COUNT, hs.getNumCpuPkgs())
                .put(CustomProperties.HS_MEMORY_IN_GB, AdapterUtils.convertBytesToGB(hs.getTotalMemoryBytes()))
                .put(CustomProperties.HS_NIC_COUNT, hs.getNumNics())
                .put(CustomProperties.HS_NICS_INFO, hs.getConsolidatedNicInfo())
                .put(CustomProperties.HS_CPU_DESC, hs.getCpuModel())
                // TODO : Find the logic for setting this prop
                .put(CustomProperties.IS_PHYSICAL, "");

        if (hs.isClusterHost()) {
            CustomProperties.of(state)
                    .put(CustomProperties.CLUSTER_LINK, enumerationProgress
                            .getComputeResourceTracker().getSelfLink(hs.getParentMoref()));
        }

        if (null != hs.isHyperThreadAvailable()) {
            CustomProperties.of(state)
                    .put(CustomProperties.HS_HYPERTHREAD_AVAILABLE, hs.isHyperThreadAvailable().booleanValue());
        }

        if (null != hs.isHyperThreadActive()) {
            CustomProperties.of(state)
                    .put(CustomProperties.HS_HYPERTHREAD_ACTIVE, hs.isHyperThreadAvailable().booleanValue());
        }

        VsphereEnumerationHelper.populateResourceStateWithAdditionalProps(state, enumerationProgress.getVcUuid(),
                hs.getId());
        return state;
    }

    private static CompletionHandler trackHostSystem(EnumerationProgress enumerationProgress,
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

    private static void updateHostSystem(
            VSphereIncrementalEnumerationService service, ComputeState oldDocument,
            EnumerationProgress enumerationProgress, HostSystemOverlay hs, boolean fullUpdate,
            EnumerationClient client) throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        ComputeState state;
        if (fullUpdate) {
            state = makeHostSystemFromResults(enumerationProgress, hs, client);
        } else {
            state = makeHostSystemFromChanges(enumerationProgress, hs, client);
        }

        state.documentSelfLink = oldDocument.documentSelfLink;
        state.resourcePoolLink = null;

        if (oldDocument.tenantLinks == null) {
            state.tenantLinks = enumerationProgress.getTenantLinks();
        }

        service.logFine(() -> String.format("Syncing HostSystem %s", oldDocument.documentSelfLink));
        Operation.createPatch(PhotonModelUriUtils.createInventoryUri(service.getHost(), state.documentSelfLink))
                .setBody(state)
                .setCompletion((o, e) -> {
                    trackHostSystem(enumerationProgress, hs).handle(o, e);
                    if (e == null) {
                        VsphereEnumerationHelper.submitWorkToVSpherePool(service, ()
                                -> VsphereEnumerationHelper
                                .updateLocalTags(service, enumerationProgress, hs, o.getBody(ResourceState.class)));
                    }
                })
                .sendWith(service);

        ComputeDescription desc;
        if (fullUpdate) {
            desc = makeDescriptionForHost(service, enumerationProgress, hs);
        } else {
            desc = makeDescriptionForHostFromChange(hs);
        }

        desc.documentSelfLink = oldDocument.descriptionLink;
        Operation.createPatch(PhotonModelUriUtils.createInventoryUri(service.getHost(), desc.documentSelfLink))
                .setBody(desc)
                .sendWith(service);
    }

    private static void createNewHostSystem(
            VSphereIncrementalEnumerationService service, EnumerationProgress enumerationProgress, HostSystemOverlay hs, EnumerationClient client) throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        ComputeDescription desc = makeDescriptionForHost(service, enumerationProgress, hs);
        desc.tenantLinks = enumerationProgress.getTenantLinks();
        Operation.createPost(
                PhotonModelUriUtils.createInventoryUri(service.getHost(), ComputeDescriptionService.FACTORY_LINK))
                .setBody(desc)
                .sendWith(service);

        ComputeState state = makeHostSystemFromResults(enumerationProgress, hs, client);
        state.descriptionLink = desc.documentSelfLink;
        state.tenantLinks = enumerationProgress.getTenantLinks();

        VsphereEnumerationHelper.submitWorkToVSpherePool(service, () -> {
            VsphereEnumerationHelper.populateTags(service, enumerationProgress, hs, state);

            service.logFine(() -> String.format("Found new HostSystem %s", hs.getName()));
            Operation.createPost(PhotonModelUriUtils.createInventoryUri(service.getHost(), ComputeService.FACTORY_LINK))
                    .setBody(state)
                    .setCompletion(trackHostSystem(enumerationProgress, hs))
                    .sendWith(service);
        });
    }

    /**
     * Process all the host system retrieved from the endpoint.
     */
    public static void processFoundHostSystem(VSphereIncrementalEnumerationService service,
                                              EnumerationProgress enumerationProgress, HostSystemOverlay hs, EnumerationClient client) {
        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();
        QueryTask task = queryForHostSystem(enumerationProgress, request.resourceLink(), hs.getId().getValue());

        withTaskResults(service, task, result -> {
            try {
                if (result.documentLinks.isEmpty()) {
                    createNewHostSystem(service, enumerationProgress, hs, client);
                } else {
                    ComputeState oldDocument = convertOnlyResultToDocument(result, ComputeState.class);
                    updateHostSystem(service, oldDocument, enumerationProgress, hs, true, client);
                }
            } catch (Exception e) {
                service.logSevere("Error occurred while processing host system: %s", Utils.toString(e));
                enumerationProgress.getHostSystemTracker().track();
            }
        });
    }

    public static void handleHostSystemChanges(VSphereIncrementalEnumerationService service, List<HostSystemOverlay> hostSystemOverlays,
                                               EnumerationProgress enumerationProgress, EnumerationClient client) {

        enumerationProgress.expectHostSystemCount(hostSystemOverlays.size());
        for (HostSystemOverlay hs : hostSystemOverlays) {
            try {
                if (ObjectUpdateKind.ENTER.equals(hs.getObjectUpdateKind())) {
                    createNewHostSystem(service, enumerationProgress, hs, client);
                } else {
                    ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();
                    QueryTask task = queryForHostSystem(enumerationProgress, request.resourceLink(), hs.getId().getValue());
                    withTaskResults(service, task, result -> {
                        try {
                            if (!result.documentLinks.isEmpty()) {
                                try {
                                    ComputeState oldDocument = convertOnlyResultToDocument(result, ComputeState.class);
                                    if (ObjectUpdateKind.MODIFY.equals(hs.getObjectUpdateKind())) {
                                        updateHostSystem(service, oldDocument, enumerationProgress, hs, false, client);
                                    } else {
                                        deleteHostSystem(service, enumerationProgress, hs, oldDocument);
                                    }
                                } catch (Exception ex) {
                                    service.logSevere("Error occurred while processing host system: %s", Utils.toString(ex));
                                    enumerationProgress.getHostSystemTracker().track();
                                }
                            } else {
                                enumerationProgress.getHostSystemTracker().track();
                            }
                        } catch (Exception e) {
                            service.logSevere("Error occurred while processing host system: %s", Utils.toString(e));
                            enumerationProgress.getHostSystemTracker().track();
                        }
                    });
                }
            } catch (Exception ex) {
                service.logSevere("Error occurred while processing host system: %s", Utils.toString(ex));
                enumerationProgress.getHostSystemTracker().track();
            }
        }
    }

    private static void deleteHostSystem(VSphereIncrementalEnumerationService service, EnumerationProgress enumerationProgress,
                                         HostSystemOverlay hostSystemOverlay, ComputeState oldDocument) {
        Operation.createDelete(
                PhotonModelUriUtils.createInventoryUri(service.getHost(), oldDocument.documentSelfLink))
                .setCompletion((o, e) -> {
                    trackHostSystem(enumerationProgress, hostSystemOverlay).handle(o, e);
                }).sendWith(service);
    }
}
