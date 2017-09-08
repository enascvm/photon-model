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

import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.vmware.photon.controller.model.adapterapi.DiskInstanceRequest;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.util.PhotonModelUriUtils;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * DiskContext holds all the details that are needed to perform disk related operations.
 */
public class DiskContext {

    public DiskService.DiskStateExpanded diskState;
    public String datastoreName;
    public ManagedObjectReference datacenterMoRef;
    public VSphereIOThreadPool pool;
    public AuthCredentialsService.AuthCredentialsServiceState vSphereCredentials;
    public URI adapterManagementReference;
    public final TaskManager mgr;
    public final DiskInstanceRequest diskInstanceRequest;

    private final URI diskReference;

    private Consumer<Throwable> errorHandler;
    private String endpointComputeLink;

    public DiskContext(TaskManager taskManager, DiskInstanceRequest req) {
        this.mgr = taskManager;
        this.diskInstanceRequest = req;
        this.diskReference = req.resourceReference;
        this.errorHandler = failure -> {
            Utils.logWarning("Error while performing disk operation: %s. for disk %s",
                    failure.getMessage(),
                    Utils.toJsonHtml(DiskContext.this.diskReference));
            this.mgr.patchTaskToFailure(failure);
        };
    }

    /**
     * Populates the given initial context and invoke the onSuccess handler when built. At every
     * step, if failure occurs the DiskContext's errorHandler is invoked to cleanup.
     */
    public static void populateContextThen(Service service, DiskContext ctx,
            Consumer<DiskContext> onSuccess) {
        // Step 1: Get disk details
        if (ctx.diskState == null) {
            URI diskUri = DiskService.DiskStateExpanded.buildUri(ctx.diskReference);
            AdapterUtils.getServiceState(service, diskUri, op -> {
                ctx.diskState = op.getBody(DiskService.DiskStateExpanded.class);
                populateContextThen(service, ctx, onSuccess);
            }, ctx.errorHandler);
            return;
        }

        // Step 2: If there is a datastore or storage policy defined, get the datastore name for
        // the disk.
        if (ctx.datastoreName == null && ctx.diskInstanceRequest.requestType
                == DiskInstanceRequest.DiskRequestType.CREATE) {
            if (ctx.diskState.storageDescription != null) {
                ctx.datastoreName = ctx.diskState.storageDescription.id;
                populateContextThen(service, ctx, onSuccess);
            } else if (ctx.diskState.resourceGroupStates != null && !ctx.diskState
                    .resourceGroupStates.isEmpty()) {
                // There will always be only one resource group state existing for a disk
                ResourceGroupState resource = ctx.diskState.resourceGroupStates.iterator().next();
                getDatastoresForProfile(service, resource.documentSelfLink, ctx.diskState
                                .endpointLink, ctx.diskState.tenantLinks, ctx.errorHandler,
                        (result) -> {
                            if (result.documents != null && result.documents.size() > 0) {
                                // pick the first datastore and proceed.
                                ctx.datastoreName = Utils
                                        .fromJson(result.documents.values().iterator().next(),
                                                StorageDescriptionService.StorageDescription.class).id;
                            } else {
                                // Since no result found default to the available datastore.
                                ctx.datastoreName = "";
                            }
                            populateContextThen(service, ctx, onSuccess);
                        });
            } else {
                // Mark empty so that it can fall back to any available datastore from the system.
                ctx.datastoreName = "";
                populateContextThen(service, ctx, onSuccess);
            }
            return;
        }

        // Step 3: Get Credentials
        if (ctx.vSphereCredentials == null) {
            if (ctx.diskState.authCredentialsLink == null || ctx.diskState.authCredentialsLink
                    .isEmpty()) {
                ctx.fail(new IllegalArgumentException("Auth credentials cannot be empty"));
                return;
            }

            URI credUri = UriUtils
                    .buildUri(service.getHost(), ctx.diskState.authCredentialsLink);
            AdapterUtils.getServiceState(service, credUri, op -> {
                ctx.vSphereCredentials = op
                        .getBody(AuthCredentialsService.AuthCredentialsServiceState.class);
                populateContextThen(service, ctx, onSuccess);
            }, ctx.errorHandler);
            return;
        }

        // Step 4: Get the endpoint compute link
        if (ctx.endpointComputeLink == null) {
            URI endpointUri = UriUtils
                    .buildUri(service.getHost(), ctx.diskState.endpointLink);
            AdapterUtils.getServiceState(service, endpointUri, op -> {
                EndpointService.EndpointState endpointState = op
                        .getBody(EndpointService.EndpointState.class);
                ctx.endpointComputeLink = endpointState.computeLink;
                populateContextThen(service, ctx, onSuccess);
            }, ctx.errorHandler);
            return;
        }

        // Step 5: Get the adapter reference to from the endpoint compute link
        if (ctx.adapterManagementReference == null) {
            URI computeUri = UriUtils
                    .buildUri(service.getHost(), ctx.endpointComputeLink);
            AdapterUtils.getServiceState(service, computeUri, op -> {
                ComputeService.ComputeState computeState = op
                        .getBody(ComputeService.ComputeState.class);
                ctx.adapterManagementReference = computeState.adapterManagementReference;
                populateContextThen(service, ctx, onSuccess);
            }, ctx.errorHandler);
            return;
        }

        // Step 6: Obtain reference to the datacenter moref.
        if (ctx.datacenterMoRef == null) {
            try {
                ctx.datacenterMoRef = VimUtils.convertStringToMoRef(ctx.diskState.regionId);
            } catch (IllegalArgumentException ex) {
                ctx.fail(ex);
                return;
            }
        }

        onSuccess.accept(ctx);
    }

    /**
     * Fails the disk operation by invoking the errorHandler.
     */
    public boolean fail(Throwable t) {
        if (t != null) {
            this.errorHandler.accept(t);
            return true;
        } else {
            return false;
        }
    }

    /**
     * The returned JoinedCompletionHandler fails this context by invoking the error handler if any
     * error is found in {@link OperationJoin.JoinedCompletionHandler#handle(java.util.Map,
     * java.util.Map) error map}.
     */
    public OperationJoin.JoinedCompletionHandler failTaskOnError() {
        return (ops, failures) -> {
            if (failures != null && !failures.isEmpty()) {
                this.fail(new Throwable(Utils.toString(failures)));
            }
        };
    }

    private static void getDatastoresForProfile(Service service, String storagePolicyLink,
            String endpointLink, List<String> tenantLinks, Consumer<Throwable> failure,
            Consumer<ServiceDocumentQueryResult> handler) {
        QueryTask.Query.Builder builder = QueryTask.Query.Builder.create()
                .addKindFieldClause(StorageDescriptionService.StorageDescription.class);
        builder.addCollectionItemClause(
                StorageDescriptionService.StorageDescription.FIELD_NAME_GROUP_LINKS,
                storagePolicyLink);

        QueryUtils.addEndpointLink(builder, StorageDescriptionService.StorageDescription.class,
                endpointLink);
        QueryUtils.addTenantLinks(builder, tenantLinks);

        QueryTask task = QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();
        task.querySpec.options = EnumSet
                .of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);
        Operation.createPost(
                PhotonModelUriUtils.createDiscoveryUri(service.getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
                .setBody(task)
                .setConnectionSharing(true)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        service.getHost().log(Level.WARNING, "Error processing task %s",
                                task.documentSelfLink);

                        failure.accept(e);
                        return;
                    }
                    QueryTask body = o.getBody(QueryTask.class);
                    handler.accept(body.results);
                }).sendWith(service);
    }
}

