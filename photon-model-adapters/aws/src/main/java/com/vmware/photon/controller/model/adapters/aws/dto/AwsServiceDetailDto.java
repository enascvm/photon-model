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

public class AwsServiceDetailDto {
    public String name;
    public String id;
    public Map<Long, Double> directCosts = new HashMap<>();
    public Map<Long, Double> otherCosts = new HashMap<>();
    public Double remainingCost = 0d;
    public Double reservedRecurringCost = 0d;
    public Map<String, AwsResourceDetailDto> resourceDetailsMap;
    public String type;

    public void addToRemainingCost(Double otherCost) {
        this.remainingCost += otherCost;
    }

    public void addToOtherCosts(Long time, Double otherCost) {
        Double daysOtherCost = this.otherCosts.get(time);
        daysOtherCost = daysOtherCost == null ? otherCost : daysOtherCost + otherCost;
        this.otherCosts.put(time, daysOtherCost);
    }

    public void addToDirectCosts(Long time, Double directCost) {
        Double daysDirectCost = this.directCosts.get(time);
        daysDirectCost = daysDirectCost == null ? directCost : daysDirectCost + directCost;
        this.directCosts.put(time, daysDirectCost);
    }

    public void addToReservedRecurringCost(Double reservedRecurringCost) {
        this.reservedRecurringCost += this.reservedRecurringCost;
    }

    public AwsResourceDetailDto getResourceDetail(String resourceId) {
        if (this.resourceDetailsMap != null) {
            return this.resourceDetailsMap.get(resourceId);
        }
        return null;
    }

    public void addToResourceDetailMap(String resourceId, AwsResourceDetailDto resourceDetail) {
        if (this.resourceDetailsMap == null) {
            this.resourceDetailsMap = new HashMap<>();
        }
        this.resourceDetailsMap.put(resourceId, resourceDetail);
    }

}
