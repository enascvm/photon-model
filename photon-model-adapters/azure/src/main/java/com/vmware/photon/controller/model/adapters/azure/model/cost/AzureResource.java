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

import java.util.HashMap;
import java.util.Map;

/**
 * POJO for storing processed details of a resource. Entity ID of a resource is the resource
 * URI or the instance ID and the parent ID is the subscription GUID to which the resource
 * belongs to.
 */
public class AzureResource {

    public String resourceUri;
    public String resourceGroup;
    public String resourceLocation;
    public String tags;
    public Map<Long, Double> cost = new HashMap<>();

    AzureResource() {
    }

    public AzureResource(String resourceUri, Long time, double cost) {
        this.resourceUri = resourceUri;
        this.cost.put(time, cost);
    }

    AzureResource directCost(Long time, Double directCost) {
        if (directCost.compareTo(0d) != 0) {
            this.cost.put(time, directCost);
        }
        return this;
    }

    /**
     * Update cost only if non-zero. This is being done since it was observed that
     * a lot of cost values are zero and it isn't useful to store zero cost values.
     * The identity of the service is maintained, meaning the service details other than
     * its zero-cost values are known.
     */
    void addToDirectCosts(Long time, Double directCost) {
        if (directCost.compareTo(0d) == 0) {
            return;
        }
        Double daysDirectCost = this.cost.get(time);
        daysDirectCost = daysDirectCost == null ? directCost : daysDirectCost + directCost;
        this.cost.put(time, daysDirectCost);
    }
}