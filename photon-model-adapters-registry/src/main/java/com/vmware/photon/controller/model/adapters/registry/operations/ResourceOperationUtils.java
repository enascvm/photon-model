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

import static com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster.SELF_SERVICE;

import java.net.URI;
import java.util.Arrays;
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
import com.vmware.photon.controller.model.util.ClusterUtil;
import com.vmware.photon.controller.model.util.ServiceEndpointLocator;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
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

    public static enum TargetCriteria {
        RESOURCE_POWER_STATE_ON("resource.powerState == 'ON'"),

        // ComputeProperties.CUSTOM_PROP_COMPUTE_HAS_SNAPSHOTS = "__hasSnapshot". So "__hasSnapshot"
        // is used here. Any modification in that custom property requires manual changes here as well.
        RESOURCE_HAS_SNAPSHOTS("resource.customProperties != null && " +
                " (resource.customProperties.get('__hasSnapshot') != null) && " +
                "('true' == (resource.customProperties.get('__hasSnapshot')).toLowerCase())");

        private final String criteria;

        private TargetCriteria(String criteria) {
            this.criteria = criteria;
        }

        public String getCriteria() {
            return this.criteria;
        }
    }

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
     * @param queryTaskTenantLinks
     *         tenant links used for the QueryTask
     * @param authorizationContext
     *         authorization context that will be used for operations (if set to null the context
     *         will not be changed)
     * @return
     */
    public static DeferredResult<ResourceOperationSpec> lookUpByEndpointType(
            ServiceHost host,
            URI refererURI,
            String endpointType,
            ResourceType resourceType,
            String operation,
            List<String> queryTaskTenantLinks,
            AuthorizationContext authorizationContext) {

        return lookUp(host, refererURI, endpointType, resourceType, operation,
                queryTaskTenantLinks, authorizationContext)
                .thenApply(specs -> specs.isEmpty() ? null : specs.iterator().next());
    }

    /**
     * Lookup for {@link ResourceOperationSpec} by given {@code resourceState} and {@code operation}
     * @param host
     *         host to use to create operation
     * @param refererURI
     *         the referer to use when send the operation
     * @param resourceState
     *         the resource state specialization for which to lookup the spec
     * @param operation
     *         the operation
     * @param authorizationContext
     *         authorization context that will be used for operations (if set to null the context
     *         will not be changed)
     * @return
     */
    public static <T extends ResourceState> DeferredResult<List<ResourceOperationSpec>> lookupByResourceState(
            ServiceHost host,
            URI refererURI,
            T resourceState,
            String operation,
            AuthorizationContext authorizationContext) {
        AssertUtil.assertNotNull(resourceState, "'resourceState' must be set.");

        String endpointLink = null;
        // All endpoint links have to be of the same type, so it's enough to get the first one
        if (resourceState.endpointLinks != null && !resourceState.endpointLinks.isEmpty()) {
            endpointLink = resourceState.endpointLinks.iterator().next();
        }

        ResourceType resourceType;
        if (resourceState instanceof ComputeState) {
            ComputeState compute = (ComputeState) resourceState;
            endpointLink = endpointLink == null ? compute.endpointLink : endpointLink;
            resourceType = ResourceType.COMPUTE;
        } else if (resourceState instanceof NetworkState) {
            NetworkState network = (NetworkState) resourceState;
            endpointLink = endpointLink == null ? network.endpointLink : endpointLink;
            resourceType = ResourceType.NETWORK;
        } else {
            throw new IllegalArgumentException("Unsupported resource state: "
                    + resourceState.getClass().getName());
        }
        AssertUtil.assertNotNull(endpointLink, " must be set.");

        return host.sendWithDeferredResult(
                Operation.createGet(host, endpointLink).setReferer(refererURI),
                EndpointState.class)
                .thenCompose(ep -> lookUp(
                        host, refererURI, (ep).endpointType, resourceType, operation,
                        resourceState.tenantLinks, authorizationContext)
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

    /**
     * A generic utility method to register any Day 2 Operation service/adapter with the framework
     * as a ResourceOperationSpecService. It accepts a list of {@code specs} which a service can
     * handle as input and submits them to the ResourceOperationSpecService's Factory. This call
     * should generally be part of handleStart method of the adapter/service, preferably near the
     * end after any service specification configuration settings.
     * @param service
     *         the resourceOperation service/adapter
     * @param handler
     *         the operation completion handler for making the success/failure actions
     * @param specs
     *         list of intended the ResourceOperationSpec's to register with the service
     */
    public static void registerResourceOperation(Service service, CompletionHandler handler,
            ResourceOperationSpec... specs) {

        registerResourceOperations(service.getHost(), null, service.getSelfLink(), specs)
                .whenComplete((ops, err) -> {
                    if (err != null) {
                        service.getHost().log(Level.SEVERE, "Error: %s", Utils.toString(err));
                        handler.handle(ops.iterator().next(), err);
                    } else {
                        service.getHost().log(Level.FINE,
                                "Successfully registered operations.");
                        handler.handle(null, null);
                    }
                });
    }

    /**
     * A generic utility method to register any Day 2 Operation service/adapter with the framework
     * as a ResourceOperationSpecService. It accepts a list of {@code specs} which a service can
     * handle as input and submits them to the ResourceOperationSpecService's Factory. This call
     * should generally be part of handleStart method of the adapter/service, preferably near the
     * end after any service specification configuration settings.
     * @param host
     *         the host of the Service
     * @param locator
     *         Locator referencing the ResourceOperationSpecService's location can be @null if
     *         the registry is on the same host
     * @param adapterLink
     *         the adapter's SELF_LINK
     * @param specs
     *         list of intended the ResourceOperationSpec's to register with the service
     */
    public static void registerResourceOperation(ServiceHost host,
            ServiceEndpointLocator locator,
            String adapterLink,
            ResourceOperationSpec... specs) {

        registerResourceOperations(host, locator, adapterLink, specs)
                .whenComplete((op, err) -> {
                    if (err != null) {
                        host.log(Level.SEVERE, "Error: %s", Utils.toString(err));
                    } else {
                        host.log(Level.FINE, "Successfully registered operations.");
                    }
                }
            );
    }

    private static DeferredResult<List<Operation>> registerResourceOperations(ServiceHost host,
            ServiceEndpointLocator locator,
            String adapterLink,
            ResourceOperationSpec... specs) {

        if (specs == null || specs.length == 0) {
            host.log(Level.FINE,
                    "No ResourceOperationSpec to register by %s",
                    adapterLink);
            return DeferredResult.completed(null);
        }

        List<DeferredResult<Operation>> operations = Arrays.stream(specs)
                .map(spec -> {
                    spec.adapterReference = buildPublicAdapterUri(host, adapterLink);
                    Operation op = createOperation(host, locator, spec);
                    return host.sendWithDeferredResult(op);
                })
                .collect(Collectors.toList());

        return DeferredResult.allOf(operations);
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
     * @param queryTaskTenantLinks
     *         tenant links used for the QueryTask
     * @param authorizationContext
     *         authorization context that will be used for operations (if set to null the context
     *         will not be changed)
     * @return
     */
    private static DeferredResult<List<ResourceOperationSpec>> lookUp(
            ServiceHost host,
            URI refererURI,
            String endpointType,
            ResourceType resourceType,
            String operation,
            List<String> queryTaskTenantLinks,
            AuthorizationContext authorizationContext) {

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
                host,
                query,
                ResourceOperationSpec.class,
                null)
                .setQueryTaskTenantLinks(queryTaskTenantLinks)
                .setAuthorizationContext(authorizationContext);

        if (operation != null) {
            //resource operation spec id and selfLink are built from the endpoint type, resource
            // type and operation id, so the query result is guaranteed to return at most 1 element
            top.setMaxResultsLimit(1);
        }
        top.setReferer(refererURI);
        return top.collectDocuments(Collectors.toList());
    }

    private static Operation createOperation(ServiceHost host, ResourceOperationSpec spec) {
        return createOperation(host, null, spec);
    }

    private static Operation createOperation(ServiceHost host, ServiceEndpointLocator locator,
            ResourceOperationSpec spec) {
        host.log(Level.FINE,
                "Going to register Resource Operation name=%s, operation='%s'",
                spec.name, spec.operation);

        URI uri = UriUtils.buildUri(ClusterUtil.getClusterUri(host, locator),
                ResourceOperationSpecService.FACTORY_LINK);

        return Operation.createPost(uri)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setReferer(host.getUri())
                .setBody(spec);
    }

    /**
     * Handles Resource Operation Registration during an adapter's start
     *
     * @param service the Adapter Service
     * @param startPost the startPost operation
     * @param registerResourceOperation should the Resource Operation Specs be registered
     * @param resourceOperationSpecs Resource Operation Specs to register
     */
    public static void handleAdapterResourceOperationRegistration(Service service, Operation startPost,
            boolean registerResourceOperation, ResourceOperationSpec... resourceOperationSpecs) {
        if (registerResourceOperation) {
            Operation.CompletionHandler handler = (op, exc) -> {
                if (exc != null) {
                    startPost.fail(exc);
                } else {
                    startPost.complete();
                }
            };
            ResourceOperationUtils.registerResourceOperation(service, handler,
                    resourceOperationSpecs);
        } else {
            startPost.complete();
        }
    }

    /**
     * Builds an adapter reference using {@value ServiceHost#LOCAL_HOST}.
     * <p>NOTE: <b>use with care!</b>
     */
    public static URI buildAdapterUri(ServiceHost host, String path) {
        return buildAdapterUri(host.getPort(), path);
    }

    /**
     * Builds an adapter reference using {@value ServiceHost#LOCAL_HOST}.
     * <p>NOTE: <b>use with care!</b>
     */
    public static URI buildAdapterUri(int port, String path) {
        return buildAdapterUri(ServiceHost.LOCAL_HOST, port, path);
    }

    /**
     * Builds an public adapter reference using
     * {@link com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster#SELF_SERVICE}.
     *
     * If SELF_SERVICE is not defined it will use {@link ResourceOperationUtils#buildAdapterUri(ServiceHost, String)}.
     * <p>
     * <p>NOTE: <b>use with care!</b>
     */
    public static URI buildPublicAdapterUri(ServiceHost host, String path) {

        if (ClusterUtil.isClusterDefined(SELF_SERVICE)) {
            return UriUtils.buildUri(ClusterUtil.getClusterUri(host, SELF_SERVICE), path);
        }

        return buildAdapterUri(host, path);
    }

    /**
     * Builds an adapter reference using the given host URI.
     */
    public static URI buildAdapterUri(String host, int port, String path) {
        return UriUtils.buildUri(host, port, path, null);
    }

    /**
     * Builds an adapter reference using the given host URI.
     */
    public static URI buildAdapterUri(String scheme, String host, int port, String path) {
        return UriUtils.buildUri(scheme, host, port, path, null);
    }

}
