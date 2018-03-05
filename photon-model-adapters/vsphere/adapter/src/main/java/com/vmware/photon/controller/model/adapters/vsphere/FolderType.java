/*
 * Copyright (c) 2015-2016 VMware, Inc. All Rights Reserved.
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

public enum FolderType {
    VM_FOLDER(1),
    HOST_FOLDER(2),
    DATACENTER_FOLDER(3),
    DATASTORE_FOLDER(4),
    NETWORK_FOLDER(5),
    DATASTORE_CLUSTER(6),
    VAPP(7),
    DEFAULT_FOLDER(8);

    private int id;

    public int getId() {
        return this.id;
    }

    FolderType(int id) {
        this.id = id;
    }

    public static FolderType getFolderType(EntityUtils.EntityType entityType) {
        switch (entityType) {
        case VM_FOLDER:
            return FolderType.VM_FOLDER;
        case HOST_FOLDER:
            return FolderType.HOST_FOLDER;
        case DATACENTER_FOLDER:
            return FolderType.DATACENTER_FOLDER;
        case STORAGEPOD:
            return FolderType.DATASTORE_CLUSTER;
        case DATASTORE_FOLDER:
            return FolderType.DATASTORE_FOLDER;
        case NETWORK_FOLDER:
            return FolderType.NETWORK_FOLDER;
        case VAPP:
            return FolderType.VAPP;
        default:
            return FolderType.DEFAULT_FOLDER;
        }
    }
}
