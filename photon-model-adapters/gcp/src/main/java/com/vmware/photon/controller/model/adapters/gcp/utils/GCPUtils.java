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

package com.vmware.photon.controller.model.adapters.gcp.utils;

import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.ASIA_EAST1_A;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.ASIA_EAST1_B;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.ASIA_EAST1_C;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.CENTRAL_US;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.EASTERN_US;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.EAST_ASIA;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.EUROPE_WEST1_B;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.EUROPE_WEST1_C;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.EUROPE_WEST1_D;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.INSTANCE_STATUS_PROVISIONING;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.INSTANCE_STATUS_RUNNING;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.INSTANCE_STATUS_STAGING;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.INSTANCE_STATUS_STOPPING;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.INSTANCE_STATUS_SUSPENDED;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.INSTANCE_STATUS_SUSPENDING;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.INSTANCE_STATUS_TERMINATED;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.PRIVATE_KEY;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.UNKNOWN_REGION;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.US_CENTRAL1_A;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.US_CENTRAL1_B;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.US_CENTRAL1_C;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.US_CENTRAL1_F;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.US_EAST1_B;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.US_EAST1_C;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.US_EAST1_D;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.WESTERN_EUROPE;

import java.io.IOException;
import java.io.StringReader;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;

import com.google.api.client.util.PemReader;
import com.google.api.client.util.PemReader.Section;
import com.google.api.client.util.SecurityUtils;

import com.vmware.photon.controller.model.adapters.gcp.podo.vm.GCPInstance;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;

/**
 * GCP Utility methods.
 */
public class GCPUtils {
    /**
     * The method validates an input string of private key and generate a java PrivateKey
     * object. The method is non-blocking.
     * @param privateKeyPem The private key in string format.
     * @return The private key in java PrivateKey object format.
     * @throws IOException When input key is not valid.
     */
    public static PrivateKey privateKeyFromPkcs8(String privateKeyPem) throws IOException {
        StringReader reader = new StringReader(privateKeyPem);
        Section section = PemReader.readFirstSectionAndClose(reader, PRIVATE_KEY);
        if (section == null) {
            throw new IOException("Invalid PKCS8 data.");
        }
        try {
            byte[] decodedKey = section.getBase64DecodedBytes();
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decodedKey);
            KeyFactory keyFactory = SecurityUtils.getRsaKeyFactory();
            return keyFactory.generatePrivate(keySpec);
        } catch (Exception e) {
            throw new IOException("Unexpected exception reading PKCS data", e);
        }
    }

    /**
     * Extract the actual instance type from default machine type from GCP response.
     * @param instanceType The default machine type from GCP response.
     * @return The actual instance type.
     */
    public static String extractActualInstanceType(String instanceType) {
        String[] split = instanceType.split("/");
        return split[split.length - 1];
    }

    /**
     * Assign power state to a compute state according to the given vm status.
     * @param computeState The compute state to be assigned the power state to.
     * @param vmStatus The vm status used to decide which power state to assign.
     */
    public static void assignPowerState(ComputeState computeState, String vmStatus) {
        switch (vmStatus) {
        case INSTANCE_STATUS_RUNNING:
            computeState.powerState = PowerState.ON;
            break;
        case INSTANCE_STATUS_PROVISIONING:
        case INSTANCE_STATUS_STAGING:
        case INSTANCE_STATUS_STOPPING:
        case INSTANCE_STATUS_SUSPENDED:
        case INSTANCE_STATUS_SUSPENDING:
            computeState.powerState = PowerState.SUSPEND;
            break;
        case INSTANCE_STATUS_TERMINATED:
            computeState.powerState = PowerState.OFF;
            break;
        default:
            computeState.powerState = PowerState.UNKNOWN;
        }
    }

    /**
     * Assign IP address to a compute state according to the given GCP instance.
     * @param computeState The compute state to be assigned the IP address.
     * @param instance The GCP instance which contains the IP address.
     */
    public static void assignIPAddress(ComputeState computeState, GCPInstance instance) {
        if (instance.networkInterfaces != null && !instance.networkInterfaces.isEmpty()) {
            // Only one network interface is supported per instance.
            computeState.address = instance.networkInterfaces.get(0).networkIP;
        }
    }

    /**
     * Return the corresponding region according to given zone.
     * @param zone The given zone name.
     * @return The corresponding region name.
     */
    public static String extractRegionFromZone(String zone) {
        switch (zone) {
        case US_EAST1_B:
        case US_EAST1_C:
        case US_EAST1_D:
            return EASTERN_US;
        case US_CENTRAL1_A:
        case US_CENTRAL1_B:
        case US_CENTRAL1_C:
        case US_CENTRAL1_F:
            return CENTRAL_US;
        case EUROPE_WEST1_B:
        case EUROPE_WEST1_C:
        case EUROPE_WEST1_D:
            return WESTERN_EUROPE;
        case ASIA_EAST1_A:
        case ASIA_EAST1_B:
        case ASIA_EAST1_C:
            return EAST_ASIA;
        default:
            return UNKNOWN_REGION;
        }
    }
}
