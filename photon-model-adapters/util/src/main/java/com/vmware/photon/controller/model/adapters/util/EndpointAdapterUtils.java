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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Stream;

import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.RequestType;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService.PhotonModelAdapterConfig;
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
     * Register end-point config into Adapters Registry.
     *
     * @param service
     *            The end-point adapter service.
     * @param serviceStartOp
     *            The service start operation. It is completed upon completion of registration into
     *            Adapters Registry.
     * @param endpointType
     *            The type of the end-point.
     * @param endpointConfigEnhancer
     *            Optional {@link PhotonModelAdapterConfig} enhance logic specific to the end-point
     *            type. The config passed to the callback is pre-populated with id, name and
     *            documentSelfLink (all set with {@code endpointType} param). The enhancer might
     *            populate the config with the links of end-point adapters. Once enhanced the config
     *            is posted to the {@link PhotonModelAdaptersRegistryService Adapters Registry}.
     */
    public static void handleEndpointRegistration(
            ServiceHost host,
            String endpointType,
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

            endpointConfig.id = endpointType;
            endpointConfig.documentSelfLink = endpointConfig.id;
            endpointConfig.name = endpointType;
            endpointConfig.adapterEndpoints = new HashMap<>();

            if (endpointConfigEnhancer != null) {
                // Pass to enhancer to customize the end-point config.
                endpointConfigEnhancer.accept(endpointConfig);
            }

            URI uri = buildFactoryUri(host, PhotonModelAdaptersRegistryService.class);

            Operation postEndpointConfigOp = Operation.createPost(uri).setBody(endpointConfig);

            host.sendWithDeferredResult(postEndpointConfigOp).whenComplete((o, e) -> {
                if (e != null) {
                    host.log(Level.WARNING,
                            "Registering %d '%s' adapters into End-point Adapters Registry: FAILED - %s",
                            endpointConfig.adapterEndpoints.size(), endpointType, Utils.toString(ex));
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

        default:
            op.complete();
            configureEndpoint(service, body, credEnhancer, descEnhancer, compEnhancer,
                    endpointEnhancer);
            break;
        }
    }

    private static void configureEndpoint(StatelessService service, EndpointConfigRequest body,
            BiConsumer<AuthCredentialsServiceState, Retriever> credEnhancer,
            BiConsumer<ComputeDescription, Retriever> descEnhancer,
            BiConsumer<ComputeState, Retriever> compEnhancer,
            BiConsumer<EndpointState, Retriever> endpointEnhancer) {

        Consumer<Throwable> onFailure = (t) -> {
            AdapterUtils.sendFailurePatchToProvisioningTask(service, body.taskReference, t);
        };

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
                endpointEnhancer.accept(es, r);

                Stream<Operation> operations = Stream.of(
                        Pair.of(authState, endpoint.authCredentialsLink),
                        Pair.of(cd, endpoint.computeDescriptionLink),
                        Pair.of(cs, endpoint.computeLink),
                        Pair.of(es, endpoint.documentSelfLink))
                        .map((p) -> {
                            return Operation.createPatch(body.buildUri(p.right)).setBody(p.left)
                                    .setReferer(service.getUri());
                        });

                applyChanges(service, body, endpoint, operations);
            } catch (Exception e) {
                AdapterUtils.sendFailurePatchToProvisioningTask(service, body.taskReference, e);
            }

        };

        AdapterUtils.getServiceState(service, body.resourceReference, onSuccess, onFailure);
    }

    private static void applyChanges(StatelessService service, EndpointConfigRequest body,
            EndpointState endpoint, Stream<Operation> operations) {

        OperationJoin joinOp = OperationJoin.create(operations);
        joinOp.setCompletion((ox, exc) -> {
            if (exc != null) {
                service.logSevere(
                        "Error patching endpoint configuration data for %s. %s",
                        endpoint.endpointType,
                        Utils.toString(exc));
                AdapterUtils.sendFailurePatchToEnumerationTask(service, body.taskReference,
                        exc.values().iterator().next());
                return;
            }
            service.logFine(
                    () -> String.format("Successfully completed %s endpoint configuration tasks.",
                            endpoint.endpointType));
            AdapterUtils.sendPatchToProvisioningTask(service, body.taskReference);
        });
        joinOp.sendWith(service);
    }

    private static void validate(StatelessService service, Operation op, EndpointConfigRequest body,
            BiConsumer<AuthCredentialsServiceState, Retriever> enhancer,
            BiConsumer<AuthCredentialsServiceState, BiConsumer<ServiceErrorResponse, Throwable>> validator) {

        Consumer<Operation> onSuccessGetCredentials = oc -> {
            try {
                AuthCredentialsServiceState credentials = oc
                        .getBody(AuthCredentialsServiceState.class);
                enhancer.accept(credentials, Retriever.of(body.endpointProperties));

                BiConsumer<ServiceErrorResponse, Throwable> callback = (r, e) -> {
                    service.logInfo("Finished validating credentials for operation: %d", op.getId());
                    if (r == null && e == null) {
                        if (body.requestType == RequestType.VALIDATE) {
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
                        onSuccessGetCredentials, e -> op.fail(e));
            } else {
                onSuccessGetCredentials.accept(getEmptyAuthCredentialState());
            }
        };

        // if there is an endpoint, get it and then get the credentials
        if (body.resourceReference != null) {
            // If there is an error getting endpoint state, we assume that endpoint is not yet
            // created, but it was requested with a predefined link
            AdapterUtils.getServiceState(service, body.resourceReference, onSuccessGetEndpoint,
                    e -> onSuccessGetCredentials.accept(getEmptyAuthCredentialState()));
        } else { // otherwise, proceed with empty credentials and rely on what's in
                 // endpointProperties
            onSuccessGetCredentials.accept(getEmptyAuthCredentialState());
        }
    }

    private static Operation getEmptyAuthCredentialState() {
        return new Operation().setBody(new AuthCredentialsServiceState());
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
