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

package com.vmware.photon.controller.model.adapters.vsphere;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Phaser;

import org.codehaus.jackson.node.ObjectNode;

import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapterapi.ImageEnumerateRequest;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.adapters.vsphere.vapi.LibraryClient;
import com.vmware.photon.controller.model.adapters.vsphere.vapi.RpcException;
import com.vmware.photon.controller.model.adapters.vsphere.vapi.VapiClient;
import com.vmware.photon.controller.model.adapters.vsphere.vapi.VapiConnection;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ImageService;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.vim25.ObjectContent;
import com.vmware.vim25.PropertyFilterSpec;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;

/**
 */
public class VSphereAdapterImageEnumerationService extends StatelessService {

    public static final String SELF_LINK = VSphereUriPaths.IMAGE_ENUMERATION_SERVICE;

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        op.setStatusCode(Operation.STATUS_CODE_CREATED);
        op.complete();

        ImageEnumerateRequest request = op.getBody(ImageEnumerateRequest.class);

        validate(request);

        TaskManager mgr = new TaskManager(this, request.taskReference, request.resourceLink());

        if (request.isMockRequest) {
            // just finish the mock request
            mgr.patchTask(TaskStage.FINISHED);
            return;
        }

        Operation.createGet(request.resourceReference)
                .setCompletion(
                        o -> thenWithEndpointState(request, o.getBody(EndpointState.class), mgr), mgr)
                .sendWith(this);
    }

    private void thenWithEndpointState(ImageEnumerateRequest request, EndpointState endpoint,
            TaskManager mgr) {
        URI parentUri = ComputeStateWithDescription
                .buildUri(UriUtils.buildUri(getHost(), endpoint.computeLink));
        Operation.createGet(parentUri)
                .setCompletion(o -> thenWithParentState(request,
                        o.getBody(ComputeStateWithDescription.class), mgr),
                        mgr)
                .sendWith(this);
    }

    private void thenWithParentState(ImageEnumerateRequest request,
            ComputeStateWithDescription parent, TaskManager mgr) {

        collectAllEndpointImages(request).thenAccept(oldImages -> {
            VSphereIOThreadPool pool = VSphereIOThreadPoolAllocator.getPool(this);
            pool.submit(this, parent.adapterManagementReference,
                    parent.description.authCredentialsLink, (connection, e) -> {
                        if (e != null) {
                            String msg = String.format("Cannot establish connection to %s",
                                    parent.adapterManagementReference);
                            logWarning(msg);
                            mgr.patchTaskToFailure(msg, e);
                        } else {
                            if (request.enumerationAction == EnumerationAction.REFRESH) {
                                refreshResourcesOnce(oldImages, request, parent, connection, mgr);
                            } else {
                                mgr.patchTaskToFailure(
                                        new IllegalArgumentException("Only REFRESH supported"));
                            }
                        }
                    });
        });
    }

    private void refreshResourcesOnce(Set<String> oldImages, ImageEnumerateRequest request,
            ComputeStateWithDescription parent,
            Connection connection,
            TaskManager mgr) {

        try {
            EnumerationClient client = new EnumerationClient(connection, parent);
            processAllTemplates(oldImages, request.resourceLink(), request.taskLink(), client, parent.tenantLinks);
        } catch (Throwable e) {
            mgr.patchTaskToFailure("Error processing library items", e);
            return;
        }

        VapiConnection vapi = VapiConnection.createFromVimConnection(connection);
        try {
            vapi.login();
            LibraryClient libraryClient = vapi.newLibraryClient();
            processAllLibraries(oldImages, request.resourceLink(), request.taskLink(), libraryClient,
                    parent.tenantLinks);

            mgr.patchTask(TaskStage.FINISHED);
        } catch (Throwable t) {
            mgr.patchTaskToFailure("Error processing library items", t);
            return;
        } finally {
            vapi.close();
        }

        // garbage collection runs async
        garbageCollectUntouchedImages(oldImages);
    }

    private void processAllTemplates(Set<String> oldImages, String endpointLink, String taskLink,
            EnumerationClient client, List<String> tenantLinks) throws RuntimeFaultFaultMsg {
        PropertyFilterSpec spec = client.createVmFilterSpec(client.getDatacenter());
        for (List<ObjectContent> page : client.retrieveObjects(spec)) {
            Phaser phaser = new Phaser(1);
            for (ObjectContent oc : page) {
                if (!VimUtils.isVirtualMachine(oc.getObj())) {
                    continue;
                }

                VmOverlay vm = new VmOverlay(oc);
                if (!vm.isTemplate()) {
                    continue;
                }

                ImageState state = makeImageFromTemplate(vm);
                state.documentSelfLink = buildStableImageLink(endpointLink, state.id);
                state.endpointLink = endpointLink;
                state.tenantLinks = tenantLinks;

                oldImages.remove(state.documentSelfLink);
                phaser.register();

                Operation.createPost(this, ImageService.FACTORY_LINK)
                        .setBody(state)
                        .setCompletion((o, e) -> phaser.arrive())
                        .sendWith(this);
            }

            phaser.arriveAndAwaitAdvance();
        }
    }

    private ImageState makeImageFromTemplate(VmOverlay vm) {
        ImageState res = new ImageState();

        res.name = "Template: " + vm.getName();
        res.description = vm.getName();
        res.id = vm.getInstanceUuid();

        CustomProperties.of(res)
                .put(CustomProperties.MOREF, vm.getId());

        return res;
    }

    private DeferredResult<Set<String>> collectAllEndpointImages(ImageEnumerateRequest req) {
        Builder builder = Query.Builder.create()
                .addKindFieldClause(ImageState.class)
                .addFieldClause(ImageState.FIELD_NAME_ENDPOINT_LINK, req.resourceLink());

        QueryTask task = QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .setResultLimit(QueryUtils.DEFAULT_RESULT_LIMIT)
                .build();

        DeferredResult<Set<String>> res = new DeferredResult<>();
        Set<String> imageLinks = new ConcurrentSkipListSet<>();

        QueryUtils.startQueryTask(this, task).whenComplete((result, e) -> {
            if (e != null) {
                res.complete(new HashSet<>());
                return;
            }

            if (result.results.nextPageLink == null) {
                res.complete(imageLinks);
                return;
            }

            Operation.createGet(UriUtils.buildUri(getHost(), result.results.nextPageLink))
                    .setCompletion(makeCompletion(imageLinks, res))
                    .sendWith(this);
        });

        return res;
    }

    private CompletionHandler makeCompletion(Set<String> imageLinks, DeferredResult<Set<String>> res) {
        return (o, e) -> {
            if (e != null) {
                res.complete(imageLinks);
                return;
            }

            QueryTask qt = o.getBody(QueryTask.class);
            imageLinks.addAll(qt.results.documentLinks);

            if (qt.results.nextPageLink == null) {
                res.complete(imageLinks);
            } else {
                Operation.createGet(UriUtils.buildUri(getHost(), qt.results.nextPageLink))
                        .setCompletion(makeCompletion(imageLinks, res))
                        .sendWith(this);
            }
        };
    }

    private void garbageCollectUntouchedImages(Set<String> untouchedImages) {
        untouchedImages.forEach(link -> {
            Operation.createDelete(UriUtils.buildUri(getHost(), link))
                    .sendWith(this);
        });
    }

    private void processAllLibraries(Set<String> oldImages, String endpointLink, String taskLink,
            LibraryClient libraryClient, List<String> tenantLinks)
            throws IOException, RpcException {
        Phaser phaser = new Phaser(1);

        for (String libId : libraryClient.listLibs()) {
            ObjectNode libModel = libraryClient.loadLib(libId);
            for (String itemId : libraryClient.listItemsInLib(libId)) {
                ObjectNode itemModel = libraryClient.loadItem(itemId);

                String type = VapiClient.getString(itemModel, "type", VapiClient.K_OPTIONAL);
                if (!"ovf".equals(type)) {
                    // only ovfs can be deployed
                    continue;
                }

                ImageState state = makeImageFromItem(libModel, itemModel);
                state.documentSelfLink = buildStableImageLink(endpointLink, state.id);
                state.endpointLink = endpointLink;
                state.tenantLinks = tenantLinks;

                oldImages.remove(state.documentSelfLink);
                phaser.register();

                Operation.createPost(this, ImageService.FACTORY_LINK)
                        .setBody(state)
                        .setCompletion((o, e) -> phaser.arrive())
                        .sendWith(this);
            }
        }

        phaser.arriveAndAwaitAdvance();
    }

    private String buildStableImageLink(String endpointLink, String itemId) {
        return ImageService.FACTORY_LINK + "/" + VimUtils.buildStableId(endpointLink, itemId);
    }

    private ImageState makeImageFromItem(ObjectNode lib, ObjectNode item) {
        ImageState res = new ImageState();

        String itemName = VapiClient.getString(item, "name", VapiClient.K_OPTIONAL);
        String libraryName = VapiClient.getString(lib, "name", VapiClient.K_OPTIONAL);
        res.name = libraryName + " / " + itemName;

        res.description = VapiClient.getString(item, "description", VapiClient.K_OPTIONAL);
        res.id = VapiClient.getString(item, "id", VapiClient.K_OPTIONAL);

        CustomProperties.of(res)
                .put(CustomProperties.IMAGE_LIBRARY_ID,
                        VapiClient.getString(item, "library_id", VapiClient.K_OPTIONAL));

        return res;
    }

    private void validate(ImageEnumerateRequest request) {
        // assume all request are REFRESH requests
        if (request.enumerationAction == null) {
            request.enumerationAction = EnumerationAction.REFRESH;
        }
    }
}
