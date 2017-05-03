/*
 * Copyright (c) 2015-2017 VMware, Inc. All Rights Reserved.
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
import java.util.UUID;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Represents a load balancer.
 */
public class LoadBalancerService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES_LOAD_BALANCERS;

    public static final String FIELD_NAME_ENDPOINT_LINK = PhotonModelConstants.FIELD_NAME_ENDPOINT_LINK;
    public static final String FIELD_NAME_REGION_ID = "regionId";
    public static final String FIELD_NAME_ZONE_ID = "zoneId";

    public static final int MIN_PORT_NUMBER = 1;
    public static final int MAX_PORT_NUMBER = 65535;

    /**
     * Represents the state of a load balancer.
     */
    public static class LoadBalancerState extends ResourceState {
        /**
         * Link to the cloud account endpoint the load balancer belongs to.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String endpointLink;

        /**
         * Region identifier of this service instance.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String regionId;

        /**
         * Zone identifier of this service instance.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String zoneId;

        /**
         * Load balancer protocol.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String protocol;

        /**
         * The port the load balancer is listening on.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Integer port;

        /**
         * The protocol to use for routing traffic to instances.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String instanceProtocol;

        /**
         * The port on which the instances are listening.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Integer instancePort;

        /**
         * The adapter to use to create the load balancer instance.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public URI instanceAdapterReference;
    }

    public LoadBalancerService() {
        super(LoadBalancerState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleStart(Operation start) {
        processInput(start);
        start.complete();
    }

    @Override
    public void handlePut(Operation put) {
        LoadBalancerState returnState = processInput(put);
        setState(put, returnState);
        put.complete();
    }

    private LoadBalancerState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        LoadBalancerState state = op.getBody(LoadBalancerState.class);
        validateState(state);
        return state;
    }

    @Override
    public void handlePatch(Operation patch) {
        LoadBalancerState currentState = getState(patch);
        ResourceUtils.handlePatch(patch, currentState, getStateDescription(),
                LoadBalancerState.class, null);
    }

    private void validateState(LoadBalancerState state) {
        Utils.validateState(getStateDescription(), state);
        if (state.port < MIN_PORT_NUMBER || state.port > MAX_PORT_NUMBER) {
            throw new IllegalArgumentException("Invalid load balancer port number.");
        }
        if (state.instancePort < MIN_PORT_NUMBER || state.instancePort > MAX_PORT_NUMBER) {
            throw new IllegalArgumentException("Invalid instance port number.");
        }
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        ServiceUtils.setRetentionLimit(td);
        LoadBalancerState template = (LoadBalancerState) td;

        template.id = UUID.randomUUID().toString();
        template.name = "load-balancer";
        template.endpointLink = UriUtils.buildUriPath(EndpointService.FACTORY_LINK,
                "my-endpoint");
        template.protocol = "HTTP";
        template.port = 80;
        template.instanceProtocol = "HTTP";
        template.instancePort = 80;

        return template;
    }
}
