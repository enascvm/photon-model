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

import static com.vmware.photon.controller.model.adapters.vsphere.ClientUtils.VM_PATH_FORMAT;
import static com.vmware.photon.controller.model.adapters.vsphere.ClientUtils.getDiskProvisioningType;
import static com.vmware.photon.controller.model.adapters.vsphere.ClientUtils.getPbmProfileSpec;
import static com.vmware.photon.controller.model.adapters.vsphere.ClientUtils.toKb;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.DISK_DATASTORE_NAME;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.DISK_FULL_PATH;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.DISK_PARENT_DIRECTORY;

import java.nio.file.Paths;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BaseHelper;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.adapters.vsphere.util.finders.Finder;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskStateExpanded;
import com.vmware.vim25.FileBackedVirtualDiskSpec;
import com.vmware.vim25.FileFaultFaultMsg;
import com.vmware.vim25.InvalidCollectorVersionFaultMsg;
import com.vmware.vim25.InvalidDatastoreFaultMsg;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.VirtualDiskAdapterType;
import com.vmware.vim25.VirtualDiskType;
import com.vmware.vim25.VirtualMachineDefinedProfileSpec;
import com.vmware.xenon.common.Utils;

/**
 * A simple client for vsphere which handles disk related operation. Consist of a valid connection
 * and some context. This class does blocking IO but doesn't talk back to xenon.
 */
public class DiskClient extends BaseHelper {

    private static final long SINCE_TIME = new GregorianCalendar(2016, Calendar.JANUARY, 1)
            .getTime().getTime();
    public static final String TOKEN_DELIMITER = "-";
    private final DiskContext diskContext;
    private final Finder finder;
    private final DiskService.DiskStateExpanded diskState;

    public DiskClient(Connection connection, DiskContext context) {
        super(connection);
        this.diskContext = context;
        this.finder = new Finder(connection, this.diskContext.datacenterMoRef);
        this.diskState = this.diskContext.diskState;
    }

    /**
     * Create Virtual Disk
     */
    public void createVirtualDisk() throws Exception {
        ManagedObjectReference diskManager = this.connection.getServiceContent()
                .getVirtualDiskManager();
        List<VirtualMachineDefinedProfileSpec> pbmSpec = getPbmProfileSpec(this.diskState);

        String dsName = this.diskContext.datastoreName != null && !this.diskContext.datastoreName
                .isEmpty() ? this.diskContext.datastoreName : ClientUtils.getDefaultDatastore(this.finder);
        String diskName = getUniqueDiskName();

        // Create the parent folder before creation of the disk file
        String parentDir = String.format(VM_PATH_FORMAT, dsName, diskName);
        createParentFolder(parentDir);

        String diskFullPath = constructDiskFullPath(dsName, diskName);
        ManagedObjectReference createTask = getVimPort().createVirtualDiskTask(diskManager,
                diskFullPath, this.diskContext.datacenterMoRef,
                createVirtualDiskSpec(this.diskState, pbmSpec));
        TaskInfo info = waitTaskEnd(createTask);

        if (info.getState() == TaskInfoState.ERROR) {
            VimUtils.rethrow(info.getError());
        }

        // Update the details of the disk
        CustomProperties.of(this.diskState)
                .put(DISK_FULL_PATH, diskFullPath)
                .put(DISK_PARENT_DIRECTORY, parentDir)
                .put(DISK_DATASTORE_NAME, dsName);
        this.diskState.status = DiskService.DiskStatus.AVAILABLE;
        this.diskState.id = diskName;
    }

    /**
     * Delete Virtual disk.
     */
    public void deleteVirtualDisk()
            throws Exception {
        ManagedObjectReference diskManager = this.connection.getServiceContent()
                .getVirtualDiskManager();

        if (this.diskState.status != DiskService.DiskStatus.AVAILABLE) {
            throw new IllegalArgumentException("Only disk with status AVAILABLE can be deleted, as it is not attached to any VM.");
        }

        String diskFullPath = CustomProperties.of(this.diskState).getString(DISK_FULL_PATH, null);
        if (diskFullPath == null) {
            throw new IllegalArgumentException("Disk full path to issue delete request is empty.");
        }

        // Delete the vmdk file
        ManagedObjectReference deleteTask = getVimPort().deleteVirtualDiskTask(diskManager,
                diskFullPath, this.diskContext.datacenterMoRef);
        TaskInfo info = waitTaskEnd(deleteTask);

        if (info.getState() == TaskInfoState.ERROR) {
            VimUtils.rethrow(info.getError());
        }

        // Delete the folder that holds the vmdk file
        String dirName = CustomProperties.of(this.diskState).getString(DISK_PARENT_DIRECTORY, null);
        if (dirName != null) {
            ManagedObjectReference fileManager = this.connection.getServiceContent()
                    .getFileManager();
            ManagedObjectReference deleteFile = getVimPort().deleteDatastoreFileTask(fileManager, dirName, this.diskContext
                    .datacenterMoRef);
            info = waitTaskEnd(deleteFile);
            if (info.getState() == TaskInfoState.ERROR) {
                VimUtils.rethrow(info.getError());
            }
        } else {
            Utils.logWarning("Disk parent directory is null, hence couldn't cleanup disk directory in the datastore");
        }
    }

    /**
     * Wait for the server initiated task to complete.
     */
    private TaskInfo waitTaskEnd(ManagedObjectReference task)
            throws InvalidCollectorVersionFaultMsg, InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        return VimUtils.waitTaskEnd(this.connection, task);
    }

    /**
     * Create virtual disk spec for creating the virtual disk
     */
    private FileBackedVirtualDiskSpec createVirtualDiskSpec(DiskStateExpanded diskStateExpanded,
            List<VirtualMachineDefinedProfileSpec> pbmSpec) {
        if (diskStateExpanded.capacityMBytes <= 0) {
            throw new IllegalArgumentException("Capacity of disk should be greater than 0");
        }

        if (diskStateExpanded.name == null || diskStateExpanded.name.isEmpty()) {
            throw new IllegalArgumentException("Disk name should not be empty");
        }

        FileBackedVirtualDiskSpec diskSpec = new FileBackedVirtualDiskSpec();
        diskSpec.setCapacityKb(toKb(diskStateExpanded.capacityMBytes));
        VirtualDiskType provisionType = getDiskProvisioningType(diskStateExpanded);
        if (provisionType != null) {
            diskSpec.setDiskType(provisionType.value());
        } else {
            // Default it to THIN
            diskSpec.setDiskType(VirtualDiskType.THIN.value());
        }
        diskSpec.setAdapterType(VirtualDiskAdapterType.LSI_LOGIC.value());

        if (pbmSpec != null) {
            diskSpec.getProfile().addAll(pbmSpec);
        }

        return diskSpec;
    }

    /**
     * Get a unique name for the disk by appending the timestamp.
     */
    private String getUniqueDiskName() {
        // Construct current time milli
        long timestamp = System.currentTimeMillis() - SINCE_TIME;
        String diskName = this.diskState.name + TOKEN_DELIMITER + timestamp;

        return diskName;
    }

    /**
     * Construct disk full path ex: [dsName] diskName-timestamp\diskName-timestamp.vmdk
     */
    private String constructDiskFullPath(String datastoreName, String diskName) {
        // Construct the full path of the file
        String diskFilePath = Paths.get(diskName, diskName).toString();
        diskFilePath = String.format(VM_PATH_FORMAT, datastoreName, diskFilePath);
        diskFilePath += ".vmdk";

        return diskFilePath;
    }

    /**
     * Create parent directory for the disk file to be created.
     */
    private void createParentFolder(String path)
            throws FileFaultFaultMsg, InvalidDatastoreFaultMsg, RuntimeFaultFaultMsg {
        ManagedObjectReference fileManager = this.connection.getServiceContent().getFileManager();
        getVimPort().makeDirectory(fileManager, path, this.diskContext.datacenterMoRef,
                true);
    }
}
