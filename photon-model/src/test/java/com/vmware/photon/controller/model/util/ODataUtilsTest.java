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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Unit test for {@link ODataUtils}.
 */
public class ODataUtilsTest {

    /**
     * Unit test for {@link ODataUtils#parenthesis(String)}.
     */
    @Test
    public void testParenthesis() {

        assertEquals((String) null, ODataUtils.parenthesis(null));

        assertEquals((String) null, ODataUtils.parenthesis(" "));

        // Should add brackets

        assertEquals("(A and B)", ODataUtils.parenthesis(" A and B "));

        assertEquals("((A and C) and B)", ODataUtils.parenthesis("(A and C) and B"));

        assertEquals("(A and (C and B))", ODataUtils.parenthesis("A and (C and B)"));

        assertEquals("((A) and (B))", ODataUtils.parenthesis("(A) and (B)"));
        assertEquals("((A and (C)) and (B))", ODataUtils.parenthesis("(A and (C)) and (B)"));

        // No need to add brackets

        assertEquals("(A)", ODataUtils.parenthesis("(A)"));

        assertEquals("((A and (C)) and B)", ODataUtils.parenthesis("((A and (C)) and B)"));
    }

    /**
     * Unit test for {@link ODataUtils#bool(String, String, String)}.
     */
    @Test
    public void testBool() {

        assertEquals("(lhs) and (rhs)", ODataUtils.bool("lhs", ODataUtils.BoolOp.AND, "rhs"));

        assertEquals("lhs", ODataUtils.bool("lhs", ODataUtils.BoolOp.AND, null));

        assertEquals("rhs", ODataUtils.bool(null, ODataUtils.BoolOp.AND, "rhs"));

        assertEquals((String) null, ODataUtils.bool(null, null, null));
    }

    /**
     * Unit test for {@link ODataUtils#expr(String, String, String)}.
     */
    @Test
    public void testExpr() {

        assertEquals("name eq 'value'", ODataUtils.expr("name", ODataUtils.CompOp.EQ, "value"));
    }

}