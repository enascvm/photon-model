/*
 * Copyright (c) 2014-2015 VMware, Inc. All Rights Reserved.
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

import com.vmware.xenon.common.ODataQueryVisitor;

/**
 * Utility methods to build OData expressions. Useful when sending OData GET request to the Xenon
 * Factory service.
 */
public class ODataUtils {

    /**
     * Boolean operators supported by underlying Xenon Factory service.
     */
    public enum BoolOp {

        AND(ODataQueryVisitor.BinaryVerb.AND),
        OR(ODataQueryVisitor.BinaryVerb.OR);

        ODataQueryVisitor.BinaryVerb boolOp;

        BoolOp(ODataQueryVisitor.BinaryVerb boolOp) {
            this.boolOp = boolOp;
        }

        @Override
        public String toString() {
            return this.boolOp.operator;
        }
    }

    /**
     * Comparison operators supported by underlying Xenon Factory service.
     */
    public enum CompOp {

        EQ(ODataQueryVisitor.BinaryVerb.EQ),
        NE(ODataQueryVisitor.BinaryVerb.NE),
        LT(ODataQueryVisitor.BinaryVerb.LT),
        LE(ODataQueryVisitor.BinaryVerb.LE),
        GT(ODataQueryVisitor.BinaryVerb.GT),
        GE(ODataQueryVisitor.BinaryVerb.GE);

        ODataQueryVisitor.BinaryVerb compOp;

        CompOp(ODataQueryVisitor.BinaryVerb compOp) {
            this.compOp = compOp;
        }

        @Override
        public String toString() {
            return this.compOp.operator;
        }
    }

    /**
     * Build OData boolean expression (such as 'or', 'and') in the form of
     * {@code lhsBoolExpr op rhsBoolExpr}.
     */
    public static String bool(String lhsBoolExpr, BoolOp op, String rhsBoolExpr) {

        if (lhsBoolExpr != null && rhsBoolExpr != null) {
            AssertUtil.assertNotNull(op, "OData BoolOp should not be null");
            return String.format("%s %s %s",
                    parenthesis(lhsBoolExpr),
                    op,
                    parenthesis(rhsBoolExpr));
        }
        if (lhsBoolExpr != null) {
            return lhsBoolExpr;
        }
        if (rhsBoolExpr != null) {
            return rhsBoolExpr;
        }
        return null;
    }

    /**
     * Build OData comparison expression in the form of {@code name op 'value'}.
     */
    public static String expr(String name, CompOp op, String value) {
        AssertUtil.assertNotEmpty(name, "OData name should not be empty");
        AssertUtil.assertNotNull(op, "OData comparison op should not be null");
        AssertUtil.assertNotNull(value, "OData value should not be null");

        return String.format("%s %s '%s'", name, op, value);
    }

    /**
     * Add starting and ending brackets around an OData expression, if needed.
     */
    public static String parenthesis(String expression) {

        if (expression == null) {
            return null;
        }

        expression = expression.trim();

        if (expression.isEmpty()) {
            return null;
        }

        final String OPEN_BRACKET = "(";
        final String CLOSE_BRACKET = ")";

        if (!expression.startsWith(OPEN_BRACKET) || !expression.endsWith(CLOSE_BRACKET)) {
            // Doesn't start or doesn't end with a bracket -> add brackets
            expression = OPEN_BRACKET + expression + CLOSE_BRACKET;
        } else {
            // Starts and ends with brackets. Still need to validate that brackets are needed
            for (int counter = 1, i = 1; i < expression.length() - 1; i++) {
                if (expression.charAt(i) == OPEN_BRACKET.charAt(0)) {
                    counter++;
                } else if (expression.charAt(i) == CLOSE_BRACKET.charAt(0)) {
                    counter--;
                }
                if (counter == 0) {
                    expression = OPEN_BRACKET + expression + CLOSE_BRACKET;
                    break;
                }
            }
        }

        return expression;
    }

}
