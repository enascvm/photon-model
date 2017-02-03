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

import java.util.Objects;

/**
 * A pair consisting of two elements: 'left' and 'right'.
 * @param <L>
 *         the left element type
 * @param <R>
 *         the right element type
 */
public class Pair<L, R> {

    /**
     * Left object
     */
    public final L left;
    /**
     * Right object
     */
    public final R right;

    public Pair(L left, R right) {
        this.left = left;
        this.right = right;
    }

    /**
     * Obtains an immutable pair of from two objects inferring the generic types.
     * @param <L>
     *         the left element type
     * @param <R>
     *         the right element type
     * @param left
     *         the left element, may be null
     * @param right
     *         the right element, may be null
     * @return a pair formed from the two parameters, not null
     */
    public static <L, R> Pair<L, R> of(final L left, final R right) {
        return new Pair<L, R>(left, right);
    }

    /**
     * <p>Compares this pair to another based on the two elements.</p>
     * @param obj
     *         the object to compare to, null returns false
     * @return true if the elements of the pair are equal
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof Pair<?, ?>)) {
            return false;
        }
        Pair<?, ?> other = (Pair<?, ?>) obj;
        return Objects.equals(this.left, other.left)
                && Objects.equals(this.right, other.right);
    }

    /**
     * <p>Returns a suitable hash code.
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(this.left, this.right);
    }

    /**
     * <p>Returns a String representation of this pair.</p>
     * @return a string describing this object, not null
     */
    @Override
    public String toString() {
        return String.format("%s[left=%s, right=%s]",
                getClass().getSimpleName(), this.left, this.right);
    }

}