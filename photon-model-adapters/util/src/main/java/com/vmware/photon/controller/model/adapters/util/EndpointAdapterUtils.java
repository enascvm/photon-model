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

import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.RequestType;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class EndpointAdapterUtils {

    public static void handleEndpointRequest(StatelessService service, Operation op,
            EndpointConfigRequest body,
            BiConsumer<AuthCredentialsServiceState, Retriever> credEnhancer,
            BiConsumer<ComputeDescription, Retriever> descEnhancer,
            BiConsumer<ComputeState, Retriever> compEnhancer,
            BiConsumer<AuthCredentialsServiceState, BiConsumer<ServiceErrorResponse, Throwable>> validator) {

        switch (body.requestType) {
        case VALIDATE:
            if (body.isMockRequest) {
                op.complete();
            } else {
                validate(op, body, credEnhancer, validator);
            }
            break;

        default:
            op.complete();
            configureEndpoint(service, body, credEnhancer, descEnhancer, compEnhancer);
            break;
        }
    }

    private static void configureEndpoint(StatelessService service, EndpointConfigRequest body,
            BiConsumer<AuthCredentialsServiceState, Retriever> credEnhancer,
            BiConsumer<ComputeDescription, Retriever> descEnhancer,
            BiConsumer<ComputeState, Retriever> compEnhancer) {

        Consumer<Operation> onSuccess = (op) -> {
            EndpointState endpoint = op.getBody(EndpointState.class);
            op.complete();

            Retriever r = Retriever.of(endpoint.endpointProperties);
            try {
                AuthCredentialsServiceState authState = new AuthCredentialsServiceState();
                credEnhancer.accept(authState, r);

                ComputeDescription cd = new ComputeDescription();
                descEnhancer.accept(cd, r);

                ComputeState cs = new ComputeState();
                cs.powerState = PowerState.ON;
                compEnhancer.accept(cs, r);

                Stream<Operation> operations = Stream.of(
                        Pair.of(authState, endpoint.authCredentialsLink),
                        Pair.of(cd, endpoint.computeDescriptionLink),
                        Pair.of(cs, endpoint.computeLink))
                        .map((p) -> {
                            return Operation.createPatch(body.buildUri(p.right)).setBody(p.leff)
                                    .setReferer(service.getUri());
                        });

                applyChanges(service, body, endpoint, operations);
            } catch (IllegalArgumentException e) {
                AdapterUtils.sendFailurePatchToProvisioningTask(service, body.taskReference, e);
            }
        };

        Consumer<Throwable> onFailure = (t) -> {
            AdapterUtils.sendFailurePatchToProvisioningTask(service, body.taskReference, t);
        };

        AdapterUtils.getServiceState(service, body.resourceReference, onSuccess, onFailure);
    }

    private static void applyChanges(StatelessService service, EndpointConfigRequest body,
            EndpointState endpoint, Stream<Operation> operations) {

        OperationJoin joinOp = OperationJoin.create(operations);
        joinOp.setCompletion((ox,
                exc) -> {
            if (exc != null) {
                service.logSevere(
                        "Error patching endpoint configuration data for %s. %s",
                        endpoint.endpointType,
                        Utils.toString(exc));
                AdapterUtils.sendFailurePatchToEnumerationTask(service, body.taskReference,
                        exc.values().iterator().next());

            }
            service.logInfo("Successfully completed %s endpoint configuration tasks.",
                    endpoint.endpointType);
            AdapterUtils.sendPatchToProvisioningTask(service, body.taskReference);
            return;
        });
        joinOp.sendWith(service);
    }

    private static void validate(Operation op, EndpointConfigRequest body,
            BiConsumer<AuthCredentialsServiceState, Retriever> enhancer,
            BiConsumer<AuthCredentialsServiceState, BiConsumer<ServiceErrorResponse, Throwable>> validator) {

        try {
            AuthCredentialsServiceState credentials = new AuthCredentialsServiceState();
            enhancer.accept(credentials, Retriever.of(body.endpointProperties));

            BiConsumer<ServiceErrorResponse, Throwable> callback = (r, e) -> {
                if (r == null && e == null) {
                    if (body.requestType == RequestType.VALIDATE) {
                        op.complete();
                    }
                } else if (e != null && r != null) {
                    op.fail(e, r);
                } else {
                    op.fail(e);
                }
            };

            validator.accept(credentials, callback);
        } catch (IllegalArgumentException e) {
            op.fail(e);
        }
    }

    private static class Pair<L, R> {
        final L leff;
        final R right;

        private Pair(L leff, R right) {
            this.leff = leff;
            this.right = right;
        }

        static <L, R> Pair<L, R> of(L left, R right) {
            return new Pair<L, R>(left, right);
        }
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
