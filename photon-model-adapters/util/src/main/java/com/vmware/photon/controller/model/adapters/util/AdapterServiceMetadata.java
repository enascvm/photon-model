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

package com.vmware.photon.controller.model.adapters.util;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.UriPaths.AdapterTypePath;
import com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata;
import com.vmware.xenon.common.Service;

/**
 * Helper class, kind of extension to {@link ServiceMetadata}, which ease Adapters specific metadata
 * manipulation.
 *
 * @deprecated Use {@link AdapterServiceMetadataBuilder}
 */
@Deprecated
public class AdapterServiceMetadata {

    private static final String ADAPTER_TYPE_KEY_NAME = AdapterTypePath.class.getSimpleName();

    /**
     * Creates meta-data for an Adapter Service. The adapter should not be registered into Adapters
     * Registry.
     *
     * @return cached ServiceMetadata
     */
    @Deprecated
    public static ServiceMetadata adapter(Class<? extends Service> adapterClass) {

        return adapter(adapterClass, null);
    }

    /**
     * Creates meta-data for a public Adapter Service, that should be registered into Adapters
     * Registry under passed adapter type.
     *
     * @return cached ServiceMetadata
     */
    @Deprecated
    public static ServiceMetadata adapter(
            Class<? extends Service> adapterClass,
            AdapterTypePath adapterType) {

        ServiceMetadata adapter = ServiceMetadata.service(adapterClass);

        if (adapterType != null) {
            adapter.extension.put(ADAPTER_TYPE_KEY_NAME, adapterType);
        }

        return adapter;
    }

    /**
     * Returns mapping of adapter link to adapter type key for public adapters (those are adapters
     * that should be registered into Adapters Registry).
     */
    @Deprecated
    public static Map<String, String> getPublicAdapters(
            ServiceMetadata[] adaptersMetadata) {

        return Arrays.stream(adaptersMetadata)
                .filter(aMD -> aMD.extension.containsKey(ADAPTER_TYPE_KEY_NAME))
                .collect(Collectors.toMap(
                        ServiceMetadata::getLink,
                        aMD -> ((AdapterTypePath) aMD.extension.get(ADAPTER_TYPE_KEY_NAME)).key));
    }

}