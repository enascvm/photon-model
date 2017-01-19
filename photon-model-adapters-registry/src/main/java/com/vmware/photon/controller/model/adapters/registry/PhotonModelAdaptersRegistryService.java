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

package com.vmware.photon.controller.model.adapters.registry;

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;

import java.util.Map;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;

/**
 * Represents an photon adapter registry resource.
 */
public class PhotonModelAdaptersRegistryService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.CONFIG + "/photon-model-adapters-registry";

    /**
     * This class represents the document state associated with a
     * {@link PhotonModelAdaptersRegistryService}.
     */
    public static class PhotonModelAdapterConfig extends ServiceDocument {
        public static final String FIELD_NAME_ID = "id";
        public static final String FIELD_NAME_NAME = "name";
        public static final String FIELD_NAME_CUSTOM_PROPERTIES = "customProperties";
        public static final String FIELD_NAME_ADAPTER_ENDPOINTS = "adapterEndpoints";

        /**
         * Identifier of the photon model adapter instance.
         */
        @Documentation(description = "Identifier of the photon model adapter configuration.",
                exampleString = "openstack, virtustream, etc.")
        @UsageOption(option = PropertyUsageOption.ID)
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.SINGLE_ASSIGNMENT)
        public String id;

        /**
         * Name of the photon model adapter instance
         */
        @Documentation(description = "Name of the photon model adapter configuration.",
                exampleString = "Openstack, Virtustream, etc.")
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @PropertyOptions(
                indexing = { PropertyIndexingOption.CASE_INSENSITIVE, PropertyIndexingOption.SORT })
        public String name;

        /**
         * Custom property bag that can be used to store photon model adapter's specific properties.
         */
        @Documentation(description = "Custom property bag that can be used to store "
                + "photon model adapter specific properties.")
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @PropertyOptions(
                indexing = { PropertyIndexingOption.CASE_INSENSITIVE, PropertyIndexingOption.EXPAND,
                        PropertyIndexingOption.FIXED_ITEM_NAME })
        public Map<String, String> customProperties;

        @Documentation(description = "Map with endpoints, given photon adapter provides."
                + "The key represents the endpoint type id (e.g. instanceAdapterLink, "
                + "enumerationAdapterLink, etc) and the value is the actual link (url) of the "
                + "endpoint")
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL }, indexing = {
                PropertyIndexingOption.CASE_INSENSITIVE,
                PropertyIndexingOption.EXPAND,
                PropertyIndexingOption.FIXED_ITEM_NAME })
        public Map<String, String> adapterEndpoints;

        @Override
        public String toString() {
            return String.format("%s[id=%s, name=%s, customProperties=%s, adapterEndpoints=%s]",
                    getClass().getSimpleName(), this.id, this.name,
                    this.customProperties, this.adapterEndpoints);
        }
    }

    public PhotonModelAdaptersRegistryService() {
        super(PhotonModelAdapterConfig.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }
}