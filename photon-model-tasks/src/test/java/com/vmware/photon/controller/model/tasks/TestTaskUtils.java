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

package com.vmware.photon.controller.model.tasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.model.tasks.TaskUtils.getResourceExpirationMicros;

import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService.ResourceExpirationPolicy;
import com.vmware.xenon.common.Utils;

public class TestTaskUtils {
    @Test
    public void testGetResourceExpirationMicros() {
        // Validate that expiration time returned for ResourceExpirationPolicy.EXPIRE_NEVER is 0.
        long timeForExpireNever = getResourceExpirationMicros(ResourceExpirationPolicy
                .EXPIRE_NEVER);
        assertEquals(0, timeForExpireNever);

        // Validate that expiration time returned for ResourceExpirationPolicy.EXPIRE_NOW is
        // about the same as current time accounting for time delta for test call.
        long timeForExpireNow = getResourceExpirationMicros(ResourceExpirationPolicy.EXPIRE_NOW);
        long timeDiffForExpireNow = Utils.getNowMicrosUtc() - timeForExpireNow;
        assertTrue(timeDiffForExpireNow < TimeUnit.SECONDS.toMicros(1));

        // Validate that expiration time returned for ResourceExpirationTime.EXPIRE_AFTER_ONE_MONTH
        // is in future and a month after
        long timeForExpireAfterOneMonth = getResourceExpirationMicros(ResourceExpirationPolicy
                .EXPIRE_AFTER_ONE_MONTH);
        long timeDiffForExpireAfterOneMonth = timeForExpireAfterOneMonth - Utils.getNowMicrosUtc();

        assertTrue(timeForExpireAfterOneMonth > Utils.getNowMicrosUtc());
        assertTrue(timeDiffForExpireAfterOneMonth < TimeUnit.DAYS.toMicros(31));

    }
}