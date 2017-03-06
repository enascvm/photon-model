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

/**
 * PODO for storing individual JSON elements of the response obtained for the following request:
 * https://consumption.azure.com/v1/enrollments/{enrollmentNumber}/billingperiods
 */
public class BillingPeriodElement {
    public String billingPeriodId;
    public String billingStart;
    public String billingEnd;
    public String balanceSummary;
    public String usageDetails;
    public String marketplaceCharges;
    public String priceSheet;

    @Override
    public String toString() {
        return "BillingPeriodElement{" +
                "billingPeriodId='" + this.billingPeriodId + '\'' +
                ", billingStart='" + this.billingStart + '\'' +
                ", billingEnd='" + this.billingEnd + '\'' +
                ", balanceSummary='" + this.balanceSummary + '\'' +
                ", usageDetails='" + this.usageDetails + '\'' +
                ", marketplaceCharges='" + this.marketplaceCharges + '\'' +
                ", priceSheet='" + this.priceSheet + '\'' +
                '}';
    }
}