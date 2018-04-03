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

import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.ARN_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.EXTERNAL_ID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.REGION_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.ZONE_KEY;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.getEc2AsyncClient;
import static com.vmware.photon.controller.model.adapters.util.AdapterConstants.PHOTON_MODEL_ADAPTER_UNAUTHORIZED_MESSAGE;
import static com.vmware.photon.controller.model.adapters.util.AdapterConstants.PHOTON_MODEL_ADAPTER_UNAUTHORIZED_MESSAGE_CODE;
import static com.vmware.xenon.common.Operation.STATUS_CODE_UNAUTHORIZED;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesRequest;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;

import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.RequestType;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSDeferredResultAsyncHandler;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.EndpointAdapterUtils;
import com.vmware.photon.controller.model.adapters.util.EndpointAdapterUtils.Retriever;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;

/**
 * Adapter to validate and enhance AWS based endpoints.
 */
public class AWSEndpointAdapterService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_ENDPOINT_CONFIG_ADAPTER;

    public static final String UNABLE_TO_VALIDATE_CREDENTIALS_IN_ANY_AWS_REGION =
            "Unable to validate credentials in any AWS region!";

    private AWSClientManager clientManager;

    @Override
    public void handleStart(Operation op) {
        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.EC2);

        super.handleStart(op);
    }

    @Override
    public void handleStop(Operation op) {
        AWSClientManagerFactory.returnClientManager(this.clientManager,
                AWSConstants.AwsClientType.EC2);

        super.handleStop(op);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        EndpointConfigRequest body = op.getBody(EndpointConfigRequest.class);
        if (body.requestType == RequestType.CHECK_IF_ACCOUNT_EXISTS) {
            checkIfAccountExistsAndGetExistingDocuments(body, op);
            return;
        }

        EndpointAdapterUtils.handleEndpointRequest(this, op, body, credentials(),
                computeDesc(), compute(), endpoint(), validate(body));
    }


    private BiConsumer<AuthCredentialsServiceState, BiConsumer<ServiceErrorResponse, Throwable>> validate(
            EndpointConfigRequest body) {

        return (credentials, callback) -> {
            String regionId = body.endpointProperties.get(REGION_KEY) == null ?
                    Regions.DEFAULT_REGION.getName() :
                    body.endpointProperties.get(REGION_KEY);

            validateEndpointUniqueness(credentials, body.checkForEndpointUniqueness, body.tenantLinks)
                    .thenCompose(aVoid -> validateCredentialsWithRegions(credentials, regionId))
                    .whenComplete((aVoid, e) -> {
                        if (e != null) {
                            if (e instanceof CompletionException) {
                                e = e.getCause();
                            }

                            ServiceErrorResponse r = Utils.toServiceErrorResponse(e);
                            callback.accept(r, e);
                            return;
                        }
                        callback.accept(null, null);
                    });
        };
    }


    private DeferredResult<Void> validateCredentialsWithRegions(
            AuthCredentialsServiceState credentials, String endpointRegion) {

        AtomicInteger index = new AtomicInteger(0);
        Regions[] regions = Regions.values();

        int epRegionIndex = Arrays.stream(regions)
                .map(Regions::getName)
                .collect(Collectors.toList())
                .indexOf(endpointRegion);

        // if found, swap defaultRegion with the first region to optimize
        if (epRegionIndex != -1) {
            Regions temp = regions[0];
            regions[0] = regions[epRegionIndex];
            regions[epRegionIndex] = temp;
        }

        DeferredResult<Void> deferredResult = new DeferredResult<>();
        validateCredentialsWithRegions(
                credentials, index, regions, deferredResult);
        return  deferredResult;
    }


    /**
     * Method to validate credentials until atleast one region returns success. Validation fails if
     * unable to validate in any region.
     */
    private void validateCredentialsWithRegions(
            AuthCredentialsServiceState credentials, AtomicInteger index,
            Regions[] regions, DeferredResult<Void> deferredResult) {

        if (index.get() >= regions.length) {
            //Unable to validate in any of the Regions.
            deferredResult.fail(new LocalizableValidationException(
                    UNABLE_TO_VALIDATE_CREDENTIALS_IN_ANY_AWS_REGION,
                    PHOTON_MODEL_ADAPTER_UNAUTHORIZED_MESSAGE,
                    PHOTON_MODEL_ADAPTER_UNAUTHORIZED_MESSAGE_CODE));
            return;
        }

        String region = regions[index.get()].getName();
        getEc2AsyncClient(credentials, region, this.clientManager.getExecutor())
                .thenCompose(this::validateCredentials)
                .whenComplete((res,e) -> {
                    if (e == null) {
                        //Validation succeeded in the region
                        deferredResult.complete((Void) null);
                        return;
                    }

                    if (!(e.getCause() instanceof LocalizableValidationException)) {
                        deferredResult.fail(new LocalizableValidationException(e,
                                PHOTON_MODEL_ADAPTER_UNAUTHORIZED_MESSAGE,
                                PHOTON_MODEL_ADAPTER_UNAUTHORIZED_MESSAGE_CODE));
                        return;
                    }

                    index.getAndIncrement();
                    validateCredentialsWithRegions(credentials, index, regions, deferredResult);
                });
    }


    private DeferredResult<Void> validateCredentials(AmazonEC2AsyncClient client) {
        AWSDeferredResultAsyncHandler<DescribeAvailabilityZonesRequest,
                DescribeAvailabilityZonesResult> asyncHandler =
                new AWSDeferredResultAsyncHandler<>(this, "Validate Credentials");

        client.describeAvailabilityZonesAsync(asyncHandler);

        return asyncHandler
                .toDeferredResult()
                .handle((describeAvailabilityZonesResult, e) -> {
                    if (e instanceof AmazonServiceException) {
                        AmazonServiceException ase = (AmazonServiceException) e;
                        if (ase.getStatusCode() == STATUS_CODE_UNAUTHORIZED) {

                            throw new LocalizableValidationException(
                                    e,
                                    PHOTON_MODEL_ADAPTER_UNAUTHORIZED_MESSAGE,
                                    PHOTON_MODEL_ADAPTER_UNAUTHORIZED_MESSAGE_CODE);
                        }
                    }
                    return null;
                });
    }

    /**
     * Validate that the endpoint is unique by comparing the Access key ID
     */
    private DeferredResult<Void> validateEndpointUniqueness(AuthCredentialsServiceState credentials,
            Boolean endpointUniqueness, List<String> queryTaskTenantLinks) {
        if (Boolean.TRUE.equals(endpointUniqueness)) {
            Query authQuery = Builder.create()
                    .addFieldClause(PRIVATE_KEYID_KEY, credentials.privateKeyId).build();

            return EndpointAdapterUtils.validateEndpointUniqueness(this.getHost(), authQuery,
                    null, EndpointType.aws.name(), queryTaskTenantLinks);
        }
        return DeferredResult.completed(null);
    }

    private BiConsumer<AuthCredentialsServiceState, Retriever> credentials() {
        return (c, r) -> {
            // overwrite fields that are set in endpointProperties, otherwise use the present ones
            if (c.privateKey != null) {
                r.get(PRIVATE_KEY_KEY).ifPresent(pKey -> c.privateKey = pKey);
            } else {
                c.privateKey = r.getRequired(PRIVATE_KEY_KEY);
            }
            if (c.privateKeyId != null) {
                r.get(PRIVATE_KEYID_KEY).ifPresent(pKeyId -> c.privateKeyId = pKeyId);
            } else {
                c.privateKeyId = r.getRequired(PRIVATE_KEYID_KEY);
            }
            c.customProperties = new HashMap<>();
            r.get(ARN_KEY).ifPresent(arn -> {
                c.customProperties.put(ARN_KEY, arn);
            });
            r.get(EXTERNAL_ID_KEY).ifPresent(externalId -> {
                c.customProperties.put(EXTERNAL_ID_KEY, externalId);
            });
            c.type = "accessKey";
        };
    }

    private BiConsumer<ComputeDescription, Retriever> computeDesc() {
        return (cd, r) -> {
            cd.regionId = r.get(REGION_KEY).orElse(null);
            cd.zoneId = r.get(ZONE_KEY).orElse(null);
            cd.environmentName = ComputeDescription.ENVIRONMENT_NAME_AWS;

            List<String> children = new ArrayList<>();
            children.add(ComputeType.ZONE.toString());
            cd.supportedChildren = children;

            cd.instanceAdapterReference = AdapterUriUtil.buildPublicAdapterUri(getHost(),
                    AWSUriPaths.AWS_INSTANCE_ADAPTER);
            cd.enumerationAdapterReference = AdapterUriUtil.buildPublicAdapterUri(getHost(),
                    AWSUriPaths.AWS_ENUMERATION_ADAPTER);
            cd.powerAdapterReference = AdapterUriUtil.buildPublicAdapterUri(getHost(),
                    AWSUriPaths.AWS_POWER_ADAPTER);
            cd.diskAdapterReference = AdapterUriUtil.buildPublicAdapterUri(getHost(),
                    AWSUriPaths.AWS_DISK_ADAPTER);

            {
                URI statsAdapterUri = AdapterUriUtil.buildPublicAdapterUri(getHost(),
                        AWSUriPaths.AWS_STATS_ADAPTER);
                URI costStatsAdapterUri = AdapterUriUtil.buildPublicAdapterUri(getHost(),
                        AWSUriPaths.AWS_COST_STATS_ADAPTER);

                cd.statsAdapterReferences = new LinkedHashSet<>();
                cd.statsAdapterReferences.add(costStatsAdapterUri);
                cd.statsAdapterReferences.add(statsAdapterUri);
                cd.statsAdapterReference = statsAdapterUri;
            }
        };
    }

    private BiConsumer<ComputeState, Retriever> compute() {
        return (c, r) -> {
            StringBuffer b = new StringBuffer("https://ec2.");
            b.append(r.get(REGION_KEY).orElse(""));
            b.append(".amazonaws.com");

            c.type = ComputeType.ENDPOINT_HOST;
            c.regionId = r.get(REGION_KEY).orElse(null);
            c.environmentName = ComputeDescription.ENVIRONMENT_NAME_AWS;
            c.adapterManagementReference = UriUtils.buildUri(b.toString());
            String billsBucketName = r.get(AWSConstants.AWS_BILLS_S3_BUCKET_NAME_KEY).orElse(null);
            if (billsBucketName != null) {
                addEntryToCustomProperties(c, AWSConstants.AWS_BILLS_S3_BUCKET_NAME_KEY,
                        billsBucketName);
            }

            String isAutoDiscoveryEnabled = r
                    .get(PhotonModelConstants.IS_RESOURCE_AUTO_DISCOVERY_ENABLED).orElse(null);
            if (isAutoDiscoveryEnabled != null) {
                addEntryToCustomProperties(c,
                        PhotonModelConstants.IS_RESOURCE_AUTO_DISCOVERY_ENABLED,
                        isAutoDiscoveryEnabled);
            }

            Boolean mock = Boolean.valueOf(r.getRequired(EndpointAdapterUtils.MOCK_REQUEST));
            if (!mock) {
                String accountId = getAccountId(r.get(ARN_KEY).orElse(null),
                        r.get(PRIVATE_KEYID_KEY).orElse(null),
                        r.get(PRIVATE_KEY_KEY).orElse(null));
                if (accountId != null && !accountId.isEmpty()) {
                    addEntryToCustomProperties(c, AWSConstants.AWS_ACCOUNT_ID_KEY, accountId);
                    EndpointState es = new EndpointState();
                    es.endpointProperties = new HashMap<>();
                    es.endpointProperties.put(AWSConstants.AWS_ACCOUNT_ID_KEY, accountId);
                    String endpointReference = r
                            .getRequired(EndpointAdapterUtils.ENDPOINT_REFERENCE_URI);
                    Operation.createPatch(UriUtils.buildUri(endpointReference)).setReferer(getUri())
                            .setBody(es).sendWith(this);
                }
            }
        };
    }

    private BiConsumer<EndpointState, Retriever> endpoint() {
        return (e, r) -> {
            e.endpointProperties.put(EndpointConfigRequest.REGION_KEY,
                    r.get(REGION_KEY).orElse(null));
            e.endpointProperties.put(EndpointConfigRequest.PRIVATE_KEYID_KEY,
                    r.getRequired(PRIVATE_KEYID_KEY));

            // AWS end-point does support public images enumeration
            e.endpointProperties.put(EndpointConfigRequest.SUPPORT_PUBLIC_IMAGES,
                    Boolean.TRUE.toString());
        };
    }

    private void addEntryToCustomProperties(ComputeState c, String key, String value) {
        if (c.customProperties == null) {
            c.customProperties = new HashMap<>();
        }
        c.customProperties.put(key, value);
    }

    /**
     * Method to get the aws account ID from the specified credentials. If the ARN is set, it will
     * retrieve the account ID from the ARN directly. Otherwise, will attempt via the private key ID
     * and private key.
     * @param arn
     *         An Amazon Resource Name
     * @param privateKeyId
     *         An AWS account private key ID.
     * @param privateKey
     *         An AWS account private key.
     * @return
     */
    private String getAccountId(String arn, String privateKeyId, String privateKey) {
        if (arn != null) {
            return getAccountId(arn);
        }

        return getAccountId(privateKeyId, privateKey);
    }

    /**
     * Splits the ARN key to retrieve the account ID.
     * <p>
     * An ARN is of the format arn:aws:service:region:account:resource -> so limiting the split to
     * 6 words and extracting the accountId which is 5th one in list. If the user is not authorized
     * to perform iam:GetUser on that resource,still error mesage will have accountId
     * @param arn
     *         An Amazon Resource Name.
     * @return The account ID.
     */
    private String getAccountId(String arn) {
        if (arn == null) {
            return null;
        }

        return arn.split(":", 6)[4];
    }

    /**
     * Method gets the aws accountId from the specified credentials.
     * @param privateKeyId
     * @param privateKey
     * @return account ID
     */
    private String getAccountId(String privateKeyId, String privateKey) {
        AWSCredentials awsCredentials = new BasicAWSCredentials(privateKeyId, privateKey);

        AWSStaticCredentialsProvider awsStaticCredentialsProvider = new AWSStaticCredentialsProvider(
                awsCredentials);

        AmazonIdentityManagementClientBuilder amazonIdentityManagementClientBuilder = AmazonIdentityManagementClientBuilder
                .standard()
                .withCredentials(awsStaticCredentialsProvider)
                .withRegion(Regions.DEFAULT_REGION);

        AmazonIdentityManagementClient iamClient = (AmazonIdentityManagementClient) amazonIdentityManagementClientBuilder
                .build();

        String userId = null;
        try {
            if ((iamClient.getUser() != null) && (iamClient.getUser().getUser() != null)
                    && (iamClient.getUser().getUser().getArn() != null)) {

                return getAccountId(iamClient.getUser().getUser().getArn());
            }
        } catch (AmazonServiceException ex) {
            if (ex.getErrorCode().compareTo("AccessDenied") == 0) {
                String msg = ex.getMessage();
                userId = msg.split(":", 7)[5];
            } else {
                logSevere("Exception getting the accountId %s", ex);
            }
        }
        return userId;
    }

    private void checkIfAccountExistsAndGetExistingDocuments(EndpointConfigRequest req,
            Operation op) {
        if (req.isMockRequest) {
            req.accountAlreadyExists = false;
            op.setBody(req);
            op.complete();
            return;
        }

        String accountId = getAccountId(req.endpointProperties.get(ARN_KEY),
                req.endpointProperties.get(EndpointConfigRequest.PRIVATE_KEYID_KEY),
                req.endpointProperties.get(EndpointConfigRequest.PRIVATE_KEY_KEY));
        if (accountId != null && !accountId.isEmpty()) {
            QueryTask queryTask = QueryUtils
                    .createAccountQuery(accountId, PhotonModelConstants.EndpointType.aws.name(),
                            req.tenantLinks);

            queryTask.tenantLinks = req.tenantLinks;
            QueryUtils.startInventoryQueryTask(this, queryTask)
                    .whenComplete((qrt, e) -> {
                        if (e != null) {
                            logSevere(
                                    () -> String.format(
                                            "Failure retrieving query results for compute host corresponding to"
                                                    + "the account ID: %s",
                                            e.toString()));
                            op.fail(e);
                            return;
                        }
                        if (qrt.results.documentCount > 0) {
                            req.accountAlreadyExists = true;
                            Object state = qrt.results.documents.values().iterator().next();
                            ComputeState computeHost = Utils.fromJson(state,
                                    ComputeState.class);
                            req.existingComputeState = computeHost;
                            getComputeDescription(req, computeHost.descriptionLink, op);
                        } else {
                            req.accountAlreadyExists = false;
                            op.setBody(req);
                            op.complete();
                            return;
                        }
                    });
        } else { //If the account Id cannot be looked up with the given set of credentials then de duplication is not possible.
            req.accountAlreadyExists = false;
            op.setBody(req);
            op.complete();
        }
    }

    /**
     * Retrieves the compute description corresponding to the compute host for a given account id.
     */
    private void getComputeDescription(EndpointConfigRequest req, String descriptionLink,
            Operation op) {
        Operation.createGet(getHost(), descriptionLink)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logSevere(
                                () -> String.format(
                                        "Failure retrieving the compute host description corresponding to"
                                                + " the account ID: %s",
                                        ex.toString()));
                        op.fail(ex);
                        return;
                    }
                    ComputeDescription computeHostDescription = o.getBody(ComputeDescription.class);
                    req.existingComputeDescription = computeHostDescription;
                    op.setBody(req);
                    op.complete();
                    return;
                }).sendWith(this);
    }
}
