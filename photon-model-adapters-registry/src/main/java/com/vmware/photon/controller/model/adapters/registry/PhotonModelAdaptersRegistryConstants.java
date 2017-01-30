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

/**
 * Photon model adapters registry related constants.
 */
public class PhotonModelAdaptersRegistryConstants {
    /**
     * A key for the ui link entry of the {@link com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService.PhotonModelAdapterConfig}'s
     * customProperties, specifying the path to javascript file with custom(contributed) ui for the
     * adapter (e.g. endpointEditor)
     */
    public static final String UI_LINK_KEY = "uiLink";
    /**
     * A key for the ui link entry of the {@link com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService.PhotonModelAdapterConfig}'s
     * customProperties, specifying the path to adapter's icon to use in the UI
     */
    public static final String ICON_KEY = "icon";
}
