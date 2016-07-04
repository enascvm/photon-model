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

package com.vmware.photon.controller.model.adapters.vsphere.stats;

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.junit.Test;

import com.vmware.photon.controller.model.adapters.vsphere.EnumerationClientTest;
import com.vmware.photon.controller.model.adapters.vsphere.TestProperties;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BasicConnection;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.xenon.common.ServiceStats.ServiceStat;

public class StatsClientTest {
    Logger logger = Logger.getLogger(EnumerationClientTest.class.getName());

    @Test
    public void test() throws Exception {
        String url = System.getProperty(TestProperties.VC_URL);

        if (url == null) {
            return;
        }

        String username = System.getProperty(TestProperties.VC_USERNAME);
        String password = System.getProperty(TestProperties.VC_PASSWORD);

        BasicConnection conn = new BasicConnection();

        conn.setURI(URI.create(url));
        conn.setUsername(username);
        conn.setPassword(password);
        conn.setIgnoreSslErrors(true);

        conn.setRequestTimeout(30, TimeUnit.SECONDS);
        conn.connect();

        StatsClient client = new StatsClient(conn);
        List<ServiceStat> metrics;

        ManagedObjectReference vm = new ManagedObjectReference();
        vm.setType(VimNames.TYPE_VM);
        vm.setValue("vm-49");

        metrics = client.retrieveMetricsForVm(vm);
        this.logger.info("vm metrics " + metrics);

        ManagedObjectReference host = new ManagedObjectReference();
        host.setType(VimNames.TYPE_HOST);
        host.setValue("host-504");

        metrics = client.retrieveMetricsForHost(host);
        this.logger.info("host metrics " + metrics);
    }
}