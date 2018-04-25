/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;

import com.vmware.photon.controller.model.adapters.vsphere.ProvisionContext.NetworkInterfaceStateWithDetails;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.util.PhotonModelUriUtils;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;

public class VSphereAdapterInstanceUpdateIpHelper {

    public static DeferredResult<Operation> updateIPForCompute(ServiceHost host, String computeLink, String ip) {
        ComputeState state = new ComputeState();
        state.address = ip;
        // update compute

        Operation updateIpAddressOp = Operation
                .createPatch(PhotonModelUriUtils.createInventoryUri(host, computeLink))
                .setBody(state)
                .setReferer(host.getPublicUri());
        return host.sendWithDeferredResult(updateIpAddressOp);
    }

    public static void updateIPs(OperationContext operationContext, Operation taskFinisher,
            ServiceHost
            host, String
            computeLink,
            String ip, Map<String, List<String>> ipV4Addresses,
            List<NetworkInterfaceStateWithDetails> nics) {
        updateIPs(operationContext, taskFinisher, host, computeLink, ip, ipV4Addresses, nics, "");
    }

    public static void updateIPs(OperationContext operationContext, Operation taskFinisher,
            ServiceHost host,
            String
            computeLink,
            String ip, Map<String, List<String>> ipV4Addresses,
            List<NetworkInterfaceStateWithDetails> nics, String prefixMsg) {

        List<String> ips = ipV4Addresses.values().stream().distinct()
                .flatMap(List::stream).collect(Collectors.toList());
        OperationContext.restoreOperationContext(operationContext);

        final Map<String, List<String>> ipMaps = ipV4Addresses;
        updateIPForCompute(host, computeLink, ip)
                .thenApply(c -> {
                    host.log(Level.INFO, prefixMsg + "Update compute IP [%s] for computeLink "
                                    + "[%s] ", ip, computeLink);
                    updateIPForNics(taskFinisher, host, ipMaps, nics,
                            () -> {
                                return prefixMsg + String.format("Update networkInterfaces IP "
                                                + "[%s] for computeLink [%s]", ips, computeLink);
                            },
                            () -> {
                                return prefixMsg + String.format("Error updating networkInterfaces "
                                                + "IP [%s] for computeLink [%s]: ", ips, computeLink);
                            }, null);
                    return null;
                })
                .exceptionally(e -> {
                    if (e != null) {
                        host.log(Level.SEVERE, prefixMsg + String.format("Error updating compute "
                                        + "IP [%s] for "
                                        + "computeLink [%s]: %s",
                                ip,
                                computeLink,
                                e));
                    }
                    // finish task
                    taskFinisher.setReferer(host.getPublicUri());
                    taskFinisher.sendWith(host);
                    return null;
                });
    }

    public static void updateIPForNics(ServiceHost host, Map<String, List<String>> ipMaps,
            List<NetworkInterfaceStateWithDetails> nics, Supplier<String> success,
            Supplier<String> failure, Consumer<Void> callback) {
        updateIPForNics(null, host, ipMaps, nics, success, failure, callback);
    }

    private static void updateIPForNics(Operation taskFinisher, ServiceHost host, Map<String,
            List<String>> ipMaps,
            List<NetworkInterfaceStateWithDetails> nics, Supplier<String> success,
            Supplier<String> failure, Consumer<Void> callback) {

        List<Operation> updateIpAddressOperations = createUpdateIPOperationsForNics(host,  ipMaps, nics);
        if (CollectionUtils.isNotEmpty(updateIpAddressOperations) ) {
            OperationJoin.create(updateIpAddressOperations)
                    .setCompletion((o, exs) -> {
                        if (exs != null && !exs.isEmpty()) {
                            String failureMsg = failure.get();
                            host.log(Level.SEVERE, failureMsg + Utils.toString(exs));
                        } else {
                            String successMsg = success.get();
                            host.log(Level.INFO, successMsg);
                        }
                        if (callback != null) {
                            callback.accept(null);
                        }
                        // Finish task
                        if (taskFinisher != null) {
                            taskFinisher.setReferer(host.getPublicUri());
                            taskFinisher.sendWith(host);
                        }
                    })
                    .sendWith(host);
        } else {
            if (taskFinisher != null) {
                // Finish task
                taskFinisher.setReferer(host.getPublicUri());
                taskFinisher.sendWith(host);
            }
        }
    }

    private static List<Operation> createUpdateIPOperationsForNics(ServiceHost host, Map<String,
            List<String>> ipV4Addresses, List<NetworkInterfaceStateWithDetails> nics) {
        List<Operation> updateIpAddressOperations = new ArrayList<>();
        if (ipV4Addresses != null) {
            int sizeIpV4Addresses = ipV4Addresses.size();
            for (NetworkInterfaceStateWithDetails nic : nics) {
                String deviceKey = null;
                deviceKey = VmOverlay.getDeviceKey(nic);
                if (deviceKey == null && nic.deviceIndex < sizeIpV4Addresses) {
                    deviceKey = Integer.toString(nic.deviceIndex);
                }
                if (deviceKey != null) {
                    List<String> ipsV4 = ipV4Addresses.containsKey(deviceKey) ? ipV4Addresses.get(deviceKey) : Collections
                            .emptyList();
                    if (ipsV4.size() > 0) {
                        NetworkInterfaceState patchNic = new NetworkInterfaceState();
                        // if nic has multiple ip addresses for ipv4 only pick 1st ip address
                        patchNic.address = ipsV4.get(0);
                        Operation updateAddressNetWorkInterface = Operation
                                .createPatch(PhotonModelUriUtils.createInventoryUri(host,
                                        nic.documentSelfLink))
                                .setReferer(host.getPublicUri())
                                .setBody(patchNic);
                        updateIpAddressOperations.add(updateAddressNetWorkInterface);
                    } else {
                        host.log(Level.WARNING, "Address is not going to be updated "
                                + "in network interface state: [%s], deviceKey: [%s] was not "
                                + "found in ipV4Addresses: [%s]", nic.documentSelfLink,
                                deviceKey, ipV4Addresses.keySet());

                    }
                } else {
                    host.log(Level.WARNING, "Address is not going to be updated in network interface state: [%s] deviceKey is null", nic.documentSelfLink);
                }
            }
        }
        return updateIpAddressOperations;
    }
}
