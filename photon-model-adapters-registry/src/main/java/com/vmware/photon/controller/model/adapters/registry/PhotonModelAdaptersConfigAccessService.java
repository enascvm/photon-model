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

package com.vmware.photon.controller.model.adapters.registry;

import java.net.URI;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

/**
 * The service provides read-only view on top of {@link PhotonModelAdapterConfig}s.
 *
 * <p>
 * The task SHOULD be started as privileged service in order to call
 * {@link PhotonModelAdaptersRegistryService} with system auth context to get non-tenanted
 * {@code PhotonModelAdapterConfig} data. Otherwise will called it with current context.
 */
public class PhotonModelAdaptersConfigAccessService extends StatelessService {

    public static final String SELF_LINK = UriPaths.CONFIG + "/photon-model-adapters-config";

    public PhotonModelAdaptersConfigAccessService() {
        super.toggleOption(ServiceOption.URI_NAMESPACE_OWNER, true);
    }

    @Override
    public void handleGet(Operation get) {

        final String configLink = extractConfigLink(get);

        final Operation configOp;

        if (configLink == null || configLink.isEmpty()) {
            // return all configs
            configOp = doGetAll(get);
        } else {
            // return the requested config
            configOp = doGet(get, configLink);
        }

        try {
            setAuthorizationContext(configOp, getSystemAuthorizationContext());
        } catch (RuntimeException notPrivilegedExc) {
            // Defensive programming. Sym and Prov hosts should start it as Privileged.
            logWarning("'%s' is not started as Privileged", getClass().getSimpleName());
        }

        configOp.sendWith(this);
    }

    private Operation doGetAll(Operation get) {

        URI configUri = UriUtils.buildUri(
                getHost(),
                PhotonModelAdaptersRegistryService.FACTORY_LINK,
                get.getUri().getQuery());

        return Operation.createGet(configUri)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        get.fail(e);
                        return;
                    }
                    get.setBodyNoCloning(o.getBodyRaw());
                    get.complete();
                });
    }

    private Operation doGet(Operation get, String configLink) {

        configLink = UriUtils.buildUriPath(
                PhotonModelAdaptersRegistryService.FACTORY_LINK, configLink);

        return Operation.createGet(this, configLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        get.fail(o.getStatusCode(), e, o.getBodyRaw());
                        return;
                    }
                    get.setBodyNoCloning(o.getBodyRaw());
                    get.complete();
                });
    }

    private String extractConfigLink(Operation op) {
        String currentPath = UriUtils.normalizeUriPath(op.getUri().getPath());
        // resolve the link to the requested config
        String configLink = null;
        if (currentPath.startsWith(SELF_LINK)) {
            configLink = currentPath.substring(SELF_LINK.length());
        }
        return configLink;
    }
}