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

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.vim25.ManagedObjectReference;

/**
 * Provides a convenience get/put interface over classes that hold customProperties.
 * Does type conversion, default values and creates and empty map and assigns it to the
 * owner on the first call to any put* method.
 */
public class CustomProperties {
    /**
     * Key for the MoRef of a vm provisioned by the adapter.
     */
    public static final String MOREF = "__moref";

    /**
     * When part of computeDescription, causes a vm to be created as a clone of the
     * compute state.
     */
    public static final String TEMPLATE_LINK = "__templateComputeLink";

    /**
     * Reference to the description of the disk using which the disk is created.
     */
    public static final String TEMPLATE_DISK_LINK = "__templateDiskLink";

    /**
     * When the instance needs to be linked clone from a snapshot.
     */
    public static final String SNAPSHOT_LINK = "__snapshotLink";

    /**
     * compute link from which the linkedclone has to be created.
     */
    public static final String REF_ENDPOINT_LINK = "__refComputeLink";

    /**
     * When part of computeDescription, causes a vm to be created from an the
     * library item found at this link.
     */
    public static final String LIBRARY_ITEM_LINK = "__libraryItemLink";

    /**
     * Stores the VIM type of the vSphere entity.
     */
    public static final String TYPE = "__computeType";

    /**
     * How cloning should be performed. Valid values are FULL and LINKED.
     * If not specified LINKED is assumed.
     * https://www.vmware.com/support/developer/vc-sdk/linked_vms_note.pdf
     */
    public static final String CLONE_STRATEGY = "__cloneStrategy";

    /**
     * The guest type, valid values are any constants in {@link com.vmware.vim25.VirtualMachineGuestOsIdentifier}
     */
    public static final String GUEST_ID = "__guestId";

    public static final String IMAGE_LIBRARY_ID = "__libraryId";

    /**
     * Groups that are represent connectivity between a host and a target resource (network, storage, etc)
     * store the target link under this property.
     */
    public static final String TARGET_LINK = "__targetLink";

    // Storage Related constants
    public static final String DISK_MODE_PERSISTENT = "persistent";
    /**
     * Disk mode of the disk to be independent of snapshots or not.
     */
    public static final String DISK_MODE_INDEPENDENT = "independent";

    /**
     * Provisioning type of the disk. See also {@link com.vmware.vim25.VirtualDiskType}
     */
    public static final String PROVISION_TYPE = "provisioningType";

    /**
     * Shares level of the disk. See also {@link com.vmware.vim25.SharesLevel}
     */
    public static final String SHARES_LEVEL = "sharesLevel";

    /**
     * Shares which are defaulted to based on the shares level. If the shares level is
     * {@link com.vmware.vim25.SharesLevel.CUSTOM}, then this value can be customized.
     */
    public static final String SHARES = "shares";

    /**
     * Limit IOPS for the disk.
     */
    public static final String LIMIT_IOPS = "limitIops";

    /**
     * Full path of the disk
     */
    public static final String DISK_FULL_PATH = "__diskFullPath";

    /**
     * Disk parent directory
     */
    public static final String DISK_PARENT_DIRECTORY = "__diskParentDir";

    /**
     * Disk datastore name
     */
    public static final String DISK_DATASTORE_NAME = "__diskDatastoreName";

    /**
     * Disk controller (ex: SCSI) unit number which will be used to identify the disk uniquely
     */
    public static final String DISK_CONTROLLER_NUMBER = "__controllerUnitNumber";

    public static final String PROVIDER_DISK_UNIQUE_ID = "__providerUniqueIdentifier";

    /**
     * Snapshot limit: maximum permissible snapshots for this vSphere compute
     */
    public static final String SNAPSHOT_MAXIMUM_LIMIT = "__snapshotLimit"; // the specified limit of snapshots allowed

    private final Supplier<Map<String, String>> getPropsForRead;
    private final Supplier<Map<String, String>> getPropsForWrite;
    private final Consumer<String> remove;

    public CustomProperties(ResourceState resourceState) {
        if (resourceState == null) {
            throw new IllegalArgumentException("resourceState is required");
        }

        this.getPropsForRead = () -> {
            if (resourceState.customProperties == null) {
                return Collections.emptyMap();
            } else {
                return resourceState.customProperties;
            }
        };

        this.getPropsForWrite = () -> {
            if (resourceState.customProperties == null) {
                resourceState.customProperties = new HashMap<>();
            }

            return resourceState.customProperties;
        };

        this.remove = (String key) -> {
            if (resourceState.customProperties != null) {
                resourceState.customProperties.remove(key);
            }
        };
    }

    public static CustomProperties of(ResourceState snapshot) {
        return new CustomProperties(snapshot);
    }

    public String getString(String key) {
        return getString(key, null);
    }

    public String getString(String key, String defaultValue) {
        String result = this.getPropsForRead.get().get(key);
        if (result == null) {
            return defaultValue;
        } else {
            return result;
        }
    }

    public ManagedObjectReference getMoRef(String key) {
        return VimUtils.convertStringToMoRef(getString(key));
    }

    public URI getUri(String key) {
        String s = getString(key);
        if (s != null) {
            return URI.create(s);
        } else {
            return null;
        }
    }

    public Integer getInt(String key, Integer defaultValue) {
        String s = getString(key);
        if (s == null) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public Long getLong(String key, Long defaultValue) {
        String s = getString(key);
        if (s == null) {
            return defaultValue;
        }

        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public Boolean getBoolean(String key, Boolean defaultValue) {
        String s = getString(key);
        if (s == null) {
            return defaultValue;
        }

        try {
            return Boolean.parseBoolean(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public CustomProperties put(String key, ManagedObjectReference moref) {
        return put(key, VimUtils.convertMoRefToString(moref));
    }

    public CustomProperties put(String key, URI uri) {
        if (uri == null) {
            this.remove.accept(key);
        } else {
            this.getPropsForWrite.get().put(key, uri.toString());
        }

        return this;
    }

    public CustomProperties put(String key, String s) {
        if (s == null) {
            this.remove.accept(key);
        } else {
            this.getPropsForWrite.get().put(key, s);
        }

        return this;
    }

    public CustomProperties put(String key, Integer i) {
        if (i == null) {
            this.remove.accept(key);
        } else {
            put(key, Integer.toString(i));
        }

        return this;
    }

    public CustomProperties put(String key, Long i) {
        if (i == null) {
            this.remove.accept(key);
        } else {
            put(key, Long.toString(i));
        }

        return this;
    }

    public CustomProperties put(String key, Boolean value) {
        if (value == null) {
            this.remove.accept(key);
        } else {
            put(key, value.toString());
        }

        return this;
    }
}
