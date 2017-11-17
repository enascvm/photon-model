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
import java.util.concurrent.ConcurrentHashMap;

/**
 * POJO for storing processed details of a subscription. Entity ID for subscription is
 * the subscription GUID and the parent ID is the account owner ID to which the subscription
 * belongs to.
 */
public class AzureSubscription extends AzureEntity {

    public String subscriptionName;
    private Map<String, AzureService> serviceDetailsMap = new ConcurrentHashMap<>();
    // Stores the cost for a particular time-stamp
    public Map<Long, Double> cost = new HashMap<>();

    public AzureSubscription() {
    }

    public AzureSubscription(String subscriptionGuid, String subscriptionName,
            String accountOwnerId) {
        super.entityId = subscriptionGuid;
        super.parentEntityId = accountOwnerId;
        super.entityName = subscriptionName;
    }

    public AzureSubscription(String subscriptionGuid, String subscriptionName,
            String accountOwnerId, String accountOwnerName) {
        super.entityId = subscriptionGuid;
        super.parentEntityId = accountOwnerId;
        super.entityName = subscriptionName;
        super.parentEntityName = accountOwnerName;
    }

    public Map<String, AzureService> getServices() {
        return this.serviceDetailsMap;
    }

    public AzureService fetchServiceDetail(String serviceName) {
        return this.serviceDetailsMap.get(serviceName);
    }

    public AzureService createServiceDetailDto(
            EaDetailedBillElement detailedBillElement) {
        AzureService service = new AzureService();
        service.subscriptionGuid = detailedBillElement.subscriptionGuid;
        service.meterId = detailedBillElement.meterId;
        service.meterName = detailedBillElement.meterName;
        service.meterCategory = detailedBillElement.meterCategory;
        service.meterSubCategory = detailedBillElement.meterSubCategory;
        service.meterRegion = detailedBillElement.meterRegion;
        service.addToDailyCosts(detailedBillElement.epochDate, detailedBillElement.extendedCost);
        return service;
    }

    /**
     * Check if the service in the line item provided as input are present in the services' map.
     * If not, add the new service and its details to the accounts map. If the service was
     * already present, update the cost of the service in the accounts map.
     * @param detailedBillElement the line item in the bill
     */
    public AzureService addToServicesMap(EaDetailedBillElement detailedBillElement) {
        String meterCategory = detailedBillElement.meterCategory;
        AzureService serviceDetails = this.serviceDetailsMap.get(meterCategory);
        if (serviceDetails == null) {
            serviceDetails = createServiceDetailDto(detailedBillElement);
        } else {
            serviceDetails.addToDailyCosts(detailedBillElement.epochDate,
                    detailedBillElement.extendedCost);
        }
        this.serviceDetailsMap.put(meterCategory, serviceDetails);
        return serviceDetails;
    }

    public double getCost(long timeStampMillis) {
        return this.cost.get(timeStampMillis);
    }

    /**
     * Will add to map of direct costs: the map contains the day as the key for which the cost is
     * being stored (with the cost as the value).
     * @param timeMillis in epoch, representing the day
     * @param cost the cost corresponding to the particular day.
     */
    public AzureSubscription addToDailyCosts(Long timeMillis, double cost) {
        if (cost == 0 || timeMillis == null) {
            return this;
        }
        Double daysDirectCost = this.cost.get(timeMillis);
        daysDirectCost = daysDirectCost == null ? cost : daysDirectCost + cost;
        this.cost.put(timeMillis, daysDirectCost);
        return this;
    }

    @Override public String toString() {
        return "AzureSubscription{" +
                "subscriptionGuid='" + super.entityId + '\'' +
                ", accountOwnerId='" + super.parentEntityId + '\'' +
                ", cost=" + this.cost +
                '}';
    }
}