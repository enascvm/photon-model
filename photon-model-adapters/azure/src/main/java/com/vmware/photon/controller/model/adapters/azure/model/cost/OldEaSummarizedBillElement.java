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

import com.google.gson.annotations.SerializedName;

/**
 * POJO for storing individual JSON elements of the response obtained for the following request:
 * https://ea.azure.com/rest/{enrollmentNumber}/usage-report?month={month}&type=summary&fmt=json
 */
public class OldEaSummarizedBillElement {
    @SerializedName("Azure Service Commitment")
    public String serviceCommitment;
    @SerializedName("Currency Code")
    public String currencyCode;
    @SerializedName("Amount")
    public String amount;

    public static final String BILL_TYPE = "summary";
    public static final String SUMMARIZED_CSV_BILL_NAME_MID = "-summarized-bill";

    @Override public String toString() {
        return "OldEaSummarizedBillElement{" +
                "serviceCommitment='" + this.serviceCommitment + '\'' +
                ", currencyCode='" + this.currencyCode + '\'' +
                ", amount='" + this.amount + '\'' +
                '}';
    }

}
