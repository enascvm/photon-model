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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.BUCKET_OWNER_NAME;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.STORAGE_TYPE_S3;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.getQueryResultLimit;
import static com.vmware.photon.controller.model.adapters.util.AdapterUtils.createPostOperation;
import static com.vmware.photon.controller.model.adapters.util.AdapterUtils.getDeletionState;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.TAG_KEY_TYPE;
import static com.vmware.photon.controller.model.util.PhotonModelUriUtils.createInventoryUri;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.BucketTaggingConfiguration;

import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSResourceType;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AwsClientType;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.ComputeEnumerateAdapterRequest;
import com.vmware.photon.controller.model.adapters.util.TagsUtil;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
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
 * S3 Storage Enumeration Adapter for the Amazon Web Services.
 * - Performs a list call to the S3 list buckets API and reconciles the local state with the state on the remote system.
 * - It lists the S3 buckets on the remote system. Compares those with the local system and creates or updates
 * the buckets that are missing in the local system. In the local system each S3 bucket is mapped to a disk state.
 */
public class AWSS3StorageEnumerationAdapterService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_S3_STORAGE_ENUMERATION_ADAPTER_SERVICE;
    private ExecutorService executorService;

    public enum S3StorageEnumerationStages {
        CLIENT, ENUMERATE, ERROR, FINISHED
    }

    public enum S3StorageEnumerationSubStage {
        CREATE_INTERNAL_TYPE_TAG, QUERY_LOCAL_RESOURCES, COMPARE, ENUMERATE_TAGS, CREATE_DISK_STATES,
        CREATE_TAG_STATES_UPDATE_TAG_LINKS, DELETE_DISKS, ENUMERATION_STOP
    }

    public AWSS3StorageEnumerationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    /**
     * The enumeration service context that holds all the information needed to determine the list
     * of buckets that need to be represented in the system.
     */
    public static class S3StorageEnumerationContext {
        public AWSClientManager clientManager;
        public StatelessService service;
        public AmazonS3Client amazonS3Client;
        public ComputeEnumerateAdapterRequest request;
        public AuthCredentialsService.AuthCredentialsServiceState endpointAuth;
        public ComputeStateWithDescription parentCompute;
        public S3StorageEnumerationStages stage;
        public S3StorageEnumerationSubStage subStage;
        public Throwable error;
        public Map<String, Bucket> remoteBucketsByBucketName;
        public Map<String, String> regionsByBucketName;
        public Map<String, Map<String, String>> tagsByBucketName;
        public Map<String, DiskState> localDiskStatesByBucketName;
        public List<Bucket> bucketsToBeCreated;
        public List<DiskState> diskStatesEnumerated;
        public Map<String, DiskState> diskStatesToBeUpdatedByBucketName;
        public String deletionNextPageLink;
        public String localResourcesNextPageLink;
        public Operation operation;
        // The list of operations that have to created/updated as part of the S3 enumeration.
        public List<Operation> enumerationOperations;
        // The time stamp at which the enumeration started.
        public long enumerationStartTimeInMicros;
        // selfLink for internal type TagState
        public String internalTypeTagSelfLink;
        public ResourceState resourceDeletionState;

        public S3StorageEnumerationContext(ComputeEnumerateAdapterRequest request,
                Operation op) {
            this.clientManager = AWSClientManagerFactory.getClientManager(AwsClientType.S3);
            this.operation = op;
            this.request = request;
            this.endpointAuth = request.endpointAuth;
            this.parentCompute = request.parentCompute;
            this.remoteBucketsByBucketName = new ConcurrentHashMap<>();
            this.regionsByBucketName = new ConcurrentHashMap<>();
            this.tagsByBucketName = new ConcurrentHashMap<>();
            this.localDiskStatesByBucketName = new ConcurrentHashMap<>();
            this.diskStatesToBeUpdatedByBucketName = new ConcurrentHashMap<>();
            this.bucketsToBeCreated = new ArrayList<>();
            this.diskStatesEnumerated = new ArrayList<>();
            this.enumerationOperations = new ArrayList<>();
            this.stage = S3StorageEnumerationStages.CLIENT;
            this.subStage = S3StorageEnumerationSubStage.CREATE_INTERNAL_TYPE_TAG;
            this.resourceDeletionState = getDeletionState(request.original
                    .deletedResourceExpirationMicros);
        }
    }

    @Override
    public void handleStart(Operation startPost) {
        // Initialize an executor with fixed thread pool.
        this.executorService = getHost().allocateExecutor(this);
        super.handleStart(startPost);
    }

    @Override
    public void handleStop(Operation op) {
        this.executorService.shutdown();
        AdapterUtils.awaitTermination(this.executorService);
        super.handleStop(op);
    }

    @Override
    public void handlePatch(Operation op) {
        setOperationHandlerInvokeTimeStat(op);
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        S3StorageEnumerationContext awsEnumerationContext = new S3StorageEnumerationContext(
                op.getBody(ComputeEnumerateAdapterRequest.class), op);
        awsEnumerationContext.service = this;

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
    private void handleEnumerationRequest(S3StorageEnumerationContext aws) {
        switch (aws.stage) {
        case CLIENT:
            getAWSS3Client(aws, S3StorageEnumerationStages.ENUMERATE);
            break;
        case ENUMERATE:
            switch (aws.request.original.enumerationAction) {
            case START:
                logInfo(() -> String.format("Started S3 enumeration for %s",
                        aws.request.original.resourceReference));
                aws.enumerationStartTimeInMicros = Utils.getNowMicrosUtc();
                aws.request.original.enumerationAction = EnumerationAction.REFRESH;
                handleEnumerationRequest(aws);
                break;
            case REFRESH:
                enumerateS3Buckets(aws);
                break;
            case STOP:
                aws.stage = S3StorageEnumerationStages.FINISHED;
                handleEnumerationRequest(aws);
                break;
            default:
                break;
            }
            break;
        case ERROR:
            AWSClientManagerFactory.returnClientManager(aws.clientManager,
                    AWSConstants.AwsClientType.S3);
            logSevere("Failure during S3 enumeration: %s", aws.error == null
                    ? "No additional details provided."
                    : aws.error.getMessage());
            aws.operation.fail(aws.error);
            break;
        case FINISHED:
            AWSClientManagerFactory.returnClientManager(aws.clientManager,
                    AWSConstants.AwsClientType.S3);
            logInfo(() -> String.format("Stopping S3 enumeration for %s",
                    aws.request.original.resourceReference));
            setOperationDurationStat(aws.operation);
            aws.operation.complete();
            break;
        default:
            logSevere(() -> String.format("Unknown AWS enumeration stage %s ",
                    aws.stage.toString()));
            aws.error = new Exception("Unknown AWS enumeration stage %s");
            aws.stage = S3StorageEnumerationStages.ERROR;
            handleEnumerationRequest(aws);
            break;
        }
    }

    /**
     * Method to instantiate the AWS Async client for future use
     */
    private void getAWSS3Client(S3StorageEnumerationContext aws,
            S3StorageEnumerationStages next) {
        aws.amazonS3Client = aws.clientManager.getOrCreateS3Client
                (aws.endpointAuth, aws.request.regionId, this, t -> aws.error = t);

        if (aws.clientManager.isS3ClientInvalid(aws.endpointAuth, aws.request.regionId)
                || (aws.error != null)) {
            logWarning("AWS client is invalid for [endpoint=%s]",
                    aws.request.original.endpointLink);
            aws.stage = S3StorageEnumerationStages.FINISHED;
            handleEnumerationRequest(aws);
            return;
        }

        aws.stage = next;
        handleEnumerationRequest(aws);
    }

    /**
     * Call the listBuckets() method to enumerate S3 buckets.
     * AWS SDK does not have an async method for listing buckets, so we use the synchronous method
     * in a fixed thread pool for S3 enumeration service.
     * If listBuckets() call fails due to unsupported region, we mark the S3 client invalid,
     * stop the enumeration flow and patch back to parent.
     */
    private void enumerateS3Buckets(S3StorageEnumerationContext aws) {
        logInfo(() -> String.format("Running creation enumeration in refresh mode for %s",
                aws.request.original.resourceReference));

        OperationContext operationContext = OperationContext.getOperationContext();
        this.executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    List<Bucket> bucketList = aws.amazonS3Client.listBuckets();
                    for (Bucket bucket : bucketList) {
                        aws.remoteBucketsByBucketName.put(bucket.getName(), bucket);
                    }

                    OperationContext.restoreOperationContext(operationContext);

                    if (aws.remoteBucketsByBucketName.isEmpty()) {
                        aws.subStage = S3StorageEnumerationSubStage.DELETE_DISKS;
                    }

                    handleReceivedEnumerationData(aws);
                } catch (Exception e) {
                    if (e instanceof AmazonS3Exception && ((AmazonS3Exception) e)
                            .getStatusCode() == Operation.STATUS_CODE_FORBIDDEN) {
                        markClientInvalid(aws);
                    } else {
                        logSevere("Exception enumerating S3 buckets for [ex=%s]",
                                e.getMessage());
                        aws.error = e;
                        aws.stage = S3StorageEnumerationStages.ERROR;
                        handleEnumerationRequest(aws);
                    }
                }
            }
        });
    }

    /**
     * Some regions do not support listBuckets() method and return 400. In that case, add the client to
     * invalid client cache and complete the operation.
     * @param aws
     */
    private void markClientInvalid(S3StorageEnumerationContext aws) {
        logWarning("AWS client is invalid for [endpoint=%s]",
                aws.request.original.endpointLink);
        aws.clientManager
                .markS3ClientInvalid(this, aws.endpointAuth, aws.request.regionId);
        aws.stage = S3StorageEnumerationStages.FINISHED;
        handleEnumerationRequest(aws);
    }

    /**
     * State machine to handle S3 enumeration data received.
     */
    private void handleReceivedEnumerationData(S3StorageEnumerationContext aws) {
        switch (aws.subStage) {
        case CREATE_INTERNAL_TYPE_TAG:
            createInternalTypeTag(aws, S3StorageEnumerationSubStage.QUERY_LOCAL_RESOURCES);
            break;
        case QUERY_LOCAL_RESOURCES:
            getLocalResources(aws, S3StorageEnumerationSubStage.COMPARE);
            break;
        case COMPARE:
            compareLocalStateWithEnumerationData(aws, S3StorageEnumerationSubStage.ENUMERATE_TAGS);
            break;
        case ENUMERATE_TAGS:
            enumerateTags(aws, S3StorageEnumerationSubStage.CREATE_DISK_STATES);
            break;
        case CREATE_DISK_STATES:
            createDiskStates(aws, S3StorageEnumerationSubStage.CREATE_TAG_STATES_UPDATE_TAG_LINKS);
            break;
        case CREATE_TAG_STATES_UPDATE_TAG_LINKS:
            createTagStatesAndUpdateTagLinks(aws).whenComplete(thenDiskDelete(aws,
                    S3StorageEnumerationSubStage.DELETE_DISKS));
            break;
        case DELETE_DISKS:
            deleteDiskStates(aws, S3StorageEnumerationSubStage.ENUMERATION_STOP);
            break;
        case ENUMERATION_STOP:
            signalStopToEnumerationAdapter(aws);
            break;
        default:
            Throwable t = new Exception("Unknown AWS enumeration sub stage");
            signalErrorToEnumerationAdapter(aws, t);
        }
    }

    /**
     * Send a POST to create internal type tag for S3. Don't wait for completion, tag will eventually get created
     * while we go through the enumeration flow. If tag creation is successful, we set the internalTypeTagCreated
     * flag and add the internalTypeTagSelfLink to DiskStates.
     */
    private void createInternalTypeTag(S3StorageEnumerationContext aws, S3StorageEnumerationSubStage next) {

        TagState internalTypeTagState = TagsUtil.newTagState(TAG_KEY_TYPE, AWSResourceType.s3_bucket.toString(),
                false, aws.parentCompute.tenantLinks);

        Operation.createPost(aws.service,TagService.FACTORY_LINK)
                .setBody(internalTypeTagState)
                .setReferer(aws.service.getUri())
                .setCompletion((o, e) -> {
                    // log exception if tag creation fails with anything other than IDEMPOTENT_POST behaviour.
                    if (e != null) {
                        logSevere("Error creating internal type tag for S3");
                        return;
                    }
                    aws.internalTypeTagSelfLink = internalTypeTagState.documentSelfLink;
                }).sendWith(aws.service);

        aws.subStage = next;
        handleReceivedEnumerationData(aws);
    }
    /**
     * Query and get list of S3 buckets present locally in disk states in current context.
     */
    private void getLocalResources(S3StorageEnumerationContext aws, S3StorageEnumerationSubStage next) {
        // query all disk state resources for the cluster filtered by the received set of
        // instance Ids. the filtering is performed on the selected resource pool.
        if (aws.localResourcesNextPageLink == null) {
            Query.Builder qBuilder = Query.Builder.create()
                    .addKindFieldClause(DiskState.class)
                    .addFieldClause(DiskState.FIELD_NAME_STORAGE_TYPE, STORAGE_TYPE_S3)
                    .addInClause(DiskState.FIELD_NAME_ID, aws.remoteBucketsByBucketName.keySet());

            addScopeCriteria(qBuilder, aws);

            QueryTask queryTask = QueryTask.Builder.createDirectTask()
                    .setQuery(qBuilder.build())
                    .addOption(QueryOption.EXPAND_CONTENT)
                    .setResultLimit(getQueryResultLimit())
                    .build();
            queryTask.tenantLinks = aws.parentCompute.tenantLinks;

            QueryUtils.startInventoryQueryTask(this, queryTask).whenComplete((qrt, e) -> {
                if (e != null) {
                    this.logSevere(() -> String.format("Failure retrieving query" + " results: %s", e.toString()));
                    signalErrorToEnumerationAdapter(aws, e);
                    return;
                }
                qrt.results.documents.values().forEach(documentJson -> {
                    DiskState localDisk = Utils.fromJson(documentJson, DiskState.class);
                    aws.localDiskStatesByBucketName.put(localDisk.name, localDisk);

                });
                this.logFine(() -> String.format("%d S3 disk states found.", qrt.results.documentCount));

                if (qrt.results.nextPageLink != null) {
                    this.logFine("Processing next page for local disk states.");
                    aws.localResourcesNextPageLink = qrt.results.nextPageLink;
                    handleReceivedEnumerationData(aws);
                } else {
                    aws.subStage = next;
                    handleReceivedEnumerationData(aws);
                }
            });
        } else {
            Operation.createGet(createInventoryUri(this.getHost(), aws.localResourcesNextPageLink))
                    .setReferer(this.getUri())
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            this.logSevere(() -> String.format("Failure retrieving query" + " results: %s",
                                    e.toString()));
                            signalErrorToEnumerationAdapter(aws, e);
                            return;
                        }
                        QueryTask qrt = o.getBody(QueryTask.class);
                        qrt.results.documents.values().forEach(documentJson -> {
                            DiskState localDisk = Utils.fromJson(documentJson, DiskState.class);
                            aws.localDiskStatesByBucketName.put(localDisk.name, localDisk);
                        });

                        this.logFine(() -> String.format("%d S3 disk states found.", qrt.results.documentCount));

                        if (qrt.results.nextPageLink != null) {
                            this.logFine("Processing next page for local disk states.");
                            aws.localResourcesNextPageLink = qrt.results.nextPageLink;
                            handleReceivedEnumerationData(aws);
                        } else {
                            aws.subStage = next;
                            handleReceivedEnumerationData(aws);
                        }
                    }).sendWith(this);
        }
    }

    /**
     * Compares the local list of disks against what is received from the AWS endpoint. Creates
     * a list of disks to be updated and created based on the comparison of local and remote state.
     */
    private void compareLocalStateWithEnumerationData(S3StorageEnumerationContext aws,
            S3StorageEnumerationSubStage next) {
        // No remote disks
        if (aws.remoteBucketsByBucketName == null
                || aws.remoteBucketsByBucketName.size() == 0) {
            this.logFine(() -> "No disks discovered on the remote system.");
            // no local disks
        } else if (aws.localDiskStatesByBucketName == null
                || aws.localDiskStatesByBucketName.size() == 0) {
            aws.remoteBucketsByBucketName.entrySet().forEach(
                    entry -> aws.bucketsToBeCreated
                            .add(entry.getValue()));
            // Compare local and remote state and find candidates for update and create.
        } else {
            for (String key : aws.remoteBucketsByBucketName.keySet()) {
                if (aws.localDiskStatesByBucketName.containsKey(key)) {
                    aws.diskStatesToBeUpdatedByBucketName
                            .put(key, aws.localDiskStatesByBucketName.get(key));
                } else {
                    aws.bucketsToBeCreated.add(aws.remoteBucketsByBucketName.get(key));
                }
            }
        }

        aws.subStage = next;
        handleReceivedEnumerationData(aws);
    }

    /**
     * Calls getBucketTaggingConfiguration() on every bucket to enumerate bucket tags.
     * getBucketTaggingConfiguration() method is region aware and only returns valid results when we
     * call it with the client region same as the S3 bucket region. Since S3 bucket region is not
     * available through API, when a new bucket is discovered, we try to get tags for it by calling
     * getBucketTaggingConfiguration() with client in every AWS region. We then store the client region
     * for which we received successful response as region in DiskState for that S3 bucket. This region
     * in DiskState is then used for any subsequent calls for enumerating tags.
     */
    private void enumerateTags(S3StorageEnumerationContext aws,
            S3StorageEnumerationSubStage next) {
        OperationContext operationContext = OperationContext.getOperationContext();
        this.executorService.submit(new Runnable() {
            @Override
            public void run() {
                OperationContext.restoreOperationContext(operationContext);
                AmazonS3Client s3Client;
                BucketTaggingConfiguration bucketTaggingConfiguration = null;

                // We have previously enumerated these disks so we know which region they belong to.
                // Get a client for that region and make a call to enumerate tags.
                for (Map.Entry<String, DiskState> entry : aws.diskStatesToBeUpdatedByBucketName.entrySet()) {
                    // We need valid S3 bucket region in diskState in order to enumerate S3 tags. If we
                    // encounter a diskState with null region, we delete that disk, it will get re-enumerated
                    // with valid region in subsequent enumeration runs.
                    if (entry.getValue().regionId != null) {
                        aws.regionsByBucketName.put(entry.getValue().id, entry.getValue().regionId);
                    } else {
                        logWarning("Null region found in S3 diskState");
                        Operation.createDelete(aws.service.getHost(), entry.getValue().documentSelfLink)
                                .setReferer(aws.service.getUri())
                                .setBody(getDeletionState(Utils.getNowMicrosUtc()))
                                .setCompletion((o, e) -> {
                                    if (e != null) {
                                        logWarning("Exception deleting diskState with null region [ex=%s]",
                                                e.getMessage());
                                        return;
                                    }
                                    logWarning("Deleted diskState with null region [diskState=%s]",
                                            Utils.toJsonHtml(entry.getValue()));
                                })
                                .sendWith(aws.service);
                        continue;
                    }

                    s3Client = aws.clientManager
                            .getOrCreateS3Client(aws.endpointAuth, entry.getValue().regionId, aws.service,
                                    t -> aws.error = t);
                    if (aws.error != null) {
                        logSevere("Error getting AWS S3 client for [endpoint=%s] [region=%s] [ex=%s]",
                                aws.request.original.endpointLink, entry.getValue().regionId, aws.error.getMessage());
                        continue;
                    }

                    try {
                        bucketTaggingConfiguration = s3Client
                                .getBucketTaggingConfiguration(entry.getKey());

                        if (bucketTaggingConfiguration != null) {
                            aws.tagsByBucketName.put(entry.getKey(), new ConcurrentHashMap<>());

                            bucketTaggingConfiguration.getAllTagSets().stream().forEach(tagSet -> {
                                aws.tagsByBucketName.get(entry.getKey()).putAll(tagSet.getAllTags());
                            });
                        }
                    } catch (Exception e) {
                        logSevere("Exception enumerating tags for S3 bucket with known region " +
                                        "[endpoint=%s] [bucketName=%s - %s] [region=%s] [ex=%s]",
                                aws.request.original.endpointLink, entry.getKey(),
                                entry.getValue().id, entry.getValue().regionId, e.getMessage());
                        continue;
                    }
                }

                // This is the first time these buckets are being enumerated. Brute force and try to enumerate
                // tags for these buckets over every region until we find the correct region and then store it
                // in DiskState for future reference.
                for (Bucket bucket : aws.bucketsToBeCreated) {
                    for (Regions region : Regions.values()) {
                        s3Client = aws.clientManager
                                .getOrCreateS3Client(aws.endpointAuth, region.getName(), aws.service,
                                        t -> aws.error = t);
                        if (aws.error != null) {
                            logSevere("Error getting AWS S3 client for [endpoint=%s] [region=%s] [ex=%s]",
                                    aws.request.original.endpointLink, region.getName(), aws.error.getMessage());
                            continue;
                        }

                        try {
                            bucketTaggingConfiguration = s3Client
                                    .getBucketTaggingConfiguration(bucket.getName());

                            aws.regionsByBucketName.put(bucket.getName(), region.getName());

                            if (bucketTaggingConfiguration != null) {
                                aws.tagsByBucketName.put(bucket.getName(), new ConcurrentHashMap<>());

                                bucketTaggingConfiguration.getAllTagSets().stream().forEach(tagSet -> {
                                    aws.tagsByBucketName.get(bucket.getName()).putAll(tagSet.getAllTags());
                                });
                            }
                            break;
                        } catch (Exception e) {
                            // If AmazonS3Exception is thrown due to region mismatch, ignore it and continue.
                            // 301 or 403 is thrown when a client with a different region than S3 bucket
                            // calls getbucketTaggingConfiguration().
                            // 400 is thrown when a client in invalid region (such as government region) calls
                            // getbucketTaggingConfiguration().
                            if (e instanceof AmazonS3Exception && (((AmazonS3Exception) e).getStatusCode() ==
                                    Operation.STATUS_CODE_MOVED_PERM
                                    || ((AmazonS3Exception) e).getStatusCode() == Operation.STATUS_CODE_FORBIDDEN
                                    || ((AmazonS3Exception) e).getStatusCode() == Operation.STATUS_CODE_BAD_REQUEST)) {
                                continue;
                            } else {
                                logSevere("Exception enumerating tags for S3 bucket with unknown region " +
                                                "[endpoint=%s] [region=%s] [ex=%s]", aws.request.original.endpointLink,
                                        region.getName(), e.getMessage());
                                continue;
                            }
                        }
                    }
                }

                aws.subStage = next;
                handleReceivedEnumerationData(aws);
            }
        });
    }

    /**
     * Creates the disk states that represent the buckets received from AWS during
     * enumeration. Fields currently being enumerated for S3 are all immutable on AWS side, hence we only create
     * disks and don't patch to them in subsequent except for changes in tagLinks.
     */
    private void createDiskStates(S3StorageEnumerationContext aws,
            S3StorageEnumerationSubStage next) {
        // For all the disks to be created, we filter them based on whether we were able to find the correct
        // region for the disk using getBucketTaggingConfiguration() call and then map them and create operations.
        // Filtering is done to avoid creating disk states with null region (since we don't PATCH region field
        // after creating the disk, we need to ensure that disk state is initially created with the correct region).
        // kick off the operation using a JOIN
        List<DiskState> diskStatesToBeCreated = new ArrayList<>();

        aws.bucketsToBeCreated.stream()
                .filter(bucket -> aws.regionsByBucketName.containsKey(bucket.getName()))
                .forEach(bucket -> {
                    diskStatesToBeCreated.add(mapBucketToDiskState(bucket, aws));
                });
        diskStatesToBeCreated.forEach(diskState ->
                aws.enumerationOperations.add(
                        createPostOperation(this, diskState,
                                DiskService.FACTORY_LINK)));
        this.logFine(() -> String.format("Creating %d S3 disks",
                aws.bucketsToBeCreated.size()));

        // If internal type tag creation was successful, check if existing S3 disk states have that tag in tagLinks.
        // For those disk states which do not have the tagLink, add the tagLink by PATCHing those states.
        if (aws.internalTypeTagSelfLink != null) {
            aws.diskStatesToBeUpdatedByBucketName.entrySet().stream()
                    .filter(diskMap -> diskMap.getValue().tagLinks == null
                            || !diskMap.getValue().tagLinks.contains(aws.internalTypeTagSelfLink))
                    .forEach(diskMap -> {
                        Map<String, Collection<Object>> collectionsToAddMap = Collections.singletonMap
                                (DiskState.FIELD_NAME_TAG_LINKS,
                                        Collections.singletonList(aws.internalTypeTagSelfLink));
                        Map<String, Collection<Object>> collectionsToRemoveMap = Collections.singletonMap
                                (DiskState.FIELD_NAME_TAG_LINKS, Collections.emptyList());

                        ServiceStateCollectionUpdateRequest updateTagLinksRequest = ServiceStateCollectionUpdateRequest
                                .create(collectionsToAddMap, collectionsToRemoveMap);

                        aws.enumerationOperations.add(Operation.createPatch(this.getHost(),
                                diskMap.getValue().documentSelfLink)
                                .setReferer(aws.service.getUri())
                                .setBody(updateTagLinksRequest));
                    });
        }

        // update endpointLinks
        aws.diskStatesToBeUpdatedByBucketName.entrySet().stream()
                .filter(diskMap -> diskMap.getValue().endpointLinks == null
                        || !diskMap.getValue().endpointLinks.contains(aws.request.original.endpointLink))
                .forEach(diskMap -> {
                    Map<String, Collection<Object>> collectionsToAddMap = Collections.singletonMap
                            (DiskState.FIELD_NAME_ENDPOINT_LINKS,
                                    Collections.singletonList(aws.request.original.endpointLink));
                    Map<String, Collection<Object>> collectionsToRemoveMap = Collections.singletonMap
                            (DiskState.FIELD_NAME_ENDPOINT_LINKS, Collections.emptyList());

                    ServiceStateCollectionUpdateRequest updateEndpointLinksRequest = ServiceStateCollectionUpdateRequest
                            .create(collectionsToAddMap, collectionsToRemoveMap);

                    aws.enumerationOperations.add(Operation.createPatch(this.getHost(),
                            diskMap.getValue().documentSelfLink)
                            .setReferer(aws.service.getUri())
                            .setBody(updateEndpointLinksRequest));
                });

        OperationJoin.JoinedCompletionHandler joinCompletion = (ox,
                exc) -> {
            if (exc != null) {
                this.logSevere(() -> String.format("Error creating/updating disk %s",
                        Utils.toString(exc)));
                aws.subStage = S3StorageEnumerationSubStage.DELETE_DISKS;
                handleReceivedEnumerationData(aws);
                return;
            }

            ox.entrySet().stream()
                    .forEach(operationEntry -> {
                        aws.diskStatesEnumerated.add(operationEntry.getValue().getBody(DiskState.class));
                    });

            this.logFine(() -> "Successfully created and updated all the disk states.");
            aws.subStage = next;
            handleReceivedEnumerationData(aws);
        };

        if (aws.enumerationOperations.isEmpty()) {
            aws.subStage = next;
            handleReceivedEnumerationData(aws);
            return;
        }

        OperationJoin joinOp = OperationJoin.create(aws.enumerationOperations);
        joinOp.setCompletion(joinCompletion);
        joinOp.sendWith(this.getHost());
    }

    /**
     * Create tag states for S3 buckets. States are created only for newly discovered tags.
     */
    private DeferredResult<S3StorageEnumerationContext>
            createTagStatesAndUpdateTagLinks(S3StorageEnumerationContext aws) {
        aws.diskStatesEnumerated.addAll(aws.diskStatesToBeUpdatedByBucketName.values());

        List<DeferredResult<Set<String>>> updateCSTagLinksOps = new ArrayList<>();

        aws.diskStatesEnumerated.stream()
                .filter(diskState -> diskState.id != null && aws.tagsByBucketName.containsKey
                        (diskState.id))
                .forEach(diskState ->
                        updateCSTagLinksOps.add(TagsUtil.updateLocalTagStates(aws.service, diskState,
                                aws.tagsByBucketName.get(diskState.id), null)));

        return DeferredResult.allOf(updateCSTagLinksOps).thenApply(gnore -> aws);
    }

    /**
     * Deletes undiscovered resources.
     * <p>
     * The logic works by recording a timestamp when enumeration starts. This timestamp is used to
     * lookup resources which haven't been touched as part of current enumeration cycle.
     * <p>
     * Finally, delete on a resource is invoked only if it meets two criteria:
     * - Timestamp older than current enumeration cycle.
     * - S3 bucket is not present on AWS.
     * <p>
     * The method paginates through list of resources for deletion.
     */
    private void deleteDiskStates(S3StorageEnumerationContext aws, S3StorageEnumerationSubStage next) {
        Query.Builder qBuilder = Builder.create()
                .addKindFieldClause(DiskState.class)
                .addFieldClause(DiskState.FIELD_NAME_STORAGE_TYPE,
                        STORAGE_TYPE_S3)
                .addRangeClause(DiskState.FIELD_NAME_UPDATE_TIME_MICROS,
                        NumericRange
                                .createLessThanRange(aws.enumerationStartTimeInMicros));

        addScopeCriteria(qBuilder, aws);

        QueryTask q = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .setQuery(qBuilder.build())
                .setResultLimit(getQueryResultLimit())
                .build();
        q.tenantLinks = aws.parentCompute.tenantLinks;
        q.documentExpirationTimeMicros = Utils.getNowMicrosUtc() + QueryUtils.TEN_MINUTES_IN_MICROS;
        this.logFine(() -> "Querying disks for deletion");
        QueryUtils.startInventoryQueryTask(this, q)
                .whenComplete((queryTask, e) -> {
                    if (e != null) {
                        this.logWarning("Failure querying S3 disks for deletion for [endpoint=%s]",
                                aws.request.original.endpointLink);
                        aws.subStage = next;
                        handleReceivedEnumerationData(aws);
                        return;
                    }

                    if (queryTask.results.nextPageLink == null) {
                        this.logFine(() -> "No disk states match for deletion");
                        aws.subStage = next;
                        handleReceivedEnumerationData(aws);
                        return;
                    }
                    aws.deletionNextPageLink = queryTask.results.nextPageLink;
                    processDeletionRequest(aws, next);
                });
    }

    /**
     * Helper method to paginate through resources to be deleted.
     */
    private void processDeletionRequest(S3StorageEnumerationContext aws, S3StorageEnumerationSubStage next) {
        if (aws.deletionNextPageLink == null) {
            this.logFine(() -> "Finished deletion of disk states for AWS");
            aws.subStage = next;
            handleReceivedEnumerationData(aws);
            return;
        }
        this.logFine(() -> String.format("Querying page [%s] for resources to be deleted",
                aws.deletionNextPageLink));
        this.sendRequest(
                Operation.createGet(createInventoryUri(this.getHost(), aws.deletionNextPageLink))
                        .setCompletion((o, e) -> {
                            if (e != null) {
                                this.logWarning("Failure querying S3 disks for deletion for [endpoint=%s]",
                                        aws.request.original.endpointLink);
                                aws.subStage = next;
                                handleReceivedEnumerationData(aws);
                                return;
                            }
                            QueryTask queryTask = o.getBody(QueryTask.class);

                            aws.deletionNextPageLink = queryTask.results.nextPageLink;
                            // List<Operation> deleteOperations = new ArrayList<>();
                            for (Object s : queryTask.results.documents.values()) {
                                DiskState diskState = Utils
                                        .fromJson(s, DiskState.class);
                                // If the global list of ids does not contain this entity and it
                                // was not updated then delete it. This check is necessary as
                                // the document update timestamp/version does not change if
                                // there are no changes to the attributes of the entity during
                                // update.
                                if (aws.remoteBucketsByBucketName
                                        .get(diskState.id) == null) {
                                    Operation updateOp = PhotonModelUtils
                                            .createRemoveEndpointLinksOperation(
                                                    this,
                                                    aws.request.original.endpointLink,
                                                    diskState);

                                    if (updateOp != null) {
                                        updateOp.sendWith(getHost());
                                    }
                                }
                            }
                            processDeletionRequest(aws, next);
                        }));
    }

    /**
     * Map an S3 bucket to a photon-model disk state.
     */
    private DiskState mapBucketToDiskState(Bucket bucket, S3StorageEnumerationContext aws) {
        DiskState diskState = new DiskState();
        diskState.id = bucket.getName();
        diskState.name = bucket.getName();
        diskState.storageType = STORAGE_TYPE_S3;
        diskState.regionId = aws.regionsByBucketName.get(bucket.getName());
        diskState.authCredentialsLink = aws.endpointAuth.documentSelfLink;
        diskState.resourcePoolLink = aws.request.original.resourcePoolLink;
        diskState.endpointLink = aws.request.original.endpointLink;
        if (diskState.endpointLinks == null) {
            diskState.endpointLinks = new HashSet<>();
        }
        diskState.endpointLinks.add(aws.request.original.endpointLink);
        diskState.tenantLinks = aws.parentCompute.tenantLinks;
        diskState.computeHostLink = aws.parentCompute.documentSelfLink;
        diskState.tagLinks = new HashSet<>();

        if (bucket.getCreationDate() != null) {
            diskState.creationTimeMicros = TimeUnit.MILLISECONDS
                    .toMicros(bucket.getCreationDate().getTime());
        }

        if (bucket.getOwner() != null && bucket.getOwner().getDisplayName() != null) {
            diskState.customProperties = new HashMap<>();
            diskState.customProperties.put(BUCKET_OWNER_NAME, bucket.getOwner().getDisplayName());
        }

        // Set internal type tag for all S3 disk states only if POST for the TagState was successful.
        if (aws.internalTypeTagSelfLink != null) {
            diskState.tagLinks.add(aws.internalTypeTagSelfLink);
        }

        return diskState;
    }

    /**
     * Signals Enumeration Stop to the AWS enumeration adapter. The AWS enumeration adapter will
     * in turn patch the parent task to indicate completion.
     */
    private void signalStopToEnumerationAdapter(S3StorageEnumerationContext aws) {
        aws.request.original.enumerationAction = EnumerationAction.STOP;
        this.handleEnumerationRequest(aws);
    }

    /**
     * Signals error to the AWS enumeration adapter. The adapter will in turn clean up resources
     * and signal error to the parent task.
     */
    private void signalErrorToEnumerationAdapter(S3StorageEnumerationContext aws, Throwable t) {
        aws.error = t;
        aws.stage = S3StorageEnumerationStages.ERROR;
        this.handleEnumerationRequest(aws);
    }

    /**
     * Constrain every query with region and tenantLinks, if presented.
     */
    private static void addScopeCriteria(
            Query.Builder qBuilder,
            S3StorageEnumerationContext ctx) {
        // Add parentComputeHost criteria
        qBuilder.addFieldClause(ResourceState.FIELD_NAME_COMPUTE_HOST_LINK, ctx.request.parentCompute.documentSelfLink);
        // Add TENANT_LINKS criteria
        QueryUtils.addTenantLinks(qBuilder, ctx.parentCompute.tenantLinks);
    }

    /**
     * {@code deleteDisks} version suitable for chaining to
     * {@code DeferredResult.whenComplete}.
     */
    private BiConsumer<S3StorageEnumerationContext, Throwable> thenDiskDelete(S3StorageEnumerationContext aws,
            S3StorageEnumerationSubStage next) {
        // NOTE: In case of error 'ignoreCtx' is null so use passed context!
        return (ignoreCtx, exc) -> {
            if (exc != null) {
                logSevere("Exception while creating tag states and updating tag links for " +
                        "[endpoint=%s], [ex=%s]", aws.parentCompute.endpointLink, exc.getMessage());
            }
            aws.subStage = next;
            handleReceivedEnumerationData(aws);
        };
    }

}