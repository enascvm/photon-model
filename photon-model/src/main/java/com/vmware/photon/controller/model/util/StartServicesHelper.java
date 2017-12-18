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
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.Level;

import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ReflectionUtils;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;

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
        Arrays.stream(servicesMetadata).forEach(serviceDesc -> serviceDesc.start(host));
    }

    /**
     * Use this method to start the set of services synchronously.
     */
    public static void startServicesSynchronously(ServiceHost host, ServiceMetadata[] servicesMetadata) {
        try {
            for (ServiceMetadata serviceMetadata : servicesMetadata) {
                serviceMetadata.startSynchronously(host);
            }
        } catch (Throwable t) {
            host.log(Level.SEVERE, "Failed starting service synchronously: %s", t.getMessage());
        }
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

        /**
         * Create meta-data for a {@link Service}.
         */
        public static ServiceMetadata service(Class<? extends Service> serviceClass) {

            final ServiceMetadata serviceDesc = new ServiceMetadata();

            serviceDesc.isServiceOrFactory = true;
            serviceDesc.serviceClass = serviceClass;

            return serviceDesc;
        }

        /**
         * Create meta-data for a {@link FactoryService}.
         */
        public static ServiceMetadata factoryService(
                Class<? extends Service> serviceClass,
                Supplier<FactoryService> factoryCreator) {

            final ServiceMetadata serviceDesc = new ServiceMetadata();

            serviceDesc.isServiceOrFactory = false;
            serviceDesc.serviceClass = serviceClass;
            serviceDesc.factoryCreator = factoryCreator;

            return serviceDesc;
        }

        /**
         * Create meta-data for a {@link FactoryService}.
         */
        public static ServiceMetadata factoryService(Class<? extends Service> serviceClass) {

            return factoryService(serviceClass, null);
        }

        // true = service; false = factory service
        private boolean isServiceOrFactory;

        private Class<? extends Service> serviceClass;

        private Supplier<FactoryService> factoryCreator;

        private ConcurrentSkipListSet<String> servicesToStartSynchronously =
                new ConcurrentSkipListSet<>();

        /**
         * Get (through reflection by analogy with UriUtils.buildFactoryUri) the SELF_LINK of a
         * service or the FACTORY_LINK of a factory service.
         */
        public String getLink() {
            String selfLinkOrFactoryLinkName = this.isServiceOrFactory
                    ? UriUtils.FIELD_NAME_SELF_LINK
                    : UriUtils.FIELD_NAME_FACTORY_LINK;

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

        /**
         * Start the service asynchronously considering its type.
         */
        private void start(ServiceHost serviceHost) {
            if (this.isServiceOrFactory) {

                serviceHost.startService(newServiceInstance());

            } else {
                if (this.factoryCreator == null) {

                    serviceHost.startFactory(newServiceInstance());

                } else {

                    serviceHost.startFactory(this.serviceClass, this.factoryCreator);
                }
            }
        }

        /**
         * Start the service synchronously considering its type.
         */
        private void startSynchronously(ServiceHost serviceHost) throws Throwable {

            if (this.isServiceOrFactory) {
                startServicesSynchronously(serviceHost, newServiceInstance());
            } else {
                FactoryService factoryService;
                if (this.factoryCreator == null) {
                    final Class<? extends Service> serviceClass = newServiceInstance().getClass();
                    factoryService = FactoryService.create(serviceClass,
                            newServiceInstance().getStateType());
                } else {
                    factoryService = this.factoryCreator.get();
                }

                startServicesSynchronously(serviceHost, factoryService,
                        this.serviceClass);
            }
        }

        private Service newServiceInstance() {
            try {
                return this.serviceClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new IllegalAccessError(
                        "Failed to create an instance of " + this.serviceClass);
            }
        }

        private void startServicesSynchronously(ServiceHost serviceHost, FactoryService factoryService,
                Class<? extends Service> serviceClass) throws Throwable {
            URI factoryUri = UriUtils.buildFactoryUri(serviceHost, this.serviceClass);
            Operation post = Operation.createPost(UriUtils.buildUri(serviceHost,
                    factoryUri.getPath()));
            startServicesSynchronously(serviceHost, Collections.singletonList(post),
                    Collections.singletonList(factoryService));
        }

        private void startServicesSynchronously(ServiceHost serviceHost, Service... services) throws Throwable {
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
                    this.servicesToStartSynchronously.add(o.getUri().getPath());
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
