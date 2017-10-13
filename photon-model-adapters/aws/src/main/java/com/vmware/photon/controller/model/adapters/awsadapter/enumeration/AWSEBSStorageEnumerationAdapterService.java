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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_TAG_NAME;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.DISK_ENCRYPTED_FLAG;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.DISK_IOPS;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.SNAPSHOT_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.STORAGE_TYPE_EBS;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.VOLUME_TYPE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.VOLUME_TYPE_GENERAL_PURPOSED_SSD;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.VOLUME_TYPE_PROVISIONED_SSD;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.getQueryPageSize;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.getQueryResultLimit;
import static com.vmware.photon.controller.model.adapters.util.AdapterUtils.createPatchOperation;
import static com.vmware.photon.controller.model.adapters.util.AdapterUtils.createPostOperation;
import static com.vmware.photon.controller.model.adapters.util.AdapterUtils.getDeletionState;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.SOURCE_TASK_LINK;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.TAG_KEY_TYPE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.DescribeVolumesRequest;
import com.amazonaws.services.ec2.model.DescribeVolumesResult;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Volume;

import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSResourceType;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.util.ComputeEnumerateAdapterRequest;
import com.vmware.photon.controller.model.adapters.util.TagsUtil;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskStatus;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.NumericRange;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

/**
 * EBS Storage Enumeration Adapter for the Amazon Web Services.
 * - Performs a list call to the EBS list volumes API and reconciles the local state with the state on the remote system.
 * - It lists the volumes on the remote system. Compares those with the local system and creates or updates
 * the volumes that are missing in the local system. In the local system each EBS volume is mapped to a disk state.
 */
public class AWSEBSStorageEnumerationAdapterService extends StatelessService {
    public static final String SELF_LINK = AWSUriPaths.AWS_EBS_STORAGE_ENUMERATION_ADAPTER_SERVICE;
    public static final int GB_TO_MB_MULTIPLIER = 1000;
    private AWSClientManager clientManager;

    public enum AWSEBSStorageEnumerationStages {
        CLIENT, ENUMERATE, ERROR
    }

    public enum EBSVolumesEnumerationSubStage {
        CREATE_INTERNAL_TYPE_TAG, QUERY_LOCAL_RESOURCES, COMPARE, CREATE_EXTERNAL_TAGS, CREATE_UPDATE_DISK_STATES,
        UPDATE_TAGS, GET_NEXT_PAGE, DELETE_DISKS, ENUMERATION_STOP
    }

    public AWSEBSStorageEnumerationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.EC2);
    }

    /**
     * The enumeration service context that holds all the information needed to determine the list
     * of volumes that need to be represented in the system.
     */
    public static class EBSStorageEnumerationContext {
        public AmazonEC2AsyncClient amazonEC2Client;
        public ComputeEnumerateAdapterRequest request;
        public AuthCredentialsService.AuthCredentialsServiceState endpointAuth;
        public ComputeStateWithDescription parentCompute;
        public AWSEBSStorageEnumerationStages stage;
        public EBSVolumesEnumerationSubStage subStage;
        public Throwable error;
        public int pageNo;
        // Mapping of volume Id and the disk state in the local system.
        public Map<String, DiskState> localDiskStateMap;
        public Map<String, Volume> remoteAWSVolumes;
        public Set<String> remoteAWSVolumeIds;
        public List<Volume> volumesToBeCreated;
        public List<Tag> volumeTagsToBeCreated;
        // Mappings of the volume and the String documentSelf link of the volume to be updated.
        public Map<String, Volume> volumesToBeUpdated;
        // Mappings of the disk state and the String documentSelf link of the disk state to be updated.
        public Map<String, DiskState> diskStatesToBeUpdated;
        // The request object that is populated and sent to AWS to get the list of volumes.
        public DescribeVolumesRequest describeVolumesRequest;
        // The async handler that works with the response received from AWS
        public AsyncHandler<DescribeVolumesRequest, DescribeVolumesResult> resultHandler;
        // The token to use to retrieve the next page of results from AWS. This value is null when
        // there are no more results to return.
        public String nextToken;
        // The link used to navigate through the list of records that are to be deleted from the
        // local system.
        public String deletionNextPageLink;
        public Operation operation;
        // The list of operations that have to created/updated as part of the EBS enumeration.
        public List<Operation> enumerationOperations;
        // The time stamp at which the enumeration started.
        public long enumerationStartTimeInMicros;
        public String internalTypeTagSelfLink;
        // Holds tags of newly discovered instaces that got successfully POSTed (with 200 or 304)
        public List<Tag> createdExternalTags;
        public ResourceState resourceDeletionState;

        public EBSStorageEnumerationContext(ComputeEnumerateAdapterRequest request,
                Operation op) {
            this.operation = op;
            this.request = request;
            this.endpointAuth = request.endpointAuth;
            this.parentCompute = request.parentCompute;
            this.localDiskStateMap = new ConcurrentSkipListMap<>();
            this.volumesToBeUpdated = new HashMap<>();
            this.volumeTagsToBeCreated = new ArrayList<>();
            this.diskStatesToBeUpdated = new HashMap<>();
            this.remoteAWSVolumes = new ConcurrentSkipListMap<>();
            this.volumesToBeCreated = new ArrayList<>();
            this.enumerationOperations = new ArrayList<>();
            this.remoteAWSVolumeIds = new HashSet<>();
            this.createdExternalTags = new ArrayList<>();
            this.stage = AWSEBSStorageEnumerationStages.CLIENT;
            this.subStage = EBSVolumesEnumerationSubStage.CREATE_INTERNAL_TYPE_TAG;
            this.pageNo = 1;
            this.resourceDeletionState = getDeletionState(request.original
                    .deletedResourceExpirationMicros);
        }
    }

    @Override
    public void handleStart(Operation startPost) {
        super.handleStart(startPost);
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
        EBSStorageEnumerationContext awsEnumerationContext = new EBSStorageEnumerationContext(
                op.getBody(ComputeEnumerateAdapterRequest.class), op);
        if (awsEnumerationContext.request.original.isMockRequest) {
            awsEnumerationContext.operation.complete();
            return;
        }
        handleEnumerationRequest(awsEnumerationContext);
    }

    /**
     * Handles the different steps required to hit the AWS endpoint and get the set of resources
     * available and proceed to update the state in the local system based on the received data.
     */
    private void handleEnumerationRequest(EBSStorageEnumerationContext aws) {
        switch (aws.stage) {
        case CLIENT:
            getAWSAsyncClient(aws, AWSEBSStorageEnumerationStages.ENUMERATE);
            break;
        case ENUMERATE:
            switch (aws.request.original.enumerationAction) {
            case START:
                logInfo(() -> String.format("Started EBS enumeration for %s",
                        aws.request.original.resourceReference));
                aws.enumerationStartTimeInMicros = Utils.getNowMicrosUtc();
                aws.request.original.enumerationAction = EnumerationAction.REFRESH;
                handleEnumerationRequest(aws);
                break;
            case REFRESH:
                if (aws.pageNo == 1) {
                    logInfo(() -> String.format("Running creation enumeration in refresh mode for %s",
                            aws.request.original.resourceReference));
                }
                logFine(() -> String.format("Processing page %d ", aws.pageNo));
                aws.pageNo++;
                if (aws.describeVolumesRequest == null) {
                    createAWSRequestAndAsyncHandler(aws);
                }
                aws.amazonEC2Client.describeVolumesAsync(aws.describeVolumesRequest,
                        aws.resultHandler);
                break;
            case STOP:
                logInfo(() -> String.format("Stopping EBS enumeration for %s",
                        aws.request.original.resourceReference));
                setOperationDurationStat(aws.operation);
                aws.operation.complete();
                break;
            default:
                break;
            }
            break;
        case ERROR:
            aws.operation.fail(aws.error);
            break;
        default:
            logSevere(() -> String.format("Unknown AWS enumeration stage %s ",
                    aws.stage.toString()));
            aws.error = new Exception("Unknown AWS enumeration stage %s");
            aws.stage = AWSEBSStorageEnumerationStages.ERROR;
            handleEnumerationRequest(aws);
            break;
        }
    }

    /**
     * Method to instantiate the AWS Async client for future use
     */
    private void getAWSAsyncClient(EBSStorageEnumerationContext aws,
            AWSEBSStorageEnumerationStages next) {
        aws.amazonEC2Client = this.clientManager.getOrCreateEC2Client(aws.endpointAuth,
                aws.request.regionId, this, t -> aws.error = t);
        if (aws.error != null) {
            aws.stage = AWSEBSStorageEnumerationStages.ERROR;
            handleEnumerationRequest(aws);
            return;
        }
        OperationContext opContext = OperationContext.getOperationContext();
        AWSUtils.validateCredentials(aws.amazonEC2Client, this.clientManager, aws.endpointAuth,
                aws.request, aws.operation, this,
                (describeAvailabilityZonesResult) -> {
                    aws.stage = next;
                    OperationContext.restoreOperationContext(opContext);
                    handleEnumerationRequest(aws);
                },
                t -> {
                    OperationContext.restoreOperationContext(opContext);
                    aws.error = t;
                    aws.stage = AWSEBSStorageEnumerationStages.ERROR;
                    handleEnumerationRequest(aws);
                });
    }

    /**
     * Initializes and saves a reference to the request object that is sent to AWS to get a page of
     * volumes. Also saves an instance to the async handler that will be used to handle the
     * responses received from AWS. It sets the nextToken value in the request object sent to AWS
     * for getting the next page of results from AWS.
     */
    private void createAWSRequestAndAsyncHandler(EBSStorageEnumerationContext aws) {
        DescribeVolumesRequest request = new DescribeVolumesRequest();
        request.setMaxResults(getQueryPageSize());
        request.setNextToken(aws.nextToken);
        aws.describeVolumesRequest = request;
        AsyncHandler<DescribeVolumesRequest, DescribeVolumesResult> resultHandler = new AWSStorageEnumerationAsyncHandler(
                this, aws);
        aws.resultHandler = resultHandler;
    }

    /**
     * The async handler to handle the success and errors received after invoking the describe
     * volumes API on AWS
     */
    public static class AWSStorageEnumerationAsyncHandler implements
            AsyncHandler<DescribeVolumesRequest, DescribeVolumesResult> {

        private AWSEBSStorageEnumerationAdapterService service;
        private EBSStorageEnumerationContext context;
        private OperationContext opContext;

        private AWSStorageEnumerationAsyncHandler(AWSEBSStorageEnumerationAdapterService service,
                EBSStorageEnumerationContext context) {
            this.service = service;
            this.context = context;
            this.opContext = OperationContext.getOperationContext();
        }

        @Override
        public void onError(Exception exception) {
            OperationContext.restoreOperationContext(this.opContext);
            this.context.operation.fail(exception);
        }

        @Override
        public void onSuccess(DescribeVolumesRequest request,
                DescribeVolumesResult result) {
            OperationContext.restoreOperationContext(this.opContext);
            result.getVolumes()
                    .forEach(volume -> {
                        this.context.remoteAWSVolumes.put(volume.getVolumeId(), volume);
                        this.context.remoteAWSVolumeIds.add(volume.getVolumeId());
                    });

            this.service.logFine(() -> String.format("Successfully enumerated %d volumes on the AWS"
                    + " host", result.getVolumes().size()));
            // Save the reference to the next token that will be used to retrieve the next page of
            // results from AWS.
            this.context.nextToken = result.getNextToken();
            if (this.context.remoteAWSVolumes.size() == 0) {
                if (this.context.nextToken != null) {
                    this.context.subStage = EBSVolumesEnumerationSubStage.GET_NEXT_PAGE;
                } else {
                    this.context.subStage = EBSVolumesEnumerationSubStage.DELETE_DISKS;
                }
            }
            handleReceivedEnumerationData();
        }

        /**
         * Uses the received enumeration information and compares it against it the state of the
         * local system and then tries to find and fix the gaps. At a high level this is the
         * sequence of steps that is followed: 1) Create a query to get the list of local disk
         * states 2) Compare the list of local resources against the list received from the AWS
         * endpoint. 3) Create the volumes not know to the local system. 4) Update the disk
         * states known to the local system based on the latest version received from AWS.
         * 5) Delete the disk states that correspond to deleted volumes on AWS.
         */
        private void handleReceivedEnumerationData() {
            switch (this.context.subStage) {
            case CREATE_INTERNAL_TYPE_TAG:
                createInternalTypeTag(EBSVolumesEnumerationSubStage.QUERY_LOCAL_RESOURCES);
                break;
            case QUERY_LOCAL_RESOURCES:
                getLocalResources(EBSVolumesEnumerationSubStage.COMPARE);
                break;
            case COMPARE:
                compareLocalStateWithEnumerationData(
                        EBSVolumesEnumerationSubStage.CREATE_EXTERNAL_TAGS);
                break;
            case CREATE_EXTERNAL_TAGS:
                createExternalTags(EBSVolumesEnumerationSubStage.CREATE_UPDATE_DISK_STATES);
                break;
            case CREATE_UPDATE_DISK_STATES:
                createOrUpdateDiskStates(EBSVolumesEnumerationSubStage.UPDATE_TAGS);
                break;
            case UPDATE_TAGS:
                if (this.context.nextToken == null) {
                    updateTagLinks().whenComplete(thenDiskStateCreateOrUpdate(
                            EBSVolumesEnumerationSubStage.DELETE_DISKS));
                } else {
                    updateTagLinks().whenComplete(thenDiskStateCreateOrUpdate(
                            EBSVolumesEnumerationSubStage.GET_NEXT_PAGE));
                }
                break;
            case GET_NEXT_PAGE:
                getNextPageFromEnumerationAdapter(
                        EBSVolumesEnumerationSubStage.QUERY_LOCAL_RESOURCES);
                break;
            case DELETE_DISKS:
                deleteDiskStates(
                        EBSVolumesEnumerationSubStage.ENUMERATION_STOP);
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
         * Send a POST to create internal type tag for S3. Don't wait for completion, tag will eventually get created
         * while we go through the enumeration flow. If tag creation is successful, we set the internalTypeTagCreated
         * flag and add the internalTypeTagSelfLink to DiskStates.
         */
        private void createInternalTypeTag(EBSVolumesEnumerationSubStage next) {
            TagService.TagState internalTypeTagState = TagsUtil.newTagState(TAG_KEY_TYPE, AWSResourceType.ebs_block.toString(),
                    false, this.context.parentCompute.tenantLinks);

            // Create internal type TagState for EBS.
            Operation.createPost(this.service, TagService.FACTORY_LINK)
                    .setBody(internalTypeTagState)
                    .setReferer(this.service.getUri())
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            this.service.logWarning("Failure creating internal type tag for EBS [ex=%s]",
                                    e.getMessage());
                            return;
                        }
                        this.context.internalTypeTagSelfLink = internalTypeTagState.documentSelfLink;
                    }).sendWith(this.service);

            this.context.subStage = next;
            handleReceivedEnumerationData();
        }

        /**
         * Query the local data store and retrieve all the the compute states that exist filtered by
         * the volumeIds that are received in the enumeration data from AWS.
         */
        private void getLocalResources(EBSVolumesEnumerationSubStage next) {
            // query all disk state resources for the cluster filtered by the received set of
            // instance Ids. The filtering is performed on the selected resource pool.
            Query.Builder qBuilder = Query.Builder.create()
                    .addKindFieldClause(DiskState.class)
                    .addFieldClause(DiskState.FIELD_NAME_STORAGE_TYPE, STORAGE_TYPE_EBS)
                    .addInClause(ComputeState.FIELD_NAME_ID,
                            this.context.remoteAWSVolumes.keySet());

            addScopeCriteria(qBuilder, this.context);

            QueryTask queryTask = QueryTask.Builder.createDirectTask()
                    .setQuery(qBuilder.build())
                    .addOption(QueryOption.EXPAND_CONTENT)
                    .addOption(QueryOption.TOP_RESULTS)
                    .setResultLimit(getQueryResultLimit())
                    .build();
            queryTask.tenantLinks = this.context.parentCompute.tenantLinks;

            QueryUtils.startQueryTask(this.service, queryTask)
                    .whenComplete((qrt, e) -> {
                        if (e != null) {
                            this.service.logSevere(() -> String.format("Failure retrieving query"
                                    + " results: %s", e.toString()));
                            signalErrorToEnumerationAdapter(e);
                            return;
                        }
                        qrt.results.documents.values().forEach(documentJson -> {
                            DiskState localDisk = Utils.fromJson(documentJson,
                                    DiskState.class);
                            this.context.localDiskStateMap.put(localDisk.id,
                                    localDisk);

                        });
                        this.service.logFine(() -> String.format("%d EBS disk states found.",
                                qrt.results.documentCount));
                        this.context.subStage = next;
                        handleReceivedEnumerationData();
                    });
        }

        /**
         * Compares the local list of disks against what is received from the AWS endpoint. Creates
         * a list of disks to be updated and created based on the comparison of local and remote state.
         */
        private void compareLocalStateWithEnumerationData(
                EBSVolumesEnumerationSubStage next) {
            // No remote disks
            if (this.context.remoteAWSVolumes == null
                    || this.context.remoteAWSVolumes.size() == 0) {
                this.service.logFine(() -> "No disks discovered on the remote system.");
                // no local disks
            } else if (this.context.localDiskStateMap == null
                    || this.context.localDiskStateMap.size() == 0) {
                this.context.remoteAWSVolumes.entrySet().forEach(
                        entry -> this.context.volumesToBeCreated
                                .add(entry.getValue()));
                // Compare local and remote state and find candidates for update and create.
            } else {
                for (String key : this.context.remoteAWSVolumes.keySet()) {
                    if (this.context.localDiskStateMap.containsKey(key)) {
                        this.context.volumesToBeUpdated
                                .put(key, this.context.remoteAWSVolumes.get(key));
                        this.context.diskStatesToBeUpdated
                                .put(key, this.context.localDiskStateMap.get(key));
                    } else {
                        this.context.volumesToBeCreated.add(this.context.remoteAWSVolumes.get(key));
                    }
                }
            }
            this.context.subStage = next;
            handleReceivedEnumerationData();
        }

        /**
         * Create tags for disk states based on the volumes being created
         */
        private void createExternalTags(EBSVolumesEnumerationSubStage next) {

            List<Operation> operations = new ArrayList<>();
            Map<Long, Tag> tagsCreationOperationIdsMap = new ConcurrentHashMap<>();

            this.context.volumesToBeCreated.stream()
                    .forEach(volume -> {
                        this.context.volumeTagsToBeCreated.addAll(volume.getTags());
                    });

            this.context.volumeTagsToBeCreated.stream()
                    .filter(t -> !AWSConstants.AWS_TAG_NAME.equals(t.getKey()))
                    .forEach(t -> {
                        TagState tagState = TagsUtil.newTagState(t.getKey(), t.getValue(),
                                true,
                                this.context.parentCompute.tenantLinks);
                        Operation createTagOp = Operation.createPost(this.service,
                                TagService.FACTORY_LINK)
                                .setBody(tagState)
                                .setReferer(this.service.getUri());
                        operations.add(createTagOp);
                        tagsCreationOperationIdsMap.put(createTagOp.getId(), t);
                    });

            if (operations.isEmpty()) {
                this.service.logFine(() -> "No disk state tags to be created.");
                this.context.subStage = next;
                handleReceivedEnumerationData();
                return;
            }

            OperationJoin.create(operations).setCompletion((ops, exs) -> {
                if (exs != null && !exs.isEmpty()) {
                    this.service.logSevere(() -> String.format("Error creating disk tags %s",
                            Utils.toString(exs)));
                }

                ops.values().stream()
                        .filter(operation -> operation.getStatusCode() == Operation.STATUS_CODE_OK
                                || operation.getStatusCode() == Operation.STATUS_CODE_NOT_MODIFIED)
                        .forEach(operation -> {
                            if (tagsCreationOperationIdsMap.containsKey(operation.getId())) {
                                this.context.createdExternalTags.add(tagsCreationOperationIdsMap
                                        .get(operation.getId()));
                            }
                        });

                this.context.subStage = next;
                handleReceivedEnumerationData();
            }).sendWith(this.service);
        }

        /**
         * Creates the disk states that represent the volumes received from AWS during
         * enumeration.
         */
        private void createOrUpdateDiskStates(EBSVolumesEnumerationSubStage next) {

            // For all the disks to be created..map them and create operations.
            // kick off the operation using a JOIN
            List<DiskState> diskStatesToBeCreated = new ArrayList<>();
            this.context.volumesToBeCreated.forEach(volume -> {
                diskStatesToBeCreated.add(mapVolumeToDiskState(volume,
                        this.context.request.original.resourcePoolLink,
                        this.context.endpointAuth.documentSelfLink,
                        this.context.request.original.endpointLink,
                        this.context.request.regionId,
                        this.context.request.parentCompute.documentSelfLink,
                        this.context.parentCompute.tenantLinks, true));

            });
            diskStatesToBeCreated.forEach(diskState ->
                    this.context.enumerationOperations.add(
                            createPostOperation(this.service, diskState,
                                    DiskService.FACTORY_LINK)));
            this.service.logFine(() -> String.format("Creating %d EBS disks",
                    this.context.volumesToBeCreated.size()));

            // For existing disk states, check if tagLinks contains internal type tag. If it doesn't
            // then send a collection update request and add internal type tag to tagLinks.
            if (this.context.internalTypeTagSelfLink != null) {
                this.context.diskStatesToBeUpdated.entrySet().stream()
                        .filter(diskMap -> diskMap.getValue().tagLinks == null
                                || !diskMap.getValue().tagLinks.contains(this.context.internalTypeTagSelfLink))
                        .forEach(diskMap -> {
                            Map<String, Collection<Object>> collectionsToAddMap = Collections.singletonMap
                                    (DiskState.FIELD_NAME_TAG_LINKS,
                                            Collections.singletonList(this.context.internalTypeTagSelfLink));
                            Map<String, Collection<Object>> collectionsToRemoveMap = Collections.singletonMap
                                    (DiskState.FIELD_NAME_TAG_LINKS, Collections.EMPTY_LIST);

                            ServiceStateCollectionUpdateRequest updateTagLinksRequest = ServiceStateCollectionUpdateRequest
                                    .create(collectionsToAddMap, collectionsToRemoveMap);

                            this.context.enumerationOperations.add(Operation.createPatch(this.service.getHost(),
                                    diskMap.getValue().documentSelfLink)
                                    .setReferer(this.service.getUri())
                                    .setBody(updateTagLinksRequest));
                        });
            }

            // For those existing disk states which do not have internal type tag, add
            // For all the disks to be updated, map the updated state from the received
            // volumes and issue patch requests against the existing disk state representations
            // in the system
            List<DiskState> diskStatesToBeUpdated = new ArrayList<>();
            this.context.volumesToBeUpdated.forEach((selfLink, volume) -> {
                diskStatesToBeUpdated.add(mapVolumeToDiskState(volume,
                        null,
                        this.context.endpointAuth.documentSelfLink,
                        this.context.request.original.endpointLink,
                        this.context.request.regionId,
                        this.context.request.parentCompute.documentSelfLink,
                        this.context.parentCompute.tenantLinks, false));

            });
            diskStatesToBeUpdated.forEach(diskState -> {
                this.context.enumerationOperations.add(
                        createPatchOperation(this.service, diskState,
                                this.context.localDiskStateMap.get(diskState.id).documentSelfLink));
            });

            this.service.logFine(() -> String.format("Updating %d disks",
                    this.context.volumesToBeUpdated.size()));

            OperationJoin.JoinedCompletionHandler joinCompletion = (ox, exc) -> {
                if (exc != null) {
                    this.service.logSevere(() -> String.format("Error creating/updating disk %s",
                            Utils.toString(exc)));
                    signalErrorToEnumerationAdapter(exc.values().iterator().next());
                    return;
                }
                this.service.logFine(() -> "Successfully created and updated all the disk states.");
                this.context.subStage = next;
                handleReceivedEnumerationData();
            };

            if (this.context.enumerationOperations.isEmpty()) {
                this.context.subStage = next;
                handleReceivedEnumerationData();
                return;
            }

            OperationJoin joinOp = OperationJoin.create(this.context.enumerationOperations);
            joinOp.setCompletion(joinCompletion);
            joinOp.sendWith(this.service.getHost());
        }

        /**
         * Update newly identified tags for disk states based on volumes.
         */
        private DeferredResult<EBSStorageEnumerationContext> updateTagLinks() {
            if (this.context.volumesToBeUpdated == null
                    || this.context.volumesToBeUpdated.size() == 0) {

                return DeferredResult.completed(this.context);
            } else {

                List<DeferredResult<Set<String>>> updateCSTagLinksOps = new ArrayList<>();

                for (String volumeId : this.context.volumesToBeUpdated.keySet()) {
                    if (!this.context.diskStatesToBeUpdated.containsKey(volumeId)) {
                        continue; // this is not a disk to update
                    }
                    Volume volume = this.context.volumesToBeUpdated.get(volumeId);
                    DiskState existingDiskState = this.context.diskStatesToBeUpdated
                            .get(volumeId);
                    Map<String, String> remoteTags = new HashMap<>();
                    for (Tag awsVolumeTag : volume.getTags()) {
                        if (!awsVolumeTag.getKey().equals(AWSConstants.AWS_TAG_NAME)) {
                            remoteTags.put(awsVolumeTag.getKey(), awsVolumeTag.getValue());
                        }
                    }
                    updateCSTagLinksOps.add(TagsUtil
                            .updateLocalTagStates(this.service, existingDiskState, remoteTags));
                }
                return DeferredResult.allOf(updateCSTagLinksOps).thenApply(gnore -> this.context);
            }
        }

        private BiConsumer<EBSStorageEnumerationContext, Throwable> thenDiskStateCreateOrUpdate(
                EBSVolumesEnumerationSubStage next) {
            // NOTE: In case of error 'ignoreCtx' is null so use passed context!
            return (ignoreCtx, exc) -> {
                if (exc != null) {
                    this.service.logWarning("Failure while updating EBS tagLinks: %s",
                            exc.getMessage());
                }
                this.context.subStage = next;
                handleReceivedEnumerationData();
            };
        }

        /**
         * Map an EBS volume to a photon-model disk state.
         */
        private DiskState mapVolumeToDiskState(Volume volume, String resourcePoolLink,
                String authCredentialsLink, String endpointLink, String regionId,
                String parentComputeLink, List<String> tenantLinks, boolean isNewDiskState) {
            DiskState diskState = new DiskState();
            diskState.id = volume.getVolumeId();

            // AWS returns the disk size in GBs
            diskState.capacityMBytes = volume.getSize() * GB_TO_MB_MULTIPLIER;
            diskState.storageType = STORAGE_TYPE_EBS;
            diskState.regionId = regionId;
            diskState.zoneId = volume.getAvailabilityZone();
            diskState.authCredentialsLink = authCredentialsLink;
            diskState.resourcePoolLink = resourcePoolLink;
            diskState.endpointLink = endpointLink;
            if (diskState.endpointLinks == null) {
                diskState.endpointLinks = new HashSet<String>();
            }
            diskState.endpointLinks.add(endpointLink);
            diskState.tenantLinks = tenantLinks;
            diskState.computeHostLink = parentComputeLink;
            diskState.tagLinks = new HashSet<>();

            if (volume.getCreateTime() != null) {
                diskState.creationTimeMicros = TimeUnit.MILLISECONDS
                        .toMicros(volume.getCreateTime().getTime());
            }

            // calculate disk name, default to volume-id if 'Name' tag is not present
            if (volume.getTags() == null) {
                diskState.name = volume.getVolumeId();
            } else {
                diskState.name = volume.getTags().stream()
                        .filter(tag -> tag.getKey().equals(AWS_TAG_NAME))
                        .map(tag -> tag.getValue()).findFirst()
                        .orElse(volume.getVolumeId());
            }

            // If we're creating a new DiskState, we've already created TagStates for it, so populate the
            // tagLinks with appropriate links. We store tags got successfully created in createdExternalTags
            // list and only add valid tagLinks to new disks.
            if (isNewDiskState) {
                diskState.tagLinks = volume.getTags().stream()
                        .filter(tag -> !tag.getKey().equals(AWS_TAG_NAME)
                                && this.context.createdExternalTags.contains(tag))
                        .map(tag -> TagsUtil.newTagState(tag.getKey(), tag.getValue(),
                                true, this.context.parentCompute.tenantLinks))
                        .map(tag -> tag.documentSelfLink)
                        .collect(Collectors.toSet());

                // If we've successfully created the internal type tag for EBS, add it's tagLink to diskStates.
                if (this.context.internalTypeTagSelfLink != null) {
                    // Add internal type tag with for all EBS Disk States.
                    diskState.tagLinks.add(this.context.internalTypeTagSelfLink);
                }
            }

            mapAttachmentState(diskState, volume);
            mapDiskType(diskState, volume);
            mapCustomProperties(diskState, volume);
            return diskState;

        }

        /**
         * Method for mapping additionl properties in the EBS volume to the local diskstate. For e.g. snapshotID, iops,
         * encrypted etc.
         */
        private void mapCustomProperties(DiskState diskState, Volume volume) {
            diskState.customProperties = new HashMap<>();
            if (volume.getSnapshotId() != null) {
                diskState.customProperties.put(SNAPSHOT_ID, volume.getSnapshotId());
            }
            if (volume.getIops() != null) {
                diskState.customProperties.put(DISK_IOPS, volume.getIops().toString());
            }
            if (volume.getEncrypted() != null) {
                diskState.customProperties.put(DISK_ENCRYPTED_FLAG,
                        volume.getEncrypted().toString());
            }
            diskState.customProperties.put(VOLUME_TYPE,
                    volume.getVolumeType());
            diskState.customProperties.put(SOURCE_TASK_LINK,
                    ResourceEnumerationTaskService.FACTORY_LINK);
        }

        /**
         * This method determines if the given EBS volume is currently in "attached" OR "detached" state.
         * The given EBS volume has an "attachment" object associated with it in case the disk is
         * associated with any of the running instances on AWS.
         */
        private void mapAttachmentState(DiskState diskState, Volume volume) {
            if (volume.getAttachments().size() > 0) {
                diskState.status = DiskStatus.ATTACHED;
                // TODO VSYM-2341 add logic to update the compute state to be linked to this
                // disk based on the list of attachments
            } else {
                diskState.status = DiskStatus.DETACHED;
            }
        }

        /**
         * The disk types available on AWS include
         * - gp2 = general purpose SSD
         * - io1 = provisioned SSD
         * - standard = Magnetic Disk
         * <p>
         * These are mapped to SSD or HDD in the local system.
         */
        private void mapDiskType(DiskState diskState, Volume volume) {
            String volumeType = volume.getVolumeType();
            if (volumeType.equalsIgnoreCase(VOLUME_TYPE_GENERAL_PURPOSED_SSD)
                    || (volumeType.equalsIgnoreCase(VOLUME_TYPE_PROVISIONED_SSD))) {
                diskState.type = DiskType.SSD;
            } else {
                diskState.type = DiskType.HDD;
            }
        }

        /**
         * Deletes undiscovered resources.
         * <p>
         * The logic works by recording a timestamp when enumeration starts. This timestamp is used to
         * lookup resources which haven't been touched as part of current enumeration cycle.
         * <p>
         * Finally, delete on a resource is invoked only if it meets two criteria:
         * - Timestamp older than current enumeration cycle.
         * - EBS block not present on AWS.
         * <p>
         * The method paginates through list of resources for deletion.
         */
        private void deleteDiskStates(EBSVolumesEnumerationSubStage next) {
            Query.Builder qBuilder = Builder.create()
                    .addKindFieldClause(DiskState.class)
                    .addFieldClause(DiskState.FIELD_NAME_STORAGE_TYPE,
                            STORAGE_TYPE_EBS)
                    .addRangeClause(DiskState.FIELD_NAME_UPDATE_TIME_MICROS,
                            NumericRange
                                    .createLessThanRange(this.context.enumerationStartTimeInMicros))
                    .addCompositeFieldClause(
                            ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                            SOURCE_TASK_LINK, ResourceEnumerationTaskService.FACTORY_LINK,
                            QueryTask.Query.Occurance.MUST_OCCUR);

            addScopeCriteria(qBuilder, this.context);

            QueryTask q = QueryTask.Builder.createDirectTask()
                    .addOption(QueryOption.EXPAND_CONTENT)
                    .setQuery(qBuilder.build())
                    .setResultLimit(getQueryResultLimit())
                    .build();
            q.tenantLinks = this.context.parentCompute.tenantLinks;
            q.documentExpirationTimeMicros = Utils.getNowMicrosUtc() + QueryUtils.TEN_MINUTES_IN_MICROS;
            this.service.logFine(() -> "Querying disks for deletion");
            QueryUtils.startQueryTask(this.service, q)
                    .whenComplete((queryTask, e) -> {
                        if (e != null) {
                            this.service.logWarning("Failure getting EBS diskStates for deletion: %s",
                                    e.getMessage());
                            this.context.subStage = next;
                            handleReceivedEnumerationData();
                            return;
                        }

                        if (queryTask.results.nextPageLink == null) {
                            this.service.logFine(() -> "No disk states match for deletion");
                            this.context.subStage = next;
                            handleReceivedEnumerationData();
                            return;
                        }
                        this.context.deletionNextPageLink = queryTask.results.nextPageLink;
                        processDeletionRequest(next);
                    });
        }

        /**
         * Helper method to paginate through resources to be deleted.
         */
        private void processDeletionRequest(EBSVolumesEnumerationSubStage next) {
            if (this.context.deletionNextPageLink == null) {
                this.service.logFine(() -> "Finished deletion of disk states for AWS");
                this.context.subStage = next;
                handleReceivedEnumerationData();
                return;
            }
            this.service.logFine(() -> String.format("Querying page [%s] for resources to be deleted",
                    this.context.deletionNextPageLink));
            this.service
                    .sendRequest(
                            Operation.createGet(this.service, this.context.deletionNextPageLink)
                                    .setCompletion((o, e) -> {
                                        if (e != null) {
                                            signalErrorToEnumerationAdapter(e);
                                            return;
                                        }
                                        QueryTask queryTask = o.getBody(QueryTask.class);

                                        this.context.deletionNextPageLink = queryTask.results.nextPageLink;
                                        List<Operation> deleteOperations = new ArrayList<>();
                                        for (Object s : queryTask.results.documents.values()) {
                                            DiskState diskState = Utils
                                                    .fromJson(s, DiskState.class);
                                            // If the global list of ids does not contain this entity and it
                                            // was not updated then delete it. This check is necessary as
                                            // the document update timestamp/version does not change if
                                            // there are no changes to the attributes of the entity during
                                            // update.
                                            if (!this.context.remoteAWSVolumeIds
                                                    .contains(diskState.id)) {
                                                // Deleting the diskState is done by disassociating
                                                // the endpointLink from the diskstate. If the
                                                // diskstate isn't associated with any other
                                                // endpointLink, it should be deleted by the
                                                // groomer task
                                                createEndpointLinksUpdateOperation(this.context
                                                                .request.original.endpointLink,
                                                        deleteOperations, diskState
                                                                .documentSelfLink, diskState
                                                                .endpointLinks);

                                            }
                                        }
                                        this.service.logFine(() -> String.format("Deleting %d disks",
                                                deleteOperations.size()));
                                        if (deleteOperations.size() == 0) {
                                            this.service.logFine(() -> "No disk states to be deleted");
                                            processDeletionRequest(next);
                                            return;
                                        }
                                        OperationJoin.create(deleteOperations)
                                                .setCompletion((ops, exs) -> {
                                                    if (exs != null) {
                                                        // We don't want to fail the whole data collection
                                                        // if some of the operation fails.
                                                        exs.values().forEach(
                                                                ex -> this.service
                                                                        .logWarning(() ->
                                                                                String.format("Error: %s",
                                                                                        ex.getMessage())));
                                                    }
                                                    processDeletionRequest(next);
                                                })
                                                .sendWith(this.service);
                                    }));

        }


        private void createEndpointLinksUpdateOperation(String endpointLink, List<Operation>
                updateOperations, String selfLink, Set<String> endpointLinks) {
            if (endpointLinks != null && endpointLinks.contains(endpointLink)) {

                Set<String> endpointLinksToBeDisassociated = new HashSet<>();
                endpointLinksToBeDisassociated.add(endpointLink);
                Map<String, Collection<Object>> endpointsToRemove = Collections
                        .singletonMap(EndpointService.EndpointState.FIELD_NAME_ENDPOINT_LINKS,
                                new HashSet<>(endpointLinksToBeDisassociated));
                ServiceStateCollectionUpdateRequest serviceStateCollectionUpdateRequest =
                        ServiceStateCollectionUpdateRequest.create(null,
                                endpointsToRemove);

                updateOperations.add(Operation
                        .createPatch(this.service, selfLink)
                        .setReferer(this.service.getUri())
                        .setBody(serviceStateCollectionUpdateRequest)
                        .setCompletion(
                                (updateOp, exception) -> {
                                    if (exception != null) {
                                        this.service.logWarning(() -> String.format("PATCH to " +
                                                        "instance service %s, failed: %s",
                                                updateOp.getUri(), exception.toString()));
                                        return;
                                    }
                                }));

            }
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
            this.context.stage = AWSEBSStorageEnumerationStages.ERROR;
            this.service.handleEnumerationRequest(this.context);
        }

        /**
         * Calls the AWS enumeration adapter to get the next page from AWSs
         */
        private void getNextPageFromEnumerationAdapter(EBSVolumesEnumerationSubStage next) {
            // Reset all the results from the last page that was processed.
            this.context.remoteAWSVolumes.clear();
            this.context.volumesToBeCreated.clear();
            this.context.volumesToBeUpdated.clear();
            this.context.diskStatesToBeUpdated.clear();
            this.context.localDiskStateMap.clear();
            this.context.createdExternalTags.clear();
            this.context.describeVolumesRequest.setNextToken(this.context.nextToken);
            this.context.subStage = next;
            this.service.handleEnumerationRequest(this.context);
        }

    }


    /**
     * Constrain every query with regionId and tenantLinks, if presented.
     */
    private static void addScopeCriteria(
            Query.Builder qBuilder,
            EBSStorageEnumerationContext ctx) {

        // Add REGION criteria
        qBuilder.addFieldClause(ResourceState.FIELD_NAME_REGION_ID, ctx.request.regionId);
        // Add TENANT_LINKS criteria
        QueryUtils.addTenantLinks(qBuilder, ctx.parentCompute.tenantLinks);
    }

}