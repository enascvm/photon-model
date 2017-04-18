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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.DescribeRegionsRequest;
import com.amazonaws.services.ec2.model.DescribeRegionsResult;
import com.amazonaws.services.ec2.model.DescribeReservedInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeReservedInstancesResult;
import com.amazonaws.services.ec2.model.Region;
import com.amazonaws.services.ec2.model.ReservedInstances;
import org.apache.commons.collections.CollectionUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;

/**
 * Service to collect AWS Reserved Instances Plans for an account
 */
public class AWSReservedInstancePlanService extends StatelessService {
    public static final String SELF_LINK = AWSUriPaths.AWS_RESERVED_INSTANCE_PLANS_ADAPTER;
    private static final int NO_OF_MONTHS = 3;
    private static final String REGION = "Region";

    protected AWSClientManager ec2ClientManager;

    public static class AWSReservedInstanceContext {
        public List<ReservedInstances> reservedInstancesPlan;
        public ComputeService.ComputeStateWithDescription computeDesc;
        public AuthCredentialsService.AuthCredentialsServiceState parentAuth;
    }

    public AWSReservedInstancePlanService() {
        this.ec2ClientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.EC2);
    }

    @Override
    public void handlePost(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        op.complete();
        AWSReservedInstanceContext context = new AWSReservedInstanceContext();
        String computeLink = op.getBody(String.class);
        context.reservedInstancesPlan = Collections.synchronizedList(new ArrayList<>());
        getAccountDescription(context, computeLink);
    }

    @Override
    public void handleStop(Operation delete) {
        AWSClientManagerFactory.returnClientManager(this.ec2ClientManager,
                AWSConstants.AwsClientType.EC2);
        super.handleStop(delete);
    }

    private Consumer<Throwable> getFailureConsumer(AWSReservedInstanceContext context, String msg) {
        return ((t) -> {
            if (context != null && context.computeDesc != null) {
                log(Level.SEVERE, msg + " for compute " +
                        context.computeDesc.documentSelfLink + " " + t.getMessage());
            }
        });
    }

    protected void getAccountDescription(AWSReservedInstanceContext context, String computeLink) {
        Consumer<Operation> onSuccess = (op) -> {
            context.computeDesc = op.getBody(ComputeService.ComputeStateWithDescription.class);
            getParentAuth(context);
        };
        URI computeUri = UriUtils.extendUriWithQuery(UriUtils.buildUri(getHost(), computeLink),
                UriUtils.URI_PARAM_ODATA_EXPAND, Boolean.TRUE.toString());
        AdapterUtils.getServiceState(this, computeUri, onSuccess, getFailureConsumer(context,
                "Error while retrieving compute description"));
    }

    protected void getParentAuth(AWSReservedInstanceContext context) {
        Consumer<Operation> onSuccess = (op) -> {
            context.parentAuth = op.getBody(AuthCredentialsService.AuthCredentialsServiceState.class);
            getReservedInstancesPlans(context);
        };
        String authLink = context.computeDesc.description.authCredentialsLink;
        AdapterUtils.getServiceState(this, authLink, onSuccess, getFailureConsumer(context,
                "Error while retrieving auth credentials"));
    }

    protected void getReservedInstancesPlans(AWSReservedInstanceContext context) {
        AmazonEC2AsyncClient ec2AsyncClient = this.ec2ClientManager
                .getOrCreateEC2Client(context.parentAuth, null,
                        this, getFailureConsumer(context, "Error while creating EC2 client for"));
        ec2AsyncClient.describeRegionsAsync(getRegionAsyncHandler(context));
    }

    private AsyncHandler<DescribeRegionsRequest, DescribeRegionsResult> getRegionAsyncHandler(
            AWSReservedInstanceContext context) {
        AWSReservedInstancePlanService service = this;
        return new AsyncHandler<DescribeRegionsRequest, DescribeRegionsResult>() {
            @Override
            public void onError(Exception e) {
                log(Level.WARNING, "Error while fetching the regions for compute "
                        + context.computeDesc.documentSelfLink
                        + " " + Utils.toString(e));
            }

            @Override
            public void onSuccess(DescribeRegionsRequest request,
                    DescribeRegionsResult describeRegionsResult) {
                List<Region> regions = describeRegionsResult.getRegions();
                if (CollectionUtils.isEmpty(regions)) {
                    log(Level.INFO, "No regions exist for compute"
                            + context.computeDesc.documentSelfLink);
                    return;
                }
                AtomicInteger currentStageTaskCount = new AtomicInteger(regions.size());
                /*
                 * Fetch all the regions from AWS and collect reserved instances plans for
                 * only those regions which are supported by SDK.
                 */
                for (Region region : regions) {
                    try {
                        Regions r = Regions.fromName(region.getRegionName());
                        if (r == null) {
                            new AWSReservedInstanceAsyncHandler(service.getHost(),
                                    currentStageTaskCount, region, context)
                                    .checkAndPatchReservedInstancesPlans();
                            continue;
                        }
                    } catch (Exception e) {
                        log(Level.WARNING, e.getMessage());
                        new AWSReservedInstanceAsyncHandler(service.getHost(),
                                currentStageTaskCount, region, context)
                                .checkAndPatchReservedInstancesPlans();
                        continue;
                    }
                    AmazonEC2AsyncClient amazonEC2Client = service.ec2ClientManager
                            .getOrCreateEC2Client(context.parentAuth,
                                    region.getRegionName(), service, getFailureConsumer(context,
                                            "Error while creating EC2 client for"));
                    if (amazonEC2Client == null) {
                        new AWSReservedInstanceAsyncHandler(service.getHost(),
                                currentStageTaskCount, region, context)
                                .checkAndPatchReservedInstancesPlans();
                        log(Level.WARNING, "client is null for region " + region.getRegionName()
                                        + "for compute " + context.computeDesc.documentSelfLink);
                    } else {
                        amazonEC2Client.describeReservedInstancesAsync(
                                new AWSReservedInstanceAsyncHandler(service.getHost(),
                                        currentStageTaskCount, region, context));
                    }
                }
            }
        };
    }

    public class AWSReservedInstanceAsyncHandler
            implements AsyncHandler<DescribeReservedInstancesRequest,
            DescribeReservedInstancesResult> {
        private AtomicInteger count;
        private Region region;
        private ServiceHost host;
        private AWSReservedInstanceContext context;

        public AWSReservedInstanceAsyncHandler(ServiceHost host, AtomicInteger count, Region region,
                AWSReservedInstanceContext context) {
            this.count = count;
            this.region = region;
            this.host = host;
            this.context = context;
        }

        @Override
        public void onError(Exception e) {
            log(Level.WARNING,
                    "Error while fetching Reserved Instances for region " + this.region.getRegionName()
                            + "for compute " + this.context.computeDesc.documentSelfLink + " " + Utils
                            .toString(e));
            checkAndPatchReservedInstancesPlans();
        }

        @Override
        public void onSuccess(DescribeReservedInstancesRequest request,
                DescribeReservedInstancesResult describeReservedInstancesResult) {
            List<ReservedInstances> reservedInstances = describeReservedInstancesResult
                    .getReservedInstances();
            if (CollectionUtils.isNotEmpty(reservedInstances)) {
                DateTime endDate = new DateTime(DateTimeZone.UTC).withDayOfMonth(1)
                        .minusMonths(NO_OF_MONTHS)
                        .withTimeAtStartOfDay();
                for (ReservedInstances reservedInstance : reservedInstances) {
                    if (reservedInstance.getEnd() != null && reservedInstance.getEnd().before(
                            endDate.toDate())) {
                        continue;
                    }
                    // Set the Region for RI's whose scope is region.
                    if (reservedInstance != null && reservedInstance.getScope() != null &&
                            reservedInstance.getScope().equals(REGION)) {
                        reservedInstance.setAvailabilityZone(this.region.getRegionName());
                    }
                    this.context.reservedInstancesPlan.add(reservedInstance);
                }
            }
            checkAndPatchReservedInstancesPlans();
        }

        public void checkAndPatchReservedInstancesPlans() {
            if (this.count.decrementAndGet() <= 0) {
                // Patch the reserved instances plans only if they are changed
                String reservedInstancesPlans = this.context.computeDesc.customProperties
                        .getOrDefault(AWSConstants.RESERVED_INSTANCE_PLAN_DETAILS, null);
                if (reservedInstancesPlans != null) {
                    this.context.reservedInstancesPlan.sort(new ReservedInstancesIdComparator());
                    if (!reservedInstancesPlans
                            .equals(Utils.toJson(this.context.reservedInstancesPlan))) {
                        setCustomProperty(AWSConstants.RESERVED_INSTANCE_PLAN_DETAILS,
                                Utils.toJson(this.context.reservedInstancesPlan));
                    } else {
                        log(Level.FINE, "Reserved Instances plans are not changed for compute " +
                                this.context.computeDesc.documentSelfLink);
                    }
                } else {
                    if (this.context.reservedInstancesPlan.size() > 0) {
                        log(Level.FINE, "Patching " + this.context.reservedInstancesPlan.size() +
                                " Reserved Instances Plans for compute "
                                + this.context.computeDesc.documentSelfLink);
                        this.context.reservedInstancesPlan.sort(new ReservedInstancesIdComparator());
                        setCustomProperty(AWSConstants.RESERVED_INSTANCE_PLAN_DETAILS,
                                Utils.toJson(this.context.reservedInstancesPlan));
                    } else {
                        log(Level.FINE, "Reserved Instances plans are not present for compute " +
                                this.context.computeDesc.documentSelfLink);
                    }
                }

            }
        }

        private class ReservedInstancesIdComparator implements Comparator<ReservedInstances> {
            @Override
            public int compare(ReservedInstances ri1, ReservedInstances ri2) {
                return ri1.getReservedInstancesId().compareTo(ri2.getReservedInstancesId());
            }
        }

        private void setCustomProperty(String key, String value) {
            ComputeService.ComputeState accountState = new ComputeService.ComputeState();
            accountState.customProperties = new HashMap<>();
            accountState.customProperties.put(key, value);
            sendRequest(Operation.createPatch(this.host, this.context.computeDesc.documentSelfLink)
                    .setBody(accountState));
        }
    }

}
