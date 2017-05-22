/*
 * Copyright (c) 2015-2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.adapters.vsphere.tagging;

import java.util.function.Function;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;

import com.vmware.photon.controller.model.resources.TagService.TagState;

/**
 * Caches TagState in a LRU cache to avoid too many remote calls to the tags service.
 */
public class TagCache {
    private static final int MAX_TAGS = 500;

    private final ConcurrentLinkedHashMap<String, TagState> cache;

    public TagCache() {
        // TODO integrate proper LRU cache
        this.cache = new Builder<String, TagState>().maximumWeightedCapacity(MAX_TAGS).build();
    }

    public TagState get(String id, Function<String, TagState> valueProvider) {
        return this.cache.computeIfAbsent(id, valueProvider);
    }

    public TagState get(String id) {
        return this.cache.get(id);
    }
}
