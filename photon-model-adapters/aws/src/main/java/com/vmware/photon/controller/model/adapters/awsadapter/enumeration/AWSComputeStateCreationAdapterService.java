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
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSNetworkUtils.createOperationToUpdateOrCreateNetworkInterface;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSNetworkUtils.getExistingNetworkInterfaceLink;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSNetworkUtils.mapIPAddressToNetworkInterfaceState;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSNetworkUtils.mapInstanceIPAddressToNICCreationOperations;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSNetworkUtils.removeNetworkLinkAndDocument;
import static com.vmware.photon.controller.model.adapters.util.AdapterUtils.createPatchOperation;
import static com.vmware.photon.controller.model.adapters.util.AdapterUtils.createPostOperation;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Tag;

import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSNetworkStateCreationAdapterService.AWSNetworkEnumerationResponse;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.InstanceDescKey;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.ZoneData;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
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
        POPULATE_COMPUTESTATES,
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
        /**
         * Discovered/Enumerated networks in Amazon.
         */
        public AWSNetworkEnumerationResponse enumeratedNetworks;
        public Map<String, ZoneData> zones;
        public String resourcePoolLink;
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
        public Operation awsAdapterOperation;

        public AWSComputeStateCreationContext(AWSComputeStateCreationRequest request,
                Operation op) {
            this.request = request;
            this.enumerationOperations = new ArrayList<Operation>();
            this.computeDescriptionMap = new HashMap<>();
            this.creationStage = AWSComputeStateCreationStage.GET_RELATED_COMPUTE_DESCRIPTIONS;
            this.awsAdapterOperation = op;
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
        switch (context.creationStage) {
        case GET_RELATED_COMPUTE_DESCRIPTIONS:
            getRelatedComputeDescriptions(context,
                    AWSComputeStateCreationStage.CREATE_TAGS);
            break;
        case CREATE_TAGS:
            createTags(context, AWSComputeStateCreationStage.POPULATE_COMPUTESTATES);
            break;
        case POPULATE_COMPUTESTATES:
            populateOperations(context, AWSComputeStateCreationStage.CREATE_COMPUTESTATES);
            break;
        case CREATE_COMPUTESTATES:
            kickOffComputeStateCreation(context, AWSComputeStateCreationStage.SIGNAL_COMPLETION);
            break;
        case SIGNAL_COMPLETION:
            setOperationDurationStat(context.awsAdapterOperation);
            context.awsAdapterOperation.complete();
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
    private void populateOperations(AWSComputeStateCreationContext context,
            AWSComputeStateCreationStage next) {
        if (context.request.instancesToBeCreated == null
                || context.request.instancesToBeCreated.size() == 0) {
            logInfo("No instances need to be created in the local system");
        } else {
            logInfo("Need to create %d compute states in the local system",
                    context.request.instancesToBeCreated.size());
            for (int i = 0; i < context.request.instancesToBeCreated.size(); i++) {
                populateComputeStateAndNetworksForCreation(context,
                        context.request.instancesToBeCreated.get(i));
            }
        }
        if (context.request.instancesToBeUpdated == null
                || context.request.instancesToBeUpdated.size() == 0) {
            logInfo("No instances need to be updated in the local system");
        } else {
            logInfo("Need to update %d compute states in the local system",
                    context.request.instancesToBeUpdated.size());
            for (String instanceId : context.request.instancesToBeUpdated
                    .keySet()) {
                populateComputeStateAndNetworksForUpdates(context,
                        context.request.instancesToBeUpdated.get(instanceId),
                        context.request.computeStatesToBeUpdated.get(instanceId));
            }
        }
        context.creationStage = next;
        handleComputeStateCreateOrUpdate(context);

    }

    /**
     * Populates the compute state / network link associated with an AWS VM instance and creates an
     * operation for posting it.
     */
    private void populateComputeStateAndNetworksForCreation(
            AWSComputeStateCreationContext context,
            Instance instance) {
        String zoneId = instance.getPlacement().getAvailabilityZone();
        ZoneData zoneData = context.request.zones.get(zoneId);
        String regionId = zoneData.regionId;

        InstanceDescKey descKey = InstanceDescKey.build(regionId, zoneId,
                instance.getInstanceType());

        ComputeService.ComputeState computeState = mapInstanceToComputeState(instance,
                context.request.parentComputeLink, zoneData.computeLink,
                context.request.resourcePoolLink,
                context.computeDescriptionMap.get(descKey),
                context.request.tenantLinks);

        // Create operations
        List<Operation> networkOperations = mapInstanceIPAddressToNICCreationOperations(
                instance, computeState, context.request.tenantLinks, this, context.request.enumeratedNetworks);
        if (networkOperations != null && !networkOperations.isEmpty()) {
            context.enumerationOperations.addAll(networkOperations);
        }
        // Create operation for compute state once all the
        Operation postComputeState = createPostOperation(this, computeState,
                ComputeService.FACTORY_LINK);
        context.enumerationOperations.add(postComputeState);
    }

    /**
     * Populates the compute state / network link associated with an AWS VM instance and creates an
     * operation for PATCHing existing compute and network interfaces .
     */
    private void populateComputeStateAndNetworksForUpdates(AWSComputeStateCreationContext context,
            Instance instance, ComputeState existingComputeState) {
        String zoneId = instance.getPlacement().getAvailabilityZone();
        ZoneData zoneData = context.request.zones.get(zoneId);

        // Operation for update to compute state.
        ComputeService.ComputeState computeState = mapInstanceToComputeState(instance,
                context.request.parentComputeLink, zoneData.computeLink,
                context.request.resourcePoolLink,
                existingComputeState.descriptionLink,
                context.request.tenantLinks);

        computeState.networkInterfaceLinks = new ArrayList<String>();

        String existingNICLink = null;
        // NIC - Private
        if (instance.getPrivateIpAddress() != null) {
            existingNICLink = getExistingNetworkInterfaceLink(existingComputeState, false);
            NetworkInterfaceState privateNICState = mapIPAddressToNetworkInterfaceState(instance,
                    false, context.request.tenantLinks, existingNICLink, context.request.enumeratedNetworks);
            Operation privateNICOperation = createOperationToUpdateOrCreateNetworkInterface(
                    existingComputeState, privateNICState,
                    context.request.tenantLinks, this, false);
            context.enumerationOperations.add(privateNICOperation);
            computeState.networkInterfaceLinks.add(UriUtils.buildUriPath(
                    NetworkInterfaceService.FACTORY_LINK,
                    privateNICState.documentSelfLink));
        }

        // NIC - Public
        if (instance.getPublicIpAddress() != null) {
            existingNICLink = getExistingNetworkInterfaceLink(existingComputeState, true);
            NetworkInterfaceState publicNICState = mapIPAddressToNetworkInterfaceState(instance,
                    true, context.request.tenantLinks, existingNICLink, context.request.enumeratedNetworks);
            Operation privateNICOperation = createOperationToUpdateOrCreateNetworkInterface(
                    existingComputeState, publicNICState,
                    context.request.tenantLinks, this, true);
            context.enumerationOperations.add(privateNICOperation);
            computeState.networkInterfaceLinks.add(UriUtils.buildUriPath(
                    NetworkInterfaceService.FACTORY_LINK,
                    publicNICState.documentSelfLink));
        } else {
            existingNICLink = getExistingNetworkInterfaceLink(existingComputeState, true);
            if (existingNICLink != null) {
                // delete public network interface link and its document
                removeNetworkLinkAndDocument(this, existingComputeState,
                        existingNICLink, context.enumerationOperations);
            }
        }

        // Create operation for compute state once all the associated network entities are accounted
        // for.
        Operation patchComputeState = createPatchOperation(this,
                computeState, existingComputeState.documentSelfLink);
        context.enumerationOperations.add(patchComputeState);
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
        context.awsAdapterOperation.fail(exc);
        AdapterUtils.sendFailurePatchToEnumerationTask(this, context.request.parentTaskLink,
                exc);
    }

}
