/*
 * Copyright (c) 2015-2018 VMware, Inc. All Rights Reserved.
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

import static com.amazonaws.retry.PredefinedRetryPolicies.DEFAULT_BACKOFF_STRATEGY;
import static com.amazonaws.retry.PredefinedRetryPolicies.DEFAULT_RETRY_CONDITION;

import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.ARN_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.EXTERNAL_ID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.SESSION_EXPIRATION_TIME_MICROS_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.SESSION_TOKEN_KEY;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_MOCK_HOST_SYSTEM_PROPERTY;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_S3PROXY_SYSTEM_PROPERTY;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_TAG_NAME;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.DEVICE_NAME;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.DEVICE_TYPE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.INSTANCE_STATE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.INSTANCE_STATE_PENDING;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.INSTANCE_STATE_RUNNING;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.INSTANCE_STATE_SHUTTING_DOWN;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.INSTANCE_STATE_STOPPED;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.INSTANCE_STATE_STOPPING;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.PROVISIONED_SSD_MAX_SIZE_IN_MB;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.PROVISIONED_SSD_MIN_SIZE_IN_MB;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.VOLUME_TYPE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.VOLUME_TYPE_GENERAL_PURPOSED_SSD;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.VOLUME_TYPE_PROVISIONED_SSD;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSSecurityGroupClient.DEFAULT_SECURITY_GROUP_NAME;
import static com.vmware.photon.controller.model.util.PhotonModelUriUtils.createInventoryUri;
import static com.vmware.xenon.common.Operation.STATUS_CODE_FORBIDDEN;
import static com.vmware.xenon.common.Operation.STATUS_CODE_UNAUTHORIZED;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.regions.Regions;
import com.amazonaws.retry.RetryPolicy;
import com.amazonaws.retry.RetryPolicy.BackoffStrategy;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClient;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClientBuilder;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.AmazonEC2AsyncClientBuilder;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DeleteSecurityGroupRequest;
import com.amazonaws.services.ec2.model.DeleteSecurityGroupResult;
import com.amazonaws.services.ec2.model.DeleteTagsRequest;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesRequest;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeTagsRequest;
import com.amazonaws.services.ec2.model.DescribeTagsResult;
import com.amazonaws.services.ec2.model.DescribeVpcsResult;
import com.amazonaws.services.ec2.model.EbsInstanceBlockDeviceSpecification;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceAttributeName;
import com.amazonaws.services.ec2.model.InstanceBlockDeviceMappingSpecification;
import com.amazonaws.services.ec2.model.InstanceState;
import com.amazonaws.services.ec2.model.InstanceStateChange;
import com.amazonaws.services.ec2.model.ModifyInstanceAttributeRequest;
import com.amazonaws.services.ec2.model.ModifyInstanceAttributeResult;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TagDescription;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingAsyncClient;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingAsyncClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceAsync;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceAsyncClientBuilder;
import com.amazonaws.services.securitytoken.model.AWSSecurityTokenServiceException;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import com.amazonaws.services.securitytoken.model.Credentials;

import org.apache.commons.collections.CollectionUtils;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSInstanceContext.AWSNicContext;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSAsyncHandler;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSCsvBillParser;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSDeferredResultAsyncHandler;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSSecurityGroupClient;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperation;
import com.vmware.photon.controller.model.adapters.util.ComputeEnumerateAdapterRequest;
import com.vmware.photon.controller.model.adapters.util.Pair;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.security.util.EncryptionUtils;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * AWS utils.
 */
public class AWSUtils {
    public static final String AWS_FILTER_RESOURCE_ID = "resource-id";
    public static final String AWS_FILTER_VPC_ID = "vpc-id";
    public static final String NO_VALUE = "no-value";
    public static final String TILDA = "~";
    public static final String AWS_MOCK_EC2_ENDPOINT = "/aws-mock/ec2-endpoint/";
    public static final String AWS_MOCK_CLOUDWATCH_ENDPOINT = "/aws-mock/cloudwatch/";
    public static final String AWS_MOCK_LOAD_BALANCING_ENDPOINT = "/aws-mock/load-balancing-endpoint/";
    public static final String AWS_REGION_HEADER = "region";

    public static final String AWS_IMAGE_REGEX = "^ami-\\S+";
    /**
     * -Dphoton-model.aws.masterAccount.accessKey
     * -Dphoton-model.aws.masterAccount.secretKey
     *
     * The AWS credentials of the service accepting ARN-based credential requests. These credentials
     * are used to authenticate to the service account that has been authorized to assume the role
     * of a user's ARN-based AWS account.
     *
     * When a user generates an ARN, they authorize an account ID to assume the role on their
     * behalf - these keys must correspond to that specific account ID.
     */
    public static final String AWS_MASTER_ACCOUNT_ACCESS_KEY_PROPERTY = UriPaths.PROPERTY_PREFIX +
            "aws.masterAccount.accessKey";
    public static final String AWS_MASTER_ACCOUNT_ACCESS_KEY =
            System.getProperty(AWS_MASTER_ACCOUNT_ACCESS_KEY_PROPERTY);

    public static final String AWS_MASTER_ACCOUNT_SECRET_KEY_PROPERTY = UriPaths.PROPERTY_PREFIX +
            "aws.masterAccount.secretKey";
    public static final String AWS_MASTER_ACCOUNT_SECRET_KEY =
            System.getProperty(AWS_MASTER_ACCOUNT_SECRET_KEY_PROPERTY);

    /**
     * -Dphoton-model.aws.sessionExpirationOffset.millis
     *
     * An offset in milliseconds to "look-ahead" for session credential expiration and allow
     * credential refreshing. Defaults to 10 minutes (600000 milliseconds).
     */
    public static final String AWS_EXPIRATION_OFFSET_MILLIS_PROPERTY = UriPaths.PROPERTY_PREFIX +
            "aws.sessionExpirationOffset.millis";
    public static final Long AWS_DEFAULT_EXPIRATION_OFFSET_MILLIS = TimeUnit.MINUTES.toMillis(10);
    public static final Long AWS_EXPIRATION_OFFSET_MILLIS = Long.getLong(
            AWS_EXPIRATION_OFFSET_MILLIS_PROPERTY, AWS_DEFAULT_EXPIRATION_OFFSET_MILLIS);

    /**
     * -Dphoton-model.aws.arnDefaultSessionDuration.seconds
     *
     * The AWS ARN default session duration (in seconds). Defaults to 1 hour (3600 seconds) if not
     * set manually.
     *
     * This property may be between 15 minutes (900 seconds) and 1 hour (3600 seconds), as set by
     * AWS.
     */
    public static final String AWS_ARN_DEFAULT_SESSION_DURATION_SECONDS_PROPERTY =
            UriPaths.PROPERTY_PREFIX + "aws.arnDefaultSessionDuration.seconds";
    private static final Long ARN_DEFAULT_SESSION_DURATION_SECONDS = TimeUnit.HOURS.toSeconds(1);
    private static final Long AWS_MINIMUM_SESSION_DURATION_SECONDS = TimeUnit.MINUTES.toSeconds(15);
    private static final Long AWS_MAXIMUM_SESSION_DURATION_SECONDS = TimeUnit.HOURS.toSeconds(1);
    public static final Long AWS_ARN_SESSION_DURATION_SECONDS = Long.getLong(
            AWS_ARN_DEFAULT_SESSION_DURATION_SECONDS_PROPERTY,
            ARN_DEFAULT_SESSION_DURATION_SECONDS);

    /**
     * -Dphoton-model.aws.max.error.retry
     *
     * The AWS max retry request count. This is how many times to retry the request if it fails.
     * Default is 7.
     */
    public static final String AWS_MAX_ERROR_RETRY_PROPERTY =
            UriPaths.PROPERTY_PREFIX + "aws.max.error.retry";
    public static final int AWS_MAX_ERROR_RETRY = Integer
            .getInteger(AWS_MAX_ERROR_RETRY_PROPERTY, 7);

    /**
     * -Dphoton-model.aws.log.retry.error.attempt
     *
     * Log retry requests after attempt count reaches this threshold.
     * Default is 0 - all retires will be logged.
     */
    public static final String AWS_LOG_RETRY_ERROR_ATTEMPT_PROPERTY =
            UriPaths.PROPERTY_PREFIX + "aws.log.retry.error.attempt";
    public static final int AWS_LOG_RETRY_ERROR_ATTEMPT =
            Integer.getInteger(AWS_LOG_RETRY_ERROR_ATTEMPT_PROPERTY, 0);

    /**
     * Flag to use aws-mock, will be set in test files. Aws-mock is a open-source tool for testing
     * AWS services in a mock EC2 environment.
     *
     * @see <a href="https://github.com/treelogic-swe/aws-mock">aws-mock</a>
     */
    private static boolean IS_AWS_CLIENT_MOCK = false;

    /**
     * Mock Host and port http://<ip-address>:<port> of aws-mock, will be set in test files.
     */
    private static String awsMockHost = null;

    /**
     * Flag to use s3proxy, will be set in test files. s3proxy is a open-source tool for testing AWS
     * services in a mock S3 environment.
     *
     * @see <a href="https://https://github.com/andrewgaul/s3proxy">s3proxy</a>
     */
    private static boolean IS_S3_PROXY = false;

    /**
     * Mock Host and port http://<ip-address>:<port> of s3proxy, will be set in test files.
     */
    private static String awsS3ProxyHost = null;

    /**
     * Backoff strategy that uses the DEFAULT_BACKOFF_STRATEGY with added error logging.
     */
    public static class LoggingBackoffStrategy implements BackoffStrategy {
        @Override
        public long delayBeforeNextRetry(AmazonWebServiceRequest originalRequest,
                AmazonClientException exception,
                int retriesAttempted) {
            long delay = DEFAULT_BACKOFF_STRATEGY.delayBeforeNextRetry(
                    originalRequest,
                    exception,
                    retriesAttempted);

            if (retriesAttempted >= AWS_LOG_RETRY_ERROR_ATTEMPT) {
                Utils.log(LoggingBackoffStrategy.class,
                        LoggingBackoffStrategy.class.getSimpleName(),
                        Level.WARNING,
                        "Retriable error on attempt %d for request %s %s, will retry in %s ms: %s",
                        retriesAttempted,
                        Integer.toHexString(System.identityHashCode(originalRequest)),
                        originalRequest, delay, exception);
            }
            return delay;
        }
    }


    public static void setAwsMockHost(String mockHost) {
        awsMockHost = mockHost;
    }

    public static boolean isAwsClientMock() {
        return System.getProperty(AWS_MOCK_HOST_SYSTEM_PROPERTY) == null ? IS_AWS_CLIENT_MOCK
                : true;
    }

    public static void setAwsClientMock(boolean isAwsClientMock) {
        IS_AWS_CLIENT_MOCK = isAwsClientMock;
    }

    private static String getAWSMockHost() {
        return System.getProperty(AWS_MOCK_HOST_SYSTEM_PROPERTY) == null ? awsMockHost
                : System.getProperty(AWS_MOCK_HOST_SYSTEM_PROPERTY);

    }

    public static void setAwsS3ProxyHost(String s3ProxyHost) {
        awsMockHost = s3ProxyHost;
    }

    public static boolean isAwsS3Proxy() {
        return System.getProperty(AWS_S3PROXY_SYSTEM_PROPERTY) == null ? IS_S3_PROXY : true;
    }

    public static void setAwsS3Proxy(boolean isAwsS3Proxy) {
        IS_S3_PROXY = isAwsS3Proxy;
    }

    /**
     * Method to get an EC2 Async Client.
     *
     * Allows for ARN-based credentials (as well as traditional key-based credentials), where a set
     * of credentials with the ARN key set will communicate with AWS to trade for a set of session
     * credentials that can allow the instantiation of an Amazon client.
     *
     * @param credentials An {@link AuthCredentialsServiceState} object.
     * @param region The region to get the AWS client in.
     * @param executorService The executor service to run async services in.
     */
    public static DeferredResult<AmazonEC2AsyncClient> getEc2AsyncClient(
            AuthCredentialsServiceState credentials, String region,
            ExecutorService executorService) {
        OperationContext operationContext = OperationContext.getOperationContext();
        return checkAndRefreshCredentials(credentials, region, executorService)
                .thenApply(refreshedCredentials -> {
                    OperationContext.restoreOperationContext(operationContext);
                    return getAsyncClient(refreshedCredentials, region, executorService);
                });
    }

    /**
     * Method to get an EC2 Async Client.
     *
     * Note: ARN-based credentials will not work unless they have already been exchanged to
     * AWS for session credentials. If unset, this method will fail. To enable ARN-based
     * credentials, migrate to {@link #getEc2AsyncClient(AuthCredentialsServiceState, String,
     * ExecutorService)}.
     *
     * @param credentials An {@link AuthCredentialsServiceState} object.
     * @param region The region to get the AWS client in.
     * @param executorService The executor service to run async services in.
     */
    public static AmazonEC2AsyncClient getAsyncClient(
            AuthCredentialsServiceState credentials, String region,
            ExecutorService executorService) {

        ClientConfiguration configuration = createClientConfiguration()
                .withMaxConnections(100);

        AmazonEC2AsyncClientBuilder ec2AsyncClientBuilder = AmazonEC2AsyncClientBuilder
                .standard()
                .withClientConfiguration(configuration)
                .withCredentials(getAwsStaticCredentialsProvider(credentials))
                .withExecutorFactory(() -> executorService);

        if (region == null) {
            region = Regions.DEFAULT_REGION.getName();
        }

        if (isAwsClientMock()) {
            configuration.addHeader(AWS_REGION_HEADER, region);
            ec2AsyncClientBuilder.setClientConfiguration(configuration);
            AwsClientBuilder.EndpointConfiguration endpointConfiguration = new AwsClientBuilder.EndpointConfiguration(
                    getAWSMockHost() + AWS_MOCK_EC2_ENDPOINT, region);
            ec2AsyncClientBuilder.setEndpointConfiguration(endpointConfiguration);
        } else {
            ec2AsyncClientBuilder.setRegion(region);
        }

        return (AmazonEC2AsyncClient) ec2AsyncClientBuilder.build();
    }

    static ClientConfiguration createClientConfiguration() {
        return new ClientConfiguration().withRetryPolicy(
                new RetryPolicy(
                        DEFAULT_RETRY_CONDITION,
                        new LoggingBackoffStrategy(),
                        AWS_MAX_ERROR_RETRY,
                        true));
    }

    private static String getAwsS3ProxyHost() {
        return System.getProperty(AWS_S3PROXY_SYSTEM_PROPERTY) == null ? awsS3ProxyHost
                : System.getProperty(AWS_S3PROXY_SYSTEM_PROPERTY);

    }

    public static void validateCredentials(AmazonEC2AsyncClient ec2Client,
            AWSClientManager clientManager, AuthCredentialsServiceState credentials,
            ComputeEnumerateAdapterRequest context, StatelessService service,
            Consumer<DescribeAvailabilityZonesResult> onSuccess,
            Consumer<Throwable> onFail,
            Runnable onUnaccessible) {

        if (clientManager.isEc2ClientInvalid(credentials, context.regionId)) {
            onUnaccessible.run();
            return;
        }

        // NOTE: If an access to Xenon is required DO USE AWSAsyncHandler which set OperationContext
        ec2Client.describeAvailabilityZonesAsync(new DescribeAvailabilityZonesRequest(),
                new AsyncHandler<DescribeAvailabilityZonesRequest, DescribeAvailabilityZonesResult>() {

                    @Override
                    public void onError(Exception e) {
                        if (e instanceof AmazonServiceException) {
                            AmazonServiceException ase = (AmazonServiceException) e;
                            if (ase.getStatusCode() == STATUS_CODE_UNAUTHORIZED ||
                                    ase.getStatusCode() == STATUS_CODE_FORBIDDEN) {
                                clientManager.markEc2ClientInvalid(service, credentials,
                                        context.regionId);
                                // Signal passed creds does not have Access to this region
                                onUnaccessible.run();
                                return;
                            }
                            // Signal the failure
                            onFail.accept(e);
                        }
                    }

                    @Override
                    public void onSuccess(DescribeAvailabilityZonesRequest request,
                            DescribeAvailabilityZonesResult describeAvailabilityZonesResult) {

                        // Signal success
                        onSuccess.accept(describeAvailabilityZonesResult);
                    }
                });
    }

    /**
     * Method to get a CloudWatch Async Client.
     *
     * Allows for ARN-based credentials (as well as traditional key-based credentials), where a set
     * of credentials with the ARN key set will communicate with AWS to trade for a set of session
     * credentials that can allow the instantiation of an Amazon client.
     *
     * @param credentials An {@link AuthCredentialsServiceState} object.
     * @param region The region to get the AWS client in.
     * @param executorService The executor service to run async services in.
     */
    public static DeferredResult<AmazonCloudWatchAsyncClient> getCloudWatchStatsAsyncClient(
            AuthCredentialsServiceState credentials, String region,
            ExecutorService executorService, boolean isMockRequest) {
        OperationContext operationContext = OperationContext.getOperationContext();
        return checkAndRefreshCredentials(credentials, region, executorService)
                .thenApply(refreshedCredentials -> {
                    OperationContext.restoreOperationContext(operationContext);
                    return getStatsAsyncClient(refreshedCredentials, region, executorService,
                            isMockRequest);
                });
    }

    /**
     * Method to get a CloudWatch Async Client.
     *
     * Note: ARN-based credentials will not work unless they have already been exchanged to
     * AWS for session credentials. If unset, this method will fail. To enable ARN-based
     * credentials, migrate to {@link #getCloudWatchStatsAsyncClient(AuthCredentialsServiceState,
     * String, ExecutorService, boolean)}.
     *
     * @param credentials An {@link AuthCredentialsServiceState} object.
     * @param region The region to get the AWS client in.
     * @param executorService The executor service to run async services in.
     */
    public static AmazonCloudWatchAsyncClient getStatsAsyncClient(
            AuthCredentialsServiceState credentials, String region,
            ExecutorService executorService, boolean isMockRequest) {

        ClientConfiguration configuration = createClientConfiguration();

        AmazonCloudWatchAsyncClientBuilder amazonCloudWatchAsyncClientBuilder =
                AmazonCloudWatchAsyncClientBuilder
                        .standard()
                        .withClientConfiguration(configuration)
                        .withCredentials(getAwsStaticCredentialsProvider(credentials))
                        .withExecutorFactory(() -> executorService);

        if (region == null) {
            region = Regions.DEFAULT_REGION.getName();
        }

        if (isAwsClientMock()) {
            configuration.addHeader(AWS_REGION_HEADER, region);
            amazonCloudWatchAsyncClientBuilder.setClientConfiguration(configuration);
            AwsClientBuilder.EndpointConfiguration endpointConfiguration = new AwsClientBuilder.EndpointConfiguration(
                    getAWSMockHost() + AWS_MOCK_CLOUDWATCH_ENDPOINT, region);
            amazonCloudWatchAsyncClientBuilder.setEndpointConfiguration(endpointConfiguration);
        } else {
            amazonCloudWatchAsyncClientBuilder.setRegion(region);
        }

        return (AmazonCloudWatchAsyncClient) amazonCloudWatchAsyncClientBuilder.build();
    }

    /**
     * Method to get an S3 transfer manager client.
     *
     * Allows for ARN-based credentials (as well as traditional key-based credentials), where a set
     * of credentials with the ARN key set will communicate with AWS to trade for a set of session
     * credentials that can allow the instantiation of an Amazon client.
     *
     * @param credentials An {@link AuthCredentialsServiceState} object.
     * @param region The region to get the AWS client in.
     * @param executorService The executor service to run async services in.
     */
    public static DeferredResult<TransferManager> getS3TransferManagerAsync(
            AuthCredentialsServiceState credentials, String region,
            ExecutorService executorService) {
        OperationContext operationContext = OperationContext.getOperationContext();
        return checkAndRefreshCredentials(credentials, region, executorService)
                .thenApply(refreshedCredentials -> {
                    OperationContext.restoreOperationContext(operationContext);
                    return getS3TransferManager(refreshedCredentials, region, executorService);
                });
    }

    /**
     * Method to get an S3 transfer manager client.
     *
     * Note: ARN-based credentials will not work unless they have already been exchanged to
     * AWS for session credentials. If unset, this method will fail. To enable ARN-based
     * credentials, migrate to {@link #getS3TransferManagerAsync(AuthCredentialsServiceState,
     * String, ExecutorService)}
     *
     * @param credentials An {@link AuthCredentialsServiceState} object.
     * @param region The region to get the AWS client in.
     * @param executorService The executor service to run async services in.
     */
    public static TransferManager getS3TransferManager(AuthCredentialsServiceState credentials,
            String region, ExecutorService executorService) {

        AmazonS3ClientBuilder amazonS3ClientBuilder = AmazonS3ClientBuilder.standard()
                .withCredentials(getAwsStaticCredentialsProvider(credentials))
                .withForceGlobalBucketAccessEnabled(true);

        if (region == null) {
            region = Regions.DEFAULT_REGION.getName();
        }

        if (isAwsS3Proxy()) {
            AwsClientBuilder.EndpointConfiguration endpointConfiguration = new AwsClientBuilder.EndpointConfiguration(
                    getAwsS3ProxyHost(), region);
            amazonS3ClientBuilder.setEndpointConfiguration(endpointConfiguration);
        } else {
            amazonS3ClientBuilder.setRegion(region);
        }

        TransferManagerBuilder transferManagerBuilder = TransferManagerBuilder.standard()
                .withS3Client(amazonS3ClientBuilder.build())
                .withExecutorFactory(() -> executorService)
                .withShutDownThreadPools(false);

        return transferManagerBuilder.build();
    }

    /**
     * Method to get a load balancing async client.
     *
     * Allows for ARN-based credentials (as well as traditional key-based credentials), where a set
     * of credentials with the ARN key set will communicate with AWS to trade for a set of session
     * credentials that can allow the instantiation of an Amazon client.
     *
     * @param credentials An {@link AuthCredentialsServiceState} object.
     * @param region The region to get the AWS client in.
     * @param executorService The executor service to run async services in.
     */
    public static DeferredResult<AmazonElasticLoadBalancingAsyncClient> getAwsLoadBalancingAsyncClient(
            AuthCredentialsServiceState credentials, String region,
            ExecutorService executorService) {
        OperationContext operationContext = OperationContext.getOperationContext();
        return checkAndRefreshCredentials(credentials, region, executorService)
                .thenApply(refreshedCredentials -> {
                    OperationContext.restoreOperationContext(operationContext);
                    return getLoadBalancingAsyncClient(refreshedCredentials, region, executorService);
                });
    }

    /**
     * Method to get a load balancing async client.
     *
     * Note: ARN-based credentials will not work unless they have already been exchanged to
     * AWS for session credentials. If unset, this method will fail. To enable ARN-based
     * credentials, migrate to {@link #getAwsLoadBalancingAsyncClient(AuthCredentialsServiceState,
     * String, ExecutorService)}.
     *
     * @param credentials An {@link AuthCredentialsServiceState} object.
     * @param region The region to get the AWS client in.
     * @param executorService The executor service to run async services in.
     */
    public static AmazonElasticLoadBalancingAsyncClient getLoadBalancingAsyncClient(
            AuthCredentialsServiceState credentials, String region,
            ExecutorService executorService) {

        AmazonElasticLoadBalancingAsyncClientBuilder amazonElasticLoadBalancingAsyncClientBuilder =
                AmazonElasticLoadBalancingAsyncClientBuilder
                        .standard()
                        .withClientConfiguration(createClientConfiguration())
                        .withCredentials(getAwsStaticCredentialsProvider(credentials))
                        .withExecutorFactory(() -> executorService);

        if (region == null) {
            region = Regions.DEFAULT_REGION.getName();
        }

        if (isAwsClientMock()) {
            AwsClientBuilder.EndpointConfiguration endpointConfiguration = new AwsClientBuilder.EndpointConfiguration(
                    getAWSMockHost() + AWS_MOCK_LOAD_BALANCING_ENDPOINT, region);
            amazonElasticLoadBalancingAsyncClientBuilder
                    .setEndpointConfiguration(endpointConfiguration);
        } else {
            amazonElasticLoadBalancingAsyncClientBuilder.setRegion(region);
        }

        return (AmazonElasticLoadBalancingAsyncClient) amazonElasticLoadBalancingAsyncClientBuilder
                .build();
    }

    /**
     * Method to get an S3 Async Client.
     *
     * Allows for ARN-based credentials (as well as traditional key-based credentials), where a set
     * of credentials with the ARN key set will communicate with AWS to trade for a set of session
     * credentials that can allow the instantiation of an Amazon client.
     *
     * @param credentials An {@link AuthCredentialsServiceState} object.
     * @param region The region to get the AWS client in.
     * @param executorService The executor service to run async services in.
     */
    public static DeferredResult<AmazonS3Client> getS3ClientAsync(
            AuthCredentialsServiceState credentials, String region,
            ExecutorService executorService) {
        OperationContext operationContext = OperationContext.getOperationContext();
        return checkAndRefreshCredentials(credentials, region, executorService)
                .thenApply(refreshedCredentials -> {
                    OperationContext.restoreOperationContext(operationContext);
                    return getS3Client(refreshedCredentials, region);
                });
    }

    /**
     * Method to get an S3 Async Client.
     *
     * Note: ARN-based credentials will not work unless they have already been exchanged to
     * AWS for session credentials. If unset, this method will fail. To enable ARN-based
     * credentials, migrate to {@link #getS3ClientAsync(AuthCredentialsServiceState, String,
     * ExecutorService)}.
     *
     * @param credentials An {@link AuthCredentialsServiceState} object.
     * @param regionId The region to get the AWS client in.
     */
    public static AmazonS3Client getS3Client(AuthCredentialsServiceState credentials,
            String regionId) {

        AmazonS3ClientBuilder amazonS3ClientBuilder = AmazonS3ClientBuilder
                .standard()
                .withClientConfiguration(createClientConfiguration())
                .withCredentials(getAwsStaticCredentialsProvider(credentials))
                .withRegion(regionId);

        if (isAwsClientMock()) {
            throw new IllegalArgumentException("AWS Mock does not support S3 client");
        }

        return (AmazonS3Client) amazonS3ClientBuilder.build();
    }

    /**
     * Synchronous UnTagging of one or many AWS resources with the provided tags.
     */
    public static void unTagResources(AmazonEC2AsyncClient client, Collection<Tag> tags,
            String... resourceIds) {
        if (isAwsClientMock()) {
            return;
        }

        DeleteTagsRequest req = new DeleteTagsRequest()
                .withTags(tags)
                .withResources(resourceIds);

        client.deleteTags(req);
    }

    /**
     * Synchronous Tagging of one or many AWS resources with the provided tags.
     */
    public static void tagResources(AmazonEC2AsyncClient client,
            Collection<Tag> tags, String... resourceIds) {
        if (isAwsClientMock()) {
            return;
        }

        CreateTagsRequest req = new CreateTagsRequest()
                .withResources(resourceIds).withTags(tags);

        client.createTags(req);
    }

    /**
     * Synchronous Tagging of one or many AWS resources with the provided name.
     */
    public static void tagResourcesWithName(AmazonEC2AsyncClient client, String name,
            String... resourceIds) {
        Tag awsNameTag = new Tag().withKey(AWS_TAG_NAME).withValue(name);
        tagResources(client, Collections.singletonList(awsNameTag), resourceIds);
    }

    /*
     * Return the tags for a giving resource
     */
    public static List<TagDescription> getResourceTags(String resourceID,
            AmazonEC2AsyncClient client) {
        Filter resource = new Filter().withName(AWS_FILTER_RESOURCE_ID)
                .withValues(resourceID);
        DescribeTagsRequest req = new DescribeTagsRequest()
                .withFilters(resource);
        DescribeTagsResult result = client.describeTags(req);
        return result.getTags();
    }

    public static Filter getFilter(String name, String value) {
        return new Filter().withName(name).withValues(value);
    }

    /**
     * Returns the region Id for the AWS instance
     *
     * @return the region id
     */
    public static String getRegionId(Instance i) {
        // Drop the zone suffix "a" ,"b" etc to get the region Id.
        String zoneId = i.getPlacement().getAvailabilityZone();
        String regiondId = zoneId.substring(0, zoneId.length() - 1);
        return regiondId;
    }

    /**
     * Maps the Aws machine state to {@link PowerState}
     *
     * @param state
     * @return the {@link PowerState} of the machine
     */
    public static PowerState mapToPowerState(InstanceState state) {
        PowerState powerState = PowerState.UNKNOWN;
        switch (state.getCode()) {
        case 16:
            powerState = PowerState.ON;
            break;
        case 80:
            powerState = PowerState.OFF;
            break;
        default:
            break;
        }
        return powerState;
    }

    /**
     * Creates a filter for the instances that are in non terminated state on the AWS endpoint.
     *
     * @return
     */
    public static Filter getAWSNonTerminatedInstancesFilter() {
        // Create a filter to only get non terminated instances from the remote instance.
        List<String> stateValues = new ArrayList<>(Arrays.asList(INSTANCE_STATE_RUNNING,
                INSTANCE_STATE_PENDING, INSTANCE_STATE_STOPPING, INSTANCE_STATE_STOPPED,
                INSTANCE_STATE_SHUTTING_DOWN));
        Filter runningInstanceFilter = new Filter();
        runningInstanceFilter.setName(INSTANCE_STATE);
        runningInstanceFilter.setValues(stateValues);
        return runningInstanceFilter;
    }

    public static List<String> getOrCreateSecurityGroups(AWSInstanceContext aws) {
        return getOrCreateSecurityGroups(aws, aws.getPrimaryNic());
    }

    /*
     * method will create new or validate existing security group has the necessary settings for CM
     * to function. It will return the security group id that is required during instance
     * provisioning. for each nicContext element provided, for each of its securityGroupStates,
     * security group is discovered from AWS in case that there are no securityGroupStates, security
     * group ID is obtained from the custom properties in case that none of the above methods
     * discover a security group, the default one is discovered from AWS in case that none of the
     * above method discover a security group, a new security group is created
     */
    public static List<String> getOrCreateSecurityGroups(AWSInstanceContext aws,
            AWSNicContext nicCtx) {

        String groupId;
        SecurityGroup group;

        List<String> groupIds = new ArrayList<>();

        AWSSecurityGroupClient client = new AWSSecurityGroupClient(aws.amazonEC2Client);

        if (nicCtx != null) {
            if (nicCtx.securityGroupStates != null && !nicCtx.securityGroupStates.isEmpty()) {
                List<String> securityGroupNames = nicCtx.securityGroupStates.stream()
                        .map(securityGroupState -> securityGroupState.name)
                        .collect(Collectors.toList());
                List<SecurityGroup> securityGroups = client.getSecurityGroups(
                        new ArrayList<>(securityGroupNames), nicCtx.vpc.getVpcId());
                for (SecurityGroup securityGroup : securityGroups) {
                    groupIds.add(securityGroup.getGroupId());
                }
                return groupIds;
            }
        }

        // use the security group provided in the description properties
        String sgId = getFromCustomProperties(aws.child.description,
                AWSConstants.AWS_SECURITY_GROUP_ID);
        if (sgId != null) {
            return Arrays.asList(sgId);
        }

        // in case no group is configured in the properties, attempt to discover the default one
        if (nicCtx != null && nicCtx.vpc != null) {
            try {
                group = client.getSecurityGroup(DEFAULT_SECURITY_GROUP_NAME,
                        nicCtx.vpc.getVpcId());
                if (group != null) {
                    return Arrays.asList(group.getGroupId());
                }
            } catch (AmazonServiceException t) {
                if (!t.getMessage().contains(
                        DEFAULT_SECURITY_GROUP_NAME)) {
                    throw t;
                }
            }
        }

        // if the group doesn't exist an exception is thrown. We won't throw a
        // missing group exception
        // we will continue and create the group
        groupId = createSecurityGroupOnDefaultVPC(aws);

        return Collections.singletonList(groupId);
    }

    public static List<String> getOrCreateDefaultSecurityGroup(AmazonEC2AsyncClient amazonEC2Client,
            AWSNicContext nicCtx) {

        AWSSecurityGroupClient client = new AWSSecurityGroupClient(amazonEC2Client);
        // in case no group is configured in the properties, attempt to discover the default one
        if (nicCtx != null && nicCtx.vpc != null) {
            try {
                SecurityGroup group = client.getSecurityGroup(
                        DEFAULT_SECURITY_GROUP_NAME,
                        nicCtx.vpc.getVpcId());
                if (group != null) {
                    return Arrays.asList(group.getGroupId());
                }
            } catch (AmazonServiceException t) {
                if (!t.getMessage().contains(
                        DEFAULT_SECURITY_GROUP_NAME)) {
                    throw t;
                }
            }
        }

        // if the group doesn't exist an exception is thrown. We won't throw a
        // missing group exception
        // we will continue and create the group
        String groupId = client.createDefaultSecurityGroupWithDefaultRules(nicCtx.vpc);

        return Collections.singletonList(groupId);
    }

    // method create a security group in the VPC from custom properties or the default VPC
    private static String createSecurityGroupOnDefaultVPC(AWSInstanceContext aws) {
        String vpcId = null;
        // get the subnet cidr (if any)
        String subnetCidr = null;
        // in case subnet will be obtained from the default vpc, the security group should
        // as well be created there
        Vpc defaultVPC = getDefaultVPC(aws);
        if (defaultVPC != null) {
            vpcId = defaultVPC.getVpcId();
            subnetCidr = defaultVPC.getCidrBlock();
        }

        // no subnet or no vpc is not an option...
        if (subnetCidr == null || vpcId == null) {
            throw new AmazonServiceException("default VPC not found");
        }

        return new AWSSecurityGroupClient(aws.amazonEC2Client)
                .createDefaultSecurityGroupWithDefaultRules(defaultVPC);
    }

    public static String getFromCustomProperties(
            ComputeDescriptionService.ComputeDescription description,
            String key) {
        if (description == null || description.customProperties == null) {
            return null;
        }

        return description.customProperties.get(key);
    }

    /**
     * Gets the default VPC
     */
    public static Vpc getDefaultVPC(AWSInstanceContext aws) {
        DescribeVpcsResult result = aws.amazonEC2Client.describeVpcs();
        List<Vpc> vpcs = result.getVpcs();
        for (Vpc vpc : vpcs) {
            if (vpc.isDefault()) {
                return vpc;
            }
        }
        return null;
    }

    /**
     * Calculate the average burn rate, given a list of datapoints from Amazon AWS.
     */
    public static Double calculateAverageBurnRate(List<Datapoint> dpList) {
        if (dpList.size() <= 1) {
            return null;
        }
        Datapoint oldestDatapoint = dpList.get(0);
        Datapoint latestDatapoint = dpList.get(dpList.size() - 1);

        // Adjust oldest datapoint to account for billing cycle when the estimated charges is reset
        // to 0.
        // Iterate over the sublist from the oldestDatapoint element + 1 to the latestDatapoint
        // element (excluding).
        // If the oldestDatapoint value is greater than the latestDatapoint value,
        // move the oldestDatapoint pointer until the oldestDatapoint value is less than the
        // latestDatapoint value.
        // Eg: 4,5,6,7,0,1,2,3 -> 4 is greater than 3. Move the pointer until 0.
        // OldestDatapoint value is 0 and the latestDatapoint value is 3.
        for (Datapoint datapoint : dpList.subList(1, dpList.size() - 1)) {
            if (latestDatapoint.getAverage() > oldestDatapoint.getAverage()) {
                break;
            }
            oldestDatapoint = datapoint;
        }

        double averageBurnRate = (latestDatapoint.getAverage()
                - oldestDatapoint.getAverage())
                / getDateDifference(oldestDatapoint.getTimestamp(),
                        latestDatapoint.getTimestamp(), TimeUnit.HOURS);
        // If there are only 2 datapoints and the oldestDatapoint is greater than the
        // latestDatapoint, value will be negative.
        // Eg: oldestDatapoint = 5 and latestDatapoint = 0, when the billing cycle is reset.
        // In such cases, set the burn rate value to 0
        averageBurnRate = (averageBurnRate < 0 ? 0 : averageBurnRate);
        return averageBurnRate;
    }

    /**
     * Calculate the current burn rate, given a list of datapoints from Amazon AWS.
     */
    public static Double calculateCurrentBurnRate(List<Datapoint> dpList) {
        if (dpList.size() <= 7) {
            return null;
        }
        Datapoint dayOldDatapoint = dpList.get(dpList.size() - 7);
        Datapoint latestDatapoint = dpList.get(dpList.size() - 1);

        // Adjust the dayOldDatapoint to account for billing cycle when the estimated charges is
        // reset to 0.
        // Iterate over the sublist from the oldestDatapoint element + 1 to the latestDatapoint
        // element.
        // If the oldestDatapoint value is greater than the latestDatapoint value,
        // move the oldestDatapoint pointer until the oldestDatapoint value is less than the
        // latestDatapoint value.
        // Eg: 4,5,6,7,0,1,2,3 -> 4 is greater than 3. Move the pointer until 0.
        // OldestDatapoint value is 0 and the latestDatapoint value is 3.
        for (Datapoint datapoint : dpList.subList(dpList.size() - 6, dpList.size() - 1)) {
            if (latestDatapoint.getAverage() > dayOldDatapoint.getAverage()) {
                break;
            }
            dayOldDatapoint = datapoint;
        }

        double currentBurnRate = (latestDatapoint.getAverage()
                - dayOldDatapoint.getAverage())
                / getDateDifference(dayOldDatapoint.getTimestamp(),
                        latestDatapoint.getTimestamp(), TimeUnit.HOURS);
        // If there are only 2 datapoints and the oldestDatapoint is greater than the
        // latestDatapoint, value will be negative.
        // Eg: oldestDatapoint = 5 and latestDatapoint = 0, when the billing cycle is reset.
        // In such cases, set the burn rate value to 0
        currentBurnRate = (currentBurnRate < 0 ? 0 : currentBurnRate);
        return currentBurnRate;
    }

    private static long getDateDifference(Date oldDate, Date newDate, TimeUnit timeUnit) {
        long differenceInMillies = newDate.getTime() - oldDate.getTime();
        return timeUnit.convert(differenceInMillies, TimeUnit.MILLISECONDS);
    }

    public static String autoDiscoverBillsBucketName(AmazonS3 s3Client, String awsAccountId) {
        String billFilePrefix = awsAccountId + AWSCsvBillParser.AWS_DETAILED_BILL_CSV_FILE_NAME_MID;
        for (Bucket bucket : s3Client.listBuckets()) {
            // For each bucket accessible to this client, try to search for files with the
            // 'billFilePrefix'
            ObjectListing objectListing = s3Client.listObjects(bucket.getName(), billFilePrefix);
            if (!objectListing.getObjectSummaries().isEmpty()) {
                // This means that this bucket contains zip files representing the detailed csv
                // bills.
                return bucket.getName();
            }
        }
        return null;
    }

    public static void waitForTransitionCompletion(ServiceHost host,
            List<InstanceStateChange> stateChangeList,
            final String desiredState, AmazonEC2AsyncClient client,
            BiConsumer<InstanceState, Exception> callback) {
        InstanceStateChange stateChange = stateChangeList.get(0);

        try {
            DescribeInstancesRequest request = new DescribeInstancesRequest();
            request.withInstanceIds(stateChange.getInstanceId());
            DescribeInstancesResult result = client.describeInstances(request);
            Instance instance = result.getReservations()
                    .stream()
                    .flatMap(r -> r.getInstances().stream())
                    .filter(i -> i.getInstanceId()
                            .equalsIgnoreCase(stateChange.getInstanceId()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException(
                            String.format("%s instance not found", stateChange.getInstanceId())));

            String state = instance.getState().getName();

            if (state.equals(desiredState)) {
                callback.accept(instance.getState(), null);
            } else {
                host.schedule(() -> waitForTransitionCompletion(host, stateChangeList, desiredState,
                        client, callback), 5, TimeUnit.SECONDS);
            }

        } catch (AmazonServiceException | IllegalArgumentException ase) {
            callback.accept(null, ase);
        }

    }

    public static void setEbsDefaultsIfNotSet(DiskService.DiskState diskState, Boolean persist) {
        diskState.persistent = Optional.ofNullable(diskState.persistent).orElse(persist);

        if (diskState.customProperties == null) {
            diskState.customProperties = new HashMap<>();
        }

        if (diskState.customProperties.get(DEVICE_TYPE) == null) {
            diskState.customProperties.put(DEVICE_TYPE, AWSConstants.AWSStorageType.EBS.getName());
        }

        if (diskState.customProperties.get(VOLUME_TYPE) == null) {
            diskState.customProperties.put(VOLUME_TYPE, VOLUME_TYPE_GENERAL_PURPOSED_SSD);
        }
    }

    public static void validateSizeSupportedByVolumeType(int capacityGiB, String volumeType) {
        if (volumeType.equals(VOLUME_TYPE_PROVISIONED_SSD)) {
            long capacityMBytes = capacityGiB * 1024;
            if (capacityMBytes < PROVISIONED_SSD_MIN_SIZE_IN_MB ||
                    capacityMBytes > PROVISIONED_SSD_MAX_SIZE_IN_MB) {
                String message = String
                        .format("Cannot provision a %s GiB IOPS disk. An io1 type of "
                                + "volume must be at least 4 GiB in size.", capacityGiB);
                throw new IllegalArgumentException(message);
            }
        }
    }

    /**
     * Generates an AWS credentials provider, determining if to use general basic authentication or
     * if the credentials are session-based.
     * @param credentials An {@link AuthCredentialsServiceState} object.
     */
    private static AWSStaticCredentialsProvider getAwsStaticCredentialsProvider(
            AuthCredentialsServiceState credentials) throws AWSSecurityTokenServiceException {

        // If the credentials are non-session based, then simply generate basic AWS credential set
        // and return them.
        if (credentials.customProperties == null ||
                !credentials.customProperties.containsKey(SESSION_TOKEN_KEY)) {
            return new AWSStaticCredentialsProvider(
                    new BasicAWSCredentials(credentials.privateKeyId,
                            EncryptionUtils.decrypt(credentials.privateKey)));
        }

        return new AWSStaticCredentialsProvider(
                new BasicSessionCredentials(credentials.privateKeyId,
                        EncryptionUtils.decrypt(credentials.privateKey),
                        credentials.customProperties.get(SESSION_TOKEN_KEY)));
    }

    /**
     * Helper method to check if a set of credentials have expired and if so, retrieves a set of
     * refreshed credentials. If not, then returns the current credentials set.
     *
     * @param credentials An {@link AuthCredentialsServiceState} object.
     * @param region The region to get the credentials in.
     * @param executorService The executor service to run async services in.
     */
    public static DeferredResult<AuthCredentialsServiceState> checkAndRefreshCredentials(
            AuthCredentialsServiceState credentials, String region,
            ExecutorService executorService) {
        // If the credentials are non-ARN based, or they are not yet expired, then they may be
        // returned automatically.
        if (credentials.customProperties == null ||
                (!credentials.customProperties.containsKey(ARN_KEY) &&
                        !credentials.customProperties.containsKey(SESSION_TOKEN_KEY)) ||
                (credentials.customProperties.containsKey(SESSION_EXPIRATION_TIME_MICROS_KEY) &&
                        !isExpiredCredentials(credentials))) {
            return DeferredResult.completed(credentials);
        }

        return getArnSessionCredentialsAsync(credentials.customProperties.get(ARN_KEY),
                credentials.customProperties.get(EXTERNAL_ID_KEY), region, executorService)
                .thenApply(AWSUtils::awsSessionCredentialsToAuthCredentialsState);
    }

    /**
     * A helper method to convert an AWS {@link Credentials} object to an
     * {@link AuthCredentialsServiceState} object. This will use the customProperties
     * `SESSION_TOKEN_KEY` and `SESSION_EXPIRATION_TIME_MICROS_KEY` to represent the temporary
     * nature of these credentials.
     */
    public static AuthCredentialsServiceState awsSessionCredentialsToAuthCredentialsState(
            Credentials credentials) {
        AuthCredentialsServiceState authCredentials = new AuthCredentialsServiceState();
        authCredentials.privateKeyId = credentials.getAccessKeyId();
        authCredentials.privateKey = credentials.getSecretAccessKey();
        authCredentials.customProperties = new HashMap<>();
        authCredentials.customProperties.put(SESSION_TOKEN_KEY, credentials.getSessionToken());
        authCredentials.customProperties.put(SESSION_EXPIRATION_TIME_MICROS_KEY,
                String.valueOf(String.valueOf(credentials.getExpiration().getTime())));
        return authCredentials;
    }

    /**
     * A helper method to check if a set of credentials are ARN credentials, assumed via whether
     * or not the ARN_KEY custom property is set.
     *
     * @return True if ARN_KEY is set.
     */
    public static boolean isArnCredentials(AuthCredentialsServiceState credentials) {
        return credentials.customProperties != null &&
                credentials.customProperties.containsKey(ARN_KEY);
    }

    /**
     * Checks if a set of credentials have the key `SESSION_EXPIRATION_TIME_MICROS_KEY` and if so,
     * whether or not that value is less than or equal to the current system time, minus the
     * property set at {@link #AWS_EXPIRATION_OFFSET_MILLIS_PROPERTY}.
     *
     * @return True if the credentials are expired, false otherwise.
     */
    public static boolean isExpiredCredentials(AuthCredentialsServiceState credentials) {
        return credentials != null && credentials.customProperties != null &&
                credentials.customProperties.containsKey(SESSION_EXPIRATION_TIME_MICROS_KEY) &&
                (Long.parseLong(credentials.customProperties
                        .get(SESSION_EXPIRATION_TIME_MICROS_KEY)) - AWS_EXPIRATION_OFFSET_MILLIS)
                        < System.currentTimeMillis();
    }

    /**
     * Returns the designated ARN credentials session duration in seconds. By default, it returns
     * 3600 (1 hour), which is the maximum AWS permits. This is toggleable via system property
     * {@link #AWS_ARN_DEFAULT_SESSION_DURATION_SECONDS_PROPERTY}.
     *
     * The value may be between 900 seconds (15 minutes) and 3600 seconds (1 hour). If the
     * designated duration is not within those bounds, it will be set to the nearest boundary.
     */
    private static Integer getArnSessionDurationSeconds() {
        Long duration = AWS_ARN_SESSION_DURATION_SECONDS;

        if (duration < AWS_MINIMUM_SESSION_DURATION_SECONDS) {
            duration = AWS_MINIMUM_SESSION_DURATION_SECONDS;
            Utils.log(AWSUtils.class, AWSUtils.class.getSimpleName(), Level.WARNING,
                    "AWS ARN session duration may not be lower than 900 seconds. Defaulting to 900 seconds.");
        }

        if (duration > AWS_MAXIMUM_SESSION_DURATION_SECONDS) {
            duration = AWS_MAXIMUM_SESSION_DURATION_SECONDS;
            Utils.log(AWSUtils.class, AWSUtils.class.getSimpleName(), Level.WARNING,
                    "AWS ARN session duration may not be greater than 3600 seconds. Defaulting to 3600 seconds.");
        }

        return Math.toIntExact(duration);
    }

    /**
     * Authenticates and returns a DeferredResult set of session credentials for a valid ARN that
     * authorizes this system's account ID (validated through
     * {@link #AWS_MASTER_ACCOUNT_ACCESS_KEY_PROPERTY} and
     * {@link #AWS_MASTER_ACCOUNT_SECRET_KEY_PROPERTY}) and the externalId parameter.
     *
     * If the system properties are unset, then this call will automatically fail.
     *
     * @param arn The Amazon Resource Name to validate.
     * @param externalId The external ID this ARN has authorized.
     * @param executorService The executor service to issue the request.
     */
    public static DeferredResult<Credentials> getArnSessionCredentialsAsync(String arn,
            String externalId, ExecutorService executorService) {
        return getArnSessionCredentialsAsync(arn, externalId, Regions.DEFAULT_REGION.getName(),
                executorService);
    }

    /**
     * Authenticates and returns a DeferredResult set of session credentials for a valid ARN that
     * authorizes this system's account ID (validated through
     * {@link #AWS_MASTER_ACCOUNT_ACCESS_KEY_PROPERTY} and
     * {@link #AWS_MASTER_ACCOUNT_SECRET_KEY_PROPERTY}) and the externalId parameter.
     *
     * If the system properties are unset, then this call will automatically fail.
     *
     * @param arn The Amazon Resource Name to validate.
     * @param externalId The external ID this ARN has authorized.
     * @param region The region to validate within.
     * @param executorService The executor service to issue the request.
     */
    public static DeferredResult<Credentials> getArnSessionCredentialsAsync(String arn,
            String externalId, String region, ExecutorService executorService) {
        AWSCredentialsProvider serviceAwsCredentials;
        try {
            serviceAwsCredentials = new AWSStaticCredentialsProvider(
                    new BasicAWSCredentials(AWS_MASTER_ACCOUNT_ACCESS_KEY,
                            AWS_MASTER_ACCOUNT_SECRET_KEY));
        } catch (Throwable t) {
            return DeferredResult.failed(t);
        }

        AWSSecurityTokenServiceAsync awsSecurityTokenServiceAsync =
                AWSSecurityTokenServiceAsyncClientBuilder.standard()
                        .withRegion(region)
                        .withCredentials(serviceAwsCredentials)
                        .withExecutorFactory(() -> executorService)
                        .build();

        AssumeRoleRequest assumeRoleRequest = new AssumeRoleRequest()
                .withRoleArn(arn)
                .withRoleSessionName(UUID.randomUUID().toString())
                .withDurationSeconds(getArnSessionDurationSeconds())
                .withExternalId(externalId);

        DeferredResult<AssumeRoleResult> r = new DeferredResult<>();
        OperationContext operationContext = OperationContext.getOperationContext();
        awsSecurityTokenServiceAsync.assumeRoleAsync(assumeRoleRequest,
                new AsyncHandler<AssumeRoleRequest, AssumeRoleResult>() {
                    @Override
                    public void onSuccess(AssumeRoleRequest request, AssumeRoleResult result) {
                        OperationContext.restoreOperationContext(operationContext);
                        r.complete(result);
                    }

                    @Override
                    public void onError(Exception ex) {
                        OperationContext.restoreOperationContext(operationContext);
                        r.fail(ex);
                    }
                });
        return r.thenApply(AssumeRoleResult::getCredentials);
    }

    /**
     * Deletes a security group and retries if the returned error is on the collection of retriable
     * errors that is passed into the method.
     * The operation is retried up to AWS_MAX_ERROR_RETRY times, and an exponential backoff
     * strategy is used.
     */
    public static DeferredResult<Void> deleteSecurityGroupWithRetry(
            StatelessService service,
            AmazonEC2AsyncClient client,
            DeleteSecurityGroupRequest req,
            Set<String> retriableErrors,
            int retryCount) {
        String message = "Delete AWS Security Group with id [" + req.getGroupId() + "].";

        AWSDeferredResultAsyncHandler<DeleteSecurityGroupRequest, DeleteSecurityGroupResult>
                handler = new AWSDeferredResultAsyncHandler<>(service, message);

        client.deleteSecurityGroupAsync(req, handler);

        DeferredResult<Void> result = new DeferredResult<>();
        handler.toDeferredResult()
                .thenAccept(__ -> result.complete(null))
                .exceptionally(t -> {
                    if (t.getCause() == null ||
                            !(t.getCause() instanceof AmazonEC2Exception) ||
                            !retriableErrors.contains(((AmazonEC2Exception)t.getCause())
                                    .getErrorCode())) {
                        result.fail(t);
                        return null;
                    }

                    if (retryCount < AWS_MAX_ERROR_RETRY) {
                        long delay = (long)Math.pow(2, retryCount) * 1000;
                        service.log(Level.WARNING, "Error deleting SG: [%s]. Error: [%s]. "
                                        + "Retrying in [%s] milliseconds.",
                                req.getGroupId(), t.getMessage(), delay);
                        service.getHost().schedule(() -> {
                            deleteSecurityGroupWithRetry(service, client, req, retriableErrors,
                                    retryCount + 1).whenComplete((a, th) -> {
                                        if (th == null) {
                                            result.complete(null);
                                        } else {
                                            result.fail(th);
                                        }
                                    });
                        }, delay, TimeUnit.MILLISECONDS);
                    } else {
                        service.log(Level.SEVERE, "Error deleting SG: [%s]. Error: [%s]. "
                                        + "Finished retrying.",
                                req.getGroupId(), t.getMessage());
                        result.fail(t);
                    }
                    return null;
                });
        return result;
    }

    public static DeferredResult<Operation> getUpdateDiskStatusDr(StatelessService service,
            DiskService.DiskStatus status, ResourceState diskState) {
        ((DiskService.DiskState)diskState).status = status;
        return service.sendWithDeferredResult(
                Operation.createPatch(createInventoryUri(service.getHost(), diskState.documentSelfLink))
                        .setBody(diskState)
                        .setReferer(service.getHost().getUri()));
    }

    /**
     * Update attach/detach status of disk
     */
    public static DeferredResult<Operation> updateDiskState(DiskService.DiskState diskState,
            String operation, Service service) {
        Operation diskOp = null;

        if (operation.equals(ResourceOperation.ATTACH_DISK.operation)) {
            diskState.status = DiskService.DiskStatus.ATTACHED;
            diskOp = Operation.createPatch(createInventoryUri(service.getHost(),
                    diskState.documentSelfLink))
                    .setBody(diskState)
                    .setReferer(service.getUri());
        } else if (operation.equals(ResourceOperation.DETACH_DISK.operation)) {
            diskState.persistent = Boolean.TRUE;
            diskState.status = DiskService.DiskStatus.AVAILABLE;
            diskState.customProperties.remove(DEVICE_NAME);
            diskOp = Operation.createPut(createInventoryUri(service.getHost(), diskState
                    .documentSelfLink))
                    .setBody(diskState)
                    .setReferer(service.getUri());
        }

        return service.sendWithDeferredResult(diskOp);
    }

    /**
     * Add or remove diskLink from ComputeState by sending a ServiceStateCollectionUpdateRequest.
     */
    public static DeferredResult<Operation> updateComputeState(
            String computeStateLink, List<String> diskLinks,
            String operation, Service service) {
        Map<String, Collection<Object>> collectionsToModify = Collections
                .singletonMap(ComputeService.ComputeState.FIELD_NAME_DISK_LINKS,
                        new ArrayList<>(diskLinks));

        Map<String, Collection<Object>> collectionsToAdd = null;
        Map<String, Collection<Object>> collectionsToRemove = null;
        if (operation.equals(ResourceOperation.ATTACH_DISK.operation)) {
            collectionsToAdd = collectionsToModify;
        } else {
            //DETACH case
            collectionsToRemove = collectionsToModify;
        }

        ServiceStateCollectionUpdateRequest updateDiskLinksRequest = ServiceStateCollectionUpdateRequest
                .create(collectionsToAdd, collectionsToRemove);

        Operation computeStateOp = Operation.createPatch(createInventoryUri(service.getHost(),
                computeStateLink))
                .setBody(updateDiskLinksRequest)
                .setReferer(service.getUri());

        return service.sendWithDeferredResult(computeStateOp);
    }

    public static DeferredResult<List<String>> updatePersistentDiskAsAvailable(
            List<String> diskLinks, StatelessService service) {
        List<DeferredResult<Operation>> getDiskStatesOp = diskLinks.stream()
                .map(diskLink -> service.sendWithDeferredResult(
                        Operation.createGet(createInventoryUri(service.getHost(), diskLink))
                                .setReferer(service.getHost().getUri()))
                        .exceptionally( exc -> {
                            Utils.log(AWSUtils.class, AWSUtils.class.getSimpleName(), Level.WARNING,
                                    "Exception occured while getting disk state for link '%s' : %s", diskLink, Utils.toString(exc));
                            return null;
                        })
                ).collect(Collectors.toList());

        //As this method is only called in delete operation, we should not fail if we can't find a disk.
        return DeferredResult.allOf(getDiskStatesOp)
                .thenApply(ops -> ops.stream().filter(Objects::nonNull)
                        .map(op -> op.getBody(DiskService.DiskState.class))
                        .filter(diskState -> diskState.persistent != null && diskState.persistent)
                        .map(diskState -> updateDiskAsAvailable(diskState, service))
                        .collect(Collectors.toList()));
    }

    private static String updateDiskAsAvailable(DiskService.DiskState diskState, Service service) {
        AWSUtils.updateDiskState(diskState, ResourceOperation.DETACH_DISK.operation, service)
                .thenApply(op -> op.getBody(DiskService.DiskState.class));
        return diskState.documentSelfLink;
    }

    /**
     *
     * removes the disklinks from the compute.
     */
    public static DeferredResult<ResourceState> removeDiskLinks(String computeStateLink,
            List<String> diskLinks, Service service) {

        if (CollectionUtils.isEmpty(diskLinks)) {
            return DeferredResult.completed(new ResourceState());
        }

        return AWSUtils.updateComputeState(computeStateLink, diskLinks,
                ResourceOperation.DETACH_DISK.operation, service)
                .thenApply(op -> (ResourceState) (op.getBody(ComputeService.ComputeState.class)));
    }


    public static DeferredResult<DiskService.DiskState> setDeleteOnTerminateAttribute(
            AmazonEC2AsyncClient client, String instanceId,
            Map<String, Pair<String, Boolean>> deleteDiskMapByDeviceName,
            OperationContext opCtx) {
        List<InstanceBlockDeviceMappingSpecification> instanceBlockDeviceMappingSpecificationList =
                deleteDiskMapByDeviceName.entrySet().stream()
                        .map(entry -> new InstanceBlockDeviceMappingSpecification()
                                .withDeviceName(entry.getKey())
                                .withEbs(
                                        new EbsInstanceBlockDeviceSpecification()
                                                .withDeleteOnTermination(entry.getValue().right)
                                                .withVolumeId(entry.getValue().left)
                                )
                        ).collect(Collectors.toList());

        DeferredResult<DiskService.DiskState> modifyInstanceAttrDr = new DeferredResult();
        ModifyInstanceAttributeRequest modifyInstanceAttrReq =
                new ModifyInstanceAttributeRequest()
                        .withInstanceId(instanceId).withAttribute(InstanceAttributeName.BlockDeviceMapping)
                        .withBlockDeviceMappings(instanceBlockDeviceMappingSpecificationList);

        AWSAsyncHandler<ModifyInstanceAttributeRequest, ModifyInstanceAttributeResult> modifyInstanceAttrHandler =
                new AWSAsyncHandler<ModifyInstanceAttributeRequest, ModifyInstanceAttributeResult>() {
                    @Override
                    protected void handleError(Exception exception) {
                        OperationContext.restoreOperationContext(opCtx);
                        modifyInstanceAttrDr.fail(exception);
                    }

                    @Override
                    protected void handleSuccess(
                            ModifyInstanceAttributeRequest request,
                            ModifyInstanceAttributeResult result) {
                        OperationContext.restoreOperationContext(opCtx);
                        modifyInstanceAttrDr.complete(new DiskService.DiskState());
                    }
                };
        client.modifyInstanceAttributeAsync(modifyInstanceAttrReq, modifyInstanceAttrHandler);
        return modifyInstanceAttrDr;
    }

}
