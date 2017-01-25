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

import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.getCDsRepresentingVMsInLocalSystemCreatedByEnumerationQuery;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.getKeyForComputeDescriptionFromCD;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.getRepresentativeListOfCDsFromInstanceList;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.mapInstanceToComputeState;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.mapTagToTagState;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSNetworkUtils.getNICStateByDeviceId;
import static com.vmware.photon.controller.model.adapters.util.AdapterUtils.createPatchOperation;
import static com.vmware.photon.controller.model.adapters.util.AdapterUtils.createPostOperation;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.IpAssignment;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.tasks.QueryUtils;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Stateless service for the creation of compute states. It accepts a list of AWS instances that
 * need to be created in the local system.It also accepts a few additional fields required for
 * mapping the referential integrity relationships for the compute state when it is persisted in the
 * local system.
 */
public class AWSComputeStateCreationAdapterService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_COMPUTE_STATE_CREATION_ADAPTER;
    private static final long QUERY_TASK_EXPIRY_MICROS = TimeUnit.MINUTES.toMicros(1);
    private AWSClientManager clientManager;

    public static enum AWSComputeStateCreationStage {
        GET_RELATED_COMPUTE_DESCRIPTIONS,
        /**
         * Create the operations for creating ComputeStates
         * and their corresponding NetworkInterfaceStates
         */
        CREATE_COMPUTESTATES_OPERATIONS,
        /**
         * For each ComputeState which needs an update
         * create post operation. Update corresponding NetworkInterfaceStates
         */
        UPDATE_COMPUTESTATES_OPERATIONS,
        /**
         * Execute all the crete and update operations,
         * generated during the previous stages
         */
        CREATE_COMPUTESTATES,
        SIGNAL_COMPLETION,
        CREATE_TAGS,
    }

    public AWSComputeStateCreationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.EC2);
    }

    /**
     * Request accepted by this service to trigger create or update of Compute states
     * representing compute instances in Amazon.
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
        public AuthCredentialsService.AuthCredentialsServiceState parentAuth;
        public String regionId;
        public URI parentTaskLink;
        boolean isMock;
        public List<String> tenantLinks;
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

        public AWSComputeStateCreationContext(AWSComputeStateCreationRequest request,
                Operation op) {
            this.request = request;
            this.enumerationOperations = new ArrayList<>();
            this.computeDescriptionMap = new HashMap<>();
            this.creationStage = AWSComputeStateCreationStage.GET_RELATED_COMPUTE_DESCRIPTIONS;
            this.operation = op;
        }
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

        this.logInfo("Transition to: %s", context.creationStage);
        switch (context.creationStage) {
        case GET_RELATED_COMPUTE_DESCRIPTIONS:
            getRelatedComputeDescriptions(context,
                    AWSComputeStateCreationStage.CREATE_TAGS);
            break;
        case CREATE_TAGS:
            createTags(context, AWSComputeStateCreationStage.CREATE_COMPUTESTATES_OPERATIONS);
            break;
        case CREATE_COMPUTESTATES_OPERATIONS:
            populateCreateOperations(context,
                    AWSComputeStateCreationStage.UPDATE_COMPUTESTATES_OPERATIONS);
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
            Throwable t = new IllegalArgumentException(
                    "Unknown AWS enumeration:compute state creation stage");
            finishWithFailure(context, t);
            break;
        }
    }

    private void createTags(
            AWSComputeStateCreationContext context,
            AWSComputeStateCreationStage next) {
        // Get all tags from the instances to be created and the instances to be updated
        Set<Tag> create = context.request.instancesToBeCreated.stream()
                .flatMap(i -> i.getTags().stream())
                .collect(Collectors.toSet());

        Set<Tag> update = context.request.instancesToBeUpdated.values().stream()
                .flatMap(i -> i.getTags().stream())
                .collect(Collectors.toSet());

        // Put them in a set to remove the duplicates
        Set<Tag> allTags = new HashSet<>();
        allTags.addAll(create);
        allTags.addAll(update);

        // POST each of the tags. If a tag exists it won't be created again. We don't want the name
        // tags, so filter them out
        List<Operation> operations = allTags.stream()
                .filter(t -> !AWSConstants.AWS_TAG_NAME.equals(t.getKey()))
                .map(t -> mapTagToTagState(t, context.request.tenantLinks))
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
                        logWarning("Failure retrieving query results: %s", e.toString());
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
                        logInfo(
                                "%d compute descriptions already exist in the system that match the supplied criteria. ",
                                context.computeDescriptionMap.size());
                    } else {
                        logInfo("No matching compute descriptions exist in the system.");
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
            logInfo("No instances need to be created in the local system");
            context.creationStage = next;
            handleComputeStateCreateOrUpdate(context);
        } else {
            logInfo("Need to CREATE %d compute states in the local system",
                    context.request.instancesToBeCreated.size());

            for (int i = 0; i < context.request.instancesToBeCreated.size(); i++) {

                Instance instance = context.request.instancesToBeCreated.get(i);

                String zoneId = instance.getPlacement().getAvailabilityZone();
                ZoneData zoneData = context.request.zones.get(zoneId);
                String regionId = zoneData.regionId;

                InstanceDescKey descKey = InstanceDescKey.build(regionId, zoneId,
                        instance.getInstanceType());

                ComputeService.ComputeState computeStateToBeCreated = mapInstanceToComputeState(
                        instance,
                        context.request.parentComputeLink, zoneData.computeLink,
                        context.request.resourcePoolLink,
                        context.request.endpointLink,
                        context.computeDescriptionMap.get(descKey),
                        context.request.tenantLinks);
                computeStateToBeCreated.networkInterfaceLinks = new ArrayList<>();

                if (!AWSEnumerationUtils.instanceIsInStoppedState(instance)) {
                    // for each NIC create Description and State create operations. Link the
                    // ComputeState to be created to the NIC State
                    for (InstanceNetworkInterface awsNic : instance.getNetworkInterfaces()) {

                        final NetworkInterfaceDescription nicDescription;
                        {
                            nicDescription = new NetworkInterfaceDescription();
                            nicDescription.id = UUID.randomUUID().toString();
                            nicDescription.name = "nic-" + awsNic.getAttachment().getDeviceIndex()
                                    + "-desc";
                            nicDescription.assignment = IpAssignment.DYNAMIC;
                            nicDescription.deviceIndex = awsNic.getAttachment().getDeviceIndex();
                            // Link is set, because it's referenced by NICState before post
                            nicDescription.documentSelfLink = UUID.randomUUID().toString();
                            nicDescription.tenantLinks = context.request.tenantLinks;
                            nicDescription.endpointLink = context.request.endpointLink;

                            Operation postNetworkInterfaceDescription = createPostOperation(
                                    this, nicDescription,
                                    NetworkInterfaceDescriptionService.FACTORY_LINK);

                            context.enumerationOperations
                                    .add(postNetworkInterfaceDescription);
                        }

                        final NetworkInterfaceState nicState;
                        {
                            nicState = new NetworkInterfaceState();
                            nicState.id = awsNic.getNetworkInterfaceId();
                            nicState.name = nicState.id;
                            nicState.address = awsNic.getPrivateIpAddress();
                            nicState.subnetLink = context.request.enumeratedNetworks.subnets
                                    .get(awsNic.getSubnetId());
                            nicState.securityGroupLinks = new ArrayList<>();
                            nicState.tenantLinks = context.request.tenantLinks;
                            nicState.endpointLink = context.request.endpointLink;

                            for (GroupIdentifier awsSG : awsNic.getGroups()) {
                                // we should have updated the list of SG Ids before this step and
                                // should have ensured that all the SGs exist locally
                                nicState.securityGroupLinks
                                        .add(context.request.enumeratedSecurityGroups.securityGroupStates
                                                .get(awsSG.getGroupId()));
                            }

                            nicState.deviceIndex = nicDescription.deviceIndex;
                            nicState.networkInterfaceDescriptionLink = UriUtils
                                    .buildUriPath(
                                            NetworkInterfaceDescriptionService.FACTORY_LINK,
                                            nicDescription.documentSelfLink);
                            // Link is set, because it's referenced by CS before post
                            nicState.documentSelfLink = UUID.randomUUID().toString();

                            Operation postNetworkInterfaceState = createPostOperation(this, nicState,
                                    NetworkInterfaceService.FACTORY_LINK);

                            context.enumerationOperations
                                    .add(postNetworkInterfaceState);
                        }

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

    private void populateUpdateOperations(AWSComputeStateCreationContext context,
            AWSComputeStateCreationStage next) {
        if (context.request.instancesToBeUpdated == null
                || context.request.instancesToBeUpdated.size() == 0) {
            logInfo("No instances need to be updated in the local system");
            context.creationStage = next;
            handleComputeStateCreateOrUpdate(context);
        } else {
            logInfo("Need to UPDATE %d compute states in the local system",
                    context.request.instancesToBeUpdated.size());

            for (String instanceId : context.request.instancesToBeUpdated.keySet()) {

                Instance instance = context.request.instancesToBeUpdated.get(instanceId);

                // Update the ComputeState
                ComputeState existingComputeState = context.request.computeStatesToBeUpdated
                        .get(instanceId);
                String zoneId = instance.getPlacement().getAvailabilityZone();
                ZoneData zoneData = context.request.zones.get(zoneId);

                ComputeService.ComputeState computeStateToBeUpdated = mapInstanceToComputeState(instance,
                        context.request.parentComputeLink, zoneData.computeLink,
                        context.request.resourcePoolLink,
                        context.request.endpointLink,
                        existingComputeState.descriptionLink,
                        context.request.tenantLinks);
                computeStateToBeUpdated.documentSelfLink = existingComputeState.documentSelfLink;

                Operation patchComputeState = createPatchOperation(this,
                        computeStateToBeUpdated, computeStateToBeUpdated.documentSelfLink);

                context.enumerationOperations.add(patchComputeState);

                // The stopped or stopping instance does not have full network settings.
                if (!AWSEnumerationUtils.instanceIsInStoppedState(instance)) {

                    // Update the NetworkInterfaceStates for this ComputeState
                    List<NetworkInterfaceState> existingNicStates = context.request.nicStatesToBeUpdated
                            .get(instanceId);
                    if (existingNicStates != null) {
                        List<Operation> patchNICsOperations = createPatchNICsOperations(context, instance,
                                existingNicStates);

                        context.enumerationOperations.addAll(patchNICsOperations);
                    }
                }
            }

            context.creationStage = next;
            handleComputeStateCreateOrUpdate(context);
        }
    }

    /**
     * For each NetworkInterfaceState, obtain the corresponding AWS NIC, and generate POST operation to update its private address
     */
    private List<Operation> createPatchNICsOperations(AWSComputeStateCreationContext context,
            Instance instance, List<NetworkInterfaceState> nicStatesWithDesc) {

        List<Operation> updateNICsOperations = new ArrayList<>();

        for (InstanceNetworkInterface awsNic : instance.getNetworkInterfaces()) {

            // get existing NICState corresponding to this device index
            NetworkInterfaceState existingNicState = getNICStateByDeviceId(
                    nicStatesWithDesc, awsNic.getAttachment().getDeviceIndex());

            if (existingNicState != null) {

                // create a new NetworkInterfaceState for updating the address
                NetworkInterfaceState updateNicState = new NetworkInterfaceState();
                updateNicState.address = awsNic.getPrivateIpAddress();
                updateNicState.securityGroupLinks = new ArrayList<>();
                if (context.request.enumeratedSecurityGroups != null) {
                    for (GroupIdentifier awsSG : awsNic.getGroups()) {
                        // we should have updated the list of SG Ids before this step and should have
                        // ensured that all the SGs exist locally
                        updateNicState.securityGroupLinks.add(context.request.enumeratedSecurityGroups.securityGroupStates
                                            .get(awsSG.getGroupId()));
                    }
                }
                // create update operation
                Operation updateNicOperation = createPatchOperation(this, updateNicState,
                        existingNicState.documentSelfLink);
                updateNICsOperations.add(updateNicOperation);
            }
        }
        return updateNICsOperations;
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
            logInfo("There are no compute states or networks to be created");
            context.creationStage = next;
            handleComputeStateCreateOrUpdate(context);
            return;
        }
        OperationJoin.JoinedCompletionHandler joinCompletion = (ox,
                exc) -> {
            if (exc != null) {
                logSevere(
                        "Error creating a compute state and the associated network %s",
                        Utils.toString(exc));
                finishWithFailure(context, exc.values().iterator().next());
                return;
            }
            logInfo("Successfully created all the networks and compute states.");
            context.creationStage = next;
            handleComputeStateCreateOrUpdate(context);
        };
        OperationJoin joinOp = OperationJoin.create(context.enumerationOperations);
        joinOp.setCompletion(joinCompletion);
        joinOp.sendWith(getHost());
    }

    private void finishWithFailure(AWSComputeStateCreationContext context, Throwable exc) {
        context.operation.fail(exc);
        AdapterUtils.sendFailurePatchToEnumerationTask(this, context.request.parentTaskLink,
                exc);
    }

}
