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

package com.vmware.photon.controller.model.adapters.azure.enumeration;

import static com.vmware.photon.controller.model.adapters.azure.enumeration.AzureEnumerationAdapterService.AzureEnumerationStages.TRIGGER_RESOURCE_GROUP_ENUMERATION;
import static com.vmware.photon.controller.model.adapters.azure.enumeration.AzureEnumerationAdapterService.AzureEnumerationStages.TRIGGER_STORAGE_ENUMERATION;

import java.util.concurrent.ConcurrentHashMap;

import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.BaseAdapterStage;
import com.vmware.photon.controller.model.adapters.util.ComputeEnumerateAdapterRequest;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;

/**
 * Enumeration Adapter for Azure. Performs a list call to the Azure API and reconciles the local
 * state with the state on the remote system. It lists the instances on the remote system. Compares
 * those with the local system, creates the instances that are missing in the local system, and
 * deletes the ones that no longer exist in the Azure environment.
 */
public class AzureEnumerationAdapterService extends StatelessService {
    public static final String SELF_LINK = AzureUriPaths.AZURE_ENUMERATION_ADAPTER;
    private ConcurrentHashMap<String, String> ongoingEnumerations = new ConcurrentHashMap<>();

    /**
     * The enumeration service context needed trigger adapters for Azure.
     */
    public static class EnumerationContext extends BaseAdapterContext<EnumerationContext> {
        public ComputeEnumerateResourceRequest request;
        public AzureEnumerationStages stage;

        public EnumerationContext(StatelessService service, ComputeEnumerateResourceRequest request,
                Operation op) {
            super(service, request);

            this.request = request;
            this.stage = TRIGGER_RESOURCE_GROUP_ENUMERATION;
            this.operation = op;
        }
    }

    public enum AzureEnumerationStages {
        TRIGGER_RESOURCE_GROUP_ENUMERATION,
        TRIGGER_STORAGE_ENUMERATION,
        TRIGGER_FIREWALL_ENUMERATION,
        TRIGGER_NETWORK_ENUMERATION,
        TRIGGER_COMPUTE_ENUMERATION,
        FINISHED,
        ERROR
    }

    public AzureEnumerationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handleStart(Operation startPost) {
        startHelperServices(startPost);
    }

    @Override
    public void handlePatch(Operation op) {
        setOperationHandlerInvokeTimeStat(op);
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        ComputeEnumerateResourceRequest request = op.getBody(ComputeEnumerateResourceRequest.class);
        try {
            AdapterUtils.validateEnumRequest(request);
            op.complete();
        } catch (Exception e) {
            op.fail(e);
        }
        EnumerationContext context = new EnumerationContext(this, request, op);
        if (request.isMockRequest) {
            // patch status to parent task
            context.taskManager.finishTask();
            return;
        }

        context.populateBaseContext(BaseAdapterStage.PARENTDESC)
                .whenComplete((ignoreCtx, t) -> {
                    // NOTE: In case of error 'ignoreCtx' is null so use passed context!
                    if (t != null) {
                        context.error = t;
                        context.stage = AzureEnumerationStages.ERROR;
                        handleEnumerationRequest(context);
                        return;
                    }
                    String enumKey = context.request.getEnumKey();
                    String ongoing = this.ongoingEnumerations.putIfAbsent(enumKey, enumKey);
                    if (ongoing == null) {
                        handleEnumerationRequest(context);
                    } else {
                        logInfo(() -> String.format(
                                "There is already an ongoing enumeration for endpoint:[%s], resourcePool:[%s]",
                                context.request.endpointLink, context.request.resourcePoolLink));
                        setOperationDurationStat(context.operation);
                        context.taskManager.finishTask();
                    }
                });
    }

    /**
     * Starts the related services for the Enumeration Service
     */
    public void startHelperServices(Operation startPost) {

        Operation postComputeEnumAdapterService = Operation
                .createPost(this, AzureComputeEnumerationAdapterService.SELF_LINK)
                .setReferer(this.getUri());

        Operation postStorageEnumAdapterService = Operation
                .createPost(this, AzureStorageEnumerationAdapterService.SELF_LINK)
                .setReferer(this.getUri());

        Operation postSecurityGroupEnumAdapterService = Operation
                .createPost(this, AzureSecurityGroupEnumerationAdapterService.SELF_LINK)
                .setReferer(this.getUri());

        Operation postNetworkEnumAdapterService = Operation
                .createPost(this, AzureNetworkEnumerationAdapterService.SELF_LINK)
                .setReferer(this.getUri());

        Operation postResourceGroupEnumAdapterService = Operation
                .createPost(this, AzureResourceGroupEnumerationAdapterService.SELF_LINK)
                .setReferer(this.getUri());

        this.getHost().startService(postComputeEnumAdapterService,
                new AzureComputeEnumerationAdapterService());
        this.getHost().startService(postStorageEnumAdapterService,
                new AzureStorageEnumerationAdapterService());
        this.getHost().startService(postSecurityGroupEnumAdapterService,
                new AzureSecurityGroupEnumerationAdapterService());
        this.getHost().startService(postNetworkEnumAdapterService,
                new AzureNetworkEnumerationAdapterService());
        this.getHost().startService(postResourceGroupEnumAdapterService,
                new AzureResourceGroupEnumerationAdapterService());

        AdapterUtils.registerForServiceAvailability(getHost(),
                operation -> startPost.complete(), startPost::fail,
                AzureComputeEnumerationAdapterService.SELF_LINK,
                AzureStorageEnumerationAdapterService.SELF_LINK,
                AzureSecurityGroupEnumerationAdapterService.SELF_LINK,
                AzureNetworkEnumerationAdapterService.SELF_LINK,
                AzureResourceGroupEnumerationAdapterService.SELF_LINK);
    }

    /**
     * Creates operations to trigger off adapter services in parallel
     */
    public void handleEnumerationRequest(EnumerationContext context) {
        switch (context.stage) {
        case TRIGGER_RESOURCE_GROUP_ENUMERATION:
            triggerEnumerationAdapter(context,
                    AzureResourceGroupEnumerationAdapterService.SELF_LINK,
                    TRIGGER_STORAGE_ENUMERATION);
            break;
        case TRIGGER_STORAGE_ENUMERATION:
            triggerEnumerationAdapter(context, AzureStorageEnumerationAdapterService.SELF_LINK,
                    AzureEnumerationStages.TRIGGER_FIREWALL_ENUMERATION);
            break;
        case TRIGGER_FIREWALL_ENUMERATION:
            triggerEnumerationAdapter(context,
                    AzureSecurityGroupEnumerationAdapterService.SELF_LINK,
                    AzureEnumerationStages.TRIGGER_NETWORK_ENUMERATION);
            break;
        case TRIGGER_NETWORK_ENUMERATION:
            triggerEnumerationAdapter(context, AzureNetworkEnumerationAdapterService.SELF_LINK,
                    AzureEnumerationStages.TRIGGER_COMPUTE_ENUMERATION);
            break;
        case TRIGGER_COMPUTE_ENUMERATION:
            triggerEnumerationAdapter(context, AzureComputeEnumerationAdapterService.SELF_LINK,
                    AzureEnumerationStages.FINISHED);
            break;
        case FINISHED:
            logInfo(() -> String.format("Azure enumeration %s completed", context.request.getEnumKey()));
            setOperationDurationStat(context.operation);
            this.ongoingEnumerations.remove(context.request.getEnumKey());
            context.taskManager.finishTask();
            break;
        case ERROR:
            logSevere(() -> String.format("Azure enumeration %s, error: %s ", context.request.getEnumKey(),
                    Utils.toString(context.error)));
            this.ongoingEnumerations.remove(context.request.getEnumKey());
            context.taskManager.patchTaskToFailure(context.error);
            break;
        default:
            logSevere(() -> String.format("Unknown Azure enumeration %s stage %s ",
                    context.request.getEnumKey(),
                    context.stage.toString()));
            context.error = new Exception("Unknown Azure enumeration stage");
            this.ongoingEnumerations.remove(context.request.getEnumKey());
            context.taskManager.patchTaskToFailure(context.error);
            break;
        }
    }

    /**
     * Trigger specified enumeration adapter
     */
    public void triggerEnumerationAdapter(EnumerationContext context, String adapterSelfLink,
            AzureEnumerationStages next) {
        Operation.CompletionHandler completionHandler = (o, e) -> {
            if (e != null) {
                String error = String
                        .format("Error triggering Azure enumeration adapter %s", adapterSelfLink);
                logSevere(error);
                context.error = new IllegalStateException(error);
                context.taskManager.patchTaskToFailure(context.error);
                return;
            }
            logFine(() -> String.format("Completed Azure enumeration adapter %s", adapterSelfLink));
            context.stage = next;
            handleEnumerationRequest(context);
        };

        ComputeEnumerateAdapterRequest azureEnumerationRequest = new ComputeEnumerateAdapterRequest(
                context.request, context.parentAuth,
                context.parent);

        Operation.createPatch(this, adapterSelfLink)
                .setBody(azureEnumerationRequest)
                .setCompletion(completionHandler)
                .sendWith(this);
        logInfo(() -> String.format("Triggered Azure enumeration adapter %s", adapterSelfLink));
    }
}
