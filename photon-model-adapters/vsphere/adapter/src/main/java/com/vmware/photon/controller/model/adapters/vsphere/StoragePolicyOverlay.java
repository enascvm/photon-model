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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vmware.pbm.PbmCapabilityDiscreteSet;
import com.vmware.pbm.PbmCapabilityProfile;
import com.vmware.pbm.PbmCapabilitySubProfileConstraints;
import com.vmware.pbm.PbmProfile;
import com.vmware.xenon.common.Utils;

/**
 * Storage Policy information
 */
public class StoragePolicyOverlay {

    private PbmCapabilityProfile pbmProfile;
    private Map<String, String> capabilities = new HashMap<>();
    private Map<String, String> tags = new HashMap<>();
    private List<String> dataStoreNames;

    protected StoragePolicyOverlay(PbmProfile pbmProfile, List<String> dataStoreNames) {
        this.pbmProfile = (PbmCapabilityProfile) pbmProfile;
        this.dataStoreNames = dataStoreNames;
        populateConstraints();
    }

    public String getName() {
        return this.pbmProfile.getName();
    }

    public String getDescription() {
        return this.pbmProfile.getDescription();
    }

    public String getProfileId() {
        return this.pbmProfile.getProfileId().getUniqueId();
    }

    public String getType() {
        return this.pbmProfile.getResourceType().getResourceType();
    }

    public Map<String, String> getCapabilities() {
        return this.capabilities;
    }

    public Map<String, String> getTags() {
        return this.tags;
    }

    public List<String> getDataStoreNames() {
        return this.dataStoreNames;
    }

    private void populateConstraints() {
        if (this.pbmProfile.getConstraints() != null && this.pbmProfile.getConstraints()
                instanceof PbmCapabilitySubProfileConstraints) {
            PbmCapabilitySubProfileConstraints constraints =
                    (PbmCapabilitySubProfileConstraints) this.pbmProfile.getConstraints();
            constraints.getSubProfiles().stream().forEach(ruleSet -> {
                ruleSet.getCapability().stream().forEach(capability -> {
                    capability.getConstraint().stream().forEach(rule -> {
                        rule.getPropertyInstance().stream().forEach(prop -> {
                            if (capability.getId().getNamespace().contains("tag")) {
                                PbmCapabilityDiscreteSet tagSet =
                                        (PbmCapabilityDiscreteSet) prop.getValue();
                                tagSet.getValues().stream().forEach(tag -> {
                                    this.tags.put(capability.getId().getId(),
                                            String.valueOf(tag));
                                });
                            } else {
                                this.capabilities.put(capability.getId().getId(),
                                        Utils.toJson(prop.getValue()));
                            }
                        });
                    });
                });
            });
        }
    }
}
