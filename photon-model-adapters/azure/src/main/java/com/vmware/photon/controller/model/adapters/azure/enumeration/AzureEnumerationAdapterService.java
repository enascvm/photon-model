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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

/**
 * Enumeration Adapter for Azure. Performs a list call to the Azure API
 * and reconciles the local state with the state on the remote system. It lists the instances on the remote system.
 * Compares those with the local system, creates the instances that are missing in the local system, and deletes the
 * ones that no longer exist in the Azure environment.
 */
public class AzureEnumerationAdapterService extends StatelessService {
    public static final String SELF_LINK = AzureUriPaths.AZURE_ENUMERATION_ADAPTER;
    public static final Integer SERVICES_TO_REGISTER = 3;

    public AzureEnumerationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    public static enum AzureEnumerationStages {
        TRIGGER_STORAGE_ENUMERATION,
        TRIGGER_NETWORK_ENUMERATION,
        TRIGGER_COMPUTE_ENUMERATION,
        FINISHED,
        ERROR
    }

    /**
     * The enumeration service context needed trigger adapters for Azure.
     */
    public static class EnumerationContext {
        public ComputeEnumerateResourceRequest computeEnumerationRequest;
        public AzureEnumerationStages stage;
        public Throwable error;
        public Operation azureAdapterOperation;

        public EnumerationContext(ComputeEnumerateResourceRequest request, Operation op) {
            this.computeEnumerationRequest = request;
            this.stage = AzureEnumerationStages.TRIGGER_STORAGE_ENUMERATION;
            this.azureAdapterOperation = op;
        }
    }

    @Override
    public void handleStart(Operation startPost) {
        startHelperServices();
        super.handleStart(startPost);
    }

    @Override
    public void handlePatch(Operation op) {
        setOperationHandlerInvokeTimeStat(op);
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        op.complete();

        EnumerationContext azureEnumerationContext  = new EnumerationContext(
                op.getBody(ComputeEnumerateResourceRequest.class), op);
        AdapterUtils.validateEnumRequest(azureEnumerationContext .computeEnumerationRequest);
        handleEnumerationRequest(azureEnumerationContext );
    }

    /**
     * Starts the related services for the Enumeration Service
     */
    public void startHelperServices() {
        Operation postComputeEnumAdapterService = Operation
                .createPost(this, AzureComputeEnumerationAdapterService.SELF_LINK)
                .setReferer(this.getUri());

        Operation postStorageEnumAdapterService = Operation
                .createPost(this, AzureStorageEnumerationAdapterService.SELF_LINK)
                .setReferer(this.getUri());

        Operation postNetworkEnumAdapterService = Operation
                .createPost(this, AzureNetworkEnumerationAdapterService.SELF_LINK)
                .setReferer(this.getUri());

        this.getHost().startService(postComputeEnumAdapterService,
                new AzureComputeEnumerationAdapterService());
        this.getHost().startService(postStorageEnumAdapterService,
                new AzureStorageEnumerationAdapterService());
        this.getHost().startService(postNetworkEnumAdapterService,
                new AzureNetworkEnumerationAdapterService());


        AtomicInteger completionCount = new AtomicInteger(0);
        getHost().registerForServiceAvailability((o, e) -> {
            if (e != null) {
                String message = "Failed to start up all the services related to the Azure Enumeration Adapter Service";
                this.logInfo(message);
                throw new IllegalStateException(message);
            }
            if (completionCount.incrementAndGet() == SERVICES_TO_REGISTER) {
                this.logFine("Successfully started up all Azure Enumeration Adapter Services");
            }
        }, AzureComputeEnumerationAdapterService.SELF_LINK,
                AzureStorageEnumerationAdapterService.SELF_LINK);
    }

    /**
     * Creates operations to trigger off adapter services in parallel
     *
     */
    public void handleEnumerationRequest(EnumerationContext context) {
        switch (context.stage) {
        case TRIGGER_STORAGE_ENUMERATION:
            triggerEnumerationAdapter(context, AzureStorageEnumerationAdapterService.SELF_LINK,
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
            setOperationDurationStat(context.azureAdapterOperation);
            AdapterUtils.sendPatchToEnumerationTask(this,
                    context.computeEnumerationRequest.taskReference);
            break;
        case ERROR:
            AdapterUtils.sendFailurePatchToEnumerationTask(this,
                    context.computeEnumerationRequest.taskReference, context.error);
            break;
        default:
            logSevere("Unknown Azure enumeration stage %s ", context.stage.toString());
            context.error = new Exception("Unknown Azure enumeration stage");
            AdapterUtils.sendFailurePatchToEnumerationTask(this,
                    context.computeEnumerationRequest.taskReference, context.error);
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
                String error = String.format("Error triggering Azure enumeration adapter %s", adapterSelfLink);
                logSevere(error);
                context.error = new IllegalStateException(error);
                AdapterUtils.sendFailurePatchToEnumerationTask(this,
                        context.computeEnumerationRequest.taskReference,
                        context.error);
                return;
            }

            logInfo("Successfully completed Azure enumeration adapter %s", adapterSelfLink);
            context.stage = next;
            handleEnumerationRequest(context);
            return;
        };

        Operation patchEnumAdapterService = Operation
                .createPatch(this, adapterSelfLink)
                .setBody(context.computeEnumerationRequest)
                .setReferer(this.getUri());

        patchEnumAdapterService
                .setCompletion(completionHandler)
                .sendWith(getHost());
        logInfo("Triggered Azure enumeration adapter %s", adapterSelfLink);
    }

    /**
     * Method to get the failed consumer to handle the error that was raised.
     * @param ctx The enumeration context
     * @return
     */
    private Consumer<Throwable> getFailureConsumer(EnumerationContext ctx) {
        return (t) -> {
            ctx.error = t;
            ctx.stage = AzureEnumerationStages.ERROR;
            handleEnumerationRequest(ctx);
        };
    }
}
