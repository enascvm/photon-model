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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.InstanceNetworkInterfaceSpecification;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.photon.controller.model.resources.FirewallService.FirewallState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceStateWithDescription;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.xenon.common.Service;

/**
 * AWS allocation.
 */
public class AWSAllocationContext extends BaseAdapterContext {

    public AWSStages stage;


    public ComputeInstanceRequest computeRequest;
    public AmazonEC2AsyncClient amazonEC2Client;
    public Map<DiskType, DiskState> childDisks;
    public long taskExpirationMicros;

    /**
     * The class encapsulates NIC related data (both Photon Model and AWS model) used during
     * provisioning.
     */
    public static class NicAllocationContext {

        // NIC related states (resolved by links) related to the ComputeState that is provisioned.
        public NetworkInterfaceStateWithDescription nicStateWithDesc;
        public NetworkState networkState;
        public SubnetState subnetState;
        public Map<String, FirewallState> firewallStates = new HashMap<>();

        // The AWS vpc-subnet pair this NIC is associated to. It is created by this service.
        public Vpc vpc;
        public Subnet subnet;

        // The security group Ids list that will be assigned to the current NIC networking
        // configuration
        public List<String> groupIds;

        // The NIC spec that should be created by AWS.
        public InstanceNetworkInterfaceSpecification nicSpec;
    }

    /**
     * Holds allocation data for all VM NICs.
     */
    public List<NicAllocationContext> nics = new ArrayList<>();

    /**
     * Initialize with request info and first stage.
     */
    public AWSAllocationContext(Service service, ComputeInstanceRequest computeReq) {
        this(service, computeReq, computeReq.resourceReference);
    }

    public AWSAllocationContext(Service service, ComputeInstanceRequest computeReq,
            URI resourceReference) {
        super(service, resourceReference);
        this.computeRequest = computeReq;
        this.stage = AWSStages.PROVISIONTASK;
    }

    /**
     * First NIC is considered primary.
     */
    public NicAllocationContext getVmPrimaryNic() {
        return this.nics.get(0);
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
