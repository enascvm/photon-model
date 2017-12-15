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

import static com.vmware.photon.controller.model.ComputeProperties.CUSTOM_OS_TYPE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_TAG_NAME;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VPC_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.WINDOWS_PLATFORM;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.getQueryResultLimit;
import static com.vmware.photon.controller.model.adapters.util.TagsUtil.newTagState;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.SOURCE_TASK_LINK;
import static com.vmware.xenon.common.UriUtils.URI_PATH_CHAR;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Tag;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.ComputeProperties.OSType;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.TagFactoryService;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

/**
 * Utility class to hold methods used across different enumeration classes.
 */
public class AWSEnumerationUtils {

    private static final String EMPTY_STRING = "";

    /**
     * Gets the key to uniquely represent a compute description that needs to be created in the system.
     * Currently uses regionId and instanceType and is represented as below:
     * us-east-1~t2.micro
     *
     * TODO harden key as more logic is realized in the enumeration
     * service.
     */
    public static InstanceDescKey getKeyForComputeDescriptionFromInstance(Instance i,
            Map<String, ZoneData> zones) {

        String zoneId = i.getPlacement().getAvailabilityZone();
        String regionId = zones.get(zoneId).regionId;

        return InstanceDescKey.build(regionId, zoneId, i.getInstanceType());
    }

    /**
     * Returns the regionId from the compute description key that looks like  regionId~instanceType
     */
    public static InstanceDescKey getKeyForComputeDescriptionFromCD(ComputeDescription cd) {
        // Representing the compute-description as a key regionId~zone~instanceType
        return InstanceDescKey.build(cd.regionId, cd.zoneId, cd.instanceType);
    }

    /**
     * From the list of instances that are received from AWS arrive at the minimal set of compute descriptions that need
     * to be created locally to represent them.The compute descriptions are represented as regionId~instanceType
     * and put into a hashset. As a result, a representative set is created to represent all the discovered VMs.
     * @param instanceList
     */
    public static Set<InstanceDescKey> getRepresentativeListOfCDsFromInstanceList(
            Collection<Instance> instanceList, Map<String, ZoneData> zones) {
        HashSet<InstanceDescKey> representativeCDSet = new HashSet<>();
        for (Instance instance : instanceList) {
            representativeCDSet.add(getKeyForComputeDescriptionFromInstance(instance, zones));
        }
        return representativeCDSet;
    }

    /**
     * Get all the compute descriptions already in the system that correspond to virtual machine and filter by.
     * This query is primarily used during instance discovery to find compute descriptions that exist in the system
     * to match the instances received from AWS.
     * The query filters out compute descriptions that represent compute hosts and also checks for other conditions
     * as below:
     * - Environment name(AWS),
     * - id (instance type),
     * - ZoneId(placement).
     * - Created from the enumeration task.
     * Compute hosts are modeled to support VM guests.So excluding them from the query to get
     * compute descriptions for VMs.
     */
    public static QueryTask getCDsRepresentingVMsInLocalSystemCreatedByEnumerationQuery(
            Set<InstanceDescKey> descriptionsSet, List<String> tenantLinks,
            String regionId, String parentComputeLink, String endpointLink) {
        String sourceTaskName = QueryTask.QuerySpecification
                .buildCompositeFieldName(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        SOURCE_TASK_LINK);

        Query query = Query.Builder.create()
                .addKindFieldClause(ComputeDescription.class)
                .addFieldClause(ComputeDescription.FIELD_NAME_ENVIRONMENT_NAME,
                        ComputeDescription.ENVIRONMENT_NAME_AWS)
                .addFieldClause(ComputeDescription.FIELD_NAME_REGION_ID, regionId)
                .addFieldClause(ComputeDescription.FIELD_NAME_COMPUTE_HOST_LINK, parentComputeLink)
                .addFieldClause(sourceTaskName, ResourceEnumerationTaskService.FACTORY_LINK)
                .build();

        // Instance type and zone should fall in one of the passed in values
        Query groupFilter = new Query();
        groupFilter.occurance = Occurance.MUST_OCCUR;
        for (InstanceDescKey key : descriptionsSet) {
            Query itf = new Query()
                    .setTermPropertyName(ComputeDescription.FIELD_NAME_ID)
                    .setTermMatchValue(key.instanceType);
            itf.occurance = Occurance.MUST_OCCUR;
            Query zf = new Query()
                    .setTermPropertyName(ComputeDescription.FIELD_NAME_ZONE_ID)
                    .setTermMatchValue(key.zoneId);
            zf.occurance = Occurance.MUST_OCCUR;

            Query d = new Query();
            d.occurance = Occurance.SHOULD_OCCUR;
            d.addBooleanClause(itf);
            d.addBooleanClause(zf);
            groupFilter.addBooleanClause(d);
        }

        query.addBooleanClause(groupFilter);

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(query)
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.INDEXED_METADATA)
                .addOption(QueryOption.TOP_RESULTS)
                .setResultLimit(getQueryResultLimit())
                .build();

        queryTask.documentSelfLink = UUID.randomUUID().toString();
        queryTask.tenantLinks = tenantLinks;

        return queryTask;
    }

    /**
     * Maps the instance discovered on AWS to a local compute state that will be persisted.
     */
    public static ComputeState mapInstanceToComputeState(ServiceHost host, Instance instance,
            String parentComputeLink, String placementComputeLink, String resourcePoolLink,
            String existingEndpointLink,
            Set<String> endpointLinks,  String computeDescriptionLink, Set<URI> parentCDStatsAdapterReferences,
            Set<String> internalTagLinks, String regionId, String zoneId, List<String> tenantLinks,
            List<Tag> createdExternalTags, Boolean isNewState, List<String> diskLinks) {
        ComputeState computeState = new ComputeState();
        computeState.id = instance.getInstanceId();
        computeState.name = instance.getInstanceId();
        computeState.parentLink = parentComputeLink;
        computeState.computeHostLink = parentComputeLink;
        computeState.type = ComputeType.VM_GUEST;
        computeState.environmentName = ComputeDescription.ENVIRONMENT_NAME_AWS;
        computeState.regionId = regionId;
        computeState.zoneId = zoneId;
        computeState.instanceType = instance.getInstanceType();

        computeState.instanceAdapterReference = AdapterUriUtil.buildAdapterUri(host,
                AWSUriPaths.AWS_INSTANCE_ADAPTER);
        computeState.enumerationAdapterReference = AdapterUriUtil.buildAdapterUri(host,
                AWSUriPaths.AWS_ENUMERATION_CREATION_ADAPTER);
        computeState.statsAdapterReference = AdapterUriUtil.buildAdapterUri(host,
                AWSUriPaths.AWS_STATS_ADAPTER);
        computeState.statsAdapterReferences = parentCDStatsAdapterReferences;

        computeState.resourcePoolLink = resourcePoolLink;
        if (computeState.endpointLinks == null) {
            computeState.endpointLinks = new HashSet<String>();
        }
        computeState.endpointLinks.addAll(endpointLinks);
        // assign existing one, if exists
        if (existingEndpointLink != null) {
            computeState.endpointLink = existingEndpointLink;
        } else {
            computeState.endpointLink = endpointLinks.iterator().next();
        }
        // Compute descriptions are looked up by the instanceType in the local list of CDs.
        computeState.descriptionLink = computeDescriptionLink;
        computeState.hostName = instance.getPublicDnsName();

        // TODO VSYM-375 for adding disk information
        computeState.address = instance.getPublicIpAddress();
        computeState.powerState = AWSUtils.mapToPowerState(instance.getState());

        computeState.customProperties = new HashMap<>();
        computeState.customProperties.put(CUSTOM_OS_TYPE,
                getNormalizedOSType(instance));
        computeState.customProperties.put(SOURCE_TASK_LINK,
                ResourceEnumerationTaskService.FACTORY_LINK);
        computeState.customProperties.put(ComputeProperties.PLACEMENT_LINK,
                placementComputeLink);
        // Network State. Create one network state mapping to each VPC that is discovered during
        // enumeration.
        computeState.customProperties.put(AWS_VPC_ID, instance.getVpcId());
        computeState.tagLinks = new HashSet<>();

        // We only want to add tagLinks to new ComputeStates. We use a separate collection update
        // PATCH to update tagLinks of existing disks.
        if (!instance.getTags().isEmpty() && isNewState) {

            // we have already made sure that the tags exist and we can build their links ourselves
            computeState.tagLinks = instance.getTags().stream()
                    .filter(t -> !AWSConstants.AWS_TAG_NAME.equals(t.getKey()) && createdExternalTags.contains(t))
                    .map(t -> newTagState(t.getKey(), t.getValue(), true, tenantLinks))
                    .map(TagFactoryService::generateSelfLink)
                    .collect(Collectors.toSet());

            if (computeState.tagLinks != null && computeState.tagLinks.contains(null)) {
                host.log(Level.SEVERE, "Null tag link inserted in new ComputeState for instance ID: %s",
                        instance.getInstanceId());
                host.log(Level.SEVERE, "Removing null tag link from new ComputeState");
                computeState.tagLinks.remove(null);
            }
        }

        // The name of the compute state is the value of the AWS_TAG_NAME tag
        String nameTag = getTagValue(instance.getTags(), AWS_TAG_NAME);
        if (nameTag != null && !nameTag.equals(EMPTY_STRING)) {
            computeState.name = nameTag;
        }

        // append internal tagLinks to any existing ones
        if (internalTagLinks != null) {
            computeState.tagLinks.addAll(internalTagLinks);
        }

        if (instance.getLaunchTime() != null) {
            computeState.creationTimeMicros = TimeUnit.MILLISECONDS
                    .toMicros(instance.getLaunchTime().getTime());
        }

        if (diskLinks != null && !diskLinks.isEmpty()) {
            computeState.diskLinks = new ArrayList<>();
            computeState.diskLinks.addAll(diskLinks);
        }

        computeState.tenantLinks = tenantLinks;

        return computeState;
    }

    public static boolean instanceIsInStoppedState(Instance instance) {
        return instance.getState().getName()
                .equals(AWSConstants.INSTANCE_STATE_SHUTTING_DOWN)
                || instance.getState().getName()
                .equals(AWSConstants.INSTANCE_STATE_STOPPED)
                || instance.getState().getName()
                .equals(AWSConstants.INSTANCE_STATE_STOPPING);
    }

    /**
     * Extracts the id from the document link. This is the unique identifier of the document returned as part of the result.
     * @param documentLink
     * @return
     */
    public static String getIdFromDocumentLink(String documentLink) {
        return documentLink.substring(documentLink.lastIndexOf(URI_PATH_CHAR) + 1);
    }


    /**
     * Get the Tag value corresponding to the provided key.
     */
    public static String getTagValue(List<Tag> tags, String key) {
        for (Tag tag : tags) {
            if (tag.getKey().equals(key)) {
                return tag.getValue();
            }
        }
        return null;
    }

    /**
     * Return Instance normalized OS Type.
     */
    private static String getNormalizedOSType(Instance instance) {
        if (WINDOWS_PLATFORM.equalsIgnoreCase(instance.getPlatform())) {
            return OSType.WINDOWS.toString();
        } else { // else assume Linux
            return OSType.LINUX.toString();
        }
    }

    /**
     * This class is used represent a discovered AvailabilityZone unique information.
     */
    public static class ZoneData {
        public String computeLink;
        public String regionId;
        public String zoneId;

        public static ZoneData build(String regionId, String zoneId, String computeLink) {
            ZoneData key = new ZoneData();
            key.regionId = regionId;
            key.zoneId = zoneId;
            key.computeLink = computeLink;
            return key;
        }
    }

    /**
     * This class is used represent a discovered ComputeDescription unique information.
     */
    public static class InstanceDescKey {
        public String instanceType;
        public String regionId;
        public String zoneId;

        public static InstanceDescKey build(String regionId, String zoneId, String instanceType) {
            InstanceDescKey key = new InstanceDescKey();
            key.regionId = regionId;
            key.zoneId = zoneId;
            key.instanceType = instanceType;
            return key;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.regionId, this.zoneId, this.instanceType);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }

            if (o instanceof InstanceDescKey) {
                InstanceDescKey that = (InstanceDescKey) o;
                return Objects.equals(this.instanceType, that.instanceType)
                        && Objects.equals(this.regionId, that.regionId)
                        && Objects.equals(this.zoneId, that.zoneId);
            }

            return false;
        }
    }
}
