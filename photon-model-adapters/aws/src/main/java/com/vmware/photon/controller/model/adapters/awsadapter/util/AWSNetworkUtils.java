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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_TAG_NAME;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.getTagValue;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;

import io.netty.util.internal.StringUtil;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
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

    // Resource Limit Constants
    public static final String PROPERTY_NAME_QUERY_RESULT_LIMIT = UriPaths.PROPERTY_PREFIX
            + AWSNetworkUtils.class.getSimpleName() + ".QUERY_RESULT_LIMIT";
    private static int AWS_NETWORK_QUERY_RESULT_LIMIT = Integer.getInteger(
            PROPERTY_NAME_QUERY_RESULT_LIMIT,
            1000);

    public static NetworkState mapVPCToNetworkState(Vpc vpc, String regionId,
            String resourcePoolLink, String endpointLink, String authCredentialsLink,
            String parentComputeLink, List<String> tenantLinks, URI adapterUri) {
        if (vpc == null) {
            throw new IllegalArgumentException("Cannot map VPC to network state for null instance");
        }

        NetworkState networkState = new NetworkState();
        networkState.id = vpc.getVpcId();

        // calculate vpc name
        if (vpc.getTags() == null) {
            networkState.name = vpc.getVpcId();
        } else {
            networkState.name = vpc.getTags().stream()
                    .filter(tag -> tag.getKey().equals(AWS_TAG_NAME))
                    .map(tag -> tag.getValue()).findFirst()
                    .orElse(vpc.getVpcId());
        }

        networkState.subnetCIDR = vpc.getCidrBlock();
        networkState.regionId = regionId;
        networkState.resourcePoolLink = resourcePoolLink;
        networkState.endpointLink = endpointLink;
        if (networkState.endpointLinks == null) {
            networkState.endpointLinks = new HashSet<>();
        }
        networkState.endpointLinks.add(endpointLink);
        networkState.authCredentialsLink = authCredentialsLink;
        networkState.instanceAdapterReference = adapterUri;
        networkState.tenantLinks = tenantLinks;
        networkState.computeHostLink = parentComputeLink;
        networkState.customProperties = new HashMap<>();
        networkState.customProperties.put("defaultInstance", String.valueOf(vpc.isDefault()));
        return networkState;
    }

    /**
     * NOTE: Keep in mind that subnetState.networkLink is not set and it should be updated once
     * valid NetworkState.documentSelfLink is available.
     */
    public static SubnetState mapSubnetToSubnetState(Subnet subnet, List<String> tenantLinks,
            String regionId, String parentComputeLink, String endpointLink) {
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
        subnetState.zoneId = subnet.getAvailabilityZone();
        subnetState.tenantLinks = tenantLinks;
        subnetState.endpointLink = endpointLink;
        if (subnetState.endpointLinks == null) {
            subnetState.endpointLinks = new HashSet<>();
        }
        subnetState.endpointLinks.add(endpointLink);
        subnetState.computeHostLink = parentComputeLink;
        subnetState.customProperties = new HashMap<>();
        subnetState.regionId = regionId;

        if (!subnet.getTags().isEmpty()) {

            // The name of the subnet state is the value of the AWS_TAG_NAME tag
            String nameTag = getTagValue(subnet.getTags(), AWS_TAG_NAME);
            if (!StringUtil.isNullOrEmpty(nameTag)) {
                subnetState.name = nameTag;
            }
        }
        return subnetState;
    }

    /**
     * Creates the query to retrieve existing network states filtered by the discovered VPCs.
     */
    public static QueryTask createQueryToGetExistingNetworkStatesFilteredByDiscoveredVPCs(
            Set<String> vpcIds, String computeHostLink, String endpointLink, String regionId, List<String> tenantLinks) {

        return createQueryToGetExistingStatesFilteredByDiscoveredIds(
                NetworkService.NetworkState.class, vpcIds, computeHostLink, endpointLink, regionId, tenantLinks);
    }

    /**
     * Creates the query to retrieve existing subnet states filtered by the discovered Subnets.
     */
    public static QueryTask createQueryToGetExistingSubnetStatesFilteredByDiscoveredSubnets(
            Set<String> subnetIds, String computeHostLink, String endpointLink, String regionId, List<String> tenantLinks) {

        return createQueryToGetExistingStatesFilteredByDiscoveredIds(
                SubnetService.SubnetState.class, subnetIds, computeHostLink, endpointLink, regionId, tenantLinks);
    }

    private static QueryTask createQueryToGetExistingStatesFilteredByDiscoveredIds(
            Class<? extends ResourceState> stateClass,
            Set<String> stateIds,
            String computeHostLink,
            String endpointLink,
            String regionId,
            List<String> tenantLinks) {

        Query.Builder queryBuilder = Query.Builder.create()
                .addKindFieldClause(stateClass)
                .addInClause(ResourceState.FIELD_NAME_ID, stateIds);
        queryBuilder.addFieldClause(ResourceState.FIELD_NAME_COMPUTE_HOST_LINK, computeHostLink);

        if (regionId != null) {
            queryBuilder.addFieldClause(ResourceState.FIELD_NAME_REGION_ID, regionId);
        }

        if (tenantLinks != null && !tenantLinks.isEmpty()) {
            queryBuilder.addInCollectionItemClause(ResourceState.FIELD_NAME_TENANT_LINKS, tenantLinks);
        }

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.INDEXED_METADATA)
                .addOption(QueryOption.TOP_RESULTS)
                .setQuery(queryBuilder.build())
                .setResultLimit(AWS_NETWORK_QUERY_RESULT_LIMIT)
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
        collectionsMap.put(ComputeState.FIELD_NAME_NETWORK_INTERFACE_LINKS, networkLinksToBeRemoved);
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
