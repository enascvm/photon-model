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

package com.vmware.photon.controller.model.adapters.aws.dto;

import java.util.HashMap;
import java.util.Map;

public class AwsAccountDetailDto {

    public String id;
    public String name;
    public Long month;
    public Double cost = 0d;
    public Map<String, AwsServiceDetailDto> serviceDetailsMap;
    public Double signUpCharge = 0d;

    public AwsServiceDetailDto fetchServiceDetail(String serviceName) {
        if (this.serviceDetailsMap != null) {
            return this.serviceDetailsMap.get(serviceName);
        }
        return null;
    }

    public void addToServiceDetailsMap(String serviceName, AwsServiceDetailDto serviceDetail) {
        if (this.serviceDetailsMap == null) {
            this.serviceDetailsMap = new HashMap<>();
        }
        this.serviceDetailsMap.put(serviceName, serviceDetail);
    }

}
