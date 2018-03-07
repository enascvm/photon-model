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
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.UriPaths.AdapterTypePath;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceOperationSpec;
import com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Service;

/**
 * Helper class, kind of extension to {@link ServiceMetadata}, which ease Adapters specific metadata
 * building.
 */
public class AdapterServiceMetadataBuilder {

    private static final String ADAPTER_TYPE_KEY_NAME = AdapterTypePath.class.getSimpleName();
    private static final String RESOURCE_OPERATION_SPECS_KEY_NAME = ResourceOperationSpec.class
            .getSimpleName();

    private final Map<String, Object> extension = new HashMap<>();
    private Class<? extends Service> adapterClass;
    private Supplier<FactoryService> factoryCreator;
    private boolean isFactory;

    private AdapterServiceMetadataBuilder(Class<? extends Service> adapterClass) {
        this.adapterClass = adapterClass;
    }

    private AdapterServiceMetadataBuilder(Class<? extends Service> adapterClass, boolean isFactory) {
        this.adapterClass = adapterClass;
        this.isFactory = isFactory;
    }

    /**
     * Creates meta-data for an Adapter Service.
     */
    public static AdapterServiceMetadataBuilder createAdapter(Class<? extends Service>
            adapterClass) {
        return new AdapterServiceMetadataBuilder(adapterClass);
    }

    /**
     * Creates meta-data for an Adapter Service.
     */
    public static AdapterServiceMetadataBuilder createFactoryAdapter(Class<? extends Service>
            adapterClass) {
        return new AdapterServiceMetadataBuilder(adapterClass, true);
    }

    /**
     * Adds AdapterType to the public Adapter Service that should be registered into Adapters
     * Registry.
     */
    public AdapterServiceMetadataBuilder withAdapterType(AdapterTypePath adapterType) {
        if (adapterType != null) {
            this.extension.put(ADAPTER_TYPE_KEY_NAME, adapterType);
        }
        return this;
    }

    /**
     * Adds a Factory creator to be used when instantiating the Adapter Service.
     */
    public AdapterServiceMetadataBuilder withFactoryCreator(Supplier<FactoryService>
            factoryCreator) {
        this.factoryCreator = factoryCreator;
        return this;
    }

    /**
     * Adds ResourceOperationSpecs of the Adapter Service that should be registered into Resource
     * Operation Registry.
     */
    public AdapterServiceMetadataBuilder withResourceOperationSpecs(
            ResourceOperationSpec... specs) {
        if (specs != null && specs.length > 0) {
            this.extension.put(RESOURCE_OPERATION_SPECS_KEY_NAME, specs);
        }
        return this;
    }

    /**
     * Builds the Adapter Service Metadata
     */
    public ServiceMetadata build() {
        ServiceMetadata adapter;
        if (this.isFactory) {
            if (this.factoryCreator != null) {
                adapter = ServiceMetadata.factoryService(this.adapterClass, this.factoryCreator);
            } else {
                adapter = ServiceMetadata.factoryService(this.adapterClass);
            }
        } else {
            if (this.factoryCreator != null) {
                adapter = ServiceMetadata.service(this.adapterClass, this.factoryCreator);
            } else {
                adapter = ServiceMetadata.service(this.adapterClass);
            }
        }
        adapter.extension.putAll(this.extension);
        return adapter;
    }

    /**
     * Returns mapping of adapter link to adapter type key for public adapters (those are adapters
     * that should be registered into Adapters Registry).
     */
    public static Map<String, String> getPublicAdapters(
            ServiceMetadata[] adaptersMetadata) {

        return Arrays.stream(adaptersMetadata)
                .filter(aMD -> aMD.extension.containsKey(ADAPTER_TYPE_KEY_NAME))
                .collect(Collectors.toMap(
                        ServiceMetadata::getLink,
                        aMD -> ((AdapterTypePath) aMD.extension.get(ADAPTER_TYPE_KEY_NAME)).key));
    }

    /**
     * Returns mapping of adapter link to adapter resource operation specs (those are adapters
     * that should be registered into Resource Operation Registry).
     */
    public static Map<String, ResourceOperationSpec[]> getResourceOperationSpecs(
            ServiceMetadata[] adaptersMetadata) {
        return Arrays.stream(adaptersMetadata)
                .filter(aMD -> aMD.extension.containsKey(RESOURCE_OPERATION_SPECS_KEY_NAME))
                .collect(Collectors.toMap(
                        ServiceMetadata::getLink,
                        aMD -> (ResourceOperationSpec[]) aMD.extension
                                .get(RESOURCE_OPERATION_SPECS_KEY_NAME))
                );
    }
}