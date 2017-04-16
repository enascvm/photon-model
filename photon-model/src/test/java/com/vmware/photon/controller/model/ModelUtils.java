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
import java.util.UUID;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionServiceTest;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule;
import com.vmware.xenon.common.UriUtils;

/**
 * Utility class to create service documents for tests.
 */
public class ModelUtils {
    private static final String TEST_DESC_PROPERTY_NAME = "testDescProperty";
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
        cs.networkInterfaceLinks.add(createNetworkInterface(test, cs.name).documentSelfLink);
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

    public static NetworkInterfaceState createNetworkInterface(BaseModelTest test, String name)
            throws Throwable {
        NetworkInterfaceState nis = new NetworkInterfaceState();
        nis.id = UUID.randomUUID().toString();
        nis.name = name;
        nis.documentSelfLink = nis.id;
        nis.address = "10.0.0.0";
        nis.subnetLink = "/resources/subnet/subnet9";

        NetworkInterfaceState returnState = test.postServiceSynchronously(
                NetworkInterfaceService.FACTORY_LINK, nis, NetworkInterfaceState.class);
        return returnState;
    }

    public static ComputeService.ComputeStateWithDescription createComputeWithDescription(
            BaseModelTest test, String instanceAdapterLink, String bootAdapterLink)
            throws Throwable {
        return ModelUtils.createCompute(test, ModelUtils
                .createComputeDescription(test, instanceAdapterLink,
                        bootAdapterLink));
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
