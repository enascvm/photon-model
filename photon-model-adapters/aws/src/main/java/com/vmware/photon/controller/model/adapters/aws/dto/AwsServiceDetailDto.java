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
    public Map<Long, Double> remainingCosts = new HashMap<>();
    public Map<Long, Double> reservedRecurringCosts = new HashMap<>();
    public Map<String, AwsResourceDetailDto> resourceDetailsMap = new HashMap<>();
    public String type;

    public void addToRemainingCosts(Long time, Double remainingCost) {
        Double daysRemainingCost = this.remainingCosts.get(time);
        daysRemainingCost = daysRemainingCost == null ? remainingCost : daysRemainingCost + remainingCost;
        this.remainingCosts.put(time, daysRemainingCost);
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

    public void addToReservedRecurringCosts(Long time, Double recurringCost) {
        Double daysRecurringCost = this.reservedRecurringCosts.get(time);
        daysRecurringCost = daysRecurringCost == null ? recurringCost : daysRecurringCost + recurringCost;
        this.reservedRecurringCosts.put(time, daysRecurringCost);
    }

    public AwsResourceDetailDto getResourceDetail(String resourceId) {
        return this.resourceDetailsMap.get(resourceId);
    }

    public void addToResourceDetailMap(String resourceId, AwsResourceDetailDto resourceDetail) {
        this.resourceDetailsMap.put(resourceId, resourceDetail);
    }
}
