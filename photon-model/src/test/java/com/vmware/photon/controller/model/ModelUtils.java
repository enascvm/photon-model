/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionServiceTest;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.IPAddressService;
import com.vmware.photon.controller.model.resources.IPAddressService.IPAddressState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule;
import com.vmware.photon.controller.model.resources.SnapshotService;
import com.vmware.photon.controller.model.resources.SubnetRangeService;
import com.vmware.photon.controller.model.resources.SubnetRangeService.SubnetRangeState;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.support.IPVersion;
import com.vmware.xenon.common.UriUtils;

/**
 * Utility class to create service documents for tests.
 */
public class ModelUtils {
    private static final String TEST_DESC_PROPERTY_NAME = "testDescProperty";
    public static final String COMPUTE_CUSTOM_PROPERTY_HAS_SNAPSHOTS = "__hasSnapshots";
    private static final String TEST_DESC_PROPERTY_VALUE = UUID.randomUUID()
            .toString();

    public static ComputeDescriptionService.ComputeDescription createComputeDescription(
            BaseModelTest test, String instanceAdapterLink, String bootAdapterLink)
            throws Throwable {
        ComputeDescriptionService.ComputeDescription cd = ComputeDescriptionServiceTest
                .buildValidStartState();
        // disable periodic maintenance for tests by default.
        cd.healthAdapterReference = null;
        if (instanceAdapterLink != null) {
            cd.instanceAdapterReference = UriUtils.buildUri(test.getHost(),
                    instanceAdapterLink);
        }
        if (bootAdapterLink != null) {
            cd.bootAdapterReference = UriUtils.buildUri(test.getHost(), bootAdapterLink);
        }
        return test.postServiceSynchronously(
                ComputeDescriptionService.FACTORY_LINK, cd,
                ComputeDescriptionService.ComputeDescription.class);
    }

    public static ComputeService.ComputeStateWithDescription createCompute(
            BaseModelTest test, ComputeDescriptionService.ComputeDescription cd)
            throws Throwable {
        ComputeService.ComputeState cs = new ComputeService.ComputeStateWithDescription();
        cs.id = UUID.randomUUID().toString();
        cs.name = cd.name;
        cs.descriptionLink = cd.documentSelfLink;
        cs.resourcePoolLink = null;
        cs.address = "10.0.0.1";
        cs.primaryMAC = "01:23:45:67:89:ab";
        cs.powerState = ComputeService.PowerState.ON;
        cs.adapterManagementReference = URI
                .create("https://esxhost-01:443/sdk");
        cs.diskLinks = new ArrayList<>();
        cs.diskLinks.add(createDiskState(test, cs.name).documentSelfLink);
        cs.networkInterfaceLinks = new ArrayList<>();
        EndpointState endpointState = createEndpoint(test);
        NetworkState networkState = createNetwork(test, endpointState);
        SubnetState subnetState = createSubnet(test, networkState, endpointState);
        SubnetRangeState subnetRangeState = createSubnetRange(test, subnetState,
                "12.12.12.2", "12.12.12.120");

        NetworkInterfaceState networkInterfaceState1 = createNetworkInterface(test, "nic-1",
                subnetState, networkState);
        IPAddressState ipAddressState1 = createIpAddress(test, "12.12.12.2",
                IPAddressState.IPAddressStatus.ALLOCATED, subnetRangeState,
                networkInterfaceState1.documentSelfLink);

        NetworkInterfaceState networkInterfaceState2 = createNetworkInterface(test, "nic-2",
                subnetState, networkState);
        IPAddressState ipAddressState2 = createIpAddress(test, "12.12.12.3",
                IPAddressState.IPAddressStatus.ALLOCATED, subnetRangeState,
                networkInterfaceState2.documentSelfLink);

        NetworkInterfaceState networkInterfaceState3 = createNetworkInterface(test, "nic-3",
                subnetState, networkState);
        IPAddressState ipAddressState3 = createIpAddress(test, "12.12.12.4",
                IPAddressState.IPAddressStatus.ALLOCATED, subnetRangeState,
                networkInterfaceState3.documentSelfLink);

        cs.networkInterfaceLinks.add(networkInterfaceState1.documentSelfLink);
        cs.networkInterfaceLinks.add(networkInterfaceState2.documentSelfLink);
        cs.networkInterfaceLinks.add(networkInterfaceState3.documentSelfLink);
        cs.customProperties = new HashMap<>();
        cs.customProperties.put(TEST_DESC_PROPERTY_NAME,
                TEST_DESC_PROPERTY_VALUE);
        cs.tenantLinks = new ArrayList<>();
        cs.tenantLinks.add("http://tenant");

        ComputeService.ComputeState returnState = test
                .postServiceSynchronously(ComputeService.FACTORY_LINK, cs,
                        ComputeService.ComputeState.class);

        return ComputeService.ComputeStateWithDescription.create(cd,
                returnState);
    }

    public static DiskState createDiskState(BaseModelTest test, String name) throws Throwable {
        DiskState d = new DiskState();
        d.id = UUID.randomUUID().toString();
        d.name = name;
        d.documentSelfLink = d.id;
        d.type = DiskType.HDD;
        d.sourceImageReference = new URI("http://sourceImageReference");

        DiskState returnState = test.postServiceSynchronously(DiskService.FACTORY_LINK, d,
                DiskState.class);
        return returnState;
    }

    public static EndpointState createEndpoint(BaseModelTest test) throws Throwable {
        EndpointState endpointState = new EndpointState();
        endpointState.endpointType = "vsphere";
        endpointState.computeLink = "/resources/compute/fakeGuidA";
        endpointState.computeDescriptionLink = "/resources/compute-descriptions/fakeGuidB";
        endpointState.resourcePoolLink = "/resources/pools/fakeGuidC";
        endpointState.endpointProperties = new HashMap<>();
        endpointState.endpointProperties.put("hostName", "sqa-nsxt2-vc.sqa.local");
        endpointState.endpointProperties.put("regionId", "Datacenter:datacenter-2");
        endpointState.endpointProperties.put("privateKeyId", "install.admin@sqa.local");
        endpointState.endpointProperties.put("supportDatastores", "true");
        endpointState.name = "vCenter";

        endpointState = test.postServiceSynchronously(EndpointService.FACTORY_LINK,
                endpointState, EndpointState.class);
        return endpointState;
    }

    public static NetworkState createNetwork(BaseModelTest test, EndpointState endpointState)
            throws Throwable {
        NetworkService.NetworkState networkState = new NetworkService.NetworkState();
        networkState.resourcePoolLink = "/resources/networks/fakeNetwork";
        networkState.instanceAdapterReference = new URI(
                "http://localhost/provisioning/vsphere/dvs-network-adapter");
        networkState.endpointLink = endpointState.documentSelfLink;
        networkState.type = "NetworkState";
        networkState.name = "DSwitch-Management";
        networkState.customProperties = new HashMap<>();
        networkState.customProperties.put("__dvsUuid", "");
        networkState.customProperties.put("__moref", "VmwareDistributedVirtualSwitch:dvs-21");
        networkState.customProperties.put("__computeType", "VmwareDistributedVirtualSwitch");
        networkState.regionId = "Datacenter:datacenter-2";

        networkState = test.postServiceSynchronously(NetworkService.FACTORY_LINK,
                networkState, NetworkState.class);
        return networkState;

    }

    public static NetworkInterfaceState createNetworkInterface(BaseModelTest test, String name,
            SubnetState subnetState, NetworkState networkState)
            throws Throwable {
        NetworkInterfaceState nis = new NetworkInterfaceState();
        nis.name = name;
        nis.subnetLink = subnetState.documentSelfLink;
        nis.networkLink = networkState.documentSelfLink;

        NetworkInterfaceState networkInterfaceState = test.postServiceSynchronously(
                NetworkInterfaceService.FACTORY_LINK, nis, NetworkInterfaceState.class);
/*
        List<IPAddressState> ips = createSubnetRangeWithIpAddresses(test, subnetState.documentSelfLink,
                networkInterfaceState.documentSelfLink, 1);

        networkInterfaceState.address = ips.get(0).ipAddress;
        networkInterfaceState.addressLink = ips.get(0).documentSelfLink;
        test.putServiceSynchronously(networkInterfaceState.documentSelfLink, networkInterfaceState);*/

        return networkInterfaceState;
    }

    public static SubnetState createSubnet(BaseModelTest test, NetworkState networkState,
            EndpointState endpointState) throws Throwable {
        SubnetState subnetState = new SubnetState();
        subnetState.documentSelfLink = subnetState.id;
        subnetState.networkLink = networkState.documentSelfLink;
        subnetState.gatewayAddress = "12.12.12.1";
        subnetState.domain = "vmware.com";
        subnetState.dnsServerAddresses = new ArrayList<>();
        subnetState.dnsServerAddresses.add("192.12.12.12");
        subnetState.subnetCIDR = "12.12.12.0/24";
        subnetState.endpointLink = endpointState.documentSelfLink;

        SubnetState returnState = test.postServiceSynchronously(SubnetService.FACTORY_LINK,
                subnetState, SubnetState.class);

        return returnState;
    }

    public static SubnetRangeState createSubnetRange(BaseModelTest test,
            SubnetState subnetState,
            String startIpAddr,
            String endIPAddr) throws Throwable {
        SubnetRangeState subnetRangeState = new SubnetRangeService.SubnetRangeState();
        subnetRangeState.startIPAddress = startIpAddr;
        subnetRangeState.endIPAddress = endIPAddr;
        subnetRangeState.ipVersion = IPVersion.IPv4;
        subnetRangeState.subnetLink = subnetState.documentSelfLink;

        subnetRangeState = test.postServiceSynchronously(SubnetRangeService.FACTORY_LINK,
                subnetRangeState, SubnetRangeState.class);

        return subnetRangeState;
    }

    public static List<IPAddressState> createSubnetRangeWithIpAddresses(BaseModelTest test,
            String subnetLink,
            String connectedResourceLink, int ipAddressCount)
            throws Throwable {

        SubnetRangeState subnetRange = new SubnetRangeService.SubnetRangeState();
        subnetRange.startIPAddress = "12.12.12.2";
        subnetRange.endIPAddress = "12.12.12.120";
        subnetRange.ipVersion = IPVersion.IPv4;
        subnetRange.subnetLink = subnetLink;

        SubnetRangeState subnetRangeState = test
                .postServiceSynchronously(SubnetRangeService.FACTORY_LINK,
                        subnetRange, SubnetRangeState.class);

        List<IPAddressState> returnStates = new ArrayList<>();
        String prefix = "12.12.12.";
        int initialValue = 3;
        for (int i = 0; i < ipAddressCount; i++) {
            IPAddressState ipAddressState = new IPAddressState();
            ipAddressState.ipAddress = prefix + (initialValue + i);
            ipAddressState.ipVersion = IPVersion.IPv4;
            ipAddressState.ipAddressStatus = IPAddressState.IPAddressStatus.ALLOCATED;
            ipAddressState.subnetRangeLink = subnetRangeState.documentSelfLink;
            ipAddressState.connectedResourceLink = connectedResourceLink;

            returnStates.add(test
                    .postServiceSynchronously(IPAddressService.FACTORY_LINK, ipAddressState,
                            IPAddressState.class));
        }

        return returnStates;
    }

    public static IPAddressState createIpAddress(BaseModelTest test,
            String IPAddress,
            IPAddressState.IPAddressStatus ipAddressStatus,
            SubnetRangeState subnetRangeState,
            String connectedResourceLink
    ) throws Throwable {
        IPAddressState ipAddressState = new IPAddressState();
        ipAddressState.ipAddress = IPAddress;
        ipAddressState.ipVersion = IPVersion.IPv4;
        ipAddressState.ipAddressStatus = ipAddressStatus;
        ipAddressState.subnetRangeLink = subnetRangeState.documentSelfLink;
        ipAddressState.connectedResourceLink = connectedResourceLink;

        ipAddressState = test.postServiceSynchronously(IPAddressService.FACTORY_LINK,
                ipAddressState, IPAddressState.class);

        return ipAddressState;

    }

    public static ComputeService.ComputeStateWithDescription createComputeWithDescription(
            BaseModelTest test, String instanceAdapterLink, String bootAdapterLink)
            throws Throwable {
        return ModelUtils.createCompute(test, ModelUtils
                .createComputeDescription(test, instanceAdapterLink,
                        bootAdapterLink));
    }

    public static List<SnapshotService.SnapshotState> createSnapshotsWithHierarchy(
            BaseModelTest test,
            String computeLink)
            throws Throwable {
        SnapshotService.SnapshotState snapshot1 = new SnapshotService.SnapshotState();
        snapshot1.computeLink = computeLink;
        snapshot1.name = "parent";
        snapshot1.parentLink = null;
        snapshot1.isCurrent = false;

        snapshot1 = test.postServiceSynchronously(SnapshotService.FACTORY_LINK, snapshot1,
                SnapshotService.SnapshotState.class);

        SnapshotService.SnapshotState snapshot2 = new SnapshotService.SnapshotState();
        snapshot2.computeLink = computeLink;
        snapshot2.name = "child";
        snapshot2.parentLink = snapshot1.documentSelfLink;
        snapshot2.isCurrent = true;

        snapshot2 = test.postServiceSynchronously(SnapshotService.FACTORY_LINK, snapshot2,
                SnapshotService.SnapshotState.class);

        ComputeService.ComputeState cs = new ComputeService.ComputeStateWithDescription();
        cs.customProperties = new HashMap<>();
        cs.customProperties.put(COMPUTE_CUSTOM_PROPERTY_HAS_SNAPSHOTS,
                "true");

        cs = test.patchServiceSynchronously(computeLink, cs,
                ComputeService.ComputeState.class);

        List<SnapshotService.SnapshotState> sl = new LinkedList<SnapshotService.SnapshotState>();
        sl.add(snapshot1);
        sl.add(snapshot2);

        return sl;
    }

    public static ComputeService.ComputeStateWithDescription createComputeWithDescription(
            BaseModelTest test) throws Throwable {
        return createComputeWithDescription(test, null, null);
    }

    public static ResourcePoolState createResourcePool(BaseModelTest test) throws Throwable {
        return createResourcePool(test, null);
    }

    public static ResourcePoolState createResourcePool(BaseModelTest test, String endpointLink)
            throws Throwable {
        ResourcePoolState poolState = new ResourcePoolState();
        poolState.name = UUID.randomUUID().toString();
        poolState.id = poolState.name;
        poolState.documentSelfLink = poolState.id;
        poolState.maxCpuCount = 1600L;
        poolState.minCpuCount = 16L;
        poolState.minMemoryBytes = 1024L * 1024L * 1024L * 46L;
        poolState.maxMemoryBytes = poolState.minMemoryBytes * 2;
        poolState.minDiskCapacityBytes = poolState.maxDiskCapacityBytes = 1024L * 1024L * 1024L
                * 1024L;
        if (endpointLink != null) {
            poolState.customProperties = new HashMap<>();
            poolState.customProperties.put(
                    ComputeProperties.ENDPOINT_LINK_PROP_NAME, endpointLink);
        }

        return test.postServiceSynchronously(ResourcePoolService.FACTORY_LINK, poolState,
                ResourcePoolState.class);
    }

    public static SecurityGroupState createSecurityGroup(BaseModelTest test,
            String securityGroupName, ComputeState computeHost, EndpointState endpointState)
            throws Throwable {

        SecurityGroupState securityGroupState = new SecurityGroupState();
        securityGroupState.name = securityGroupName;
        securityGroupState.authCredentialsLink = endpointState.authCredentialsLink;
        securityGroupState.tenantLinks = endpointState.tenantLinks;
        securityGroupState.endpointLink = endpointState.documentSelfLink;
        securityGroupState.resourcePoolLink = computeHost.resourcePoolLink;

        securityGroupState.customProperties = new HashMap<>();
        securityGroupState.customProperties.put(ComputeProperties.COMPUTE_HOST_LINK_PROP_NAME,
                computeHost.documentSelfLink);

        Rule ssh = new Rule();
        ssh.name = "ssh";
        ssh.protocol = "tcp";
        ssh.ipRangeCidr = "0.0.0.0/0";
        ssh.ports = "22";
        securityGroupState.ingress = new ArrayList<>();
        securityGroupState.ingress.add(ssh);

        Rule out = new Rule();
        out.name = "out";
        out.protocol = "tcp";
        out.ipRangeCidr = "0.0.0.0/0";
        out.ports = "1-65535";
        securityGroupState.egress = new ArrayList<>();
        securityGroupState.egress.add(out);

        securityGroupState.regionId = "regionId";
        securityGroupState.resourcePoolLink = "/link/to/rp";
        securityGroupState.instanceAdapterReference = new URI(
                "http://instanceAdapterReference");

        return test.postServiceSynchronously(ResourcePoolService.FACTORY_LINK, securityGroupState,
                SecurityGroupState.class);
    }
}
