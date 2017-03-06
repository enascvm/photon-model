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

public class EaSummarizedBillElement {
    public String id;
    public String billingPeriodId;
    public String currencyCode;
    public String beginningBalance;
    public String endingBalance;
    public String newPurchases;
    public String adjustments;
    public String utilized;
    public String serviceOverage;
    public String chargesBilledSeparately;
    public String totalOverage;
    public String totalUsage;
    public String azureMarketplaceServiceCharges;
    public transient String newPurchasesDetails;
    public transient String adjustmentDetails;

    @Override public String toString() {
        return "EaSummarizedBillElement{" +
                "id='" + this.id + '\'' +
                ", billingPeriodId='" + this.billingPeriodId + '\'' +
                ", currencyCode='" + this.currencyCode + '\'' +
                ", beginningBalance='" + this.beginningBalance + '\'' +
                ", endingBalance='" + this.endingBalance + '\'' +
                ", newPurchases='" + this.newPurchases + '\'' +
                ", adjustments='" + this.adjustments + '\'' +
                ", utilized='" + this.utilized + '\'' +
                ", serviceOverage='" + this.serviceOverage + '\'' +
                ", chargesBilledSeparately='" + this.chargesBilledSeparately + '\'' +
                ", totalOverage='" + this.totalOverage + '\'' +
                ", totalUsage='" + this.totalUsage + '\'' +
                ", azureMarketplaceServiceCharges='" + this.azureMarketplaceServiceCharges + '\'' +
                ", newPurchasesDetails='" + this.newPurchasesDetails + '\'' +
                ", adjustmentDetails='" + this.adjustmentDetails + '\'' +
                '}';
    }
}
