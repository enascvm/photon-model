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
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_DISK_REQUEST_TIMEOUT_MINUTES;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.DISK_IOPS;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.EBS_VOLUME_SIZE_IN_MEBI_BYTES;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSAuthentication;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.regionId;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.verifyRemovalOfResourceState;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.zoneId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.DescribeVolumesRequest;
import com.amazonaws.services.ec2.model.DescribeVolumesResult;
import com.amazonaws.services.ec2.model.Volume;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ProvisionDiskTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionDiskTaskService.ProvisionDiskTaskState;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class TestProvisionAWSDisk {

    private static final String VOLUMEID_PREFIX = "vol-";
    private static Boolean isEncrypted = true;

    public String accessKey = "accessKey";
    public String secretKey = "secretKey";

    public boolean isMock = true;
    public boolean isAwsClientMock = false;
    public String awsMockEndpointReference = null;

    private VerificationHost host;
    private EndpointState endpointState;
    private AmazonEC2AsyncClient client;
    private DiskService.DiskState diskState;

    @Rule
    public TestName currentTestName = new TestName();

    @Before
    public void setUp() throws Exception {

        CommandLineArgumentParser.parseFromProperties(this);

        setAwsClientMockInfo(this.isAwsClientMock, this.awsMockEndpointReference);

        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
        creds.privateKey = this.secretKey;
        creds.privateKeyId = this.accessKey;

        this.client = AWSUtils.getAsyncClient(creds, TestAWSSetupUtils.regionId, getExecutor());

        this.host = VerificationHost.create(0);
        try {
            this.host.setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS.toMicros(250));
            this.host.start();
            PhotonModelServices.startServices(this.host);
            PhotonModelMetricServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            PhotonModelAdaptersRegistryAdapters.startServices(this.host);
            AWSAdapters.startServices(this.host);

            this.host.setTimeoutSeconds(600);

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelAdaptersRegistryAdapters.LINKS);
            this.host.waitForServiceAvailable(AWSAdapters.LINKS);
        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    @After
    public void tearDown() throws InterruptedException {
        if (this.host == null) {
            return;
        }
        // try to delete the Disks
        if (this.diskState != null && this.diskState.id.startsWith(VOLUMEID_PREFIX)) {
            try {
                TestAWSSetupUtils
                        .deleteDisks(this.diskState.documentSelfLink, this.isMock, this.host,
                                false);

                TestAWSSetupUtils
                        .deleteEbsVolumeUsingEC2Client(this.client, this.host, this.diskState.id);
            } catch (Throwable deleteEx) {
                this.host.log(Level.WARNING, "Exception deleting Disk - %s", deleteEx.getMessage());
            }
        }
        this.host.tearDownInProcessPeers();
        this.host.toggleNegativeTestMode(false);
        this.host.tearDown();

        setAwsClientMockInfo(false, null);
    }

    public static ExecutorService getExecutor() {
        return Executors.newFixedThreadPool(Utils.DEFAULT_THREAD_COUNT,
                r -> new Thread(r, "test/" + Utils.getNowMicrosUtc()));
    }

    /**
     * Method that sets basic information in {@link AWSUtils} for aws-mock. Aws-mock is a
     * open-source tool for testing AWS services in a mock EC2 environment.
     */
    public static void setAwsClientMockInfo(boolean isAwsClientMock,
            String awsMockEndpointReference) {
        AWSUtils.setAwsClientMock(isAwsClientMock);
        AWSUtils.setAwsMockHost(awsMockEndpointReference);
    }

    @Test
    public void testDiskProvision() throws Throwable {
        createEndpoint();

        this.diskState = createAWSDiskState(this.host, this.endpointState,
                this.currentTestName.getMethodName() + "_disk1", null, regionId);

        // start provision task to do the actual disk creation
        ProvisionDiskTaskState provisionTask = new ProvisionDiskTaskState();
        provisionTask.taskSubStage = ProvisionDiskTaskState.SubStage.CREATING_DISK;

        provisionTask.diskLink = this.diskState.documentSelfLink;
        provisionTask.isMockRequest = this.isMock;

        provisionTask.documentExpirationTimeMicros =
                Utils.getNowMicrosUtc() + TimeUnit.MINUTES.toMicros(
                        AWS_DISK_REQUEST_TIMEOUT_MINUTES);
        provisionTask.tenantLinks = this.endpointState.tenantLinks;

        provisionTask = TestUtils.doPost(this.host,
                provisionTask, ProvisionDiskTaskState.class,
                UriUtils.buildUri(this.host, ProvisionDiskTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(ProvisionDiskTaskState.class,
                provisionTask.documentSelfLink);

        // check that the disk has been created
        ProvisioningUtils.queryDiskInstances(this.host, 1);

        if (!this.isMock) {
            DiskState disk = getDisk(this.host, this.diskState.documentSelfLink);
            String volumeId = disk.id;

            List<Volume> volumes = getAwsDisksByIds(this.client, this.host,
                    Collections.singletonList(volumeId));
            Volume volume = volumes.get(0);

            //check disk size
            assertEquals("Disk size is not matching with the volume size on aws",
                    disk.capacityMBytes, volume.getSize() * 1024);

            //check volume type
            assertEquals("Disk type is not matching the volume type on aws",
                    disk.customProperties.get("volumeType"), volume.getVolumeType());

            //check iops of disk
            String diskIops = disk.customProperties.get(DISK_IOPS);
            if (diskIops != null) {
                int requestedIops = Integer.parseInt(diskIops);
                int MAX_SUPPORTED_IOPS = (int) (disk.capacityMBytes / 1024) * 50;
                int provisionedIops = Math.min(requestedIops, MAX_SUPPORTED_IOPS);
                assertEquals("Disk iops is not matching with the volume on aws", provisionedIops,
                        volume.getIops().intValue());
            }

            assertEquals("availability zones are not matching", disk.zoneId,
                    volume.getAvailabilityZone());

            assertTrue("disk status not matching", disk.status == DiskService.DiskStatus.AVAILABLE);

            assertEquals("disk is encrypted", 0, disk.encrypted.compareTo(isEncrypted));

        }

        List<String> disksToDelete = new ArrayList<>();
        disksToDelete.add(this.diskState.documentSelfLink);

        //delete the local disk state documents
        TestAWSSetupUtils
                .deleteDisks(this.diskState.documentSelfLink, this.isMock, this.host, true);

        //validates the local documents of disk links have been removed
        verifyRemovalOfResourceState(this.host, disksToDelete);
    }

    /**
     * Method to get Disk details directly from Amazon
     */
    public static List<Volume> getAwsDisksByIds(AmazonEC2AsyncClient client,
            VerificationHost host, List<String> diskIds) throws Throwable {

        host.log("Getting disks with ids " + diskIds
                + " from the AWS endpoint using the EC2 client.");

        DescribeVolumesRequest describeVolumesRequest = new DescribeVolumesRequest()
                .withVolumeIds(diskIds);

        DescribeVolumesResult describeVolumesResult = client
                .describeVolumes(describeVolumesRequest);

        return new ArrayList<>(describeVolumesResult.getVolumes());
    }

    public static DiskState getDisk(VerificationHost host, String diskLink)
            throws Throwable {
        Operation response = host
                .waitForResponse(Operation.createGet(host, diskLink));
        return response.getBody(DiskState.class);
    }

    public static DiskState createAWSDiskState(VerificationHost host,
            EndpointState endpointState, String diskName, String zoneId, String regionId)
            throws Throwable {

        // Step 1: Create a Disk State
        DiskState diskDesc = new DiskState();

        diskDesc.name = diskName;
        diskDesc.capacityMBytes = EBS_VOLUME_SIZE_IN_MEBI_BYTES;
        diskDesc.encrypted = isEncrypted;

        diskDesc.zoneId = zoneId;
        diskDesc.regionId = regionId;

        diskDesc.endpointLink = endpointState.documentSelfLink;
        diskDesc.endpointLinks = new HashSet<String>();
        diskDesc.endpointLinks.add(endpointState.documentSelfLink);
        diskDesc.tenantLinks = endpointState.tenantLinks;
        diskDesc.authCredentialsLink = endpointState.authCredentialsLink;

        diskDesc.diskAdapterReference = UriUtils.buildUri(host, AWSDiskService.SELF_LINK);

        diskDesc.customProperties = new HashMap<>();
        diskDesc.customProperties.put("deviceType", "ebs");
        diskDesc.customProperties.put("volumeType", "gp2");

        DiskState disk = TestUtils.doPost(host, diskDesc, DiskService.DiskState.class,
                UriUtils.buildUri(host, DiskService.FACTORY_LINK));

        return disk;
    }

    private void createEndpoint() throws Throwable {
        AuthCredentialsServiceState auth = createAWSAuthentication(this.host, this.accessKey,
                this.secretKey);
        this.endpointState = TestAWSSetupUtils
                .createAWSEndpointState(this.host, auth.documentSelfLink, null);
    }

}
