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

package com.vmware.photon.controller.model.adapters.util.instance;

import static com.vmware.photon.controller.model.ComputeProperties.PLACEMENT_LINK;
import static com.vmware.photon.controller.model.util.PhotonModelUriUtils.createInventoryUri;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceStateWithDescription;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

/**
 * Base context class for adapters that handle {@link ComputeInstanceRequest} request. It
 * {@link #populateContext() loads} NIC related states:
 * <ul>
 * <li>{@link NetworkInterfaceStateWithDescription nicStateWithDesc}</li>
 * <li>{@link SubnetState subnetState}</li>
 * <li>{@link NetworkState networkState}</li>
 * <li>network {@link ResourceGroupState resourceGroupState}</li>
 * <li>map of {@link SecurityGroupState securityGroupStates}</li>
 * </ul>
 * in addition to the states loaded by parent {@link BaseAdapterContext}.
 */
public class BaseComputeInstanceContext<T extends BaseComputeInstanceContext<T, S>, S extends BaseComputeInstanceContext.BaseNicContext>
        extends BaseAdapterContext<T> {

    /**
     * The class encapsulates NIC related states (resolved by links related to the ComputeState)
     * used during compute provisioning.
     */
    public static class BaseNicContext {

        /**
         * Resolved from {@code ComputeState.networkInterfaceLinks[i]}.
         */
        public NetworkInterfaceStateWithDescription nicStateWithDesc;

        /**
         * Resolved from {@code NetworkInterfaceStateWithDescription.subnetLink}.
         */
        public SubnetState subnetState;

        /**
         * Resolved from {@code SubnetState.networkLink}.
         */
        public NetworkState networkState;

        /**
         * Resolved from FIRST {@code NetworkState.groupLinks}.
         */
        public ResourceGroupState networkRGState;

        /**
         * Resolved from {@code NetworkInterfaceStateWithDescription.firewallLinks}.
         */
        public List<SecurityGroupState> securityGroupStates = new ArrayList<>();
    }

    /**
     * Holds allocation data for all VM NICs.
     */
    public final List<S> nics = new ArrayList<>();

    /**
     * The {@link ComputeInstanceRequest request} that is being processed.
     */
    public final ComputeInstanceRequest computeRequest;

    /**
     * Where the machine will be placed
     */
    public String placement;

    /**
     * A set of TagStates, which are used to tag this instance with.
     * The instance will be tagged in AWS, Azure and anywhere else if needed.
     */
    public Set<TagState> tagStates = new HashSet<>();

    /**
     * Supplier/Factory for creating context specific {@link BaseNicContext} instances.
     */
    protected final Supplier<S> nicContextSupplier;

    public BaseComputeInstanceContext(StatelessService service,
            ComputeInstanceRequest computeRequest,
            Supplier<S> nicContextSupplier) {

        super(service, computeRequest);

        this.computeRequest = computeRequest;
        this.nicContextSupplier = nicContextSupplier;
    }

    @Override
    protected URI getParentAuthRef(T context) {
        if (context.computeRequest.requestType == InstanceRequestType.VALIDATE_CREDENTIALS) {
            return createInventoryUri(
                    context.service.getHost(),
                    context.computeRequest.authCredentialsLink);
        }
        return super.getParentAuthRef(context);
    }

    /**
     * The NIC with lowest deviceId is considered primary.
     *
     * @return <code>null</code> if there are no NICs
     */
    public S getPrimaryNic() {
        return this.nics.stream()
                .sorted((n1, n2) -> Integer.compare(
                        n1.nicStateWithDesc.deviceIndex,
                        n2.nicStateWithDesc.deviceIndex))
                .findFirst().orElse(null);
    }

    /**
     * Populate this context.
     * <p>
     * Notes:
     * <ul>
     * <li>It does NOT call parent
     * {@link #populateBaseContext(com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.BaseAdapterStage)}</li>
     * <li>Override {@link #customizeContext(BaseComputeInstanceContext)} if you need to extend
     * populate logic provided by this method and customize the context. The method follows
     * Open-Close principle.</li>
     * </ul>
     */
    public final DeferredResult<T> populateContext() {
        return DeferredResult.completed(self())
                .thenCompose(this::getPlacement)

                .thenCompose(this::getNicStates)
                .thenApply(log("getNicStates"))
                .thenCompose(this::getNicSubnetStates)
                .thenApply(log("getNicSubnetStates"))
                .thenCompose(this::getNicNetworkStates)
                .thenApply(log("getNicNetworkStates"))
                .thenCompose(this::getNicSecurityGroupStates)
                .thenApply(log("getNicSecurityGroupStates"))
                .thenCompose(this::getTagStates)
                .thenApply(log("getTagStates"))

                .thenCompose(this::customizeContext)
                .thenApply(log("customizeContext"));
    }

    private DeferredResult<T> getPlacement(T context) {
        return Optional.ofNullable(context.child.customProperties)
                .map(p -> p.get(PLACEMENT_LINK))
                .map(link -> {
                    Operation getComputeState = Operation.createGet(service, link);

                    return context.service
                            .sendWithDeferredResult(getComputeState, ComputeState.class)
                            .thenApply(cs -> cs.id);
                }).orElse(DeferredResult.completed(null)).thenApply(pl -> {
                    context.placement = pl;
                    return context;
                });
    }

    /**
     * Hook that might be implemented by descendants to extend {@link #populateContext() populate
     * logic} and customize the context.
     */
    protected DeferredResult<T> customizeContext(T context) {
        return DeferredResult.completed(context);
    }

    /**
     * Get NIC states assigned to the compute state we are provisioning.
     */
    private DeferredResult<T> getNicStates(T context) {
        if (context.child.networkInterfaceLinks == null
                || context.child.networkInterfaceLinks.isEmpty()) {
            return DeferredResult.completed(context);
        }

        List<DeferredResult<Void>> getStatesDR = context.child.networkInterfaceLinks.stream()
                .map(nicStateLink -> {
                    S nicContext = context.nicContextSupplier.get();

                    context.nics.add(nicContext);

                    URI nicStateUri = NetworkInterfaceStateWithDescription
                            .buildUri(UriUtils.buildUri(context.service.getHost(), nicStateLink));

                    Operation op = Operation.createGet(nicStateUri);

                    return context.service
                            .sendWithDeferredResult(op, NetworkInterfaceStateWithDescription.class)
                            .thenAccept(nsWithDesc -> nicContext.nicStateWithDesc = nsWithDesc);
                })
                .collect(Collectors.toList());

        return DeferredResult.allOf(getStatesDR).handle((all, exc) -> {
            if (exc != null) {
                String msg = String.format(
                        "Error getting NIC states for [%s] VM.",
                        context.child.name);
                throw new IllegalStateException(msg, exc);
            }
            return context;
        });
    }

    /**
     * Get {@link SubnetState}s the NICs are assigned to.
     */
    private DeferredResult<T> getNicSubnetStates(T context) {
        if (context.nics.isEmpty()) {
            return DeferredResult.completed(context);
        }

        List<DeferredResult<Void>> getStatesDR = context.nics.stream()
                .map(nicContext -> {
                    Operation op = Operation.createGet(
                            context.service.getHost(),
                            nicContext.nicStateWithDesc.subnetLink);

                    return context.service
                            .sendWithDeferredResult(op, SubnetState.class)
                            .thenAccept(subnetState -> nicContext.subnetState = subnetState);
                })
                .collect(Collectors.toList());

        return DeferredResult.allOf(getStatesDR).handle((all, exc) -> {
            if (exc != null) {
                String msg = String.format(
                        "Error getting NIC Subnet states for [%s] VM.",
                        context.child.name);
                throw new IllegalStateException(msg, exc);
            }
            return context;
        });
    }

    /**
     * Get {@link NetworkState}s containing the {@link SubnetState}s the NICs are assigned to.
     *
     * @see #getNicSubnetStates(BaseComputeInstanceContext)
     */
    private DeferredResult<T> getNicNetworkStates(T context) {
        if (context.nics.isEmpty()) {
            return DeferredResult.completed(context);
        }

        List<DeferredResult<Void>> getStatesDR = context.nics.stream()
                .map(nicContext -> {
                    Operation op = Operation.createGet(
                            context.service.getHost(),
                            nicContext.subnetState.networkLink);

                    return context.service
                            .sendWithDeferredResult(op, NetworkState.class)
                            .thenAccept(networkState -> nicContext.networkState = networkState);
                })
                .collect(Collectors.toList());

        return DeferredResult.allOf(getStatesDR).handle((all, exc) -> {
            if (exc != null) {
                String msg = String.format(
                        "Error getting NIC Network states for [%s] VM.",
                        context.child.name);
                throw new IllegalStateException(msg, exc);
            }
            return context;
        });
    }

    /**
     * Get {@link SecurityGroupState}s assigned to NICs.
     */
    private DeferredResult<T> getNicSecurityGroupStates(T context) {
        if (context.nics.isEmpty()) {
            return DeferredResult.completed(context);
        }

        List<DeferredResult<Void>> getSecurityGroupDR = context.nics.stream()
                .filter(nicContext ->
                // Only those that have at least 1 security group.
                nicContext.nicStateWithDesc.securityGroupLinks != null
                        && !nicContext.nicStateWithDesc.securityGroupLinks.isEmpty())
                .flatMap(nicContext -> nicContext.nicStateWithDesc.securityGroupLinks.stream()
                        .map(securityGroupLink -> {
                            Operation op = Operation.createGet(
                                    context.service.getHost(),
                                    securityGroupLink);

                            return context.service
                                    .sendWithDeferredResult(op, SecurityGroupState.class)
                                    .thenAccept(securityGroupState -> nicContext.securityGroupStates
                                            .add(securityGroupState));
                        }))
                .collect(Collectors.toList());

        return DeferredResult.allOf(getSecurityGroupDR).handle((all, exc) -> {
            if (exc != null) {
                String msg = String.format(
                        "Error getting NIC SecurityGroup states for [%s] VM.",
                        context.child.name);
                throw new IllegalStateException(msg, exc);
            }
            return context;
        });
    }

    /**
     * Populate all <code>child.tagLinks</code> as {@link TagState} objects into {@link #tagStates} field.
     *
     * @param context The context object to populate TagStates into.
     * @return        The same context object with populated TagStates.
     */
    private DeferredResult<T> getTagStates(T context) {
        if (context.child.tagLinks == null || context.child.tagLinks.isEmpty()) {
            return DeferredResult.completed(context);
        }

        List<DeferredResult<TagState>> collectTagsDRs = context.child.tagLinks.stream()
                .map(tagLink -> Operation.createGet(context.service, tagLink))
                .map(operation -> context.service.sendWithDeferredResult(operation, TagState.class))
                .collect(Collectors.toList());

        return DeferredResult.allOf(collectTagsDRs).handle((collectedTagStates, ex) -> {
            if (ex != null) {
                String msg = "Could not get TagState documents from tag links.";
                throw new IllegalStateException(msg, ex);
            }

            context.tagStates.addAll(collectedTagStates);
            return context;
        });

    }

    /**
     * Helper method to load {@link ImageSource} either from {@code ImageState} that is pointed by
     * {@code bootDisk.imageLink} or directly from {@code bootDisk.sourceImageReference}.
     */
    public DeferredResult<ImageSource> getImageSource(DiskState bootDisk) {

        if (bootDisk == null) {
            return DeferredResult.failed(new IllegalStateException("bootDisk should be specified"));
        }

        if (bootDisk.sourceImageReference == null && bootDisk.imageLink == null) {
            return DeferredResult.failed(new IllegalStateException(
                    "Either bootDisk.sourceImageReference or bootDisk.imageLink should be specified"));
        }

        final DeferredResult<ImageSource> imageSourceDR;

        if (bootDisk.imageLink != null) {
            // Either get ImageState as pointed by 'bootDisk.imageLink'
            Operation getImageStateOp = Operation.createGet(
                    this.service.getHost(), bootDisk.imageLink);

            imageSourceDR = this.service
                    .sendWithDeferredResult(getImageStateOp, ImageState.class)
                    .thenApply(ImageSource::fromImageState);
        } else {
            // Or use directly 'bootDisk.sourceImageReference' as 'image native id'
            imageSourceDR = DeferredResult.completed(
                    ImageSource.fromRef(bootDisk.sourceImageReference.toString()));
        }

        return imageSourceDR;
    }

    /**
     * Generic enough representation of a source of an image, such as local ImageState or native/raw
     * image reference.
     */
    public static class ImageSource {

        /**
         * The type of the source so we can do/apply conditional logic.
         */
        public enum Type {
            PUBLIC_IMAGE, PRIVATE_IMAGE, IMAGE_REFERENCE;
        }

        /**
         * Create image source from actual public/private ImageState.
         */
        public static ImageSource fromImageState(ImageState imageState) {

            final ImageSource imageHolder = new ImageSource();

            if (imageState.isPublicImage()) {

                imageHolder.type = Type.PUBLIC_IMAGE;

            } else if (imageState.isPrivateImage()) {

                imageHolder.type = Type.PRIVATE_IMAGE;

            } else {
                throw new IllegalStateException("Unexpected ImageState type.");
            }

            imageHolder.source = imageState;

            return imageHolder;
        }

        /**
         * Create image source from native/raw image reference.
         */
        public static ImageSource fromRef(String imageRef) {

            return new ImageSource(Type.IMAGE_REFERENCE, imageRef);
        }

        public Type type;
        public Object source;

        public ImageSource(Type type, Object source) {
            this.type = type;
            this.source = source;
        }

        private ImageSource() {
        }

        /**
         * Cast image source to actual ImageState.
         */
        public ImageState asImageState() {
            if (this.type == Type.PUBLIC_IMAGE || this.type == Type.PRIVATE_IMAGE) {
                return (ImageState) this.source;
            }
            return null;
        }

        /**
         * Cast image source to actual image reference.
         */
        public String asRef() {
            if (this.type == Type.IMAGE_REFERENCE) {
                return (String) this.source;
            }
            return null;
        }

        /**
         * Helper method converting supported image sources to native image id.
         */
        public String asNativeId() {
            if (asImageState() != null) {
                return asImageState().id;
            }
            if (asRef() != null) {
                return asRef();
            }
            return null;
        }
    }
}
