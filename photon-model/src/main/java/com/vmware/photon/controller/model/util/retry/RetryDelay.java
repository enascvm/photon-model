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

import java.util.concurrent.TimeUnit;

/**
 * Contains logic and state for retry delay calculation.
 * Using a RetryPolicy, it supports fixed delay, back off and jitter factor.
 */
public class RetryDelay {

    private long startTime;
    private long lastDelay = -1;
    private boolean timedOut = false;

    private RetryPolicy retryPolicy;

    public RetryDelay(RetryPolicy retryPolicy) {
        this.startTime = System.nanoTime();
        this.retryPolicy = retryPolicy;
    }

    /**
     * Check whether max duration has been exceeded when compared to creation time of this instance.
     * @return
     */
    public boolean isTimedout() {
        return isTimedout(getElapsedTime().toNanos());
    }

    /**
     * Check whether max duration has been exceeded given elapsed time.
     * @param elapsedNanos Elapsed time in nano seconds
     * @return
     */
    public boolean isTimedout(long elapsedNanos) {
        if (this.timedOut) {
            return true;
        }

        long timeoutNanos = this.retryPolicy.getMaxDuration().toNanos();

        if (elapsedNanos >= timeoutNanos) {
            return true;
        }

        return false;
    }

    /**
     * Caculate and return the next delay duration based on policy and previous delay state
     * @return Next delay in Duration
     */
    public Duration getNextDelay() {
        // get fixed delay
        long delayNanos = this.retryPolicy.getDelay().toNanos();

        // adjust with backoff (optional)
        if (this.retryPolicy.getDelayFactor() != 0.0d && this.lastDelay != -1) {
            delayNanos = calcBackoffDelay(this.lastDelay, this.retryPolicy.getDelayFactor());
        }

        // adjust with jitter (optional)
        if (this.retryPolicy.getJitterFactor() != 0.0d) {
            delayNanos = calcJitterDelay(delayNanos, this.retryPolicy.getJitterFactor());
        }

        // adjust against final timeout
        if (isTimedout(delayNanos + getElapsedTime().toNanos()) == true) {
            delayNanos = this.retryPolicy.getMaxDuration().toNanos() - getElapsedTime().toNanos();
            if (delayNanos < 0) {
                delayNanos = 0;
            }
            this.timedOut = true;
        }

        this.lastDelay = delayNanos;
        return new Duration(delayNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * Get elapsed time since instance creation
     * @return elapsed time in Duration
     */
    public Duration getElapsedTime() {
        return new Duration(System.nanoTime() - this.startTime, TimeUnit.NANOSECONDS);
    }

    /**
     * Given the previous delay, calculate the next backoff delay using a percentage factor
     * @param lastDelay previous delay to adjust
     * @param factor back off percentage - 2.0 is double each time, 1.0 is the same.
     * @return
     */
    public static long calcBackoffDelay(long lastDelay, double factor) {
        double nextDelay = lastDelay * factor;
        return (long) nextDelay;
    }

    /**
     * Adds a jitter(random) factor to the given delay. Jitter factor is a percentage of the delay
     * and it is applied as + or - the delay.
     * @param delay delay to apply jitter
     * @param factor jitter percentage to apply
     * @return
     */
    public static long calcJitterDelay(long delay, double factor) {
        double jitterMax = delay * factor;
        double nextDelay = (delay - jitterMax) + (Math.random() * 2 * jitterMax);
        return (long) nextDelay;
    }

}