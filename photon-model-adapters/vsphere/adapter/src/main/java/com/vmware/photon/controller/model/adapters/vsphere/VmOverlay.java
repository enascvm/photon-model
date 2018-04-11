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

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.adapters.vsphere.ProvisionContext.NetworkInterfaceStateWithDetails;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimPath;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.vim25.ArrayOfGuestNicInfo;
import com.vmware.vim25.ArrayOfManagedObjectReference;
import com.vmware.vim25.ArrayOfVirtualDevice;
import com.vmware.vim25.ArrayOfVirtualMachineSnapshotTree;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.ObjectContent;
import com.vmware.vim25.ObjectUpdate;
import com.vmware.vim25.VirtualCdrom;
import com.vmware.vim25.VirtualDevice;
import com.vmware.vim25.VirtualDisk;
import com.vmware.vim25.VirtualEthernetCard;
import com.vmware.vim25.VirtualFloppy;
import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.VirtualMachineSnapshotTree;

/**
 * Type-safe wrapper of a VM represented by a set of fetched properties.
 */
public class VmOverlay extends AbstractOverlay {

    private static final String DATASTORE_MOREF_DELIMITER = ";";

    private static final Comparator<String> IP_COMPARATOR = new Comparator<String>() {
        @Override
        public int compare(String s1, String s2) {
            return Integer.compare(score(s1), score(s2));
        }

        /**
         * Score a dot-decimal string according to https://en.wikipedia.org/wiki/Private_network
         * The "more private" an IP looks the lower score it gets. Classles IPs get highest score.
         *
         * @param s
         * @return
         */
        private int score(String s) {
            int n = Integer.MAX_VALUE;

            if (s.startsWith("10.")) {
                n = 24;
            } else if (s.startsWith("172.")) {
                String octet2 = s.substring(4, s.indexOf('.', 5));
                if (Integer.parseInt(octet2) >= 16) {
                    n = 20;
                }
            } else if (s.startsWith("192.168.")) {
                n = 16;
            }

            return n;
        }
    };

    public VmOverlay(ObjectContent cont) {
        super(cont);
        ensureType(VimNames.TYPE_VM);
    }

    public VmOverlay(ObjectUpdate cont) {
        super(cont);
        ensureType(VimNames.TYPE_VM);
    }

    public VmOverlay(ManagedObjectReference ref, Map<String, Object> props) {
        super(ref, props);
        ensureType(VimNames.TYPE_VM);
    }

    public PowerState getPowerState() {
        return VSphereToPhotonMapping.convertPowerState(
                (VirtualMachinePowerState) getOrFail(VimPath.vm_runtime_powerState));
    }

    public PowerState getPowerStateOrNull() {
        return VSphereToPhotonMapping.convertPowerState(
                (VirtualMachinePowerState) getOrDefault(VimPath.vm_runtime_powerState, null));
    }

    public String getInstanceUuid() {
        return (String) getOrDefault(VimPath.vm_config_instanceUuid, null);
    }

    public String getName() {
        return (String) getOrFail(VimPath.vm_config_name);
    }

    public String getNameOrNull() {
        return (String) getOrDefault(VimPath.vm_config_name, null);
    }

    public ManagedObjectReference getResourcePool() {
        return (ManagedObjectReference) getOrFail(VimPath.res_resourcePool);
    }

    public String getDatastoreMorefsAsString() {
        ArrayOfManagedObjectReference morefs = (ArrayOfManagedObjectReference) getOrDefault(
                VimPath.vm_datastore, null);
        if (morefs != null && morefs.getManagedObjectReference() != null && !morefs
                .getManagedObjectReference().isEmpty()) {

            return String.join(DATASTORE_MOREF_DELIMITER, morefs.getManagedObjectReference()
                    .stream()
                    .map(VimUtils::convertMoRefToString)
                    .collect(Collectors.toList()));
        }
        return null;
    }

    public List<VirtualEthernetCard> getNics() {
        ArrayOfVirtualDevice dev = (ArrayOfVirtualDevice) getOrDefault(
                VimPath.vm_config_hardware_device, null);
        if (dev == null) {
            return Collections.emptyList();
        }

        return dev.getVirtualDevice().stream()
                .filter(d -> d instanceof VirtualEthernetCard)
                .map(d -> (VirtualEthernetCard) d)
                .collect(Collectors.toList());
    }

    public List<VirtualDevice> getDisks() {
        ArrayOfVirtualDevice dev = (ArrayOfVirtualDevice) getOrDefault(
                VimPath.vm_config_hardware_device, null);
        if (dev == null) {
            return Collections.emptyList();
        }

        return dev.getVirtualDevice().stream()
                .filter(d -> d instanceof VirtualDisk || d instanceof VirtualCdrom || d
                        instanceof VirtualFloppy)
                .collect(Collectors.toList());
    }

    public boolean isTemplate() {
        return (boolean) getOrDefault(VimPath.vm_config_template, Boolean.FALSE);
    }

    public long getLastReconfigureMillis() {
        // config.changeVersion should be treated as an opaque string according to documentation
        // however there is no other timestamp on the VM that can be used to filter out machines
        // that may currently be provisioning
        String s = (String) getOrDefault(VimPath.vm_config_changeVersion, null);
        if (s == null) {
            return 0;
        }

        try {
            ZonedDateTime dt = ZonedDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            return dt.toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            return 0;
        }
    }

    public ManagedObjectReference getHost() {
        return (ManagedObjectReference) getOrFail(VimPath.vm_runtime_host);
    }

    public String getPrimaryMac() {
        for (VirtualEthernetCard dev : getNics()) {
            return dev.getMacAddress();
        }

        return null;
    }

    public String getHostName() {
        return (String) getOrDefault(VimPath.vm_guest_hostName, null);
    }

    public List<String> getAllIps() {
        ArrayOfGuestNicInfo arr = (ArrayOfGuestNicInfo) getOrDefault(VimPath.vm_guest_net, null);
        if (arr == null) {
            return Collections.emptyList();
        }
        return arr.getGuestNicInfo()
                .stream()
                .flatMap(gni -> gni.getIpAddress().stream()).collect(Collectors.toList());
    }

    /***
     * Builds a map of external device index and ips or mac addresses and ips
     * @return Map
     */
    public Map<String, List<String>> getMapNic2IpV4Addresses() {
        ArrayOfGuestNicInfo arr = (ArrayOfGuestNicInfo) getOrDefault(VimPath.vm_guest_net, null);
        if (arr == null) {
            return Collections.emptyMap();
        }
        HashMap<String, List<String>> mapNicIpAddresses = new HashMap<>();
        if (arr.getGuestNicInfo() != null) {
            for (int index = 0; index < arr.getGuestNicInfo().size(); index++) {
                List<String> ips = arr.getGuestNicInfo().get(index).getIpAddress().stream()
                        .filter(s -> !s.contains(":")).collect(Collectors.toList());
                mapNicIpAddresses.put(Integer.toString(index), ips);
                String macAddress = arr.getGuestNicInfo().get(index).getMacAddress();
                if (macAddress != null) {
                    mapNicIpAddresses.put(macAddress, ips);
                }
            }
        }
        return mapNicIpAddresses;
    }

    /***
     * Gets the device key for nic. The network device key is the nic external id or the nic mac
     * address
     * @param nic The network interface
     * @return The network device key
     */
    public static String getDeviceKey(NetworkInterfaceStateWithDetails nic) {
        String deviceKey = null;
        if (deviceKey == null) {
            deviceKey = nic.customProperties.get(CustomProperties.NIC_MAC_ADDRESS);
        }
        return deviceKey;
    }

    /**
     * If there is a nic with assignPublicIpAddress use the ip associated with that nic
     * otherwise rely on previous logic where it tries to guess the "public" IP of a VM. IPv6
     * addresses are excluded. It prefer routable
     * addresses, then class A, then class B, then class C. Return null if not candidates.
     *
     * @return
     */
    public String findPublicIpV4Address(List<NetworkInterfaceStateWithDetails> nics) {
        //check if any of the nics is marked as assignPublicIpAddress
        if (nics != null) {
            boolean assignPublicIpAddress = false;
            NetworkInterfaceStateWithDetails networkInterfaceStateWithDetails = nics.stream()
                    .filter(nic -> nic.description != null
                            && nic.description.assignPublicIpAddress != null &&
                            nic.description.assignPublicIpAddress == true)
                    .findFirst().orElse(null);
            if (networkInterfaceStateWithDetails != null) {
                assignPublicIpAddress = true;
            }
            if (assignPublicIpAddress == true) {
                String deviceKey = null;
                deviceKey = getDeviceKey(networkInterfaceStateWithDetails);
                if (deviceKey != null) {
                    Map<String, List<String>> mapNics2IpAddresses = getMapNic2IpV4Addresses();
                    if (mapNics2IpAddresses.containsKey(deviceKey)) {
                        List<String> ips = mapNics2IpAddresses.get(deviceKey);
                        return guessPublicIpV4Address(ips);
                    }
                }
            }
        }
        return guessPublicIpV4Address(getAllIps());
    }

    /***
     * Tries to guess the "public" IP of a VM. IPv6
     * addresses are excluded. It prefer routable
     * addresses, then class A, then class B, then class C. Return null if not candidates.
     * @return
     */
    public String guessPublicIpV4Address() {
        return guessPublicIpV4Address(getAllIps());
    }

    public String guessPublicIpV4Address(Collection<String> ips) {
        Optional<String> ip = ips.stream()
                .filter(s -> !s.contains(":"))
                .max(IP_COMPARATOR);

        return ip.orElse(null);
    }

    public int getNumCpu() {
        return (int) getOrDefault(VimPath.vm_summary_config_numCpu, 0);
    }

    public long getMemoryBytes() {
        return ((int) getOrDefault(VimPath.vm_config_hardware_memoryMB, 0)) * MB_to_bytes;
    }

    public String getGuestId() {
        return (String) getOrDefault(VimPath.vm_config_guestId, null);
    }

    public List<VirtualMachineSnapshotTree> getRootSnapshotList() {
        ArrayOfVirtualMachineSnapshotTree arrayOfVirtualMachineSnapshotTree = (ArrayOfVirtualMachineSnapshotTree) getOrDefault(
                VimPath.vm_snapshot_rootSnapshotList, null);
        if (arrayOfVirtualMachineSnapshotTree != null) {
            return arrayOfVirtualMachineSnapshotTree.getVirtualMachineSnapshotTree();
        }
        return null;
    }

    public String getOS() {
        return (String) getOrDefault(VimPath.vm_summary_config_guestFullName, null);
    }

    public String getOSId() {
        return (String) getOrDefault(VimPath.vm_summary_config_guestId, null);
    }
}
