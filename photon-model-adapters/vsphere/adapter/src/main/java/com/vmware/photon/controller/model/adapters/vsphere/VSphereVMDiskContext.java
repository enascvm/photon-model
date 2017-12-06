/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
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
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties
        .DISK_DATASTORE_NAME;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.DISK_FULL_PATH;
import static com.vmware.photon.controller.model.adapters.vsphere
        .VSphereAdapterResourceEnumerationService.PREFIX_DATASTORE;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.DISK_CONTENT_LINK;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.DISK_LINK;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.INSERT_CDROM;
import static com.vmware.photon.controller.model.util.PhotonModelUriUtils.createInventoryUri;
import static com.vmware.xenon.common.Operation.MEDIA_TYPE_APPLICATION_OCTET_STREAM;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperation;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationRequest;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.SessionUtil;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.util.PhotonModelUriUtils;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * VSphereVMDiskContext prepares the context to perform disk day 2 operation on the compute
 */
public class VSphereVMDiskContext {

    // Used for uploading contents into datastore for iso (cd-rom)
    public String datastoreName;
    /**
     * Used to store the calling operation.
     */
    public Operation operation;
    protected ComputeStateWithDescription computePlacementHost;
    protected Set<String> computeGroupLinks;
    protected DiskService.DiskStateExpanded diskState;
    protected List<DiskService.DiskStateExpanded> computeDiskStates;
    protected String contentLink;
    protected byte[] contentToUpload;
    protected ComputeStateWithDescription computeDesc;
    protected ComputeStateWithDescription parentComputeDesc;
    protected VSphereIOThreadPool pool;
    protected AuthCredentialsServiceState vSphereCredentials;
    protected ManagedObjectReference datacenterMoRef;

    protected final TaskManager mgr;
    protected final ResourceOperationRequest request;

    private Consumer<Throwable> errorHandler;

    public VSphereVMDiskContext(TaskManager taskManager, ResourceOperationRequest request,
            Operation op) {
        this(taskManager, request);
        this.operation = op;
    }

    public VSphereVMDiskContext(TaskManager taskManager, ResourceOperationRequest request) {
        this.mgr = taskManager;
        this.request = request;
        this.errorHandler = failure -> {
            Utils.logWarning(
                    "Error while performing disk day 2 operation: %s. for compute %s with disk %s",
                    failure.getMessage(), VSphereVMDiskContext.this.computeDesc.documentSelfLink,
                    VSphereVMDiskContext.this.diskState.documentSelfLink);
            this.mgr.patchTaskToFailure(failure);
        };
    }

    /**
     * Populates the given initial context and invoke the onSuccess handler when built. At every
     * step, if failure occurs the VSphereVMDiskContext's errorHandler is invoked to cleanup.
     */
    protected static void populateVMDiskContextThen(Service service, VSphereVMDiskContext ctx,
            Consumer<VSphereVMDiskContext> onSuccess) {

        if (ctx.computeDesc == null) {
            URI computeUri = createInventoryUri(service.getHost(),
                    UriUtils.extendUriWithQuery(ctx.request.resourceReference,
                            UriUtils.URI_PARAM_ODATA_EXPAND,
                            Boolean.TRUE.toString()));
            AdapterUtils.getServiceState(service, computeUri, op -> {
                ctx.computeDesc = op.getBody(ComputeStateWithDescription.class);
                if (CustomProperties.of(ctx.computeDesc).getString(CustomProperties.MOREF, null) == null) {
                    ctx.fail(new IllegalStateException(
                            String.format("VM Moref is not defined in resource %s",
                                    ctx.computeDesc.documentSelfLink)));
                    return;
                }
                populateVMDiskContextThen(service, ctx, onSuccess);
            }, ctx.errorHandler);
            return;
        }

        if (ctx.diskState == null) {
            URI diskUri = createInventoryUri(service.getHost(),
                    DiskService.DiskStateExpanded.buildUri(UriUtils.buildUri(service.getHost(),
                            ctx.request.payload.get(DISK_LINK))));
            AdapterUtils.getServiceState(service, diskUri, op -> {
                ctx.diskState = op.getBody(DiskService.DiskStateExpanded.class);
                // Disk status should be in AVAILABLE state. If it is CD-ROM then allow attach to
                // the VM. So CD_ROM can be in any status.
                if (!ctx.request.isMockRequest) {
                    if (ctx.request.operation.equals(ResourceOperation.ATTACH_DISK.operation)) {
                        EnumSet<DiskService.DiskType> notSupportedTypes = EnumSet
                                .of(DiskService.DiskType.SSD, DiskService.DiskType.NETWORK);
                        if (notSupportedTypes.contains(ctx.diskState.type)) {
                            ctx.fail(new IllegalStateException(
                                    String.format("Not supported disk type %s.", ctx.diskState.type)));
                            return;
                        }
                        if (ctx.diskState.type == DiskService.DiskType.HDD) {
                            if (ctx.diskState.status != DiskService.DiskStatus.AVAILABLE) {
                                ctx.fail(new IllegalStateException(
                                        String.format(
                                                "Disk %s is not in AVAILABLE status to attach to VM.",
                                                ctx.diskState.documentSelfLink)));
                                return;
                            } else if (
                                    CustomProperties.of(ctx.diskState).getString(DISK_FULL_PATH, null)
                                            == null ||
                                            CustomProperties.of(ctx.diskState)
                                                    .getString(DISK_DATASTORE_NAME, null)
                                                    == null) {
                                ctx.fail(new IllegalStateException(
                                        String.format(
                                                "Disk %s is missing path details to attach to VM.",
                                                ctx.diskState.documentSelfLink)));
                                return;
                            }
                        }
                    } else { // it is detach operation.
                        // Allowing only HDD based disk to be detached
                        if (ctx.diskState.type != DiskService.DiskType.HDD) {
                            ctx.fail(new IllegalStateException(
                                    String.format("Not supported disk type %s for detach.", ctx.diskState.type)));
                            return;
                        }
                        if (ctx.diskState.status != DiskService.DiskStatus.ATTACHED) {
                            ctx.fail(new IllegalStateException(
                                    String.format(
                                            "Disk %s is not in ATTACHED status to detach from VM.",
                                            ctx.diskState.documentSelfLink)));
                            return;
                        }
                    }
                }
                // If the disk type is of CD-ROM, then check it has content to be uploaded. If so
                // fetch the content from the content service
                if (ctx.diskState.type == DiskService.DiskType.CDROM) {
                    String contentUriStr = CustomProperties.of(ctx.diskState).getString(DISK_CONTENT_LINK, null);
                    if (contentUriStr != null) {
                        ctx.contentLink = contentUriStr;
                        URI contentUri = PhotonModelUriUtils.createInventoryUri(service.getHost(),
                                UriUtils.buildUri(service.getHost(), contentUriStr));
                        AdapterUtils.getServiceState(service, contentUri,
                                MEDIA_TYPE_APPLICATION_OCTET_STREAM,
                                op2 -> {
                                    ctx.contentToUpload = op2.getBody(byte[].class);
                                    populateVMDiskContextThen(service, ctx, onSuccess);
                                }, ctx.errorHandler);
                    } else {
                        populateVMDiskContextThen(service, ctx, onSuccess);
                    }
                } else {
                    populateVMDiskContextThen(service, ctx, onSuccess);
                }
            }, ctx.errorHandler);
            return;
        }

        // If it is CD-ROM attach then collect all the disk links objects if insertCDRom is true
        if (ctx.computeDiskStates == null) {
            Boolean insertCdRom = CustomProperties.of(ctx.diskState).getBoolean(INSERT_CDROM, false);
            if (ctx.diskState.type == DiskService.DiskType.CDROM && insertCdRom &&
                    ctx.computeDesc.diskLinks != null && !ctx.computeDesc.diskLinks.isEmpty()) {
                ctx.computeDiskStates = new ArrayList<>(ctx.computeDesc.diskLinks.size());

                // collect disks in parallel
                Stream<Operation> opsGetDisk = ctx.computeDesc.diskLinks.stream()
                        .map(link -> {
                            URI diskStateUri = createInventoryUri(service.getHost(), link);
                            return Operation.createGet(createInventoryUri(service.getHost(),
                                    DiskService.DiskStateExpanded.buildUri(diskStateUri)));
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

                            os.values().forEach(op -> ctx.computeDiskStates.add(op.getBody(DiskService.DiskStateExpanded.class)));
                            populateVMDiskContextThen(service, ctx, onSuccess);
                        });

                join.sendWith(service);
            } else {
                ctx.computeDiskStates = Collections.emptyList();
                populateVMDiskContextThen(service, ctx, onSuccess);
            }
            return;
        }

        if (ctx.parentComputeDesc == null && ctx.computeDesc.parentLink != null) {
            URI computeUri = createInventoryUri(service.getHost(),
                    UriUtils.extendUriWithQuery(
                            UriUtils.buildUri(service.getHost(), ctx.computeDesc.parentLink),
                            UriUtils.URI_PARAM_ODATA_EXPAND,
                            Boolean.TRUE.toString()));

            AdapterUtils.getServiceState(service, computeUri, op -> {
                ctx.parentComputeDesc = op
                        .getBody(ComputeStateWithDescription.class);
                populateVMDiskContextThen(service, ctx, onSuccess);
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
                            populateVMDiskContextThen(service, ctx, onSuccess);
                        });
            } else {
                if (ctx.parentComputeDesc.description.authCredentialsLink == null) {
                    ctx.fail(new IllegalStateException(
                            String.format("authCredentialsLink is not defined in resource %s",
                                    ctx.parentComputeDesc.description.documentSelfLink)));
                    return;
                }

                URI credUri = createInventoryUri(service.getHost(),
                        ctx.parentComputeDesc.description.authCredentialsLink);
                AdapterUtils.getServiceState(service, credUri, op -> {
                    ctx.vSphereCredentials = op
                            .getBody(AuthCredentialsServiceState.class);
                    populateVMDiskContextThen(service, ctx, onSuccess);
                }, ctx.errorHandler);
            }
            return;
        }

        if (ctx.datacenterMoRef == null) {
            try {
                String regionId = ctx.diskState.regionId;
                if (regionId == null || regionId.isEmpty()) {
                    if (ctx.computeDesc.regionId != null) {
                        regionId = ctx.computeDesc.regionId;
                    } else if (ctx.parentComputeDesc.regionId != null) {
                        regionId = ctx.parentComputeDesc.regionId;
                    }
                }
                ctx.datacenterMoRef = VimUtils.convertStringToMoRef(regionId);
            } catch (IllegalArgumentException ex) {
                ctx.fail(ex);
                return;
            }
            populateVMDiskContextThen(service, ctx, onSuccess);
            return;
        }

        if (ctx.computePlacementHost == null) {
            String placementLink = CustomProperties.of(ctx.computeDesc).getString(ComputeProperties
                    .COMPUTE_HOST_LINK_PROP_NAME);
            // compute host link will be not null here.
            URI expandedPlacementUri = UriUtils.extendUriWithQuery(
                    PhotonModelUriUtils.createInventoryUri(service.getHost(), placementLink),
                    UriUtils.URI_PARAM_ODATA_EXPAND,
                    Boolean.TRUE.toString());
            expandedPlacementUri = PhotonModelUriUtils.createInventoryUri(service.getHost(), expandedPlacementUri);

            AdapterUtils.getServiceState(service, expandedPlacementUri, op -> {
                ctx.computePlacementHost = op.getBody(ComputeStateWithDescription.class);
                if (ctx.computePlacementHost.groupLinks != null) {
                    ctx.computeGroupLinks = ctx.computePlacementHost.groupLinks.stream()
                            .filter(link -> link.contains(PREFIX_DATASTORE))
                            .collect(Collectors.toSet());
                }
                populateVMDiskContextThen(service, ctx, onSuccess);
            }, ctx.errorHandler);
            return;
        }

        // populate datastore name
        if (ctx.datastoreName == null) {
            if (ctx.diskState.customProperties != null && ctx.diskState.customProperties
                    .get(DISK_DATASTORE_NAME) != null) {
                ctx.datastoreName = ctx.diskState.customProperties.get(DISK_DATASTORE_NAME);
                populateVMDiskContextThen(service, ctx, onSuccess);
            } else if (ctx.diskState.storageDescription != null) {
                ctx.datastoreName = ctx.diskState.storageDescription.id;
                populateVMDiskContextThen(service, ctx, onSuccess);
            } else if (ctx.diskState.resourceGroupStates != null && !ctx.diskState
                    .resourceGroupStates.isEmpty()) {
                // There will always be only one resource group state existing for a disk
                ResourceGroupService.ResourceGroupState resource = ctx.diskState.resourceGroupStates.iterator().next();
                ClientUtils.getDatastoresForProfile(service, resource.documentSelfLink, ctx
                                .diskState.endpointLink, ctx.diskState.tenantLinks, ctx.errorHandler,
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
                            populateVMDiskContextThen(service, ctx, onSuccess);
                        });
            } else if (ctx.computeGroupLinks != null) {
                // try to get the datastore form the placement link of compute
                String datastoreLink = ctx.computeGroupLinks.iterator().next();
                URI dsUri = PhotonModelUriUtils.createInventoryUri(service.getHost(),
                        UriUtils.buildUri(service.getHost(), datastoreLink));
                AdapterUtils.getServiceState(service, dsUri, op -> {
                    ResourceGroupService.ResourceGroupState rgState = op
                            .getBody(ResourceGroupService.ResourceGroupState.class);
                    ctx.datastoreName = rgState.id;
                    populateVMDiskContextThen(service, ctx, onSuccess);
                }, ctx.errorHandler);
            } else {
                ctx.datastoreName = "";
                populateVMDiskContextThen(service, ctx, onSuccess);
            }
            return;
        }

        // context populated, invoke handler
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
     * Vsphere server url to invoke the operations
     */
    public URI getAdapterManagementReference() {
        if (this.computeDesc.adapterManagementReference != null) {
            return this.computeDesc.adapterManagementReference;
        } else {
            return this.parentComputeDesc.adapterManagementReference;
        }
    }

    /**
     * The returned JoinedCompletionHandler fails this context by invoking the error handler if any
     * error is found in {@link OperationJoin.JoinedCompletionHandler#handle(Map,
     * Map) error map}.
     */
    public OperationJoin.JoinedCompletionHandler failTaskOnError() {
        return (ops, failures) -> {
            if (failures != null && !failures.isEmpty()) {
                this.fail(new Throwable(Utils.toString(failures)));
            }
        };
    }
}
