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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeVpcsResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.Bucket;
import org.junit.Before;
import org.junit.Test;

import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class AWSRemoteCleanup extends BasicTestCase {
    public String accessKey = "accessKey";
    public String secretKey = "secretKey";
    public boolean isMock = true;
    public Map<String, AmazonS3Client> s3Clients = new HashMap<>();
    public Map<String, AmazonEC2> ec2Clients = new HashMap<>();

    @Before
    public void setUp() {
        CommandLineArgumentParser.parseFromProperties(this);
        this.host.setTimeoutSeconds(600);

        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
        creds.privateKey = this.secretKey;
        creds.privateKeyId = this.accessKey;

        for (Regions region : Regions.values()) {
            try {
                this.s3Clients.put(region.getName(), AWSUtils.getS3Client(creds, region.getName()));
            } catch (Exception e) {
                continue;
            }
        }

        for (Regions region : Regions.values()) {
            try {
                this.ec2Clients.put(region.getName(), TestUtils.getEC2SynchronousClient(creds, region.getName()));
            } catch (Exception e) {
                continue;
            }
        }
    }

    @Test
    public void cleanUpAWSEC2() {
        if (this.isMock) {
            return;
        }

        for (AmazonEC2 ec2Client : this.ec2Clients.values()) {
            try {
                DescribeVpcsResult describeVpcsRequest = ec2Client.describeVpcs();

                List<Vpc> vpcs = describeVpcsRequest.getVpcs();
                List<String> enumTestVpcIds = new ArrayList<>();
                List<String> instanceIdsToBeDeleted = new ArrayList<>();

                vpcs.stream()
                        .forEach(vpc -> {
                            vpc.getTags().stream()
                                    .forEach(tag -> {
                                        if (tag.getKey().equalsIgnoreCase("name")
                                                && tag.getValue()
                                                .equalsIgnoreCase("enumtest-vpc")) {
                                            enumTestVpcIds.add(vpc.getVpcId());
                                        }
                                    });
                        });

                DescribeInstancesResult describeInstancesResult = ec2Client.describeInstances();

                List<Reservation> reservations = describeInstancesResult.getReservations();
                for (Reservation reservation : reservations) {
                    List<Instance> instances = reservation.getInstances();
                    for (Instance instance : instances) {
                        long instanceLaunchTimeMicros = TimeUnit.MILLISECONDS
                                .toMicros(instance.getLaunchTime().getTime());
                        long timeDifference = Utils.getNowMicrosUtc() - instanceLaunchTimeMicros;

                        if (timeDifference > TimeUnit.HOURS.toMicros(1)
                                && enumTestVpcIds.contains(instance.getVpcId())
                                && shouldDelete(instance)) {
                            instanceIdsToBeDeleted.add(instance.getInstanceId());
                        }
                    }
                }

                if (instanceIdsToBeDeleted.isEmpty()) {
                    continue;
                }

                TerminateInstancesRequest terminateInstancesRequest = new
                        TerminateInstancesRequest(instanceIdsToBeDeleted);
                TerminateInstancesResult terminateInstancesResult = ec2Client
                        .terminateInstances(terminateInstancesRequest);

                terminateInstancesResult.getTerminatingInstances().stream()
                        .forEach(instanceStateChange -> {
                            this.host.log("Terminating stale instance: %s",
                                    instanceStateChange.getInstanceId());
                        });
            } catch (Exception e) {
                this.host.log(Level.INFO, e.getMessage());
                continue;
            }
        }
    }

    @Test
    public void cleanUpAWSS3() {
        if (this.isMock) {
            return;
        }

        List<Bucket> buckets = this.s3Clients.get(Regions.DEFAULT_REGION.getName()).listBuckets();

        for (Bucket bucket : buckets) {
            long bucketCreationTimeMicros = TimeUnit
                    .MILLISECONDS
                    .toMicros(bucket.getCreationDate().getTime());

            long timeDifference = Utils.getNowMicrosUtc() - bucketCreationTimeMicros;

            if (bucket.getName().contains("enumtest-bucket")
                    && timeDifference > TimeUnit.HOURS.toMicros(1)
                    && !bucket.getName().contains("enumtest-bucket-do-not-delete")) {
                for (AmazonS3Client s3Client : this.s3Clients.values()) {
                    try {
                        s3Client.deleteBucket(bucket.getName());
                        this.host.log(Level.INFO, "Deleting stale bucket %s", bucket.getName());
                    } catch (Exception e) {
                        continue;
                    }
                }
            }
        }
    }

    private static boolean shouldDelete(Instance instance) {
        for (Tag tag : instance.getTags()) {
            if (tag.getKey().equalsIgnoreCase("name")
                    && tag.getValue().equalsIgnoreCase("DoNotDelete")) {
                return false;
            }
        }
        return true;
    }
}