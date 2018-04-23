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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import static com.vmware.photon.controller.discovery.endpoints.CostUsageReportCreationUtils.checkIfReportAlreadyExists;
import static com.vmware.photon.controller.discovery.endpoints.CostUsageReportCreationUtils.deleteCostAndUsageConfiguration;
import static com.vmware.photon.controller.discovery.endpoints.TestEndpointUtils.createAwsCredentials;

import com.amazonaws.services.costandusagereport.model.AWSCostAndUsageReportException;

public class TestCostUsageReportUtils {

    public static void deleteReportAndAssert(String accessKey, String secretKey,
             String s3bucketName, String s3Prefix, String costAndUsageReportName) {

        boolean ifReportExists = true;
        try {

            Credentials awsCredentials = createAwsCredentials(accessKey, secretKey);
            deleteCostAndUsageConfiguration(awsCredentials, costAndUsageReportName);
            ifReportExists = checkIfReportAlreadyExists(awsCredentials, s3bucketName, s3Prefix,
                    costAndUsageReportName);

        } catch (AWSCostAndUsageReportException ex) {
            // Exception can occur if credentials are wrong, sufficient access permissions are
            // absent, trying to delete a report that doesn't exist etc
            fail("Unable to clean up the cost report from AWS! ErrorCode = " + ex.getErrorCode());
        }
        // Assert report is actually deleted
        assertEquals(ifReportExists, false);
    }

}

