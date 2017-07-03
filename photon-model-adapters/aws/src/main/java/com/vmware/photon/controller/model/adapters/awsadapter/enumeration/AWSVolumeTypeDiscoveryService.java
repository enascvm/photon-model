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

package com.vmware.photon.controller.model.adapters.awsadapter.enumeration;

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.DEVICE_TYPE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.ec2.model.VolumeType;

import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

public class AWSVolumeTypeDiscoveryService extends StatelessService {
    public static final String SELF_LINK = AWSUriPaths.AWS_VOLUME_TYPE_ENUMERATION_ADAPTER_SERVICE;

    public static class VolumeTypeList {
        /**
         * List of multiple volumeTypes.
         */
        public List<String> volumeTypes = new ArrayList<>();
    }

    @Override
    public void handleGet(Operation get) {
        Map<String, String> params = UriUtils.parseUriQueryParams(get.getUri());
        String deviceType = params.get(DEVICE_TYPE);
        if (deviceType == null || deviceType.isEmpty()) {
            get.fail(new IllegalArgumentException("No deviceType provided."));
            return;
        }
        if (!deviceType.equals(AWSConstants.AWSStorageType.EBS.name().toLowerCase())) {
            get.fail(new IllegalArgumentException("Unsupported device Type"));
            return;
        }

        VolumeTypeList volumeTypeList = new VolumeTypeList();
        for (VolumeType volumeType : VolumeType.values()) {
            volumeTypeList.volumeTypes.add(volumeType.toString());
        }

        get.setBody(volumeTypeList);
        get.complete();
    }
}
