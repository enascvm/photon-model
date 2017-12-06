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

package com.vmware.photon.controller.model.resources;

import static com.vmware.photon.controller.model.resources.SessionService.FACTORY_LINK;
import static com.vmware.photon.controller.model.util.PhotonModelUriUtils.createInventoryUri;

import java.net.URI;

import com.vmware.photon.controller.model.resources.SessionService.SessionState;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * The purpose of this custom FactoryService is to ensure only a system service is able to create
 * new {@link SessionState} documents.
 */
public class SessionFactoryService extends FactoryService {

    public static URI buildSessionURI(Service service, AuthorizationContext ctx) {
        return createInventoryUri(service.getHost(), UriUtils.buildUriPath(FACTORY_LINK,
                Utils.computeHash(ctx.getToken())));
    }

    public SessionFactoryService() {
        super(SessionState.class);
        setUseBodyForSelfLink(true);
    }

    @Override
    public void authorizeRequest(Operation op) {
        // The following allows a system user to create new session states. This also implies
        // that the caller is running on the same Xenon ServiceHost
        if (op.getAuthorizationContext() != null && op.getAuthorizationContext().isSystemUser()) {
            op.complete();
            return;
        }
        op.fail(Operation.STATUS_CODE_UNAUTHORIZED);
    }

    /**
     * Override the buildDefaultChildSelfLink method to set the documentSelfLink.
     */
    @Override
    protected String buildDefaultChildSelfLink(ServiceDocument document) {
        SessionState state = (SessionState) document;
        return Utils.computeHash(state.localToken);
    }

    @Override
    public Service createServiceInstance() {
        return new SessionService();
    }

}
