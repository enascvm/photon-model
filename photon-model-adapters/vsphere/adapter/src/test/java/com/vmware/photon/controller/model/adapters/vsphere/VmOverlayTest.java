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

package com.vmware.photon.controller.model.adapters.vsphere;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.NIC_EXTERNAL_ID;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.NIC_MAC_ADDRESS;
import static com.vmware.photon.controller.model.adapters.vsphere.util.VimPath.vm_guest_net;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.adapters.vsphere.ProvisionContext.NetworkInterfaceStateWithDetails;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.vim25.ArrayOfGuestNicInfo;
import com.vmware.vim25.GuestNicInfo;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.ObjectContent;

public class VmOverlayTest{
    private VmOverlay overlay;

    @Before
    public void setup() {
        ObjectContent cont = new ObjectContent();
        ManagedObjectReference ref = new ManagedObjectReference();
        ref.setType(VimNames.TYPE_VM);
        ref.setValue("vm-123");
        cont.setObj(ref);

        Map<String, Object> props = new HashMap<>();
        ArrayOfGuestNicInfo arrayOfGuestNicInfo = new ArrayOfGuestNicInfo();
        List<GuestNicInfo> listGuestNicInfo = arrayOfGuestNicInfo.getGuestNicInfo();
        GuestNicInfo nic1 = new GuestNicInfo();
        List<String> ipsNic1 = nic1.getIpAddress();
        String mac1Address = "00:50:56:8b:54:bd";
        String mac2Address = "98:87:fd:9e:ed:6d";
        nic1.setMacAddress(mac1Address);
        ipsNic1.add("192.168.1.10");
        ipsNic1.add("192.168.1.11");
        GuestNicInfo nic2 = new GuestNicInfo();
        List<String> ipsNic2 = nic2.getIpAddress();
        nic2.setMacAddress(mac2Address);
        ipsNic2.add("10.10.10.20");
        listGuestNicInfo.add(nic1);
        listGuestNicInfo.add(nic2);
        props.put(vm_guest_net, arrayOfGuestNicInfo);

        this.overlay = new VmOverlay(ref, props);
    }

    @Test
    public void guessPublicIpPreferPublic() {
        List<String> ips = Arrays.asList(
                "23.123.1.24",
                "172.17.0.1",
                "10.23.11.222");

        String p = this.overlay.guessPublicIpV4Address(ips);
        assertEquals("23.123.1.24", p);
    }

    @Test
    public void guessPublicIpPreferPublic2() {
        List<String> ips = Arrays.asList(
                "192.169.1.1",
                "172.17.0.1",
                "10.23.11.222");

        String p = this.overlay.guessPublicIpV4Address(ips);
        assertEquals("192.169.1.1", p);
    }

    @Test
    public void guessPublicIpPreferPublic3() {
        List<String> ips = Arrays.asList(
                "172.11.4.5",
                "172.17.0.1",
                "10.23.11.222");

        String p = this.overlay.guessPublicIpV4Address(ips);
        assertEquals("172.11.4.5", p);
    }

    @Test
    public void guessPublicIpNullIfNoCandidates() {
        List<String> ips = Arrays.asList();

        String p = this.overlay.guessPublicIpV4Address(ips);
        assertNull(p);
    }

    @Test
    public void guessPublicIpPreferClassA() {
        List<String> ips = Arrays.asList(
                "172.17.0.1",
                "172.17.0.2",
                "172.17.0.3",
                "10.23.11.222");

        String p = this.overlay.guessPublicIpV4Address(ips);
        assertEquals("10.23.11.222", p);
    }

    @Test
    public void guessPublicIpResortToPrivate() {
        List<String> ips = Arrays.asList(
                "172.17.0.1");

        String p = this.overlay.guessPublicIpV4Address(ips);
        assertEquals("172.17.0.1", p);
    }

    @Test
    public void guessPublicIpResortToPrivateBecauseOnlyIpv6Present() {
        List<String> ips = Arrays.asList(
                "fe80::42:78ff:fea3:5bc6",
                "172.17.0.1");

        String p = this.overlay.guessPublicIpV4Address(ips);
        assertEquals("172.17.0.1", p);
    }

    @Test
    public void findPublicIpAddress() {
        NetworkInterfaceStateWithDetails n1 = new NetworkInterfaceStateWithDetails();
        NetworkInterfaceStateWithDetails n2 = new NetworkInterfaceStateWithDetails();
        List<NetworkInterfaceStateWithDetails> nics = new ArrayList();
        nics.add(n1);
        nics.add(n2);

        String publicIpAddress = this.overlay.findPublicIpV4Address(nics);
        Assert.assertTrue("publicIpAddress is null", publicIpAddress != null);
    }

    @Test
    public void findPublicIpAddressWithMacAddresses() {

        NetworkInterfaceStateWithDetails n1 = new NetworkInterfaceStateWithDetails();
        NetworkInterfaceStateWithDetails n2 = new NetworkInterfaceStateWithDetails();
        String mac1Address = "00:50:56:8b:54:bd";
        String mac2Address = "98:87:fd:9e:ed:6d";

        List<NetworkInterfaceStateWithDetails> nics = new ArrayList();
        nics.add(n1);
        n1.customProperties = new HashMap<>();
        n1.customProperties.put(NIC_MAC_ADDRESS, mac1Address);
        nics.add(n2);
        n2.customProperties = new HashMap<>();
        n2.customProperties.put(NIC_MAC_ADDRESS, mac2Address);

        String publicIpAddress = this.overlay.findPublicIpV4Address(nics);
        Assert.assertTrue("publicIpAddress is null", publicIpAddress != null);

        // test with assignPublicIpAddress
        n1.description = new NetworkInterfaceDescription();
        n1.description.assignPublicIpAddress = true;

        publicIpAddress = this.overlay.findPublicIpV4Address(nics);
        Assert.assertTrue("publicIpAddress is null", publicIpAddress != null);
        Assert.assertTrue("publicIpAddress is null", publicIpAddress.equals("192.168.1.10"));
    }

    @Test
    public void findPublicIpAddressWithNicExternalId() {

        NetworkInterfaceStateWithDetails n1 = new NetworkInterfaceStateWithDetails();
        NetworkInterfaceStateWithDetails n2 = new NetworkInterfaceStateWithDetails();
        String mac1Address = "00:50:56:8b:54:bd";
        String mac2Address = "98:87:fd:9e:ed:6d";

        List<NetworkInterfaceStateWithDetails> nics = new ArrayList();
        nics.add(n1);
        n1.customProperties = new HashMap<>();
        n1.customProperties.put(NIC_EXTERNAL_ID, Integer.toString(0));
        nics.add(n2);
        n2.customProperties = new HashMap<>();
        n2.customProperties.put(NIC_EXTERNAL_ID, Integer.toString(0));

        String publicIpAddress = this.overlay.findPublicIpV4Address(nics);
        Assert.assertTrue("publicIpAddress is null", publicIpAddress != null);

        // test with assignPublicIpAddress
        n1.description = new NetworkInterfaceDescription();
        n1.description.assignPublicIpAddress = true;

        publicIpAddress = this.overlay.findPublicIpV4Address(nics);
        Assert.assertTrue("publicIpAddress is null", publicIpAddress != null);
    }


    @Test
    public void getMapNic2IpV4Addresses() {
        String mac1Address = "00:50:56:8b:54:bd";
        String mac2Address = "98:87:fd:9e:ed:6d";
        Map<String, List<String>> mapNics = this.overlay.getMapNic2IpV4Addresses();
        Assert.assertTrue("mapNic2IpV4Addresses size is different from 4",mapNics.size() == 4);
        List<String> nic1Ips = mapNics.get(Integer.toString(0));
        List<String> nic2Ips = mapNics.get(Integer.toString(1));
        Assert.assertTrue("nic ips size is different from 2", nic1Ips.size() == 2);
        Assert.assertTrue("nic ips size is different from 1", nic2Ips.size() == 1);
        Assert.assertTrue("ip is different from 192.168.1.10", nic1Ips.get(0).equals("192.168.1.10"));
        Assert.assertTrue("ip is different from 192.168.1.11", nic1Ips.get(1).equals("192.168.1.11"));
        Assert.assertTrue("ip is different from 10.10.10.20", nic2Ips.get(0).equals("10.10.10.20"));

        List<String> nic1IpsByMacAddress = mapNics.get(mac1Address);
        List<String> nic2IpsByMacAddress = mapNics.get(mac2Address);
        Assert.assertTrue("nic ips by mac address size is different from 2", nic1IpsByMacAddress
                .size
                () == 2);
        Assert.assertTrue("nic ips by mac address size is different from 1", nic2IpsByMacAddress
                .size() == 1);
        Assert.assertTrue("ip is different from 192.168.1.10",
                nic1IpsByMacAddress.get(0).equals("192.168.1.10"));
        Assert.assertTrue("ip is different from 192.168.1.11", nic1IpsByMacAddress.get(1).equals("192.168.1.11"));
        Assert.assertTrue("ip is different from 10.10.10.20", nic2IpsByMacAddress.get(0).equals("10.10.10.20"));
    }

}