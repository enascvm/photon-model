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

import static com.vmware.photon.controller.model.constants.PhotonModelConstants.CUSTOM_PROP_ENDPOINT_LINK;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.photon.controller.model.resources.LoadBalancerService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceStateWithDescription;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.RouterService;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.ReflectionUtils;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.ServiceStateMapUpdateRequest;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
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
        set.add(ResourceGroupState.class);

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

            Set<String> endpointLinks = new HashSet<>();
            endpointLinks.add(endpointLink);

            //This method will assign the value of the endpointLinks  created above if it does not exist for the given
            //resource OR it will merge it with the existing collection if already set.
            ReflectionUtils.setOrUpdatePropertyValue(
                    sdDesc.propertyDescriptions.get(PhotonModelConstants.FIELD_NAME_ENDPOINT_LINKS),
                    state,
                    endpointLinks);

        } else if (ENDPOINT_LINK_CUSTOM_PROP_SUPPORT.contains(state.getClass())) {

            ServiceDocumentDescription sdDesc = ServiceDocumentDescription.Builder.create()
                    .buildDescription(state.getClass());

            if (endpointLink != null && !endpointLink.isEmpty()) {
                ReflectionUtils.setOrUpdatePropertyValue(
                        sdDesc.propertyDescriptions.get(ResourceState.FIELD_NAME_CUSTOM_PROPERTIES),
                        state,
                        singletonMap(PhotonModelConstants.CUSTOM_PROP_ENDPOINT_LINK, endpointLink));

                Set<String> endpointLinks = new HashSet<>();
                endpointLinks.add(endpointLink);

                //This method will assign the value of the endpointLinks  created above if it does not exist for the given
                //resource OR it will merge it with the existing collection if already set.
                ReflectionUtils.setOrUpdatePropertyValue(
                        sdDesc.propertyDescriptions.get(PhotonModelConstants.FIELD_NAME_ENDPOINT_LINKS),
                        state,
                        endpointLinks);
            }
        }

        return state;
    }


    /**
     * Utility method to create an operation to remove an endpointLink from the endpointLinks set
     * of the resourceState and also update the endpointLink property of the specific resourceState
     * @param service
     * @param endpointLink
     * @param selfLink
     * @param endpointLinks
     * @return
     */

    /*
    endpointLink is also updated to provide backward compatibility to Tango team's Day 2 operation.
    The verbose code to update endpointLink can be cleaned up once the endpointLink is completely
    deprecated within photon-model.
     */
    public static Operation createRemoveEndpointLinksOperation(Service service, String
            endpointLink, Object document, String selfLink, Set<String> endpointLinks) {

        if (endpointLinks == null || !endpointLinks.contains(endpointLink)) {
            return null;
        }

        Map<String, Collection<Object>> endpointsToRemoveMap = Collections.singletonMap(
                EndpointService.EndpointState.FIELD_NAME_ENDPOINT_LINKS,
                Collections.singleton(endpointLink));
        ServiceStateCollectionUpdateRequest serviceStateCollectionUpdateRequest =
                ServiceStateCollectionUpdateRequest.create(null, endpointsToRemoveMap);

        return Operation
                .createPatch(UriUtils.buildUri(service.getHost(), selfLink))
                .setReferer(service.getUri())
                .setBody(serviceStateCollectionUpdateRequest)
                .setCompletion(
                        (updateOp, exception) -> {
                            if (exception != null) {
                                service.getHost().log(Level.WARNING, () -> String.format("PATCH " +
                                                "to instance service %s, failed: %s",
                                        updateOp.getUri(), exception.toString()));
                                return;
                            }

                            String resourceEndpointLink;
                            Set<String> resourceEndpointLinks;
                            ServiceDocument serviceDocument = Utils.fromJson(document,
                                    ServiceDocument.class);
                            String documentKind = serviceDocument.documentKind;
                            if (documentKind.equals(Utils.buildKind(ComputeState.class))) {
                                ComputeState computeState = Utils.fromJson(document,
                                        ComputeState.class);
                                resourceEndpointLinks = computeState.endpointLinks;
                                resourceEndpointLink = computeState.endpointLink;
                                //if the endpoint being deleted is the endpointLink of the
                                //resourceState, then assign a new endpointLink
                                if (endpointLink.equals(resourceEndpointLink)) {
                                    computeState.endpointLink = getUpdatedEndpointLink
                                            (resourceEndpointLink, resourceEndpointLinks);
                                    handleResourceStateEndpointLinkUpdate(service, selfLink,
                                            computeState);
                                }
                            } else if (documentKind.equals((Utils.buildKind(ComputeDescription
                                    .class)))) {
                                ComputeDescription computeDescriptionState = Utils.fromJson(
                                        document, ComputeDescription.class);
                                resourceEndpointLinks = computeDescriptionState.endpointLinks;
                                resourceEndpointLink = computeDescriptionState.endpointLink;
                                updateEndpointLink(service, endpointLink, selfLink,
                                        resourceEndpointLink, resourceEndpointLinks);
                            } else if (documentKind.equals((Utils.buildKind(
                                    ComputeStateWithDescription.class)))) {
                                ComputeStateWithDescription computeStateWithDescriptionState =
                                        Utils.fromJson(document,
                                                ComputeStateWithDescription.class);
                                resourceEndpointLinks = computeStateWithDescriptionState
                                        .endpointLinks;
                                resourceEndpointLink = computeStateWithDescriptionState
                                        .endpointLink;
                                if (endpointLink.equals(resourceEndpointLink)) {
                                    computeStateWithDescriptionState.endpointLink =
                                            getUpdatedEndpointLink
                                                    (resourceEndpointLink, resourceEndpointLinks);
                                    handleResourceStateEndpointLinkUpdate(service, selfLink,
                                            computeStateWithDescriptionState);
                                }
                            } else if (documentKind.equals((Utils.buildKind(DiskState.class)))) {
                                DiskState diskState = Utils.fromJson(document,
                                        DiskState.class);
                                resourceEndpointLinks = diskState.endpointLinks;
                                resourceEndpointLink = diskState.endpointLink;
                                if (endpointLink.equals(resourceEndpointLink)) {
                                    diskState.endpointLink = getUpdatedEndpointLink
                                            (resourceEndpointLink, resourceEndpointLinks);
                                    handleResourceStateEndpointLinkUpdate(service, selfLink,
                                            diskState);
                                }
                            } else if (documentKind.equals(Utils.buildKind(NetworkState.class))) {
                                NetworkState networkState = Utils.fromJson(document,
                                        NetworkState.class);
                                resourceEndpointLinks = networkState.endpointLinks;
                                resourceEndpointLink = networkState.endpointLink;
                                if (endpointLink.equals(resourceEndpointLink)) {
                                    networkState.endpointLink = getUpdatedEndpointLink
                                            (resourceEndpointLink, resourceEndpointLinks);
                                    handleResourceStateEndpointLinkUpdate(service, selfLink,
                                            networkState);
                                }
                            } else if (documentKind.equals((Utils
                                    .buildKind(NetworkInterfaceState.class)))) {
                                NetworkInterfaceState networkInterfaceState = Utils
                                        .fromJson(document,
                                                NetworkInterfaceState.class);
                                resourceEndpointLinks = networkInterfaceState.endpointLinks;
                                resourceEndpointLink = networkInterfaceState.endpointLink;
                                if (endpointLink.equals(resourceEndpointLink)) {
                                    networkInterfaceState.endpointLink = getUpdatedEndpointLink
                                            (resourceEndpointLink, resourceEndpointLinks);
                                    handleResourceStateEndpointLinkUpdate(service, selfLink,
                                            networkInterfaceState);
                                }
                            } else if (documentKind.equals((Utils
                                    .buildKind(NetworkInterfaceDescription.class)))) {
                                NetworkInterfaceDescription networkInterfaceDescription =
                                        Utils.fromJson(document,
                                                NetworkInterfaceDescription.class);
                                resourceEndpointLinks = networkInterfaceDescription.endpointLinks;
                                resourceEndpointLink = networkInterfaceDescription.endpointLink;
                                if (endpointLink.equals(resourceEndpointLink)) {
                                    networkInterfaceDescription.endpointLink =
                                            getUpdatedEndpointLink
                                                    (resourceEndpointLink, resourceEndpointLinks);
                                    handleResourceStateEndpointLinkUpdate(service, selfLink,
                                            networkInterfaceDescription);
                                }
                            } else if (documentKind.equals(Utils.buildKind(SubnetState.class))) {
                                SubnetState subnetState = Utils.fromJson(document,
                                        SubnetState.class);
                                resourceEndpointLinks = subnetState.endpointLinks;
                                resourceEndpointLink = subnetState.endpointLink;
                                if (endpointLink.equals(resourceEndpointLink)) {
                                    subnetState.endpointLink = getUpdatedEndpointLink
                                            (resourceEndpointLink, resourceEndpointLinks);
                                    handleResourceStateEndpointLinkUpdate(service, selfLink,
                                            subnetState);
                                }
                            } else if (documentKind.equals(Utils
                                    .buildKind(SecurityGroupState.class))) {
                                SecurityGroupState securityGroupState = Utils
                                        .fromJson(document,
                                                SecurityGroupState.class);
                                resourceEndpointLinks = securityGroupState.endpointLinks;
                                resourceEndpointLink = securityGroupState.endpointLink;
                                if (endpointLink.equals(resourceEndpointLink)) {
                                    securityGroupState.endpointLink = getUpdatedEndpointLink
                                            (resourceEndpointLink, resourceEndpointLinks);
                                    handleResourceStateEndpointLinkUpdate(service, selfLink,
                                            securityGroupState);
                                }
                            } else if (documentKind.equals(Utils
                                    .buildKind(StorageDescription.class))) {
                                StorageDescription storageDescriptionState = Utils
                                        .fromJson(document, StorageDescription.class);
                                resourceEndpointLinks = storageDescriptionState.endpointLinks;
                                resourceEndpointLink = storageDescriptionState.endpointLink;
                                if (endpointLink.equals(resourceEndpointLink)) {
                                    storageDescriptionState.endpointLink = getUpdatedEndpointLink
                                            (resourceEndpointLink, resourceEndpointLinks);
                                    handleResourceStateEndpointLinkUpdate(service, selfLink,
                                            storageDescriptionState);
                                }
                            } else if (documentKind.equals(Utils.buildKind(ImageState.class))) {
                                ImageState imageState = Utils.fromJson(document,
                                        ImageState.class);
                                resourceEndpointLinks = imageState.endpointLinks;
                                resourceEndpointLink = imageState.endpointLink;
                                if (endpointLink.equals(resourceEndpointLink)) {
                                    imageState.endpointLink = getUpdatedEndpointLink
                                            (resourceEndpointLink, resourceEndpointLinks);
                                    handleResourceStateEndpointLinkUpdate(service, selfLink,
                                            imageState);
                                }
                            } else if (documentKind.equals(
                                    Utils.buildKind(RouterService.RouterState.class))) {
                                RouterService.RouterState routerState = Utils
                                        .fromJson(document, RouterService.RouterState
                                                .class);
                                resourceEndpointLinks = routerState.endpointLinks;
                                resourceEndpointLink = routerState.endpointLink;
                                if (endpointLink.equals(resourceEndpointLink)) {
                                    routerState.endpointLink = getUpdatedEndpointLink
                                            (resourceEndpointLink, resourceEndpointLinks);
                                    handleResourceStateEndpointLinkUpdate(service, selfLink,
                                            routerState);
                                }
                            } else if (documentKind.equals(Utils
                                    .buildKind(LoadBalancerService.LoadBalancerState.class))) {
                                LoadBalancerService.LoadBalancerState loadBalancerState =
                                        Utils.fromJson
                                                (document, LoadBalancerService.LoadBalancerState
                                                        .class);
                                resourceEndpointLinks = loadBalancerState.endpointLinks;
                                resourceEndpointLink = loadBalancerState.endpointLink;
                                if (endpointLink.equals(resourceEndpointLink)) {
                                    loadBalancerState.endpointLink = getUpdatedEndpointLink
                                            (resourceEndpointLink, resourceEndpointLinks);
                                    handleResourceStateEndpointLinkUpdate(service, selfLink,
                                            loadBalancerState);
                                }
                            } else if (documentKind.equals(Utils.buildKind(StorageDescription
                                    .class))) {
                                StorageDescription storageDescription = Utils
                                        .fromJson(document,
                                                StorageDescription.class);
                                resourceEndpointLinks = storageDescription.endpointLinks;
                                resourceEndpointLink = storageDescription.endpointLink;
                                if (endpointLink.equals(resourceEndpointLink)) {
                                    storageDescription.endpointLink = getUpdatedEndpointLink
                                            (resourceEndpointLink, resourceEndpointLinks);
                                    handleResourceStateEndpointLinkUpdate(service, selfLink,
                                            storageDescription);
                                }
                            } else if (documentKind.equals(Utils.buildKind(ResourceGroupState
                                    .class))) {
                                ResourceGroupState resourceGroupState = Utils
                                        .fromJson(document,
                                                ResourceGroupState.class);
                                resourceEndpointLinks = resourceGroupState.endpointLinks;
                                resourceEndpointLink = resourceGroupState.endpointLink;
                                String updatedEndpointLink = getUpdatedEndpointLink
                                        (resourceEndpointLink, resourceEndpointLinks);

                                //update both endpointLink and custom EndpointLink for consistency
                                if (endpointLink.equals(resourceEndpointLink)) {
                                    resourceGroupState.endpointLink = updatedEndpointLink;
                                    handleResourceStateEndpointLinkUpdate(service, selfLink,
                                            resourceGroupState);
                                }

                                if (resourceGroupState.customProperties != null &&
                                        resourceGroupState.customProperties
                                                .get(CUSTOM_PROP_ENDPOINT_LINK) != null) {
                                    String resourceCustomEndpointLink =
                                            resourceGroupState.customProperties
                                                    .get(CUSTOM_PROP_ENDPOINT_LINK);

                                    if (endpointLink.equals(resourceCustomEndpointLink)) {

                                        Map<String, Collection<Object>> customEndpointLinkToRemove
                                                = Collections.singletonMap(
                                                ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                                                Collections.singleton(CUSTOM_PROP_ENDPOINT_LINK));
                                        Map<String, Map<Object, Object>> customEndpointLinkToAdd =
                                                Collections.singletonMap(
                                                ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                                                Collections.singletonMap(CUSTOM_PROP_ENDPOINT_LINK,
                                                        getUpdatedEndpointLink(
                                                        resourceEndpointLink,
                                                        resourceEndpointLinks)));
                                        ServiceStateMapUpdateRequest serviceStateMapUpdateRequest =
                                                ServiceStateMapUpdateRequest
                                                        .create(customEndpointLinkToAdd,
                                                                customEndpointLinkToRemove);
                                        handleResourceStateEndpointLinkUpdate(service, selfLink,
                                                serviceStateMapUpdateRequest);
                                    }
                                }
                            }
                        });
    }

    private static void updateEndpointLink(Service service, String endpointLink, String selfLink,
                                           String resourceEndpointLink, Set<String>
                                                   resourceEndpointLinks) {
        if (!endpointLink.equals(resourceEndpointLink)) {
            return;
        }

        EndpointLinkPatchReq req = new EndpointLinkPatchReq();
        req.endpointLink =  getUpdatedEndpointLink
                (resourceEndpointLink, resourceEndpointLinks);
        handleResourceStateEndpointLinkUpdate(service, selfLink, req);
    }

    public static class EndpointLinkPatchReq {
        String endpointLink;
    }


    private static String getUpdatedEndpointLink(String resourceEndpointLink, Set<String>
            endpointLinks) {

        String endpointLinkVal = null;
        if (endpointLinks.size() == 1) {
            return null;
        }

        for (String endpointLink : endpointLinks) {
            if (!endpointLink.equals(resourceEndpointLink)) {
                endpointLinkVal = endpointLink;
                break;
            }
        }
        return endpointLinkVal;
    }


    private static void handleResourceStateEndpointLinkUpdate(Service service, String selfLink,
                                                             Object resourceState) {
        Operation.createPatch(UriUtils.buildUri(service.getHost(), selfLink))
                .setReferer(service.getUri())
                .setBody(resourceState)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                service.getHost().log(Level.WARNING,
                                        () -> String.format("PATCH to " +
                                                "updated endpointLink in instance" +
                                                " service %s, failed: %s", o.getUri(), e
                                                .toString()));
                                return;
                            }
                        }).sendWith(service);
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

    /**
     * Merges two lists of strings, filtering duplicate elements from the second one (the patch).
     * Also keeping track if change of source list has been modified.
     * @param source The source list (can be null).
     * @param patch The patch list. If null, the @source will be the result.
     * @return Returns a pair. The left part is the merged list and the right one is the boolean
     * value, indicating if the changes to @source is modified.
     */
    public static <C extends Collection<String>> Pair<C, Boolean> mergeLists(
            C source, C patch) {
        if (patch == null) {
            return new ImmutablePair<>(source, Boolean.FALSE);
        }
        boolean hasChanged = false;
        C result = source;
        if (result == null) {
            result = patch;
            hasChanged = true;
        } else {
            for (String newValue : patch) {
                if (!result.contains(newValue)) {
                    result.add(newValue);
                    hasChanged = true;
                }
            }
        }
        return new ImmutablePair<>(result, Boolean.valueOf(hasChanged));
    }


    /**
     * Executes given code in the specified executor.
     * @param executor Executor in which code is to be executed.
     * @param runnable Code to be executed in the executor.
     * @param failure failure consumer.
     */
    public static void runInExecutor(ExecutorService executor, Runnable runnable,
            Consumer<Throwable> failure) {
        try {
            OperationContext operationContext = OperationContext.getOperationContext();

            executor.submit(() -> {
                OperationContext.restoreOperationContext(operationContext);
                try {
                    runnable.run();
                } catch (Throwable runnableExc) {
                    failure.accept(runnableExc);
                }
            });
        } catch (Throwable executorExc) {
            failure.accept(executorExc);
        }
    }

    /**
     * Sets a metric with unit and value in the ServiceStat associated with the service
     */
    public static void setStat(Service service, String name, String unit, double value) {
        service.getHost().log(Level.INFO, "Setting stat [service=%s] [name=%s] [unit=%s] [value=%f]",
                service.getClass(), name, unit, value);
        ServiceStats.ServiceStat stat = new ServiceStats.ServiceStat();
        stat.name = name;
        stat.unit = unit;
        service.setStat(stat, value);
    }
}
