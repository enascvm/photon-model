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

package com.vmware.photon.controller.model.adapters.util.instance;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext;
import com.vmware.photon.controller.model.resources.FirewallService.FirewallState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceStateWithDescription;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;

/**
 * Base context class for adapters that handle {@link ComputeInstanceRequest} request. It
 * {@link #populateNicContext() loads} NIC related states:
 * <ul>
 * <li>{@link NetworkInterfaceStateWithDescription nicStateWithDesc}</li>
 * <li>{@link NetworkState networkState}</li>
 * <li>{@link SubnetState subnetState}</li>
 * <li>map of {@link FirewallState firewallStates}</li>
 * </ul>
 * in addition to the states loaded by {@link BaseAdapterContext}.
 */
public class BaseComputeInstanceContext<T extends BaseComputeInstanceContext<T, S>, S extends BaseComputeInstanceContext.BaseNicContext>
        extends BaseAdapterContext<T> {

    /**
     * The class encapsulates NIC related states used during compute provisioning.
     */
    public static class BaseNicContext {

        // NIC related states (resolved by links) related to the ComputeState that is provisioned.
        public NetworkInterfaceStateWithDescription nicStateWithDesc;
        public NetworkState networkState;
        public SubnetState subnetState;

        public Map<String, FirewallState> firewallStates = new HashMap<>();
    }

    /**
     * Holds allocation data for all VM NICs.
     */
    public List<S> nics = new ArrayList<>();

    /**
     * The {@link ComputeInstanceRequest request} that is being processed.
     */
    public final ComputeInstanceRequest computeRequest;

    protected final Supplier<S> nicContextSupplier;

    public BaseComputeInstanceContext(Service service,
            ComputeInstanceRequest computeRequest,
            Supplier<S> nicContextSupplier) {

        this(service, computeRequest.resourceReference, computeRequest, nicContextSupplier);
    }

    public BaseComputeInstanceContext(Service service,
            URI resourceReference,
            ComputeInstanceRequest computeRequest,
            Supplier<S> nicContextSupplier) {

        super(service, resourceReference);

        this.computeRequest = computeRequest;
        this.nicContextSupplier = nicContextSupplier;
    }

    /**
     * First NIC is considered primary.
     */
    public S getPrimaryNic() {
        return this.nics.get(0);
    }

    public DeferredResult<T> populateNicContext() {
        return DeferredResult.completed(self())
                .thenCompose(this::getNicStates)
                .thenCompose(this::getNicSubnetStates)
                .thenCompose(this::getNicNetworkStates)
                .thenCompose(this::getNicFirewallStates);
    }

    /**
     * Get NIC states assigned to the compute state we are provisioning.
     */
    private DeferredResult<T> getNicStates(T context) {
        if (context.child.networkInterfaceLinks == null
                || context.child.networkInterfaceLinks.isEmpty()) {
            return DeferredResult.completed(context);
        }

        List<DeferredResult<Void>> getStatesDR = context.child.networkInterfaceLinks.stream()
                .map(nicStateLink -> {
                    S nicContext = context.nicContextSupplier.get();

                    context.nics.add(nicContext);

                    URI nicStateUri = NetworkInterfaceStateWithDescription
                            .buildUri(UriUtils.buildUri(context.service.getHost(), nicStateLink));

                    Operation op = Operation.createGet(nicStateUri);

                    return context.service
                            .sendWithDeferredResult(op, NetworkInterfaceStateWithDescription.class)
                            .thenAccept(nsWithDesc -> nicContext.nicStateWithDesc = nsWithDesc);
                })
                .collect(Collectors.toList());

        return DeferredResult.allOf(getStatesDR).handle((all, exc) -> {
            if (exc != null) {
                throw new IllegalStateException("Error getting NIC states.", exc);
            }
            return context;
        });
    }

    /**
     * Get {@link SubnetState}s the NICs are assigned to.
     */
    private DeferredResult<T> getNicSubnetStates(T context) {
        if (context.nics.isEmpty()) {
            return DeferredResult.completed(context);
        }

        List<DeferredResult<Void>> getStatesDR = context.nics.stream()
                .map(nicContext -> {
                    Operation op = Operation.createGet(
                            context.service.getHost(),
                            nicContext.nicStateWithDesc.subnetLink);

                    return context.service
                            .sendWithDeferredResult(op, SubnetState.class)
                            .thenAccept(subnetState -> nicContext.subnetState = subnetState);
                })
                .collect(Collectors.toList());

        return DeferredResult.allOf(getStatesDR).handle((all, exc) -> {
            if (exc != null) {
                throw new IllegalStateException("Error getting NIC subnet states.", exc);
            }
            return context;
        });
    }

    /**
     * Get {@link NetworkState}s containing the {@link SubnetState}s the NICs are assigned to.
     *
     * @see #getNicSubnetStates(BaseComputeInstanceContext)
     */
    protected DeferredResult<T> getNicNetworkStates(T context) {
        if (context.nics.isEmpty()) {
            return DeferredResult.completed(context);
        }

        List<DeferredResult<Void>> getStatesDR = context.nics.stream()
                .map(nicContext -> {
                    Operation op = Operation.createGet(
                            context.service.getHost(),
                            nicContext.subnetState.networkLink);

                    return context.service
                            .sendWithDeferredResult(op, NetworkState.class)
                            .thenAccept(networkState -> nicContext.networkState = networkState);
                })
                .collect(Collectors.toList());

        return DeferredResult.allOf(getStatesDR).handle((all, exc) -> {
            if (exc != null) {
                throw new IllegalStateException("Error getting NIC network states.", exc);
            }
            return context;
        });
    }

    protected DeferredResult<T> getNicFirewallStates(T context) {

        List<String> firewallLinks = context.getPrimaryNic().nicStateWithDesc.firewallLinks;

        if (context.nics.isEmpty() || firewallLinks == null || firewallLinks.isEmpty()) {
            return DeferredResult.completed(context);
        }

        List<DeferredResult<Void>> getStatesDR = firewallLinks.stream()
                .map(firewallLink -> {
                    Operation op = Operation.createGet(context.service.getHost(), firewallLink);
                    return context.service
                            .sendWithDeferredResult(op, FirewallState.class)
                            .thenAccept(firewallState -> {
                                // Populate all NICs with same Firewall state.
                                for (BaseNicContext nicCtx : context.nics) {
                                    nicCtx.firewallStates.put(firewallState.name, firewallState);
                                }
                            });
                })
                .collect(Collectors.toList());

        return DeferredResult.allOf(getStatesDR).handle((all, exc) -> {
            if (exc != null) {
                throw new IllegalStateException("Error getting NIC firewall states.", exc);
            }
            return context;
        });
    }

}
