/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.adapters.registry.operations;

import java.net.URI;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceOperationSpec;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceType;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTop;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 * Various {@link ResourceOperationSpec} related Utilities.
 */
public class ResourceOperationUtils {
    public static final String SCRIPT_ENGINE_NAME_JS = "js";
    public static final String SCRIPT_CONTEXT_RESOURCE = "resource";

    public static final String COMPUTE_KIND = Utils.buildKind(ComputeState.class);
    public static final String NETWORK_KIND = Utils.buildKind(NetworkState.class);

    /**
     * Lookup for {@link ResourceOperationSpec} by given {@code endpointType},
     * {@code resourceType} and {@code operation}
     * @param host
     *         host to use to create operation
     * @param refererURI
     *         the referer to use when send the operation
     * @param endpointType
     *         the resource's endpoint type
     * @param resourceType
     *         the resource type
     * @param operation
     *         the operation
     * @return
     */
    public static DeferredResult<ResourceOperationSpec> lookUpByEndpointType(
            ServiceHost host,
            URI refererURI,
            String endpointType,
            ResourceType resourceType,
            String operation) {

        return lookUp(host, refererURI, endpointType, resourceType, operation).thenApply(specs -> {
            if (specs.isEmpty()) {
                return null;
            } else {
                ResourceOperationSpec spec = specs.iterator().next();
                if (specs.size() > 1) {
                    Utils.log(ResourceOperationUtils.class, "lookUpByEndpointType",
                            Level.SEVERE,
                            "Multiple specs for endpointType: %s, resourceType: %s and "
                                    + "operation: %s. Will use the first one: %s",
                            endpointType, resourceType, operation, spec);

                }
                return spec;
            }
        });
    }

    /**
     * Lookup for {@link ResourceOperationSpec}s by given {@code endpointType},
     * {@code resourceType} and optionally {@code operation}
     * <p>
     * If operation not specified then return all resource operation specs for the given
     * {@code endpointType} and {@code resourceType}
     * @param host
     *         host to use to create operation
     * @param refererURI
     *         the referer to use when send the operation
     * @param endpointType
     *         the resource's endpoint type
     * @param resourceType
     *         the resource type
     * @param operation
     *         optional operation id argument
     * @return
     */
    private static DeferredResult<List<ResourceOperationSpec>> lookUp(
            ServiceHost host,
            URI refererURI,
            String endpointType,
            ResourceType resourceType,
            String operation) {

        Query.Builder builder = Query.Builder.create()
                .addKindFieldClause(ResourceOperationSpec.class)
                .addFieldClause(
                        ResourceOperationSpec.FIELD_NAME_ENDPOINT_TYPE,
                        endpointType)
                .addFieldClause(
                        ResourceOperationSpec.FIELD_NAME_RESOURCE_TYPE,
                        resourceType);
        if (operation != null) {
            builder.addFieldClause(
                    ResourceOperationSpec.FIELD_NAME_OPERATION,
                    operation);
        }
        Query query = builder.build();

        QueryTop<ResourceOperationSpec> top = new QueryTop<>(
                host, query, ResourceOperationSpec.class, null);
        if (operation != null) {
            top.setMaxResultsLimit(3);
        }
        top.setReferer(refererURI);
        return top.collectDocuments(Collectors.toList());
    }

    /**
     * Lookup for {@link ResourceOperationSpec} by given {@code endpointLink},
     * {@code resourceType} and {@code operation}
     * @param host
     *         host to use to create operation
     * @param refererURI
     *         the referer to use when send the operation
     * @param endpointLink
     *         the endpoint link of the resource
     * @param resourceType
     *         the resource type
     * @param operation
     *         the operation
     * @return
     */
    public static DeferredResult<ResourceOperationSpec> lookUpByEndpointLink(
            ServiceHost host,
            URI refererURI,
            String endpointLink,
            ResourceType resourceType,
            String operation) {

        return host.sendWithDeferredResult(Operation.createGet(host, endpointLink)
                .setReferer(refererURI))
                .thenCompose(o -> lookUpByEndpointType(host, refererURI,
                        (o.getBody(EndpointState.class)).endpointType,
                        resourceType, operation)
                );
    }

    public static <T extends ResourceState> DeferredResult<List<ResourceOperationSpec>> lookupByResourceState(
            ServiceHost host,
            URI refererURI,
            T resourceState) {
        AssertUtil.assertNotNull(resourceState, "'resourceState' must be set.");
        String endpointLink;
        ResourceType resourceType;
        if (resourceState instanceof ComputeState) {
            ComputeState compute = (ComputeState) resourceState;
            endpointLink = compute.endpointLink;
            resourceType = ResourceType.COMPUTE;
        } else if (resourceState instanceof NetworkState) {
            NetworkState network = (NetworkState) resourceState;
            endpointLink = network.endpointLink;
            resourceType = ResourceType.NETWORK;
        } else {
            throw new IllegalArgumentException("Unsupported resource state: "
                    + resourceState.getClass().getName());
        }
        AssertUtil.assertNotNull(endpointLink, "'endpointLink' must be set.");

        return host.sendWithDeferredResult(Operation.createGet(host, endpointLink)
                .setReferer(refererURI))
                .thenCompose(o -> lookUp(host, refererURI,
                        (o.getBody(EndpointState.class)).endpointType,
                        resourceType, null)
                );
    }

    /**
     * Evaluates provided {@code spec}'s target criteria against the specified {@code resourceState}
     * and returns if the  {@link ResourceOperationSpec} is applicable for the given {@link
     * ResourceState}
     * @param resourceState
     *         the resource state for which to check whether given {@code spec} is available
     * @param spec
     *         the {@link ResourceOperationSpec} which to check whether is available for the given
     *         {@code resourceState}
     * @return {@literal true} only in case there is targetCriteria, and the targetCriteria is
     * evaluated to {@literal true} for the given {@code resourceState}
     */
    public static boolean isAvailable(ResourceState resourceState, ResourceOperationSpec spec) {
        AssertUtil.assertNotNull(spec, "'spec' must be set.");
        if (spec.targetCriteria == null) {
            return true;
        }

        ScriptEngine engine = new ScriptEngineManager().getEngineByName(SCRIPT_ENGINE_NAME_JS);

        if (resourceState != null) {
            //Clone original object to avoid changing props of original object from vulnerable
            // targetCriteria
            ResourceState clone = Utils.cloneObject(resourceState);
            engine.getBindings(ScriptContext.ENGINE_SCOPE).put(SCRIPT_CONTEXT_RESOURCE, clone);
        }
        try {
            Object res = engine.eval(spec.targetCriteria);
            if (res instanceof Boolean) {
                return ((Boolean) res).booleanValue();
            } else {
                Utils.log(ResourceOperationUtils.class, "isAvailable",
                        Level.WARNING,
                        "Expect boolean result when evaluate targetCriteria \"%s\" of "
                                + "endpointType: %s, resourceType: %s, operation: %s, "
                                + "adapterReference: %s. Result: %s",
                        spec.targetCriteria,
                        spec.endpointType, spec.resourceType, spec.operation,
                        spec.adapterReference, res);
            }
        } catch (ScriptException e) {
            Utils.log(ResourceOperationUtils.class, "isAvailable",
                    Level.SEVERE,
                    "Cannot evaluate targetCriteria '%s' of "
                            + "endpointType: %s, resourceType: %s, operation: %s, "
                            + "adapterReference: %s. Cause: %s",
                    spec.targetCriteria,
                    spec.endpointType, spec.resourceType, spec.operation, spec.adapterReference,
                    Utils.toString(e));

        }
        return false;
    }
}
