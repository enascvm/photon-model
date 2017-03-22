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

import static com.vmware.photon.controller.model.ComputeProperties.REGION_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.getQueryPageSize;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.getQueryResultLimit;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.getAWSNonTerminatedInstancesFilter;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.SOURCE_TASK_LINK;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.AvailabilityZone;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesRequest;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;

import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSComputeDescriptionEnumerationAdapterService.AWSComputeDescriptionCreationState;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSComputeStateCreationAdapterService.AWSComputeStateCreationRequest;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSNetworkStateEnumerationAdapterService.AWSNetworkEnumerationRequest;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSNetworkStateEnumerationAdapterService.AWSNetworkEnumerationResponse;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSSecurityGroupEnumerationAdapterService.AWSSecurityGroupEnumerationResponse;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSAsyncHandler;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSEnumerationUtils.ZoneData;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.ComputeEnumerateAdapterRequest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.tasks.QueryUtils;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;

import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

/**
 * Enumeration Adapter for the Amazon Web Services. Performs a list call to the AWS API and
 * reconciles the local state with the state on the remote system. It lists the instances on the
 * remote system. Compares those with the local system and creates the instances that are missing in
 * the local system.
 *
 */
public class AWSEnumerationAndCreationAdapterService extends StatelessService {
    public static final String SELF_LINK = AWSUriPaths.AWS_ENUMERATION_CREATION_ADAPTER;

    public enum AWSEnumerationCreationStages {
        CLIENT,
        ENUMERATE,
        ERROR
    }

    private AWSClientManager clientManager;

    public enum AWSComputeEnumerationCreationSubStage {
        QUERY_LOCAL_RESOURCES,
        COMPARE,
        CREATE_COMPUTE_DESCRIPTIONS,
        CREATE_COMPUTE_STATES,
        GET_NEXT_PAGE,
        ENUMERATION_STOP
    }

    private enum AWSEnumerationRefreshSubStage {
        ZONES,
        VCP,
        SECURITY_GROUP,
        COMPUTE,
        ERROR
    }

    public AWSEnumerationAndCreationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.EC2);
    }

    /**
     * The enumeration service context that holds all the information needed to determine the list
     * of instances that need to be represented in the system.
     */
    public static class EnumerationCreationContext {
        public AmazonEC2AsyncClient amazonEC2Client;
        public ComputeEnumerateAdapterRequest request;
        public AuthCredentialsService.AuthCredentialsServiceState parentAuth;
        public ComputeStateWithDescription parentCompute;
        public AWSEnumerationCreationStages stage;
        public AWSEnumerationRefreshSubStage refreshSubStage;
        public AWSComputeEnumerationCreationSubStage subStage;
        public Throwable error;
        public int pageNo;
        // Mapping of instance Id and the compute state in the local system.
        public Map<String, ComputeState> localAWSInstanceMap;
        public Map<String, Instance> remoteAWSInstances;
        public List<Instance> instancesToBeCreated;
        // Mappings of the instanceId ,the local compute state and the associated instance on AWS.
        public Map<String, Instance> instancesToBeUpdated;
        public Map<String, List<NetworkInterfaceState>> nicStatesToBeUpdated;
        public Map<String, ComputeState> computeStatesToBeUpdated;
        // The request object that is populated and sent to AWS to get the list of instances.
        public DescribeInstancesRequest describeInstancesRequest;
        // The async handler that works with the response received from AWS
        public AsyncHandler<DescribeInstancesRequest, DescribeInstancesResult> resultHandler;
        // The token to use to retrieve the next page of results from AWS. This value is null when
        // there are no more results to return.
        public String nextToken;
        public Operation operation;
        /**
         * Discovered/Enumerated networks in Amazon.
         */
        public AWSNetworkEnumerationResponse enumeratedNetworks;
        /**
         * Discovered/Enumerated security groups in Amazon
         */
        public AWSSecurityGroupEnumerationResponse enumeratedSecurityGroups;
        public Map<String, ZoneData> zones;

        public EnumerationCreationContext(ComputeEnumerateAdapterRequest request,
                Operation op) {
            this.operation = op;
            this.request = request;
            this.parentAuth = request.parentAuth;
            this.parentCompute = request.parentCompute;
            this.localAWSInstanceMap = new ConcurrentSkipListMap<>();
            this.instancesToBeUpdated = new ConcurrentSkipListMap<>();
            this.nicStatesToBeUpdated = new ConcurrentSkipListMap<>();
            this.computeStatesToBeUpdated = new ConcurrentSkipListMap<>();
            this.remoteAWSInstances = new ConcurrentSkipListMap<>();
            this.instancesToBeCreated = new ArrayList<>();
            this.zones = new HashMap<>();
            this.stage = AWSEnumerationCreationStages.CLIENT;
            this.refreshSubStage = AWSEnumerationRefreshSubStage.ZONES;
            this.subStage = AWSComputeEnumerationCreationSubStage.QUERY_LOCAL_RESOURCES;
            this.pageNo = 1;
        }
    }

    @Override
    public void handleStart(Operation startPost) {
        startHelperServices(startPost);
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
        EnumerationCreationContext awsEnumerationContext = new EnumerationCreationContext(
                op.getBody(ComputeEnumerateAdapterRequest.class), op);
        if (awsEnumerationContext.request.original.isMockRequest) {
            // patch status to parent task
            AdapterUtils.sendPatchToEnumerationTask(this,
                    awsEnumerationContext.request.original.taskReference);
            return;
        }
        handleEnumerationRequest(awsEnumerationContext);
    }

    /**
     * Starts the related services for the Enumeration Service
     */
    private void startHelperServices(Operation startPost) {
        Operation postAWScomputeDescriptionService = Operation
                .createPost(this.getHost(),
                        AWSComputeDescriptionEnumerationAdapterService.SELF_LINK)
                .setReferer(this.getUri());

        Operation postAWScomputeStateService = Operation
                .createPost(this.getHost(), AWSComputeStateCreationAdapterService.SELF_LINK)
                .setReferer(this.getUri());

        Operation postAWSNetworkStateService = Operation
                .createPost(this.getHost(), AWSNetworkStateEnumerationAdapterService.SELF_LINK)
                .setReferer(this.getUri());

        Operation postAWSSecurityGroupStateService = Operation
                .createPost(this.getHost(), AWSSecurityGroupEnumerationAdapterService.SELF_LINK)
                .setReferer(this.getUri());

        this.getHost().startService(postAWScomputeDescriptionService,
                new AWSComputeDescriptionEnumerationAdapterService());
        this.getHost().startService(postAWScomputeStateService,
                new AWSComputeStateCreationAdapterService());
        this.getHost().startService(postAWSNetworkStateService,
                new AWSNetworkStateEnumerationAdapterService());
        this.getHost().startService(postAWSSecurityGroupStateService,
                new AWSSecurityGroupEnumerationAdapterService());

        AdapterUtils.registerForServiceAvailability(getHost(),
                operation -> startPost.complete(), startPost::fail,
                AWSComputeDescriptionEnumerationAdapterService.SELF_LINK,
                AWSComputeStateCreationAdapterService.SELF_LINK,
                AWSNetworkStateEnumerationAdapterService.SELF_LINK,
                AWSSecurityGroupEnumerationAdapterService.SELF_LINK);
    }

    /**
     * Handles the different steps required to hit the AWS endpoint and get the set of resources
     * available and proceed to update the state in the local system based on the received data.
     */
    private void handleEnumerationRequest(EnumerationCreationContext aws) {
        switch (aws.stage) {
        case CLIENT:
            getAWSAsyncClient(aws, AWSEnumerationCreationStages.ENUMERATE);
            break;
        case ENUMERATE:
            switch (aws.request.original.enumerationAction) {
            case START:
                logInfo(() -> String.format("Started enumeration for creation for %s",
                        aws.request.original.resourceReference));
                aws.request.original.enumerationAction = EnumerationAction.REFRESH;
                handleEnumerationRequest(aws);
                break;
            case REFRESH:
                processRefreshSubStages(aws);
                break;
            case STOP:
                logInfo(() -> String.format("Stopping enumeration service for creation for %s",
                        aws.request.original.resourceReference));
                setOperationDurationStat(aws.operation);
                aws.operation.complete();
                break;
            default:
                break;
            }
            break;
        case ERROR:
            AdapterUtils.sendFailurePatchToEnumerationTask(this,
                    aws.request.original.taskReference, aws.error);
            break;
        default:
            logSevere(() -> String.format("Unknown AWS enumeration stage %s ",
                    aws.stage.toString()));
            aws.error = new Exception("Unknown AWS enumeration stage %s");
            AdapterUtils.sendFailurePatchToEnumerationTask(this,
                    aws.request.original.taskReference, aws.error);
            break;
        }
    }

    private void processRefreshSubStages(EnumerationCreationContext aws) {
        switch (aws.refreshSubStage) {
        case ZONES:
            collectAvailabilityZones(aws, AWSEnumerationRefreshSubStage.VCP);
            break;
        case VCP:
            refreshVPCInformation(aws, AWSEnumerationRefreshSubStage.SECURITY_GROUP);
            break;
        case SECURITY_GROUP:
            refreshSecurityGroupInformation(aws, AWSEnumerationRefreshSubStage.COMPUTE);
            break;
        case COMPUTE:
            if (aws.pageNo == 1) {
                logFine(() -> String.format("Running creation enumeration in refresh mode for %s",
                        aws.parentCompute.description.environmentName));
            }
            logFine(() -> String.format("Processing page %d ", aws.pageNo));
            aws.pageNo++;
            if (aws.describeInstancesRequest == null) {
                createAWSRequestAndAsyncHandler(aws);
            }
            aws.amazonEC2Client.describeInstancesAsync(aws.describeInstancesRequest,
                    aws.resultHandler);
            break;
        case ERROR:
            AdapterUtils.sendFailurePatchToEnumerationTask(this,
                    aws.request.original.taskReference, aws.error);
            break;
        default:
            logSevere(() -> String.format("Unknown AWS enumeration stage %s ",
                    aws.refreshSubStage.toString()));
            aws.error = new Exception("Unknown AWS enumeration stage");
            AdapterUtils.sendFailurePatchToEnumerationTask(this,
                    aws.request.original.taskReference, aws.error);
            break;
        }

    }

    /**
     * Method to instantiate the AWS Async client for future use
     */
    private void getAWSAsyncClient(EnumerationCreationContext aws,
            AWSEnumerationCreationStages next) {
        aws.amazonEC2Client = this.clientManager.getOrCreateEC2Client(aws.parentAuth,
                aws.request.regionId, this,
                aws.request.original.taskReference, true);
        OperationContext opContext = OperationContext.getOperationContext();
        AWSUtils.validateCredentials(aws.amazonEC2Client, aws.request, aws.operation, this,
                (describeAvailabilityZonesResult) -> {
                    aws.stage = next;
                    OperationContext.restoreOperationContext(opContext);
                    handleEnumerationRequest(aws);
                });
    }

    /**
     * Initializes and saves a reference to the request object that is sent to AWS to get a page of
     * instances. Also saves an instance to the async handler that will be used to handle the
     * responses received from AWS. It sets the nextToken value in the request object sent to AWS
     * for getting the next page of results from AWS.
     */
    private void createAWSRequestAndAsyncHandler(EnumerationCreationContext aws) {
        DescribeInstancesRequest request = new DescribeInstancesRequest();
        Filter runningInstanceFilter = getAWSNonTerminatedInstancesFilter();
        request.getFilters().add(runningInstanceFilter);
        request.setMaxResults(getQueryPageSize());
        request.setNextToken(aws.nextToken);
        aws.describeInstancesRequest = request;
        aws.resultHandler = new AWSEnumerationAsyncHandler(this, aws);
    }

    private void refreshSecurityGroupInformation(EnumerationCreationContext aws,
            AWSEnumerationRefreshSubStage next) {
        ComputeEnumerateAdapterRequest sgEnumeration = new ComputeEnumerateAdapterRequest(
                aws.request.original,
                aws.parentAuth,
                aws.parentCompute, aws.request.regionId
        );
        Operation patchSGOperation = Operation
                .createPatch(this, AWSSecurityGroupEnumerationAdapterService.SELF_LINK)
                .setBody(sgEnumeration)
                .setReferer(UriUtils.buildUri(getHost().getPublicUri(), getSelfLink()));
        this.getHost()
                .sendWithDeferredResult(patchSGOperation,
                        AWSSecurityGroupEnumerationAdapterService.AWSSecurityGroupEnumerationResponse.class)
                .thenAccept(securityGroupEnumerationResponse -> {
                    logFine(() -> "Successfully enumerated security group states");
                    aws.enumeratedSecurityGroups = securityGroupEnumerationResponse;
                    aws.refreshSubStage = next;
                    processRefreshSubStages(aws);
                })
                .exceptionally(throwable -> {
                    if (throwable != null) {
                        logWarning(() -> String.format("Failed to enumerate security groups: %s ",
                                throwable.getLocalizedMessage()));
                        aws.error = throwable;
                        aws.refreshSubStage = AWSEnumerationRefreshSubStage.ERROR;
                        processRefreshSubStages(aws);
                    }
                    return null;
                });
    }

    private void refreshVPCInformation(EnumerationCreationContext aws,
            AWSEnumerationRefreshSubStage next) {
        AWSNetworkEnumerationRequest networkEnumeration = new AWSNetworkEnumerationRequest();
        networkEnumeration.tenantLinks = aws.parentCompute.tenantLinks;
        networkEnumeration.parentAuth = aws.parentAuth;
        networkEnumeration.regionId = aws.request.regionId;
        networkEnumeration.request = aws.request.original;

        Operation patchNetworkOperation = Operation
                .createPatch(this, AWSNetworkStateEnumerationAdapterService.SELF_LINK)
                .setBody(networkEnumeration)
                .setReferer(UriUtils.buildUri(getHost().getPublicUri(), getSelfLink()));

        this.getHost()
                .sendWithDeferredResult(
                        patchNetworkOperation,
                        AWSNetworkStateEnumerationAdapterService.AWSNetworkEnumerationResponse.class)
                .thenAccept(networkResponse -> {
                    logFine(() -> "Successfully enumerated subnet states");
                    aws.enumeratedNetworks = networkResponse;
                    aws.refreshSubStage = next;
                    processRefreshSubStages(aws);
                })
                .exceptionally(throwable -> {
                    if (throwable != null) {
                        logWarning(() -> String.format("Failed to enumerate subnets: %s ",
                                throwable.getLocalizedMessage()));
                        aws.error = throwable;
                        aws.refreshSubStage = AWSEnumerationRefreshSubStage.ERROR;
                        processRefreshSubStages(aws);
                    }
                    return null;
                });
    }

    private void collectAvailabilityZones(EnumerationCreationContext aws,
            AWSEnumerationRefreshSubStage next) {
        DescribeAvailabilityZonesRequest azRequest = new DescribeAvailabilityZonesRequest();
        AWSAvailabilityZoneAsyncHandler asyncHandler = new AWSAvailabilityZoneAsyncHandler(this,
                aws, next);
        aws.amazonEC2Client.describeAvailabilityZonesAsync(azRequest, asyncHandler);
    }

    /**
     * The async handler to handle the success and errors received after invoking the describe
     * AvailabilityZones API on AWS
     */
    private static class AWSAvailabilityZoneAsyncHandler extends
            AWSAsyncHandler<DescribeAvailabilityZonesRequest, DescribeAvailabilityZonesResult> {

        private AWSEnumerationAndCreationAdapterService service;
        private EnumerationCreationContext context;
        private AWSEnumerationRefreshSubStage next;

        private AWSAvailabilityZoneAsyncHandler(AWSEnumerationAndCreationAdapterService service,
                EnumerationCreationContext context, AWSEnumerationRefreshSubStage next) {
            super();
            this.service = service;
            this.context = context;
            this.next = next;
        }

        @Override
        public void handleSuccess(DescribeAvailabilityZonesRequest request,
                DescribeAvailabilityZonesResult result) {

            List<AvailabilityZone> zones = result.getAvailabilityZones();
            if (zones == null || zones.isEmpty()) {
                this.service.logFine(() -> "No AvailabilityZones found. Nothing to be created locally");
                this.context.refreshSubStage = this.next;
                this.service.processRefreshSubStages(this.context);
                return;
            }

            loadLocalResources(this.service, this.context,
                    zones.stream()
                            .map(AvailabilityZone::getZoneName)
                            .collect(Collectors.toList()),
                    cm -> createMissingLocalInstances(zones, cm),
                    cm -> {
                        this.service.logFine(() -> "No AvailabilityZones found. Nothing to be"
                                + " created locally");
                        this.context.refreshSubStage = this.next;
                        this.service.processRefreshSubStages(this.context);
                    });
        }

        private void proceedWithRefresh() {
            this.context.refreshSubStage = this.next;
            this.service.processRefreshSubStages(this.context);
        }

        private void createMissingLocalInstances(List<AvailabilityZone> zones,
                Map<String, ComputeState> cm) {
            zones.stream()
                    .filter(z -> cm.containsKey(z.getZoneName()))
                    .forEach(z -> {
                        ComputeState c = cm.get(z.getZoneName());
                        this.context.zones.put(c.id,
                                ZoneData.build(this.context.request.regionId,
                                        c.id, c.documentSelfLink));
                    });
            List<Operation> descOps = zones.stream()
                    .filter(z -> !cm.containsKey(z.getZoneName()))
                    .map(this::createComputeDescription)
                    .map(cd -> Operation
                            .createPost(this.service, ComputeDescriptionService.FACTORY_LINK)
                            .setBody(cd))
                    .collect(Collectors.toList());

            if (descOps.isEmpty()) {
                proceedWithRefresh();
                return;
            }

            OperationJoin
                    .create(descOps)
                    .setCompletion(
                            (ops, exs) -> {
                                if (exs != null) {
                                    this.service
                                            .logSevere(() -> String.format("Error creating a"
                                                            + " compute descriptions for "
                                                            + "discovered AvailabilityZone: %s",
                                                    Utils.toString(exs)));
                                    AdapterUtils.sendFailurePatchToEnumerationTask(this.service,
                                            this.context.request.original.taskReference,
                                            exs.values().iterator().next());
                                    return;
                                }
                                List<Operation> computeOps = ops
                                        .values()
                                        .stream()
                                        .map(o -> o.getBody(ComputeDescription.class))
                                        .map(this::createComputeInstanceForAvailabilityZone)
                                        .map(c -> Operation
                                                .createPost(this.service,
                                                        ComputeService.FACTORY_LINK)
                                                .setBody(c))
                                        .collect(Collectors.toList());

                                invokeComputeOps(computeOps);
                            })
                    .sendWith(this.service);
        }

        private void invokeComputeOps(List<Operation> computeOps) {
            if (computeOps.isEmpty()) {
                proceedWithRefresh();
                return;
            }

            OperationJoin.create(computeOps)
                    .setCompletion((ops, exs) -> {
                        if (exs != null) {
                            this.service.logSevere(() -> String.format("Error creating a compute"
                                            + " states for discovered AvailabilityZone: %s",
                                    Utils.toString(exs)));
                            AdapterUtils.sendFailurePatchToEnumerationTask(this.service,
                                    this.context.request.original.taskReference,
                                    exs.values().iterator().next());
                            return;
                        }
                        ops.values().stream()
                                .map(o -> o.getBody(ComputeState.class))
                                .forEach(c -> this.context.zones.put(c.id,
                                        ZoneData.build(
                                                this.context.request.regionId,
                                                c.id, c.documentSelfLink)));
                        proceedWithRefresh();
                    })
                    .sendWith(this.service);
        }

        private ComputeState createComputeInstanceForAvailabilityZone(ComputeDescription cd) {
            ComputeService.ComputeState computeState = new ComputeService.ComputeState();
            computeState.name = cd.name;
            computeState.id = cd.id;
            computeState.adapterManagementReference =
                    this.context.parentCompute.adapterManagementReference;
            computeState.parentLink = this.context.parentCompute.documentSelfLink;
            computeState.resourcePoolLink = this.context.request.original.resourcePoolLink;
            computeState.endpointLink = this.context.request.original.endpointLink;
            computeState.descriptionLink = cd.documentSelfLink;
            computeState.type = ComputeType.ZONE;
            computeState.environmentName = ComputeDescription.ENVIRONMENT_NAME_AWS;

            computeState.powerState = PowerState.ON;

            computeState.customProperties = this.context.parentCompute.customProperties;
            if (computeState.customProperties == null) {
                computeState.customProperties = new HashMap<>();
            }
            computeState.customProperties.put(REGION_ID, this.context.request.regionId);
            computeState.customProperties.put(SOURCE_TASK_LINK,
                    ResourceEnumerationTaskService.FACTORY_LINK);

            computeState.tenantLinks = this.context.parentCompute.tenantLinks;
            return computeState;
        }

        private ComputeDescription createComputeDescription(AvailabilityZone z) {
            ComputeDescriptionService.ComputeDescription cd = Utils
                    .clone(this.context.parentCompute.description);
            cd.documentSelfLink = null;
            cd.id = z.getZoneName();
            cd.zoneId = z.getZoneName();
            cd.name = z.getZoneName();
            cd.endpointLink = this.context.request.original.endpointLink;
            // Book keeping information about the creation of the compute description in the system.
            if (cd.customProperties == null) {
                cd.customProperties = new HashMap<>();
            }
            cd.customProperties.put(SOURCE_TASK_LINK,
                    ResourceEnumerationTaskService.FACTORY_LINK);

            return cd;
        }

        @Override
        protected void handleError(Exception exception) {
            AdapterUtils.sendFailurePatchToEnumerationTask(this.service,
                    this.context.request.original.taskReference,
                    exception);
        }

    }

    /**
     * The async handler to handle the success and errors received after invoking the describe
     * instances API on AWS
     */
    public static class AWSEnumerationAsyncHandler implements
            AsyncHandler<DescribeInstancesRequest, DescribeInstancesResult> {

        private AWSEnumerationAndCreationAdapterService service;
        private EnumerationCreationContext context;
        private OperationContext opContext;

        private AWSEnumerationAsyncHandler(AWSEnumerationAndCreationAdapterService service,
                EnumerationCreationContext context) {
            this.service = service;
            this.context = context;
            this.opContext = OperationContext.getOperationContext();
        }

        @Override
        public void onError(Exception exception) {
            OperationContext.restoreOperationContext(this.opContext);
            AdapterUtils.sendFailurePatchToEnumerationTask(this.service,
                    this.context.request.original.taskReference,
                    exception);
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
                    this.context.remoteAWSInstances.put(i.getInstanceId(), i);
                }
            }
            final int finalTotal2 = totalNumberOfInstances;
            this.service.logFine(() -> String.format("Successfully enumerated %d instances on the"
                            + " AWS host", finalTotal2));
            // Save the reference to the next token that will be used to retrieve the next page of
            // results from AWS.
            this.context.nextToken = result.getNextToken();
            // Since there is filtering of resources at source, there can be a case when no
            // resources are returned from AWS.
            if (this.context.remoteAWSInstances.size() == 0) {
                if (this.context.nextToken != null) {
                    this.context.subStage = AWSComputeEnumerationCreationSubStage.GET_NEXT_PAGE;
                } else {
                    this.context.subStage = AWSComputeEnumerationCreationSubStage.ENUMERATION_STOP;
                }
            }
            handleReceivedEnumerationData();
        }

        /**
         * Uses the received enumeration information and compares it against it the state of the
         * local system and then tries to find and fix the gaps. At a high level this is the
         * sequence of steps that is followed: 1) Create a query to get the list of local compute
         * states 2) Compare the list of local resources against the list received from the AWS
         * endpoint. 3) Create the instances not know to the local system. These are represented
         * using a combination of compute descriptions and compute states. 4) Find and create a
         * representative list of compute descriptions. 5) Create compute states to represent each
         * and every VM that was discovered on the AWS endpoint.
         */
        private void handleReceivedEnumerationData() {
            switch (this.context.subStage) {
            case QUERY_LOCAL_RESOURCES:
                getLocalResources(AWSComputeEnumerationCreationSubStage.COMPARE);
                break;
            case COMPARE:
                compareLocalStateWithEnumerationData(
                        AWSComputeEnumerationCreationSubStage.CREATE_COMPUTE_DESCRIPTIONS);
                break;
            case CREATE_COMPUTE_DESCRIPTIONS:
                if (this.context.instancesToBeCreated.size() > 0
                        || this.context.instancesToBeUpdated.size() > 0) {
                    createComputeDescriptions(
                            AWSComputeEnumerationCreationSubStage.CREATE_COMPUTE_STATES);
                } else {
                    if (this.context.nextToken == null) {
                        this.context.subStage = AWSComputeEnumerationCreationSubStage.ENUMERATION_STOP;
                    } else {
                        this.context.subStage = AWSComputeEnumerationCreationSubStage.GET_NEXT_PAGE;
                    }
                    handleReceivedEnumerationData();
                }
                break;
            case CREATE_COMPUTE_STATES:
                AWSComputeEnumerationCreationSubStage next;
                if (this.context.nextToken == null) {
                    next = AWSComputeEnumerationCreationSubStage.ENUMERATION_STOP;
                } else {
                    next = AWSComputeEnumerationCreationSubStage.GET_NEXT_PAGE;
                }
                createComputeStates(next);
                break;
            case GET_NEXT_PAGE:
                getNextPageFromEnumerationAdapter(
                        AWSComputeEnumerationCreationSubStage.QUERY_LOCAL_RESOURCES);
                break;
            case ENUMERATION_STOP:
                signalStopToEnumerationAdapter();
                break;
            default:
                Throwable t = new Exception("Unknown AWS enumeration sub stage");
                signalErrorToEnumerationAdapter(t);
            }
        }

        /**
         * Query the local data store and retrieve all the the compute states that exist filtered by
         * the instanceIds that are received in the enumeration data from AWS.
         */
        private void getLocalResources(AWSComputeEnumerationCreationSubStage next) {
            loadLocalResources(this.service, this.context, this.context.remoteAWSInstances.keySet(),
                    lcsm -> {
                        this.context.localAWSInstanceMap.putAll(lcsm);
                        this.context.subStage = next;
                        handleReceivedEnumerationData();
                    },
                    lcsm -> {
                        if (this.context.nextToken == null) {
                            this.service.logFine(() -> "Completed enumeration");
                            this.context.subStage = AWSComputeEnumerationCreationSubStage
                                    .ENUMERATION_STOP;
                        } else {
                            this.service.logFine(() -> "No remote resources found, proceeding to"
                                    + " next page");
                            this.context.subStage = AWSComputeEnumerationCreationSubStage
                                    .GET_NEXT_PAGE;
                        }
                        handleReceivedEnumerationData();
                    });

        }

        /**
         * Compares the local list of VMs against what is received from the AWS endpoint. Saves a
         * list of the VMs that have to be created in the local system to correspond to the remote
         * AWS endpoint.
         */
        private void compareLocalStateWithEnumerationData(
                AWSComputeEnumerationCreationSubStage next) {
            // No remote instances
            if (this.context.remoteAWSInstances == null
                    || this.context.remoteAWSInstances.size() == 0) {
                this.service.logFine(() -> "No remote resources found. Nothing to be created locally");
                // no local instances
            } else if (this.context.localAWSInstanceMap == null
                    || this.context.localAWSInstanceMap.size() == 0) {
                for (String key : this.context.remoteAWSInstances.keySet()) {
                    this.context.instancesToBeCreated.add(this.context.remoteAWSInstances.get(key));
                }
                // compare and add the ones that do not exist locally for creation. Mark others
                // for updates.
            } else {
                for (String key : this.context.remoteAWSInstances.keySet()) {
                    if (!this.context.localAWSInstanceMap.containsKey(key)) {
                        this.context.instancesToBeCreated
                                .add(this.context.remoteAWSInstances.get(key));
                        // A map of the local compute state id and the corresponding latest
                        // state on AWS
                    } else {
                        this.context.instancesToBeUpdated.put(key,
                                this.context.remoteAWSInstances.get(key));
                        this.context.computeStatesToBeUpdated.put(key,
                                this.context.localAWSInstanceMap.get(key));
                    }
                }
                queryAndCollectAllNICStatesToBeUpdated(next);
                return;
            }
            this.context.subStage = next;
            handleReceivedEnumerationData();
        }

        private void queryAndCollectAllNICStatesToBeUpdated(
                AWSComputeEnumerationCreationSubStage next) {

            List<DeferredResult<Void>> getNICsDR = this.context.computeStatesToBeUpdated
                    .entrySet()
                    .stream()
                    // Get those computes which have NICs assigned
                    .filter(en -> en.getValue().networkInterfaceLinks != null
                            && !en.getValue().networkInterfaceLinks.isEmpty())
                    // Merge NICs across all computes
                    .flatMap(
                            en -> {
                                this.context.nicStatesToBeUpdated.put(en.getKey(),
                                        new ArrayList<>());

                                Stream<DeferredResult<Void>> getNICsPerComputeDRs = en
                                        .getValue().networkInterfaceLinks
                                        .stream()
                                        .map(nicLink -> {
                                            Operation op = Operation.createGet(
                                                    this.service.getHost(), nicLink);

                                            return this.service
                                                    .sendWithDeferredResult(op,
                                                            NetworkInterfaceState.class)
                                                    .thenAccept(
                                                            nic -> this.context.nicStatesToBeUpdated
                                                                    .get(en.getKey()).add(nic));
                                        });
                                return getNICsPerComputeDRs;
                            })
                    .collect(Collectors.toList());
            if (getNICsDR.isEmpty()) {
                this.context.subStage = next;
                handleReceivedEnumerationData();
            } else {
                DeferredResult.allOf(getNICsDR).whenComplete((o, e) -> {
                    if (e != null) {
                        this.service.logSevere(() -> String.format("Failure querying"
                                + " NetworkInterfaceStates for update %s",Utils.toString(e)));
                    }
                    this.service.logFine(() -> String.format("Populated NICs to be updated: %s",
                            this.context.nicStatesToBeUpdated));
                    this.context.subStage = next;
                    handleReceivedEnumerationData();
                });
            }
        }

        /**
         * Posts a compute description to the compute description service for creation.
         */
        private void createComputeDescriptions(AWSComputeEnumerationCreationSubStage next) {
            this.service.logFine(() -> "Creating compute descriptions for enumerated VMs");
            AWSComputeDescriptionCreationState cd = new AWSComputeDescriptionCreationState();
            cd.instancesToBeCreated = this.context.instancesToBeCreated;
            cd.parentTaskLink = this.context.request.original.taskReference;
            cd.authCredentiaslLink = this.context.parentAuth.documentSelfLink;
            cd.tenantLinks = this.context.parentCompute.tenantLinks;
            cd.parentDescription = this.context.parentCompute.description;
            cd.endpointLink = this.context.request.original.endpointLink;
            cd.regionId = this.context.request.regionId;
            cd.zones = this.context.zones;

            this.service.sendRequest(Operation
                    .createPatch(this.service,
                            AWSComputeDescriptionEnumerationAdapterService.SELF_LINK)
                    .setBody(cd)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            this.service.logSevere(() -> String.format("Failure creating compute"
                                            + " descriptions %s", Utils.toString(e)));
                            signalErrorToEnumerationAdapter(e);
                        } else {
                            this.service.logFine(() -> "Successfully created compute descriptions.");
                            this.context.subStage = next;
                            handleReceivedEnumerationData();
                        }
                    }));
        }

        /**
         * Creates the compute states that represent the instances received from AWS during
         * enumeration.
         */
        private void createComputeStates(AWSComputeEnumerationCreationSubStage next) {
            this.service.logFine(() -> "Creating compute states for enumerated VMs");
            AWSComputeStateCreationRequest awsComputeState = new AWSComputeStateCreationRequest();
            awsComputeState.instancesToBeCreated = this.context.instancesToBeCreated;
            awsComputeState.instancesToBeUpdated = this.context.instancesToBeUpdated;
            awsComputeState.nicStatesToBeUpdated = this.context.nicStatesToBeUpdated;
            awsComputeState.computeStatesToBeUpdated = this.context.computeStatesToBeUpdated;
            awsComputeState.parentComputeLink = this.context.parentCompute.documentSelfLink;
            awsComputeState.resourcePoolLink = this.context.request.original.resourcePoolLink;
            awsComputeState.endpointLink = this.context.request.original.endpointLink;
            awsComputeState.parentTaskLink = this.context.request.original.taskReference;
            awsComputeState.tenantLinks = this.context.parentCompute.tenantLinks;
            awsComputeState.parentAuth = this.context.parentAuth;
            awsComputeState.regionId = this.context.request.regionId;
            awsComputeState.enumeratedNetworks = this.context.enumeratedNetworks;
            awsComputeState.enumeratedSecurityGroups = this.context.enumeratedSecurityGroups;
            awsComputeState.zones = this.context.zones;

            this.service.sendRequest(Operation
                    .createPatch(this.service, AWSComputeStateCreationAdapterService.SELF_LINK)
                    .setBody(awsComputeState)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            this.service.logSevere(() -> String.format("Failure creating compute"
                                            + " states %s", Utils.toString(e)));
                            signalErrorToEnumerationAdapter(e);
                        } else {
                            this.service.logFine(() -> "Successfully created compute states.");
                            this.context.subStage = next;
                            handleReceivedEnumerationData();
                        }
                    }));
        }

        /**
         * Signals Enumeration Stop to the AWS enumeration adapter. The AWS enumeration adapter will
         * in turn patch the parent task to indicate completion.
         */
        private void signalStopToEnumerationAdapter() {
            this.context.request.original.enumerationAction = EnumerationAction.STOP;
            this.service.handleEnumerationRequest(this.context);
        }

        /**
         * Signals error to the AWS enumeration adapter. The adapter will in turn clean up resources
         * and signal error to the parent task.
         */
        private void signalErrorToEnumerationAdapter(Throwable t) {
            this.context.error = t;
            this.context.stage = AWSEnumerationCreationStages.ERROR;
            this.service.handleEnumerationRequest(this.context);
        }

        /**
         * Calls the AWS enumeration adapter to get the next page from AWSs
         */
        private void getNextPageFromEnumerationAdapter(AWSComputeEnumerationCreationSubStage next) {
            // Reset all the results from the last page that was processed.
            this.context.remoteAWSInstances.clear();
            this.context.instancesToBeCreated.clear();
            this.context.instancesToBeUpdated.clear();
            this.context.nicStatesToBeUpdated.clear();
            this.context.computeStatesToBeUpdated.clear();
            this.context.localAWSInstanceMap.clear();
            this.context.describeInstancesRequest.setNextToken(this.context.nextToken);
            this.context.subStage = next;
            this.service.handleEnumerationRequest(this.context);
        }
    }

    /**
     * Signals error to the AWS enumeration adapter. The adapter will in turn clean up resources and
     * signal error to the parent task.
     */
    private static void signalErrorToEnumerationAdapter(Throwable t, EnumerationCreationContext aws,
            AWSEnumerationAndCreationAdapterService service) {
        aws.error = t;
        aws.stage = AWSEnumerationCreationStages.ERROR;
        service.handleEnumerationRequest(aws);
    }

    /**
     * Query the local data store and retrieve all the the compute states that exist filtered by the
     * instanceIds that are received in the enumeration data from AWS.
     */
    private static void loadLocalResources(AWSEnumerationAndCreationAdapterService service,
            EnumerationCreationContext context, Collection<String> remoteIds,
            Consumer<Map<String, ComputeState>> successHandler,
            Consumer<Map<String, ComputeState>> failureHandler) {
        // query all ComputeState resources for the cluster filtered by the received set of
        // instance Ids
        if (remoteIds == null || remoteIds.isEmpty()) {
            failureHandler.accept(null);
            return;
        }
        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK,
                        context.request.original.resourceLink())
                .addFieldClause(ComputeState.FIELD_NAME_RESOURCE_POOL_LINK,
                        context.request.original.resourcePoolLink)
                .addInClause(ResourceState.FIELD_NAME_ID, remoteIds);

        addScopeCriteria(qBuilder, ComputeState.class, context);

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(qBuilder.build())
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.TOP_RESULTS)
                .setResultLimit(getQueryResultLimit())
                .build();
        queryTask.tenantLinks = context.parentCompute.tenantLinks;

        service.logFine(() -> String.format("Created query for resources: " + remoteIds));
        QueryUtils.startQueryTask(service, queryTask)
                .whenComplete((qrt, e) -> {
                    if (e != null) {
                        service.logSevere(() -> String.format("Failure retrieving query results: %s",
                                e.toString()));
                        signalErrorToEnumerationAdapter(e, context, service);
                        return;
                    }
                    service.logFine(() -> String.format("%d compute states found",
                            qrt.results.documentCount));

                    Map<String, ComputeState> localInstances = new HashMap<>();
                    for (Object s : qrt.results.documents.values()) {
                        ComputeState localInstance = Utils.fromJson(s, ComputeState.class);
                        localInstances.put(localInstance.id, localInstance);
                    }

                    successHandler.accept(localInstances);
                });
    }

    /**
     * Constrain every query with endpointLink and tenantLinks, if presented.
     */
    private static void addScopeCriteria(Query.Builder qBuilder, Class<? extends ResourceState> stateClass, EnumerationCreationContext ctx) {
        // Add TENANT_LINKS criteria
        QueryUtils.addTenantLinks(qBuilder, ctx.parentCompute.tenantLinks);
        // Add ENDPOINT_LINK criteria
        QueryUtils.addEndpointLink(qBuilder, stateClass, ctx.request.original.endpointLink);
    }

}
