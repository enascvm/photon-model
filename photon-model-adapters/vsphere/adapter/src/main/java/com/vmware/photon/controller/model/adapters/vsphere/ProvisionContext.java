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

import static com.vmware.photon.controller.model.UriPaths.IAAS_API_ENABLED;
import static com.vmware.photon.controller.model.util.PhotonModelUriUtils.createInventoryUri;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.adapterapi.ResourceRequest;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.DiskService.DiskStateExpanded;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService
        .NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.SessionUtil;
import com.vmware.photon.controller.model.resources.SnapshotService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.OperationJoin.JoinedCompletionHandler;
import com.vmware.xenon.common.QueryResultsProcessor;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

/**
 */
public class ProvisionContext {
    public final URI computeReference;
    public final TaskManager mgr;

    public ComputeStateWithDescription parent;
    public ComputeStateWithDescription child;

    public ImageState image;
    public ManagedObjectReference templateMoRef;
    public ManagedObjectReference computeMoRef;
    public ManagedObjectReference datacenterMoRef; // target datacenter resolved from the target placement

    public ManagedObjectReference snapshotMoRef;
    public ManagedObjectReference referenceComputeMoRef;

    public List<DiskStateExpanded> disks;
    public List<NetworkInterfaceStateWithDetails> nics;
    public AuthCredentialsServiceState vSphereCredentials;

    public VSphereIOThreadPool pool;
    public Consumer<Throwable> errorHandler;

    public ServiceDocument task;
    private final URI provisioningTaskReference;
    public final InstanceRequestType instanceRequestType;
    /**
     * Used to store the calling operation.
     */
    public Operation operation;

    public static class NetworkInterfaceStateWithDetails extends NetworkInterfaceState {
        public NetworkState network;
        public SubnetState subnet;
        public NetworkInterfaceDescription description;
    }

    public ProvisionContext(Service service, ResourceRequest req, Operation op) {
        this(service, req);
        this.operation = op;
    }

    public ProvisionContext(Service service, ResourceRequest req) {
        this.computeReference = req.resourceReference;
        this.instanceRequestType = req instanceof ComputeInstanceRequest ?
                ((ComputeInstanceRequest) req).requestType : null;
        this.mgr = new TaskManager(service, req.taskReference, req.resourceLink());
        this.provisioningTaskReference = req.taskReference;
        this.errorHandler = failure -> {
            Utils.logWarning("Error while provisioning. %s:  \ncompute: %s\nnics: %s\ndisks: %s",
                    failure.getMessage(),
                    Utils.toJsonHtml(ProvisionContext.this.child),
                    Utils.toJsonHtml(ProvisionContext.this.nics),
                    Utils.toJsonHtml(ProvisionContext.this.disks));
            this.mgr.patchTaskToFailure(failure);
        };
    }

    /**
     * Populates the given initial context and invoke the onSuccess handler when built. At every step,
     * if failure occurs the ProvisionContext's errorHandler is invoked to cleanup.
     *
     * @param ctx
     * @param onSuccess
     */
    public static void populateContextThen(Service service, ProvisionContext ctx,
            Consumer<ProvisionContext> onSuccess) {
        // TODO fetch all required state in parallel using OperationJoin.
        if (ctx.child == null) {
            URI computeUri = UriUtils
                    .extendUriWithQuery(ctx.computeReference,
                            UriUtils.URI_PARAM_ODATA_EXPAND,
                            Boolean.TRUE.toString());
            computeUri = createInventoryUri(service.getHost(), computeUri);

            AdapterUtils.getServiceState(service, computeUri, op -> {
                ctx.child = op.getBody(ComputeStateWithDescription.class);
                populateContextThen(service, ctx, onSuccess);
            }, ctx.errorHandler);
            return;
        }

        String templateLink = VimUtils.firstNonNull(
                CustomProperties.of(ctx.child).getString(CustomProperties.TEMPLATE_LINK),
                CustomProperties.of(ctx.child.description).getString(CustomProperties.TEMPLATE_LINK)
        );

        // if it is create request and there is template link, fetch the template
        // in all other cases ignore the presence of the template
        if (templateLink != null && ctx.templateMoRef == null
                && ctx.instanceRequestType == InstanceRequestType.CREATE) {
            URI computeUri = createInventoryUri(service.getHost(), templateLink);

            AdapterUtils.getServiceState(service, computeUri, op -> {
                ImageState body = op.getBody(ImageState.class);
                ctx.templateMoRef = CustomProperties.of(body).getMoRef(CustomProperties.MOREF);
                if (ctx.templateMoRef == null) {
                    String msg = String
                            .format("The linked template %s does not contain a MoRef in its custom properties",
                                    templateLink);
                    ctx.fail(new IllegalStateException(msg));
                } else {
                    populateContextThen(service, ctx, onSuccess);
                }
            }, ctx.errorHandler);
            return;
        }

        //For creation based on linked clone of snapshot
        if (ctx.snapshotMoRef == null) {
            String snapshotLink = CustomProperties.of(ctx.child).getString(CustomProperties.SNAPSHOT_LINK);

            if (snapshotLink != null && ctx.instanceRequestType == InstanceRequestType.CREATE) {
                URI snapshotUri = createInventoryUri(service.getHost(), snapshotLink);

                AdapterUtils.getServiceState(service, snapshotUri, op -> {
                    SnapshotService.SnapshotState snapshotState = op.getBody(SnapshotService.SnapshotState.class);

                    ctx.snapshotMoRef = CustomProperties.of(snapshotState).getMoRef(CustomProperties.MOREF);

                    if (ctx.snapshotMoRef == null) {
                        String msg = String
                                .format("The linked clone snapshot %s does not contain a MoRef in its custom properties",
                                    snapshotLink);
                        ctx.fail(new IllegalStateException(msg));
                    } else {
                        //Retrieve the reference endpoint moref from which the linkedclone has to be created.
                        String refComputeLink = snapshotState.computeLink;

                        if (refComputeLink != null) {
                            URI refComputeUri = createInventoryUri(service.getHost(), refComputeLink);

                            AdapterUtils.getServiceState(service, refComputeUri, opCompute -> {
                                ComputeStateWithDescription refComputeState = opCompute.getBody(ComputeStateWithDescription.class);

                                ctx.referenceComputeMoRef = CustomProperties.of(refComputeState).getMoRef(CustomProperties.MOREF);

                                if (ctx.referenceComputeMoRef == null) {
                                    String msg = String
                                            .format("The linked clone endpoint ref %s does not contain a MoRef in its custom properties",
                                                refComputeLink);
                                    ctx.fail(new IllegalStateException(msg));
                                }
                                populateContextThen(service, ctx, onSuccess);
                            }, ctx.errorHandler);
                        }
                    }
                }, ctx.errorHandler);
                return;
            }
        }

        if (ctx.parent == null && ctx.child.parentLink != null) {
            URI computeUri = UriUtils
                    .extendUriWithQuery(
                            UriUtils.buildUri(service.getHost(), ctx.child.parentLink),
                            UriUtils.URI_PARAM_ODATA_EXPAND,
                            Boolean.TRUE.toString());

            computeUri = createInventoryUri(service.getHost(), computeUri);

            AdapterUtils.getServiceState(service, computeUri, op -> {
                ctx.parent = op.getBody(ComputeStateWithDescription.class);
                populateContextThen(service, ctx, onSuccess);
            }, ctx.errorHandler);
            return;
        }

        if (ctx.vSphereCredentials == null) {
            if (IAAS_API_ENABLED) {
                if (ctx.operation == null) {
                    ctx.fail(new IllegalArgumentException("Caller operation cannot be empty"));
                    return;
                }
                SessionUtil.retrieveExternalToken(service, ctx.operation
                        .getAuthorizationContext()).whenComplete((authCredentialsServiceState,
                        throwable) -> {
                            if (throwable != null) {
                                ctx.errorHandler.accept(throwable);
                                return;
                            }
                            ctx.vSphereCredentials = authCredentialsServiceState;
                            populateContextThen(service, ctx, onSuccess);
                        });
            } else {
                if (ctx.parent.description.authCredentialsLink == null) {
                    ctx.fail(new IllegalStateException(
                            "authCredentialsLink is not defined in resource "
                                    + ctx.parent.description.documentSelfLink));
                    return;
                }

                URI credUri = createInventoryUri(service.getHost(),
                        ctx.parent.description.authCredentialsLink);
                AdapterUtils.getServiceState(service, credUri, op -> {
                    ctx.vSphereCredentials = op.getBody(AuthCredentialsServiceState.class);
                    populateContextThen(service, ctx, onSuccess);
                }, ctx.errorHandler);
            }
            return;
        }

        if (ctx.task == null) {
            // Verify if this makes sense? These tasks should always be local to deployment?
            AdapterUtils.getServiceState(service, ctx.provisioningTaskReference, op -> {
                ctx.task = op.getBody(ServiceDocument.class);
                populateContextThen(service, ctx, onSuccess);
            }, ctx.errorHandler);
            return;
        }

        if (ctx.nics == null && ctx.instanceRequestType == InstanceRequestType.CREATE) {
            if (ctx.child.networkInterfaceLinks == null || ctx.child.networkInterfaceLinks
                    .isEmpty()) {
                ctx.nics = Collections.emptyList();
                populateContextThen(service, ctx, onSuccess);
                return;
            }

            ctx.nics = new ArrayList<>();

            Query query = Query.Builder.create()
                    .addInClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                            ctx.child.networkInterfaceLinks)
                    .build();

            QueryTask qt = QueryTask.Builder.createDirectTask()
                    .setQuery(query)
                    .addOption(QueryOption.EXPAND_CONTENT)
                    .addOption(QueryOption.EXPAND_LINKS)
                    .addOption(QueryOption.SELECT_LINKS)
                    .addOption(QueryOption.INDEXED_METADATA)
                    .addLinkTerm(NetworkInterfaceState.FIELD_NAME_NETWORK_LINK)
                    .addLinkTerm(NetworkInterfaceState.FIELD_NAME_SUBNET_LINK)
                    .addLinkTerm(NetworkInterfaceState.FIELD_NAME_DESCRIPTION_LINK)
                    .build();

            QueryUtils.startInventoryQueryTask(service, qt)
                    .whenComplete((o, e) -> {
                        if (e != null) {
                            ctx.errorHandler.accept(e);
                            return;
                        }
                        QueryResultsProcessor processor = QueryResultsProcessor.create(o);
                        for (NetworkInterfaceStateWithDetails nic : processor
                                .documents(NetworkInterfaceStateWithDetails.class)) {

                            if (nic.networkInterfaceDescriptionLink != null) {
                                NetworkInterfaceDescription desc =
                                        processor.selectedDocument(
                                                nic.networkInterfaceDescriptionLink,
                                                NetworkInterfaceDescription.class);
                                nic.description = desc;
                            }

                            if (nic.subnetLink != null) {
                                SubnetState subnet = processor
                                        .selectedDocument(nic.subnetLink, SubnetState.class);
                                nic.subnet = subnet;
                            }
                            if (nic.networkLink != null) {
                                NetworkState network = processor
                                        .selectedDocument(nic.networkLink, NetworkState.class);
                                nic.network = network;
                            }

                            ctx.nics.add(nic);
                        }

                        populateContextThen(service, ctx, onSuccess);
                    });

            return;
        }

        if (ctx.computeMoRef == null) {
            String placementLink = CustomProperties.of(ctx.child)
                    .getString(ComputeProperties.PLACEMENT_LINK);

            if (placementLink == null) {
                Exception error = new IllegalStateException(
                        "A Compute resource must have a " + ComputeProperties.PLACEMENT_LINK
                                + " custom property");
                ctx.fail(error);
                return;
            }

            URI expandedPlacementUri = UriUtils.extendUriWithQuery(
                    createInventoryUri(service.getHost(), placementLink),
                    UriUtils.URI_PARAM_ODATA_EXPAND,
                    Boolean.TRUE.toString());

            expandedPlacementUri = createInventoryUri(service.getHost(), expandedPlacementUri);

            Operation.createGet(expandedPlacementUri).setCompletion((o, e) -> {
                if (e != null) {
                    ctx.fail(e);
                    return;
                }

                ComputeStateWithDescription host = o.getBody(ComputeStateWithDescription.class);

                // extract the target resource pool for the placement
                CustomProperties hostCustomProperties = CustomProperties.of(host);

                ctx.computeMoRef = hostCustomProperties.getMoRef(CustomProperties.MOREF);
                if (ctx.computeMoRef == null) {
                    Exception error = new IllegalStateException(String.format(
                            "Compute @ %s does not contain a %s custom property",
                            placementLink,
                            CustomProperties.MOREF));
                    ctx.fail(error);
                    return;
                }

                if (host.description.regionId == null) {
                    Exception error = new IllegalStateException(String.format(
                            "Compute @ %s does not specify a region", placementLink));
                    ctx.fail(error);
                    return;
                }
                try {
                    ctx.datacenterMoRef = VimUtils.convertStringToMoRef(host.description.regionId);
                } catch (IllegalArgumentException ex) {
                    ctx.fail(ex);
                    return;
                }

                populateContextThen(service, ctx, onSuccess);
            }).sendWith(service);

            return;
        }

        if (ctx.disks == null) {
            // no disks attached
            if (ctx.child.diskLinks == null || ctx.child.diskLinks
                    .isEmpty()) {
                ctx.disks = Collections.emptyList();
                populateContextThen(service, ctx, onSuccess);
                return;
            }

            ctx.disks = new ArrayList<>(ctx.child.diskLinks.size());

            // collect disks in parallel
            Stream<Operation> opsGetDisk = ctx.child.diskLinks.stream()
                    .map(link -> {
                        URI diskStateUri = UriUtils.buildUri(service.getHost(), link);
                        return Operation.createGet(createInventoryUri(service.getHost(),
                                DiskStateExpanded.buildUri(diskStateUri)));
                    });

            OperationJoin join = OperationJoin.create(opsGetDisk)
                    .setCompletion((os, errors) -> {
                        if (errors != null && !errors.isEmpty()) {
                            // fail on first error
                            ctx.errorHandler
                                    .accept(new IllegalStateException("Cannot get disk state",
                                            errors.values().iterator().next()));
                            return;
                        }

                        os.values().forEach(op -> ctx.disks.add(op.getBody(DiskStateExpanded.class)));

                        populateContextThen(service, ctx, onSuccess);
                    });

            join.sendWith(service);
            return;
        }

        String libraryItemLink = VimUtils.firstNonNull(
                CustomProperties.of(ctx.child).getString(CustomProperties.LIBRARY_ITEM_LINK),
                CustomProperties.of(ctx.child.description)
                        .getString(CustomProperties.LIBRARY_ITEM_LINK)
        );
        if (libraryItemLink != null && ctx.image == null
                && ctx.instanceRequestType == InstanceRequestType.CREATE) {
            URI libraryUri = createInventoryUri(service.getHost(), libraryItemLink);

            AdapterUtils.getServiceState(service, libraryUri, op -> {
                ImageState body = op.getBody(ImageState.class);
                ctx.image = body;
                populateContextThen(service, ctx, onSuccess);
            }, ctx.errorHandler);
            return;
        }


        if (ctx.instanceRequestType == InstanceRequestType.CREATE) {
            if (ctx.image == null) {
                DiskStateExpanded bootDisk = ctx.disks.stream()
                        .filter(d -> d.imageLink != null)
                        .findFirst().orElse(null);

                if (bootDisk != null) {
                    URI bootImageRef = createInventoryUri(service.getHost(), bootDisk.imageLink);
                    AdapterUtils.getServiceState(service, bootImageRef, op -> {
                        ImageState body = op.getBody(ImageState.class);
                        ctx.image = body;
                        populateContextThen(service, ctx, onSuccess);
                    }, ctx.errorHandler);
                    return;
                }
            }
        }

        // Order networks by deviceIndex so that nics are created in the same order
        if (ctx.nics != null) {
            // configure network
            ctx.nics.sort((lnis, rnis) -> {
                return Integer.compare(lnis.deviceIndex, rnis.deviceIndex);
            });
        }
        // context populated, invoke handler
        onSuccess.accept(ctx);
    }

    /**
     * The returned JoinedCompletionHandler fails this context by invoking the error handler if any
     * error is found in {@link JoinedCompletionHandler#handle(java.util.Map, java.util.Map) error map}.
     */
    public JoinedCompletionHandler failTaskOnError() {
        return (ops, failures) -> {
            if (failures != null && !failures.isEmpty()) {
                Throwable firstError = failures.values().iterator().next();
                this.fail(firstError);
            }
        };
    }

    /**
     * Fails the provisioning by invoking the errorHandler.
     *
     * @param t
     * @return tre if t is defined, false otherwise
     */
    public boolean fail(Throwable t) {
        if (t != null) {
            this.errorHandler.accept(t);
            return true;
        } else {
            return false;
        }
    }

    public void failWithMessage(String msg, Throwable t) {
        this.fail(new Exception(msg, t));
    }

    public URI getAdapterManagementReference() {
        if (this.child.adapterManagementReference != null) {
            return this.child.adapterManagementReference;
        } else {
            return this.parent.adapterManagementReference;
        }
    }

    public void failWithMessage(String msg) {
        fail(new IllegalStateException(msg));
    }

    //    public JoinedCompletionHandler logOnError() {
    //        return (ops, failures) -> {
    //            if (failures != null && !failures.isEmpty()) {
    //                logger.info(
    //                        "Ignoring errors while completing task " + this.task.documentSelfLink + ": "
    //                                + failures.values());
    //            }
    //        };
    //    }
}
