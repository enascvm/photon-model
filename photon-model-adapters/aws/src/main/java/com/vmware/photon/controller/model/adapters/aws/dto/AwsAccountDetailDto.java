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
    public Double cost = 0d;
    public Map<String, AwsServiceDetailDto> serviceDetailsMap = new HashMap<>();
    //This will contain all taxes, like VAT,Sales Tax etc for this account.
    public Double otherCharges = 0d;
    public Double accountOneTimeCharges = 0d;
    // Will contain the usageStartTime of the row in the bill that was successfully persisted in
    // the last collection cycle. The bill records (rows) will be processed beginning from
    // the next record in the bill being processed in the current collection cycle. In case
    // this value is 0, the complete bill be processed and persisted.
    public Long billProcessedTimeMillis = 0L;
    // if lineItem is hourly, key will be hour startTime,otherwise key will be interval
    public Map<String, Integer> lineCountPerInterval = new HashMap<>();

    public AwsServiceDetailDto fetchServiceDetail(String serviceName) {
        return this.serviceDetailsMap.get(serviceName);
    }

    public void addToServiceDetailsMap(String serviceName, AwsServiceDetailDto serviceDetail) {
        this.serviceDetailsMap.put(serviceName, serviceDetail);
    }
}
