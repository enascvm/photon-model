/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.discovery.cloudaccount.utils;

import static com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster.INVENTORY_SERVICE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;

import com.vmware.photon.controller.model.util.ClusterUtil;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Symphony cluster related utility methods
 */
public class ClusterUtils {

    /**
     * Checks if the metrics cluster is enabled.
     */
    public static boolean isMetricClusterEnabled() {
        return ServiceTypeCluster.METRIC_SERVICE.getUri() != null &&
                !ServiceTypeCluster.METRIC_SERVICE.getUri().isEmpty();
    }

    /**
     * Checks if the inventory cluster is enabled.
     */
    public static boolean isInventoryClusterEnabled() {
        return INVENTORY_SERVICE.getUri() != null &&
                !INVENTORY_SERVICE.getUri().isEmpty();
    }

    public static URI createInventoryUri(ServiceHost host, String link) {
        return UriUtils.buildUri(ClusterUtil.getClusterUri(host, INVENTORY_SERVICE), link);
    }

    /**
     * Get cluster URI based on cluster type.
     */
    public static URI createClusterUri(ServiceHost host, String link, ServiceTypeCluster clusterType) {
        return UriUtils.buildUri(ClusterUtil.getClusterUri(host, clusterType), link);
    }

    public static URI createClusterQueryUri(ServiceHost host, ServiceTypeCluster clusterType) {
        String queryLink = ServiceUriPaths.CORE_LOCAL_QUERY_TASKS;

        try {
            if (clusterType != null && clusterType.getServiceUri() != null) {
                queryLink = ServiceUriPaths.CORE_QUERY_TASKS;
            }
        } catch (URISyntaxException e) {
            host.log(Level.WARNING, "Cluster URI malformed: '%s'", e.getMessage());
        }

        return createClusterUri(host, queryLink, clusterType);
    }

    /**
     * Get inventory cluster URI if available.
     */
    public static URI createInventoryUri(ServiceHost host, URI uri) {
        URI clusterUri = ClusterUtil.getClusterUri(host, INVENTORY_SERVICE);
        return UriUtils.buildUri(clusterUri.getHost(), clusterUri.getPort(), uri.getPath(),
                uri.getQuery());
    }

    /**
     * Get inventory cluster URI string.
     */
    public static String getInventoryUriStr() {
        return INVENTORY_SERVICE.getUri();
    }

    /**
     * Get inventory cluster URI.
     */
    public static URI getInventoryUri() throws URISyntaxException {
        return INVENTORY_SERVICE.getServiceUri();
    }
}
