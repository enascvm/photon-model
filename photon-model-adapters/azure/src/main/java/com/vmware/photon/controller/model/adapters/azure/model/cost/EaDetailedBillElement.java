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

import com.opencsv.bean.CsvBindByName;

/**
 * POJO for storing individual JSON elements of the response obtained for the following request:
 * https://ea.azure.com/rest/{enrollmentNumber}/usage-report?month={month}&type=detail&fmt=json
 */
public class EaDetailedBillElement {
    public static final String AccountOwnerIdField = "AccountOwnerId";
    public static final String AccountNameField = "Account Name";
    public static final String ServiceAdministratorIdField = "ServiceAdministratorId";
    public static final String SubscriptionIdField = "SubscriptionId";
    public static final String SubscriptionGuidField = "SubscriptionGuid";
    public static final String SubscriptionNameField = "Subscription Name";
    public static final String DateField = "Date";
    public static final String MonthField = "Month";
    public static final String DayField = "Day";
    public static final String YearField = "Year";
    public static final String ProductField = "Product";
    public static final String MeterIdField = "Meter ID";
    public static final String MeterCategoryField = "Meter Category";
    public static final String MeterSubCategoryField = "Meter Sub-Category";
    public static final String MeterRegionField = "Meter Region";
    public static final String MeterNameField = "Meter Name";
    public static final String ConsumedQuantityField = "Consumed Quantity";
    public static final String ResourceRateField = "ResourceRate";
    public static final String ExtendedCostField = "ExtendedCost";
    public static final String ResourceLocationField = "Resource Location";
    public static final String ConsumedServiceField = "Consumed Service";
    public static final String InstanceIdField = "Instance ID";
    public static final String ServiceInfo1Field = "ServiceInfo1";
    public static final String ServiceInfo2Field = "ServiceInfo2";
    public static final String AdditionalInfoField = "AdditionalInfo";
    public static final String TagsField = "Tags";
    public static final String StoreServiceIdentifierField = "Store Service Identifier";
    public static final String DepartmentNameField = "Department Name";
    public static final String CostCenterField = "Cost Center";
    public static final String UnitOfMeasureField = "Unit Of Measure";
    public static final String ResourceGroupField = "Resource Group";

    @CsvBindByName(column = AccountOwnerIdField)
    public String accountOwnerId;
    @CsvBindByName(column = AccountNameField)
    public String accountName;
    @CsvBindByName(column = ServiceAdministratorIdField)
    public String serviceAdministratorId;
    @CsvBindByName(column = SubscriptionIdField)
    public String subscriptionId;
    @CsvBindByName(column = SubscriptionGuidField)
    public String subscriptionGuid;
    @CsvBindByName(column = SubscriptionNameField)
    public String subscriptionName;
    @CsvBindByName(column = DateField)
    public String date;
    public long epochDate;
    @CsvBindByName(column = MonthField)
    public int month;
    @CsvBindByName(column = DayField)
    public int day;
    @CsvBindByName(column = YearField)
    public int year;
    @CsvBindByName(column = ProductField)
    public String product;
    @CsvBindByName(column = MeterIdField)
    public String meterId;
    @CsvBindByName(column = MeterCategoryField)
    public String meterCategory;
    @CsvBindByName(column = MeterSubCategoryField)
    public String meterSubCategory;
    @CsvBindByName(column = MeterRegionField)
    public String meterRegion;
    @CsvBindByName(column = MeterNameField)
    public String meterName;
    @CsvBindByName(column = ConsumedQuantityField)
    public double consumedQuantity;
    @CsvBindByName(column = ResourceRateField)
    public double resourceRate;
    @CsvBindByName(column = ExtendedCostField)
    public double extendedCost;
    @CsvBindByName(column = ResourceLocationField)
    public String resourceLocation;
    @CsvBindByName(column = ConsumedServiceField)
    public String consumedService;
    @CsvBindByName(column = InstanceIdField)
    public String instanceId;
    @CsvBindByName(column = ServiceInfo1Field)
    public String serviceInfo1;
    @CsvBindByName(column = ServiceInfo2Field)
    public String serviceInfo2;
    @CsvBindByName(column = AdditionalInfoField)
    public String additionalInfo;
    @CsvBindByName(column = TagsField)
    public String tags;
    @CsvBindByName(column = StoreServiceIdentifierField)
    public String storeServiceIdentifier;
    @CsvBindByName(column = DepartmentNameField)
    public String departmentName;
    @CsvBindByName(column = CostCenterField)
    public String costCenter;
    @CsvBindByName(column = UnitOfMeasureField)
    public String unitOfMeasure;
    @CsvBindByName(column = ResourceGroupField)
    public String resourceGroup;

    @Override public String toString() {
        return "EaDetailedBillElement{" +
                "accountOwnerId='" + this.accountOwnerId + '\'' +
                ", accountName='" + this.accountName + '\'' +
                ", serviceAdministratorId='" + this.serviceAdministratorId + '\'' +
                ", subscriptionId='" + this.subscriptionId + '\'' +
                ", subscriptionGuid='" + this.subscriptionGuid + '\'' +
                ", subscriptionName='" + this.subscriptionName + '\'' +
                ", date='" + this.date + '\'' +
                ", epochDate=" + this.epochDate +
                ", month=" + this.month +
                ", day=" + this.day +
                ", year=" + this.year +
                ", product='" + this.product + '\'' +
                ", meterId='" + this.meterId + '\'' +
                ", meterCategory='" + this.meterCategory + '\'' +
                ", meterSubCategory='" + this.meterSubCategory + '\'' +
                ", meterRegion='" + this.meterRegion + '\'' +
                ", meterName='" + this.meterName + '\'' +
                ", consumedQuantity=" + this.consumedQuantity +
                ", resourceRate=" + this.resourceRate +
                ", extendedCost=" + this.extendedCost +
                ", resourceLocation='" + this.resourceLocation + '\'' +
                ", consumedService='" + this.consumedService + '\'' +
                ", instanceId='" + this.instanceId + '\'' +
                ", serviceInfo1='" + this.serviceInfo1 + '\'' +
                ", serviceInfo2='" + this.serviceInfo2 + '\'' +
                ", additionalInfo='" + this.additionalInfo + '\'' +
                ", tags='" + this.tags + '\'' +
                ", storeServiceIdentifier='" + this.storeServiceIdentifier + '\'' +
                ", departmentName='" + this.departmentName + '\'' +
                ", costCenter='" + this.costCenter + '\'' +
                ", unitOfMeasure='" + this.unitOfMeasure + '\'' +
                ", resourceGroup='" + this.resourceGroup + '\'' +
                '}';
    }
}
