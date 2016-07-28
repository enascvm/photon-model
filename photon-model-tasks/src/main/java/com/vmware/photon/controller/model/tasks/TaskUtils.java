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

package com.vmware.photon.controller.model.tasks;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Collection;

import com.vmware.photon.controller.model.UriPaths.AdapterTypePath;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.TaskService.TaskServiceState;

/**
 * Utility functions used in provisioning hosts.
 */
public class TaskUtils {

    /**
     * Verify if IP string is an IPv4 address.
     *
     * @param IP IP to verify
     * @throws IllegalArgumentException
     */
    public static void isValidInetAddress(String IP) throws IllegalArgumentException {

        // Opened issue #84 to track proper validation
        if (IP == null || IP.isEmpty()) {
            throw new IllegalArgumentException("IP is missing or empty");
        }

        if (IP.contains(":")) {
            // implement IPv6 validation
        } else {
            String[] segments = IP.split("\\.");
            if (segments.length != 4) {
                throw new IllegalArgumentException("IP does not appear valid:" + IP);
            }
            // it appears to be literal IP, its safe to use the getByName method
            try {
                InetAddress.getByName(IP);
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    /*
     * method takes a string that can either be a subnet or
     * ip address and ensures that it falls in the range of
     * RFC-1918 addresses
     */
    public static void isRFC1918(String subnetAddress) throws IllegalArgumentException {
        String address = null;
        InetAddress ipAddress;

        if (subnetAddress == null || subnetAddress.isEmpty()) {
            throw new IllegalArgumentException("IP or subnet is missing or empty");
        }

        if (subnetAddress.contains("/")) {
            String[] netAddr = subnetAddress.split("/");
            address = netAddr[0];
        }

        // validate the IP to start...
        isValidInetAddress(address);

        try {
            ipAddress = InetAddress.getByName(address);
        } catch (Throwable t) {
            throw new IllegalArgumentException(t.getMessage());
        }

        if (!ipAddress.isSiteLocalAddress()) {
            throw new IllegalArgumentException("must be an RFC-1918 address or CIDR");
        }
    }

    /**
     * Translate a MAC to its canonical format.
     *
     * @param mac
     * @throws java.lang.IllegalArgumentException
     */
    public static String normalizeMac(String mac) throws IllegalArgumentException {
        mac = mac.replaceAll("[:-]", "");
        mac = mac.toLowerCase();
        return mac;
    }

    /**
     * Verify if CIDR string is a valid CIDR address.
     *
     * @param network CIDR to verify
     * @throws IllegalArgumentException
     */
    public static void isCIDR(String network) throws IllegalArgumentException {
        String[] hostMask = network.split("/");
        if (hostMask.length != 2) {
            throw new IllegalArgumentException("subnetAddress is not a CIDR");
        }

        isValidInetAddress(hostMask[0]);

        // Mask must be < 32
        if (Integer.parseUnsignedInt(hostMask[1]) > 32) {
            throw new IllegalArgumentException("CIDR mask may not be larger than 32");
        }
    }

    /**
     * Issue a patch request to the specified service
     * @param service Service to issue the patch to
     * @param body Patch body
     */
    public static void sendPatch(StatefulService service, Object body) {
        Operation patch = Operation
                .createPatch(service.getUri())
                .setBody(body);
        service.sendRequest(patch);
    }

    /**
     * Patch a service to failure after logging all errors
     * @param service Service to patch
     * @param tList List of throwable objects
     */
    public static void sendFailurePatch(StatefulService service, TaskServiceState taskState, Collection<Throwable> tList) {
        Throwable errorToPatch = null;
        for (Throwable t: tList) {
            errorToPatch = t;
            service.logWarning("Operation failed: %s", Utils.toString(t));
        }
        sendFailurePatch(service, taskState, errorToPatch);
    }

    /**
     * Patch a service to failure
     * @param service Service to patch
     * @param t Throwable object
     */
    public static void sendFailurePatch(StatefulService service, TaskServiceState taskState, Throwable t) {
        TaskState state = new TaskState();
        state.stage = TaskStage.FAILED;
        state.failure = Utils.toServiceErrorResponse(t);
        service.logWarning("Operation failed: %s", Utils.toString(t));
        taskState.taskInfo = state;
        sendPatch(service, taskState);
    }

    /**
     * Create a TaskState object with the specified stage
     * @param stage Stage for the TaskState object
     * @return
     */
    public static TaskState createTaskState(TaskStage stage) {
        TaskState tState = new TaskState();
        tState.stage = stage;
        return tState;
    }

    public static URI getAdapterUri(StatefulService service, AdapterTypePath adapterTypePath,
            String endpointType) {
        return UriUtils.buildUri(service.getHost(), adapterTypePath.adapterLink(endpointType));
    }
}
