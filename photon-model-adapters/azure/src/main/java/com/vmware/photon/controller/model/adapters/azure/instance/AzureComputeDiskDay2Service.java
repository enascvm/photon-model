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

package com.vmware.photon.controller.model.adapters.azure.instance;

import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.DISK_CONTROLLER_NUMBER;
import static com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationUtils.handleAdapterResourceOperationRegistration;
import static com.vmware.photon.controller.model.util.PhotonModelUriUtils.createInventoryUri;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.microsoft.azure.management.compute.DataDisk;
import com.microsoft.azure.management.compute.Disk;
import com.microsoft.azure.management.compute.VirtualMachine;

import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureBaseAdapterContext;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperation;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationRequest;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.BaseAdapterStage;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Service to attach/detach a disk to VM
 */
public class AzureComputeDiskDay2Service extends StatelessService {

    public static final String SELF_LINK = AzureUriPaths.AZURE_DISK_DAY2_ADAPTER;

    private boolean registerResourceOperation;

    /**
     * Azure Disk request context.
     */
    public static class AzureComputeDiskDay2Context
            extends AzureBaseAdapterContext<AzureComputeDiskDay2Context> {

        public final ResourceOperationRequest request;

        public ComputeState computeState;
        public DiskState diskState;
        public EndpointState endpointState;

        private VirtualMachine provisionedVm;
        private Disk provisionedDisk;

        public AzureComputeDiskDay2Context(
                StatelessService service,
                ResourceOperationRequest request) {

            super(service, request);

            this.request = request;
        }

        /**
         * Get the authentication object using endpoint authentication.
         */
        @Override
        protected URI getParentAuthRef(AzureComputeDiskDay2Context context) {
            return UriUtils.buildUri(createInventoryUri(context.service.getHost(),
                    context.endpointState.authCredentialsLink));
        }

    }

    public static class AzureComputeDiskDay2FactoryService extends FactoryService {
        private boolean registerResourceOperation;

        public AzureComputeDiskDay2FactoryService(boolean registerResourceOperation) {
            super(ServiceDocument.class);
            this.registerResourceOperation = registerResourceOperation;
        }

        @Override
        public Service createServiceInstance() {
            return new AzureComputeDiskDay2Service(this.registerResourceOperation);
        }
    }

    public AzureComputeDiskDay2Service() {
        this(true);
    }

    public AzureComputeDiskDay2Service(boolean registerResourceOperation) {
        this.registerResourceOperation = registerResourceOperation;
    }

    @Override
    public void handleStart(Operation startPost) {
        handleAdapterResourceOperationRegistration(this, startPost,
                this.registerResourceOperation,
                getResourceOperationSpecs());
    }

    @Override
    public void handleStop(Operation op) {
        super.handleStop(op);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("Body is required"));
            return;
        }

        ResourceOperationRequest request = op.getBody(ResourceOperationRequest.class);

        try {
            validateInputRequest(request);
        } catch (Exception ex) {
            op.fail(ex);
            return;
        }
        op.complete();

        // initialize context object
        AzureComputeDiskDay2Context context = new AzureComputeDiskDay2Context(this, request);

        DeferredResult<AzureComputeDiskDay2Context> execution = DeferredResult.completed(context);

        // common steps for both attach / detach ops
        execution = execution.thenCompose(this::getComputeState)
                .thenCompose(this::getDiskState)
                .thenCompose(this::configureAzureSDKClient)
                .thenCompose(this::getAzureVirtualMachine);

        if (request.operation.equals(ResourceOperation.ATTACH_DISK.operation)) {

            execution = execution.thenCompose(this::getAzureDisk)
                    .thenCompose(this::performDiskAttachment)
                    .thenCompose(this::updateComputeStateAndDiskState);

        } else if (request.operation.equals(ResourceOperation.DETACH_DISK.operation)) {

            execution = execution.thenCompose(this::performDiskDetach)
                    .thenCompose(this::updateComputeStateAndDiskState);
        }

        execution.whenComplete((o, ex) -> context.finish(ex));
    }

    /**
     * Validate input request before performing the disk day 2 operation.
     */
    private void validateInputRequest(ResourceOperationRequest request) {
        if (request.operation == null) {
            throw new IllegalArgumentException(
                    "Operation cannot be empty");
        }

        final Collection<String> operations = Arrays.asList(
                ResourceOperation.ATTACH_DISK.operation,
                ResourceOperation.DETACH_DISK.operation);
        if (!operations.contains(request.operation)) {
            throw new IllegalArgumentException(
                    "Operation value should be any of " + operations);
        }

        if (request.resourceReference == null) {
            throw new IllegalArgumentException(
                    "Compute resource reference to perform the disk operation cannot be empty");
        }

        if (request.payload == null
                || request.payload.get(PhotonModelConstants.DISK_LINK) == null) {
            throw new IllegalArgumentException(
                    "Disk reference to attach to Compute cannot be empty");
        }
    }

    /**
     * Get ComputeState object of a VM using resource reference and save in context
     */
    private DeferredResult<AzureComputeDiskDay2Context> getComputeState(
            AzureComputeDiskDay2Context context) {
        return this.sendWithDeferredResult(
                Operation.createGet(
                        createInventoryUri(this.getHost(), context.request.resourceReference)),
                ComputeState.class)
                .thenApply(computeState -> {
                    context.computeState = computeState;
                    return context;
                });
    }

    /**
     * Get DiskState object of disk using disk reference and save in context
     */
    private DeferredResult<AzureComputeDiskDay2Context> getDiskState(
            AzureComputeDiskDay2Context context) {
        return this.sendWithDeferredResult(
                Operation.createGet(
                        createInventoryUri(this.getHost(),
                        context.request.payload.get(PhotonModelConstants.DISK_LINK))),
                DiskState.class)
                .thenApply(diskState -> {
                    context.diskState = diskState;
                    return context;
                });
    }

    /**
     * Method to detach Azure disk from VM
     */
    private DeferredResult<AzureComputeDiskDay2Context> performDiskDetach(
            AzureComputeDiskDay2Context context) {

        if (context.request.isMockRequest) {
            return DeferredResult.completed(context);
        }

        final String msg = "Detaching disk with name [" + context.diskState.name +
                "]" + "from machine with name [" + context.computeState.name + "]";

        AzureDeferredResultServiceCallback<VirtualMachine> handler = new AzureDeferredResultServiceCallback<VirtualMachine>(
                this, msg) {

            @Override
            protected DeferredResult<VirtualMachine> consumeSuccess(VirtualMachine vm) {
                String msg = String.format("[AzureComputeDiskDay2Service] Successfully "
                        + "detached volume %s from instance %s",
                        context.diskState.name, context.computeState.name);
                this.service.logInfo(() -> msg);
                return DeferredResult.completed(vm);
            }

            @Override
            protected Throwable consumeError(Throwable exc) {
                String msg = String.format(
                        "[AzureComputeDiskDay2Service] Failure in detaching volume %s from instance %s : %s",
                        context.diskState.name, context.computeState.name, exc);
                this.service.logSevere(msg);
                return exc;
            }
        };

        if (context.diskState.customProperties == null) {
            return DeferredResult.failed(new Throwable("LUN number not found in disk state"));
        } else {
            context.provisionedVm.update()
                    .withoutDataDisk(Integer.parseInt(context.diskState.customProperties
                            .get(AzureConstants.DISK_CONTROLLER_NUMBER)))
                    .applyAsync(handler);
        }

        return handler.toDeferredResult().thenApply(virtualMachine -> context);

    }

    /**
     * Method to fetch Azure VM reference
     */
    private DeferredResult<AzureComputeDiskDay2Context> getAzureVirtualMachine(
            AzureComputeDiskDay2Context context) {

        if (context.request.isMockRequest) {
            return DeferredResult.completed(context);
        }

        String instanceId = context.computeState.id;
        if (instanceId == null) {
            String msg = "Compute Id cannot be null";
            this.logSevere("[AzureComputeDiskDay2Service] " + msg);
            return DeferredResult.failed(new IllegalArgumentException(msg));
        }

        final String msg = "Getting Virtual Machine details for [" + context.computeState.name +
                "]";

        AzureDeferredResultServiceCallback<VirtualMachine> handler = new AzureDeferredResultServiceCallback<VirtualMachine>(
                this, msg) {

            @Override
            protected DeferredResult<VirtualMachine> consumeSuccess(VirtualMachine vm) {
                context.provisionedVm = vm;
                return DeferredResult.completed(vm);
            }
        };

        context.azureSdkClients.getComputeManager().virtualMachines()
                .getByIdAsync(instanceId, handler);

        return handler.toDeferredResult().thenApply(virtualMachine -> context);
    }

    /**
     * Method to fetch Azure disk reference
     */
    private DeferredResult<AzureComputeDiskDay2Context> getAzureDisk(
            AzureComputeDiskDay2Context context) {

        if (context.request.isMockRequest) {
            return DeferredResult.completed(context);
        }

        String diskId = context.diskState.id;
        if (diskId == null) {
            String msg = "Disk Id cannot be null";
            this.logSevere("[AzureComputeDiskDay2Service] " + msg);
            return DeferredResult.failed(new IllegalArgumentException(msg));
        }

        final String msg = "Getting Disk details for [" + context.diskState.name +
                "]";

        AzureDeferredResultServiceCallback<Disk> handler = new AzureDeferredResultServiceCallback<Disk>(
                this, msg) {

            @Override
            protected DeferredResult<Disk> consumeSuccess(Disk disk) {
                context.provisionedDisk = disk;
                return DeferredResult.completed(disk);
            }
        };

        context.azureSdkClients.getComputeManager().disks()
                .getByIdAsync(diskId, handler);

        return handler.toDeferredResult().thenApply(disk -> context);
    }

    /**
     * Method to attach Azure disk to VM
     */
    private DeferredResult<AzureComputeDiskDay2Context> performDiskAttachment(
            AzureComputeDiskDay2Context context) {

        if (context.request.isMockRequest) {
            return DeferredResult.completed(context);
        }

        final String msg = "Attaching an independent disk with name [" + context.diskState.name +
                "]" + "to machine with name [" + context.computeState.name + "]";

        AzureDeferredResultServiceCallback<VirtualMachine> handler = new AzureDeferredResultServiceCallback<VirtualMachine>(
                this, msg) {

            @Override
            protected DeferredResult<VirtualMachine> consumeSuccess(VirtualMachine vm) {
                String msg = String.format(
                        "[AzureComputeDiskDay2Service] Successfully attached volume %s to instance %s",
                        context.diskState.id, context.computeState.id);
                this.service.logInfo(() -> msg);
                return DeferredResult.completed(vm);
            }

            @Override
            protected Throwable consumeError(Throwable exc) {
                String msg = String.format(
                        "[AzureComputeDiskDay2Service] Failure in attaching volume %s to instance %s : %s",
                        context.diskState.id, context.computeState.id, exc);
                this.service.logSevere(msg);
                return exc;
            }
        };

        context.provisionedVm.update()
                .withExistingDataDisk(context.provisionedDisk)
                .applyAsync(handler);

        return handler.toDeferredResult().thenApply(virtualMachine -> context);
    }

    private DeferredResult<AzureComputeDiskDay2Context> updateComputeStateAndDiskState(
            AzureComputeDiskDay2Context context) {
        List<DeferredResult<Operation>> patchedDRs = new ArrayList<>();
        patchedDRs.add(updateDiskState(context));
        patchedDRs.add(updateComputeState(context));

        return DeferredResult.allOf(patchedDRs).handle((o, e) -> {
            if (e != null) {
                logSevere(() -> String.format(
                        "Updating ComputeState %s and DiskState %s : FAILED with %s",
                        context.computeState.name, context.diskState.name,
                        Utils.toString(e)));
                throw new IllegalStateException(e);
            } else {
                logFine(() -> String.format("Updating ComputeState %s and DiskState %s : SUCCESS",
                        context.computeState.name, context.diskState.name));
            }
            return context;
        });
    }

    /**
     * Update the diskLink of DiskState in ComputeState
     */
    private DeferredResult<Operation> updateComputeState(AzureComputeDiskDay2Context context) {
        Map<String, Collection<Object>> collectionsToModify = Collections
                .singletonMap(ComputeState.FIELD_NAME_DISK_LINKS,
                        Collections.singletonList(context.diskState.documentSelfLink));

        Map<String, Collection<Object>> collectionsToAdd = null;
        Map<String, Collection<Object>> collectionsToRemove = null;

        ComputeState computeState = context.computeState;

        if (context.request.operation.equals(ResourceOperation.ATTACH_DISK.operation)) {
            collectionsToAdd = collectionsToModify;
        } else if (context.request.operation.equals(ResourceOperation.DETACH_DISK.operation)) {
            collectionsToRemove = collectionsToModify;
        }

        ServiceStateCollectionUpdateRequest updateDiskLinksRequest = ServiceStateCollectionUpdateRequest
                .create(collectionsToAdd, collectionsToRemove);

        Operation computeStateOp = Operation.createPatch(createInventoryUri(this.getHost(),
                computeState.documentSelfLink))
                .setBody(updateDiskLinksRequest)
                .setReferer(this.getUri());

        return this.sendWithDeferredResult(computeStateOp);
    }

    /**
     * Update status and LUN of DiskState
     */
    private DeferredResult<Operation> updateDiskState(AzureComputeDiskDay2Context context) {
        DiskState diskState = context.diskState;

        if (context.request.operation.equals(ResourceOperation.ATTACH_DISK.operation)) {
            diskState.status = DiskService.DiskStatus.ATTACHED;
        } else if (context.request.operation.equals(ResourceOperation.DETACH_DISK.operation)) {
            diskState.status = DiskService.DiskStatus.AVAILABLE;
            diskState.customProperties.remove(DISK_CONTROLLER_NUMBER);
        }

        if (!context.request.isMockRequest) {
            DataDisk dataDisk = context.provisionedVm.inner().storageProfile().dataDisks()
                    .stream()
                    .filter(dd -> diskState.name.equalsIgnoreCase(dd.name()))
                    .findFirst()
                    .orElse(null);

            if (dataDisk != null) {
                if (diskState.customProperties == null) {
                    diskState.customProperties = new HashMap<>();
                }
                diskState.customProperties.put(DISK_CONTROLLER_NUMBER,
                        String.valueOf(dataDisk.lun()));
            }
        }

        Operation diskPatchOp = null;

        if (context.request.operation.equals(ResourceOperation.ATTACH_DISK.operation)) {
            diskPatchOp = Operation
                    .createPatch(createInventoryUri(this.getHost(), diskState.documentSelfLink))
                    .setBody(diskState)
                    .setReferer(this.getUri());
        } else if (context.request.operation.equals(ResourceOperation.DETACH_DISK.operation)) {
            diskPatchOp = Operation
                    .createPut(createInventoryUri(this.getHost(), diskState.documentSelfLink))
                    .setBody(diskState)
                    .setReferer(this.getUri());
        }

        return this.sendWithDeferredResult(diskPatchOp);
    }

    private DeferredResult<AzureComputeDiskDay2Context> configureAzureSDKClient(
            AzureComputeDiskDay2Context context) {
        return DeferredResult.completed(context)
                .thenCompose(this::getEndpointState)
                .thenCompose(this::getAzureClient);
    }

    /**
     * Get the endpoint state object
     */
    private DeferredResult<AzureComputeDiskDay2Context> getEndpointState(
            AzureComputeDiskDay2Context context) {
        URI uri = context.request.buildUri(context.computeState.endpointLink);
        return this.sendWithDeferredResult(
                Operation.createGet(uri), EndpointState.class)
                .thenApply(endpointState -> {
                    context.endpointState = endpointState;
                    return context;
                });
    }

    /**
     * Get Azure sdk client object to access Azure APIs
     */
    private DeferredResult<AzureComputeDiskDay2Context> getAzureClient(
            AzureComputeDiskDay2Context context) {

        return context.populateBaseContext(BaseAdapterStage.PARENTAUTH);
    }

    /**
     * List of resource operations that are supported by this service.
     */
    public static ResourceOperationSpecService.ResourceOperationSpec[] getResourceOperationSpecs() {
        ResourceOperationSpecService.ResourceOperationSpec attachDiskSpec = createResourceOperationSpec(
                ResourceOperation.ATTACH_DISK);
        ResourceOperationSpecService.ResourceOperationSpec detachDiskSpec = createResourceOperationSpec(
                ResourceOperation.DETACH_DISK);
        return new ResourceOperationSpecService.ResourceOperationSpec[] { attachDiskSpec,
                detachDiskSpec };
    }

    /**
     * Create a resource operation spec
     */
    private static ResourceOperationSpecService.ResourceOperationSpec createResourceOperationSpec(
            ResourceOperation operationType) {
        ResourceOperationSpecService.ResourceOperationSpec spec = new ResourceOperationSpecService.ResourceOperationSpec();
        spec.endpointType = PhotonModelConstants.EndpointType.azure.name();
        spec.resourceType = ResourceOperationSpecService.ResourceType.COMPUTE;
        spec.operation = operationType.operation;
        spec.name = operationType.displayName;
        spec.description = operationType.description;
        return spec;
    }
}
