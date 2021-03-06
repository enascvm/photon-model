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

package com.vmware.photon.controller.model.helpers;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import org.junit.Before;

import com.vmware.photon.controller.model.PhotonModelInMemoryServices;
import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.DiskInstanceRequest;
import com.vmware.photon.controller.model.resources.ComputeService;

import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Abstract base class that creates a DCP ServiceHost running all the model DCP
 * services for unit-tests.
 */
public abstract class BaseModelTest extends BasicReusableHostTestCase {

    public static void startFactories(BaseModelTest test) throws Throwable {
        if (test.getHost().getServiceStage(ComputeService.FACTORY_LINK) != null) {
            return;
        }
        PhotonModelServices.startServices(test.getHost());
        PhotonModelInMemoryServices.startServices(test.getHost());
        PhotonModelMetricServices.startServices(test.getHost());
        test.getHost().waitForServiceAvailable(PhotonModelServices.LINKS);
    }

    protected void startRequiredServices() throws Throwable {
        startFactories(this);
    }

    @Before
    public void setUp() throws Throwable {
        this.host.setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS.toMicros(250));
        startRequiredServices();
    }

    protected void waitForServiceAvailability(String... serviceLinks) throws Throwable {
        waitForServiceAvailability(host, serviceLinks);
    }

    protected void waitForServiceAvailability(ServiceHost h, String... serviceLinks)
            throws Throwable {
        if (serviceLinks == null || serviceLinks.length == 0) {
            throw new IllegalArgumentException("null or empty serviceLinks");
        }
        TestContext ctx = testCreate(serviceLinks.length);
        h.registerForServiceAvailability(ctx.getCompletion(), serviceLinks);
        ctx.await();
    }

    public <T extends ServiceDocument> T postServiceSynchronously(
            String serviceUri, T body, Class<T> type) throws Throwable {
        return postServiceSynchronously(serviceUri, body, type, null);
    }

    public <T extends ServiceDocument> T postServiceSynchronously(
            String serviceUri, T body, Class<T> type, Class<? extends Throwable> expectedException)
                    throws Throwable {

        AtomicReference<T> responseBody = new AtomicReference<>();
        TestContext ctx = this.host.testCreate(1);

        Operation postOperation = Operation
                .createPost(UriUtils.buildUri(this.host, serviceUri))
                .setBody(body)
                .setCompletion(
                        (operation, throwable) -> {

                            boolean failureExpected = (expectedException != null);
                            boolean failureReturned = (throwable != null);

                            if (failureExpected ^ failureReturned) {
                                Throwable t = throwable == null ? new IllegalArgumentException(
                                        "Call did not fail as expected")
                                        : throwable;

                                ctx.failIteration(t);
                                return;
                            }

                            if (failureExpected
                                    && expectedException != throwable
                                            .getClass()) {
                                ctx.failIteration(throwable);
                                return;
                            }

                            if (!failureExpected) {
                                responseBody.set(operation.getBody(type));
                            }
                            ctx.completeIteration();
                        });

        this.host.send(postOperation);
        this.host.testWait(ctx);

        return responseBody.get();
    }

    public <T> void patchServiceSynchronously(
            String serviceUri, T patchBody) throws Throwable {

        TestContext ctx = this.host.testCreate(1);

        Operation patchOperation = Operation
                .createPatch(UriUtils.buildUri(this.host, serviceUri))
                .setBody(patchBody)
                .setCompletion(ctx.getCompletion());

        this.host.send(patchOperation);
        this.host.testWait(ctx);
    }

    public <T> T patchServiceSynchronously(
            String serviceUri, T patchBody, Class<T> type) throws Throwable {
        AtomicReference<T> responseObj = new AtomicReference<>();

        TestContext ctx = this.host.testCreate(1);
        Operation patchOperation = Operation
                .createPatch(UriUtils.buildUri(this.host, serviceUri))
                .setBody(patchBody)
                .setCompletion(
                        (operation, throwable) -> {
                            if (throwable != null) {
                                ctx.failIteration(throwable);
                            } else {
                                responseObj.set(operation.getBody(type));
                                ctx.completeIteration();
                            }
                        });

        this.host.send(patchOperation);
        this.host.testWait(ctx);

        return responseObj.get();
    }

    public <T extends ServiceDocument> void putServiceSynchronously(
            String serviceUri, T putBody) throws Throwable {

        TestContext ctx = this.host.testCreate(1);

        Operation putOperation = Operation
                .createPut(UriUtils.buildUri(this.host, serviceUri))
                .setBody(putBody)
                .setCompletion(ctx.getCompletion());

        this.host.send(putOperation);
        this.host.testWait(ctx);
    }

    public Operation sendOperationSynchronously(Operation op) throws Throwable {
        final Operation[] returnedOp = { null };
        TestContext ctx = this.host.testCreate(1);
        op.setCompletion(
                (operation, throwable) -> {
                    returnedOp[0] = operation;
                    if (throwable != null) {
                        ctx.failIteration(throwable);
                    } else {
                        ctx.completeIteration();
                    }
                });

        this.host.send(op);
        this.host.testWait(ctx);
        return returnedOp[0];
    }

    public void testStart(int count) {
        this.host.testStart(count);
    }

    public void completeIteration() {
        this.host.completeIteration();
    }

    public void failIteration(Throwable e) {
        this.host.failIteration(e);
    }

    public void testWait() throws Throwable {
        this.host.testWait();
    }

    public void send(Operation op) {
        this.host.send(op);
    }

    public void sendAndWait(Operation op) throws Throwable {
        this.host.sendAndWait(op);
    }

    public CompletionHandler getCompletion() {
        return this.host.getCompletion();
    }

    public CompletionHandler getExpectedFailureCompletion() {
        return this.host.getExpectedFailureCompletion();
    }

    public <T extends ServiceDocument> int patchServiceSynchronously(
            String serviceUri, ComputeInstanceRequest patchBody)
                    throws Throwable {

        TestContext ctx = this.host.testCreate(1);

        Operation patchOperation = Operation
                .createPatch(UriUtils.buildUri(this.host, serviceUri))
                .setBody(patchBody)
                .setCompletion(ctx.getCompletion());

        this.host.send(patchOperation);
        this.testWait(ctx);
        return patchOperation.getStatusCode();
    }

    public <T extends ServiceDocument> int patchServiceSynchronously(
            String serviceUri, DiskInstanceRequest patchBody)
            throws Throwable {

        TestContext ctx = this.host.testCreate(1);

        Operation patchOperation = Operation
                .createPatch(UriUtils.buildUri(this.host, serviceUri))
                .setBody(patchBody)
                .setCompletion(ctx.getCompletion());

        this.host.send(patchOperation);
        this.testWait(ctx);
        return patchOperation.getStatusCode();
    }

    public <T extends ServiceDocument> T getServiceSynchronously(
            String serviceUri, Class<T> type) throws Throwable {

        AtomicReference<T> responseBody = new AtomicReference<>();

        TestContext ctx = this.host.testCreate(1);

        Operation getOperation = Operation
                .createGet(UriUtils.buildUri(this.host, serviceUri))
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setCompletion((operation, throwable) -> {
                    if (throwable != null) {
                        ctx.failIteration(throwable);
                        return;
                    }

                    responseBody.set(operation.getBody(type));
                    ctx.completeIteration();
                });

        this.send(getOperation);
        this.testWait(ctx);

        return responseBody.get();
    }

    public <T extends ServiceDocument> T getServiceSynchronously(
            String serviceUri, Class<T> type, Class<? extends Throwable> expectedException)
            throws Throwable {

        AtomicReference<T> responseBody = new AtomicReference<>();
        TestContext ctx = this.host.testCreate(1);

        Operation getOperation = Operation
                .createGet(UriUtils.buildUri(this.host, serviceUri))
                .setCompletion((operation, throwable) -> {

                    boolean failureExpected = (expectedException != null);
                    boolean failureReturned = (throwable != null);

                    if (failureExpected ^ failureReturned) {
                        Throwable t = throwable == null ? new IllegalArgumentException(
                                "Call did not fail as expected")
                                : throwable;

                        ctx.failIteration(t);
                        return;
                    }

                    if (failureExpected && expectedException != throwable.getClass()) {
                        ctx.failIteration(throwable);
                        return;
                    }

                    if (!failureExpected) {
                        responseBody.set(operation.getBody(type));
                    }

                    ctx.completeIteration();
                });

        this.send(getOperation);
        this.testWait(ctx);

        return responseBody.get();
    }

    private <T extends ServiceDocument> void deleteServiceSynchronously(
            String serviceUri, boolean stopOnly) throws Throwable {
        TestContext ctx = this.host.testCreate(1);

        Operation deleteOperation = Operation
                .createDelete(UriUtils.buildUri(this.host, serviceUri))
                .setCompletion((operation, throwable) -> {
                    if (throwable != null) {
                        ctx.failIteration(throwable);
                    }

                    ctx.completeIteration();
                });

        if (stopOnly) {
            deleteOperation
                    .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_NO_INDEX_UPDATE);
        }

        this.send(deleteOperation);
        this.testWait(ctx);
    }

    public <T extends ServiceDocument> void stopServiceSynchronously(
            String serviceUri) throws Throwable {
        deleteServiceSynchronously(serviceUri, true);
    }

    public <T extends ServiceDocument> void deleteServiceSynchronously(
            String serviceUri) throws Throwable {
        deleteServiceSynchronously(serviceUri, false);
    }

    public <T extends ServiceDocument> T waitForServiceState(Class<T> type,
            String serviceUri, Predicate<T> test) throws Throwable {
        Date exp = this.host.getTestExpiration();
        while (new Date().before(exp)) {
            T t = getServiceSynchronously(serviceUri, type);
            if (test.test(t)) {
                return t;
            }
            Thread.sleep(this.host.getMaintenanceIntervalMicros() / 1000);
        }

        throw new TimeoutException("timeout waiting for state transition for " + serviceUri);
    }

    public QueryTask createDirectQueryTask(String kind, String propertyName,
            String propertyValue) {
        QueryTask q = new QueryTask();
        q.querySpec = new QueryTask.QuerySpecification();
        q.taskInfo.isDirect = true;

        QueryTask.Query kindClause = new QueryTask.Query().setTermPropertyName(
                ServiceDocument.FIELD_NAME_KIND).setTermMatchValue(kind);
        kindClause.occurance = QueryTask.Query.Occurance.MUST_OCCUR;
        q.querySpec.query.addBooleanClause(kindClause);

        QueryTask.Query customPropClause = new QueryTask.Query()
                .setTermPropertyName(propertyName).setTermMatchValue(
                        propertyValue);
        customPropClause.occurance = QueryTask.Query.Occurance.MUST_OCCUR;
        q.querySpec.query.addBooleanClause(customPropClause);

        return q;
    }

    public QueryTask querySynchronously(QueryTask queryTask) throws Throwable {
        return postServiceSynchronously(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS,
                queryTask, QueryTask.class);
    }

    public VerificationHost getHost() {
        return this.host;
    }
}
