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

package com.vmware.photon.controller.model.tasks;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourceState;

import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskService.TaskServiceState;

/**
 * Helper class for VM provisioning tests.
 */
public class ProvisioningUtils {

    public static int getVMCount(VerificationHost host, URI peerURI) throws Throwable {
        ServiceDocumentQueryResult res;
        res = host.getFactoryState(UriUtils
                .buildExpandLinksQueryUri(createServiceURI(host, peerURI,
                        ComputeService.FACTORY_LINK)));
        return res.documents.size() - 1;

    }

    public static void waitForTaskCompletion(VerificationHost host, String endpoint,
            Class<? extends TaskServiceState> clazz)
            throws Throwable, InterruptedException, TimeoutException {
        waitForTaskCompletion(host, Arrays.asList(UriUtils.buildUri(host, endpoint)), clazz);
    }

    public static void waitForTaskCompletion(VerificationHost host,
            List<URI> provisioningTasks, Class<? extends TaskServiceState> clazz)
            throws Throwable, InterruptedException, TimeoutException {
        Date expiration = host.getTestExpiration();
        List<String> pendingTasks = new ArrayList<String>();
        do {
            pendingTasks.clear();
            // grab in parallel, all task state, from all running tasks
            Map<URI, ? extends TaskServiceState> taskStates = host.getServiceState(null,
                    clazz,
                    provisioningTasks);

            boolean isConverged = true;
            for (Entry<URI, ? extends TaskServiceState> e : taskStates
                    .entrySet()) {
                TaskServiceState currentState = e.getValue();

                if (currentState.taskInfo.stage == TaskState.TaskStage.FAILED) {
                    throw new IllegalStateException(
                            "Task failed:" + Utils.toJsonHtml(currentState));
                }

                if (currentState.taskInfo.stage != TaskState.TaskStage.FINISHED) {
                    pendingTasks.add(currentState.documentSelfLink);
                    isConverged = false;
                }
            }

            if (isConverged) {
                return;
            }

            Thread.sleep(1000);
        } while (new Date().before(expiration));

        for (String taskLink : pendingTasks) {
            host.log("Pending task:\n%s", taskLink);
        }

        throw new TimeoutException("Some tasks never finished");
    }

    public static ServiceDocumentQueryResult queryComputeInstances(VerificationHost host,
            int desiredCount)
            throws Throwable {
        return queryComputeInstances(host, host.getUri(), desiredCount);
    }

    public static ServiceDocumentQueryResult queryComputeInstances(VerificationHost host,
            URI peerURI, int desiredCount)
            throws Throwable {
        Date expiration = host.getTestExpiration();
        ServiceDocumentQueryResult res;
        do {
            res = host.getFactoryState(UriUtils
                    .buildExpandLinksQueryUri(createServiceURI(host, peerURI,
                            ComputeService.FACTORY_LINK)));
            if (res.documents.size() == desiredCount) {
                return res;
            }
        } while (new Date().before(expiration));
        throw new TimeoutException("Desired number of compute states not found. Expected "
                + desiredCount + ", found " + res.documents.size());
    }

    public static ServiceDocumentQueryResult queryComputeInstancesByType(VerificationHost host,
            long desiredCount, String type, boolean exactCountFlag) throws Throwable {
        QueryTask.Query kindClause = new QueryTask.Query().setTermPropertyName(
                ServiceDocument.FIELD_NAME_KIND).setTermMatchValue(
                Utils.buildKind(ComputeState.class));
        kindClause.occurance = Occurance.MUST_OCCUR;

        QueryTask.Query typeClause = new QueryTask.Query().setTermPropertyName(
                ComputeState.FIELD_NAME_TYPE).setTermMatchValue(type);
        kindClause.occurance = Occurance.MUST_OCCUR;

        QueryTask.QuerySpecification querySpec = new QueryTask.QuerySpecification();
        querySpec.query.addBooleanClause(kindClause);
        querySpec.query.addBooleanClause(typeClause);

        QueryTask[] tasks = new QueryTask[1];
        host.waitFor("", () -> {
            QueryTask task = QueryTask.create(querySpec).setDirect(true);
            host.createQueryTaskService(UriUtils.buildUri(host, ServiceUriPaths.CORE_QUERY_TASKS),
                    task, false, true, task, null);
            if (exactCountFlag) {
                if (task.results.documentLinks.size() == desiredCount) {
                    tasks[0] = task;
                    return true;
                }
            } else {
                if (task.results.documentLinks.size() >= desiredCount) {
                    tasks[0] = task;
                    return true;
                }
            }
            host.log("Expected %d, got %d, Query task: %s", desiredCount,
                    task.results.documentLinks.size(), task);
            return false;
        });

        QueryTask resultTask = tasks[0];
        return resultTask.results;
    }

    /**
     * Queries the documents and ensures expected count. If <code>exactCountFlag</code> is
     * <code>true</code> the <code>desiredCount</code> should be equals to the documents found,
     * in case it is <code>false</code> then  <code>desiredCount</code> the minimal number of
     * documents that should be loaded.
     *
     * @param host           the host
     * @param desiredCount   expected count of documents
     * @param factoryLink    factory link for the documents
     * @param exactCountFlag if true the documents count should be equal to the
     *                       <code>desiredCount</code>, else documents should be more then
     *                       <code>desiredCount</code>
     * @return the documents
     */
    public static ServiceDocumentQueryResult queryDocumentsAndAssertExpectedCount(
            VerificationHost host, int desiredCount, String factoryLink, boolean exactCountFlag)
            throws Throwable {
        return queryDocumentsAndAssertExpectedCount(
                host, host.getUri(), desiredCount, factoryLink, exactCountFlag);
    }

    public static ServiceDocumentQueryResult queryDocumentsAndAssertExpectedCount(
            VerificationHost host, URI peerURI,
            int desiredCount, String factoryLink, boolean exactCountFlag) throws Throwable {
        URI queryUri = UriUtils.buildExpandLinksQueryUri(
                createServiceURI(host, peerURI, factoryLink));

        // add limit, otherwise the query will not return if there are too many docs or versions
        queryUri = UriUtils.extendUriWithQuery(queryUri,
                UriUtils.URI_PARAM_ODATA_LIMIT, String.valueOf(desiredCount * 2));
        ServiceDocumentQueryResult res = host.getFactoryState(queryUri);
        if (exactCountFlag) {
            if (res.documents.size() == desiredCount) {
                return res;
            }
        } else {
            if (res.documents.size() >= desiredCount) {
                host.log(Level.INFO, "Documents count in %s is %s, expected at least %s",
                        factoryLink, res.documents.size(), desiredCount);
                return res;
            }
        }
        throw new Exception("Desired number of documents not found in " + factoryLink
                + " factory states. Expected " + desiredCount + ", found " + res.documents.size());
    }

    /**
     * Query all resources for a factory
     */
    public static ServiceDocumentQueryResult queryAllFactoryResources(VerificationHost host, String factoryLink)
            throws Throwable {
        URI queryUri = UriUtils.buildExpandLinksQueryUri(
                createServiceURI(host, host.getUri(), factoryLink));

        ServiceDocumentQueryResult res = host.getFactoryState(queryUri);
        return res;
    }

    /**
     * Query all States for the given Service
     */
    public static <K extends ResourceState> Map<String, K> getResourceStates(VerificationHost host,
            String serviceFactoryLink, Class<K> typeKey)
            throws Throwable {
        return getResourceStates(host, null, serviceFactoryLink, typeKey);
    }

    public static <K extends ResourceState> Map<String, K> getResourceStates(VerificationHost host,
            URI peerURI, String serviceFactoryLink, Class<K> resourceStateClass)
            throws Throwable {
        Map<String, K> elementStateMap = new HashMap<>();
        ServiceDocumentQueryResult res;
        res = host.getFactoryState(UriUtils
                .buildExpandLinksQueryUri(createServiceURI(host, peerURI,
                        serviceFactoryLink)));
        if (res != null && res.documentCount > 0) {
            for (Object s : res.documents.values()) {
                K elementState = Utils.fromJson(s, resourceStateClass);
                elementStateMap.put(elementState.id, elementState);
            }
        }
        return elementStateMap;
    }

    /**
     * Creates the URI for the service based on the passed in values of the verification host and the peer URI.
     * If the peerURI value is set, then it is used to create the service URI else the verification host URI
     * is used to compute the service URI.
     */
    public static URI createServiceURI(VerificationHost host, URI peerURI, String factoryLink) {
        URI uri = (peerURI != null) ? UriUtils.buildUri(peerURI,
                factoryLink) : UriUtils.buildUri(host, factoryLink);
        return uri;
    }

}
