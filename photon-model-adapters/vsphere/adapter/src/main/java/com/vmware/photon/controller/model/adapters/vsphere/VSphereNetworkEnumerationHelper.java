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

import java.net.URI;

import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.vsphere.network.DvsProperties;
import com.vmware.photon.controller.model.adapters.vsphere.network.NsxProperties;
import com.vmware.photon.controller.model.adapters.vsphere.util.MoRefKeyedMap;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.util.PhotonModelUriUtils;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.ObjectUpdateKind;
import com.vmware.vim25.OpaqueNetworkSummary;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;

public class VSphereNetworkEnumerationHelper {

    private static CompletionHandler trackNetwork(EnumerationProgress enumerationProgress,
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

    private static NetworkState makeNetworkStateFromResults(
            VSphereIncrementalEnumerationService service, EnumerationProgress enumerationProgress,
            NetworkOverlay net, EndpointState endpointState) {
        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();
        ComputeStateWithDescription parent = enumerationProgress.getParent();

        NetworkState state = new NetworkState();

        state.documentSelfLink = NetworkService.FACTORY_LINK + "/" +
                service.getHost().nextUUID();
        state.id = state.name = net.getName();
        state.endpointLink = enumerationProgress.getRequest().endpointLink;
        AdapterUtils.addToEndpointLinks(state, enumerationProgress.getRequest().endpointLink);
        state.regionId = enumerationProgress.getRegionId();
        state.resourcePoolLink = request.resourcePoolLink;
        state.adapterManagementReference = request.adapterManagementReference;
        state.authCredentialsLink = endpointState.authCredentialsLink;

        URI ref = parent.description.instanceAdapterReference;
        state.instanceAdapterReference = AdapterUriUtil.buildAdapterUri(ref.getPort(),
                VSphereUriPaths.DVS_NETWORK_SERVICE);

        CustomProperties custProp = CustomProperties.of(state)
                .put(CustomProperties.MOREF, net.getId())
                .put(CustomProperties.DATACENTER_SELF_LINK, enumerationProgress.getDcLink())
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
        VsphereEnumerationHelper.populateResourceStateWithAdditionalProps(state, enumerationProgress.getVcUuid());
        return state;
    }

    private static QueryTask queryForNetwork(EnumerationProgress ctx, NetworkOverlay net) {
        URI adapterManagementReference = ctx.getRequest().adapterManagementReference;
        String regionId = ctx.getRegionId();

        Builder builder = Builder.create()
                .addFieldClause(NetworkState.FIELD_NAME_ADAPTER_MANAGEMENT_REFERENCE,
                        adapterManagementReference.toString())
                .addKindFieldClause(NetworkState.class)
                .addFieldClause(NetworkState.FIELD_NAME_REGION_ID, regionId)
                .addCompositeFieldClause(NetworkState.FIELD_NAME_CUSTOM_PROPERTIES, CustomProperties.MOREF,
                        VimUtils.convertMoRefToString(net.getId()), Occurance.MUST_OCCUR);

        QueryUtils.addEndpointLink(builder, NetworkState.class, ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());

        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .setResultLimit(1)
                .build();
    }

    private static SubnetState createSubnetStateForNetwork(
            NetworkState networkState, EnumerationProgress enumerationProgress, NetworkOverlay net) {

        if (!VimNames.TYPE_NETWORK.equals(net.getId().getType()) &&
                !VimNames.TYPE_OPAQUE_NETWORK.equals(net.getId().getType())) {
            return null;
        }

        SubnetState subnet = new SubnetState();
        subnet.documentSelfLink = UriUtils.buildUriPath(SubnetService.FACTORY_LINK,
                UriUtils.getLastPathSegment(networkState.documentSelfLink));
        subnet.id = subnet.name = net.getName();
        subnet.endpointLink = enumerationProgress.getRequest().endpointLink;
        AdapterUtils.addToEndpointLinks(subnet, enumerationProgress.getRequest().endpointLink);
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

    private static void updateNetwork(VSphereIncrementalEnumerationService service, NetworkState oldDocument,
                                      EnumerationProgress enumerationProgress, NetworkOverlay net) {
        VsphereEnumerationHelper.getEndpoint(service.getHost(), enumerationProgress.getRequest().endpointLinkReference)
                .thenAccept(endpointState -> {
                    NetworkState networkState = makeNetworkStateFromResults(service,
                            enumerationProgress, net, endpointState);
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
                                subnet.documentSelfLink))
                                .setBody(subnet)
                                .sendWith(service);
                    }
                });

    }

    private static ResourceGroupState makeNetworkGroup(NetworkOverlay net, EnumerationProgress ctx) {
        ResourceGroupState res = new ResourceGroupState();
        res.id = net.getName();
        res.name = "Hosts connected to network '" + net.getName() + "'";
        res.endpointLink = ctx.getRequest().endpointLink;
        AdapterUtils.addToEndpointLinks(res, ctx.getRequest().endpointLink);
        res.tenantLinks = ctx.getTenantLinks();
        CustomProperties.of(res)
                .put(CustomProperties.MOREF, net.getId())
                .put(CustomProperties.TARGET_LINK, ctx.getNetworkTracker().getSelfLink(net.getId()));
        res.documentSelfLink = VsphereEnumerationHelper.computeGroupStableLink(net.getId(),
                VSphereIncrementalEnumerationService.PREFIX_NETWORK, ctx.getRequest().endpointLink);

        return res;
    }

    private static QueryTask queryForSubnet(
            EnumerationProgress ctx, NetworkOverlay portgroup, ManagedObjectReference parent) {
        String moref = VimUtils.convertMoRefToString(portgroup.getId());

        Builder builder = Builder.create()
                .addKindFieldClause(SubnetState.class)
                .addCompositeFieldClause(SubnetState.FIELD_NAME_CUSTOM_PROPERTIES,
                        CustomProperties.MOREF, moref);
        if (parent != null) {
            String dvsLink = buildStableDvsLink(parent, ctx.getRequest().endpointLink);
            builder.addFieldClause(SubnetState.FIELD_NAME_NETWORK_LINK, dvsLink);
        }

        QueryUtils.addEndpointLink(builder, SubnetState.class, ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());

        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();
    }

    private static SubnetState makeSubnetStateFromResults(EnumerationProgress enumerationProgress,
                                                          NetworkOverlay net) {
        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();

        SubnetState state = new SubnetState();

        state.id = state.name = net.getName();
        state.endpointLink = enumerationProgress.getRequest().endpointLink;
        AdapterUtils.addToEndpointLinks(state, enumerationProgress.getRequest().endpointLink);
        ManagedObjectReference parentSwitch = net.getParentSwitch();
        state.networkLink = buildStableDvsLink(parentSwitch, request.endpointLink);

        state.regionId = enumerationProgress.getRegionId();

        CustomProperties customProperties = CustomProperties.of(state)
                .put(CustomProperties.MOREF, net.getId())
                .put(CustomProperties.DATACENTER_SELF_LINK, enumerationProgress.getDcLink())
                .put(CustomProperties.TYPE, net.getId().getType());

        customProperties.put(DvsProperties.PORT_GROUP_KEY, net.getPortgroupKey());
        return state;
    }

    private static SubnetState makeSubnetStateFromChanges(NetworkOverlay networkOverlay) {
        SubnetState state = new SubnetState();
        // if name is changed
        String name = networkOverlay.getNameOrNull();
        if (null != name) {
            state.name = name;
            state.id = name;
        }

        return state;
    }

    private static void createNewNetwork(VSphereIncrementalEnumerationService service,
                                         EnumerationProgress enumerationProgress, NetworkOverlay net) {
        VsphereEnumerationHelper.getEndpoint(service.getHost(), enumerationProgress.getRequest().endpointLinkReference)
                .thenAccept(endpointState -> {
                    NetworkState networkState = makeNetworkStateFromResults(
                            service, enumerationProgress, net, endpointState);
                    networkState.tenantLinks = enumerationProgress.getTenantLinks();
                    Operation.createPost(
                            PhotonModelUriUtils.createInventoryUri(service.getHost(),
                                    NetworkService.FACTORY_LINK))
                            .setBody(networkState)
                            .setCompletion((o, e) -> {
                                trackNetwork(enumerationProgress, net).handle(o, e);
                                Operation.createPost(PhotonModelUriUtils.createInventoryUri(
                                        service.getHost(),
                                        ResourceGroupService.FACTORY_LINK))
                                        .setBody(makeNetworkGroup(net, enumerationProgress))
                                        .sendWith(service);
                            })
                            .sendWith(service);

                    service.logFine(() -> String.format("Found new Network %s", net.getName()));

                    SubnetState subnet = createSubnetStateForNetwork(networkState, enumerationProgress, net);

                    if (subnet != null) {
                        Operation.createPost(
                                PhotonModelUriUtils.createInventoryUri(service.getHost(),
                                        SubnetService.FACTORY_LINK))
                                .setBody(subnet)
                                .sendWith(service);
                    }
                });
    }

    private static void createNewSubnet(VSphereIncrementalEnumerationService service,
                                        EnumerationProgress enumerationProgress, NetworkOverlay net,
                                        String dvsUuid) {
        VsphereEnumerationHelper.getEndpoint(service.getHost(), enumerationProgress.getRequest().endpointLinkReference)
                .thenAccept(endpointState -> {
                    SubnetState state = makeSubnetStateFromResults(enumerationProgress, net);
                    state.customProperties.put(DvsProperties.DVS_UUID, dvsUuid);

                    state.tenantLinks = enumerationProgress.getTenantLinks();
                    Operation.createPost(PhotonModelUriUtils.createInventoryUri(
                            service.getHost(), SubnetService.FACTORY_LINK))
                            .setBody(state)
                            .setCompletion(trackNetwork(enumerationProgress, net))
                            .sendWith(service);

                    service.logFine(() -> String.format("Found new Subnet(Portgroup) %s",
                                net.getName()));
                });
    }

    private static void updateSubnet(VSphereIncrementalEnumerationService service, SubnetState oldDocument,
                                     EnumerationProgress enumerationProgress, NetworkOverlay net, boolean fullUpdate) {
        SubnetState state;
        if (fullUpdate) {
            state = makeSubnetStateFromResults(enumerationProgress, net);
        } else {
            state = makeSubnetStateFromChanges(net);
        }
        state.documentSelfLink = oldDocument.documentSelfLink;
        if (oldDocument.tenantLinks == null) {
            state.tenantLinks = enumerationProgress.getTenantLinks();
        }

        service.logFine(() -> String.format("Syncing Subnet(Portgroup) %s",
                net.getName()));
        Operation.createPatch(PhotonModelUriUtils.createInventoryUri(
                service.getHost(), oldDocument.documentSelfLink))
                .setBody(state)
                .setCompletion(trackNetwork(enumerationProgress, net))
                .sendWith(service);
    }

    public static void processFoundNetwork(VSphereIncrementalEnumerationService service,
                                           EnumerationProgress enumerationProgress, NetworkOverlay net,
                                           MoRefKeyedMap<NetworkOverlay> allNetworks) {
        if (net.getParentSwitch() != null) {
            // portgroup: create subnet
            QueryTask task = queryForSubnet(enumerationProgress, net, net.getParentSwitch());
            withTaskResults(service, task,
                    enumerationProgress.getNetworkTracker(), (ServiceDocumentQueryResult result) -> {
                    try {
                        if (result.documentLinks.isEmpty()) {
                            createNewSubnet(service, enumerationProgress, net,
                                    allNetworks.get(net.getParentSwitch()).getDvsUuid());
                        } else {
                            SubnetState oldDocument = convertOnlyResultToDocument(
                                    result, SubnetState.class);
                            updateSubnet(service, oldDocument, enumerationProgress, net, true);
                        }
                    } catch (Exception e) {
                        service.logSevere("Error while processing sub-network: %s", Utils.toString(e));
                        enumerationProgress.getNetworkTracker().track(net.getId(), ResourceTracker.ERROR);
                    }
                });
        } else {
            // DVS or opaque network
            QueryTask task = queryForNetwork(enumerationProgress, net);
            withTaskResults(service, task, enumerationProgress.getNetworkTracker(), result -> {
                try {
                    if (result.documentLinks.isEmpty()) {
                        createNewNetwork(service, enumerationProgress, net);
                    } else {
                        NetworkState oldDocument = convertOnlyResultToDocument(
                                result, NetworkState.class);
                        updateNetwork(service, oldDocument, enumerationProgress, net);
                    }
                } catch (Exception e) {
                    service.logSevere("Error while processing network: %s", Utils.toString(e));
                    enumerationProgress.getNetworkTracker().track(net.getId(), ResourceTracker.ERROR);
                }
            });
        }
    }

    /**
     * Handles incremental changes on networks.
     *
     * @param service             the incremental service
     * @param networks            the filtered network overlays
     * @param enumerationProgress the context for enumeration.
     * @param client              the enumeration client
     */
    public static void handleNetworkChanges(
            VSphereIncrementalEnumerationService service, MoRefKeyedMap<NetworkOverlay> networks,
            EnumerationProgress enumerationProgress, EnumerationClient client)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {

        enumerationProgress.expectNetworkCount(networks.values().size());
        for (NetworkOverlay netOverlay : networks.values()) {

            // if a network | DVS | DV port group is added.
            if (ObjectUpdateKind.ENTER == netOverlay.getObjectUpdateKind()) {
                // if DV port group is added
                if (netOverlay.getParentSwitch() != null) {
                    // check if the parent switch is present as part of this enumeration
                    NetworkOverlay parentSwitch = networks.get(netOverlay.getParentSwitch());
                    String dvsUUID;
                    if (parentSwitch == null) {
                        // retrieve the uuid from vCenter
                        dvsUUID = client.getUUIDForDVS(netOverlay);
                    } else {
                        dvsUUID = parentSwitch.getDvsUuid();
                    }
                    createNewSubnet(service, enumerationProgress, netOverlay, dvsUUID);
                } else {
                    createNewNetwork(service, enumerationProgress, netOverlay);
                }
            } else {
                // if DV port group is changed
                if (netOverlay.getId().getType().equals(VimNames.TYPE_PORTGROUP)) {

                    ManagedObjectReference parentSwitch = netOverlay.getParentSwitch();
                    // if parent is not retrieved, Retrieve it.
                    if (null == parentSwitch && ObjectUpdateKind.MODIFY.equals(netOverlay.getObjectUpdateKind())) {
                        parentSwitch = client.getParentSwitchForDVPortGroup(netOverlay.getId());
                    }
                    QueryTask task = queryForSubnet(enumerationProgress, netOverlay, parentSwitch);
                    withTaskResults(service, task, enumerationProgress.getNetworkTracker(), (ServiceDocumentQueryResult result) -> {
                        try {
                            if (!result.documentLinks.isEmpty()) {
                                SubnetState oldDocument = convertOnlyResultToDocument(result, SubnetState.class);
                                if (ObjectUpdateKind.MODIFY.equals(netOverlay.getObjectUpdateKind())) {
                                    updateSubnet(service, oldDocument, enumerationProgress, netOverlay, false);
                                } else {
                                    // DV port group has been removed. Remove the subnet state document.
                                    deleteNetwork(service, enumerationProgress, netOverlay, oldDocument);
                                }
                            } else {
                                enumerationProgress.getNetworkTracker().track();
                            }
                        } catch (Exception e) {
                            service.logSevere("Error occurred while processing incremental sub-network changes: %s", Utils.toString(e));
                            enumerationProgress.getNetworkTracker().track();
                        }
                    });
                } else {
                    // DVS or Opaque network.
                    QueryTask task = queryForNetwork(enumerationProgress, netOverlay);
                    VsphereEnumerationHelper.withTaskResults(service, task, enumerationProgress.getNetworkTracker(), result -> {
                        try {
                            if (!result.documentLinks.isEmpty()) {
                                NetworkState oldDocument = convertOnlyResultToDocument(result, NetworkState.class);
                                if (ObjectUpdateKind.MODIFY.equals(netOverlay.getObjectUpdateKind())) {
                                    updateNetwork(service, oldDocument, enumerationProgress, netOverlay);
                                } else {
                                    deleteNetwork(service, enumerationProgress, netOverlay, oldDocument);
                                }
                            } else {
                                enumerationProgress.getNetworkTracker().track();
                            }
                        } catch (Exception e) {
                            service.logSevere("Error occurred while processing incremental network changes: %s", Utils.toString(e));
                            enumerationProgress.getNetworkTracker().track();
                        }
                    });
                }
            }
        }

        try {
            enumerationProgress.getNetworkTracker().await();
        } catch (InterruptedException e) {
            service.logSevere("Interrupted during incremental enumeration for networks: %s", Utils.toString(e));
        }
    }

    private static void deleteNetwork(
            VSphereIncrementalEnumerationService service, EnumerationProgress enumerationProgress,
            NetworkOverlay netOverlay, ServiceDocument oldDocument) {
        Operation.createDelete(
                PhotonModelUriUtils.createInventoryUri(service.getHost(), oldDocument.documentSelfLink))
                .setCompletion((o, e) -> {
                    trackNetwork(enumerationProgress, netOverlay).handle(o, e);
                }).sendWith(service);
    }
}
