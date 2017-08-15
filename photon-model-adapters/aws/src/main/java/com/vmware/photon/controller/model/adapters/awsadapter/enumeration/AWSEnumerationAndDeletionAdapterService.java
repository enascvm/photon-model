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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_INSTANCE_ID_PREFIX;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.getQueryResultLimit;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.getAWSNonTerminatedInstancesFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;

import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;

import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.util.ComputeEnumerateAdapterRequest;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.ComputeService.LifecycleState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

/**
 * Enumeration Adapter for the Amazon Web Services. Performs a list call to the AWS API and
 * reconciles the local state with the state on the remote system. It starts by looking at the local
 * state in the system. Queries the remote endpoint to check if the same instances exist there. In
 * case some items are found to be deleted on the remote endpoint then it goes ahead and deletes
 * them from the local system.
 *
 */
public class AWSEnumerationAndDeletionAdapterService extends StatelessService {
    public static final String SELF_LINK = AWSUriPaths.AWS_ENUMERATION_DELETION_ADAPTER;
    private AWSClientManager clientManager;

    public static enum AWSEnumerationDeletionStages {
        CLIENT,
        ENUMERATE,
        ERROR
    }

    public static enum AWSEnumerationDeletionSubStage {
        GET_LOCAL_RESOURCES,
        GET_REMOTE_RESOURCES,
        COMPARE,
        PROCESS_COMPUTE_STATES,
        GET_NEXT_PAGE,
        ENUMERATION_STOP
    }

    public AWSEnumerationAndDeletionAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.EC2);
    }

    /**
     * The enumeration service context that holds all the information needed to determine the list
     * of instances that need to be deleted from the system as they have been terminated from the
     * remote instance.
     */
    public static class EnumerationDeletionContext {
        public AmazonEC2AsyncClient amazonEC2Client;
        public ComputeEnumerateAdapterRequest request;
        public AuthCredentialsService.AuthCredentialsServiceState parentAuth;
        public ComputeStateWithDescription parentCompute;
        public AWSEnumerationDeletionStages stage;
        public AWSEnumerationDeletionSubStage subStage;
        public Throwable error;
        // Mapping of instance Id and the compute state that represents it in the local system.
        public Map<String, ComputeState> localInstanceIds;
        // Set of all the instance Ids of the non terminated instances on AWS
        public Set<String> remoteInstanceIds;
        // Map of Instance Ids and compute states that have to be deleted from the local system.
        public List<ComputeState> instancesToBeDeleted;
        public Operation operation;
        // The next page link for the next set of results to fetch from the local system.
        public String nextPageLink;
        public int pageNo = 0;

        public EnumerationDeletionContext(ComputeEnumerateAdapterRequest request,
                Operation op) {
            this.request = request;
            this.operation = op;
            this.parentAuth = request.parentAuth;
            this.parentCompute = request.parentCompute;
            this.localInstanceIds = new ConcurrentSkipListMap<>();
            this.remoteInstanceIds = new HashSet<>();
            this.instancesToBeDeleted = new ArrayList<>();
            this.stage = AWSEnumerationDeletionStages.CLIENT;
            this.subStage = AWSEnumerationDeletionSubStage.GET_LOCAL_RESOURCES;
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
        EnumerationDeletionContext awsEnumerationContext = new EnumerationDeletionContext(
                op.getBody(ComputeEnumerateAdapterRequest.class), op);
        handleEnumerationRequestForDeletion(awsEnumerationContext);
    }

    /**
     * Handles the different steps required to process the local resources , get the corresponding
     * resources from the remote endpoint and delete the instances from the local system that do not
     * exist on the remote system any longer.
     */
    private void handleEnumerationRequestForDeletion(EnumerationDeletionContext aws) {
        switch (aws.stage) {
        case CLIENT:
            getAWSAsyncClient(aws, AWSEnumerationDeletionStages.ENUMERATE);
            break;
        case ENUMERATE:
            switch (aws.request.original.enumerationAction) {
            case START:
                logInfo(() -> String.format("Started deletion enumeration for %s",
                        aws.request.original.resourceReference));
                aws.request.original.enumerationAction = EnumerationAction.REFRESH;
                handleEnumerationRequestForDeletion(aws);
                break;
            case REFRESH:
                logInfo(() -> String.format("Running deletion enumeration in refresh mode for %s",
                        aws.parentCompute.description.environmentName));
                deleteResourcesInLocalSystem(aws);
                break;
            case STOP:
                logInfo(() -> String.format("Stopping deletion enumeration for %s",
                        aws.request.original.resourceReference));
                setOperationDurationStat(aws.operation);
                aws.operation.complete();
                break;
            default:
                logSevere(() -> String.format("Unknown AWS enumeration action %s",
                        aws.request.original.enumerationAction.toString()));
                Throwable t = new Exception("Unknown AWS enumeration action");
                signalErrorToEnumerationAdapter(aws, t);
                break;
            }
            break;
        case ERROR:
            aws.operation.fail(aws.error);
            break;
        default:
            logSevere(() -> String.format("Unknown AWS enumeration stage %s", aws.stage.toString()));
            Throwable t = new Exception("Unknown AWS enumeration stage");
            signalErrorToEnumerationAdapter(aws, t);
            break;
        }
    }

    /**
     * Method to instantiate the AWS Async client for future use
     */
    private void getAWSAsyncClient(EnumerationDeletionContext aws,
            AWSEnumerationDeletionStages next) {
        aws.amazonEC2Client = this.clientManager.getOrCreateEC2Client(aws.parentAuth,
                aws.request.regionId, this, (t) -> aws.error = t);
        if (aws.error != null) {
            aws.stage = AWSEnumerationDeletionStages.ERROR;
            handleEnumerationRequestForDeletion(aws);
            return;
        }
        OperationContext opContext = OperationContext.getOperationContext();
        AWSUtils.validateCredentials(aws.amazonEC2Client, this.clientManager, aws.parentAuth,
                aws.request, aws.operation, this,
                (describeAvailabilityZonesResult) -> {
                    aws.stage = next;
                    OperationContext.restoreOperationContext(opContext);
                    handleEnumerationRequestForDeletion(aws);
                },
                t -> {
                    OperationContext.restoreOperationContext(opContext);
                    aws.error = t;
                    aws.stage = AWSEnumerationDeletionStages.ERROR;
                    handleEnumerationRequestForDeletion(aws);
                });
    }

    /**
     * Uses the received enumeration information and compares it against it the state of the local
     * system and then tries to find and fix the gaps. At a high level this is the sequence of steps
     * that is followed: 1) Create a query to get the list of local compute states 2) Compare the
     * list of local resources against the list received from the AWS endpoint. 3) In case some
     * instances have been terminated on the AWS endpoint, mark those instances for deletion in the
     * local system. 4) Delete the compute state and network associated with that AWS instance from
     * the local system that have been terminated on AWS.
     *
     * @param aws
     */
    private void deleteResourcesInLocalSystem(EnumerationDeletionContext aws) {
        switch (aws.subStage) {
        case GET_LOCAL_RESOURCES:
            getLocalResources(aws,
                    AWSEnumerationDeletionSubStage.GET_REMOTE_RESOURCES);
            break;
        case GET_REMOTE_RESOURCES:
            getRemoteInstances(aws, AWSEnumerationDeletionSubStage.COMPARE);
            break;
        case COMPARE:
            compareResources(aws, AWSEnumerationDeletionSubStage.PROCESS_COMPUTE_STATES);
            break;
        case PROCESS_COMPUTE_STATES:
            if (aws.nextPageLink == null) {
                aws.subStage = AWSEnumerationDeletionSubStage.ENUMERATION_STOP;
            } else {
                aws.subStage = AWSEnumerationDeletionSubStage.GET_NEXT_PAGE;
            }

            if (aws.instancesToBeDeleted == null || aws.instancesToBeDeleted.size() == 0) {
                logFine(() -> "No local compute states found.");
                deleteResourcesInLocalSystem(aws);
                return;
            } else {
                if (aws.request.original.preserveMissing) {
                    retireComputeStates(aws);
                } else {
                    deleteComputeStates(aws);
                }
            }
            break;
        case GET_NEXT_PAGE:
            getNextPageFromLocalSystem(aws, AWSEnumerationDeletionSubStage.GET_REMOTE_RESOURCES);
            break;
        case ENUMERATION_STOP:
            logInfo(() -> "Stopping enumeration");
            stopEnumeration(aws);
            break;
        default:
            Throwable t = new Exception("Unknown AWS enumeration deletion sub stage");
            signalErrorToEnumerationAdapter(aws, t);
        }
    }

    /**
     * Get the list of compute states already known to the local system. Filter them by parent
     * compute link : AWS.
     */
    public void getLocalResources(EnumerationDeletionContext context,
            AWSEnumerationDeletionSubStage next) {
        // query all ComputeState resources known to the local system.
        logFine(() -> "Getting local resources that need to be reconciled with the AWS endpoint.");
        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK,
                        context.request.original.resourceLink())
                .addInClause(ComputeState.FIELD_NAME_LIFECYCLE_STATE,
                        Arrays.asList(LifecycleState.PROVISIONING.toString(),
                                LifecycleState.RETIRED.toString()),
                        Occurance.MUST_NOT_OCCUR);

        addScopeCriteria(qBuilder, ComputeState.class, context);

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(qBuilder.build())
                .addOption(QueryOption.EXPAND_CONTENT)
                .setResultLimit(getQueryResultLimit())
                .build();
        queryTask.tenantLinks = context.parentCompute.tenantLinks;

        // create the query to find resources
        QueryUtils.startQueryTask(this, queryTask)
                .whenComplete((qrt, e) -> {
                    if (e != null) {
                        logSevere(() -> String.format("Failure retrieving query results: %s",
                                e.toString()));
                        signalErrorToEnumerationAdapter(context, e);
                        return;
                    }

                    populateLocalInstanceInformationFromQueryResults(context, qrt);
                    logFine(() -> String.format("Got page No. %d of local resources. %d instances"
                                    + " found.", context.pageNo, qrt.results.documentCount));
                    context.subStage = next;
                    deleteResourcesInLocalSystem(context);
                });
    }

    /**
     * Populates the local instance information from the query results.
     */
    public QueryTask populateLocalInstanceInformationFromQueryResults(
            EnumerationDeletionContext context, QueryTask queryTask) {
        for (Object s : queryTask.results.documents.values()) {
            ComputeState localInstance = Utils.fromJson(s, ComputeState.class);
            if (!localInstance.id.startsWith(AWS_INSTANCE_ID_PREFIX)) {
                continue;
            }
            context.localInstanceIds.put(localInstance.id, localInstance);
        }
        context.pageNo++;
        context.nextPageLink = queryTask.results.nextPageLink;
        logFine(() -> String.format("Next page link %s", context.nextPageLink));
        return queryTask;
    }

    /**
     * Get the instances from AWS filtered by the instances Ids known to the local system.
     */
    public void getRemoteInstances(EnumerationDeletionContext aws,
            AWSEnumerationDeletionSubStage next) {
        if (aws.localInstanceIds == null || aws.localInstanceIds.size() == 0) {
            logFine(() -> "No local records found. No states need to be fetched from the AWS"
                    + " endpoint.");
            aws.subStage = next;
            deleteResourcesInLocalSystem(aws);
            return;
        }
        DescribeInstancesRequest request = new DescribeInstancesRequest();
        Filter runningInstanceFilter = getAWSNonTerminatedInstancesFilter();
        request.getFilters().add(runningInstanceFilter);
        // Get only the instances from the remote system for which a compute state exists in the
        // local system.
        logFine(() -> String.format("Fetching instance details for %d instances on the AWS"
                        + " endpoint.", aws.localInstanceIds.keySet().size()));
        request.getInstanceIds().addAll(new ArrayList<>(aws.localInstanceIds.keySet()));
        AsyncHandler<DescribeInstancesRequest, DescribeInstancesResult> resultHandler =
                new AWSEnumerationAsyncHandler(this, aws, next);
        aws.amazonEC2Client.describeInstancesAsync(request,
                resultHandler);
    }

    /**
     * The async handler to handle the success and errors received after invoking the describe
     * instances API on AWS
     */
    public static class AWSEnumerationAsyncHandler implements
            AsyncHandler<DescribeInstancesRequest, DescribeInstancesResult> {

        private StatelessService service;
        private EnumerationDeletionContext aws;
        public AWSEnumerationDeletionSubStage next;
        private OperationContext opContext;

        private AWSEnumerationAsyncHandler(StatelessService service,
                EnumerationDeletionContext aws, AWSEnumerationDeletionSubStage next) {
            this.service = service;
            this.aws = aws;
            this.next = next;
            this.opContext = OperationContext.getOperationContext();
        }

        @Override
        public void onError(Exception exception) {
            OperationContext.restoreOperationContext(this.opContext);
            this.service.logSevere(exception);
            this.aws.operation.fail(exception);
        }

        @Override
        public void onSuccess(DescribeInstancesRequest request,
                DescribeInstancesResult result) {
            OperationContext.restoreOperationContext(this.opContext);
            int totalNumberOfInstances = 0;
            // Print the details of the instances discovered on the AWS endpoint
            for (Reservation r : result.getReservations()) {
                for (Instance i : r.getInstances()) {
                    ++totalNumberOfInstances;
                    final int finalTotal1 = totalNumberOfInstances;
                    this.service.logFine(() -> String.format("%d=====Instance details %s =====",
                            finalTotal1, i.getInstanceId()));
                    this.aws.remoteInstanceIds.add(i.getInstanceId());
                }
            }
            final int finalTotal2 = totalNumberOfInstances;
            this.service.logFine(() -> String.format("Successfully enumerated %d instances on the"
                            + " AWS host.", finalTotal2));
            this.aws.subStage = this.next;
            ((AWSEnumerationAndDeletionAdapterService) this.service)
                    .deleteResourcesInLocalSystem(this.aws);
            return;
        }
    }

    /**
     * Compares the state between what is known to the local system and what is retrieved from AWS.
     * If some instances are terminated on the AWS endpoint then they are marked for deletion on the
     * local system.
     */
    public void compareResources(EnumerationDeletionContext aws,
            AWSEnumerationDeletionSubStage next) {
        // No local resources
        if (aws.localInstanceIds == null || aws.localInstanceIds.size() == 0) {
            logFine(() -> "No local resources found. Nothing to delete.");
            // No remote instances
        } else if (aws.remoteInstanceIds == null || aws.remoteInstanceIds.size() == 0) {
            logFine(() -> "No resources discovered on the cloud. Delete stale local resources.");
            aws.instancesToBeDeleted.addAll(aws.localInstanceIds.values());
            logFine(() -> String.format("====Deleting compute state for instance Ids %s ====",
                    aws.localInstanceIds.keySet().toString()));
        } else { // compare and mark the instances for deletion that have been terminated from the
                 // AWS endpoint.
            for (String key : aws.localInstanceIds.keySet()) {
                if (!aws.remoteInstanceIds.contains(key)) {
                    aws.instancesToBeDeleted.add(aws.localInstanceIds.get(key));
                    logFine(() -> String.format("====Deleting compute state for instance Id %s ====",
                            key));
                }
            }
            logFine(() -> String.format("%d local instances to be deleted as they were terminated on the AWS endpoint.",
                    aws.instancesToBeDeleted.size()));
        }
        aws.subStage = next;
        deleteResourcesInLocalSystem(aws);
        return;
    }

    /**
     * Creates operations for the deletion of all the compute states and networks from the local
     * system for which the AWS instance has been terminated from the remote instance. Kicks off the
     * deletion of all the identified compute states, also the networks and disks for which the
     * actual AWS instance has been terminated.
     */
    private void deleteComputeStates(EnumerationDeletionContext context) {

        List<Operation> deleteOperations = new ArrayList<>();
        // Create delete operations for the compute states that have to be deleted from the system.
        for (ComputeState computeStateToDelete : context.instancesToBeDeleted) {
            Operation deleteComputeStateOperation = Operation
                    .createDelete(this.getHost(), computeStateToDelete.documentSelfLink)
                    .setReferer(getHost().getUri());
            deleteOperations.add(deleteComputeStateOperation);
            // Create delete operations for all the network links associated with each of the
            // compute states.
            if (computeStateToDelete.networkInterfaceLinks != null) {
                for (String networkLinkToDelete : computeStateToDelete.networkInterfaceLinks) {
                    Operation deleteNetworkOperation = Operation
                            .createDelete(this.getHost(), networkLinkToDelete)
                            .setReferer(getHost().getUri());
                    deleteOperations.add(deleteNetworkOperation);
                }
            }

            // Create delete operations for all the disk links associated with each of the
            // compute states.
            if (computeStateToDelete.diskLinks != null) {
                for (String diskLinkToDelete : computeStateToDelete.diskLinks) {
                    Operation deleteDiskOperation = Operation
                            .createDelete(this.getHost(), diskLinkToDelete)
                            .setReferer(getHost().getUri());
                    deleteOperations.add(deleteDiskOperation);
                }
            }
        }
        // Kick off deletion operations with a join handler.
        if (deleteOperations == null || deleteOperations.size() == 0) {
            logFine(() -> "No compute states to be deleted.");
            deleteResourcesInLocalSystem(context);
            return;
        }
        OperationJoin.JoinedCompletionHandler joinCompletion = (ox,
                exc) -> {
            if (exc != null) {
                logSevere(() -> String.format("Failure deleting local compute states.",
                        Utils.toString(exc)));
                deleteResourcesInLocalSystem(context);
                return;

            }
            logFine(() -> "Deleted local compute states and networks.");
            deleteResourcesInLocalSystem(context);
            return;
        };
        OperationJoin joinOp = OperationJoin.create(deleteOperations);
        joinOp.setCompletion(joinCompletion);
        joinOp.sendWith(getHost());
    }

    /**
     * Creates operations to retire all the compute states in the local system for which the AWS
     * instance has been terminated/missing from the remote instance.
     */
    private void retireComputeStates(EnumerationDeletionContext context) {

        List<Operation> operations = new ArrayList<>();
        // Create patch operations for the compute states that have to be retired in the system.
        // We don't modify the lifecycle state for contained objects like NetworkInterfaces and
        // Disks.
        for (ComputeState cs : context.instancesToBeDeleted) {
            ComputeState cps = new ComputeState();
            cps.powerState = PowerState.OFF;
            cps.lifecycleState = LifecycleState.RETIRED;
            Operation operation = Operation
                    .createPatch(this.getHost(), cs.documentSelfLink)
                    .setBody(cps)
                    .setReferer(getHost().getUri());
            operations.add(operation);
        }
        // Kick off patch operations with a join handler.
        if (operations == null || operations.size() == 0) {
            logFine(() -> "No local compute states to be deleted.");
            deleteResourcesInLocalSystem(context);
            return;
        }
        OperationJoin.JoinedCompletionHandler joinCompletion = (ox,
                exc) -> {
            if (exc != null) {
                logSevere(() -> String.format("Failure retiring local compute states: %s ",
                        Utils.toString(exc)));
                deleteResourcesInLocalSystem(context);
                return;

            }
            logFine(() -> "Successfully retired local compute states.");
            deleteResourcesInLocalSystem(context);
            return;
        };
        OperationJoin joinOp = OperationJoin.create(operations);
        joinOp.setCompletion(joinCompletion);
        joinOp.sendWith(getHost());
    }

    /**
     * Signals Enumeration Stop to the AWS enumeration adapter. The AWS enumeration adapter will in
     * turn patch the parent task to indicate completion.
     */
    public void stopEnumeration(EnumerationDeletionContext aws) {
        aws.request.original.enumerationAction = EnumerationAction.STOP;
        handleEnumerationRequestForDeletion(aws);
    }

    /**
     * Signals error to the AWS enumeration adapter. The adapter will in turn clean up resources and
     * signal error to the parent task.
     */
    public void signalErrorToEnumerationAdapter(EnumerationDeletionContext aws, Throwable t) {
        aws.error = t;
        aws.stage = AWSEnumerationDeletionStages.ERROR;
        handleEnumerationRequestForDeletion(aws);
    }

    /**
     * Gets the next page from the local system for which the state has to be reconciled after
     * comparison with the remote AWS endpoint.
     */
    private void getNextPageFromLocalSystem(EnumerationDeletionContext context,
            AWSEnumerationDeletionSubStage next) {
        context.localInstanceIds.clear();
        context.remoteInstanceIds.clear();
        context.instancesToBeDeleted.clear();
        logFine(() -> "Getting next page of local records.");
        sendRequest(Operation
                .createGet(getHost(), context.nextPageLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere(() -> String.format("Failure retrieving next page from the local"
                                        + " system: %s", e.toString()));
                        signalErrorToEnumerationAdapter(context, e);
                        return;
                    }
                    QueryTask responseTask = populateLocalInstanceInformationFromQueryResults(context,
                            o.getBody(QueryTask.class));
                    logFine(() -> String.format("Got page No. %d of local resources. %d instances"
                                    + " in this page.", context.pageNo,
                            responseTask.results.documentCount));
                    context.subStage = next;
                    deleteResourcesInLocalSystem(context);
                }));
    }

    /**
     * Constrain every query with endpointLink/region and tenantLinks, if presented.
     */
    private static void addScopeCriteria(
            Query.Builder qBuilder,
            Class<? extends ResourceState> stateClass,
            EnumerationDeletionContext ctx) {

        // Add REGION criteria
        qBuilder.addFieldClause(ResourceState.FIELD_NAME_REGION_ID, ctx.request.regionId);
        // Add TENANT_LINKS criteria
        QueryUtils.addTenantLinks(qBuilder, ctx.parentCompute.tenantLinks);
        // Add ENDPOINT_LINK criteria
        QueryUtils.addEndpointLink(qBuilder, stateClass, ctx.request.original.endpointLink);
    }

}
