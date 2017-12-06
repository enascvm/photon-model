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

import java.util.concurrent.TimeUnit;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.security.util.EncryptionUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * A service which stores a relation between locally issued tokens and tokens issued by an
 * external service.
 * The relation is stored as a {@link SessionState}
 * <p>
 * Intended to work with VSphere tokens but can be used with any other remotely issued tokens
 * <p>
 */
public class SessionService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.SESSION_SERVICE;

    /**
     * If session expiration is not set, this value will be used as a default
     */
    public static final Long DEFAULT_SESSION_EXPIRATION_MICROS = TimeUnit.HOURS.toMicros(1L);

    /**
     * The SessionState represents the relation between the local and the externally issued tokens
     */
    public static class SessionState extends ServiceDocument {

        @Documentation(description = "The local token issued by Xenon")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String localToken;

        @Documentation(description = "Token issued by the external service")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String externalToken;

    }

    public SessionService() {
        super(SessionState.class);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handlePut(Operation op) {
        if (op.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_POST_TO_PUT)) {
            op.complete();
            return;
        }
        // normal PUT is not supported
        op.fail(Operation.STATUS_CODE_BAD_METHOD);
    }

    @Override
    public void handleCreate(Operation op) {
        if (checkForValid(op)) {
            SessionState body = op.getBody(SessionState.class);
            body.externalToken = EncryptionUtils.encrypt(body.externalToken);

            super.handleCreate(op);
        }
    }

    @Override
    public void handleGet(Operation op) {
        if (op.getAuthorizationContext() != null && op.getAuthorizationContext().isSystemUser()) {
            super.handleGet(op);
            return;
        }

        // Allows a user to access her own token map
        if (op.getAuthorizationContext() != null &&
                op.getAuthorizationContext().getToken() != null &&
                op.getUri().getPath().endsWith(
                        FACTORY_LINK + "/" +
                                Utils.computeHash(op.getAuthorizationContext().getToken()))) {

            super.handleGet(op);
            return;
        }
        op.fail(Operation.STATUS_CODE_UNAUTHORIZED);
    }

    private boolean checkForValid(Operation op) {
        if (op.hasBody()) {
            try {
                SessionState state = op.getBody(SessionState.class);
                Utils.validateState(getStateDescription(), state);

                if (state.documentExpirationTimeMicros == 0L) {
                    state.documentExpirationTimeMicros = Utils.fromNowMicrosUtc
                            (DEFAULT_SESSION_EXPIRATION_MICROS);
                }

                return true;

            } catch (Throwable t) {
                op.fail(t);
                return false;
            }
        }
        return false;
    }
}
