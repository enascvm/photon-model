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

package com.vmware.photon.controller.model.adapters.azure.model.cost;

/**
 * Basic entity to hold an Azure Entity.
 */
public class AzureEntity {
    public String entityId;
    public String parentEntityId;

    public AzureEntity () {
    }

    public AzureEntity(String entityId, String parentEntityId) {
        this.entityId = entityId;
        this.parentEntityId = parentEntityId;
    }

    @Override public String toString() {
        return "AzureEntity{" +
                "entityId='" + this.entityId + '\'' +
                ", parentEntityId='" + this.parentEntityId + '\'' +
                '}';
    }
}
