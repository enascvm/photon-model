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

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.net.util.SubnetUtils;

import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BaseHelper;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.vim25.CustomizationAdapterMapping;
import com.vmware.vim25.CustomizationFaultFaultMsg;
import com.vmware.vim25.CustomizationFixedIp;
import com.vmware.vim25.CustomizationFixedName;
import com.vmware.vim25.CustomizationGlobalIPSettings;
import com.vmware.vim25.CustomizationIPSettings;
import com.vmware.vim25.CustomizationLinuxOptions;
import com.vmware.vim25.CustomizationLinuxPrep;
import com.vmware.vim25.CustomizationSpec;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.xenon.common.UriUtils;

/**
 * Builds and applies CustomizationSpec to apply a static IP address on a VM.
 */
public class CustomizationClient extends BaseHelper {
    private final ComputeStateWithDescription state;
    private final String guestId;

    private static final Pattern HOSTNAME_CHARS = Pattern.compile("[^a-zA-Z0-9-]+");

    public CustomizationClient(Connection connection, ComputeStateWithDescription stateWithDescription,
            String guestId) {
        super(connection);
        this.state = stateWithDescription;
        this.guestId = guestId;
    }

    public ManagedObjectReference customizeGuest(ManagedObjectReference vm, CustomizationSpec spec)
            throws RuntimeFaultFaultMsg, CustomizationFaultFaultMsg {
        return connection.getVimPort().customizeVMTask(vm, spec);
    }

    public void customizeNic(String macAddress, String hostName, String address,
            SubnetState subnetState, CustomizationSpec template) {
        // remove existing mapping
        template.getNicSettingMap().removeIf(x -> Objects.equals(x.getMacAddress(), macAddress));

        CustomizationAdapterMapping mapping = new CustomizationAdapterMapping();
        mapping.setMacAddress(macAddress);
        CustomizationIPSettings adapter = new CustomizationIPSettings();
        mapping.setAdapter(adapter);

        adapter.setSubnetMask(cidr2mask(subnetState.subnetCIDR));
        adapter.getGateway().add(subnetState.gatewayAddress);
        adapter.setDnsDomain(subnetState.domain);
        CustomizationFixedIp ipGen = new CustomizationFixedIp();
        ipGen.setIpAddress(address);
        adapter.setIp(ipGen);

        template.getNicSettingMap().add(mapping);

        if (isLinux()) {
            CustomizationLinuxPrep identity = new CustomizationLinuxPrep();
            template.setIdentity(identity);
            identity.setDomain(subnetState.domain);

            CustomizationFixedName name = new CustomizationFixedName();
            if (hostName == null || hostName.isEmpty()) {
                hostName = makeHostName(UriUtils.getLastPathSegment(this.state.documentSelfLink));
            }
            name.setName(hostName);
            identity.setHostName(name);

            template.setOptions(new CustomizationLinuxOptions());
        }
    }

    private String makeHostName(String link) {
        return HOSTNAME_CHARS.matcher(link).replaceAll("-");
    }

    private String cidr2mask(String subnetCIDR) {
        return new SubnetUtils(subnetCIDR).getInfo().getNetmask();
    }

    public void customizeDns(Set<String> dnsServers, Set<String> dnsSearchDomains,
            CustomizationSpec template) {
        CustomizationGlobalIPSettings settings = new CustomizationGlobalIPSettings();
        if (dnsServers != null) {
            settings.getDnsServerList().addAll(dnsServers);
        }

        if (dnsSearchDomains != null) {
            settings.getDnsSuffixList().addAll(dnsSearchDomains);
        }

        template.setGlobalIPSettings(settings);
    }

    private boolean isLinux() {
        if (this.guestId == null) {
            return true;
        }
        String s = this.guestId.toLowerCase();
        return !s.contains("win") && !s.contains("darwin");
    }
}
