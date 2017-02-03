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

package com.vmware.photon.controller.model.adapters.util;

import org.junit.Assert;
import org.junit.Test;

public class PairTest {

    private static final String LEFT_VALUE = "leftValue";
    private static final String RIGHT_VALUE = "rightValue";

    @Test
    public void testGetObjects() {
        Pair<String, String> one = Pair.of(LEFT_VALUE, RIGHT_VALUE);
        Assert.assertEquals(LEFT_VALUE, one.left);
        Assert.assertEquals(RIGHT_VALUE, one.right);
    }

    @Test
    public void testEquals() {
        Pair<String, String> one = Pair.of(LEFT_VALUE, RIGHT_VALUE);
        Pair<String, String> other = Pair.of(LEFT_VALUE, RIGHT_VALUE);
        Assert.assertEquals(one, other);
    }

    @Test
    public void testEqualsNegative() {
        Pair<String, String> one = Pair.of(RIGHT_VALUE, LEFT_VALUE);
        Pair<String, String> other = Pair.of(LEFT_VALUE, RIGHT_VALUE);
        Assert.assertNotEquals(one, other);
    }

    @Test
    public void testEqualsNegativeNullValue() {
        Pair<String, String> one = Pair.of(LEFT_VALUE, RIGHT_VALUE);
        Pair<String, String> other = Pair.of(LEFT_VALUE, null);
        Assert.assertNotEquals(one, other);
    }

    @Test
    public void testHashcodeImmutable() {
        Pair<String, String> one = Pair.of(LEFT_VALUE, RIGHT_VALUE);
        Pair<String, String> other = Pair.of(LEFT_VALUE, RIGHT_VALUE);
        Assert.assertEquals(one.hashCode(), other.hashCode());
    }

    @Test
    public void testToStringImmutable() {
        Pair<String, String> one = Pair.of(LEFT_VALUE, RIGHT_VALUE);
        Pair<String, String> other = Pair.of(LEFT_VALUE, RIGHT_VALUE);
        Assert.assertEquals(one.toString(), other.toString());
    }
}
