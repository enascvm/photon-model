/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.discovery.cloudaccount;

import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.handleCompletion;

import java.util.logging.Level;

import com.vmware.photon.controller.discovery.cloudaccount.users.UserQueryTaskService;
import com.vmware.photon.controller.discovery.cloudaccount.users.UserQueryTaskService.UserQueryTaskState;
import com.vmware.photon.controller.discovery.endpoints.EndpointUtils;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.UserGroupService;
import com.vmware.xenon.services.common.UserGroupService.UserGroupState;

/**
 * Helper to retrieve cloud account owners.
 */
public class CloudAccountOwnerRetriever {
    private static final CloudAccountOwnerRetriever INSTANCE = new CloudAccountOwnerRetriever();
    private static final String SERVICE_NOT_FOUND = "Service not found";

    private CloudAccountOwnerRetriever() {
    }

    public static CloudAccountOwnerRetriever getInstance() {
        return INSTANCE;
    }

    /**
     * Retrieve cloud account owners for the given id.
     *
     * @param service - The service invoking this method.
     * @param parentOp - The parent operation.
     * @param id - The id of the cloud account.
     */
    public void getCloudAccountOwner(Service service, Operation parentOp, String id) {
        if (service == null || parentOp == null) {
            throw new IllegalStateException("'service' and 'parentOp' must be specified");
        }

        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("'id' must be specified");
        }

        Integer resultLimit = UriUtils.getODataLimitParamValue(parentOp.getUri());

        OperationContext ctx = OperationContext.getOperationContext();
        getEndpoint(service, id)
                .thenCompose(endpointState -> getEndpointOwnerUserGroup(service, endpointState))
                .whenComplete((state, throwable) -> OperationContext.restoreOperationContext(ctx))
                .thenCompose(userGroupState -> getCloudAccountOwners(service, userGroupState.query, resultLimit))
                .thenAccept((taskOp) -> {
                    if (!handleCompletion(parentOp, taskOp, null)) {
                        service.getHost()
                                .log(Level.WARNING, "Could not retrieve owners for %s", id);
                        return;
                    }

                    UserQueryTaskState body = taskOp.getBody(UserQueryTaskState.class);

                    parentOp.setBody(body.results);
                    parentOp.complete();
                })
                .exceptionally(throwable -> {
                    if (throwable != null) {
                        service.getHost().log(Level.WARNING, throwable.getMessage());
                        // Xenon doesn't seem to guarantee ServiceNotFoundException in all cases.
                        if (throwable.getMessage().contains(SERVICE_NOT_FOUND)) {
                            parentOp.fail(Operation.STATUS_CODE_NOT_FOUND);
                            return null;
                        }
                        parentOp.fail(throwable);
                    }
                    return null;
                });
    }

    private DeferredResult<EndpointState> getEndpoint(Service service, String endpointId) {
        Operation endpointOp = Operation.createGet(service,
                UriUtils.buildUriPath(EndpointService.FACTORY_LINK, endpointId));
        return service.sendWithDeferredResult(endpointOp, EndpointState.class);
    }

    private DeferredResult<UserGroupState> getEndpointOwnerUserGroup(Service service,
            EndpointState endpointState) {
        String endpointOwnerLink = EndpointUtils
                .buildEndpointAuthzArtifactSelfLink(UserGroupService.FACTORY_LINK,
                        endpointState.documentSelfLink, endpointState.tenantLinks);
        Operation userGroupOp = Operation.createGet(service, endpointOwnerLink);
        service.setAuthorizationContext(userGroupOp, service.getSystemAuthorizationContext());
        return service.sendWithDeferredResult(userGroupOp, UserGroupState.class);
    }

    private DeferredResult<Operation> getCloudAccountOwners(Service service, Query query,
            Integer resultLimit) {
        UserQueryTaskState userQuery = new UserQueryTaskState();
        userQuery.taskInfo = TaskState.createDirect();
        QuerySpecification filterOverride = new QuerySpecification();
        filterOverride.query = query;
        if (resultLimit != null && resultLimit > 0) {
            filterOverride.resultLimit = resultLimit;
        }
        userQuery.filterOverride = filterOverride;

        Operation ownersOp = Operation.createPost(service, UserQueryTaskService.FACTORY_LINK)
                .setBody(userQuery);
        return service.sendWithDeferredResult(ownersOp);
    }
}
