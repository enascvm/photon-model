/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.adapters.awsadapter.enumeration;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.amazonaws.regions.Regions;

import com.vmware.photon.controller.model.adapterapi.RegionEnumerationResponse;
import com.vmware.photon.controller.model.adapterapi.RegionEnumerationResponse.RegionInfo;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

public class AWSRegionEnumerationAdapterService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_REGION_ENUMERATION_ADAPTER_SERVICE;

    @Override
    public void handlePost(Operation post) {
        List<RegionInfo> regions = Arrays.stream(Regions.values())
                .map(r -> new RegionInfo(r.getName(), r.getName())).collect(
                        Collectors.toList());

        RegionEnumerationResponse result = new RegionEnumerationResponse();
        result.regions = regions;

        post.setBody(result);
        post.complete();
    }
}
