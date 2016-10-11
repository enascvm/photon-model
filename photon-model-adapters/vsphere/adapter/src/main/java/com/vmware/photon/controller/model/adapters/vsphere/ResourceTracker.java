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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.vmware.vim25.ManagedObjectReference;

/**
 * Thread-safe class for tracking progress writing objects to xenon storage.
 * Instances are not reusable. You can associated the selfLink of the xenon resources with
 * a random string describing the found object, most often the moref or name.
 */
public class ResourceTracker {
    public static final String ERROR = "ERROR";

    private final CountDownLatch countdownLatch;

    private final ConcurrentMap<String, String> mapping;

    public ResourceTracker(int count) {
        this.countdownLatch = new CountDownLatch(count);
        this.mapping = new ConcurrentHashMap<>();
    }

    public void track(String key, String selfLink) {
        String old = this.mapping.put(key, selfLink);
        this.countdownLatch.countDown();
    }

    public String getSelfLink(String key) {
        return this.mapping.get(key);
    }

    public String getSelfLink(ManagedObjectReference moref) {
        return this.mapping.get(VimUtils.convertMoRefToString(moref));
    }

    public void await() throws InterruptedException {
        this.countdownLatch.await();
    }

    public void await(long timeout, TimeUnit unit) throws InterruptedException {
        this.countdownLatch.await(timeout, unit);
    }
}
