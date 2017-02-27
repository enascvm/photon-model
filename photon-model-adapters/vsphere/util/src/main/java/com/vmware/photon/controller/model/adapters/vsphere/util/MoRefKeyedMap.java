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

package com.vmware.photon.controller.model.adapters.vsphere.util;

import java.util.Comparator;
import java.util.concurrent.ConcurrentSkipListMap;

import com.vmware.vim25.ManagedObjectReference;

/**
 * A map that allows using a {@link ManagedObjectReference} for the keys. Plain Moref's cannot be used
 * as they dont implement equals/hashCode
 * @param <V>
 */
public final class MoRefKeyedMap<V> extends ConcurrentSkipListMap<ManagedObjectReference, V> {
    private static final long serialVersionUID = 1;

    public MoRefKeyedMap() {
        super(Comparator.comparing(ManagedObjectReference::getValue)
                .thenComparing(ManagedObjectReference::getType));
    }
}
