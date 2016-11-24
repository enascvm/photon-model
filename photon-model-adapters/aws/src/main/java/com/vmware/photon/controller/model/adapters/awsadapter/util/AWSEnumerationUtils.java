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
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.TILDA;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.getRegionId;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.SOURCE_TASK_LINK;
import static com.vmware.xenon.common.UriUtils.URI_PATH_CHAR;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Tag;

import com.vmware.photon.controller.model.ComputeProperties.OSType;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSInstanceService;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.TagFactoryService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

/**
 * Utility class to hold methods used across different enumeration classes.
 */
public class AWSEnumerationUtils {

    /**
     * Gets the key to uniquely represent a compute description that needs to be created in the system.
     * Currently uses regionId and instanceType and is represented as below:
     * us-east-1~t2.micro
     *
     * TODO harden key as more logic is realized in the enumeration
     * service.
     */
    public static String getKeyForComputeDescriptionFromInstance(Instance i) {
        // Representing the compute-description as a key regionId~instanceType
        return getRegionId(i).concat(TILDA).concat(i.getInstanceType());
    }

    /**
     * Returns the key that includes the attributes that define this compute description. This key is
     * used to link the compute states to the correct compute descriptions.
     */
    public static String getKeyForComputeDescriptionFromCD(ComputeDescription computeDescription) {
        // Representing the compute-description as a key regionId~instanceType
        return computeDescription.regionId.concat(TILDA).concat(computeDescription.instanceType);
    }

    /**
     * Returns the instanceType from the compute description key which is created to look like
     * regionId~instanceType
     */
    public static String getInstanceTypeFromComputeDescriptionKey(String computeDescriptionKey) {
        return computeDescriptionKey.substring(computeDescriptionKey.indexOf(TILDA) + 1);

    }

    /**
     * Returns the regionId from the compute description key that looks like  regionId~instanceType
     */
    public static String getRegionIdFromComputeDescriptionKey(String computeDescriptionKey) {
        return computeDescriptionKey.substring(0, computeDescriptionKey.indexOf(TILDA));
    }

    /**
     * From the list of instances that are received from AWS arrive at the minimal set of compute descriptions that need
     * to be created locally to represent them.The compute descriptions are represented as regionId~instanceType
     * and put into a hashset. As a result, a representative set is created to represent all the discovered VMs.
     * @param instanceList
     */
    public static HashSet<String> getRepresentativeListOfCDsFromInstanceList(
            Collection<Instance> instanceList) {
        HashSet<String> representativeCDSet = new HashSet<String>();
        for (Instance instance : instanceList) {
            representativeCDSet.add(getKeyForComputeDescriptionFromInstance(instance));
        }
        return representativeCDSet;
    }

    /**
     * Get all the compute descriptions already in the system that correspond to virtual machine and filter by This query is primarily used during instance discovery to find compute descriptions that exist in the system
     * to match the instances received from AWS.
     * The query filters out compute descriptions that represent compute hosts and also checks for other conditions as below:
     * - Environment name(AWS),
     * - id (instance type),
     * - ZoneId(placement).
     * - Created from the enumeration task.
     * Compute hosts are modeled to support VM guests.So excluding them from the query to get
     * compute descriptions for VMs.
     */
    public static QueryTask getCDsRepresentingVMsInLocalSystemCreatedByEnumerationQuery(
            Set<String> representativeComputeDescriptionSet, List<String> tenantLinks,
            String regionId) {
        String sourceTaskName = QueryTask.QuerySpecification
                .buildCompositeFieldName(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        SOURCE_TASK_LINK);

        Query query = Query.Builder.create()
                .addKindFieldClause(ComputeDescription.class)
                .addFieldClause(ComputeDescription.FIELD_NAME_ENVIRONMENT_NAME,
                        AWSInstanceService.AWS_ENVIRONMENT_NAME)
                .addFieldClause(ComputeDescription.FIELD_NAME_REGION_ID, regionId)
                .addFieldClause(sourceTaskName, ResourceEnumerationTaskService.FACTORY_LINK)
                .build();

        // Instance type should fall in one of the passed in values
        Query instanceTypeFilterParentQuery = new Query();
        instanceTypeFilterParentQuery.occurance = Occurance.MUST_OCCUR;
        for (String key : representativeComputeDescriptionSet) {
            Query instanceTypeFilter = new Query()
                    .setTermPropertyName(ComputeDescription.FIELD_NAME_ID)
                    .setTermMatchValue(getInstanceTypeFromComputeDescriptionKey(key));
            instanceTypeFilter.occurance = Occurance.SHOULD_OCCUR;
            instanceTypeFilterParentQuery.addBooleanClause(instanceTypeFilter);
        }

        query.addBooleanClause(instanceTypeFilterParentQuery);

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(query)
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.TOP_RESULTS)
                .setResultLimit(representativeComputeDescriptionSet.size())
                .build();

        queryTask.documentSelfLink = UUID.randomUUID().toString();
        queryTask.tenantLinks = tenantLinks;

        return queryTask;
    }

    /**
     * Maps the instance discovered on AWS to a local compute state that will be persisted.
     */
    public static ComputeState mapInstanceToComputeState(Instance instance,
            String parentComputeLink, String resourcePoolLink, String computeDescriptionLink,
            List<String> tenantLinks) {
        ComputeState computeState = new ComputeState();
        computeState.id = instance.getInstanceId();
        computeState.name = instance.getInstanceId();
        computeState.parentLink = parentComputeLink;
        computeState.type = ComputeType.VM_GUEST;

        computeState.resourcePoolLink = resourcePoolLink;
        // Compute descriptions are looked up by the instanceType in the local list of CDs.
        computeState.descriptionLink = UriUtils
                .buildUriPath(computeDescriptionLink);

        // TODO VSYM-375 for adding disk information

        computeState.address = instance.getPublicIpAddress();
        computeState.powerState = AWSUtils.mapToPowerState(instance.getState());
        computeState.customProperties = new HashMap<String, String>();

        computeState.customProperties.put(CUSTOM_OS_TYPE,
                getNormalizedOSType(instance));

        if (!instance.getTags().isEmpty()) {

            // we have already made sure that the tags exist and we can build their links ourselves
            computeState.tagLinks = instance.getTags().stream()
                    .filter(t -> !AWSConstants.AWS_TAG_NAME.equals(t.getKey()))
                    .map(t -> mapTagToTagState(t, tenantLinks))
                    .map(TagFactoryService::generateSelfLink)
                    .collect(Collectors.toSet());

            // The name of the compute state is the value of the AWS_TAG_NAME tag
            String nameTag = getTagValue(instance, AWS_TAG_NAME);
            if (nameTag != null) {
                computeState.name = nameTag;
            }
        }
        computeState.customProperties.put(SOURCE_TASK_LINK,
                ResourceEnumerationTaskService.FACTORY_LINK);

        if (instance.getLaunchTime() != null) {
            computeState.creationTimeMicros = TimeUnit.MILLISECONDS
                    .toMicros(instance.getLaunchTime().getTime());
        }

        computeState.tenantLinks = tenantLinks;

        // Network State. Create one network state mapping to each VPC that is discovered during
        // enumeration.
        computeState.customProperties.put(AWS_VPC_ID,
                instance.getVpcId());
        return computeState;
    }

    public static TagState mapTagToTagState(Tag tag, List<String> tenantLinks) {
        TagState tagState = new TagState();
        tagState.key = tag.getKey() == null ? "" : tag.getKey();
        tagState.value = tag.getValue();
        tagState.tenantLinks = tenantLinks;

        return tagState;
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
    private static String getTagValue(Instance instance, String key) {
        for (Tag tag : instance.getTags()) {
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
}
