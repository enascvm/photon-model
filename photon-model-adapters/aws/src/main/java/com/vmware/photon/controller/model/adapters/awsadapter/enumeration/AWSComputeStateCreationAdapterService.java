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

package com.vmware.photon.controller.model.adapters.awsadapter.enumeration;

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSResourceType.ec2_instance;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.getCDsRepresentingVMsInLocalSystemCreatedByEnumerationQuery;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.getKeyForComputeDescriptionFromCD;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.getRepresentativeListOfCDsFromInstanceList;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.mapInstanceToComputeState;
import static com.vmware.photon.controller.model.adapters.util.AdapterUtils.createDeleteOperation;
import static com.vmware.photon.controller.model.adapters.util.AdapterUtils.createPatchOperation;
import static com.vmware.photon.controller.model.adapters.util.AdapterUtils.createPostOperation;
import static com.vmware.photon.controller.model.adapters.util.TagsUtil.newTagState;
import static com.vmware.photon.controller.model.adapters.util.TagsUtil.updateLocalTagStates;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.TAG_KEY_TYPE;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import com.amazonaws.services.ec2.model.GroupIdentifier;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceNetworkInterface;
import com.amazonaws.services.ec2.model.Tag;

import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSNetworkStateEnumerationAdapterService.AWSNetworkEnumerationResponse;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSSecurityGroupEnumerationAdapterService.AWSSecurityGroupEnumerationResponse;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.InstanceDescKey;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.ZoneData;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Stateless service for the creation of compute states. It accepts a list of AWS instances that
 * need to be created in the local system.It also accepts a few additional fields required for
 * mapping the referential integrity relationships for the compute state when it is persisted in the
 * local system.
 */
public class AWSComputeStateCreationAdapterService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_COMPUTE_STATE_CREATION_ADAPTER;

    private static final String UPDATE_NIC_STATES = "update";

    private static final String REMOVE_NIC_STATES = "remove";

    private static final String ADD_NIC_STATES = "add";

    private static final long QUERY_TASK_EXPIRY_MICROS = TimeUnit.MINUTES.toMicros(1);
    private AWSClientManager clientManager;

    /**
     * Request accepted by this service to trigger create or update of Compute states representing
     * compute instances in Amazon.
     */
    public static class AWSComputeStateCreationRequest {

        public List<Instance> instancesToBeCreated;

        public Map<String, Instance> instancesToBeUpdated;
        public Map<String, ComputeState> computeStatesToBeUpdated;
        // Expands computeStatesToBeUpdated with corresponding NICs
        public Map<String, List<NetworkInterfaceState>> nicStatesToBeUpdated;

        /**
         * Discovered/Enumerated networks in Amazon.
         */
        public AWSNetworkEnumerationResponse enumeratedNetworks;
        public AWSSecurityGroupEnumerationResponse enumeratedSecurityGroups;
        public Map<String, ZoneData> zones;
        public String resourcePoolLink;
        public String endpointLink;
        public String parentComputeLink;
        public AuthCredentialsServiceState parentAuth;
        public String regionId;
        public URI parentTaskLink;

        public List<String> tenantLinks;
        boolean isMock;
        public Set<URI> parentCDStatsAdapterReferences;
    }

    /**
     * The service context that is created for representing the list of instances received into a
     * list of compute states that will be persisted in the system.
     *
     */
    private static class AWSComputeStateCreationContext {
        public AWSComputeStateCreationRequest request;
        public List<Operation> enumerationOperations;
        public AWSComputeStateCreationStage creationStage;
        // Holds the mapping between the instanceType (t2.micro etc) and the document self link to
        // that compute description.
        public Map<InstanceDescKey, String> computeDescriptionMap;
        // Cached operation to signal completion to the AWS instance adapter once all the compute
        // states are successfully created.
        public Operation operation;
        // maintains internal tagLinks Example, link for tagState for type=ec2_instances
        public String ec2TagLink;

        public AWSComputeStateCreationContext(AWSComputeStateCreationRequest request,
                Operation op) {
            this.request = request;
            this.enumerationOperations = new ArrayList<>();
            this.computeDescriptionMap = new HashMap<>();
            this.creationStage = AWSComputeStateCreationStage.GET_RELATED_COMPUTE_DESCRIPTIONS;
            this.operation = op;
        }
    }

    public static enum AWSComputeStateCreationStage {
        GET_RELATED_COMPUTE_DESCRIPTIONS,
        /**
         * Query for the internal tagState {key = "type", value = "ec2_instance"}
         */
        GET_INTERNAL_TYPE_TAG,
        /**
         * Create internal tag for type
         */
        CREATE_INTERNAL_TYPE_TAG,
        /**
         * Create the operations for creating ComputeStates and their corresponding
         * NetworkInterfaceStates
         */
        CREATE_COMPUTESTATES_OPERATIONS,
        /**
         * For each ComputeState which needs an update create post operation. Update corresponding
         * NetworkInterfaceStates
         */
        UPDATE_COMPUTESTATES_OPERATIONS,
        /**
         * Execute all the crete and update operations, generated during the previous stages
         */
        CREATE_COMPUTESTATES,
        SIGNAL_COMPLETION,
        CREATE_EXTERNAL_TAGS,
        UPDATE_EXTERNAL_TAGS
    }

    public AWSComputeStateCreationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.EC2);
    }

    @Override
    public void handleStop(Operation op) {
        AWSClientManagerFactory.returnClientManager(this.clientManager,
                AWSConstants.AwsClientType.EC2);
        super.handleStop(op);
    }

    @Override
    public void handlePatch(Operation op) {
        setOperationHandlerInvokeTimeStat(op);
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        AWSComputeStateCreationRequest cs = op.getBody(AWSComputeStateCreationRequest.class);
        AWSComputeStateCreationContext context = new AWSComputeStateCreationContext(cs, op);
        if (cs.isMock) {
            op.complete();
        }
        handleComputeStateCreateOrUpdate(context);
    }

    /**
     * Creates the compute states in the local document store based on the AWS instances received
     * from the remote endpoint.
     *
     * @param context
     *            The local service context that has all the information needed to create the
     *            additional compute states in the local system.
     */
    private void handleComputeStateCreateOrUpdate(AWSComputeStateCreationContext context) {
        this.logFine(() -> String.format("Transition to: %s", context.creationStage));
        switch (context.creationStage) {
        case GET_RELATED_COMPUTE_DESCRIPTIONS:
            getRelatedComputeDescriptions(context, AWSComputeStateCreationStage.CREATE_INTERNAL_TYPE_TAG);
            break;
        case CREATE_INTERNAL_TYPE_TAG:
            createInternalTypeTag(context, AWSComputeStateCreationStage.CREATE_EXTERNAL_TAGS);
            break;
        case CREATE_EXTERNAL_TAGS:
            createTags(context, AWSComputeStateCreationStage.CREATE_COMPUTESTATES_OPERATIONS);
            break;
        case CREATE_COMPUTESTATES_OPERATIONS:
            populateCreateOperations(context,
                    AWSComputeStateCreationStage.UPDATE_EXTERNAL_TAGS);
            break;
        case UPDATE_EXTERNAL_TAGS:
            updateTagLinks(context).whenComplete(thenComputeStateCreateOrUpdate(context,
                    AWSComputeStateCreationStage.UPDATE_COMPUTESTATES_OPERATIONS));
            break;
        case UPDATE_COMPUTESTATES_OPERATIONS:
            populateUpdateOperations(context, AWSComputeStateCreationStage.CREATE_COMPUTESTATES);
            break;
        case CREATE_COMPUTESTATES:
            kickOffComputeStateCreation(context, AWSComputeStateCreationStage.SIGNAL_COMPLETION);
            break;
        case SIGNAL_COMPLETION:
            setOperationDurationStat(context.operation);
            context.operation.complete();
            break;
        default:
            Throwable t = new IllegalArgumentException("Unknown AWS enumeration:compute state"
                    + " creation stage");
            finishWithFailure(context, t);
            break;
        }
    }

    /**
     * {@code handleComputeStateCreateOrUpdate} version suitable for chaining to
     * {@code DeferredResult.whenComplete}.
     */
    private BiConsumer<AWSComputeStateCreationContext, Throwable> thenComputeStateCreateOrUpdate(
            AWSComputeStateCreationContext context,
            AWSComputeStateCreationStage next) {
        // NOTE: In case of error 'ignoreCtx' is null so use passed context!
        return (ignoreCtx, exc) -> {
            if (exc != null) {
                finishWithFailure(context, exc);
                return;
            }
            context.creationStage = next;
            handleComputeStateCreateOrUpdate(context);
        };
    }

    private void createInternalTypeTag(AWSComputeStateCreationContext context, AWSComputeStateCreationStage next) {
        TagState typeTag = newTagState(TAG_KEY_TYPE, ec2_instance.toString(), false, context.request.tenantLinks);

        Operation.CompletionHandler handler = (completedOp, failure) -> {
            if (failure != null) {
                // Process successful operations only.
                context.creationStage = next;
                handleComputeStateCreateOrUpdate(context);
            }

            TagState tagState = completedOp.getBody(TagState.class);
            context.ec2TagLink = tagState.documentSelfLink;
            context.creationStage = next;
            handleComputeStateCreateOrUpdate(context);
        };

        sendRequest(Operation.createPost(this, TagService.FACTORY_LINK)
                .setBody(typeTag)
                .setCompletion(handler));
    }

    private void createTags(AWSComputeStateCreationContext context,
            AWSComputeStateCreationStage next) {
        // Get all tags from the instances to be created
        Set<Tag> create = context.request.instancesToBeCreated.stream()
                .flatMap(i -> i.getTags().stream())
                .collect(Collectors.toSet());

        // Put them in a set to remove the duplicates
        Set<Tag> allTags = new HashSet<>();
        allTags.addAll(create);

        // POST each of the tags. If a tag exists it won't be created again. We don't want the name
        // tags, so filter them out
        List<Operation> operations = allTags.stream()
                .filter(t -> !AWSConstants.AWS_TAG_NAME.equals(t.getKey()))
                .map(t -> newTagState(t.getKey(), t.getValue(), true,
                        context.request.tenantLinks))
                .map(tagState -> Operation.createPost(this, TagService.FACTORY_LINK)
                        .setBody(tagState))
                .collect(Collectors.toList());

        if (operations.isEmpty()) {
            context.creationStage = next;
            handleComputeStateCreateOrUpdate(context);
        } else {
            OperationJoin.create(operations).setCompletion((ops, exs) -> {
                if (exs != null && !exs.isEmpty()) {
                    finishWithFailure(context, exs.values().iterator().next());
                    return;
                }

                context.creationStage = next;
                handleComputeStateCreateOrUpdate(context);
            }).sendWith(this);
        }
    }

    /**
     * Looks up the compute descriptions associated with the compute states to be created in the
     * system.
     */
    private void getRelatedComputeDescriptions(AWSComputeStateCreationContext context,
            AWSComputeStateCreationStage next) {
        // Get the related compute descriptions for all the compute states are to be updated and
        // created.
        Set<InstanceDescKey> representativeCDSet = getRepresentativeListOfCDsFromInstanceList(
                context.request.instancesToBeCreated, context.request.zones);
        representativeCDSet.addAll(getRepresentativeListOfCDsFromInstanceList(
                context.request.instancesToBeUpdated.values(), context.request.zones));

        if (representativeCDSet.isEmpty()) {
            context.creationStage = next;
            handleComputeStateCreateOrUpdate(context);
            return;
        }

        QueryTask queryTask = getCDsRepresentingVMsInLocalSystemCreatedByEnumerationQuery(
                representativeCDSet,
                context.request.tenantLinks,
                this, context.request.parentTaskLink,
                context.request.regionId);
        queryTask.documentExpirationTimeMicros = Utils.getNowMicrosUtc() + QUERY_TASK_EXPIRY_MICROS;

        // create the query to find an existing compute description
        QueryUtils.startQueryTask(this, queryTask)
                .whenComplete((qrt, e) -> {
                    if (e != null) {
                        logWarning(() -> String.format("Failure retrieving query results: %s",
                                e.toString()));
                        finishWithFailure(context, e);
                        return;
                    }

                    if (qrt != null && qrt.results.documentCount > 0) {
                        for (Object s : qrt.results.documents.values()) {
                            ComputeDescription localComputeDescription = Utils.fromJson(s,
                                    ComputeDescription.class);
                            context.computeDescriptionMap.put(
                                    getKeyForComputeDescriptionFromCD(localComputeDescription),
                                    localComputeDescription.documentSelfLink);
                        }
                        logFine(() -> String.format("%d compute descriptions found",
                                context.computeDescriptionMap.size()));
                    } else {
                        logFine(() -> "No compute descriptions found");
                    }
                    context.creationStage = next;
                    handleComputeStateCreateOrUpdate(context);
                });
    }

    /**
     * Method to create Compute States associated with the instances received from the AWS host.
     */
    private void populateCreateOperations(AWSComputeStateCreationContext context,
            AWSComputeStateCreationStage next) {
        if (context.request.instancesToBeCreated == null
                || context.request.instancesToBeCreated.size() == 0) {
            logFine(() -> "No local compute states to be created");
            context.creationStage = next;
            handleComputeStateCreateOrUpdate(context);
        } else {
            logFine(() -> String.format("Need to create %d local compute states",
                    context.request.instancesToBeCreated.size()));

            for (int i = 0; i < context.request.instancesToBeCreated.size(); i++) {

                Instance instance = context.request.instancesToBeCreated.get(i);

                String zoneId = instance.getPlacement().getAvailabilityZone();
                ZoneData zoneData = context.request.zones.get(zoneId);
                String regionId = zoneData.regionId;

                InstanceDescKey descKey = InstanceDescKey.build(regionId, zoneId,
                        instance.getInstanceType());

                ComputeState computeStateToBeCreated = mapInstanceToComputeState(
                        this.getHost(), instance,
                        context.request.parentComputeLink, zoneData.computeLink,
                        context.request.resourcePoolLink,
                        context.request.endpointLink,
                        context.computeDescriptionMap.get(descKey),
                        context.request.parentCDStatsAdapterReferences,
                        context.ec2TagLink,
                        regionId, zoneId,
                        context.request.tenantLinks);
                computeStateToBeCreated.networkInterfaceLinks = new ArrayList<>();

                if (!AWSEnumerationUtils.instanceIsInStoppedState(instance)) {
                    // for each NIC create Description and State create operations. Link the
                    // ComputeState to be created to the NIC State
                    for (InstanceNetworkInterface awsNic : instance.getNetworkInterfaces()) {

                        NetworkInterfaceState nicState = createNICStateAndDescription(
                                context, awsNic);

                        computeStateToBeCreated.networkInterfaceLinks.add(UriUtils.buildUriPath(
                                NetworkInterfaceService.FACTORY_LINK,
                                nicState.documentSelfLink));
                    }
                }

                Operation postComputeState = createPostOperation(this, computeStateToBeCreated,
                        ComputeService.FACTORY_LINK);

                context.enumerationOperations.add(postComputeState);
            }

            context.creationStage = next;
            handleComputeStateCreateOrUpdate(context);
        }
    }

    // Utility method which creates a new NetworkInterface State and Descriptions
    // from provided AWS Nic, and adds them to the enumerationOperations list
    private NetworkInterfaceState createNICStateAndDescription(
            AWSComputeStateCreationContext context, InstanceNetworkInterface awsNic) {

        final NetworkInterfaceState nicState;
        {
            nicState = new NetworkInterfaceState();
            nicState.id = awsNic.getNetworkInterfaceId();
            nicState.name = nicState.id;
            nicState.address = awsNic.getPrivateIpAddress();
            nicState.subnetLink = context.request.enumeratedNetworks.subnets
                    .get(awsNic.getSubnetId());
            nicState.tenantLinks = context.request.tenantLinks;
            nicState.endpointLink = context.request.endpointLink;
            nicState.regionId = context.request.regionId;

            if (context.request.enumeratedSecurityGroups != null) {
                for (GroupIdentifier awsSG : awsNic.getGroups()) {
                    // we should have updated the list of SG Ids before this step and
                    // should have ensured that all the SGs exist locally
                    String securityGroupLink = context.request.enumeratedSecurityGroups.securityGroupStates
                            .get(awsSG.getGroupId());
                    if (securityGroupLink == null || securityGroupLink.isEmpty()) {
                        continue;
                    }
                    if (nicState.securityGroupLinks == null) {
                        nicState.securityGroupLinks = new ArrayList<>();
                    }
                    nicState.securityGroupLinks.add(securityGroupLink);
                }
            }

            nicState.deviceIndex = awsNic.getAttachment().getDeviceIndex();

            // Link is set, because it's referenced by CS before post
            nicState.documentSelfLink = UUID.randomUUID().toString();

            Operation postNetworkInterfaceState = createPostOperation(this, nicState,
                    NetworkInterfaceService.FACTORY_LINK);

            context.enumerationOperations
                    .add(postNetworkInterfaceState);
        }
        return nicState;
    }

    private DeferredResult<AWSComputeStateCreationContext> updateTagLinks(
            AWSComputeStateCreationContext context) {
        if (context.request.instancesToBeUpdated == null
                || context.request.instancesToBeUpdated.size() == 0) {
            logFine(() -> "No local compute states to be updated so there are no tags to update.");
            return DeferredResult.completed(context);
        } else {

            List<DeferredResult<Set<String>>> updateCSTagLinksOps = new ArrayList<>();

            for (String instanceId : context.request.instancesToBeUpdated.keySet()) {
                Instance instance = context.request.instancesToBeUpdated.get(instanceId);
                ComputeState existingComputeState = context.request.computeStatesToBeUpdated
                        .get(instanceId);
                Map<String, String> remoteTags = new HashMap<>();
                for (Tag awsInstanceTag : instance.getTags()) {
                    if (!awsInstanceTag.getKey().equals(AWSConstants.AWS_TAG_NAME)) {
                        remoteTags.put(awsInstanceTag.getKey(), awsInstanceTag.getValue());
                    }
                }
                updateCSTagLinksOps
                        .add(updateLocalTagStates(this, existingComputeState, remoteTags));
            }
            return DeferredResult.allOf(updateCSTagLinksOps).thenApply(gnore -> context);
        }
    }

    private void populateUpdateOperations(AWSComputeStateCreationContext context,
            AWSComputeStateCreationStage next) {
        if (context.request.instancesToBeUpdated == null
                || context.request.instancesToBeUpdated.size() == 0) {
            logFine(() -> "No local compute states to be updated");
            context.creationStage = next;
            handleComputeStateCreateOrUpdate(context);
        } else {
            logFine(() -> String.format("Need to update %d local compute states",
                    context.request.instancesToBeUpdated.size()));

            for (String instanceId : context.request.instancesToBeUpdated.keySet()) {

                Instance instance = context.request.instancesToBeUpdated.get(instanceId);
                ComputeState existingComputeState = context.request.computeStatesToBeUpdated
                        .get(instanceId);

                // Calculate NICs delta - collection of NIC States to add, to update and to delete
                Map<String, List<Integer>> deviceIndexesDelta = new HashMap<>();
                Map<String, Map<String, Collection<Object>>> linksToNICSToAddOrRemove = new HashMap<>();

                // The stopped or stopping instance does not have full network settings.
                if (!AWSEnumerationUtils.instanceIsInStoppedState(instance)) {

                    // Get existing NetworkInterfaceStates for this ComputeState
                    List<NetworkInterfaceState> existingNicStates = context.request.nicStatesToBeUpdated
                            .get(instanceId);

                    deviceIndexesDelta = calculateNICsDeviceIndexesDelta(instance,
                            existingNicStates);

                    linksToNICSToAddOrRemove = addUpdateOrRemoveNICStates(context, instance,
                            deviceIndexesDelta);
                }

                // Create dedicated PATCH operation for updating NIC Links {{
                if (linksToNICSToAddOrRemove.get(ADD_NIC_STATES) != null
                        || linksToNICSToAddOrRemove.get(REMOVE_NIC_STATES) != null) {
                    ServiceStateCollectionUpdateRequest updateComputeStateRequest = ServiceStateCollectionUpdateRequest
                            .create(linksToNICSToAddOrRemove.get(ADD_NIC_STATES),
                                    linksToNICSToAddOrRemove.get(REMOVE_NIC_STATES));

                    Operation patchComputeStateNICLinks = Operation
                            .createPatch(UriUtils.buildUri(this.getHost(),
                                    existingComputeState.documentSelfLink))
                            .setBody(updateComputeStateRequest)
                            .setReferer(this.getUri());
                    context.enumerationOperations.add(patchComputeStateNICLinks);
                }

                // Update ComputeState
                String zoneId = instance.getPlacement().getAvailabilityZone();
                ZoneData zoneData = context.request.zones.get(zoneId);

                ComputeState computeStateToBeUpdated = mapInstanceToComputeState(
                        this.getHost(), instance,
                        context.request.parentComputeLink, zoneData.computeLink,
                        existingComputeState.resourcePoolLink != null
                                ? existingComputeState.resourcePoolLink
                                : context.request.resourcePoolLink,
                        context.request.endpointLink,
                        existingComputeState.descriptionLink,
                        context.request.parentCDStatsAdapterReferences,
                        context.ec2TagLink,
                        zoneData.regionId, zoneId,
                        context.request.tenantLinks);
                computeStateToBeUpdated.documentSelfLink = existingComputeState.documentSelfLink;

                Operation patchComputeState = createPatchOperation(this, computeStateToBeUpdated,
                        existingComputeState.documentSelfLink);
                context.enumerationOperations.add(patchComputeState);

                Map<String, Collection<Object>> collectionsToAddMap = Collections.singletonMap
                        (ComputeState.FIELD_NAME_TAG_LINKS, Collections.singletonList(context.ec2TagLink));
                Map<String, Collection<Object>> collectionsToRemoveMap = Collections.singletonMap
                        (ComputeState.FIELD_NAME_TAG_LINKS, Collections.EMPTY_LIST);

                ServiceStateCollectionUpdateRequest updateTagLinksRequest = ServiceStateCollectionUpdateRequest
                        .create(collectionsToAddMap, collectionsToRemoveMap);
                context.enumerationOperations.add(Operation.createPatch(this.getHost(),
                        computeStateToBeUpdated.documentSelfLink)
                        .setReferer(this.getUri())
                        .setBody(updateTagLinksRequest));
            }

            context.creationStage = next;
            handleComputeStateCreateOrUpdate(context);
        }
    }

    /**
     * Compare the deviceIndexes of the AWS NICs to the ones of local NIC States and return arrays
     * with indexes to add, to remove or to delete
     */
    private Map<String, List<Integer>> calculateNICsDeviceIndexesDelta(Instance instance,
            List<NetworkInterfaceState> existingNicStates) {

        // Collect all device indexes of local and remote NICs
        List<Integer> awsDeviceIndexes = instance.getNetworkInterfaces() != null
                ? instance.getNetworkInterfaces().stream()
                        .map(awsNic -> awsNic.getAttachment().getDeviceIndex())
                        .collect(Collectors.toList())
                : Collections.emptyList();
        List<Integer> localNICsDeviceIndexes = existingNicStates != null
                ? existingNicStates.stream()
                        .filter(Objects::nonNull)
                        .map(nicState -> nicState.deviceIndex)
                        .collect(Collectors.toList())
                : Collections.emptyList();

        // Calculate delta lists {{

        // From the AWS NICs, substract all local NICs to identify NICs that should be created
        List<Integer> remoteDeviceIndexesToAdd = new ArrayList<>(awsDeviceIndexes);
        remoteDeviceIndexesToAdd.removeAll(localNICsDeviceIndexes);

        // From the local NIC States, retain only the ones which correspond to AWS NICs to identify
        // which one to update
        List<Integer> localDeviceIndexesToUpdate = new ArrayList<>(awsDeviceIndexes);
        localDeviceIndexesToUpdate.retainAll(localNICsDeviceIndexes);

        // From the local NIC States, substract all the AWS NICs to identify NICs that should be
        // removed
        List<Integer> localDeviceIndexesToRemove = new ArrayList<>(localNICsDeviceIndexes);
        localDeviceIndexesToRemove.removeAll(awsDeviceIndexes);
        // }}

        Map<String, List<Integer>> deltaLists = new HashMap<>();

        deltaLists.put(ADD_NIC_STATES, remoteDeviceIndexesToAdd);
        deltaLists.put(UPDATE_NIC_STATES, localDeviceIndexesToUpdate);
        deltaLists.put(REMOVE_NIC_STATES, localDeviceIndexesToRemove);

        return deltaLists;
    }

    /**
     * From the previously calculated NICs delta (based on whether the local state correspond to
     * existing AWS object, the AWS object was deleted, or a new AWS object was added): 1) Create,
     * update or delete NICState objects 2) Update the CS's references to the added or removed
     * NetworkInterfaceStates
     */
    private Map<String, Map<String, Collection<Object>>> addUpdateOrRemoveNICStates(
            AWSComputeStateCreationContext context, Instance instance,
            Map<String, List<Integer>> nicsDeviceIndexDeltaMap) {

        List<NetworkInterfaceState> existingNicStates = context.request.nicStatesToBeUpdated
                .get(instance.getInstanceId());

        // Generate operation for adding NIC state and description, and retain its link to add to
        // the CS {{
        List<Integer> deviceIndexesToAdd = nicsDeviceIndexDeltaMap.get(ADD_NIC_STATES);

        Collection<Object> networkInterfaceLinksToBeAdded = instance.getNetworkInterfaces().stream()
                .filter(awsNic -> deviceIndexesToAdd
                        .contains(awsNic.getAttachment().getDeviceIndex()))

                // create new NIC State and Description operation
                .map(awsNic -> createNICStateAndDescription(context, awsNic))
                // and collect their documentSelfLinks
                .map(addedNicState -> UriUtils.buildUriPath(NetworkInterfaceService.FACTORY_LINK,
                        addedNicState.documentSelfLink))
                .collect(Collectors.toList());
        // }}

        // Generate operation for removing NIC states, and retain its link to remove from the CS {{
        List<Integer> deviceIndexesToRemove = nicsDeviceIndexDeltaMap.get(REMOVE_NIC_STATES);
        Collection<Object> networkInterfaceLinksToBeRemoved = deviceIndexesToRemove.stream()
                // else, create Post operations to delete NICStates and collect their
                // documentSelfLinks
                .map(deviceIndexToRemove -> {
                    NetworkInterfaceState stateToDelete = existingNicStates.stream()
                            .filter(existNicState -> existNicState.deviceIndex == deviceIndexToRemove)
                            .findFirst()
                            .orElse(null);
                    return stateToDelete;
                })
                .filter(existingNicState -> existingNicState != null)

                // create NIC state patch operation which set the expiration time to now
                .map(existingNicState -> deleteNICState(context, existingNicState))

                .map(existingNicState -> existingNicState.documentSelfLink)
                .collect(Collectors.toList());
        // }}

        // Generate operation for updating NIC states, no links should be updated on CS in this case
        // {{
        List<Integer> deviceIndexesToUpdate = nicsDeviceIndexDeltaMap.get(UPDATE_NIC_STATES);
        deviceIndexesToUpdate.stream()
                .map(deviceIndexToUpdate -> existingNicStates
                        .stream()
                        .filter(existNicState -> existNicState.deviceIndex == deviceIndexToUpdate)
                        .findFirst()
                        .orElse(null))
                .filter(existingNicState -> existingNicState != null)

                // create NIC patch operation for update
                .forEach(existingNicState -> updateNICState(context, instance, existingNicState));
        // }}

        Map<String, Map<String, Collection<Object>>> nicsDeltaMap = new HashMap<>();
        // only add the collections to the delta map in case there is something to add/remove
        if (!networkInterfaceLinksToBeRemoved.isEmpty()) {
            Map<String, Collection<Object>> collectionsToRemoveMap = new HashMap<>();
            collectionsToRemoveMap.put(ComputeState.FIELD_NAME_NETWORK_INTERFACE_LINKS,
                    networkInterfaceLinksToBeRemoved);
            nicsDeltaMap.put(REMOVE_NIC_STATES, collectionsToRemoveMap);
        }

        if (!networkInterfaceLinksToBeAdded.isEmpty()) {
            Map<String, Collection<Object>> collectionsToAddMap = new HashMap<>();
            collectionsToAddMap.put(ComputeState.FIELD_NAME_NETWORK_INTERFACE_LINKS,
                    networkInterfaceLinksToBeAdded);
            nicsDeltaMap.put(ADD_NIC_STATES, collectionsToAddMap);
        }

        return nicsDeltaMap;
    }

    /**
     * Set the expiration time of the identified existing NetworkInterfaceState to now so that it is
     * deleted from the systems
     */
    private NetworkInterfaceState deleteNICState(AWSComputeStateCreationContext context,
            NetworkInterfaceState existingNicState) {
        Operation updateNicOperation = createDeleteOperation(this, existingNicState);
        context.enumerationOperations.add(updateNicOperation);
        return existingNicState;
    }

    /**
     * For each NetworkInterfaceState, obtain the corresponding AWS NIC, and generate POST operation
     * to update its private address
     */
    private NetworkInterfaceState updateNICState(AWSComputeStateCreationContext context,
            Instance instance, NetworkInterfaceState existingNicState) {

        InstanceNetworkInterface awsNic = instance
                .getNetworkInterfaces()
                .stream()
                .filter(currentAwsNic -> currentAwsNic.getAttachment()
                        .getDeviceIndex() == existingNicState.deviceIndex)
                .findFirst().orElse(null);

        // create a new NetworkInterfaceState for updating the address
        NetworkInterfaceState updateNicState = new NetworkInterfaceState();
        updateNicState.address = awsNic.getPrivateIpAddress();
        if (context.request.enumeratedSecurityGroups != null) {
            for (GroupIdentifier awsSG : awsNic.getGroups()) {
                // we should have updated the list of SG Ids before this step and should have
                // ensured that all the SGs exist locally
                String securityGroupLink = context.request.enumeratedSecurityGroups.securityGroupStates
                        .get(awsSG.getGroupId());
                if (securityGroupLink == null || securityGroupLink.isEmpty()) {
                    continue;
                }
                if (updateNicState.securityGroupLinks == null) {
                    updateNicState.securityGroupLinks = new ArrayList<>();
                }
                updateNicState.securityGroupLinks.add(securityGroupLink);
            }
        }
        // create update operation and add it for batch execution on the next stage
        Operation updateNicOperation = createPatchOperation(this, updateNicState,
                existingNicState.documentSelfLink);
        context.enumerationOperations.add(updateNicOperation);

        return updateNicState;
    }

    /**
     * Kicks off the creation of all the identified compute states and networks and creates a join
     * handler for the successful completion of each one of those. Patches completion to parent once
     * all the entities are created successfully.
     */
    private void kickOffComputeStateCreation(AWSComputeStateCreationContext context,
            AWSComputeStateCreationStage next) {
        if (context.enumerationOperations == null
                || context.enumerationOperations.size() == 0) {
            logFine(() -> "No compute or networks states to be created");
            context.creationStage = next;
            handleComputeStateCreateOrUpdate(context);
            return;
        }
        OperationJoin.JoinedCompletionHandler joinCompletion = (ox,
                exc) -> {
            if (exc != null) {
                logSevere(() -> String.format("Error creating compute state and the associated"
                        + " network %s", Utils.toString(exc)));
                finishWithFailure(context, exc.values().iterator().next());
                return;
            }
            logFine(() -> "Successfully created compute and networks states.");
            context.creationStage = next;
            handleComputeStateCreateOrUpdate(context);
        };
        OperationJoin joinOp = OperationJoin.create(context.enumerationOperations);
        joinOp.setCompletion(joinCompletion);
        joinOp.sendWith(getHost());
    }

    private void finishWithFailure(AWSComputeStateCreationContext context, Throwable exc) {
        context.operation.fail(exc);
    }

}
