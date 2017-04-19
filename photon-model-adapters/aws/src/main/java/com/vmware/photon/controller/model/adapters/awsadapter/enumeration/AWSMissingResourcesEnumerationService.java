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

package com.vmware.photon.controller.model.adapters.awsadapter.enumeration;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class AWSMissingResourcesEnumerationService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_MISSING_RESOURCES_SERVICE;

    public static class Request {
        public ComputeStateWithDescription primaryAccountCompute;
        public Collection<String> missingLinkedAccountIds;
    }

    public class AwsMissingResourcesEnumContext {
        protected AwsMissingResourcesEnumStages stage;
        protected Request request;
        protected Operation requestOp;

        public AwsMissingResourcesEnumContext() {
            this.stage = AwsMissingResourcesEnumStages.CREATE_LINKED_COMPUTES;
        }
    }

    public enum AwsMissingResourcesEnumStages {
        CREATE_LINKED_COMPUTES
    }

    @Override
    public void handlePost(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        Object obj = op.getBody(Request.class);
        Request request = Utils.fromJson(obj,
                Request.class);
        AwsMissingResourcesEnumContext context = new AwsMissingResourcesEnumContext();
        context.request = request;
        context.requestOp = op;
        handleRequest(context);
    }

    protected void handleRequest(AwsMissingResourcesEnumContext context) {
        switch (context.stage) {
        case CREATE_LINKED_COMPUTES:
            createLinkedComputeStates(context);
            break;

        default:
            logSevere("Unknown stage while creating the missing linked account compute states");
            break;
        }
    }

    /**
     *  creates new linked account endpoint states of a primary account
     * @param context
     *
     */

    private void createLinkedComputeStates(AwsMissingResourcesEnumContext context) {
        AtomicInteger completionCounter = new AtomicInteger(context.request.missingLinkedAccountIds
                .size());
        AtomicBoolean isSuccessful = new AtomicBoolean(true);
        context.request.missingLinkedAccountIds.forEach(linkedAccountId -> {

            ComputeDescription computeDescription = populateComputeDescription(context,
                    linkedAccountId);
            ComputeState computeState = populateComputeState(context, linkedAccountId);
            computeState.descriptionLink = computeDescription.documentSelfLink;
            Operation csOp = Operation.createPost(this, ComputeService.FACTORY_LINK)
                    .setBody(computeState);
            Operation cdOp = Operation.createPost(this, ComputeDescriptionService.FACTORY_LINK)
                    .setBody(computeDescription);

            OperationSequence.create(cdOp, csOp).setCompletion((ops, exs) -> {
                if (exs != null && !exs.isEmpty()) {
                    logWarning(() -> String.format("Error creating missing resources for"
                            + " account with ID %s.", linkedAccountId));
                    isSuccessful.set(false);
                } else {
                    logInfo(() -> String.format("Created missing resources for account " +
                            "with ID %s.", linkedAccountId));
                }
                if (completionCounter.decrementAndGet() == 0) {
                    if (isSuccessful.get()) {
                        context.requestOp.complete();
                    } else {
                        context.requestOp.fail(new Exception("Failed to create missing resources " +
                                "for atleast one linked account."));
                    }
                }
            }).sendWith(this);
        });
    }

    /**
     *  creates a compute description for the identified linked accounts
     * @return
     */
    private ComputeDescription populateComputeDescription(AwsMissingResourcesEnumContext context,
            String linkedAccountId) {
        ComputeDescription cd = new ComputeDescription();
        ComputeStateWithDescription primaryAccountCompute = context.request.primaryAccountCompute;
        cd.regionId = primaryAccountCompute.regionId;
        cd.environmentName = primaryAccountCompute.environmentName;
        cd.tenantLinks = primaryAccountCompute.tenantLinks;
        cd.id = generateUuidFromStr(linkedAccountId + context.request
                .primaryAccountCompute.endpointLink);
        cd.documentSelfLink = UriUtils.buildUriPath(ComputeDescriptionService.FACTORY_LINK, cd.id);
        cd.name = primaryAccountCompute.name + "_" + linkedAccountId;
        cd.endpointLink = primaryAccountCompute.endpointLink;
        return cd;
    }

    /**
     * creates compute state for the identified linked account
     * @return
     */
    private ComputeState populateComputeState(AwsMissingResourcesEnumContext context,
            String linkedAccountId) {
        ComputeState computeState = new ComputeState();
        computeState.id = generateUuidFromStr(linkedAccountId + context.request
                .primaryAccountCompute.endpointLink);
        ComputeState primaryAccountCompute = context.request.primaryAccountCompute;
        computeState.name = primaryAccountCompute.name + "_" + linkedAccountId;
        computeState.resourcePoolLink = primaryAccountCompute.resourcePoolLink;
        computeState.type = primaryAccountCompute.type;
        computeState.adapterManagementReference = primaryAccountCompute.adapterManagementReference;
        computeState.tenantLinks = primaryAccountCompute.tenantLinks;
        computeState.customProperties = new HashMap<>();
        computeState.customProperties.put(AWSConstants.AWS_ACCOUNT_ID_KEY, linkedAccountId);
        computeState.customProperties.put(EndpointAllocationTaskService.CUSTOM_PROP_ENPOINT_TYPE,
                primaryAccountCompute.customProperties
                        .get(EndpointAllocationTaskService.CUSTOM_PROP_ENPOINT_TYPE));
        computeState.customProperties.put(AWSConstants.ACCOUNT_IS_AUTO_DISCOVERED, Boolean.TRUE.toString());
        computeState.documentSelfLink = UriUtils
                .buildUriPath(ComputeService.FACTORY_LINK, computeState.id);
        computeState.endpointLink = primaryAccountCompute.endpointLink;
        return computeState;
    }

    private String generateUuidFromStr(String linkedAccountId) {
        return UUID.nameUUIDFromBytes(linkedAccountId.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
    }
}