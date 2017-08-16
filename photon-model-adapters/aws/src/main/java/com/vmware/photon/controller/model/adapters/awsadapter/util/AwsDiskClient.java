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

package com.vmware.photon.controller.model.adapters.awsadapter.util;

import java.util.List;

import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.AvailabilityZone;
import com.amazonaws.services.ec2.model.CreateVolumeRequest;
import com.amazonaws.services.ec2.model.CreateVolumeResult;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;


public class AwsDiskClient {

    private final AmazonEC2AsyncClient client;

    public AwsDiskClient(AmazonEC2AsyncClient client) {
        this.client = client;
    }

    /**
     * Creates an ebs volume.
     */
    public void createVolume(CreateVolumeRequest req,
            AsyncHandler<CreateVolumeRequest, CreateVolumeResult> creationHandler) {
        this.client.createVolumeAsync(req, creationHandler);
    }

    public List<AvailabilityZone> getAvailabilityZones() {
        DescribeAvailabilityZonesResult res = this.client.describeAvailabilityZones();
        return res.getAvailabilityZones();
    }
}
