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

package com.vmware.photon.controller.discovery.notification;

import static com.vmware.photon.controller.model.UriPaths.NOTIFICATION_MGMT;

import java.util.logging.Level;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class NotificationManagementService extends StatefulService {

    public static final String FACTORY_LINK = NOTIFICATION_MGMT;
    public static final String SINGLETON = "singleton";
    public static final String SINGLETON_LINK = UriUtils.buildUriPath(FACTORY_LINK, SINGLETON);

    public NotificationManagementService() {
        super(NotificationState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    public static class NotificationState extends ServiceDocument {
        @Documentation(description = "Flag that indicates whether notifications are enabled. Default: true")
        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.REQUIRED)
        public boolean isEnabled = true;
    }

    public static NotificationManagementService.NotificationState createSingletonState(Boolean isEnabled) {
        NotificationState state = new NotificationState();
        state.isEnabled = isEnabled;
        state.documentSelfLink = SINGLETON;
        return state;
    }

    public static Operation.CompletionHandler startService(ServiceHost host, Boolean isEnabled) {
        return (o, e) -> {
            if (e != null) {
                host.log(Level.SEVERE, Utils.toString(e));
                return;
            }
            o.complete();

            Operation.createPost(host, NotificationManagementService.FACTORY_LINK)
                    .setBody(NotificationManagementService.createSingletonState(isEnabled))
                    .setReferer(host.getUri())
                    .setCompletion((oo, ee) -> {
                        if (ee != null) {
                            host.log(Level.SEVERE, Utils.toString(ee));
                            oo.fail(ee);
                            return;
                        }
                        oo.complete();
                    }).sendWith(host);
        };
    }

    @Override
    public void handleStart(Operation start) {
        try {
            processInput(start);
            start.complete();
        } catch (Throwable t) {
            start.fail(t);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        if (!patch.hasBody()) {
            patch.fail(new IllegalArgumentException("Body is required."));
            return;
        }

        NotificationState body = getBody(patch);
        NotificationState state = getState(patch);

        state.isEnabled = body.isEnabled;
        setState(patch, state);

        patch.setBody(state);
        patch.complete();
    }

    @Override
    public void handlePut(Operation put) {
        if (put.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_FROM_MIGRATION_TASK)) {
            super.handlePut(put);
            return;
        }
        if (put.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_POST_TO_PUT)) {
            // converted PUT due to IDEMPOTENT_POST option
            logInfo("Service %s has already started. Ignoring converted PUT.", put.getUri());
            put.complete();
            return;
        }

        // normal PUT is not supported
        Operation.failActionNotSupported(put);
    }

    @Override
    public void handleDelete(Operation delete) {
        Operation.failActionNotSupported(delete);
    }

    private NotificationState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        NotificationState state = op.getBody(NotificationState.class);
        Utils.validateState(getStateDescription(), state);
        return state;
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        ServiceUtils.setRetentionLimit(td);
        NotificationState template = (NotificationState) td;
        return template;
    }
}