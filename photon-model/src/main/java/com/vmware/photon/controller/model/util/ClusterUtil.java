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
     * Enum mapping Clusters with their URIs.
     *
     */
    public enum ServiceTypeCluster {

        METRIC_SERVICE(METRICS_URI);

        private String uri;

        ServiceTypeCluster(String uri) {
            this.uri = uri;
        }

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
    public static URI getClusterUri(ServiceHost host, ServiceTypeCluster cluster) {
        // If cluster is null, return the host URI.
        if (cluster == null) {
            return host.getUri();
        }

        String uriString = cluster.getUri();
        if (uriString == null || uriString.isEmpty()) {
            // If the clusterUri is not passed as a parameter, return host URI.
            return host.getUri();
        }

        URI clusterUri = null;
        try {
            clusterUri = new URI(uriString);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e.getLocalizedMessage());
        }
        return clusterUri;
    }
}
