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

import com.vmware.photon.controller.model.UriPaths;
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
 * Notable future work:
 * An authorization mechanism.
 * A periodic maintenance task will clean expired entries.
 */
public class SessionService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.SESSION_SERVICE;

    /**
     * The SessionState represents the relation between the local and the externally issued tokens
     */
    public static class SessionState extends ServiceDocument {

        @Documentation(description = "The local token issued by Xenon")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String localToken;

        @Documentation(description = "Expiration of the local token. In microseconds since UNIX "
                + "epoch")
        public Long localTokenExpiry;

        @Documentation(description = "Token issued by the external service")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String externalToken;

    }

    public SessionService() {
        super(SessionState.class);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
    }

    @Override
    public void handleCreate(Operation op) {
        validateInput(op);
        op.complete();
    }

    private void validateInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        SessionState state = op.getBody(SessionState.class);
        Utils.validateState(getStateDescription(), state);
    }

}