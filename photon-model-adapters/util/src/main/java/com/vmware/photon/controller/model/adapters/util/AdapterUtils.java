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

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Common utility methods for different adapters.
 */
public class AdapterUtils {

    private static final int EXECUTOR_SHUTDOWN_INTERVAL_MINUTES = 5;

    /**
     * Checks if the given resource is a compute host or a VM.
     *
     * @param computeDesc ComputeDescription of the resource.
     * @return If the resource is a compute host or not.
     */
    public static boolean isComputeHost(ComputeStateWithDescription computeDesc) {
        if (computeDesc.parentLink == null) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Method will be responsible for getting the service state for the
     * requested resource and invoke Consumer callback for success and
     * failure
     */
    public static void getServiceState(Service service, URI computeUri, Consumer<Operation> success,
            Consumer<Throwable> failure) {
        service.sendRequest(Operation.createGet(computeUri).setCompletion((o, e) -> {
            if (e != null) {
                failure.accept(e);
                return;
            }
            success.accept(o);
        }));
    }

    /**
     * Method will be responsible for getting the service state for the
     * requested resource and invoke Consumer callback for success and
     * failure
     */
    public static void getServiceState(Service service, String path,
            Consumer<Operation> success, Consumer<Throwable> failure) {
        service.sendRequest(Operation.createGet(service, path).setCompletion(success, (o, e) -> {
            failure.accept(e);
        }));
    }

    /**
     * Creates a POST operation for the creation of a state.
     */
    public static Operation createPostOperation(StatelessService service,
            ResourceState state, String factoryLink) {
        return Operation
                .createPost(service,
                        factoryLink)
                .setBody(state)
                .setReferer(service.getUri());
    }

    /**
     * Creates a PATCH operation for updating an existing state.
     */
    public static Operation createPatchOperation(StatelessService service,
            ResourceState state, String existingStateLink) {
        URI existingStateURI = UriUtils.buildUri(service.getHost(),
                existingStateLink);
        return Operation
                .createPatch(existingStateURI)
                .setBody(state)
                .setReferer(service.getUri());
    }

    /**
     * Creates a PUT operation for updating an existing state.
     */
    public static Operation createPutOperation(StatelessService service,
            ResourceState state, String existingStateLink) {
        URI existingStateURI = UriUtils.buildUri(service.getHost(),
                existingStateLink);
        return Operation
                .createPut(existingStateURI)
                .setBody(state)
                .setReferer(service.getUri());
    }

    /**
     * Creates a PATCH operation for updating an existing state.
     */
    public static Operation createDeleteOperation(StatelessService service,
            ResourceState state) {
        URI existingStateURI = UriUtils.buildUri(service.getHost(),
                state.documentSelfLink);
        return Operation
                .createDelete(existingStateURI)
                .setBody(state)
                .setReferer(service.getUri());
    }

    /**
     * Waits for termination of given executor service.
     */
    public static void awaitTermination(ExecutorService executor) {
        try {
            if (!executor.awaitTermination(EXECUTOR_SHUTDOWN_INTERVAL_MINUTES, TimeUnit.MINUTES)) {
                Utils.logWarning(
                        "Executor service can't be shutdown. Trying to shutdown now...");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Utils.logWarning(e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            Utils.logWarning(e.getMessage());
        }
    }

    /**
     * Method to validate that the passed in Enumeration Request State is valid.
     * Validating that the parent compute link, the adapter links and the resource
     * reference are populated in the request.
     * <p>
     * Also defaulting the enumeration action to START.
     *
     * @param enumRequest The enumeration request.
     */
    public static void validateEnumRequest(ComputeEnumerateResourceRequest enumRequest) {
        if (enumRequest.adapterManagementReference == null) {
            throw new IllegalArgumentException("adapterManagementReference is required.");
        }
        if (enumRequest.resourceReference == null) {
            throw new IllegalArgumentException("resourceReference is required.");
        }
        if (enumRequest.enumerationAction == null) {
            enumRequest.enumerationAction = EnumerationAction.START;
        }
    }

    /**
     * See {@link #registerForServiceAvailability(ServiceHost, Consumer, Consumer, String...)}.
     */
    public static void registerForServiceAvailability(ServiceHost host, String... servicePaths) {
        registerForServiceAvailability(host, null, null, servicePaths);
    }

    /**
     * See {@link ServiceHost#registerForServiceAvailability} for more details. This method differs
     * by giving a single callback for multiple servicePaths.
     */
    public static void registerForServiceAvailability(ServiceHost host,
            Consumer<Operation> onSuccess, Consumer<Throwable> onFailure, String... servicePaths) {
        AtomicInteger completionCount = new AtomicInteger(0);
        host.registerForServiceAvailability(
                (o, e) -> {
                    if (e != null) {
                        if (onFailure != null) {
                            onFailure.accept(e);
                            return;
                        }

                        String message = String
                                .format("Failed waiting for service availability: %s",
                                        e.getMessage());
                        host.log(Level.WARNING, message);
                        throw new IllegalStateException(message);
                    }
                    if (completionCount.incrementAndGet() == servicePaths.length) {
                        host.log(Level.FINE, "Services available: %s",
                                Arrays.toString(servicePaths));
                        if (onSuccess != null) {
                            onSuccess.accept(o);
                        }
                    }
                }, true, servicePaths);
    }

    public static ResourceState getDeletionState(long documentExpirationMicros) {
        ResourceState resourceState = new ResourceState();
        resourceState.documentExpirationTimeMicros = documentExpirationMicros;

        return resourceState;
    }


    /**
     * Utility method to create and operation to remove an endpointLink from the endpointLinks set
     * of the resourceState
     * @param service
     * @param endpointLink
     * @param selfLink
     * @param endpointLinks
     * @return
     */
    public static Operation createEndpointLinksUpdateOperation(StatelessService service, String
            endpointLink, String selfLink, Set<String> endpointLinks) {

        if (endpointLinks == null || !endpointLinks.contains(endpointLink)) {
            return null;
        }

        Map<String, Collection<Object>> endpointsToRemoveMap = Collections.singletonMap(
                EndpointState.FIELD_NAME_ENDPOINT_LINKS, Collections.singleton(endpointLink));
        ServiceStateCollectionUpdateRequest serviceStateCollectionUpdateRequest =
                ServiceStateCollectionUpdateRequest.create(null, endpointsToRemoveMap);

        return Operation
                .createPatch(UriUtils.buildUri(service.getHost(), selfLink))
                .setReferer(service.getUri())
                .setBody(serviceStateCollectionUpdateRequest)
                .setCompletion(
                        (updateOp, exception) -> {
                            if (exception != null) {
                                service.logWarning(() -> String.format("PATCH to " +
                                        "instance " +
                                        "service %s, " +
                                        "failed: %s", updateOp.getUri(), exception.toString()));
                                return;
                            }
                        });
    }

}
