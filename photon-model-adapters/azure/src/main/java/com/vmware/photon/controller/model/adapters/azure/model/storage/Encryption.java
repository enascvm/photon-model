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

package com.vmware.photon.controller.model.adapters.azure.model.storage;

/**
 * Encryption is a complex object that is part of the azure api response for storage account
 * We need to find if encryption is enabled at the blob level so we need to check that in the
 * EncryptionServices. Blob is one of the 4 EncryptionService. Object outline below.
 *
 * <pre>
 * Encryption
 *  -EncryptionServices
 *      -blob
 *          -enabled (value we need to get hold of to know if disk is encrypted)
 *          -lastEnabledTime
 *      -file
 *      -queue
 *      -table
 * </pre>
 * please refer to the following URL for api definition
 * https://docs.microsoft.com/en-us/rest/api/storagerp/storageaccounts#StorageAccounts_List
 */
public class Encryption {

    public EncryptionServices services;

}
