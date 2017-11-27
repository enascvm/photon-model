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

import static com.vmware.photon.controller.model.ComputeProperties.LINKED_ENDPOINT_PROP_NAME;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.REGION_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.SUPPORT_DATASTORES;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.ZONE_KEY;
import static com.vmware.photon.controller.model.adapters.vsphere.constants.VSphereConstants.VSPHERE_IGNORE_CERTIFICATE_WARNINGS;
import static com.vmware.xenon.common.Operation.STATUS_CODE_BAD_REQUEST;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;

import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.RequestType;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.EndpointAdapterUtils;
import com.vmware.photon.controller.model.adapters.util.EndpointAdapterUtils.Retriever;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BasicConnection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.ConnectionException;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.GetMoRef;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.security.ssl.ServerX509TrustManager;
import com.vmware.photon.controller.model.security.ssl.X509TrustManagerResolver;
import com.vmware.photon.controller.model.security.util.CertificateUtil;
import com.vmware.photon.controller.model.security.util.EncryptionUtils;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;

/**
 * Adapter to validate and enhance vSphere based endpoints.
 */
public class VSphereEndpointAdapterService extends StatelessService {
    public static final String SELF_LINK = VSphereUriPaths.ENDPOINT_CONFIG_ADAPTER;

    public static final String HOST_NAME_KEY = "hostName";

    private static final long CERT_RESOLVE_TIMEOUT = CertificateUtil.DEFAULT_CONNECTION_TIMEOUT_MILLIS;

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        EndpointConfigRequest body = op.getBody(EndpointConfigRequest.class);

        if (body.requestType == RequestType.CHECK_IF_ACCOUNT_EXISTS) {
            checkIfAccountExistsAndGetExistingDocuments(body, op);
            return;
        }

        EndpointAdapterUtils.handleEndpointRequest(this, op, body, credentials(),
                computeDesc(), compute(), endpoint(), validate(body));
    }

    public static BiConsumer<EndpointService.EndpointState, Retriever> endpoint() {
        return (e, r) -> {
            e.endpointProperties.put(PRIVATE_KEYID_KEY, r.getRequired(PRIVATE_KEYID_KEY));
            e.endpointProperties.put(HOST_NAME_KEY, r.getRequired(HOST_NAME_KEY));

            r.get(LINKED_ENDPOINT_PROP_NAME)
                    .ifPresent(rk -> e.endpointProperties.put(LINKED_ENDPOINT_PROP_NAME, rk));
            r.get(REGION_KEY).ifPresent(rk -> e.endpointProperties.put(REGION_KEY, rk));
            r.get(ZONE_KEY).ifPresent(zk -> e.endpointProperties.put(ZONE_KEY, zk));

            // vSphere end-point does have the notion of datastores.
            e.endpointProperties.put(SUPPORT_DATASTORES, Boolean.TRUE.toString());
        };
    }

    private BiConsumer<AuthCredentialsServiceState, BiConsumer<ServiceErrorResponse, Throwable>> validate(
            EndpointConfigRequest body) {

        return (credentials, callback) -> {
            String host = body.endpointProperties.get(HOST_NAME_KEY);
            URI adapterManagementUri = getAdapterManagementUri(host);
            String id = body.endpointProperties.get(REGION_KEY);

            validateEndpointUniqueness(credentials, body.checkForEndpointUniqueness, host)
                    .whenComplete((aVoid, throwable) -> {
                        if (throwable != null) {
                            if (throwable instanceof CompletionException) {
                                throwable = throwable.getCause();
                            }
                            callback.accept(null, throwable);
                            return;
                        }
                        // certificate to trust in the request
                        String certPEM = body.endpointProperties
                                .get(EndpointConfigRequest.CERTIFICATE_PROP_NAME);
                        if (certPEM != null) {
                            X509Certificate[] certificates = CertificateUtil
                                    .createCertificateChain(certPEM);
                            storeCertificate(certificates[0], body.tenantLinks, (o, e) -> {
                                doValidate(credentials, callback, adapterManagementUri, id);
                            });
                            return;
                        } else {
                            // check certificate if the endpoint.
                            X509TrustManagerResolver resolver = CertificateUtil
                                    .resolveCertificate(adapterManagementUri, CERT_RESOLVE_TIMEOUT);

                            if (!resolver.isCertsTrusted()) {
                                // bad cert
                                if (body.endpointProperties
                                        .get(EndpointConfigRequest.ACCEPT_SELFSIGNED_CERTIFICATE)
                                        != null) {
                                    // lax policy
                                    storeCertificate(resolver.getCertificate(), body.tenantLinks,
                                            (o, e) -> {
                                                doValidate(credentials, callback,
                                                        adapterManagementUri, id);
                                            });
                                } else {
                                    // reply with error
                                    handleBadCertificate(resolver, callback);
                                }
                                return;
                            }
                        }

                        doValidate(credentials, callback, adapterManagementUri, id);
                    });
        };
    }

    /**
     * Validate that the endpoint is unique by comparing the User and Host
     */
    private DeferredResult<Void> validateEndpointUniqueness(AuthCredentialsServiceState credentials,
            Boolean endpointUniqueness, String host) {
        if (Boolean.TRUE.equals(endpointUniqueness)) {

            Query authQuery = Builder.create()
                    .addFieldClause(PRIVATE_KEYID_KEY, credentials.privateKeyId).build();

            Query endpointQuery = Builder.create()
                    .addCompositeFieldClause(EndpointState.FIELD_NAME_ENDPOINT_PROPERTIES,
                            HOST_NAME_KEY, host).build();

            return EndpointAdapterUtils.validateEndpointUniqueness(this.getHost(), authQuery,
                    endpointQuery, EndpointType.vsphere.name());
        }
        return DeferredResult.completed(null);
    }

    private void doValidate(AuthCredentialsServiceState credentials,
            BiConsumer<ServiceErrorResponse, Throwable> callback, URI adapterManagementUri,
            String id) {
        BasicConnection connection = createConnection(adapterManagementUri, credentials);
        try {
            // login and session creation
            connection.connect();
            if (id != null && !id.isEmpty()) {
                // if a datacenter is configured also validate moref is OK
                new GetMoRef(connection).entityProp(VimUtils.convertStringToMoRef(id), VimNames.PROPERTY_NAME);
            }
            callback.accept(null, null);
        } catch (RuntimeFaultFaultMsg | InvalidPropertyFaultMsg e) {
            ServiceErrorResponse r = Utils.toServiceErrorResponse(e);
            r.statusCode = STATUS_CODE_BAD_REQUEST;
            r.message = String.format("Error looking for datacenter for id '%s'", id);
            callback.accept(r, e);
        } catch (ConnectionException e) {
            String msg = String.format("Cannot establish connection to %s",
                    adapterManagementUri);
            logWarning(msg);
            callback.accept(null, e);
        } finally {
            closeQuietly(connection);
        }
    }

    private void storeCertificate(X509Certificate endCertificate,
            List<String> tenantLinks,
            CompletionHandler ch) {
        CertificateUtil.storeCertificate(endCertificate, tenantLinks,
                this.getHost(), this, ch);
    }

    private void handleBadCertificate(X509TrustManagerResolver resolver,
            BiConsumer<ServiceErrorResponse, Throwable> callback) {
        callback.accept(resolver.getCertificateInfoServiceErrorResponse(), null);
    }

    public static BiConsumer<AuthCredentialsServiceState, Retriever> credentials() {
        return (c, r) -> {
            // overwrite fields that are set in endpointProperties, otherwise use the present ones
            if (c.privateKey != null) {
                r.get(PRIVATE_KEY_KEY).ifPresent(pKey -> c.privateKey = pKey);
            } else {
                c.privateKey = r.getRequired(PRIVATE_KEY_KEY);
            }

            if (c.privateKeyId != null) {
                r.get(PRIVATE_KEYID_KEY).ifPresent(pKeyId -> c.privateKeyId = pKeyId);
            } else {
                c.privateKeyId = r.getRequired(PRIVATE_KEYID_KEY);
            }
            c.type = "Username";
        };
    }

    private BiConsumer<ComputeDescription, Retriever> computeDesc() {
        return (cd, r) -> {
            cd.regionId = r.get(REGION_KEY).orElse(null);
            cd.zoneId = r.get(ZONE_KEY).orElse(null);

            cd.environmentName = ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;

            cd.instanceAdapterReference = AdapterUriUtil.buildAdapterUri(this.getHost(),
                    VSphereUriPaths.INSTANCE_SERVICE);
            cd.enumerationAdapterReference = AdapterUriUtil.buildAdapterUri(this.getHost(),
                    VSphereUriPaths.ENUMERATION_SERVICE);
            cd.statsAdapterReference = AdapterUriUtil.buildAdapterUri(this.getHost(),
                    VSphereUriPaths.STATS_SERVICE);
            cd.powerAdapterReference = AdapterUriUtil.buildAdapterUri(this.getHost(),
                    VSphereUriPaths.POWER_SERVICE);
            cd.diskAdapterReference = AdapterUriUtil.buildAdapterUri(this.getHost(),
                    VSphereUriPaths.DISK_SERVICE);
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

    private BasicConnection createConnection(URI adapterReference,
            AuthCredentialsServiceState auth) {
        BasicConnection connection = new BasicConnection();

        // ignores the certificate for testing purposes
        if (VSPHERE_IGNORE_CERTIFICATE_WARNINGS) {
            connection.setIgnoreSslErrors(true);
        } else {
            ServerX509TrustManager trustManager = ServerX509TrustManager.getInstance();
            connection.setTrustManager(trustManager);
        }

        connection.setUsername(auth.privateKeyId);
        connection.setPassword(EncryptionUtils.decrypt(auth.privateKey));

        connection.setURI(adapterReference);

        return connection;
    }

    private void closeQuietly(BasicConnection connection) {
        try {
            connection.close();
        } catch (Exception e) {
            logWarning(
                    () -> String.format("Error closing connection to " + connection.getURI(), e));
        }
    }

    //TODO https://jira-hzn.eng.vmware.com/browse/VSYM-8583
    private void checkIfAccountExistsAndGetExistingDocuments(EndpointConfigRequest req,
            Operation op) {
        req.accountAlreadyExists = false;
        op.setBody(req);
        op.complete();
    }
}
