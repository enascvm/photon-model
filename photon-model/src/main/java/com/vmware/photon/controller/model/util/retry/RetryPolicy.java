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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import com.vmware.photon.controller.model.util.AssertUtil;

/**
 * A policy that defines when retries should be performed.
 *
 * This class was ported from the net.jodah.failsafe open source project.
 */
public class RetryPolicy {

    private Duration delay;
    private double delayFactor;
    private Duration jitter;
    private double jitterFactor;
    private Duration maxDuration;
    private int maxRetries;
    /** Indicates whether failures are checked by a configured retry condition */
    private boolean failuresChecked;
    private List<BiPredicate<Object, Throwable>> retryConditions;

    /**
     * Creates a retry policy that always retries with no delay.
     */
    public RetryPolicy() {
        this.delay = Duration.NONE;
        this.retryConditions = new ArrayList<BiPredicate<Object, Throwable>>();
    }

    /**
     * Copy constructor.
     */
    public RetryPolicy(RetryPolicy rp) {
        this.delay = rp.delay;
        this.delayFactor = rp.delayFactor;
        this.maxDuration = rp.maxDuration;
        this.maxRetries = rp.maxRetries;
        this.jitterFactor = rp.jitterFactor;
        this.retryConditions = new ArrayList<BiPredicate<Object, Throwable>>(rp.retryConditions);
    }

    /**
     * Returns a copy of this RetryPolicy.
     */
    public RetryPolicy copy() {
        return new RetryPolicy(this);
    }

    /**
     * Returns the delay between retries. Defaults to {@link Duration#NONE}.
     */
    public Duration getDelay() {
        return this.delay;
    }

    /**
     * Returns the delay factor for backoff retries.
     */
    public double getDelayFactor() {
        return this.delayFactor;
    }

    /**
     * Returns the jitter factor, else {@code 0.0} if none has been configured.
     */
    public double getJitterFactor() {
        return this.jitterFactor;
    }

    /**
     * Returns the max duration to perform retries for.
     */
    public Duration getMaxDuration() {
        return this.maxDuration;
    }

    /**
     * Specifies that a retry should occur if the {@code resultPredicate} matches the result and the retry policy is not
     * exceeded.
     *
     * @throws NullPointerException if {@code resultPredicate} is null
     */
    public <T> RetryPolicy retryIf(Predicate<T> resultPredicate) {
        AssertUtil.assertNotNull(resultPredicate, "resultPredicate");
        this.retryConditions.add(resultPredicateFor(resultPredicate));
        return this;
    }

    /**
     * Specifies that a retry should occur if the {@code completionPredicate} matches the completion result and the retry
     * policy is not exceeded.
     *
     * @throws NullPointerException if {@code completionPredicate} is null
     */
    @SuppressWarnings("unchecked")
    public <T> RetryPolicy retryIf(BiPredicate<T, ? extends Throwable> completionPredicate) {
        AssertUtil.assertNotNull(completionPredicate, "completionPredicate");
        this.failuresChecked = true;
        this.retryConditions.add((BiPredicate<Object, Throwable>) completionPredicate);
        return this;
    }

    /**
     * Returns whether an execution result can be retried given the configured abort conditions.
     */
    public boolean canRetryFor(Object result, Throwable failure) {
        for (BiPredicate<Object, Throwable> predicate : this.retryConditions) {
            if (predicate.test(result, failure)) {
                return true;
            }
        }

        // Retry by default if a failure is not checked by a retry condition
        return failure != null && !this.failuresChecked;
    }

    /**
     * Specifies the failure to retry on. Any failure that is assignable from the {@code failure} will be retried.
     *
     * @throws NullPointerException if {@code failure} is null
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public RetryPolicy retryOn(Class<? extends Throwable> failure) {
        AssertUtil.assertNotNull(failure, "failure");
        return retryOn((List) Arrays.asList(failure));
    }

    /**
     * Specifies the failures to retry on. Any failure that is assignable from the {@code failures} will be retried.
     *
     * @throws NullPointerException if {@code failures} is null
     * @throws IllegalArgumentException if failures is empty
     */
    @SuppressWarnings("unchecked")
    public RetryPolicy retryOn(Class<? extends Throwable>... failures) {
        AssertUtil.assertNotNull(failures, "failures");
        AssertUtil.assertTrue(failures.length > 0, "Failures cannot be empty");
        return retryOn(Arrays.asList(failures));
    }

    /**
     * Specifies the failures to retry on. Any failure that is assignable from the {@code failures} will be retried.
     *
     * @throws NullPointerException if {@code failures} is null
     * @throws IllegalArgumentException if failures is null or empty
     */
    public RetryPolicy retryOn(List<Class<? extends Throwable>> failures) {
        AssertUtil.assertNotNull(failures, "failures");
        AssertUtil.assertTrue(!failures.isEmpty(), "failures cannot be empty");
        this.failuresChecked = true;
        this.retryConditions.add(failurePredicateFor(failures));
        return this;
    }

    /**
     * Sets the {@code delay} to occur between retries.
     *
     * @throws NullPointerException if {@code timeUnit} is null
     * @throws IllegalArgumentException if {@code delay} <= 0
     * @throws IllegalStateException if {@code delay} is >= the {@link RetryPolicy#withMaxDuration(long, TimeUnit)
     *           maxDuration}, if random delays have already been set, or if backoff delays have already been set
     */
    public RetryPolicy withDelay(long delay, TimeUnit timeUnit) {
        AssertUtil.assertNotNull(timeUnit, "timeUnit");
        AssertUtil.assertTrue(timeUnit.toNanos(delay) > 0, "delay must be greater than 0");
        AssertUtil.assertTrue(this.maxDuration == null || timeUnit.toNanos(delay) < this.maxDuration.toNanos(),
                "delay must be less than the maxDuration");
        this.delay = new Duration(delay, timeUnit);
        return this;
    }

    /**
     * Sets the {@code delay} between retries, exponentially backing off to the {@code maxDelay} and multiplying
     * successive delays by the {@code delayFactor}.
     *
     * @throws NullPointerException if {@code timeUnit} is null
     * @throws IllegalArgumentException if {@code delay} <= 0, {@code delay} is >= {@code maxDelay}, or the
     *           {@code delayFactor} is <= 1
     *           maxDuration}, if delays have already been set, or if random delays have already been set
     */
    public RetryPolicy withBackoff(double delayFactor) {
        AssertUtil.assertTrue(delayFactor > 1, "delayFactor must be greater than 1");
        this.delayFactor = delayFactor;
        return this;
    }

    /**
     * Sets the {@code jitterFactor} to randomly vary retry delays by. For each retry delay, a random portion of the delay
     * multiplied by the {@code jitterFactor} will be added or subtracted to the delay. For example: a retry delay of
     * {@code 100} milliseconds and a {@code jitterFactor} of {@code .25} will result in a random retry delay between
     * {@code 75} and {@code 125} milliseconds.
     */
    public RetryPolicy withJitter(double jitterFactor) {
        AssertUtil.assertTrue(jitterFactor >= 0.0 && jitterFactor <= 1.0, "jitterFactor must be >= 0 and <= 1");
        AssertUtil.assertTrue(this.delay != null, "A delay must be configured");
        AssertUtil.assertTrue(this.jitter == null, "withJitter(long, timeUnit) has already been called");
        this.jitterFactor = jitterFactor;
        return this;
    }

    /**
     * Sets the max duration to perform retries for, else the execution will be failed.
     *
     * @throws NullPointerException if {@code timeUnit} is null
     */
    public RetryPolicy withMaxDuration(long maxDuration, TimeUnit timeUnit) {
        AssertUtil.assertNotNull(timeUnit, "timeUnit");
        AssertUtil.assertTrue(timeUnit.toNanos(maxDuration) > this.delay.toNanos(),
                "maxDuration must be greater than the delay");
        this.maxDuration = new Duration(maxDuration, timeUnit);
        return this;
    }

    /**
     * Returns a predicate that evaluates whether the {@code result} equals an execution result.
     */
    static BiPredicate<Object, Throwable> resultPredicateFor(final Object result) {
        return new BiPredicate<Object, Throwable>() {
            @Override
            public boolean test(Object t, Throwable u) {
                return result == null ? t == null : result.equals(t);
            }
        };
    }

    /**
     * Returns a predicate that returns whether any of the {@code failures} are assignable from an execution failure.
     */
    static BiPredicate<Object, Throwable> failurePredicateFor(final List<Class<? extends Throwable>> failures) {
        return new BiPredicate<Object, Throwable>() {
            @Override
            public boolean test(Object t, Throwable u) {
                if (u == null) {
                    return false;
                }
                for (Class<? extends Throwable> failureType : failures) {
                    if (failureType.isAssignableFrom(u.getClass())) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

}