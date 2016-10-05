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

public class AwsResourceDetailDto {
    public String serviceName;
    public String availabilityZone;
    public String itemDescription;
    public Map<Long, Double> directCosts = new HashMap<Long, Double>();
    public Long usageStartTime;
    public Map<String, String> tags;
    public boolean isReservedInstance;
    public String type;
    public String state;
    public Map<Long, Double> hoursAsReservedPerDay = new HashMap<>();
    public String name;

    @Override
    public String toString() {
        return "AwsResourceDetails [availabilityZone=" + this.availabilityZone
                + ", isReservedInstance="
                + this.isReservedInstance
                + ", itemDescription=" + this.itemDescription + ", usageStartTime="
                + this.usageStartTime
                + ", usageEndTime="
                + ", tags=" + this.tags + "]";
    }

    public void addToDirectCosts(Long time, double directCost) {
        Double daysDirectCost = this.directCosts.get(time);
        daysDirectCost = daysDirectCost == null ? directCost : daysDirectCost + directCost;
        this.directCosts.put(time, daysDirectCost);
    }

    public void addToHoursAsReservedPerDay(Long time, Double hoursAsReserved) {
        Double currentHoursAsReservedInDay = this.hoursAsReservedPerDay.get(time);
        currentHoursAsReservedInDay = currentHoursAsReservedInDay == null ? hoursAsReserved
                : hoursAsReserved + currentHoursAsReservedInDay;
        this.hoursAsReservedPerDay.put(time, currentHoursAsReservedInDay);
    }
}