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
import static com.vmware.photon.controller.model.adapters.util.AdapterUtils.createPatchOperation;
import static com.vmware.photon.controller.model.adapters.util.AdapterUtils.createPostOperation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.Bucket;

import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AwsClientType;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.ComputeEnumerateAdapterRequest;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
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
    private AWSClientManager clientManager;
    private ExecutorService executorService;

    public enum S3StorageEnumerationStages {
        CLIENT, ENUMERATE, ERROR
    }

    public enum S3StorageEnumerationSubStage {
        QUERY_LOCAL_RESOURCES, COMPARE, CREATE_UPDATE_DISK_STATES, DELETE_DISKS, ENUMERATION_STOP
    }

    public AWSS3StorageEnumerationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        this.clientManager = AWSClientManagerFactory
                .getClientManager(AwsClientType.S3);
    }

    /**
     * The enumeration service context that holds all the information needed to determine the list
     * of buckets that need to be represented in the system.
     */
    public static class S3StorageEnumerationContext {
        public AmazonS3Client amazonS3Client;
        public ComputeEnumerateAdapterRequest request;
        public AuthCredentialsService.AuthCredentialsServiceState parentAuth;
        public ComputeStateWithDescription parentCompute;
        public S3StorageEnumerationStages stage;
        public S3StorageEnumerationSubStage subStage;
        public Throwable error;
        public Map<String, Bucket> remoteAWSBuckets;
        public Map<String, DiskState> localDiskStateMap;
        public List<Bucket> bucketsToBeCreated;
        public Map<String, Bucket> bucketsToBeUpdated;
        public Map<String, DiskState> diskStatesToBeUpdated;
        public String deletionNextPageLink;
        public String localResourcesNextPageLink;
        public Operation operation;
        // The list of operations that have to created/updated as part of the S3 enumeration.
        public List<Operation> enumerationOperations;
        // The time stamp at which the enumeration started.
        public long enumerationStartTimeInMicros;

        public S3StorageEnumerationContext(ComputeEnumerateAdapterRequest request,
                Operation op) {
            this.operation = op;
            this.request = request;
            this.parentAuth = request.parentAuth;
            this.parentCompute = request.parentCompute;
            this.remoteAWSBuckets = new ConcurrentHashMap<>();
            this.localDiskStateMap = new ConcurrentHashMap<>();
            this.diskStatesToBeUpdated = new ConcurrentHashMap<>();
            this.bucketsToBeCreated = new ArrayList<>();
            this.bucketsToBeUpdated = new ConcurrentHashMap<>();
            this.enumerationOperations = new ArrayList<>();
            this.stage = S3StorageEnumerationStages.CLIENT;
            this.subStage = S3StorageEnumerationSubStage.QUERY_LOCAL_RESOURCES;
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
        AWSClientManagerFactory.returnClientManager(this.clientManager,
                AwsClientType.S3);
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
                logInfo(() -> String.format("Stopping S3 enumeration for %s",
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
        aws.amazonS3Client = this.clientManager.getOrCreateS3Client
                (aws.parentAuth, aws.request.regionId,this,t -> aws.error = t);
        if (aws.error != null) {
            aws.stage = S3StorageEnumerationStages.ERROR;
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
                        aws.remoteAWSBuckets.put(bucket.getName(), bucket);
                    }
                    OperationContext.restoreOperationContext(operationContext);
                    handleReceivedEnumerationData(aws);
                } catch (Exception e) {
                    if (e instanceof AmazonS3Exception && ((AmazonS3Exception) e)
                            .getStatusCode() == Operation.STATUS_CODE_FORBIDDEN) {
                        markClientInvalid(aws);
                    } else {
                        logSevere("Exception enumerating S3 buckets for [region=%s] [ex=%s]",
                                aws.request.regionId, e.getMessage());
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
        AWSClientManagerFactory.getClientManager(AwsClientType.S3)
                .markS3ClientInvalid(this, aws.parentAuth, aws.request.regionId);
        aws.operation.complete();
    }

    /**
     * State machine to handle S3 enumeration data received.
     */
    private void handleReceivedEnumerationData(S3StorageEnumerationContext aws) {
        switch (aws.subStage) {
        case QUERY_LOCAL_RESOURCES:
            getLocalResources(aws, S3StorageEnumerationSubStage.COMPARE);
            break;
        case COMPARE:
            compareLocalStateWithEnumerationData(aws, S3StorageEnumerationSubStage.CREATE_UPDATE_DISK_STATES);
            break;
        case CREATE_UPDATE_DISK_STATES:
            createOrUpdateDiskStates(aws, S3StorageEnumerationSubStage.DELETE_DISKS);
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
     * Query and get list of S3 buckets present locally in disk states in current context.
     */
    private void getLocalResources(S3StorageEnumerationContext aws, S3StorageEnumerationSubStage next) {
        // query all disk state resources for the cluster filtered by the received set of
        // instance Ids. the filtering is performed on the selected resource pool and auth
        // credentials link.
        if (aws.localResourcesNextPageLink == null) {
            Query.Builder qBuilder = Query.Builder.create()
                    .addKindFieldClause(DiskState.class)
                    .addFieldClause(DiskState.FIELD_NAME_AUTH_CREDENTIALS_LINK, aws.parentAuth.documentSelfLink)
                    .addFieldClause(DiskState.FIELD_NAME_STORAGE_TYPE, STORAGE_TYPE_S3)
                    .addInClause(ComputeState.FIELD_NAME_ID, aws.remoteAWSBuckets.keySet());

            addScopeCriteria(qBuilder, DiskState.class,aws);

            QueryTask queryTask = QueryTask.Builder.createDirectTask()
                    .setQuery(qBuilder.build())
                    .addOption(QueryOption.EXPAND_CONTENT)
                    .setResultLimit(getQueryResultLimit())
                    .build();
            queryTask.tenantLinks = aws.parentCompute.tenantLinks;

            QueryUtils.startQueryTask(this, queryTask).whenComplete((qrt, e) -> {
                if (e != null) {
                    this.logSevere(() -> String.format("Failure retrieving query" + " results: %s", e.toString()));
                    signalErrorToEnumerationAdapter(aws,e);
                    return;
                }
                qrt.results.documents.values().forEach(documentJson -> {
                    DiskState localDisk = Utils.fromJson(documentJson, DiskState.class);
                    aws.localDiskStateMap.put(localDisk.name, localDisk);

                });
                this.logFine(() -> String.format("%d S3 disk states found.",qrt.results.documentCount));

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
            Operation.createGet(UriUtils.buildUri(this.getHost().getUri(), aws.localResourcesNextPageLink))
                    .setReferer(this.getUri())
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            this.logSevere(() -> String.format("Failure retrieving query" + " results: %s",e.toString()));
                            signalErrorToEnumerationAdapter(aws,e);
                            return;
                        }
                        QueryTask qrt = o.getBody(QueryTask.class);
                        qrt.results.documents.values().forEach(documentJson -> {
                            DiskState localDisk = Utils.fromJson(documentJson, DiskState.class);
                            aws.localDiskStateMap.put(localDisk.name, localDisk);
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
        if (aws.remoteAWSBuckets == null
                || aws.remoteAWSBuckets.size() == 0) {
            this.logFine(() -> "No disks discovered on the remote system.");
            // no local disks
        } else if (aws.localDiskStateMap == null
                || aws.localDiskStateMap.size() == 0) {
            aws.remoteAWSBuckets.entrySet().forEach(
                    entry -> aws.bucketsToBeCreated
                            .add(entry.getValue()));
            // Compare local and remote state and find candidates for update and create.
        } else {
            for (String key : aws.remoteAWSBuckets.keySet()) {
                if (aws.localDiskStateMap.containsKey(key)) {
                    aws.bucketsToBeUpdated
                            .put(key, aws.remoteAWSBuckets.get(key));
                    aws.diskStatesToBeUpdated
                            .put(key, aws.localDiskStateMap.get(key));
                } else {
                    aws.bucketsToBeCreated.add(aws.remoteAWSBuckets.get(key));
                }
            }
        }
        aws.subStage = next;
        handleReceivedEnumerationData(aws);
    }

    /**
     * Creates the disk states that represent the buckets received from AWS during
     * enumeration.
     */
    private void createOrUpdateDiskStates(S3StorageEnumerationContext aws,
            S3StorageEnumerationSubStage next) {

        // For all the disks to be created..map them and create operations.
        // kick off the operation using a JOIN
        List<DiskState> diskStatesToBeCreated = new ArrayList<>();
        aws.bucketsToBeCreated.forEach(bucket -> {
            diskStatesToBeCreated.add(mapBucketToDiskState(bucket, aws));

        });
        diskStatesToBeCreated.forEach(diskState ->
                aws.enumerationOperations.add(
                        createPostOperation(this, diskState,
                                DiskService.FACTORY_LINK)));
        this.logFine(() -> String.format("Creating %d S3 disks",
                aws.bucketsToBeCreated.size()));

        // For all the disks to be updated, map the updated state from the received
        // volumes and issue patch requests against the existing disk state representations
        // in the system
        List<DiskState> diskStatesToBeUpdated = new ArrayList<>();
        aws.bucketsToBeUpdated.forEach((selfLink, bucket) -> {
            diskStatesToBeUpdated.add(mapBucketToDiskState(bucket, aws));

        });
        diskStatesToBeUpdated.forEach(diskState -> {
            aws.enumerationOperations.add(
                    createPatchOperation(this, diskState,
                            aws.localDiskStateMap.get(diskState.id).documentSelfLink));
        });

        this.logFine(() -> String.format("Updating %d disks",
                aws.bucketsToBeUpdated.size()));

        OperationJoin.JoinedCompletionHandler joinCompletion = (ox,
                exc) -> {
            if (exc != null) {
                this.logSevere(() -> String.format("Error creating/updating disk %s",
                        Utils.toString(exc)));
                signalErrorToEnumerationAdapter(aws, exc.values().iterator().next());
                return;
            }
            this.logFine(() -> "Successfully created and updated all the disk states.");
            aws.subStage = next;
            handleReceivedEnumerationData(aws);
        };
        OperationJoin joinOp = OperationJoin.create(aws.enumerationOperations);
        joinOp.setCompletion(joinCompletion);
        joinOp.sendWith(this.getHost());
    }

    /**
     * Map an S3 bucket to a photon-model disk state.
     */
    private DiskState mapBucketToDiskState(Bucket bucket, S3StorageEnumerationContext aws) {
        DiskState diskState = new DiskState();
        diskState.id = bucket.getName();
        diskState.name = bucket.getName();
        diskState.storageType = STORAGE_TYPE_S3;
        diskState.regionId = aws.request.regionId;
        diskState.authCredentialsLink = aws.parentAuth.documentSelfLink;
        diskState.resourcePoolLink = aws.request.original.resourcePoolLink;
        diskState.endpointLink = aws.request.original.endpointLink;
        diskState.tenantLinks = aws.parentCompute.tenantLinks;

        if (bucket.getCreationDate() != null) {
            diskState.creationTimeMicros = TimeUnit.MILLISECONDS
                    .toMicros(bucket.getCreationDate().getTime());
        }

        if (bucket.getOwner() != null && bucket.getOwner().getDisplayName() != null) {
            diskState.customProperties = new HashMap<>();
            diskState.customProperties.put(BUCKET_OWNER_NAME, bucket.getOwner().getDisplayName());
        }

        return diskState;
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
                .addFieldClause(DiskState.FIELD_NAME_AUTH_CREDENTIALS_LINK,
                        aws.parentAuth.documentSelfLink)
                .addFieldClause(DiskState.FIELD_NAME_STORAGE_TYPE,
                        STORAGE_TYPE_S3)
                .addRangeClause(DiskState.FIELD_NAME_UPDATE_TIME_MICROS,
                        NumericRange
                                .createLessThanRange(aws.enumerationStartTimeInMicros));

        addScopeCriteria(qBuilder, DiskState.class, aws);

        QueryTask q = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .setQuery(qBuilder.build())
                .setResultLimit(getQueryResultLimit())
                .build();
        q.tenantLinks = aws.parentCompute.tenantLinks;
        q.documentExpirationTimeMicros = Utils.getNowMicrosUtc() + QueryUtils.TEN_MINUTES_IN_MICROS;
        this.logFine(() -> "Querying disks for deletion");
        QueryUtils.startQueryTask(this, q)
                .whenComplete((queryTask, e) -> {
                    if (e != null) {
                        signalErrorToEnumerationAdapter(aws, e);
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
                        Operation.createGet(this, aws.deletionNextPageLink)
                                .setCompletion((o, e) -> {
                                    if (e != null) {
                                        signalErrorToEnumerationAdapter(aws, e);
                                        return;
                                    }
                                    QueryTask queryTask = o.getBody(QueryTask.class);

                                    aws.deletionNextPageLink = queryTask.results.nextPageLink;
                                    List<Operation> deleteOperations = new ArrayList<>();
                                    for (Object s : queryTask.results.documents.values()) {
                                        DiskState diskState = Utils
                                                .fromJson(s, DiskState.class);
                                        // If the global list of ids does not contain this entity and it
                                        // was not updated then delete it. This check is necessary as
                                        // the document update timestamp/version does not change if
                                        // there are no changes to the attributes of the entity during
                                        // update.
                                        if (aws.remoteAWSBuckets
                                                .get(diskState.id) == null) {
                                            deleteOperations
                                                    .add(Operation.createDelete(this,
                                                            diskState.documentSelfLink));
                                        }
                                    }
                                    this.logFine(() -> String.format("Deleting %d disks",
                                            deleteOperations.size()));
                                    if (deleteOperations.size() == 0) {
                                        this.logFine(() -> "No disk states to be deleted");
                                        processDeletionRequest(aws, next);
                                        return;
                                    }
                                    OperationJoin.create(deleteOperations)
                                            .setCompletion((ops, exs) -> {
                                                if (exs != null) {
                                                    // We don't want to fail the whole data collection
                                                    // if some of the operation fails.
                                                    exs.values().forEach(
                                                            ex -> this
                                                                    .logWarning(() ->
                                                                            String.format("Error: %s",
                                                                                    ex.getMessage())));
                                                }
                                                processDeletionRequest(aws, next);
                                            })
                                            .sendWith(this);
                                }));

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
     * Constrain every query with endpointLink and tenantLinks, if presented.
     */
    private static void addScopeCriteria(
            Query.Builder qBuilder,
            Class<? extends ResourceState> stateClass,
            S3StorageEnumerationContext ctx) {
        // Add TENANT_LINKS criteria
        QueryUtils.addTenantLinks(qBuilder, ctx.parentCompute.tenantLinks);
        // Add ENDPOINT_LINK criteria
        QueryUtils.addEndpointLink(qBuilder, stateClass, ctx.request.original.endpointLink);
    }
}
