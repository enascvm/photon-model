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

package com.vmware.photon.controller.model.adapters.vsphere;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class ResourceTrackerTest {

    @Test
    public void test() throws InterruptedException {
        ResourceTracker tracker = new ResourceTracker(2);
        String k1 = "moref";
        String k2 = "moref2";

        String v1 = "/link";
        String v2 = "/link2";

        tracker.track(k1, v1);
        tracker.track(k2, v2);

        tracker.await(1, TimeUnit.SECONDS);

        assertEquals(v1, tracker.getSelfLink(k1));
        assertEquals(v2, tracker.getSelfLink(k2));
    }
}