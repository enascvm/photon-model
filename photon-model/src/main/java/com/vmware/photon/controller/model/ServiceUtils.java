/*
 * Copyright (c) 2014-2015 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model;

import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;

public class ServiceUtils {

    public static final int SERVICE_DOCUMENT_VERSION_RETENTION_LIMIT = Integer
            .getInteger("service.document.version.retention.limit",
                    ServiceDocumentDescription.DEFAULT_VERSION_RETENTION_LIMIT);
    public static final int SERVICE_DOCUMENT_VERSION_RETENTION_FLOOR = Integer
            .getInteger("service.document.version.retention.floor",
                    ServiceDocumentDescription.DEFAULT_VERSION_RETENTION_FLOOR);

    public static void setRetentionLimit(ServiceDocument template) {
        template.documentDescription.versionRetentionLimit = SERVICE_DOCUMENT_VERSION_RETENTION_LIMIT;
        template.documentDescription.versionRetentionFloor = SERVICE_DOCUMENT_VERSION_RETENTION_FLOOR;
    }
}
