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

package com.vmware.photon.controller.model.adapters.azure.instance;

/**
 * Azure stages required to create/delete a VM.
 */
public enum AzureInstanceStage {

    /**
     * This stage gets the vm description.
     */
    VMDESC,

    /**
     * This stage gets the parent description.
     */
    PARENTDESC,

    /**
     * This stage gets the authentication information from the parent.
     */
    PARENTAUTH,

    /**
     * This stage gets the authentication information from the child.
     */
    CHILDAUTH,

    /**
     * Stage where VM creation happens.
     */
    CREATE,

    /**
     * Stage where VM deletion happens.
     */
    DELETE,

    /**
     * Stage to get VM disk information.
     */
    VMDISKS,

    /**
     * Differentiate between a Windows VM and a Linux VM.
     */
    GET_IMAGE,

    /**
     * Stage where resource group is initialized.
     */
    INIT_RES_GROUP,

    /**
     * Stage where storage account is initialized.
     */
    INIT_STORAGE,

    /**
     * Stage where network interface context is initialized.
     * This includes retrieving of subnet, security group, etc. objects from Azure.
     */
    POPULATE_NIC_CONTEXT,

    /**
     * Stage where networks (vNet-subnet pair) are created.
     */
    CREATE_NETWORKS,

    /**
     * Stage where public IPs are created.
     */
    CREATE_PUBLIC_IPS,

    /**
     * Stage where security groups are created.
     */
    CREATE_SECURITY_GROUPS,

    /**
     * Stage where NICs are created.
     */
    CREATE_NICS,

    /**
     * Generate and set ComputeState ip.
     */
    GENERATE_VM_ID,

    /**
     * Stage to initialize Azure client.
     */
    CLIENT,

    /**
     * Stage to handle errors.
     */
    ERROR,

    /**
     * Stage to get public IP address.
     */
    UPDATE_NETWORK_DETAILS,

    /**
     * Stage to get storage account keys.
     */
    GET_STORAGE_KEYS,

    /**
     * The finished stage.
     */
    FINISHED,

    /**
     * Enable monitoring on a VM.
     */
    ENABLE_MONITORING

}
