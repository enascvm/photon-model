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

package com.vmware.photon.controller.model.adapters.awsadapter;

/**
 * AWS stages.
 */
public enum AWSInstanceStage {

    PROVISIONTASK,
    CLIENT,
    DELETE,
    CREATE,
    VM_DISKS,
    GET_NIC_STATES,
    GET_NETWORKS,
    GET_SUBNETS,
    GET_FIREWALLS,
    CREATE_PUBLIC_IPS,
    CREATE_INSTANCE_NICs,
    DONE,
    ERROR

}
