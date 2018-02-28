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

package com.vmware.photon.controller.model.util;

import java.net.URI;
import java.net.URISyntaxException;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.ServiceHost;

/**
 * Utility related to handling cluster information.
 *
 */
public class ClusterUtil {
    /**
     * Metrics URI set as a system property.
     * Eg: -Dphoton-model.metrics.uri=http://localhost/
     */
    public static final String METRICS_URI = System
            .getProperty(UriPaths.PROPERTY_PREFIX + "metrics.uri");

    /**
     * Discovery URI set as a system property.
     * Eg: -Dphoton-model.discovery.uri=http://localhost/
     *
     * Deprecated - see INVENTORY_URI.
     */
    @Deprecated
    public static final String DISCOVERY_URI = System
            .getProperty(UriPaths.PROPERTY_PREFIX + "discovery.uri");

    /**
     * Inventory URI set as a system property. Defaults to discovery uri for backwards compatibility.
     * Eg: -Dphoton-model.inventory.uri=http://localhost/
     */
    public static final String INVENTORY_URI = System
            .getProperty(UriPaths.PROPERTY_PREFIX + "inventory.uri", DISCOVERY_URI);

    /**
     * Self URI set as a system property.
     */
    public static final String SELF_URI = System
            .getProperty(UriPaths.PROPERTY_PREFIX + "self.uri");

    /**
     * Enum mapping Clusters with their URIs.
     */
    public enum ServiceTypeCluster implements ServiceEndpointLocator {

        METRIC_SERVICE(METRICS_URI),
        @Deprecated
        DISCOVERY_SERVICE(INVENTORY_URI),
        INVENTORY_SERVICE(INVENTORY_URI),

        /**
         * A Service pointing to this service's cluster.
         * If the service is running in single node this is the public ip of host
         *
         * If the service is in cluster this is the URI pointing to the Cluster Uri / LoadBalancer
         * URI / K8S service uri.
         *
         * Eg: -Dphoton-model.self.uri=http://localhost/
         */
        SELF_SERVICE(SELF_URI);

        private String uri;

        ServiceTypeCluster(String uri) {
            this.uri = uri;
        }

        @Override
        public String getUri() {
            return this.uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }
    }

    /**
     * Returns the cluster URI, if set. If not set, returns the host URI.
     *
     * @param host
     *          The Service Host.
     * @param cluster
     *          The Cluster, the URI is requested for.
     * @return
     *          URI of the cluster or the host.
     */
    public static URI getClusterUri(ServiceHost host, ServiceEndpointLocator cluster) {
        // If serviceLocator is null, return the host URI.
        if (cluster == null) {
            return host.getUri();
        }

        try {
            URI serviceUri = cluster.getServiceUri();
            // If the serviceUri is not passed as a parameter, return host URI.
            return serviceUri != null ? serviceUri : host.getUri();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e.getLocalizedMessage(), e);
        }
    }

    /**
     * Returns true if the cluster is defined. false otherwise
     * @param cluster The Cluster to check for.
     * @return true if cluster is defined. false otherwise.
     */
    public static boolean isClusterDefined(ServiceEndpointLocator cluster) {
        if (cluster == null) {
            return false;
        }

        try {
            return cluster.getServiceUri() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
