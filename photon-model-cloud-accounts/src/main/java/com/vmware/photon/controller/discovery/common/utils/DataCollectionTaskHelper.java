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

package com.vmware.photon.controller.discovery.common.utils;

import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;

/**
 * Helper methods for tasks related to data collection
 */
public class DataCollectionTaskHelper {

    /**
     * Query clause created for stats collection for data collection to be performed only for
     * resources of compute type 'ENDPOINT_HOST' and 'VM_GUEST'.
     */
    public static Query getStatsCollectionTaskQuery() {
        // stats collection should be run only for resources of compute type 'ENDPOINT_HOST' and 'VM_GUEST'
        Query hostTypeQuery = Query.Builder
                .create(Occurance.SHOULD_OCCUR)
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_TYPE, ComputeType.ENDPOINT_HOST.name())
                .build();

        Query guestTypeQuery = Query.Builder
                .create(Occurance.SHOULD_OCCUR)
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_TYPE, ComputeType.VM_GUEST.name())
                .build();

        Query typeQuery = Query.Builder
                .create(Occurance.MUST_OCCUR)
                .addClauses(hostTypeQuery, guestTypeQuery)
                .build();

        return typeQuery;
    }

    public static Query getOptionalStatsAdapterTaskQuery() {
        // Ensure that the stats collection is fired only for the accounts added by the user.
        return Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_TYPE, ComputeType.ENDPOINT_HOST.name())
                .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        PhotonModelConstants.AUTO_DISCOVERED_ENTITY, Boolean.TRUE.toString(), Occurance.MUST_NOT_OCCUR)
                .build();
    }
}
