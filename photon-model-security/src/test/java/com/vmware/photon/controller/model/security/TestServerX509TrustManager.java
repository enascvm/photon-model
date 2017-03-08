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

import java.util.concurrent.TimeUnit;

import com.vmware.photon.controller.model.security.ssl.ServerX509TrustManager;
import com.vmware.xenon.common.ServiceHost;

public class TestServerX509TrustManager extends ServerX509TrustManager {

    public TestServerX509TrustManager(ServiceHost host, Long updateInterval) {
        super(host);
        this.maintenanceIntervalInitial = updateInterval;
        //reload faster the first 1 minute
        this.reloadCounterThreshold = (int) (TimeUnit.MINUTES.toMicros(1) / updateInterval);
    }
}
