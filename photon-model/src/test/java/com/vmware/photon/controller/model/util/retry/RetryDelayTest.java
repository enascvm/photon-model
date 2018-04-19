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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import com.esotericsoftware.minlog.Log;

import org.junit.Test;

/**
 * Unit test for {@link RetryDelay}
 */
public class RetryDelayTest {

    @Test
    public void testFixedDelay() throws Throwable {
        long delayMs = 100;
        long durationSec = 3;

        RetryPolicy retryPolicy = new RetryPolicy()
                .withDelay(delayMs, TimeUnit.MILLISECONDS)
                .withMaxDuration(durationSec, TimeUnit.SECONDS);

        RetryDelay delay = new RetryDelay(retryPolicy);

        long startNano = System.nanoTime();
        long tryCount = 0;

        while (!delay.isTimedout()) {
            tryCount++;
            Duration currentDelay = delay.getNextDelay();

            Log.info("tryCount: " + tryCount + " delay(ms): " + currentDelay.toMillis());

            Thread.sleep(currentDelay.toMillis());
        }

        long duration = System.nanoTime() - startNano;

        Duration fudgeRange = new Duration(10, TimeUnit.MILLISECONDS);

        // did it obey max duration?
        assertInRange(retryPolicy.getMaxDuration().toNanos(), duration, fudgeRange.toNanos());

        // expected number of tries?
        long expectedCount = durationSec * 1000 / delayMs;
        assertInRange(expectedCount, tryCount, 10);
    }

    @Test
    public void testJitterDelay() throws Throwable {
        long delayMs = 100;
        long durationSec = 3;
        double jitterFactor = 0.1;

        long jitterRange = (long)(jitterFactor * delayMs) + 10;

        RetryPolicy retryPolicy = new RetryPolicy()
                .withDelay(delayMs, TimeUnit.MILLISECONDS)
                .withMaxDuration(durationSec, TimeUnit.SECONDS)
                .withJitter(jitterFactor);

        RetryDelay delay = new RetryDelay(retryPolicy);

        long startNano = System.nanoTime();
        long tryCount = 0;

        long lastDelay = 0;
        long accumulatedJitter = 0;

        while (!delay.isTimedout()) {
            tryCount++;
            Duration currentDelay = delay.getNextDelay();

            if (!delay.isTimedout(startNano + currentDelay.toNanos())) {
                assertInRange(retryPolicy.getDelay().toMillis(), currentDelay.toMillis(), jitterRange);
            }

            Log.info("tryCount: " + tryCount + " delay(ms): " + currentDelay.toMillis());

            accumulatedJitter += Math.abs(currentDelay.toMillis() - lastDelay);
            Thread.sleep(currentDelay.toMillis());

            lastDelay = currentDelay.toMillis();
        }

        long duration = System.nanoTime() - startNano;

        Duration fudgeRange = new Duration(10, TimeUnit.MILLISECONDS);

        // did it obey max duration?
        assertInRange(retryPolicy.getMaxDuration().toNanos(), duration, fudgeRange.toNanos());

        // was there jitter?
        assertTrue(accumulatedJitter > 0);
    }

    @Test
    public void testBackoffDelay() throws Throwable {
        long delayMs = 100;
        long durationSec = 3;
        double backoffFactor = 1.5;

        RetryPolicy retryPolicy = new RetryPolicy()
                .withDelay(delayMs, TimeUnit.MILLISECONDS)
                .withMaxDuration(durationSec, TimeUnit.SECONDS)
                .withBackoff(backoffFactor);

        RetryDelay delay = new RetryDelay(retryPolicy);

        long startNano = System.nanoTime();
        long tryCount = 0;

        long lastDelay = 0;
        long accumulatedJitter = 0;

        while (!delay.isTimedout()) {
            tryCount++;
            Duration currentDelay = delay.getNextDelay();

            if (!delay.isTimedout(startNano + currentDelay.toNanos())) {
                assertTrue(lastDelay < currentDelay.toMillis());
            }

            Log.info("tryCount: " + tryCount + " delay(ms): " + currentDelay.toMillis());

            Thread.sleep(currentDelay.toMillis());
        }

        long duration = System.nanoTime() - startNano;

        Duration fudgeRange = new Duration(10, TimeUnit.MILLISECONDS);

        // did it obey max duration?
        assertInRange(retryPolicy.getMaxDuration().toNanos(), duration, fudgeRange.toNanos());
    }

    private void assertInRange(long expected, long actual, long range) {
        if (expected + range <= actual || expected - range >= actual) {
            assertEquals(String.format("Actual value in not within range [%d] of expected.", range),
                    expected,
                    actual);
        }
    }
}
