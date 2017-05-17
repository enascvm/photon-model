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

package com.vmware.photon.controller.model.resources.util;

import static java.util.Collections.singletonMap;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceStateWithDescription;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ReflectionUtils;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class PhotonModelUtils {

    /**
     * The set of ResourceStates which support {@code endpointLink} property through
     * <b>explicit</b>field.
     */
    public static final Set<Class<? extends ResourceState>> ENDPOINT_LINK_EXPLICIT_SUPPORT;

    static {
        Set<Class<? extends ResourceState>> set = new HashSet<>();
        set.add(ComputeDescription.class);
        set.add(ComputeState.class);
        set.add(ComputeStateWithDescription.class);
        set.add(DiskState.class);
        set.add(ImageState.class);
        set.add(NetworkInterfaceDescription.class);
        set.add(NetworkInterfaceState.class);
        set.add(NetworkInterfaceStateWithDescription.class);
        set.add(NetworkState.class);
        set.add(SecurityGroupState.class);
        set.add(StorageDescription.class);
        set.add(SubnetState.class);

        ENDPOINT_LINK_EXPLICIT_SUPPORT = Collections.unmodifiableSet(set);
    }

    /**
     * The set of ServiceDocuments which support {@code endpointLink} property through <b>custom
     * property</b>.
     */
    public static final Set<Class<? extends ServiceDocument>> ENDPOINT_LINK_CUSTOM_PROP_SUPPORT;

    static {
        Set<Class<? extends ServiceDocument>> set = new HashSet<>();
        set.add(AuthCredentialsServiceState.class);
        set.add(ResourceGroupState.class);

        ENDPOINT_LINK_CUSTOM_PROP_SUPPORT = Collections.unmodifiableSet(set);
    }

    public static <T extends ServiceDocument> T setEndpointLink(T state, String endpointLink) {

        if (state == null) {
            return state;
        }

        if (ENDPOINT_LINK_EXPLICIT_SUPPORT.contains(state.getClass())) {

            ServiceDocumentDescription sdDesc = ServiceDocumentDescription.Builder.create()
                    .buildDescription(state.getClass());

            ReflectionUtils.setPropertyValue(
                    sdDesc.propertyDescriptions.get(PhotonModelConstants.FIELD_NAME_ENDPOINT_LINK),
                    state,
                    endpointLink);

        } else if (ENDPOINT_LINK_CUSTOM_PROP_SUPPORT.contains(state.getClass())) {

            ServiceDocumentDescription sdDesc = ServiceDocumentDescription.Builder.create()
                    .buildDescription(state.getClass());

            if (endpointLink != null && !endpointLink.isEmpty()) {
                ReflectionUtils.setOrUpdatePropertyValue(
                        sdDesc.propertyDescriptions.get(ResourceState.FIELD_NAME_CUSTOM_PROPERTIES),
                        state,
                        singletonMap(PhotonModelConstants.CUSTOM_PROP_ENDPOINT_LINK, endpointLink));
            }
        }

        return state;
    }

    public static void handleIdempotentPut(StatefulService s, Operation put) {

        if (put.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_POST_TO_PUT)) {
            // converted PUT due to IDEMPOTENT_POST option
            s.logFine(() -> String.format("Task %s has already started. Ignoring converted PUT.",
                    put.getUri()));
            put.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
            put.complete();
            return;
        }

        // normal PUT is not supported
        put.fail(Operation.STATUS_CODE_BAD_METHOD);
    }

    public static void validateRegionId(ResourceState resourceState) {
        if (resourceState.regionId == null) {
            throw (new IllegalArgumentException("regionId is required"));
        }
    }
}
