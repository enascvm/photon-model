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

package com.vmware.photon.controller.model.resources;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import org.apache.commons.validator.routines.InetAddressValidator;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.constants.ReleaseConstants;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Represents a compute resource.
 */
public class ComputeService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES + "/compute";

    /**
     * Power State.
     */
    public enum PowerState {
        ON,
        OFF,
        UNKNOWN,
        SUSPEND
    }

    /**
     * Resource Status.
     */
    public enum LifecycleState {
        PROVISIONING,
        READY,
        SUSPEND,
        STOPPED,
        RETIRED
    }

    /**
     * Power Transition.
     */
    public enum PowerTransition {
        SOFT,
        HARD
    }

    /**
     * Boot Device.
     */
    public enum BootDevice {
        CDROM,
        DISK,
        NETWORK
    }

    /**
     * Compute State document.
     */
    public static class ComputeState extends ResourceState {
        public static final String FIELD_NAME_DESCRIPTION_LINK = "descriptionLink";
        public static final String FIELD_NAME_RESOURCE_POOL_LINK = "resourcePoolLink";
        public static final String FIELD_NAME_ADDRESS = "address";
        public static final String FIELD_NAME_PRIMARY_MAC = "primaryMAC";
        public static final String FIELD_NAME_POWER_STATE = "powerState";
        public static final String FIELD_NAME_CUSTOM_PROPERTIES = "customProperties";
        public static final String FIELD_NAME_PARENT_LINK = "parentLink";
        public static final String FIELD_NAME_LIFECYCLE_STATE = "lifecycleState";
        public static final String FIELD_NAME_NETWORK_LINKS = "networkInterfaceLinks";
        public static final String FIELD_NAME_DISK_LINKS = "diskLinks";

        /**
         * URI reference to corresponding ComputeDescription.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String descriptionLink;

        /**
         * Optional URI reference to the non-elastic resource pool which this compute contributes
         * capacity to. Based on dynamic queries in elastic resource pools this compute may
         * participate in other pools too.
         *
         * <p>
         * It is recommended to use {@code ResourcePoolState.query} instead which works for both
         * elastic and non-elastic resource pools.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String resourcePoolLink;

        /**
         * Ip address of this compute instance.
         */
        @PropertyOptions(indexing = PropertyIndexingOption.CASE_INSENSITIVE)
        public String address;

        /**
         * The type of this compute resource.
         */
        @UsageOption(option = PropertyUsageOption.SINGLE_ASSIGNMENT)
        @Since(ReleaseConstants.RELEASE_VERSION_0_5_6)
        public ComputeType type;

        /**
         * MAC address of this compute instance.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String primaryMAC;

        /**
         * Power state of this compute instance.
         */
        public PowerState powerState = PowerState.UNKNOWN;

        /** Lifecycle state indicating runtime state of a resource instance. */
        @Documentation(description = "Lifecycle state indicating runtime state of a resource instance.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public LifecycleState lifecycleState = LifecycleState.READY;

        /**
         * URI reference to parent compute instance.
         */
        public String parentLink;

        /**
         * Reference to the management endpoint of the compute provider.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public URI adapterManagementReference;

        /**
         * Disks associated with this compute instance.
         */
        @PropertyOptions(usage = PropertyUsageOption.LINKS)
        public List<String> diskLinks;

        /**
         * Network interfaces associated with this compute instance.
         */
        @PropertyOptions(usage = PropertyUsageOption.LINKS)
        public List<String> networkInterfaceLinks;

        /**
         * Compute creation time in micros since epoch.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public Long creationTimeMicros;
    }

    /**
     * State with in-line, expanded description.
     */
    public static class ComputeStateWithDescription extends ComputeState {
        /**
         * Compute description associated with this compute instance.
         */
        public ComputeDescription description;

        public static URI buildUri(URI computeHostUri) {
            return UriUtils.extendUriWithQuery(computeHostUri,
                    UriUtils.URI_PARAM_ODATA_EXPAND,
                    ComputeState.FIELD_NAME_DESCRIPTION_LINK);
        }

        public static ComputeStateWithDescription create(
                ComputeDescription desc, ComputeState currentState) {
            ComputeStateWithDescription chsWithDesc = new ComputeStateWithDescription();
            currentState.copyTo(chsWithDesc);

            chsWithDesc.address = currentState.address;
            chsWithDesc.diskLinks = currentState.diskLinks;
            chsWithDesc.parentLink = currentState.parentLink;
            chsWithDesc.powerState = currentState.powerState;
            chsWithDesc.primaryMAC = currentState.primaryMAC;
            chsWithDesc.type = currentState.type;
            chsWithDesc.resourcePoolLink = currentState.resourcePoolLink;
            chsWithDesc.adapterManagementReference = currentState.adapterManagementReference;
            chsWithDesc.networkInterfaceLinks = currentState.networkInterfaceLinks;
            chsWithDesc.creationTimeMicros = currentState.creationTimeMicros;

            chsWithDesc.description = desc;
            chsWithDesc.descriptionLink = desc.documentSelfLink;

            return chsWithDesc;
        }
    }

    public ComputeService() {
        super(ComputeState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleGet(Operation get) {
        ComputeState currentState = getState(get);
        boolean doExpand = get.getUri().getQuery() != null &&
                UriUtils.hasODataExpandParamValue(get.getUri());

        if (!doExpand) {
            get.setBody(currentState).complete();
            return;
        }

        // retrieve the description and include in an augmented version of our
        // state.
        Operation getDesc = Operation
                .createGet(this, currentState.descriptionLink)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                get.fail(e);
                                return;
                            }
                            ComputeDescription desc = o
                                    .getBody(ComputeDescription.class);
                            ComputeStateWithDescription chsWithDesc = ComputeStateWithDescription
                                    .create(desc, currentState);
                            get.setBody(chsWithDesc).complete();
                        });
        sendRequest(getDesc);
    }

    @Override
    public void handleCreate(Operation start) {
        try {
            validateCreate(start);
            start.complete();
        } catch (Throwable t) {
            start.fail(t);
        }
    }

    @Override
    public void handlePut(Operation put) {
        try {
            ComputeState returnState = validatePut(put);
            setState(put, returnState);
            put.complete();
        } catch (Throwable t) {
            put.fail(t);
        }
    }

    private ComputeState validateCreate(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        ComputeState state = op.getBody(ComputeState.class);
        if (state.creationTimeMicros == null) {
            state.creationTimeMicros = Utils.getNowMicrosUtc();
        }

        Utils.validateState(getStateDescription(), state);
        return state;
    }

    private ComputeState validatePut(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        ComputeState state = op.getBody(ComputeState.class);
        ComputeState currentState = getState(op);
        if (state.type != null && currentState.type != null && state.type != currentState.type) {
            throw new IllegalArgumentException("Compute type can not be changed");
        }
        Utils.validateState(getStateDescription(), state);
        return state;
    }

    @Override
    public void handlePatch(Operation patch) {
        ComputeState currentState = getState(patch);
        Function<Operation, Boolean> customPatchHandler = t -> {
            boolean hasStateChanged = false;
            ComputeState patchBody = patch.getBody(ComputeState.class);

            if (patchBody.type != null) {
                if (currentState.type == null) {
                    currentState.type = patchBody.type;
                    hasStateChanged = true;
                } else if (patchBody.type != currentState.type) {
                    throw new IllegalArgumentException("Compute type can not be changed");
                }
            }

            if (patchBody.address != null
                    && !patchBody.address.equals(currentState.address)) {
                InetAddressValidator.getInstance().isValidInet4Address(
                        patchBody.address);
                currentState.address = patchBody.address;
                hasStateChanged = true;
            }

            if (patchBody.powerState != null
                    && patchBody.powerState != PowerState.UNKNOWN
                    && patchBody.powerState != currentState.powerState) {
                currentState.powerState = patchBody.powerState;
                hasStateChanged = true;
            }

            if (patchBody.diskLinks != null) {
                if (currentState.diskLinks == null) {
                    currentState.diskLinks = patchBody.diskLinks;
                    hasStateChanged = true;
                } else {
                    for (String link : patchBody.diskLinks) {
                        if (!currentState.diskLinks.contains(link)) {
                            currentState.diskLinks.add(link);
                            hasStateChanged = true;
                        }
                    }
                }
            }

            if (patchBody.networkInterfaceLinks != null) {
                if (currentState.networkInterfaceLinks == null) {
                    currentState.networkInterfaceLinks = patchBody.networkInterfaceLinks;
                    hasStateChanged = true;
                } else {
                    for (String link : patchBody.networkInterfaceLinks) {
                        if (!currentState.networkInterfaceLinks.contains(link)) {
                            currentState.networkInterfaceLinks.add(link);
                            hasStateChanged = true;
                        }
                    }
                }
            }
            return hasStateChanged;
        };
        ResourceUtils.handlePatch(patch, currentState, getStateDescription(),
                ComputeState.class, customPatchHandler);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();

        ComputeState template = (ComputeState) td;

        template.id = UUID.randomUUID().toString();
        template.primaryMAC = "01:23:45:67:89:ab";
        template.descriptionLink = UriUtils.buildUriPath(
                ComputeDescriptionService.FACTORY_LINK,
                "on-prem-one-cpu-vm-guest");
        template.resourcePoolLink = null;
        template.type = ComputeType.VM_GUEST;
        template.adapterManagementReference = URI
                .create("https://esxhost-01:443/sdk");

        return template;
    }
}
