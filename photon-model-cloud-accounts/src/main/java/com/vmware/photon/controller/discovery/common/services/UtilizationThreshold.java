/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.discovery.common.services;

import com.vmware.xenon.common.ServiceDocument.Documentation;

/**
 * Defines an utilization threshold in the form of an under and over utilized
 * limits. If needed a limit unit can be provided (TB, GB) otherwise the limit
 * is assumed to be expressed as a percentage.
 */
public class UtilizationThreshold {

    @Documentation(description = "under utilization limit")
    public Integer underLimit;

    @Documentation(description = "over utilization limit")
    public Integer overLimit;

    @Documentation(description = "optional limit unit")
    public String unit;

}