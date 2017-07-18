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

package com.vmware.photon.controller.model.adapters.util;

import static com.vmware.xenon.common.UriUtils.buildFactoryUri;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Stream;

import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.RequestType;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService.PhotonModelAdapterConfig;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class EndpointAdapterUtils {

    public static final String MOCK_REQUEST = "mockRequest";
    public static final String ENDPOINT_REFERENCE_URI = "endpointReferenceUri";

    /**
     * Register end-point adapters into End-point Adapters Registry.
     * @param host
     *         The host the end-point is running on.
     * @param endpointType
     *         The type of the end-point.
     * @param startedAdapterLinks
     *         The array of started adapter links.
     * @param adapterLinksToRegister
     *         Map of adapter links (to be registered) to their adapter type key. e.g for
     *         standard adapters this is {@link com.vmware.photon.controller.model.UriPaths.AdapterTypePath#key}
     * @see #handleEndpointRegistration(ServiceHost, EndpointType, Consumer)
     */
    public static void registerEndpointAdapters(
            ServiceHost host,
            EndpointType endpointType,
            String[] startedAdapterLinks,
            Map<String, String> adapterLinksToRegister) {

        // Count all adapters - both FAILED and STARTED
        AtomicInteger adaptersCountDown = new AtomicInteger(startedAdapterLinks.length);

        // Keep started adapters only...
        // - key = adapter type ket (e.g. AdapterTypePath.key)
        // - value = adapter URI
        Map<String, String> startedAdapters = new ConcurrentHashMap<>();

        // Wait for all adapter services to start
        host.registerForServiceAvailability((op, ex) -> {

            if (ex != null) {
                String adapterPath = op.getUri().getPath();
                host.log(Level.WARNING, "Starting '%s' adapter [%s]: FAILED - %s",
                        endpointType, adapterPath, Utils.toString(ex));
            } else {
                String adapterPath = op.getUri().getPath();
                host.log(Level.FINE, "Starting '%s' adapter [%s]: SUCCESS",
                        endpointType, adapterPath);

                String adapterKey = adapterLinksToRegister.get(adapterPath);

                if (adapterKey != null) {
                    startedAdapters.put(
                            adapterKey,
                            AdapterUriUtil.buildAdapterUri(host, adapterPath).toString());
                }
            }

            if (adaptersCountDown.decrementAndGet() == 0) {
                // Once ALL Adapters are started register them into End-point Adapters Registry

                host.log(Level.INFO, "Starting %d '%s' adapters: SUCCESS",
                        startedAdapters.size(), endpointType);

                // Populate end-point config with started adapters
                Consumer<PhotonModelAdapterConfig> endpointConfigEnhancer = ep -> ep.adapterEndpoints
                        .putAll(startedAdapters);

                // Delegate to core end-point config/registration logic
                handleEndpointRegistration(
                        host, endpointType, endpointConfigEnhancer);
            }

        }, startedAdapterLinks);

    }

    /**
     * Enhance end-point config with all adapters that are to be published/registered to End-point
     * Adapters Registry.
     * @param host
     *         The host the end-point is running on.
     * @param endpointType
     *         The type of the end-point.
     * @param endpointConfigEnhancer
     *         Optional {@link PhotonModelAdapterConfig} enhance logic specific to the end-point
     *         type. The config passed to the callback is pre-populated with id, name and
     *         documentSelfLink (all set with {@code endpointType} param). The enhancer might
     *         populate the config with the links of end-point adapters. Once enhanced the config is
     *         posted to the {@link PhotonModelAdaptersRegistryService Adapters Registry}.
     */
    public static void handleEndpointRegistration(
            ServiceHost host,
            EndpointType endpointType,
            Consumer<PhotonModelAdapterConfig> endpointConfigEnhancer) {

        host.registerForServiceAvailability((op, ex) -> {

            // Once End-point Adapters Registry is available register end-point adapters

            if (ex != null) {
                host.log(Level.WARNING,
                        "End-point Adapters Registry is not available on this host. Please ensure %s is started.",
                        PhotonModelAdaptersRegistryService.class.getSimpleName());
                return;
            }

            PhotonModelAdapterConfig endpointConfig = new PhotonModelAdapterConfig();

            // By contract the id MUST equal to endpointType
            endpointConfig.id = endpointType.name();
            endpointConfig.documentSelfLink = endpointConfig.id;
            endpointConfig.name = endpointType.toString();
            endpointConfig.adapterEndpoints = new HashMap<>();

            if (endpointConfigEnhancer != null) {
                // Pass to enhancer to customize the end-point config.
                endpointConfigEnhancer.accept(endpointConfig);
            }

            URI uri = buildFactoryUri(host, PhotonModelAdaptersRegistryService.class);

            Operation postEndpointConfigOp = Operation.createPost(uri)
                    .setReferer(host.getUri())
                    .setBody(endpointConfig);

            host.sendWithDeferredResult(postEndpointConfigOp).whenComplete((o, e) -> {
                if (e != null) {
                    host.log(Level.WARNING,
                            "Registering %d '%s' adapters into End-point Adapters Registry: FAILED - %s",
                            endpointConfig.adapterEndpoints.size(), endpointType,
                            Utils.toString(e));
                } else {
                    host.log(Level.INFO,
                            "Registering %d '%s' adapters into End-point Adapters Registry: SUCCESS",
                            endpointConfig.adapterEndpoints.size(), endpointType);
                }
            });

        }, PhotonModelAdaptersRegistryService.FACTORY_LINK);
    }

    public static void handleEndpointRequest(StatelessService service, Operation op,
            EndpointConfigRequest body,
            BiConsumer<AuthCredentialsServiceState, Retriever> credEnhancer,
            BiConsumer<ComputeDescription, Retriever> descEnhancer,
            BiConsumer<ComputeState, Retriever> compEnhancer,
            BiConsumer<EndpointState, Retriever> endpointEnhancer,
            BiConsumer<AuthCredentialsServiceState, BiConsumer<ServiceErrorResponse, Throwable>> validator) {

        switch (body.requestType) {
        case VALIDATE:
            if (body.isMockRequest) {
                op.complete();
            } else {
                validate(service, op, body, credEnhancer, validator);
            }
            break;

        case ENHANCE:
            op.complete();
            configureEndpoint(service, body, credEnhancer, descEnhancer, compEnhancer,
                    endpointEnhancer);
            break;

        default:
            op.fail(new IllegalArgumentException(
                    "Unexpected endpoint request: " + body.requestType.toString()));
        }
    }

    private static void configureEndpoint(StatelessService service, EndpointConfigRequest body,
            BiConsumer<AuthCredentialsServiceState, Retriever> credEnhancer,
            BiConsumer<ComputeDescription, Retriever> descEnhancer,
            BiConsumer<ComputeState, Retriever> compEnhancer,
            BiConsumer<EndpointState, Retriever> endpointEnhancer) {

        TaskManager tm = new TaskManager(service, body.taskReference, body.resourceLink());
        Consumer<Throwable> onFailure = tm::patchTaskToFailure;

        Consumer<Operation> onSuccess = (op) -> {

            EndpointState endpoint = op.getBody(EndpointState.class);
            op.complete();

            AuthCredentialsServiceState authState = new AuthCredentialsServiceState();

            Map<String, String> props = new HashMap<>(body.endpointProperties);
            props.put(MOCK_REQUEST, String.valueOf(body.isMockRequest));
            props.put(ENDPOINT_REFERENCE_URI, body.resourceReference.toString());

            Retriever r = Retriever.of(props);
            try {
                credEnhancer.accept(authState, r);

                ComputeDescription cd = new ComputeDescription();
                descEnhancer.accept(cd, r);

                ComputeState cs = new ComputeState();
                cs.powerState = PowerState.ON;
                compEnhancer.accept(cs, r);

                EndpointState es = new EndpointState();
                es.endpointProperties = new HashMap<>();
                es.regionId = r.get(EndpointConfigRequest.REGION_KEY).orElse(null);
                endpointEnhancer.accept(es, r);

                Stream<Operation> operations = Stream.of(
                        Pair.of(authState, endpoint.authCredentialsLink),
                        Pair.of(cd, endpoint.computeDescriptionLink),
                        Pair.of(cs, endpoint.computeLink),
                        Pair.of(es, endpoint.documentSelfLink))
                        .map((p) -> Operation.createPatch(body.buildUri(p.right))
                                .setBody(p.left)
                                .setReferer(service.getUri()));

                applyChanges(tm, service, endpoint, operations);
            } catch (Exception e) {
                tm.patchTaskToFailure(e);
            }

        };

        AdapterUtils.getServiceState(service, body.resourceReference, onSuccess, onFailure);
    }

    private static void applyChanges(TaskManager tm, StatelessService service,
            EndpointState endpoint, Stream<Operation> operations) {

        OperationJoin joinOp = OperationJoin.create(operations);
        joinOp.setCompletion((ox, exc) -> {
            if (exc != null) {
                service.logSevere(
                        "Error patching endpoint configuration data for %s. %s",
                        endpoint.endpointType,
                        Utils.toString(exc));
                tm.patchTaskToFailure(exc.values().iterator().next());
                return;
            }
            service.logFine(
                    () -> String.format("Successfully completed %s endpoint configuration tasks.",
                            endpoint.endpointType));
            tm.finishTask();
        });
        joinOp.sendWith(service);
    }

    private static void validate(StatelessService service, Operation op,
            EndpointConfigRequest configRequest,
            BiConsumer<AuthCredentialsServiceState, Retriever> enhancer,
            BiConsumer<AuthCredentialsServiceState, BiConsumer<ServiceErrorResponse, Throwable>> validator) {

        Consumer<Operation> onSuccessGetCredentials = oc -> {
            try {
                AuthCredentialsServiceState credentials = oc
                        .getBody(AuthCredentialsServiceState.class);
                enhancer.accept(credentials, Retriever.of(configRequest.endpointProperties));

                BiConsumer<ServiceErrorResponse, Throwable> callback = (r, e) -> {
                    service.logInfo("Finished validating credentials for operation: %d",
                            op.getId());
                    if (r == null && e == null) {
                        if (configRequest.requestType == RequestType.VALIDATE) {
                            op.complete();
                        }
                    } else {
                        op.fail(e, r);
                    }
                };
                service.logInfo("Validating credentials for operation: %d", op.getId());
                validator.accept(credentials, callback);
            } catch (Throwable e) {
                op.fail(e);
            }
        };

        Consumer<Operation> onSuccessGetEndpoint = o -> {
            EndpointState endpointState = o.getBody(EndpointState.class);

            if (endpointState.authCredentialsLink != null) {
                AdapterUtils.getServiceState(service, endpointState.authCredentialsLink,
                        onSuccessGetCredentials, op::fail);
            } else {
                onSuccessGetCredentials.accept(getEmptyAuthCredentialState(configRequest));
            }
        };

        // if there is an endpoint, get it and then get the credentials
        if (configRequest.resourceReference != null) {
            // If there is an error getting endpoint state, we assume that endpoint is not yet
            // created, but it was requested with a predefined link
            AdapterUtils.getServiceState(service, configRequest.resourceReference,
                    onSuccessGetEndpoint,
                    e -> onSuccessGetCredentials
                            .accept(getEmptyAuthCredentialState(configRequest)));
        } else { // otherwise, proceed with empty credentials and rely on what's in
            // endpointProperties
            onSuccessGetCredentials.accept(getEmptyAuthCredentialState(configRequest));
        }
    }

    private static Operation getEmptyAuthCredentialState(EndpointConfigRequest configRequest) {
        AuthCredentialsServiceState authCredentials = new AuthCredentialsServiceState();
        if (configRequest.tenantLinks != null) {
            authCredentials.tenantLinks = configRequest.tenantLinks;
        }
        return new Operation().setBody(authCredentials);
    }

    public static class Retriever {
        final Map<String, String> values;

        private Retriever(Map<String, String> values) {
            this.values = values;
        }

        static Retriever of(Map<String, String> values) {
            return new Retriever(values);
        }

        public Optional<String> get(String key) {
            return Optional.ofNullable(this.values.get(key));
        }

        public String getRequired(String key) {
            return get(key).orElseThrow(
                    () -> new IllegalArgumentException(String.format("%s is required", key)));
        }
    }
}
