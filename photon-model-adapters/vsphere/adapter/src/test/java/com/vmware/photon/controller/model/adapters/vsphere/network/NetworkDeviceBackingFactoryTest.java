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

package com.vmware.photon.controller.model.adapters.vsphere.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.UUID;

import org.junit.Test;

import com.vmware.photon.controller.model.adapters.vsphere.CustomProperties;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.vim25.DistributedVirtualSwitchPortConnection;
import com.vmware.vim25.VirtualDeviceBackingInfo;
import com.vmware.vim25.VirtualEthernetCardDistributedVirtualPortBackingInfo;
import com.vmware.vim25.VirtualEthernetCardNetworkBackingInfo;
import com.vmware.vim25.VirtualEthernetCardOpaqueNetworkBackingInfo;

public class NetworkDeviceBackingFactoryTest {

    private SubnetState subnet;
    private NetworkState network;

    @Test
    public void testGetNetworkBackingWithNullResource() {
        assertNull(NetworkDeviceBackingFactory.getNetworkDeviceBackingInfo(null));
    }

    @Test
    public void testUnknownNetworkTypeBackingInfo() {
        this.subnet = new SubnetState();
        this.subnet.customProperties = new HashMap<>();
        assertNull(NetworkDeviceBackingFactory.getNetworkDeviceBackingInfo(null));
    }

    @Test
    public void testGetDvsPortBackingInfoWithUUID() {
        this.subnet = new SubnetState();
        this.subnet.customProperties = new HashMap<>();
        this.subnet.customProperties.put(DvsProperties.DVS_UUID, UUID.randomUUID().toString());
        this.subnet.customProperties.put(DvsProperties.PORT_GROUP_KEY, UUID.randomUUID().toString());

        VirtualDeviceBackingInfo deviceBackingInfo = NetworkDeviceBackingFactory
                .getNetworkDeviceBackingInfo(this.subnet);

        assertTrue(deviceBackingInfo instanceof
                VirtualEthernetCardDistributedVirtualPortBackingInfo);

        VirtualEthernetCardDistributedVirtualPortBackingInfo distributedVirtualPortBackingInfo =
                (VirtualEthernetCardDistributedVirtualPortBackingInfo) deviceBackingInfo;

        DistributedVirtualSwitchPortConnection port = distributedVirtualPortBackingInfo.getPort();

        assertNotNull(port);

        assertEquals(this.subnet.customProperties.get(DvsProperties.DVS_UUID),
                port.getSwitchUuid());
        assertEquals(this.subnet.customProperties.get(DvsProperties.PORT_GROUP_KEY),
                port.getPortgroupKey());
    }

    /**
     * NSX-V test case when only port group key is present
     *
     * Exception as
     */
    @Test(expected = IllegalArgumentException.class)
    public void testGetDvsPortBackingInfoWithPortGroupKey() {
        this.subnet = new SubnetState();
        this.subnet.customProperties = new HashMap<>();
        this.subnet.customProperties.put(DvsProperties.PORT_GROUP_KEY, UUID.randomUUID().toString());

        // will throw NPE here as the querytargetrequest is null
        VirtualDeviceBackingInfo deviceBackingInfo = NetworkDeviceBackingFactory
                .getNetworkDeviceBackingInfo(this.subnet);

        assertTrue(deviceBackingInfo instanceof
                VirtualEthernetCardDistributedVirtualPortBackingInfo);

        VirtualEthernetCardDistributedVirtualPortBackingInfo distributedVirtualPortBackingInfo =
                (VirtualEthernetCardDistributedVirtualPortBackingInfo) deviceBackingInfo;

        DistributedVirtualSwitchPortConnection port = distributedVirtualPortBackingInfo.getPort();

        assertNotNull(port);

        // TODO: mock get DVS switch call to vSphere and match the result
        // for now we are just using the expected exception as without UUID the backing info
        // cannot be set.
        assertNull(port.getSwitchUuid());

        assertEquals(this.subnet.customProperties.get(DvsProperties.PORT_GROUP_KEY),
                port.getPortgroupKey());
    }

    @Test
    public void testGetOpaqueNetworkBackingInfo() {
        this.subnet = new SubnetState();
        this.subnet.customProperties = new HashMap<>();
        this.subnet.customProperties.put(NsxProperties.OPAQUE_NET_ID, UUID.randomUUID().toString());
        this.subnet.customProperties.put(NsxProperties.OPAQUE_NET_TYPE, UUID.randomUUID().toString());

        VirtualDeviceBackingInfo deviceBackingInfo = NetworkDeviceBackingFactory
                .getNetworkDeviceBackingInfo(this.subnet);

        assertTrue(deviceBackingInfo instanceof VirtualEthernetCardOpaqueNetworkBackingInfo);

        VirtualEthernetCardOpaqueNetworkBackingInfo opaqueNetworkBackingInfo =
                (VirtualEthernetCardOpaqueNetworkBackingInfo) deviceBackingInfo;

        assertEquals(this.subnet.customProperties.get(NsxProperties.OPAQUE_NET_ID),
                opaqueNetworkBackingInfo.getOpaqueNetworkId());
        assertEquals(this.subnet.customProperties.get(NsxProperties.OPAQUE_NET_TYPE),
                opaqueNetworkBackingInfo.getOpaqueNetworkType());
    }

    @Test
    public void testStandardNetworkBackingInfo() {
        this.subnet = new SubnetState();
        this.subnet.name = UUID.randomUUID().toString();
        this.subnet.customProperties = new HashMap<>();
        this.subnet.customProperties.put(CustomProperties.TYPE, VimNames.TYPE_NETWORK);

        VirtualDeviceBackingInfo deviceBackingInfo = NetworkDeviceBackingFactory
                .getNetworkDeviceBackingInfo(this.subnet);

        assertTrue(deviceBackingInfo instanceof VirtualEthernetCardNetworkBackingInfo);

        VirtualEthernetCardNetworkBackingInfo virtualEthernetCardNetworkBackingInfo =
                (VirtualEthernetCardNetworkBackingInfo) deviceBackingInfo;

        assertEquals(this.subnet.name, virtualEthernetCardNetworkBackingInfo.getDeviceName());
    }

    @Test
    public void testNetworkStateBackingInfo() {
        this.network = new NetworkState();
        this.network.name = UUID.randomUUID().toString();
        this.network.customProperties = new HashMap<>();

        VirtualDeviceBackingInfo deviceBackingInfo = NetworkDeviceBackingFactory
                .getNetworkDeviceBackingInfo(this.network);

        assertTrue(deviceBackingInfo instanceof VirtualEthernetCardNetworkBackingInfo);

        VirtualEthernetCardNetworkBackingInfo virtualEthernetCardNetworkBackingInfo =
                (VirtualEthernetCardNetworkBackingInfo) deviceBackingInfo;

        assertEquals(this.network.name, virtualEthernetCardNetworkBackingInfo.getDeviceName());
    }
}
