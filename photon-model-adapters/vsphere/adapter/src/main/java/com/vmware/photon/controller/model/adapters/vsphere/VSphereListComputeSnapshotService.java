/*
 * Copyright (c) 2015-2017 VMware, Inc. All Rights Reserved.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.SnapshotService;
import com.vmware.photon.controller.model.util.ClusterUtil;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.QueryResultsProcessor;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Service that returns a list of snapshot states given the computeLink. We need
 * to provide a valid computeLink in the "compute" uri parameter
 */
public class VSphereListComputeSnapshotService extends StatelessService {

    public static final String SELF_LINK = VSphereUriPaths.COMPUTE_SNAPSHOT_SERVICE;
    public static final String QUERY_PARAM_COMPUTE = "compute";

    @Override
    public void handleStart(Operation start) {
        super.handleStart(start);
    }

    @Override
    public void handleStop(Operation stop) {
        super.handleStop(stop);
    }

    @Override
    public void handleGet(Operation get) {
        Map<String, String> params = UriUtils.parseUriQueryParams(get.getUri());
        String computeLink = params.get(QUERY_PARAM_COMPUTE);
        if (computeLink == null || computeLink.isEmpty()) {
            get.fail(new IllegalArgumentException(
                    "'" + QUERY_PARAM_COMPUTE + "' query param is required"));
            return;
        }

        QueryTask.Query snapshotQuery = QueryTask.Query.Builder.create()
                .addKindFieldClause(SnapshotService.SnapshotState.class)
                .addFieldClause(SnapshotService.SnapshotState.FIELD_NAME_COMPUTE_LINK, computeLink)
                .build();
        QueryTask qTask = QueryTask.Builder.createDirectTask()
                .setQuery(snapshotQuery)
                .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
                .addOption(QueryTask.QuerySpecification.QueryOption.INDEXED_METADATA)
                .build();

        QueryUtils.startQueryTask(this, qTask, ClusterUtil.ServiceTypeCluster
                .DISCOVERY_SERVICE)
                .thenApply(op -> {
                    QueryResultsProcessor rp = QueryResultsProcessor.create(op);
                    List<SnapshotService.SnapshotState> snapshots = new ArrayList<>();
                    if (rp.hasResults()) {
                        snapshots = rp.streamDocuments(SnapshotService.SnapshotState.class)
                                .collect(Collectors.toList());
                    }
                    return snapshots;
                })
                .whenComplete((result, e) -> {
                    if (e != null) {
                        get.fail(e);
                    } else {
                        get.setBody(result).complete();
                    }
                });
    }
}
