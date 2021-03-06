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

import static com.vmware.photon.controller.model.resources.SessionFactoryService.buildSessionURI;

import com.vmware.photon.controller.model.resources.SessionService.SessionState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class SessionUtil {

    public static DeferredResult<AuthCredentialsServiceState> retrieveExternalToken(Service
            service, AuthorizationContext ctx) {
        return service.sendWithDeferredResult(Operation.createGet(buildSessionURI(service, ctx)),
                SessionState.class).thenApply(sessionState -> {
                    AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
                    creds.privateKey = sessionState.externalToken;
                    return creds;
                });
    }
}
