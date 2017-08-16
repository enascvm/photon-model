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
import java.util.Arrays;
import java.util.function.Supplier;

import com.vmware.xenon.common.FactoryService;
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
     * Use this method to start the set of services.
     *
     * @see com.vmware.photon.controller.model.PhotonModelServices#startServices(ServiceHost)
     */
    public static void startServices(ServiceHost host, ServiceMetadata[] servicesMetadata) {
        Arrays.stream(servicesMetadata).forEach(serviceDesc -> serviceDesc.start(host));
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
         * Start the service considering its type.
         */
        public void start(ServiceHost serviceHost) {

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

        private Service newServiceInstance() {
            try {
                return this.serviceClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new IllegalAccessError(
                        "Failed to create an instance of " + this.serviceClass);
            }
        }

    }

}
