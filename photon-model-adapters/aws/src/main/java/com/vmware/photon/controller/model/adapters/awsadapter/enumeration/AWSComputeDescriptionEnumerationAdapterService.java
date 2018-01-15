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
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.SOURCE_TASK_LINK;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.amazonaws.services.ec2.model.Instance;

import org.apache.commons.lang3.StringUtils;

import com.vmware.photon.controller.model.adapters.awsadapter.AWSCostStatsService;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.InstanceDescKey;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.ZoneData;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Stateless service for the creation of compute descriptions that are discovered during the enumeration phase.
 * It first represents all the instances in a representative set of compute descriptions. Further checks if these
 * compute descriptions exist in the system. If they don't exist in the system then creates them in the local document store.
 */
public class AWSComputeDescriptionEnumerationAdapterService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_COMPUTE_DESCRIPTION_CREATION_ADAPTER;

    public enum AWSComputeDescCreationStage {
        GET_REPRESENTATIVE_LIST,
        QUERY_LOCAL_COMPUTE_DESCRIPTIONS,
        COMPARE,
        POPULATE_CREATE_COMPUTEDESC,
        POPULATE_UPDATE_COMPUTEDESC,
        CREATE_OR_UPDATE_COMPUTEDESC,
        SIGNAL_COMPLETION
    }

    public AWSComputeDescriptionEnumerationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    /**
     * Holds the list of instances for which compute descriptions have to be created.
     */
    public static class AWSComputeDescriptionCreationState {
        public List<Instance> instancesToBeCreated;
        public List<Instance> instancesToBeUpdated;
        public String regionId;
        public URI parentTaskLink;
        public String authCredentiaslLink;
        public boolean isMock;
        public List<String> tenantLinks;
        public Map<String, ZoneData> zones;
        public ComputeDescription parentDescription;
        public String endpointLink;
        public String parentComputeLink;
    }

    /**
     * The local service context that is created to identify and create a representative set of compute descriptions
     * that are required to be created in the system based on the enumeration data received from AWS.
     */
    public static class AWSComputeDescriptionCreationServiceContext {
        public List<Operation> enumerationOperations;
        public Map<InstanceDescKey, ComputeDescription> localComputeDescriptionMap;
        public Set<InstanceDescKey> representativeComputeDescriptionSet;
        public List<InstanceDescKey> computeDescriptionsToBeCreatedList;
        public List<InstanceDescKey> computeDescriptionsToBeUpdatedList;
        public AWSComputeDescCreationStage creationStage;
        public AWSComputeDescriptionCreationState cdState;
        // Cached operation to signal completion to the AWS instance adapter once all the compute
        // descriptions are successfully created.
        public Operation operation;

        public AWSComputeDescriptionCreationServiceContext(AWSComputeDescriptionCreationState cdState,
                Operation op) {
            this.cdState = cdState;
            this.localComputeDescriptionMap = new HashMap<>();
            this.representativeComputeDescriptionSet = new HashSet<>();
            this.computeDescriptionsToBeCreatedList = new ArrayList<>();
            this.computeDescriptionsToBeUpdatedList = new ArrayList<>();
            this.enumerationOperations = new ArrayList<>();
            this.creationStage = AWSComputeDescCreationStage.GET_REPRESENTATIVE_LIST;
            this.operation = op;
        }
    }

    @Override
    public void handlePatch(Operation op) {
        setOperationHandlerInvokeTimeStat(op);
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        AWSComputeDescriptionCreationState cdState = op.getBody(AWSComputeDescriptionCreationState.class);
        AWSComputeDescriptionCreationServiceContext context = new AWSComputeDescriptionCreationServiceContext(
                cdState, op);
        if (cdState.isMock) {
            op.complete();
        }
        handleComputeDescriptionCreation(context);
    }

    /**
     *
     */
    private void handleComputeDescriptionCreation(AWSComputeDescriptionCreationServiceContext context) {
        switch (context.creationStage) {
        case GET_REPRESENTATIVE_LIST:
            getRepresentativeListOfComputeDescriptions(context,
                    AWSComputeDescCreationStage.QUERY_LOCAL_COMPUTE_DESCRIPTIONS);
            break;
        case QUERY_LOCAL_COMPUTE_DESCRIPTIONS:
            if (context.representativeComputeDescriptionSet.size() > 0) {
                getLocalComputeDescriptions(context, AWSComputeDescCreationStage.COMPARE);
            } else {
                context.creationStage = AWSComputeDescCreationStage.SIGNAL_COMPLETION;
                handleComputeDescriptionCreation(context);
            }
            break;
        case COMPARE:
            compareLocalStateWithEnumerationData(context,
                    AWSComputeDescCreationStage.POPULATE_CREATE_COMPUTEDESC);
            break;
        case POPULATE_CREATE_COMPUTEDESC:
            populateCreateOperations(context,
                        AWSComputeDescCreationStage.POPULATE_UPDATE_COMPUTEDESC);
            break;
        case POPULATE_UPDATE_COMPUTEDESC:
            populateUpdateOperations(context,
                        AWSComputeDescCreationStage.CREATE_OR_UPDATE_COMPUTEDESC);
            break;
        case CREATE_OR_UPDATE_COMPUTEDESC:
            createOrUpdateComputeDescriptions(context, AWSComputeDescCreationStage.SIGNAL_COMPLETION);
            break;
        case SIGNAL_COMPLETION:
            setOperationDurationStat(context.operation);
            context.operation.complete();
            break;
        default:
            Throwable t = new IllegalArgumentException(
                    "Unknown AWS enumeration:compute description creation stage");
            finishWithFailure(context, t);
        }
    }

    /**
     * From the list of instances that are received from AWS arrive at the minimal set of compute descriptions that need
     * to be created locally to represent them.
     *
     * This logic basically tries to map n number of discovered instances to m compute descriptions.
     * The attributes of the instance considered to map to compute descriptions are the statically known template type
     * attributes that are expected to be fixed for the life of a virtual machine.
     *
     * These include
     * 1) Region Id
     * 2) Instance Type
     * 3) Network Id.
     *
     * Once the instances are mapped to these limited set of compute descriptions. Checks are performed to see if such compute descriptions
     * exist in the system. Else they are created.
     *
     * @param context
     * @param next
     */
    private void getRepresentativeListOfComputeDescriptions(
            AWSComputeDescriptionCreationServiceContext context, AWSComputeDescCreationStage next) {
        Collection<Instance> instanceList = new ArrayList<>();
        instanceList.addAll(context.cdState.instancesToBeCreated);
        instanceList.addAll(context.cdState.instancesToBeUpdated);
        context.representativeComputeDescriptionSet = getRepresentativeListOfCDsFromInstanceList(
                instanceList, context.cdState.zones);
        context.creationStage = next;
        handleComputeDescriptionCreation(context);
    }

    /**
     * Get all the compute descriptions already in the system and filtered by
     * - Supported Children (Docker Container)
     * - Environment name(AWS),
     * - Name (instance type),
     * - ZoneId(placement).
     * - Endpoint link
     */
    private void getLocalComputeDescriptions(AWSComputeDescriptionCreationServiceContext context,
            AWSComputeDescCreationStage next) {
        QueryTask queryTask = getCDsRepresentingVMsInLocalSystemCreatedByEnumerationQuery(
                context.representativeComputeDescriptionSet, context.cdState.tenantLinks,
                context.cdState.regionId, context.cdState.parentComputeLink, context.cdState.endpointLink);

        // create the query to find an existing compute description
        QueryUtils.startInventoryQueryTask(this, queryTask)
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
                            context.localComputeDescriptionMap.put(
                                    getKeyForComputeDescriptionFromCD(localComputeDescription),
                                    localComputeDescription);
                        }
                        logFine(() -> String.format("%d compute descriptions found",
                                context.localComputeDescriptionMap.size()));

                    } else {
                        logFine(() -> "No compute descriptions found");
                    }
                    context.creationStage = next;
                    handleComputeDescriptionCreation(context);
                });
    }

    /**
     *
    *Compares the locally known compute descriptions with the new list of compute descriptions to be created.
    *Identifies only the ones that do not exist locally and need to be created.
     *
    * @param context The compute description service context to be used for the creation of the compute descriptions.
    * @param next The next stage in the workflow for the compute description creation.
     */
    private void compareLocalStateWithEnumerationData(AWSComputeDescriptionCreationServiceContext context,
            AWSComputeDescCreationStage next) {
        if (context.representativeComputeDescriptionSet == null
                || context.representativeComputeDescriptionSet.size() == 0) {
            logFine(() -> "No new computes discovered on the remote system");
        } else if (context.localComputeDescriptionMap == null
                || context.localComputeDescriptionMap.size() == 0) {
            logFine(() -> "No local compute descriptions found");

            context.representativeComputeDescriptionSet
                    .forEach(cd -> context.computeDescriptionsToBeCreatedList.add(cd));
        } else { // compare and add the ones that do not exist locally
            context.representativeComputeDescriptionSet.stream()
                    .filter(d -> !context.localComputeDescriptionMap.containsKey(d))
                    .forEach(d -> context.computeDescriptionsToBeCreatedList.add(d));

            logFine(() -> String.format("%d additional compute descriptions need to be created",
                    context.computeDescriptionsToBeCreatedList.size()));

            context.representativeComputeDescriptionSet.stream()
                    .filter(d -> context.localComputeDescriptionMap.containsKey(d))
                    .forEach(d -> context.computeDescriptionsToBeUpdatedList.add(d));

            logFine(() -> String.format("%d additional compute descriptions need to be updated",
                    context.computeDescriptionsToBeUpdatedList.size()));

        }
        context.creationStage = next;
        handleComputeDescriptionCreation(context);
    }

    /**
     * Method to create compute descriptions associated with the instances received from the AWS host.
     */
    private void populateCreateOperations(AWSComputeDescriptionCreationServiceContext context,
            AWSComputeDescCreationStage next) {
        if (context.computeDescriptionsToBeCreatedList == null
                || context.computeDescriptionsToBeCreatedList.isEmpty()) {
            logFine(() -> "No local compute descriptions need to be created");
            context.creationStage = next;
            handleComputeDescriptionCreation(context);
            return;
        }
        logFine(() -> String.format("Need to create %d local compute descriptions",
                context.computeDescriptionsToBeCreatedList.size()));
        context.computeDescriptionsToBeCreatedList.stream()
                .map(dk -> createComputeDescriptionOperation(dk, context.cdState))
                .forEach(o -> context.enumerationOperations.add(o));

        context.creationStage = next;
        handleComputeDescriptionCreation(context);
    }

    private void populateUpdateOperations(AWSComputeDescriptionCreationServiceContext context,
                                          AWSComputeDescCreationStage next) {
        if (context.computeDescriptionsToBeUpdatedList == null
                || context.computeDescriptionsToBeUpdatedList.isEmpty()) {
            logFine(() -> "No local compute descriptions need to be updated");
            context.creationStage = next;
            handleComputeDescriptionCreation(context);
            return;
        }
        logFine(() -> String.format("Need to update %d local compute descriptions",
                context.computeDescriptionsToBeUpdatedList.size()));

        context.computeDescriptionsToBeUpdatedList.stream().forEach(entry -> {
            ComputeDescription cd = context.localComputeDescriptionMap.get(entry);
            if (StringUtils.isEmpty(cd.endpointLink)) {
                cd.endpointLink = context.cdState.endpointLink;
                sendRequest(Operation.createPatch(this.getHost(), cd.documentSelfLink)
                        .setReferer(this.getHost().getUri())
                        .setBody(cd));
            }
        });

        context.computeDescriptionsToBeUpdatedList.stream()
                .map(dk -> updateComputeDescriptionOperation(dk, context))
                .forEach(o -> context.enumerationOperations.add(o));

        context.creationStage = next;
        handleComputeDescriptionCreation(context);
    }

    private Operation updateComputeDescriptionOperation(InstanceDescKey cd,
                                                        AWSComputeDescriptionCreationServiceContext context) {

        Map<String, Collection<Object>> collectionsToAddMap = Collections.singletonMap
                (ComputeDescription.FIELD_NAME_ENDPOINT_LINKS,
                        Collections.singletonList(context.cdState.endpointLink));

        ServiceStateCollectionUpdateRequest updateEndpointLinksRequest = ServiceStateCollectionUpdateRequest
                .create(collectionsToAddMap, null);

        return Operation.createPatch(this.getHost(), context.localComputeDescriptionMap.get(cd).documentSelfLink)
                .setReferer(this.getHost().getUri())
                .setBody(updateEndpointLinksRequest);
    }

    /**
     * Creates a compute description based on the VM instance information received from AWS. Futher creates an operation
     * that will post to the compute description service for the creation of the compute description.
     */
    private Operation createComputeDescriptionOperation(InstanceDescKey cd,
            AWSComputeDescriptionCreationState cdState) {
        // Create a compute description for the AWS instance at hand
        ComputeDescriptionService.ComputeDescription computeDescription = new ComputeDescriptionService.ComputeDescription();
        computeDescription.instanceAdapterReference = AdapterUriUtil.buildAdapterUri(getHost(),
                AWSUriPaths.AWS_INSTANCE_ADAPTER);
        computeDescription.enumerationAdapterReference = AdapterUriUtil.buildAdapterUri(getHost(),
                AWSUriPaths.AWS_ENUMERATION_CREATION_ADAPTER);
        computeDescription.statsAdapterReference = AdapterUriUtil.buildAdapterUri(getHost(),
                AWSUriPaths.AWS_STATS_ADAPTER);
        computeDescription.diskAdapterReference = AdapterUriUtil.buildAdapterUri(getHost(),
                AWSUriPaths.AWS_DISK_ADAPTER);
        // We don't want cost adapter to run for each instance. Remove it from the list of stats adapter.
        if (cdState.parentDescription.statsAdapterReferences != null) {
            computeDescription.statsAdapterReferences = cdState.parentDescription.statsAdapterReferences
                    .stream().filter(uri -> !uri.getPath().endsWith(AWSCostStatsService.SELF_LINK))
                    .collect(Collectors.toSet());
        }

        computeDescription.environmentName = ComputeDescription.ENVIRONMENT_NAME_AWS;
        computeDescription.zoneId = cd.zoneId;
        computeDescription.regionId = cd.regionId;
        computeDescription.id = cd.instanceType;
        computeDescription.instanceType = cd.instanceType;
        computeDescription.name = cd.instanceType;
        computeDescription.endpointLink = cdState.endpointLink;
        if (computeDescription.endpointLinks == null) {
            computeDescription.endpointLinks = new HashSet<String>();
        }
        computeDescription.endpointLinks.add(cdState.endpointLink);
        computeDescription.tenantLinks = cdState.tenantLinks;
        // Book keeping information about the creation of the compute description in the system.
        computeDescription.customProperties = new HashMap<>();
        computeDescription.customProperties.put(SOURCE_TASK_LINK,
                ResourceEnumerationTaskService.FACTORY_LINK);
        computeDescription.computeHostLink = cdState.parentComputeLink;

        // security group is not being returned currently in the VM. Add additional logic VSYM-326.

        return Operation.createPost(this, ComputeDescriptionService.FACTORY_LINK)
                .setBody(computeDescription)
                .setReferer(getHost().getUri());
    }

    /**
     * Kicks off the creation of all the identified compute descriptions and creates a join handler to handle the successful
     * completion of of those operations. Once all the compute descriptions are successfully created moves the state machine
     * to the next stage.
     */
    private void createOrUpdateComputeDescriptions(AWSComputeDescriptionCreationServiceContext context,
            AWSComputeDescCreationStage next) {
        if (context.enumerationOperations == null || context.enumerationOperations.size() == 0) {
            logFine(() -> "No compute descriptions to be created");
            context.creationStage = next;
            handleComputeDescriptionCreation(context);
            return;
        }
        OperationJoin.JoinedCompletionHandler joinCompletion = (ox,
                exc) -> {
            if (exc != null) {
                logSevere(() -> String.format("Failure creating compute descriptions: %s",
                        Utils.toString(exc)));
                finishWithFailure(context, exc.values().iterator().next());
                return;
            }
            logFine(() -> "Successfully created compute descriptions");
            context.creationStage = next;
            handleComputeDescriptionCreation(context);
        };
        OperationJoin joinOp = OperationJoin.create(context.enumerationOperations);
        joinOp.setCompletion(joinCompletion);
        joinOp.sendWith(getHost());
    }

    private void finishWithFailure(AWSComputeDescriptionCreationServiceContext context, Throwable exc) {
        context.operation.fail(exc);
    }

}
