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

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.amazonaws.services.ec2.model.DescribeReservedInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeReservedInstancesResult;
import com.amazonaws.services.ec2.model.ReservedInstances;

import com.vmware.photon.controller.model.resources.ComputeService;

public class MockAWSReservedInstancePlansService extends AWSReservedInstancePlanService {
    public static final String SELF_LINK = AWSUriPaths.PROVISIONING_AWS
            + "/mock-reserved-instance-plans-adapter";

    @Override
    protected void getAccountDescription(AWSReservedInstanceContext context, String computeLink) {
        ComputeService.ComputeStateWithDescription computeDesc =
                new ComputeService.ComputeStateWithDescription();
        computeDesc.documentSelfLink = computeLink;
        computeDesc.customProperties = new HashMap<>();
        context.computeDesc = computeDesc;
        getReservedInstancesPlans(context);
    }

    @Override
    protected void getReservedInstancesPlans(AWSReservedInstanceContext context) {
        AtomicInteger currentStageTaskCount = new AtomicInteger(1);
        DescribeReservedInstancesRequest request = new DescribeReservedInstancesRequest();
        DescribeReservedInstancesResult result = new DescribeReservedInstancesResult();
        result.setReservedInstances(Arrays.asList(createReservedInstance("ri1")));
        new AWSReservedInstanceAsyncHandler(getHost(), currentStageTaskCount, null, context)
                .onSuccess(request, result);
    }

    private ReservedInstances createReservedInstance(String id) {
        ReservedInstances r = new ReservedInstances();
        r.setReservedInstancesId(id);
        r.setFixedPrice(0.003f);
        r.setStart(new Date());
        r.setDuration(10000000L);
        return r;
    }
}
