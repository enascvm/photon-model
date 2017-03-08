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

package com.vmware.photon.controller.model.security;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Before;

import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationProcessingChain;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.VerificationHost;

public abstract class BaseTestCase {

    protected static final int WAIT_FOR_STAGE_CHANGE_COUNT = Integer.getInteger(
            "dcp.management.test.change.count", 2500);
    protected static final int WAIT_THREAD_SLEEP_IN_MILLIS = Integer.getInteger(
            "dcp.management.test.wait.thread.sleep.millis", 20);
    private static final int HOST_TIMEOUT_SECONDS = 300;

    protected static final int MAINTENANCE_INTERVAL_MILLIS = 20;
    protected VerificationHost host;

    private static class CustomizationVerificationHost extends VerificationHost {
        private Map<Class<? extends Service>, Class<? extends OperationProcessingChain>> chains = new HashMap<>();

        public CustomizationVerificationHost(
                Map<Class<? extends Service>, Class<? extends OperationProcessingChain>> chains) {
            this.chains.putAll(chains);
        }

        @Override
        public ServiceHost startFactory(Service service) {
            Class<? extends Service> serviceClass = service.getClass();
            if (!applyOperationChainIfNeed(service, serviceClass, serviceClass, false)) {
                if (service instanceof FactoryService) {
                    try {
                        Service actualInstance = ((FactoryService) service).createServiceInstance();
                        Class<? extends Service> instanceClass = actualInstance.getClass();
                        applyOperationChainIfNeed(service, instanceClass, FactoryService.class,
                                false);
                    } catch (Throwable e) {
                        log(Level.SEVERE, "Failure: %s", Utils.toString(e));
                    }
                } else if (service instanceof StatefulService) {
                    applyOperationChainIfNeed(service, serviceClass, StatefulService.class,
                            true);
                }
            }
            return super.startFactory(service);
        }

        @Override
        public ServiceHost startService(Operation post, Service service) {
            Class<? extends Service> serviceClass = service.getClass();
            if (!applyOperationChainIfNeed(service, serviceClass, serviceClass, false)) {
                if (service instanceof FactoryService) {
                    try {
                        Service actualInstance = ((FactoryService) service).createServiceInstance();
                        Class<? extends Service> instanceClass = actualInstance.getClass();
                        applyOperationChainIfNeed(service, instanceClass, FactoryService.class,
                                false);
                    } catch (Throwable e) {
                        log(Level.SEVERE, "Failure: %s", Utils.toString(e));
                    }
                } else if (service instanceof StatefulService) {
                    applyOperationChainIfNeed(service, serviceClass, StatefulService.class,
                            true);
                }
            }
            return super.startService(post, service);
        }

        private boolean applyOperationChainIfNeed(Service service,
                Class<? extends Service> serviceClass, Class<? extends Service> parameterClass,
                boolean logOnError) {
            if (this.chains.containsKey(serviceClass)) {
                try {
                    service.setOperationProcessingChain(
                            this.chains.get(serviceClass)
                                    .getDeclaredConstructor(parameterClass)
                                    .newInstance(service));
                    return true;
                } catch (Exception e) {
                    if (logOnError) {
                        log(Level.SEVERE, "Failure: %s", Utils.toString(e));
                    }
                }
            }
            return false;
        }
    }

    @Before
    public void before() throws Throwable {
        this.host = createHost();
    }

    @After
    public void after() throws Throwable {
        try {
            this.host.tearDownInProcessPeers();
            this.host.tearDown();

        } catch (CancellationException e) {
            this.host.log(Level.FINE, e.getClass().getName());
        }
        this.host = null;
    }

    protected VerificationHost createHost() throws Throwable {
        ServiceHost.Arguments args = new ServiceHost.Arguments();
        args.sandbox = null; // ask runtime to pick a random storage location
        args.port = 0; // ask runtime to pick a random port
        Map<Class<? extends Service>, Class<? extends OperationProcessingChain>> chains = new HashMap<>();
        customizeChains(chains);

        VerificationHost h = VerificationHost.initialize(new CustomizationVerificationHost(chains),
                args);
        h.setMaintenanceIntervalMicros(this.getMaintenanceIntervalMillis() * 1000);
        h.setTimeoutSeconds(HOST_TIMEOUT_SECONDS);

        h.setPeerSynchronizationEnabled(this.getPeerSynchronizationEnabled());

        h.start();

        return h;
    }

    /**
     * Returns default peer synchronization flag. For hosts started in single mode it should be
     * false till https://www.pivotaltracker.com/n/projects/1471320/stories/138426713 is resolved.
     * <p/>
     * Tests for clustered nodes AND tests calling registerForServiceAvailability with
     * checkForReplica true SHOULD overwrite this method and return <code>true</code>.
     * @return boolean value
     */
    protected boolean getPeerSynchronizationEnabled() {
        return false;
    }

    /**
     * Returns maintenance interval millis to be set to the host
     * @return milliseconds
     */
    protected long getMaintenanceIntervalMillis() {
        return MAINTENANCE_INTERVAL_MILLIS;
    }

    protected void customizeChains(
            Map<Class<? extends Service>, Class<? extends OperationProcessingChain>> chains) {
    }

    public static TestContext testCreate(int count) {
        long expIntervalMicros = TimeUnit.MILLISECONDS.toMicros(
                WAIT_FOR_STAGE_CHANGE_COUNT * WAIT_THREAD_SLEEP_IN_MILLIS);
        return TestContext.create(count, expIntervalMicros);
    }

    protected void waitForServiceAvailability(String... serviceLinks) throws Throwable {
        waitForServiceAvailability(this.host, serviceLinks);
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

    protected void verifyService(Service factoryInstance,
            Class<? extends ServiceDocument> serviceDocumentType,
            TestServiceDocumentInitialization serviceDocumentInit,
            TestServiceDocumentAssertion assertion) throws Throwable {
        URI factoryUri = UriUtils.buildFactoryUri(this.host, ((FactoryService) factoryInstance)
                .createServiceInstance().getClass());
        verifyService(factoryUri, (FactoryService) factoryInstance, serviceDocumentType,
                serviceDocumentInit, assertion);
    }

    protected void verifyService(URI factoryUri, FactoryService factoryInstance,
            Class<? extends ServiceDocument> serviceDocumentType,
            TestServiceDocumentInitialization serviceDocumentInit,
            TestServiceDocumentAssertion assertion) throws Throwable {
        int childCount = 1;
        TestContext ctx = testCreate(childCount);
        String prefix = "example-";
        URI[] childURIs = new URI[childCount];
        for (int i = 0; i < childCount; i++) {
            ServiceDocument serviceDocument = serviceDocumentInit.create(prefix, i);
            final int finalI = i;
            // create a ServiceDocument instance.
            Operation createPost = createForcedPost(factoryUri)
                    .setBody(serviceDocument).setCompletion((o, e) -> {
                        if (e != null) {
                            ctx.failIteration(e);
                            return;
                        }
                        ServiceDocument rsp = o.getBody(serviceDocumentType);
                        childURIs[finalI] = UriUtils.buildUri(this.host, rsp.documentSelfLink);
                        ctx.completeIteration();
                    });
            this.host.send(createPost);
        }

        try {
            // verify factory and service instance wiring.
            factoryInstance.setHost(this.host);
            Service serviceInstance = factoryInstance.createServiceInstance();
            serviceInstance.setHost(this.host);
            assertNotNull(serviceInstance);

            ctx.await();

            // do GET on all child URIs
            Map<URI, ? extends ServiceDocument> childStates = this.host.getServiceState(null,
                    serviceDocumentType, childURIs);
            for (ServiceDocument s : childStates.values()) {
                assertion.assertState(prefix, s);
            }

            // verify template GET works on factory
            ServiceDocumentQueryResult templateResult = this.host.getServiceState(null,
                    ServiceDocumentQueryResult.class,
                    UriUtils.extendUri(factoryUri, ServiceHost.SERVICE_URI_SUFFIX_TEMPLATE));

            assertTrue(templateResult.documentLinks.size() == templateResult.documents.size());

            ServiceDocument childTemplate = Utils.fromJson(
                    templateResult.documents.get(templateResult.documentLinks.iterator().next()),
                    serviceDocumentType);

            assertTrue(childTemplate.documentDescription != null);
            assertTrue(childTemplate.documentDescription.propertyDescriptions != null
                    && childTemplate.documentDescription.propertyDescriptions
                    .size() > 0);

        } catch (Throwable t) {
            if (t instanceof RuntimeException) {
                throw t;
            }
            throw new RuntimeException(t);
        }
    }

    public interface TestServiceDocumentInitialization {
        ServiceDocument create(String prefix, int index) throws Throwable;
    }

    public interface TestServiceDocumentAssertion {
        void assertState(String prefix, ServiceDocument s) throws Throwable;
    }

    protected static AtomicInteger waitForStageChangeCount() {
        return new AtomicInteger(WAIT_FOR_STAGE_CHANGE_COUNT);
    }

    protected <T> T getDocument(Class<T> type, String selfLink) throws Throwable {
        return getDocument(type, selfLink, new String[0]);
    }

    protected <T> T getDocument(Class<T> type, String selfLink, String... keyValues)
            throws Throwable {
        testCreate(1);
        URI uri = UriUtils.buildUri(this.host, selfLink);
        uri = UriUtils.extendUriWithQuery(uri, keyValues);
        return getDocument(type, uri);
    }

    @SuppressWarnings("unchecked")
    protected <T> T getDocument(Class<T> type, URI uri)
            throws Throwable {
        TestContext ctx = testCreate(1);
        Object[] result = new Object[1];
        Operation get = Operation
                .createGet(uri)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setReferer(this.host.getReferer())
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                this.host.log("Can't load document %s. Error: %s", uri,
                                        Utils.toString(e));
                                ctx.failIteration(e);
                            } else {
                                result[0] = o.getBody(type);
                                ctx.completeIteration();
                            }
                        });

        this.host.send(get);
        ctx.await();
        return (T) result[0];
    }

    protected <T extends ServiceDocument> T doPost(T inState, String fabricServiceUrlPath)
            throws Throwable {
        return doPost(inState, UriUtils.buildUri(this.host, fabricServiceUrlPath), false);
    }

    protected <T extends ServiceDocument> T doPost(T inState, URI uri, boolean expectFailure)
            throws Throwable {
        String documentSelfLink = doOperation(inState, uri, expectFailure,
                Action.POST).documentSelfLink;
        @SuppressWarnings("unchecked")
        T outState = (T) this.host.getServiceState(null,
                inState.getClass(),
                UriUtils.buildUri(uri.getHost(), uri.getPort(), documentSelfLink, null));
        if (outState.documentSelfLink == null) {
            outState.documentSelfLink = documentSelfLink;
        }
        return outState;
    }

    @SuppressWarnings("unchecked")
    protected <T extends ServiceDocument> T doOperation(T inState, URI uri,
            boolean expectFailure, Action action) throws Throwable {
        return (T) doOperation(inState, uri, ServiceDocument.class, expectFailure, action);
    }

    protected <T extends ServiceDocument> T doOperation(T inState, URI uri, Class<T> type,
            boolean expectFailure, Action action) throws Throwable {
        this.host.log("Executing operation %s for resource: %s ...", action.name(), uri);
        final List<T> doc = Arrays.asList((T) null);
        final Throwable[] error = { null };
        TestContext ctx = testCreate(1);

        Operation op;
        if (action == Action.POST) {
            op = createForcedPost(uri);
        } else {
            // createPost sets the proper authorization context for the operation
            op = Operation.createPost(uri);
            // replace POST with the provided action
            op.setAction(action);
        }

        op.addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY);

        op.setBody(inState)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                if (expectFailure) {
                                    error[0] = e;
                                    ctx.completeIteration();
                                } else {
                                    ctx.failIteration(e);
                                }
                                return;
                            }
                            if (!o.hasBody()) {
                                ctx.failIteration(new IllegalStateException("body was expected"));
                                return;
                            }
                            doc.set(0, o.getBody(type));
                            if (expectFailure) {
                                ctx.failIteration(new IllegalStateException(
                                        "ERROR: operation completed successfully but exception excepted."));
                            } else {
                                ctx.completeIteration();
                            }
                        });
        this.host.send(op);
        ctx.await();
        this.host.logThroughput();

        if (expectFailure) {
            Throwable ex = error[0];
            throw ex;
        }
        return doc.get(0);
    }

    protected void validateLocalizableException(ExceptionHandler handler,
            String expectation)
            throws Throwable {
        try {
            handler.call();
            fail("LocalizableValidationException expected: " + expectation);
        } catch (LocalizableValidationException e) {
            // expected
        }
    }

    protected void validateIllegalArgumentException(ExceptionHandler handler,
            String expectation)
            throws Throwable {
        try {
            handler.call();
            fail("IllegalArgumentException expected: " + expectation);
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @FunctionalInterface
    protected interface ExceptionHandler {
        void call() throws Throwable;
    }

    public static Operation createForcedPost(URI uri) {
        return Operation.createPost(uri)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE);
    }

}
