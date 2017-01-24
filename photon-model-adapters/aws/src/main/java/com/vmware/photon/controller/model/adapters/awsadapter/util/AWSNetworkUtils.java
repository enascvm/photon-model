/*
 * Copyright (c) 2015-2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.adapters.awsadapter.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;

import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

/**
 * Utility class to hold methods used across different enumeration classes for creating network
 * states and NICs etc.
 */
public class AWSNetworkUtils {

    public static NetworkState mapVPCToNetworkState(Vpc vpc, String regionId,
            String resourcePoolLink, String endpointLink, String authCredentialsLink,
            List<String> tenantLinks, URI adapterUri) {
        if (vpc == null) {
            throw new IllegalArgumentException("Cannot map VPC to network state for null instance");
        }
        NetworkState networkState = new NetworkState();
        networkState.id = vpc.getVpcId();
        networkState.name = vpc.getVpcId();
        networkState.subnetCIDR = vpc.getCidrBlock();
        networkState.regionId = regionId;
        networkState.resourcePoolLink = resourcePoolLink;
        networkState.endpointLink = endpointLink;
        networkState.authCredentialsLink = authCredentialsLink;
        networkState.instanceAdapterReference = adapterUri;
        networkState.tenantLinks = tenantLinks;
        networkState.customProperties = new HashMap<>();
        networkState.customProperties.put("defaultInstance", String.valueOf(vpc.isDefault()));

        return networkState;
    }

    /**
     * NOTE: Keep in mind that subnetState.networkLink is not set and it should be updated once
     * valid NetworkState.documentSelfLink is available.
     */
    public static SubnetState mapSubnetToSubnetState(Subnet subnet, List<String> tenantLinks,
            String endpointLink) {
        if (subnet == null) {
            throw new IllegalArgumentException(
                    "Cannot map Subnet to subnet state for null instance");
        }
        SubnetState subnetState = new SubnetState();
        subnetState.id = subnet.getSubnetId();
        subnetState.name = subnet.getSubnetId();
        subnetState.subnetCIDR = subnet.getCidrBlock();
        subnetState.supportPublicIpAddress = subnet.isMapPublicIpOnLaunch();
        subnetState.defaultForZone = subnet.isDefaultForAz();
        subnetState.tenantLinks = tenantLinks;
        subnetState.endpointLink = endpointLink;

        return subnetState;
    }

    /**
     * Returns the NetworkInterfaceState object, which corresponds to a particular
     * deviceIndex. In case that there is only one Nic for this ComputeState, and the deviceIndex is 0,
     * even if the Nic State Description was not mapped to an index, the algorithm returns this only state.
     */
    public static NetworkInterfaceState getNICStateByDeviceId(
            List<NetworkInterfaceState> nicStates,
            int deviceIndex) {
        return nicStates
                .stream()
                .filter(nicState -> nicState != null)
                .filter(nicState -> nicState.deviceIndex == deviceIndex)
                .findFirst()
                .orElse(null);
    }

    /**
     * Creates the query to retrieve existing network states filtered by the discovered VPCs.
     */
    public static QueryTask createQueryToGetExistingNetworkStatesFilteredByDiscoveredVPCs(
            Set<String> vpcIds, List<String> tenantLinks) {

        return createQueryToGetExistingStatesFilteredByDiscoveredIds(
                NetworkService.NetworkState.class, vpcIds, tenantLinks);
    }

    /**
     * Creates the query to retrieve existing subnet states filtered by the discovered Subnets.
     */
    public static QueryTask createQueryToGetExistingSubnetStatesFilteredByDiscoveredSubnets(
            Set<String> subnetIds, List<String> tenantLinks) {

        return createQueryToGetExistingStatesFilteredByDiscoveredIds(
                SubnetService.SubnetState.class, subnetIds, tenantLinks);
    }

    private static QueryTask createQueryToGetExistingStatesFilteredByDiscoveredIds(
            Class<? extends ResourceState> stateClass,
            Set<String> stateIds,
            List<String> tenantLinks) {

        Query query = Query.Builder.create()
                .addKindFieldClause(stateClass)
                .addInClause(ResourceState.FIELD_NAME_ID, stateIds)
                .build();

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.TOP_RESULTS)
                .setQuery(query)
                .setResultLimit(stateIds.size())
                .build();
        queryTask.tenantLinks = tenantLinks;

        return queryTask;
    }

    /**
     * Remove a compute state's networkLink and delete the link's corresponding document
     *
     * @param service
     *            Service to issue the patch to.
     * @param computeState
     *            The compute state to be updated.
     * @param networkLink
     *            The network link need to be removed.
     * @param enumerationOperations
     *            The operation list to store the operations.
     * @return
     */
    public static void removeNetworkLinkAndDocument(StatelessService service,
            ComputeState computeState,
            String networkLink, List<Operation> enumerationOperations) {
        // create a PATCH to remove one ComputeState's networkLink
        Map<String, Collection<Object>> collectionsMap = new HashMap<>();
        Collection<Object> networkLinksToBeRemoved = new ArrayList<>(Arrays.asList(networkLink));
        collectionsMap.put(ComputeState.FIELD_NAME_NETWORK_LINKS, networkLinksToBeRemoved);
        ServiceStateCollectionUpdateRequest collectionRemovalBody = ServiceStateCollectionUpdateRequest
                .create(null, collectionsMap);

        Operation removeNetworkLinkOperation = Operation
                .createPatch(UriUtils.buildUri(service.getHost(), computeState.documentSelfLink))
                .setBody(collectionRemovalBody)
                .setReferer(service.getUri());

        enumerationOperations.add(removeNetworkLinkOperation);

        // create a DELETE to remove that networkLink's corresponding document
        Operation removeNetworkLinkDocumentOperation = Operation
                .createDelete(UriUtils.buildUri(service.getHost(), networkLink))
                .setReferer(service.getUri());

        enumerationOperations.add(removeNetworkLinkDocumentOperation);
    }

}
