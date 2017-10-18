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

import static com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster.INVENTORY_SERVICE;

import java.net.URI;

import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;

public class PhotonModelUriUtils {

    public static URI createInventoryUri(ServiceHost host, String link) {
        return UriUtils.buildUri(ClusterUtil.getClusterUri(host, INVENTORY_SERVICE), link);
    }

    public static URI createInventoryUri(ServiceHost host, URI uri) {
        URI clusterUri = ClusterUtil.getClusterUri(host, INVENTORY_SERVICE);
        return UriUtils.buildUri(clusterUri.getHost(), clusterUri.getPort(), uri.getPath(),
                uri.getQuery());
    }

}
