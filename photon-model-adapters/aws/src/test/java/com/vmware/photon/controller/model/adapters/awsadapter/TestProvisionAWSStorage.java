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


import static org.junit.Assert.assertEquals;


import java.util.ArrayList;
import java.util.List;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.Instance;


import org.junit.Ignore;
import org.junit.Test;

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.DEVICE_NAME;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.DEVICE_TYPE;

import com.vmware.photon.controller.model.resources.DiskService;



/**
 *  This class is used for testing the addition of instance-store disks to the instane-store AMI.
 */
public class TestProvisionAWSStorage extends TestAWSProvisionTask {

    public static final String INSTANCE_STORE_AMI = "ami-6872c27e";
    public static final String INSTANCE_TYPE = "d2.xlarge";

    public static final String IMAGE_DISKS_AMI = "ami-b8bbabd2";
    public static final String IMAGE_DISKS_INSTANCE_TYPE = "f1.2xlarge";

    public static boolean isExistingDiskCustomizationTest = false;

    public void setUp(String imageId, String instanceType, boolean isExistingDiskCustomizationTest)
            throws Exception {
        TestAWSSetupUtils.imageId = imageId;
        TestAWSSetupUtils.instanceType = instanceType;
        this.isExistingDiskCustomizationTest = isExistingDiskCustomizationTest;
        super.setUp();
    }

    //Ignoring test case because if this test runs in parallel(as part of different pipelines),
    //then the test might fail with InstanceLimitExceeded(Your quota allows for 0 more running
    //instance(s). You requested at least 1)
    @Ignore
    @Override
    @Test
    public void testProvision() throws Throwable {
        setUp(INSTANCE_STORE_AMI, INSTANCE_TYPE, false);
        super.testProvision();
    }

    /**
     * The size of the disk depends on the instance-type and can be verified by logging into the machine.
     * The requested device name can be verfied by making a get request from the provisioned machine.
     *  For e.g. curl http://169.254.169.254/latest/meta-data/block-device-mapping/ephemeral0
     */
    @Override
    protected void assertDataDiskConfiguration(AmazonEC2AsyncClient client,
            Instance awsInstance, List<String> diskLinks) {
        List<String> existingNames = new ArrayList<>();
        for (String diskLink : diskLinks) {
            DiskService.DiskState diskState = getDiskState(diskLink);
            if (diskState.customProperties.get(DEVICE_TYPE)
                    .equals(AWSConstants.AWSStorageType.EBS.getName())) {
                assertEbsDiskConfiguration(client, awsInstance, diskState);

            } else {
                assertEquals(String.format(
                        "Data disk size is not matching to the size supported by %s",
                        TestAWSSetupUtils.imageId),
                        getSupportedInstanceStoreDiskSize(TestAWSSetupUtils.instanceType)
                                .intValue(),
                        (int) diskState.capacityMBytes);

                assertEquals("Data disk attach status is not matching",
                        DiskService.DiskStatus.ATTACHED, diskState.status);


            }
            super.assertDeviceName(awsInstance, diskState, existingNames);
            existingNames.add(diskState.customProperties.get(DEVICE_NAME));
        }
    }

    @Override
    protected void assertBootDiskConfiguration(AmazonEC2AsyncClient client, Instance awsInstance,
            String diskLink) {
        if (isExistingDiskCustomizationTest) {
            super.assertBootDiskConfiguration(client, awsInstance, diskLink);
        } else {
            DiskService.DiskState bootDisk = super.getDiskState(diskLink);
            assertEquals(String.format("Boot disk size of %s is not the same as supported by %s",
                    INSTANCE_STORE_AMI, INSTANCE_TYPE),
                    getSupportedInstanceStoreDiskSize(INSTANCE_TYPE).intValue(),
                    (int) bootDisk.capacityMBytes);
            assertEquals("Boot disk attach status is not matching",
                    DiskService.DiskStatus.ATTACHED, bootDisk.status);
        }
    }

    @Test
    public void testExistingDiskCustomization() throws Throwable {
        setUp(IMAGE_DISKS_AMI, IMAGE_DISKS_INSTANCE_TYPE, true);
        super.testProvision();
    }
}