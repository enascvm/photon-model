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
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.ZONE_KEY;
import static com.vmware.xenon.common.Operation.STATUS_CODE_BAD_REQUEST;

import java.net.URI;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.adapters.util.EndpointAdapterUtils;
import com.vmware.photon.controller.model.adapters.util.EndpointAdapterUtils.Retriever;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BasicConnection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.ConnectionException;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.GetMoRef;
import com.vmware.photon.controller.model.adapters.vsphere.util.finders.Finder;
import com.vmware.photon.controller.model.adapters.vsphere.util.finders.FinderException;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
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

            URI adapterManagementUri = getAdapterManagementUri(host);
            String id = body.endpointProperties.get(REGION_KEY);

            BasicConnection connection = createConnection(adapterManagementUri, credentials);
            try {
                // login and session creation
                connection.connect();
                if (id != null && !id.isEmpty()) {
                    new Finder(connection, id);
                }
                callback.accept(null, null);
            } catch (RuntimeFaultFaultMsg | InvalidPropertyFaultMsg | FinderException e) {
                ServiceErrorResponse r = Utils.toServiceErrorResponse(e);
                r.statusCode = STATUS_CODE_BAD_REQUEST;
                r.message = String.format("Error looking for datacenter for id '%s'", id);
                callback.accept(r, e);
            } catch (ConnectionException e) {
                String msg = String.format("Cannot establish connection to %s",
                        adapterManagementUri);
                logInfo(msg);
                callback.accept(null, e);
            } finally {
                closeQuietly(connection);
            }
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
            String id = r.get(REGION_KEY).orElse(null);
            Consumer<String> consumer = (regionId) -> {
                cd.regionId = regionId;
                cd.zoneId = r.get(ZONE_KEY).orElse(null);

                cd.environmentName = ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;
                cd.instanceAdapterReference = UriUtils.buildUri(
                        ServiceHost.LOCAL_HOST,
                        this.getHost().getPort(),
                        VSphereUriPaths.INSTANCE_SERVICE, null);
                cd.enumerationAdapterReference = UriUtils.buildUri(
                        ServiceHost.LOCAL_HOST,
                        this.getHost().getPort(),
                        VSphereUriPaths.ENUMERATION_SERVICE, null);
                cd.statsAdapterReference = UriUtils.buildUri(
                        ServiceHost.LOCAL_HOST,
                        this.getHost().getPort(),
                        VSphereUriPaths.STATS_SERVICE, null);
                cd.powerAdapterReference = UriUtils.buildUri(
                        ServiceHost.LOCAL_HOST,
                        this.getHost().getPort(),
                        VSphereUriPaths.POWER_SERVICE, null);
            };

            if (id == null || id.isEmpty()) {
                loadDefault(r, consumer);
            } else {
                consumer.accept(id);
            }

        };
    }

    private void loadDefault(Retriever r, Consumer<String> consumer) {

        String host = r.getRequired(HOST_NAME_KEY);
        URI adapterManagementUri = getAdapterManagementUri(host);
        AuthCredentialsServiceState ac = new AuthCredentialsServiceState();
        credentials().accept(ac, r);

        BasicConnection connection = createConnection(adapterManagementUri, ac);
        try {
            // login and session creation
            connection.connect();
            Finder finder = new Finder(connection, null);
            String datacenterName = new GetMoRef(connection)
                    .entityProp(finder.getDatacenter().object, "name");
            consumer.accept(datacenterName);
        } catch (RuntimeFaultFaultMsg | InvalidPropertyFaultMsg | FinderException ex) {
            throw new IllegalArgumentException("Error looking for default datacenter", ex);
        } catch (ConnectionException e) {
            throw new IllegalArgumentException(e);
        } finally {
            closeQuietly(connection);
        }
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

    private BasicConnection createConnection(URI adapterReference,
            AuthCredentialsServiceState auth) {
        BasicConnection connection = new BasicConnection();

        // TODO control sslErrors policy externally
        connection.setIgnoreSslErrors(true);

        connection.setUsername(auth.privateKeyId);
        connection.setPassword(auth.privateKey);

        connection.setURI(adapterReference);

        return connection;
    }

    private void closeQuietly(BasicConnection connection) {
        try {
            connection.close();
        } catch (Exception e) {
            logWarning("Error closing connection to " + connection.getURI(), e);
        }
    }
}
