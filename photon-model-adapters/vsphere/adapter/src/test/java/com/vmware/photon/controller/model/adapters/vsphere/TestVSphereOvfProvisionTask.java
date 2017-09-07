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

package com.vmware.photon.controller.model.adapters.vsphere;

import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.CLONE_STRATEGY;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.PROVISION_TYPE;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.vmware.vim25.VirtualDiskType;

/**
 * This test deploys a CoreOS ova. The location of the OVF must be passed as a vc.ovfUri system
 * property.
 */
public class TestVSphereOvfProvisionTask extends TestVSphereOvfProvisionTaskBase {
    @Test
    public void deployOvf() throws Throwable {
        deployOvf(false);
    }

    @Test
    public void deployOvfWithFullClone() throws Throwable {
        Map<String, String> customProperties = new HashMap<>();
        customProperties.put(CLONE_STRATEGY, InstanceClient.CLONE_STRATEGY_FULL);
        deployOvf(false, false, customProperties);
    }

    @Test
    public void deployOvfWithThickProvision() throws Throwable {
        Map<String, String> customProperties = new HashMap<>();
        customProperties.put(PROVISION_TYPE, VirtualDiskType.THICK.value());
        deployOvf(false, false, customProperties);
    }

    @Test
    public void deployOvfWithThickEagerZeroedProvision() throws Throwable {
        Map<String, String> customProperties = new HashMap<>();
        customProperties.put(PROVISION_TYPE, VirtualDiskType.EAGER_ZEROED_THICK.value());
        deployOvf(false, false, customProperties);
    }
}
