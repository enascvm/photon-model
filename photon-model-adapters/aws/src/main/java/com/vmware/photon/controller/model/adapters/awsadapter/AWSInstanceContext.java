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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.InstanceNetworkInterfaceSpecification;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapters.util.instance.BaseComputeInstanceContext;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.xenon.common.StatelessService;

/**
 * AWS allocation.
 */
public class AWSInstanceContext
        extends BaseComputeInstanceContext<AWSInstanceContext, AWSInstanceContext.AWSNicContext> {

    /**
     * The class encapsulates NIC related data (both Photon Model and AWS model) used during
     * provisioning.
     */
    public static class AWSNicContext extends BaseComputeInstanceContext.BaseNicContext {

        // The AWS vpc-subnet pair this NIC is associated to. It is created by this service.
        public Vpc vpc;
        public Subnet subnet;

        // The security group Ids list that will be assigned to the current NIC networking
        // configuration
        public List<String> groupIds;

        // The NIC spec that should be created by AWS.
        public InstanceNetworkInterfaceSpecification nicSpec;
    }

    public AWSInstanceStage stage;

    public AmazonEC2AsyncClient amazonEC2Client;
    public Map<DiskType, DiskState> childDisks;
    public long taskExpirationMicros;

    public AWSInstanceContext(StatelessService service, ComputeInstanceRequest computeRequest) {
        super(service, computeRequest, AWSNicContext::new);
    }

    /**
     * Get the effective list of AWS NIC Specs that should be created during VM provisioning.
     */
    public List<InstanceNetworkInterfaceSpecification> getAWSNicSpecs() {
        return this.nics.stream()
                .filter(nic -> nic.nicSpec != null)
                .map(nic -> nic.nicSpec)
                .collect(Collectors.toList());
    }
}
