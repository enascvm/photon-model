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

import java.net.URI;
import java.util.HashSet;

import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.vsphere.network.DvsProperties;
import com.vmware.photon.controller.model.adapters.vsphere.network.NsxProperties;
import com.vmware.photon.controller.model.adapters.vsphere.util.MoRefKeyedMap;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.util.PhotonModelUriUtils;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.OpaqueNetworkSummary;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;

public class VSphereNetworkEnumerationHelper {

    static CompletionHandler trackNetwork(EnumerationProgress enumerationProgress,
                                          NetworkOverlay net) {
        return (o, e) -> {
            enumerationProgress.touchResource(VsphereEnumerationHelper.getSelfLinkFromOperation(o));
            if (e == null) {
                enumerationProgress.getNetworkTracker().track(net.getId(),
                        VsphereEnumerationHelper.getSelfLinkFromOperation(o));
            } else {
                enumerationProgress.getNetworkTracker().track(net.getId(), ResourceTracker.ERROR);
            }
        };
    }

    public static String buildStableDvsLink(ManagedObjectReference ref, String endpointLink) {
        return NetworkService.FACTORY_LINK + "/"
                + VimUtils.buildStableManagedObjectId(ref, endpointLink);
    }

    static NetworkState makeNetworkStateFromResults(VSphereIncrementalEnumerationService service,
                                                    EnumerationProgress enumerationProgress, NetworkOverlay net) {
        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();
        ComputeStateWithDescription parent = enumerationProgress.getParent();

        NetworkState state = new NetworkState();

        state.documentSelfLink = NetworkService.FACTORY_LINK + "/" +
                service.getHost().nextUUID();
        state.id = state.name = net.getName();
        state.endpointLink = enumerationProgress.getRequest().endpointLink;
        if (state.endpointLink != null) {
            state.endpointLinks = new HashSet<>();
            state.endpointLinks.add(state.endpointLink);
        }
        state.regionId = enumerationProgress.getRegionId();
        state.resourcePoolLink = request.resourcePoolLink;
        state.adapterManagementReference = request.adapterManagementReference;
        state.authCredentialsLink = parent.description.authCredentialsLink;

        URI ref = parent.description.instanceAdapterReference;
        state.instanceAdapterReference = AdapterUriUtil.buildAdapterUri(ref.getPort(),
                VSphereUriPaths.DVS_NETWORK_SERVICE);

        CustomProperties custProp = CustomProperties.of(state)
                .put(CustomProperties.MOREF, net.getId())
                .put(CustomProperties.TYPE, net.getId().getType());

        if (net.getSummary() instanceof OpaqueNetworkSummary) {
            OpaqueNetworkSummary ons = (OpaqueNetworkSummary) net.getSummary();
            custProp.put(NsxProperties.OPAQUE_NET_ID, ons.getOpaqueNetworkId());
            custProp.put(NsxProperties.OPAQUE_NET_TYPE, ons.getOpaqueNetworkType());
        }

        if (net.getId().getType().equals(VimNames.TYPE_DVS)) {
            // dvs'es have a stable link
            state.documentSelfLink = buildStableDvsLink(net.getId(), request.endpointLink);

            custProp.put(DvsProperties.DVS_UUID, net.getDvsUuid());
        }

        return state;
    }

    static QueryTask queryForNetwork(EnumerationProgress ctx, NetworkOverlay net) {
        URI adapterManagementReference = ctx.getRequest().adapterManagementReference;
        String regionId = ctx.getRegionId();

        Builder builder = Builder.create()
                .addFieldClause(NetworkState.FIELD_NAME_ADAPTER_MANAGEMENT_REFERENCE,
                        adapterManagementReference.toString())
                .addKindFieldClause(NetworkState.class)
                .addFieldClause(NetworkState.FIELD_NAME_REGION_ID, regionId)
                .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES, CustomProperties.MOREF,
                        VimUtils.convertMoRefToString(net.getId()), Occurance.MUST_OCCUR);

        QueryUtils.addEndpointLink(builder, NetworkState.class, ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());

        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .setResultLimit(1)
                .build();
    }

    static SubnetState createSubnetStateForNetwork(NetworkState networkState,
                                                   EnumerationProgress enumerationProgress, NetworkOverlay net) {

        if (!VimNames.TYPE_NETWORK.equals(net.getId().getType()) &&
                !VimNames.TYPE_OPAQUE_NETWORK.equals(net.getId().getType())) {
            return null;
        }

        SubnetState subnet = new SubnetState();
        subnet.documentSelfLink = UriUtils.buildUriPath(SubnetService.FACTORY_LINK,
                UriUtils.getLastPathSegment(networkState.documentSelfLink));
        subnet.id = subnet.name = net.getName();
        subnet.endpointLink = enumerationProgress.getRequest().endpointLink;
        subnet.networkLink = networkState.documentSelfLink;
        subnet.tenantLinks = enumerationProgress.getTenantLinks();
        subnet.regionId = networkState.regionId;
        enumerationProgress.touchResource(subnet.documentSelfLink);

        CustomProperties.of(subnet)
                .put(CustomProperties.MOREF, net.getId())
                .put(CustomProperties.TYPE, net.getId().getType());

        // Add NSX-T related properties to the subnet
        if (VimNames.TYPE_OPAQUE_NETWORK.equals(net.getId().getType())) {
            OpaqueNetworkSummary opaqueNetworkSummary = (OpaqueNetworkSummary) net.getSummary();
            CustomProperties.of(subnet)
                    .put(NsxProperties.OPAQUE_NET_ID, opaqueNetworkSummary.getOpaqueNetworkId())
                    .put(NsxProperties.OPAQUE_NET_TYPE,
                            opaqueNetworkSummary.getOpaqueNetworkType());
        }
        return subnet;
    }

    static void updateNetwork(VSphereIncrementalEnumerationService service,
                              NetworkState oldDocument, EnumerationProgress enumerationProgress, NetworkOverlay net) {
        NetworkState networkState = makeNetworkStateFromResults(
                service, enumerationProgress, net);
        //restore original selfLink
        networkState.documentSelfLink = oldDocument.documentSelfLink;
        networkState.resourcePoolLink = null;

        if (oldDocument.tenantLinks == null) {
            networkState.tenantLinks = enumerationProgress.getTenantLinks();
        }

        service.logFine(() -> String.format("Syncing Network %s", net.getName()));
        Operation.createPatch(
                PhotonModelUriUtils.createInventoryUri(service.getHost(), oldDocument.documentSelfLink))
                .setBody(networkState)
                .setCompletion(trackNetwork(enumerationProgress, net))
                .sendWith(service);

        // represent a Network also as a subnet
        SubnetState subnet = createSubnetStateForNetwork(networkState, enumerationProgress, net);

        if (subnet != null) {
            Operation.createPatch(PhotonModelUriUtils.createInventoryUri(service.getHost(),
                    SubnetService.FACTORY_LINK))
                    .setBody(subnet)
                    .sendWith(service);
        }
    }

    static ResourceGroupState makeNetworkGroup(NetworkOverlay net, EnumerationProgress ctx) {
        ResourceGroupState res = new ResourceGroupState();
        res.id = net.getName();
        res.name = "Hosts connected to network '" + net.getName() + "'";
        res.endpointLink = ctx.getRequest().endpointLink;
        res.tenantLinks = ctx.getTenantLinks();
        CustomProperties.of(res)
                .put(CustomProperties.MOREF, net.getId())
                .put(CustomProperties.TARGET_LINK, ctx.getNetworkTracker().getSelfLink(net.getId()));
        res.documentSelfLink = VsphereEnumerationHelper.computeGroupStableLink(net.getId(),
                VSphereIncrementalEnumerationService.PREFIX_NETWORK, ctx.getRequest().endpointLink);

        return res;
    }

    static QueryTask queryForSubnet(EnumerationProgress ctx, NetworkOverlay portgroup) {
        String dvsLink = buildStableDvsLink(portgroup.getParentSwitch(), ctx.getRequest().endpointLink);
        String moref = VimUtils.convertMoRefToString(portgroup.getId());

        Builder builder = Builder.create()
                .addKindFieldClause(SubnetState.class)
                .addFieldClause(SubnetState.FIELD_NAME_NETWORK_LINK, dvsLink)
                .addCompositeFieldClause(ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                        CustomProperties.MOREF, moref);
        QueryUtils.addEndpointLink(builder, NetworkState.class, ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());

        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();
    }

    static SubnetState makeSubnetStateFromResults(EnumerationProgress enumerationProgress, NetworkOverlay net) {
        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();

        SubnetState state = new SubnetState();

        state.id = state.name = net.getName();
        state.endpointLink = enumerationProgress.getRequest().endpointLink;
        if (state.endpointLink != null) {
            state.endpointLinks = new HashSet<>();
            state.endpointLinks.add(state.endpointLink);
        }

        ManagedObjectReference parentSwitch = net.getParentSwitch();
        state.networkLink = buildStableDvsLink(parentSwitch, request.endpointLink);

        state.regionId = enumerationProgress.getRegionId();

        CustomProperties custProp = CustomProperties.of(state)
                .put(CustomProperties.MOREF, net.getId())
                .put(CustomProperties.TYPE, net.getId().getType());

        custProp.put(DvsProperties.PORT_GROUP_KEY, net.getPortgroupKey());

        return state;
    }

    static void createNewNetwork(VSphereIncrementalEnumerationService vSphereIncrementalEnumerationService,
                                 EnumerationProgress enumerationProgress, NetworkOverlay net) {
        NetworkState networkState = makeNetworkStateFromResults(
                vSphereIncrementalEnumerationService, enumerationProgress, net);
        networkState.tenantLinks = enumerationProgress.getTenantLinks();
        Operation.createPost(
                PhotonModelUriUtils.createInventoryUri(vSphereIncrementalEnumerationService.getHost(),
                        NetworkService.FACTORY_LINK))
                .setBody(networkState)
                .setCompletion((o, e) -> {
                    trackNetwork(enumerationProgress, net).handle(o, e);
                    Operation.createPost(PhotonModelUriUtils.createInventoryUri(
                            vSphereIncrementalEnumerationService.getHost(),
                            ResourceGroupService.FACTORY_LINK))
                            .setBody(makeNetworkGroup(net, enumerationProgress))
                            .sendWith(vSphereIncrementalEnumerationService);
                })
                .sendWith(vSphereIncrementalEnumerationService);

        vSphereIncrementalEnumerationService.logFine(() -> String.format("Found new Network %s", net.getName()));

        SubnetState subnet = createSubnetStateForNetwork(networkState, enumerationProgress, net);

        if (subnet != null) {
            Operation.createPost(
                    PhotonModelUriUtils.createInventoryUri(vSphereIncrementalEnumerationService.getHost(),
                            SubnetService.FACTORY_LINK))
                    .setBody(subnet)
                    .sendWith(vSphereIncrementalEnumerationService);
        }
    }

    static void createNewSubnet(VSphereIncrementalEnumerationService vSphereIncrementalEnumerationService,
                                EnumerationProgress enumerationProgress, NetworkOverlay net,
                                NetworkOverlay parentSwitch) {
        SubnetState state = makeSubnetStateFromResults(enumerationProgress, net);
        state.customProperties.put(DvsProperties.DVS_UUID, parentSwitch.getDvsUuid());

        state.tenantLinks = enumerationProgress.getTenantLinks();
        Operation.createPost(PhotonModelUriUtils.createInventoryUri(
                vSphereIncrementalEnumerationService.getHost(), SubnetService.FACTORY_LINK))
                .setBody(state)
                .setCompletion(trackNetwork(enumerationProgress, net))
                .sendWith(vSphereIncrementalEnumerationService);

        vSphereIncrementalEnumerationService.logFine(() -> String.format("Found new Subnet(Portgroup) %s",
                net.getName()));
    }

    static void updateSubnet(VSphereIncrementalEnumerationService vSphereIncrementalEnumerationService,
                             SubnetState oldDocument, EnumerationProgress enumerationProgress, NetworkOverlay net) {
        SubnetState state = makeSubnetStateFromResults(enumerationProgress, net);
        state.documentSelfLink = oldDocument.documentSelfLink;
        if (oldDocument.tenantLinks == null) {
            state.tenantLinks = enumerationProgress.getTenantLinks();
        }

        vSphereIncrementalEnumerationService.logFine(() -> String.format("Syncing Subnet(Portgroup) %s",
                net.getName()));
        Operation.createPatch(PhotonModelUriUtils.createInventoryUri(
                vSphereIncrementalEnumerationService.getHost(), oldDocument.documentSelfLink))
                .setBody(state)
                .setCompletion(trackNetwork(enumerationProgress, net))
                .sendWith(vSphereIncrementalEnumerationService);
    }

    static void processFoundNetwork(VSphereIncrementalEnumerationService vSphereIncrementalEnumerationService,
                                    EnumerationProgress enumerationProgress, NetworkOverlay net,
                                    MoRefKeyedMap<NetworkOverlay> allNetworks) {
        if (net.getParentSwitch() != null) {
            // portgroup: create subnet
            QueryTask task = queryForSubnet(enumerationProgress, net);
            VsphereEnumerationHelper.withTaskResults(vSphereIncrementalEnumerationService, task,
                    (ServiceDocumentQueryResult result) -> {
                        if (result.documentLinks.isEmpty()) {
                            createNewSubnet(vSphereIncrementalEnumerationService, enumerationProgress, net,
                                    allNetworks.get(net.getParentSwitch()));
                        } else {
                            SubnetState oldDocument = VsphereEnumerationHelper.convertOnlyResultToDocument(
                                    result, SubnetState.class);
                            updateSubnet(vSphereIncrementalEnumerationService, oldDocument, enumerationProgress, net);
                        }
                    });
        } else {
            // DVS or opaque network
            QueryTask task = queryForNetwork(enumerationProgress, net);
            VsphereEnumerationHelper.withTaskResults(vSphereIncrementalEnumerationService, task, result -> {
                if (result.documentLinks.isEmpty()) {
                    createNewNetwork(vSphereIncrementalEnumerationService, enumerationProgress, net);
                } else {
                    NetworkState oldDocument = VsphereEnumerationHelper.convertOnlyResultToDocument(
                            result, NetworkState.class);
                    updateNetwork(vSphereIncrementalEnumerationService, oldDocument, enumerationProgress, net);
                }
            });
        }
    }
}
