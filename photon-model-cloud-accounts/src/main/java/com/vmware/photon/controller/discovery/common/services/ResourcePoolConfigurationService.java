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

package com.vmware.photon.controller.discovery.common.services;

import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.DISABLE_STATS_COLLECTION;
import static com.vmware.photon.controller.discovery.common.utils.InitializationUtils.getGroomerTaskSelfLink;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils;
import com.vmware.photon.controller.discovery.common.services.ResourceEnumerationService.ResourceEnumerationRequest;
import com.vmware.photon.controller.discovery.common.utils.DataCollectionTaskUtil;
import com.vmware.photon.controller.discovery.common.utils.InitializationUtils;
import com.vmware.photon.controller.discovery.endpoints.OptionalAdapterSchedulingService;
import com.vmware.photon.controller.discovery.endpoints.OptionalAdapterSchedulingService.OptionalAdapterSchedulingRequest;
import com.vmware.photon.controller.discovery.endpoints.OptionalAdapterSchedulingService.RequestType;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.ResourceGroomerTaskService;
import com.vmware.photon.controller.model.tasks.ScheduledTaskService;
import com.vmware.photon.controller.model.tasks.ScheduledTaskService.ScheduledTaskState;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService.StatsCollectionTaskState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.OperationJoin.JoinedCompletionHandler;
import com.vmware.xenon.common.RequestRouter.Route;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Service to configure resource pool. As part of resource pool creation, a stats collection task
 * is kicked off for the resource pool.
 */
public class ResourcePoolConfigurationService extends StatelessService {
    public static final String SELF_LINK = UriPaths.RESOURCE_POOL_CONFIGURATION_SERVICE;

    public static final String COLLECTION_INTERVAL_MILLIS = UriPaths.PROPERTY_PREFIX
            + "ResourcePoolConfigurationService.collectionIntervalMillis";
    private static final long DEFAULT_COLLECTION_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(60);
    public static final String ENUMERATION_INTERVAL_MILLIS = UriPaths.PROPERTY_PREFIX
            + "ResourcePoolConfigurationService.enumerationIntervalMillis";
    public static final long DEFAULT_ENUMERATION_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(10);
    public static final long DEFAULT_GROOMER_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(60);

    public static class ResourcePoolConfigurationRequest {
        public String orgId;
        public String projectId;
        public ConfigurationRequestType requestType = ConfigurationRequestType.CREATE;
    }

    public static enum ConfigurationRequestType {
        CREATE, TEARDOWN
    }

    @Override
    public void handlePost(Operation op) {
        ResourcePoolConfigurationRequest request = op
                .getBody(ResourcePoolConfigurationRequest.class);
        validateRequest(request);
        if (request.requestType.equals(ConfigurationRequestType.CREATE)) {
            ResourcePoolState resourcePoolState = new ResourcePoolState();
            resourcePoolState.name = resourcePoolState.id = request.projectId;
            List<String> tenantLinks = new ArrayList<>();
            tenantLinks.add(UriUtils.buildUriPath(ProjectService.FACTORY_LINK, request.projectId));
            resourcePoolState.tenantLinks = tenantLinks;
            resourcePoolState.documentSelfLink = request.projectId;
            Operation.createPost(getHost(), ResourcePoolService.FACTORY_LINK)
                .setBody(resourcePoolState)
                .setCompletion(((completedOp, failure) -> {
                    if (failure != null) {
                        logSevere(failure);
                        op.fail(failure);
                        return;
                    }

                    logInfo("Created resource pool %s", request.projectId);
                    ResourcePoolState body = completedOp.getBody(ResourcePoolState.class);
                    startScheduledTasks(body, op);
                })).sendWith(this);
        } else {
            deleteResources(request.projectId, op);
        }
    }

    private void startScheduledTasks(ResourcePoolState resourcePoolState, Operation op) {
        ResourcePoolConfigurationRequest request = op
                .getBody(ResourcePoolConfigurationRequest.class);
        List<Operation> operations = new ArrayList<>();
        ResourceEnumerationRequest enumerationRequest = new ResourceEnumerationRequest();
        enumerationRequest.resourcePoolState = resourcePoolState;

        ScheduledTaskState enumerationScheduledTask = new ScheduledTaskState();
        enumerationScheduledTask.factoryLink = ResourceEnumerationService.SELF_LINK;
        enumerationScheduledTask.initialStateJson = Utils.toJson(enumerationRequest);
        enumerationScheduledTask.intervalMicros = TimeUnit.MILLISECONDS.toMicros(
                Long.getLong(ENUMERATION_INTERVAL_MILLIS, DEFAULT_ENUMERATION_INTERVAL_MILLIS));
        enumerationScheduledTask.userLink = PhotonControllerCloudAccountUtils.getAutomationUserLink();
        enumerationScheduledTask.tenantLinks = resourcePoolState.tenantLinks;
        enumerationScheduledTask.documentSelfLink = InitializationUtils.getEnumerationTaskId(resourcePoolState.documentSelfLink);
        enumerationScheduledTask.partitionId = request.orgId;
        operations.add(Operation.createPost(this, ScheduledTaskService.FACTORY_LINK)
                .setBody(enumerationScheduledTask)
                .setReferer(getUri()));

        // start up a stats collection task
        StatsCollectionTaskState statCollectionState =
                DataCollectionTaskUtil.createStatsCollectionTask(resourcePoolState.documentSelfLink);

        ScheduledTaskState statsCollectionScheduledTask = new ScheduledTaskState();
        if (DISABLE_STATS_COLLECTION) {
            statsCollectionScheduledTask.enabled = false;
        }
        statsCollectionScheduledTask.factoryLink = StatsCollectionTaskService.FACTORY_LINK;
        statsCollectionScheduledTask.initialStateJson = Utils.toJson(statCollectionState);
        statsCollectionScheduledTask.intervalMicros = TimeUnit.MILLISECONDS.toMicros(
                Long.getLong(COLLECTION_INTERVAL_MILLIS, DEFAULT_COLLECTION_INTERVAL_MILLIS));
        statsCollectionScheduledTask.userLink = PhotonControllerCloudAccountUtils.getAutomationUserLink();
        statsCollectionScheduledTask.tenantLinks = resourcePoolState.tenantLinks;
        statsCollectionScheduledTask.documentSelfLink = InitializationUtils.getCollectionTaskId(resourcePoolState.documentSelfLink);
        operations.add(Operation.createPost(this, ScheduledTaskService.FACTORY_LINK)
                .setBody(statsCollectionScheduledTask)
                .setReferer(getUri()));

        ResourceGroomerTaskService.EndpointResourceDeletionRequest endpointResourceDeletionRequest = new ResourceGroomerTaskService.EndpointResourceDeletionRequest();
        endpointResourceDeletionRequest.tenantLinks = new HashSet<>(resourcePoolState.tenantLinks);
        endpointResourceDeletionRequest.documentSelfLink = getGroomerTaskSelfLink(resourcePoolState.tenantLinks.get(0));

        ScheduledTaskState groomerScheduledTask = new ScheduledTaskState();
        // setting it to false for now until xenon sync issues are resolved.
        groomerScheduledTask.enabled = false;
        groomerScheduledTask.factoryLink = ResourceGroomerTaskService.FACTORY_LINK;
        groomerScheduledTask.initialStateJson = Utils.toJson(endpointResourceDeletionRequest);
        groomerScheduledTask.intervalMicros = TimeUnit.MILLISECONDS.toMicros(
                Long.getLong(ENUMERATION_INTERVAL_MILLIS, DEFAULT_GROOMER_INTERVAL_MILLIS));
        groomerScheduledTask.userLink = PhotonControllerCloudAccountUtils.getAutomationUserLink();
        groomerScheduledTask.tenantLinks = resourcePoolState.tenantLinks;
        groomerScheduledTask.documentSelfLink = InitializationUtils.getGroomerTaskId(resourcePoolState.documentSelfLink);
        operations.add(Operation.createPost(this, ScheduledTaskService.FACTORY_LINK)
                .setBody(groomerScheduledTask)
                .setReferer(getUri()));

        OperationJoin joinOp = OperationJoin.create(operations);
        JoinedCompletionHandler joinHandler = (ops, exc) -> {
            if (exc != null) {
                op.fail(exc.values().iterator().next());
                return;
            }
            logInfo("Collection tasks scheduled for resource pool %s", resourcePoolState.name);
            op.complete();
        };
        joinOp.setCompletion(joinHandler);
        joinOp.sendWith(getHost());
    }

    private void deleteResources(String projectId, Operation op) {
        deleteOptionalAdaptersForResourcePool(projectId);
        List<Operation> operations = new ArrayList<>();

        String enumerationScheduledTaskId = InitializationUtils.getEnumerationTaskId(projectId);
        operations.add(Operation.createDelete(this,
                UriUtils.buildUriPath(ScheduledTaskService.FACTORY_LINK, enumerationScheduledTaskId))
                .setReferer(getUri()));

        String collectionScheduledTaskId = InitializationUtils.getCollectionTaskId(projectId);
        operations.add(Operation.createDelete(this,
                UriUtils.buildUriPath(ScheduledTaskService.FACTORY_LINK, collectionScheduledTaskId))
                .setReferer(getUri()));

        OperationJoin joinOp = OperationJoin.create(operations);
        JoinedCompletionHandler joinHandler = (ops, exc) -> {
            if (exc != null) {
                op.fail(exc.values().iterator().next());
                return;
            }
            logInfo("Scheduled tasks deleted for resource pool %s", projectId);
            op.complete();
        };
        joinOp.setCompletion(joinHandler);
        joinOp.sendWith(getHost());

    }

    private void validateRequest(ResourcePoolConfigurationRequest request) {
        if (request.projectId == null) {
            throw new IllegalArgumentException("projectId is required");
        }
        // TODO VSYM-614: Verify tenant links provided are valid and authorized.
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument d = super.getDocumentTemplate();
        d.documentDescription.serviceRequestRoutes = new HashMap<>();
        Route route = new Route();
        route.action = Action.POST;
        route.description = "Creates a resource pool and set of maintenance tasks associated with it";
        route.requestType = ResourcePoolConfigurationRequest.class;
        d.documentDescription.serviceRequestRoutes
                .put(route.action, Collections.singletonList(route));
        return d;
    }

    private void deleteOptionalAdaptersForResourcePool(String rpLink) {
        OptionalAdapterSchedulingRequest request = new OptionalAdapterSchedulingRequest();
        request.resourcePoolLink = rpLink;
        request.requestType = RequestType.UNSCHEDULE;
        Operation.createPatch(getHost(), OptionalAdapterSchedulingService.SELF_LINK).setBody(request)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Failed to delete optional stats adapters for resource pool '%s' due to '%s'",
                                rpLink, e.getMessage());
                    }
                }).sendWith(this);
    }
}
