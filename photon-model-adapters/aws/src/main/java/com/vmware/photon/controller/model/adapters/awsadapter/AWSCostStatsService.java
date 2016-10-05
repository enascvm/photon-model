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

package com.vmware.photon.controller.model.adapters.awsadapter;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressEventType;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.TransferManager;

import org.joda.time.LocalDate;

import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse.ComputeStats;
import com.vmware.photon.controller.model.adapters.aws.dto.AwsAccountDetailDto;
import com.vmware.photon.controller.model.adapters.aws.dto.AwsResourceDetailDto;
import com.vmware.photon.controller.model.adapters.aws.dto.AwsServiceDetailDto;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSCsvBillParser;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSStatsNormalizer;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceStatsCollectionTaskState;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceTaskCollectionStage;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Service to gather AWS Cost related stats
 */
public class AWSCostStatsService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_COST_STATS_ADAPTER;

    public static final String TEMP_DIR_LOCATION = "java.io.tmpdir";
    public static final String DIMENSION_CURRENCY_VALUE = "USD";
    public static final String TOTAL_COST = "cost";

    private AWSClientManager clientManager;

    public static enum AWSCostStatsCreationStages {
        ACCOUNT_DETAILS, CLIENT, DOWNLOAD_THEN_PARSE, QUERY_LOCAL_RESOURCES, CREATE_STATS, POST_STATS
    }

    public AWSCostStatsService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.S3);
    }

    protected class AWSCostStatsCreationContext {
        public ComputeStateWithDescription computeDesc;
        public AuthCredentialsService.AuthCredentialsServiceState parentAuth;
        public ComputeStatsRequest statsRequest;
        public ComputeStatsResponse statsResponse;
        public TransferManager s3Client;
        public List<String> ignorableInvoiceCharge;
        public String awsAccountId;
        public AWSCostStatsCreationStages stage;
        public Map<String, AwsAccountDetailDto> accountDetailsMap;
        Map<String, ComputeState> awsInstancesById;

        public AWSCostStatsCreationContext(ComputeStatsRequest statsRequest) {
            this.statsRequest = statsRequest;
            this.stage = AWSCostStatsCreationStages.ACCOUNT_DETAILS;
            this.ignorableInvoiceCharge = new ArrayList<>();
            this.awsInstancesById = new ConcurrentSkipListMap<String, ComputeState>();
            this.statsResponse = new ComputeStatsResponse();
            this.statsResponse.statsList = new ArrayList<>();
        }
    }

    @Override
    public void handleStop(Operation delete) {
        AWSClientManagerFactory.returnClientManager(this.clientManager,
                AWSConstants.AwsClientType.S3);
        super.handleStop(delete);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        op.complete();
        ComputeStatsRequest statsRequest = op.getBody(ComputeStatsRequest.class);
        if (statsRequest.isMockRequest) {
            // patch status to parent task
            AdapterUtils.sendPatchToProvisioningTask(this, statsRequest.taskReference);
            return;
        }
        AWSCostStatsCreationContext statsData = new AWSCostStatsCreationContext(statsRequest);
        handleCostStatsCreationRequest(statsData);

    }

    protected void handleCostStatsCreationRequest(AWSCostStatsCreationContext statsData) {
        switch (statsData.stage) {
        case ACCOUNT_DETAILS:
            getAccountDescription(statsData, AWSCostStatsCreationStages.CLIENT);
            break;
        case CLIENT:
            getAWSAsyncCostingClient(statsData, AWSCostStatsCreationStages.DOWNLOAD_THEN_PARSE);
            break;
        case DOWNLOAD_THEN_PARSE:
            scheduleDownload(statsData, AWSCostStatsCreationStages.QUERY_LOCAL_RESOURCES);
            break;
        case QUERY_LOCAL_RESOURCES:
            queryInstances(statsData, AWSCostStatsCreationStages.CREATE_STATS);
            break;
        case CREATE_STATS:
            createStats(statsData, AWSCostStatsCreationStages.POST_STATS);
            break;
        case POST_STATS:
            postAllResourcesCostStats(statsData);
            break;
        default:
            logSevere("Unknown AWS Cost Stats enumeration stage %s ", statsData.stage.toString());
            AdapterUtils.sendFailurePatchToProvisioningTask(this,
                    statsData.statsRequest.taskReference,
                    new Exception("Unknown AWS Cost Stats enumeration stage"));
            break;
        }
    }

    protected void getAccountDescription(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages next) {
        Consumer<Operation> onSuccess = (op) -> {
            statsData.computeDesc = op.getBody(ComputeStateWithDescription.class);
            getParentAuth(statsData, next);
        };
        URI computeUri = UriUtils.extendUriWithQuery(statsData.statsRequest.resourceReference,
                UriUtils.URI_PARAM_ODATA_EXPAND, Boolean.TRUE.toString());
        AdapterUtils.getServiceState(this, computeUri, onSuccess, getFailureConsumer(statsData));
    }

    protected void getParentAuth(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages next) {
        Consumer<Operation> onSuccess = (op) -> {
            statsData.parentAuth = op.getBody(AuthCredentialsServiceState.class);
            statsData.stage = next;
            handleCostStatsCreationRequest(statsData);
        };
        String authLink = statsData.computeDesc.description.authCredentialsLink;
        AdapterUtils.getServiceState(this, authLink, onSuccess, getFailureConsumer(statsData));
    }

    protected void getAWSAsyncCostingClient(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages next) {
        URI parentURI = statsData.statsRequest.taskReference;
        statsData.s3Client = this.clientManager.getOrCreateS3AsyncClient(statsData.parentAuth, null,
                this, parentURI);
        statsData.stage = next;
        handleCostStatsCreationRequest(statsData);
    }

    protected void scheduleDownload(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages next) {
        //Starting creation of cost stats for current month
        String billsBucketName = statsData.computeDesc.customProperties
                .getOrDefault(AWSConstants.AWS_BILLS_S3_BUCKET_NAME_KEY, null);
        if (billsBucketName == null) {
            logInfo("Bills Bucket name is not configured for this account. Not collecting cost stats.");
            return;
        }
        try {
            downloadAndParse(statsData, billsBucketName, LocalDate.now().getYear(),
                    LocalDate.now().getMonthOfYear(), next);
        } catch (IOException e) {
            logSevere(e);
            AdapterUtils.sendFailurePatchToProvisioningTask(this,
                    statsData.statsRequest.taskReference, e);
        }
    }

    protected void queryInstances(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages next) {
        Query query = Query.Builder.create().addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK,
                        statsData.statsRequest.resourceLink(),
                        Occurance.MUST_OCCUR)
                .build();
        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT).setQuery(query)
                .build();
        queryTask.setDirect(true);
        queryTask.tenantLinks = statsData.computeDesc.description.tenantLinks;
        queryTask.documentSelfLink = UUID.randomUUID().toString();
        sendRequest(
                Operation.createPost(getHost(), ServiceUriPaths.CORE_QUERY_TASKS).setBody(queryTask)
                        .setConnectionSharing(true).setCompletion((o, e) -> {
                            if (e != null) {
                                logSevere(e);
                                AdapterUtils.sendFailurePatchToProvisioningTask(this,
                                        statsData.statsRequest.taskReference, e);
                                return;
                            }
                            QueryTask responseTask = o.getBody(QueryTask.class);
                            for (Object s : responseTask.results.documents.values()) {
                                ComputeState localInstance = Utils.fromJson(s,
                                        ComputeService.ComputeState.class);
                                statsData.awsInstancesById.put(localInstance.id, localInstance);
                            }
                            logFine("Got result of the query to get local resources in Cost Adapter. There are %d instances known to the system.",
                                    responseTask.results.documentCount);
                            statsData.stage = next;
                            handleCostStatsCreationRequest(statsData);
                        }));
    }

    private void createStats(AWSCostStatsCreationContext statsData,
            AWSCostStatsCreationStages next) {
        int count = 0;

        for (String account : statsData.accountDetailsMap.keySet()) {
            AwsAccountDetailDto awsAccountDetailDto = statsData.accountDetailsMap.get(account);

            if (awsAccountDetailDto.id.equalsIgnoreCase(statsData.awsAccountId)) {
                // As of now we are creating stats for the primary account only.
                // TODO: handle the stats of linked accounts.
                ComputeStats accountStats = createComputeStatsForAccount(
                        statsData.computeDesc.documentSelfLink, awsAccountDetailDto);
                statsData.statsResponse.statsList.add(accountStats);
            }

            // create resouce stats for only live EC2 instances that exist in system
            Map<String, AwsServiceDetailDto> serviceDetails = awsAccountDetailDto.serviceDetailsMap;
            for (String service : serviceDetails.keySet()) {
                if (!service.equalsIgnoreCase(AWSCsvBillParser.AwsServices.ec2.getName())) {
                    // Instance Costs are present only with EC2 service.
                    continue;
                }
                Map<String, AwsResourceDetailDto> resourceDetailsMap = serviceDetails
                        .get(service).resourceDetailsMap;
                if (resourceDetailsMap == null) {
                    continue;
                }
                for (Entry<String, AwsResourceDetailDto> entry : resourceDetailsMap.entrySet()) {
                    String resourceName = entry.getKey();
                    ComputeState computeState = statsData.awsInstancesById.get(resourceName);
                    if (computeState == null) {
                        logFine("Skipping creating costs for AWS instance {} since its compute link does not exist.",
                                resourceName);
                        continue;
                    }
                    AwsResourceDetailDto resourceDetails = entry.getValue();
                    if (resourceDetails.directCosts != null) {
                        ComputeStats vmStats = createComputeStatsForResource(
                                computeState.documentSelfLink, resourceDetails);
                        statsData.statsResponse.statsList.add(vmStats);
                        count++;
                    }
                }
            }
        }
        logFine("Created Stats for %d instances", count);
        statsData.stage = next;
        handleCostStatsCreationRequest(statsData);
    }

    private void postAllResourcesCostStats(AWSCostStatsCreationContext statsData) {
        SingleResourceStatsCollectionTaskState respBody = new SingleResourceStatsCollectionTaskState();
        respBody.taskStage = (SingleResourceTaskCollectionStage) statsData.statsRequest.nextStage;
        respBody.statsAdapterReference = UriUtils.buildUri(getHost(), SELF_LINK);
        respBody.statsList = statsData.statsResponse.statsList;
        respBody.computeLink = statsData.computeDesc.documentSelfLink;
        sendRequest(Operation.createPatch(statsData.statsRequest.taskReference).setBody(respBody));
    }

    private void downloadAndParse(AWSCostStatsCreationContext statsData,
            String awsBucketname,
            int year,
            int month, AWSCostStatsCreationStages next) throws IOException {

        // Creating a working directory for downloanding and processing the bill
        final Path workingDirPath = Paths.get(System.getProperty(TEMP_DIR_LOCATION),
                UUID.randomUUID().toString());
        Files.createDirectories(workingDirPath);

        String accountId = getAccountId(statsData.parentAuth);
        statsData.awsAccountId = accountId;
        AWSCsvBillParser parser = new AWSCsvBillParser();
        final String csvBillZipFileName = parser.getCsvBillFileName(month, year, accountId, true);

        Path csvBillZipFilePath = Paths.get(workingDirPath.toString(), csvBillZipFileName);
        GetObjectRequest getObjectRequest = new GetObjectRequest(awsBucketname, csvBillZipFileName);
        Download download = statsData.s3Client.download(getObjectRequest,
                csvBillZipFilePath.toFile());

        final StatelessService service = this;
        download.addProgressListener(new ProgressListener() {
            @Override
            public void progressChanged(ProgressEvent progressEvent) {
                try {
                    ProgressEventType eventType = progressEvent.getEventType();
                    if (ProgressEventType.TRANSFER_COMPLETED_EVENT.equals(eventType)) {
                        LocalDate monthDate = new LocalDate(year, month, 1);
                        statsData.accountDetailsMap = parser.parseDetailedCsvBill(
                                statsData.ignorableInvoiceCharge,
                                csvBillZipFilePath,
                                monthDate);
                        deleteTempFiles();
                        statsData.stage = next;
                        handleCostStatsCreationRequest(statsData);
                    } else if (ProgressEventType.TRANSFER_FAILED_EVENT.equals(eventType)) {
                        deleteTempFiles();
                        throw new IOException(
                                "Download of AWS CSV Bill '" + csvBillZipFileName + "' failed.");
                    }
                } catch (IOException e) {
                    logSevere(e);
                    AdapterUtils.sendFailurePatchToProvisioningTask(service,
                            statsData.statsRequest.taskReference, e);
                }
            }

            private void deleteTempFiles() {
                try {
                    Files.deleteIfExists(csvBillZipFilePath);
                    Files.deleteIfExists(workingDirPath);
                } catch (IOException e) {
                    // Ignore IO exception while cleaning files.
                }
            }
        });
    }

    private ComputeStats createComputeStatsForResource(String resourceComputeLink,
            AwsResourceDetailDto resourceDetails) {
        ComputeStats resourceStats = new ComputeStats();
        resourceStats.statValues = new ConcurrentSkipListMap<>();
        resourceStats.computeLink = resourceComputeLink;
        List<ServiceStat> resourceServiceStats = new ArrayList<>();
        for (Entry<Long, Double> cost : resourceDetails.directCosts.entrySet()) {
            ServiceStat resourceStat = new ServiceStat();
            resourceStat.serviceReference = UriUtils.buildUri(resourceComputeLink);
            resourceStat.latestValue = cost.getValue().doubleValue();
            resourceStat.sourceTimeMicrosUtc = TimeUnit.MILLISECONDS.toMicros(cost.getKey());
            resourceStat.unit = AWSStatsNormalizer.getNormalizedUnitValue(DIMENSION_CURRENCY_VALUE);
            resourceStat.name = TOTAL_COST;
            resourceServiceStats.add(resourceStat);
        }
        resourceStats.statValues.put(TOTAL_COST, resourceServiceStats);
        return resourceStats;
    }

    private ComputeStats createComputeStatsForAccount(String accountComputeLink,
            AwsAccountDetailDto awsAccountDetailDto) {
        ServiceStat stat = new ServiceStat();
        stat.serviceReference = UriUtils.buildUri(accountComputeLink);
        stat.latestValue = awsAccountDetailDto.cost;
        stat.sourceTimeMicrosUtc = TimeUnit.MILLISECONDS.toMicros(awsAccountDetailDto.month);
        stat.unit = AWSStatsNormalizer.getNormalizedUnitValue(DIMENSION_CURRENCY_VALUE);
        stat.name = TOTAL_COST;
        ComputeStats accountStats = new ComputeStats();
        accountStats.statValues = new ConcurrentSkipListMap<>();
        accountStats.computeLink = accountComputeLink;
        accountStats.statValues.put(stat.name, Collections.singletonList(stat));
        return accountStats;
    }

    /**
     * Method gets the aws accountId from the basic credentials given by user using ARN of the account.
     * @param credentials
     * @return
     */
    private String getAccountId(AuthCredentialsServiceState credentials) {
        AWSCredentials awsCredentials = new BasicAWSCredentials(credentials.privateKeyId,
                credentials.privateKey);
        AmazonIdentityManagementClient iamClient = new AmazonIdentityManagementClient(
                awsCredentials);
        String userId = null;
        try {
            String arn = iamClient.getUser().getUser().getArn();
            /*
             *  arn:aws:service:region:account:resource -> so limiting the split to 6 words and extracting the accountId which is 5th one in list.
             *  If the user is not authorized to perform iam:GetUser on that resource,still error mesage will have accountId
             */
            userId = arn.split(":", 6)[4];
        } catch (AmazonServiceException ex) {
            if (ex.getErrorCode().compareTo("AccessDenied") == 0) {
                String msg = ex.getMessage();
                userId = msg.split(":", 7)[5];
            }
        }
        return userId;
    }

    private Consumer<Throwable> getFailureConsumer(AWSCostStatsCreationContext statsData) {
        return ((t) -> {
            AdapterUtils.sendFailurePatchToProvisioningTask(this,
                    statsData.statsRequest.taskReference, t);
        });
    }
}
