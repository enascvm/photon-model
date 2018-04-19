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

package com.vmware.photon.controller.model.util.retry;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Duration, consisting of length of a time unit.
 *
 * This class was ported from the net.jodah.failsafe open source project.
 */
public class Duration {
    public static final Duration NONE = new Duration(0, TimeUnit.MILLISECONDS);

    private final long length;
    private final TimeUnit timeUnit;

    public Duration(long length, TimeUnit timeUnit) {
        this.length = length;
        this.timeUnit = timeUnit;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || Duration.class.isInstance(o) && toNanos() == Duration.class.cast(o).toNanos();
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[] { toNanos(), NANOSECONDS });
    }

    @Override
    public String toString() {
        return this.length + " " + this.timeUnit.toString().toLowerCase();
    }

    /**
     * Returns the Duration in nanoseconds.
     */
    public long toNanos() {
        return this.timeUnit.toNanos(this.length);
    }

    /**
     * Returns the Duration in milliseconds.
     */
    public long toMillis() {
        return this.timeUnit.toMillis(this.length);
    }

    /**
     * Returns the Duration in seconds.
     */
    public long toSeconds() {
        return this.timeUnit.toSeconds(this.length);
    }

    /**
     * Returns the Duration in minutes.
     */
    public long toMinutes() {
        return this.timeUnit.toMinutes(this.length);
    }

    /**
     * Returns the Duration in hours.
     */
    public long toHours() {
        return this.timeUnit.toHours(this.length);
    }

    /**
     * Returns the Duration in days.
     */
    public long toDays() {
        return this.timeUnit.toDays(this.length);
    }
}