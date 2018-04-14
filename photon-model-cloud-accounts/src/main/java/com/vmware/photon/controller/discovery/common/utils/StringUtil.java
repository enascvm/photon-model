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

package com.vmware.photon.controller.discovery.common.utils;

/**
 * Due to direction from leadership that we should not be dependent on 3rd party libraries, we
 * implement our own "string utils" class (much to my dismay ;)
 */
public class StringUtil {

    /**
     * Returns {@code true} if {@code str} is {@code null} or is the empty string.
     *
     * @param str the string to evaluate
     * @return {@code true} if {@code str} is {@code null} or is the empty string; {@code false},
     * otherwise
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }
}
