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

package com.vmware.photon.controller.model.adapters.awsadapter.enumeration;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.adapters.awsadapter.AWSAdapters;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSVolumeTypeDiscoveryService.VolumeTypeList;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.test.TestRequestSender;
import com.vmware.xenon.common.test.VerificationHost;

public class AWSVolumeTypeDiscoveryServiceTest {

    private VerificationHost host;

    @Before
    public void setUp() throws Throwable {
        this.host = VerificationHost.create(0);
        this.host.start();

        AWSAdapters.startServices(this.host);

        this.host.waitForServiceAvailable(AWSVolumeTypeDiscoveryService.SELF_LINK);
    }

    @Test
    public void testVolumeTypeDiscovery() {
        Operation get = Operation.createGet(this.host,
                AWSVolumeTypeDiscoveryService.SELF_LINK + "?deviceType=ebs");

        get = new TestRequestSender(this.host).sendAndWait(get);

        VolumeTypeList volumes = get.getBody(AWSVolumeTypeDiscoveryService.VolumeTypeList.class);

        Assert.assertEquals(
                "The expected number of supported ebs volumes are not matching "
                        + "the actual number volumes from the discovery service",
                5,
                volumes.volumeTypes.size());
    }
}