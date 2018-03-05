/*
 * Copyright (c) 2018-2019 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.adapters.vsphere;

public class EntityUtils {

    public enum EntityType {
        VM("vm"),
        HOST("host"),
        COMPUTE_RESOURCE("domain-s"),
        CLUSTER_COMPUTE_RESOURCE("domain-c"),
        RESOURCE_POOL("resgroup"),
        VM_FOLDER("group-v"),
        HOST_FOLDER("group-h"),
        DATACENTER_FOLDER("group-d"),
        DATACENTER("datacenter"),
        VAPP("resgroup-v"),
        DVSWITCH("dvs"),
        DVPORTGROUP("dvportgroup"),
        DATASTORE_FOLDER("group-s"),
        NETWORK_FOLDER("group-n"),
        DATASTORE("datastore"),
        NETWORK("network"),
        STORAGEPOD("group-p");

        private final String description;

        EntityType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return this.description;
        }
    }

    public static EntityType getObjectTypeFromManagedObjectId(String moid) {
        /*
         * Find closest match (for example, resgroup-v matches for resgroup and
         * resgroup-v. But, we need to take resgroup-v
         */
        int matchedLength = -1;
        EntityType matchedEntityType = null;
        for (EntityType entityType : EntityType.values()) {
            if (moid.startsWith(entityType.getDescription())) {
                if (entityType.getDescription().length() > matchedLength) {
                    matchedLength = entityType.getDescription().length();
                    matchedEntityType = entityType;
                }
            }
        }

        return matchedEntityType;
    }
}
