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

import io.netty.util.internal.StringUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.photon.controller.model.adapters.vsphere.CustomProperties;
import com.vmware.photon.controller.model.adapters.vsphere.QueryConfigTargetRequest;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.vim25.ConfigTarget;
import com.vmware.vim25.DistributedVirtualPortgroupInfo;
import com.vmware.vim25.DistributedVirtualSwitchPortConnection;
import com.vmware.vim25.VirtualDeviceBackingInfo;
import com.vmware.vim25.VirtualEthernetCardDistributedVirtualPortBackingInfo;
import com.vmware.vim25.VirtualEthernetCardNetworkBackingInfo;
import com.vmware.vim25.VirtualEthernetCardOpaqueNetworkBackingInfo;

/**
 * A factory class to return {@link VirtualDeviceBackingInfo} for network connection for a given
 * network card based on the type of information is available.
 */
public class NetworkDeviceBackingFactory {

    private static final Logger logger = LoggerFactory.getLogger(
            NetworkDeviceBackingFactory.class.getName());


    /**
     * Get network device's {@link VirtualDeviceBackingInfo} based on the information available
     * on resource level. The type of custom properties present on a resource decides the type of
     * device backing. Use other overload for querying portgroup information from vsphere.
     *
     * @param resource      A {@link ResourceState} of type {@link SubnetState} or
     *                      {@link NetworkState}
     *
     * @return              {@link VirtualDeviceBackingInfo}
     */
    public static VirtualDeviceBackingInfo getNetworkDeviceBackingInfo(ResourceState resource) {
        return getNetworkDeviceBackingInfo(resource, null);
    }

    /**
     * Get network device's {@link VirtualDeviceBackingInfo} based on the information available
     * on resource level. The type of custom properties present on a resource decides the type of
     * device backing.
     *
     * @param resource      A {@link ResourceState} of type {@link SubnetState} or {@link NetworkState}
     *
     * @param queryConfigTargetRequest  A {@link QueryConfigTargetRequest} object so to get
     *                                  config target when needed
     *
     * @return              {@link VirtualDeviceBackingInfo}
     */
    public static VirtualDeviceBackingInfo getNetworkDeviceBackingInfo(ResourceState resource,
            QueryConfigTargetRequest queryConfigTargetRequest) {

        if (resource == null) {
            return null;
        }

        CustomProperties props = CustomProperties.of(resource);

        if (props.getString(DvsProperties.DVS_UUID) != null ||
                props.getString(DvsProperties.PORT_GROUP_KEY) != null) {
            // an isolated network created by vSphere adapter or nsx adapter will set this property
            // NSX-V can only set PORT_GROUP_KEY as DVS_UUID is not available
            return getDistributedPortBackingInfo(props, queryConfigTargetRequest);
        } else if (props.getString(NsxProperties.OPAQUE_NET_ID) != null) {
            // An opaque network
            // NSX-T will set OpaqueId to subnet's id
            return getOpaqueNetworkBackingInfo(props);
        } else if (props.getString(CustomProperties.TYPE) != null &&
                props.getString(CustomProperties.TYPE).equals(VimNames.TYPE_NETWORK)) {
            // The default case when we want to connect to existing network
            return getStandardNetworkBackingInfo(resource.name);
        } else if (resource instanceof NetworkState) {
            // satisfying an existing code path from InstanceClient.
            // Do we really need it?
            return getStandardNetworkBackingInfo(resource.name);
        }

        return null;
    }

    /**
     * Backing info for distributed virtual switch port or portgroup
     */
    private static VirtualEthernetCardDistributedVirtualPortBackingInfo getDistributedPortBackingInfo(
            CustomProperties props, QueryConfigTargetRequest queryConfigTargetRequest) {

        DistributedVirtualSwitchPortConnection port = new DistributedVirtualSwitchPortConnection();

        String portGroupKey = props.getString(DvsProperties.PORT_GROUP_KEY) ;
        port.setPortgroupKey(portGroupKey);

        String dvsUuid = props.getString(DvsProperties.DVS_UUID);

        if (StringUtil.isNullOrEmpty(dvsUuid)) {
            // NSX-V doesn't have UUID information in its API response
            DistributedVirtualPortgroupInfo info = null;

            try {
                ConfigTarget configTarget = queryConfigTargetRequest.getConfigTarget();
                info = configTarget.getDistributedVirtualPortgroup()
                        .stream()
                        .filter(d -> {
                            return d.getPortgroupKey().equals(portGroupKey);
                        })
                        .findFirst()
                        .orElse(null);
            } catch (Exception e) {
                logger.error("getDistributedPortBackingInfo::Failed to get dvportgroup info.", e);
            }

            if (info == null) {
                throw new IllegalArgumentException("getDistributedPortBackingInfo::The port group "
                       + "information is not found for key: " + portGroupKey);
            }

            dvsUuid = info.getSwitchUuid();
        }

        port.setSwitchUuid(dvsUuid);

        VirtualEthernetCardDistributedVirtualPortBackingInfo backing =
                new VirtualEthernetCardDistributedVirtualPortBackingInfo();
        backing.setPort(port);

        return backing;
    }

    /**
     * Backing info for existing network
     */
    private static VirtualEthernetCardNetworkBackingInfo getStandardNetworkBackingInfo(
            String name) {
        VirtualEthernetCardNetworkBackingInfo backing = new VirtualEthernetCardNetworkBackingInfo();
        backing.setDeviceName(name);
        return backing;
    }

    /**
     * Backing info for an opaque network which is managed by a management plane outside vSphere.
     */
    private static VirtualEthernetCardOpaqueNetworkBackingInfo getOpaqueNetworkBackingInfo(
            CustomProperties props) {

        VirtualEthernetCardOpaqueNetworkBackingInfo backing =
                new VirtualEthernetCardOpaqueNetworkBackingInfo();

        backing.setOpaqueNetworkId(props.getString(NsxProperties.OPAQUE_NET_ID));
        backing.setOpaqueNetworkType(props.getString(NsxProperties.OPAQUE_NET_TYPE));

        return backing;
    }
}
