/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.adapters.registry.operations;

/*
 * Common Day 2 resource operation type enums to be used across all the adapters
 * Each adapter may extend its support for any of these individual operations via ResourceOperationSpecService
 */
public enum ResourceOperation {

    RESTART("Restart", "Restart", "Restart Compute Resource"),

    REBOOT("Reboot", "Reboot", "Reboot Compute Resource"),

    SUSPEND("Suspend", "Suspend", "Suspend Compute Resource"),

    SHUTDOWN("Shutdown", "Shutdown", "Shutdown guest OS of Compute Resource"),

    RESET("Reset", "Reset", "Reset Compute Resource");

    public final String operation;

    public final String displayName;

    public final String description;

    private ResourceOperation(String operation, String displayName, String description) {
        this.operation = operation;
        this.displayName = displayName;
        this.description = description;
    }
}
