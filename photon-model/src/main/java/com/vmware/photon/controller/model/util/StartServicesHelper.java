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

package com.vmware.photon.controller.model.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ReflectionUtils;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * The goal of this helper class is to ease the start of family of services (such as
 * {@link com.vmware.photon.controller.model.PhotonModelServices}) and at the same time expose their
 * public links.
 *
 * <p>
 * Impl notes: As of now the approach is to create {@code public static String[] LINKS} var holding
 * services links and {@code public static void startServices(ServiceHost host)} method starting all
 * the services. And the expectation is that the set of links and the set of started services should
 * be the same. In order to enforce that we introduce {@link ServiceMetadata} that is used to
 * calculate the links and start the services, thus the client of the class is focused on just
 * describing its set of services.
 */
public class StartServicesHelper {

    /**
     * Use this method to get the set of service links.
     *
     * @see com.vmware.photon.controller.model.PhotonModelServices#LINKS
     */
    public static String[] getServiceLinks(ServiceMetadata[] servicesMetadata) {
        return Arrays.stream(servicesMetadata)
                .map(ServiceMetadata::getLink)
                .toArray(String[]::new);
    }

    /**
     * Use this method to start the set of services asynchronously.
     *
     * @see com.vmware.photon.controller.model.PhotonModelServices#startServices(ServiceHost)
     */
    public static void startServices(ServiceHost host, ServiceMetadata[] servicesMetadata) {

        startServices(host, tryAddPrivilegedService(host), servicesMetadata);
    }

    /**
     * Use this method to start the set of services asynchronously.
     *
     * @see com.vmware.photon.controller.model.PhotonModelServices#startServices(ServiceHost)
     */
    public static void startServices(
            ServiceHost host,
            Consumer<Class<? extends Service>> addPrivilegedService,
            ServiceMetadata[] servicesMetadata) {

        Arrays.stream(servicesMetadata)
                .forEach(serviceMetadata -> serviceMetadata.start(host, addPrivilegedService));
    }

    /**
     * Use this method to start the set of services synchronously.
     */
    public static void startServicesSynchronously(
            ServiceHost host,
            ServiceMetadata[] servicesMetadata) {

        startServicesSynchronously(host, tryAddPrivilegedService(host), servicesMetadata);
    }

    /**
     * Use this method to start the set of services synchronously.
     */
    public static void startServicesSynchronously(
            ServiceHost host,
            Consumer<Class<? extends Service>> addPrivilegedService,
            ServiceMetadata[] servicesMetadata) {
        try {
            for (ServiceMetadata serviceMetadata : servicesMetadata) {
                serviceMetadata.startSynchronously(host, addPrivilegedService);
            }
        } catch (Throwable t) {
            host.log(Level.SEVERE, "Failed starting service synchronously: %s", t.getMessage());
        }
    }

    /**
     * The following method is introduced to prevent touching tests to start services as
     * Privileged. It follows 'convention over configuration' principle. Passing any host that has
     * <b>public</b> {@code addPrivilegedService} method (such as {@code VerificationHost}) will
     * automatically use it if {@link ServiceMetadata#requirePrivileged}.
     *
     * <p>
     * In production code the EXPLICIT {@code addPrivilegedService} handler should be passed to
     * start the service.
     */
    private static Consumer<Class<? extends Service>> tryAddPrivilegedService(ServiceHost host) {

        final String addPrivilegedServiceName = "addPrivilegedService";

        Consumer<Class<? extends Service>> addPrivilegedService = null;
        try {
            Method addPrivilegedServiceMethod = host.getClass().getDeclaredMethod(
                    addPrivilegedServiceName, Class.class);

            if (Modifier.isPublic(addPrivilegedServiceMethod.getModifiers())) {

                addPrivilegedService = (Class<? extends Service> serviceClass) -> {
                    try {
                        addPrivilegedServiceMethod.invoke(host, serviceClass);

                        host.log(Level.INFO, "Auto starting of '%s' as Privileged by '%s': SUCCESS",
                                serviceClass.getSimpleName(),
                                host.getClass().getSimpleName());

                    } catch (Exception exc) {
                        host.log(Level.INFO, "Auto starting of '%s' as Privileged by '%s': FAILED",
                                serviceClass.getSimpleName(),
                                host.getClass().getSimpleName());
                    }
                };
            }
        } catch (Exception exc) {
            // since this is used by tests ONLY log with FINE level
            host.log(Level.FINE, "Auto starting of services as Privileged is not supported by '%s'",
                    host.getClass().getSimpleName());
        }

        return addPrivilegedService;
    }

    /**
     * A meta-data describing a service including:
     * <ul>
     * <li>service type (such as service or factory)</li>
     * <li>service class</li>
     * <li>service instantiation logic (applicable for factory services)</li>
     * </ul>
     */
    public static class ServiceMetadata {

        private static final Map<Class<? extends Service>, ServiceMetadata> servicesCache = new ConcurrentHashMap<>();

        private static final Map<Class<? extends Service>, ServiceMetadata> factoryServicesCache = new ConcurrentHashMap<>();

        /**
         * Creates meta-data for a {@link Service}.
         *
         * @return cached ServiceMetadata
         */
        public static ServiceMetadata service(Class<? extends Service> serviceClass) {

            return servicesCache.computeIfAbsent(
                    serviceClass,
                    key -> new ServiceMetadata(false /* isFactory */, serviceClass, null));
        }

        /**
         * Creates meta-data for a {@link FactoryService}.
         *
         * @return cached ServiceMetadata
         */
        public static ServiceMetadata factoryService(Class<? extends Service> serviceClass) {

            return factoryService(serviceClass, null /* factoryCreator */);
        }

        /**
         * Creates meta-data for a {@link FactoryService}.
         *
         * @return cached ServiceMetadata
         */
        public static ServiceMetadata factoryService(
                Class<? extends Service> serviceClass,
                Supplier<FactoryService> factoryCreator) {

            return factoryServicesCache.computeIfAbsent(
                    serviceClass,
                    key -> new ServiceMetadata(true /* isFactory */, serviceClass, factoryCreator));
        }

        /**
         * Indicates whether this meta-data represents specialized {@link FactoryService}.
         */
        public final boolean isFactory;

        public final Class<? extends Service> serviceClass;

        /**
         * Optional FactoryService creator applicable for factory service ({@link #isFactory} ==
         * true).
         */
        public final Supplier<FactoryService> factoryCreator;

        private final String link;

        /**
         * Extra properties specific/attached to this service metadata. Used for extensibility
         * purpose.
         */
        public final Map<String, Object> extension = new HashMap<>();

        /**
         * A flag indicating that this service require to be started as privileged. It is up to the
         * starter [host] whether to grant system privilege or not.
         *
         * <p>
         * Default value is {@code false}.
         */
        public boolean requirePrivileged = false;

        private ServiceMetadata(
                boolean isFactory,
                Class<? extends Service> serviceClass,
                Supplier<FactoryService> factoryCreator) {

            this.isFactory = isFactory;
            this.serviceClass = serviceClass;
            this.factoryCreator = factoryCreator;

            // Calculate and cache the link
            this.link = initLink();
        }

        @Override
        public String toString() {
            return "ServiceMetadata["
                    + (this.isFactory ? "factory" : "service") + ":"
                    + this.serviceClass.getSimpleName() + "]";
        }

        /**
         * Indicates this service require to be started as privileged or not.
         */
        public ServiceMetadata requirePrivileged(boolean requirePrivileged) {

            this.requirePrivileged = requirePrivileged;

            return this;
        }

        /**
         * Get (through reflection by analogy with UriUtils.buildFactoryUri) the SELF_LINK of a
         * service or the FACTORY_LINK of a factory service.
         */
        public String getLink() {
            return this.link;
        }

        /**
         * There's no guarantee that's the actual Xenon service being run. That's just a Java
         * instance of this service class most commonly used to call {@link Service#getStateType()}.
         */
        public Service serviceInstance() {
            // Every call MUST return new service instance cause the same service
            // might be run on two hosts
            return newServiceInstance();
        }

        /**
         * Get (through reflection by analogy with UriUtils.buildFactoryUri) the SELF_LINK of a
         * service or the FACTORY_LINK of a factory service.
         */
        private String initLink() throws IllegalAccessError {

            String selfLinkOrFactoryLinkName = this.isFactory
                    ? UriUtils.FIELD_NAME_FACTORY_LINK
                    : UriUtils.FIELD_NAME_SELF_LINK;

            try {
                Field selfLinkOrFactoryLink = this.serviceClass
                        .getDeclaredField(selfLinkOrFactoryLinkName);

                if (selfLinkOrFactoryLink != null) {
                    selfLinkOrFactoryLink.setAccessible(true);

                    return selfLinkOrFactoryLink.get(null).toString();
                }
            } catch (Exception e) {
            }

            throw new IllegalAccessError(String.format(
                    "'%s' service does not have public static '%s' field",
                    this.serviceClass,
                    selfLinkOrFactoryLinkName));
        }

        private Service newServiceInstance() throws InstantiationError {
            try {
                if (!this.isFactory) {
                    return this.serviceClass.newInstance();
                }

                if (this.factoryCreator == null) {
                    return this.serviceClass.newInstance();
                }

                FactoryService factoryService = this.factoryCreator.get();

                return factoryService.createServiceInstance();

            } catch (Throwable thr) {
                throw new InstantiationError("Failed to create an instance of "
                        + this.serviceClass
                        + ". Details: "
                        + Utils.toString(thr));
            }
        }

        /**
         * Start the service asynchronously considering its type.
         */
        private void start(
                ServiceHost serviceHost,
                Consumer<Class<? extends Service>> addPrivilegedService) {

            if (addPrivilegedService != null && this.requirePrivileged) {
                addPrivilegedService.accept(this.serviceClass);
            }

            if (!this.isFactory) {

                serviceHost.startService(serviceInstance());

            } else {
                if (this.factoryCreator == null) {

                    serviceHost.startFactory(serviceInstance());

                } else {

                    serviceHost.startFactory(this.serviceClass, this.factoryCreator);
                }
            }
        }

        /**
         * Start the service synchronously considering its type.
         */
        private void startSynchronously(
                ServiceHost serviceHost,
                Consumer<Class<? extends Service>> addPrivilegedService) throws Throwable {

            if (addPrivilegedService != null && this.requirePrivileged) {
                addPrivilegedService.accept(this.serviceClass);
            }

            Service newServiceInstance = serviceInstance();

            if (!this.isFactory) {
                startServicesSynchronously(serviceHost, newServiceInstance);
            } else {
                final FactoryService factoryService;
                if (this.factoryCreator == null) {
                    factoryService = FactoryService.create(
                            newServiceInstance.getClass(),
                            newServiceInstance.getStateType());
                } else {
                    factoryService = this.factoryCreator.get();
                }

                startServicesSynchronously(serviceHost, factoryService, this.serviceClass);
            }
        }

        private void startServicesSynchronously(ServiceHost serviceHost,
                FactoryService factoryService,
                Class<? extends Service> serviceClass) throws Throwable {
            URI factoryUri = UriUtils.buildFactoryUri(serviceHost, this.serviceClass);
            Operation post = Operation.createPost(UriUtils.buildUri(serviceHost,
                    factoryUri.getPath()));
            startServicesSynchronously(serviceHost, Collections.singletonList(post),
                    Collections.singletonList(factoryService));
        }

        private void startServicesSynchronously(ServiceHost serviceHost, Service... services)
                throws Throwable {
            List<Operation> posts = new ArrayList<>();
            for (Service s : services) {
                URI u = null;
                if (ReflectionUtils.hasField(s.getClass(), UriUtils.FIELD_NAME_SELF_LINK)) {
                    u = UriUtils.buildUri(serviceHost, s.getClass());
                } else if (s instanceof FactoryService) {
                    u = UriUtils.buildFactoryUri(serviceHost,
                            ((FactoryService) s).createServiceInstance().getClass());
                } else {
                    throw new IllegalStateException("field SELF_LINK or FACTORY_LINK is required");
                }
                Operation startPost = Operation.createPost(u);
                posts.add(startPost);
            }
            startServicesSynchronously(serviceHost, posts, Arrays.asList(services));
        }

        private void startServicesSynchronously(ServiceHost serviceHost, List<Operation> startPosts,
                List<Service> services)
                throws Throwable {
            CountDownLatch l = new CountDownLatch(services.size());
            Throwable[] failure = new Throwable[1];
            StringBuilder sb = new StringBuilder();

            CompletionHandler h = (o, e) -> {
                try {
                    if (e != null) {
                        failure[0] = e;
                        serviceHost.log(Level.SEVERE, "Service %s failed start: %s", o.getUri(), e);
                        return;
                    }

                    serviceHost.log(Level.FINE, "started %s", o.getUri().getPath());
                } finally {
                    l.countDown();
                }
            };
            int index = 0;

            for (Service s : services) {
                Operation startPost = startPosts.get(index++);
                startPost.setCompletion(h);
                sb.append(startPost.getUri().toString()).append(Operation.CR_LF);
                serviceHost.log(Level.FINE, "starting %s", startPost.getUri());
                serviceHost.startService(startPost, s);
            }

            if (!l.await(TimeUnit.SECONDS.toMicros(60), TimeUnit.MICROSECONDS)) {
                serviceHost.log(Level.SEVERE, "One of the services failed to start "
                        + "synchronously: %s",
                        sb.toString(),
                        new TimeoutException());
            }

            if (failure[0] != null) {
                throw failure[0];
            }
        }
    }

}
