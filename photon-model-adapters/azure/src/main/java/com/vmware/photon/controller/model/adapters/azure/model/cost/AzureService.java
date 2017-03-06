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
 * 1. Fields example (taken from the bill):
 *         Subscription ID: 29383096742
 *         Subscription Name: Microsoft Azure Enterprise
 *         Meter ID: 8ae013c5-03d7-48bf-b190-0227b00a6b93
 *         Meter Name: Compute Hours
 *         Meter Category: Virtual Machines
 *         Meter SubCategory: A1 VM (Windows)
 *         Meter Region: AP East
 *         Direct cost: 1488306600 : 1.299902551
 */
public class AzureService {
    public String subscriptionGuid;
    public String meterId;
    public String meterName; // Different from meter category- refer to class comment #1 for more.
    public String meterCategory;
    public String meterSubCategory;
    public String meterRegion;
    public Map<Long, Double> directCosts = new HashMap<>();
    public Map<String, AzureResource> resourceDetailsMap = new HashMap<>();

    public AzureService() {
    }

    public String getSubscriptionId() {
        return this.subscriptionGuid;
    }

    /**
     * Will add to map of direct costs: the map contains the day as the key for which the cost is
     * being stored (with the cost as the value).
     * @param time in epoch, representing the day
     * @param directCost the cost corresponding to the particular day.
     */
    public AzureService addToDailyCosts(Long time, double directCost) {
        if (directCost == 0) {
            return this;
        }
        Double daysDirectCost = this.directCosts.get(time);
        daysDirectCost = daysDirectCost == null ? directCost : daysDirectCost + directCost;
        this.directCosts.put(time, daysDirectCost);
        return this;
    }

    /**
     * Check if the resource presented as input are present in the services' map. If not, add
     * the new resource and its details to the services map. If the resource was already present,
     * update the cost of the resource in the services map.
     * @param detailedBillElement the line item in the bill
     */
    public void addToResourcesMap(EaDetailedBillElement detailedBillElement) {
        AzureResource resourceDetails = this.resourceDetailsMap
                .get(detailedBillElement.instanceId);
        if (resourceDetails == null) {
            resourceDetails = createResourceDetailsDto(detailedBillElement);
        } else {
            updateResourceDetailsDto(resourceDetails, detailedBillElement);
        }
        this.resourceDetailsMap.put(detailedBillElement.instanceId, resourceDetails);
    }

    /**
     * Create a new resource details DTO and add details to it.
     * @param detailedBillElement the line item in the bill providing details about the new resource.
     * @return the newly created resource details.
     */
    private AzureResource createResourceDetailsDto(
            EaDetailedBillElement detailedBillElement) {
        AzureResource resource = new AzureResource();
        resource.resourceUri = detailedBillElement.instanceId;
        resource.resourceGroup = detailedBillElement.resourceGroup;
        resource.tags = detailedBillElement.tags;
        resource.resourceLocation = detailedBillElement.resourceLocation;
        resource.directCost(detailedBillElement.epochDate,
                        detailedBillElement.extendedCost);
        return resource;
    }

    /**
     * Update the details of a resource present in the services map
     * @param resource the details of the resource present in the map
     * @param detailedBillElement the line item in the bill providing new details about the resource.
     */
    private void updateResourceDetailsDto(AzureResource resource,
            EaDetailedBillElement detailedBillElement) {
        resource.resourceGroup = detailedBillElement.resourceGroup;
        resource.tags = detailedBillElement.tags;
        resource.resourceLocation = detailedBillElement.resourceLocation;
        resource.addToDirectCosts(detailedBillElement.epochDate,
                        detailedBillElement.extendedCost);
    }

}