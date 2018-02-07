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

package com.vmware.photon.controller.model.adapters.awsadapter;

import com.vmware.xenon.common.test.VerificationHost;

/**
 * This utility is created as an extension of {@link AWSAdapters} for test purposes to start the
 * adapter synchronously.
 */
public class AWSAdaptersTestUtils {

    public static void startServicesSynchronously(VerificationHost host) throws Throwable {
        AWSAdapters.startServices(host);

        host.waitForServiceAvailable(AWSAdapters.CONFIG_LINK);
    }
}
