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

import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.REGION_KEY;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.adapters.util.EndpointAdapterUtils;
import com.vmware.photon.controller.model.adapters.util.EndpointAdapterUtils.Retriever;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Adapter to validate and enhance vSphere based endpoints.
 *
 */
public class VSphereEndpointAdapterService extends StatelessService {
    public static final String SELF_LINK = VSphereUriPaths.ENDPOINT_CONFIG_ADAPTER;

    public static final String HOST_NAME_KEY = "hostName";

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        EndpointConfigRequest body = op.getBody(EndpointConfigRequest.class);

        EndpointAdapterUtils.handleEndpointRequest(this, op, body, credentials(),
                computeDesc(), compute(), validate(body));
    }

    private BiConsumer<AuthCredentialsServiceState, BiConsumer<ServiceErrorResponse, Throwable>> validate(
            EndpointConfigRequest body) {

        return (credentials, callback) -> {
            String host = body.endpointProperties.get(HOST_NAME_KEY);
            VSphereIOThreadPool pool = VSphereIOThreadPool.createDefault(this.getHost(), 1);

            URI adapterManagementUri = getAdapterManagementUri(host);
            pool.submit(this, adapterManagementUri, credentials, (connection, e) -> {
                if (e != null) {
                    String msg = String.format("Cannot establish connection to %s",
                            adapterManagementUri);
                    logInfo(msg);
                    callback.accept(null, e);
                } else {
                    callback.accept(null, null);
                }
            }, (c) -> c.setRequestTimeout(5, TimeUnit.SECONDS));
        };
    }

    private BiConsumer<AuthCredentialsServiceState, Retriever> credentials() {
        return (c, r) -> {
            c.privateKey = r.getRequired(PRIVATE_KEY_KEY);
            c.privateKeyId = r.getRequired(PRIVATE_KEYID_KEY);
            c.type = "Username";
        };
    }

    private BiConsumer<ComputeDescription, Retriever> computeDesc() {
        return (cd, r) -> {
            cd.regionId = r.getRequired(REGION_KEY);
            cd.zoneId = cd.regionId;

            cd.environmentName = ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;
            cd.instanceAdapterReference = UriUtils.buildUri(getHost(),
                    VSphereUriPaths.INSTANCE_SERVICE);
            cd.enumerationAdapterReference = UriUtils.buildUri(getHost(),
                    VSphereUriPaths.ENUMERATION_SERVICE);
            cd.statsAdapterReference = UriUtils.buildUri(getHost(), VSphereUriPaths.STATS_SERVICE);
            cd.powerAdapterReference = UriUtils.buildUri(getHost(), VSphereUriPaths.POWER_SERVICE);
        };
    }

    private BiConsumer<ComputeState, Retriever> compute() {
        return (c, r) -> {
            c.adapterManagementReference = getAdapterManagementUri(r.getRequired(HOST_NAME_KEY));
        };
    }

    private URI getAdapterManagementUri(String host) {
        StringBuilder vcUrl = new StringBuilder("https://");
        vcUrl.append(host);
        vcUrl.append("/sdk");
        return UriUtils.buildUri(vcUrl.toString());
    }
}
