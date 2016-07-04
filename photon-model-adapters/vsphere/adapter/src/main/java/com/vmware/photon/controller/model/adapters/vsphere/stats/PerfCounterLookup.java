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


package com.vmware.photon.controller.model.adapters.vsphere.stats;

import java.util.List;

import com.vmware.vim25.PerfCounterInfo;
import com.vmware.vim25.PerfStatsType;
import com.vmware.vim25.PerfSummaryType;
import com.vmware.vim25.PerformanceManagerUnit;

/**
 * https://www.vmware.com/support/developer/vc-sdk/visdk41pubs/ApiReference/vim.PerformanceManager.html#field_detail
 */
public class PerfCounterLookup {

    private final List<PerfCounterInfo> counters;

    public PerfCounterLookup(List<PerfCounterInfo> counters) {
        this.counters = counters;
    }

    public PerfCounterInfo getCounter(String name, String groupName, PerfStatsType type,
            PerfSummaryType rollupType,
            PerformanceManagerUnit unit) {
        for (PerfCounterInfo pci : this.counters) {
            if (pci.getNameInfo().getKey().equals(name) &&
                    pci.getGroupInfo().getKey().equals(groupName) &&
                    pci.getRollupType().equals(rollupType) &&
                    pci.getStatsType().equals(type) &&
                    pci.getUnitInfo().getKey().equals(unit.value())) {
                return pci;
            }
        }

        return null;
    }

    public PerfCounterInfo getCounterByKey(int key) {
        for (PerfCounterInfo pci : this.counters) {
            if (pci.getKey() == key) {
                return pci;
            }
        }

        return null;
    }
}
