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

package com.vmware.photon.controller.model.util;

/**
 * This class assists in asserting arguments.
 */
public class AssertUtil {

    /**
     * Assert that the specified argument is not {@code null} otherwise throwing
     * {@link IllegalArgumentException} with the specified {@code errorMessage}.
     * <p>
     * {@code AssertUtil.assertNotNull(someArg, "'someArg' must be set.");}
     * @param obj
     *         the object to validate
     * @param errorMessage
     *         the exception message if invalid
     * @throws IllegalArgumentException
     *         if provided {@code obj} is {@code null}
     */
    public static void assertNotNull(Object obj, String errorMessage) {
        if (obj == null) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    /**
     * Assert that provided {@code expression} is {@code true} otherwise throwing an
     * {@link IllegalArgumentException} with the specified {@code errorMessage}.
     * <p>
     * {@code AssertUtil.assertTrue(arr.length>0, "'arr' shall not be empty.");}
     * @param expression
     *         the boolean expression to check
     * @param errorMessage
     *         the exception message if invalid
     * @throws IllegalArgumentException
     *         if expression is <code>false</code>
     */
    public static void assertTrue(boolean expression, String errorMessage) {
        if (!expression) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    /**
     * Assert that provided {@code expression} is {@code false} otherwise throwing an
     * {@link IllegalArgumentException} with the specified {@code errorMessage}.
     * <p>
     * {@code AssertUtil.assertFalse(arr.length>0, "'arr' shall be empty.");}
     * @param expression
     *         the boolean expression to check
     * @param errorMessage
     *         the exception message if invalid
     * @throws IllegalArgumentException
     *         if expression is <code>false</code>
     */
    public static void assertFalse(boolean expression, String errorMessage) {
        if (expression) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    public static void assertNotEmpty(String value, String errorMessage) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

}
