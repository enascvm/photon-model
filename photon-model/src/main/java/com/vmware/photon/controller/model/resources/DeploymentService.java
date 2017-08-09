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

package com.vmware.photon.controller.model.resources;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.DocumentIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Represents a Deployment AKA a Composite resource.
 *
 * Deployments are groups of resource, typically provisioned together in order
 * to deliver a complete/workable application (PAAS use cases). Deployments also
 * help to simplify resource management by providing a higher-level unit of work
 * than fine-grained resources.
 *
 * Note: ideally the destruction of a deployment should trigger the destruction
 * of all its component resources.
 */
public class DeploymentService extends StatefulService {

    /**
     * use to maintain bidirectional link in resource member of a
     * deployment.
     */
    public static final String CUSTOM_PROPERTY_DEPLOYMENT_LINK = "deploymentLink";

    public static final String FACTORY_LINK = UriPaths.RESOURCES + "/deployments";

    public static class DeploymentServiceState extends ResourceState {

        @Documentation(description = "Optional description link (i.e. template).")
        @PropertyOptions(usage = { PropertyUsageOption.LINK, PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.SINGLE_ASSIGNMENT })
        public String descriptionLink;

        @Documentation(description = "Component links.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Set<String> componentLinks;

    }

    public DeploymentService() {
        super(DeploymentServiceState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleCreate(Operation start) {
        try {
            if (!start.hasBody()) {
                throw new IllegalArgumentException("body is required");
            }

            DeploymentServiceState state = start.getBody(DeploymentServiceState.class);
            Utils.validateState(getStateDescription(), state);
            if (state.creationTimeMicros == null) {
                state.creationTimeMicros = System.currentTimeMillis();
            }
            start.complete();
        } catch (Throwable t) {
            logSevere(t);
            start.fail(t);
        }
    }

    @Override
    public void handlePut(Operation put) {
        try {
            if (!put.hasBody()) {
                throw new IllegalArgumentException("body is required");
            }

            DeploymentServiceState newState = put.getBody(DeploymentServiceState.class);
            DeploymentServiceState previousState = getState(put);
            Utils.validateState(getStateDescription(), newState);
            if (newState.descriptionLink != null && previousState.descriptionLink != null
                    && newState.descriptionLink != previousState.descriptionLink) {
                throw new IllegalArgumentException("descriptionLink type can not be changed");
            }
            newState.creationTimeMicros = previousState.creationTimeMicros;
            setState(put, newState);
            put.complete();
        } catch (Throwable t) {
            logSevere(t);
            put.fail(t);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        DeploymentServiceState currentState = getState(patch);
        Function<Operation, Boolean> customPatchHandler = t -> {
            boolean hasStateChanged = false;
            DeploymentServiceState patchBody = patch.getBody(DeploymentServiceState.class);

            if (patchBody.descriptionLink != null) {
                if (currentState.descriptionLink == null) {
                    currentState.descriptionLink = patchBody.descriptionLink;
                    hasStateChanged = true;
                } else if (patchBody.descriptionLink != currentState.descriptionLink) {
                    throw new IllegalArgumentException("descriptionLink type can not be changed");
                }
            }

            Pair<Set<String>, Boolean> mergeResult = PhotonModelUtils.mergeLists(
                    currentState.componentLinks, patchBody.componentLinks);
            currentState.componentLinks = mergeResult.getLeft();
            hasStateChanged = hasStateChanged || mergeResult.getRight();

            return hasStateChanged;
        };
        ResourceUtils.handlePatch(patch, currentState, getStateDescription(),
                DeploymentServiceState.class, customPatchHandler);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        // enable metadata indexing
        td.documentDescription.documentIndexingOptions = EnumSet
                .of(DocumentIndexingOption.INDEX_METADATA);
        ServiceUtils.setRetentionLimit(td);

        return td;
    }

}
