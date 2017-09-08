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

package com.vmware.photon.controller.model.adapterapi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.ServiceStats.ServiceStat;

/**
 * Defines the response body for getting health status of a Compute instance.
 */
public class ComputeStatsResponse {

    public static final int CUSTOM_PROPERTIES_LIMIT = Integer.getInteger(
            UriPaths.PROPERTY_PREFIX + "ComputeStatsResponse.customProperties.maxLimit", 1);

    /**
     * List of stats
     */
    public List<ComputeStats> statsList;

    /**
     * Task stage to patch back
     */
    public Object taskStage;

    public static class ComputeStats {
        /**
         *  link of the compute resource the stats belongs to
         */
        public String computeLink;

        /**
         * Stats values are of type ServiceStat
         */
        public Map<String, List<ServiceStat>> statValues;

        /**
         * These custom properties will be added to the custom properties of each resource metric
         * document created
         */
        private Map<String, String> customProperties;

        public void addCustomProperty(String key, String value) {
            if (this.customProperties == null) {
                this.customProperties = new HashMap<>();
            }
            this.customProperties.put(key, value);
            if (this.customProperties.size() > CUSTOM_PROPERTIES_LIMIT) {
                throw new IllegalStateException("ComputeStats can't have custom properties more than " +
                        "permitted limit of " + CUSTOM_PROPERTIES_LIMIT);
            }
        }

        public Map<String, String> getCustomProperties() {
            return this.customProperties;
        }
    }
}
