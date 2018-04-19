/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.discovery.endpoints;

import static java.util.Arrays.asList;

import static com.sun.javafx.runtime.async.BackgroundExecutor.getExecutor;

import static com.vmware.photon.controller.discovery.common.utils.StringUtil.isEmpty;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_COST_USAGE_COMPRESSION;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_COST_USAGE_FORMAT;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_COST_USAGE_REPORT_SERVICE_REGION;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_COST_USAGE_SCHEMA_ELEMENTS;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_COST_USAGE_TIME_UNIT;

import java.util.Collection;
import java.util.List;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.costandusagereport.AWSCostAndUsageReportAsync;
import com.amazonaws.services.costandusagereport.AWSCostAndUsageReportAsyncClientBuilder;
import com.amazonaws.services.costandusagereport.model.AWSCostAndUsageReportException;
import com.amazonaws.services.costandusagereport.model.DeleteReportDefinitionRequest;
import com.amazonaws.services.costandusagereport.model.DescribeReportDefinitionsRequest;
import com.amazonaws.services.costandusagereport.model.DescribeReportDefinitionsResult;
import com.amazonaws.services.costandusagereport.model.PutReportDefinitionRequest;
import com.amazonaws.services.costandusagereport.model.ReportDefinition;


/**
 *  Cost and Usage Report Utils
 */
public class CostUsageReportCreationUtils {

    /**
     * Method to check if the report exists in the provided S3 bucket and prefix
     * NOTE: Requires listObjects() and/or cur:DescribeReportDefinition for IAM
     *
     * @param credentials
     * @param s3BucketName
     * @param s3BucketPrefix
     * @param reportName
     * @return
     * @throws AWSCostAndUsageReportException
     */
    public static boolean checkIfReportAlreadyExists(Credentials credentials, String s3BucketName,
            String s3BucketPrefix, String reportName)
            throws AWSCostAndUsageReportException {

        AWSCredentialsProvider credentialsProvider = getAWSCredentialProvider(credentials);
        AWSCostAndUsageReportAsync awsCurClient = getAwsCostAndUsageReportClient(credentialsProvider);
        DescribeReportDefinitionsRequest request = new DescribeReportDefinitionsRequest();
        DescribeReportDefinitionsResult result = awsCurClient
                .describeReportDefinitions(request);

        List<ReportDefinition> reportDefinitions = result.getReportDefinitions();
        if (reportDefinitions == null) {
            return false;
        }

        for (ReportDefinition reportDefinition : reportDefinitions) {
            if (reportDefinition == null) {
                continue;
            }

            String reportBucket = reportDefinition.getS3Bucket();
            if (s3BucketName.equals(reportBucket)
                    && s3BucketPrefix.equals(reportDefinition.getS3Prefix())
                    && reportName.equals(reportDefinition.getReportName())) {

                return true;
            }
        }

        return false;
    }


    /**
     *  Method to create Cost and Usage Remote in the S3 bucket on AWS
     *  NOTE: Requires putObject() and/or cur:PutReportDefinitions for IAM
     *
     * @param credentials
     * @param bucketRegion
     * @param s3bucketName
     * @param s3bucketPrefix
     * @param costUsageReportName
     * @throws AWSCostAndUsageReportException
     */
    public static void createCostAndUsageReportOnAws(Credentials credentials, String
            bucketRegion, String s3bucketName, String s3bucketPrefix, String costUsageReportName)
            throws AWSCostAndUsageReportException {

        AWSCredentialsProvider credentialsProvider = getAWSCredentialProvider(credentials);
        AWSCostAndUsageReportAsync awsCurClient = getAwsCostAndUsageReportClient(credentialsProvider);

        Collection<String> schemaElements = asList(ENDPOINT_COST_USAGE_SCHEMA_ELEMENTS);

        PutReportDefinitionRequest putReportDefinitionRequest = new PutReportDefinitionRequest()
                .withReportDefinition(new ReportDefinition()
                        .withReportName(costUsageReportName)
                        .withCompression(ENDPOINT_COST_USAGE_COMPRESSION)
                        .withFormat(ENDPOINT_COST_USAGE_FORMAT)
                        .withTimeUnit(ENDPOINT_COST_USAGE_TIME_UNIT)
                        .withS3Bucket(s3bucketName)
                        .withS3Prefix(s3bucketPrefix)
                        .withS3Region(bucketRegion)
                        .withAdditionalSchemaElements(schemaElements));

        awsCurClient.putReportDefinition(putReportDefinitionRequest);
    }


    /**
     * Method to delete Cost and Usage Report
     *
     * @param credentials
     * @param costAndUsageReportName
     * @throws AWSCostAndUsageReportException
     */
    public static void deleteCostAndUsageConfiguration(Credentials credentials,
            String costAndUsageReportName) throws AWSCostAndUsageReportException {

        if (credentials == null || isEmpty(costAndUsageReportName)) {
            return;
        }

        AWSCredentialsProvider credentialsProvider = getAWSCredentialProvider(credentials);
        AWSCostAndUsageReportAsync awsCostAndUsageReport = getAwsCostAndUsageReportClient(credentialsProvider);


        DeleteReportDefinitionRequest request = new DeleteReportDefinitionRequest()
                .withReportName(costAndUsageReportName);
        awsCostAndUsageReport.deleteReportDefinition(request);
    }


    private static AWSCostAndUsageReportAsync getAwsCostAndUsageReportClient(
            AWSCredentialsProvider credentialsProvider) {

        return AWSCostAndUsageReportAsyncClientBuilder
                    .standard()
                    .withRegion(ENDPOINT_COST_USAGE_REPORT_SERVICE_REGION)
                    .withCredentials(credentialsProvider)
                    .withExecutorFactory(() -> getExecutor())
                    .build();
    }


    /**
     * Get credentialsProvider that is needed by AWS SDK APIs according to the auth method
     * (ARN/KEY)
     *
     * @param credentials
     * @return
     */
    public static AWSCredentialsProvider getAWSCredentialProvider(Credentials credentials) {

        AWSCredentialsProvider credentialsProvider = null;

        if (credentials.aws != null) {
            String accessKey = credentials.aws.accessKeyId;

            if (accessKey != null) {
                credentialsProvider = new AWSStaticCredentialsProvider(
                        new BasicAWSCredentials(credentials.aws.accessKeyId,
                                credentials.aws.secretAccessKey));
            } else {
                String arn = credentials.aws.arn;
                String externalId = credentials.aws.externalId;
                credentialsProvider = new STSAssumeRoleSessionCredentialsProvider(arn, externalId);
            }
        }

        return credentialsProvider;
    }
}
