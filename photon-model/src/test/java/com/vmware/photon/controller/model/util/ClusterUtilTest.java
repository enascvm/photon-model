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

import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;

import com.vmware.xenon.common.test.VerificationHost;

public class ClusterUtilTest {

    private VerificationHost host;

    @Before
    public void setupVerificationHost() throws Throwable {
        this.host = VerificationHost.create(0);
        this.host.start();
    }

    @After
    public void cleanUp() {
        ServiceTypeCluster.METRIC_SERVICE.setUri(null);
        if (this.host == null) {
            return;
        }
        this.host.tearDownInProcessPeers();
        this.host.toggleNegativeTestMode(false);
        this.host.tearDown();
    }

    @Test
    public void testNullCluster() {
        URI expectedUri = this.host.getUri();
        URI returnedUri = ClusterUtil.getClusterUri(this.host, null);
        assertEquals(expectedUri, returnedUri);
    }

    @Test
    public void testClusterUriNotSet() {
        // Set the Metrics URI through reflections.
        this.setMetricsUri(null);
        URI expectedUri = this.host.getUri();
        URI returnedUri = ClusterUtil.getClusterUri(this.host, ServiceTypeCluster.METRIC_SERVICE);
        assertEquals(expectedUri, returnedUri);
    }

    @Test(expected = IllegalStateException.class)
    public void testInvalidClusterUri() throws Throwable {
        this.setMetricsUri("someRandom^InvalidUri^^");
        ClusterUtil.getClusterUri(this.host, ServiceTypeCluster.METRIC_SERVICE);
    }

    @Test
    public void testGetClusterUri() throws URISyntaxException {
        String uri = "http://someRandomValidUri:8000/";
        // Set the Metrics URI through reflections.
        this.setMetricsUri(uri);
        URI expectedUri = new URI(uri);
        ClusterUtil.getClusterUri(this.host, ServiceTypeCluster.METRIC_SERVICE);
        URI returnedUri = ClusterUtil.getClusterUri(this.host, ServiceTypeCluster.METRIC_SERVICE);
        assertEquals(expectedUri, returnedUri);
    }

    private void setMetricsUri(String newUri) {
        ServiceTypeCluster.METRIC_SERVICE.setUri(newUri);
    }
}
