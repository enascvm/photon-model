/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
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
import static com.vmware.photon.controller.model.adapters.vsphere.ClientUtils.createCdrom;
import static com.vmware.photon.controller.model.adapters.vsphere.ClientUtils.createFloppy;
import static com.vmware.photon.controller.model.adapters.vsphere.ClientUtils.createHdd;
import static com.vmware.photon.controller.model.adapters.vsphere.ClientUtils.fillInControllerUnitNumber;
import static com.vmware.photon.controller.model.adapters.vsphere.ClientUtils.findFreeScsiUnit;
import static com.vmware.photon.controller.model.adapters.vsphere.ClientUtils.findFreeUnit;
import static com.vmware.photon.controller.model.adapters.vsphere.ClientUtils.getDiskControllerUnitNumber;
import static com.vmware.photon.controller.model.adapters.vsphere.ClientUtils.getFirstIdeController;
import static com.vmware.photon.controller.model.adapters.vsphere.ClientUtils.getFirstScsiController;
import static com.vmware.photon.controller.model.adapters.vsphere.ClientUtils.getFirstSioController;
import static com.vmware.photon.controller.model.adapters.vsphere.ClientUtils.getPbmProfileSpec;
import static com.vmware.photon.controller.model.adapters.vsphere.ClientUtils.insertCdrom;
import static com.vmware.photon.controller.model.adapters.vsphere.ClientUtils.insertFloppy;
import static com.vmware.photon.controller.model.adapters.vsphere.ClientUtils.nextUnitNumber;
import static com.vmware.photon.controller.model.adapters.vsphere.ClientUtils.powerOffVM;
import static com.vmware.photon.controller.model.adapters.vsphere.ClientUtils.powerOnVM;
import static com.vmware.photon.controller.model.adapters.vsphere.ClientUtils.updateDiskStateFromVirtualDisk;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.DISK_DATASTORE_NAME;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.DISK_FULL_PATH;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.DISK_PARENT_DIRECTORY;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.INSERT_CDROM;
import static com.vmware.xenon.common.Operation.MEDIA_TYPE_APPLICATION_OCTET_STREAM;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import javax.net.ssl.TrustManager;

import com.vmware.photon.controller.model.adapters.vsphere.util.VimPath;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BaseHelper;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.GetMoRef;
import com.vmware.photon.controller.model.adapters.vsphere.util.finders.Element;
import com.vmware.photon.controller.model.adapters.vsphere.util.finders.Finder;
import com.vmware.photon.controller.model.adapters.vsphere.util.finders.FinderException;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.vim25.ArrayOfDatastoreHostMount;
import com.vmware.vim25.ArrayOfVirtualDevice;
import com.vmware.vim25.DatastoreHostMount;
import com.vmware.vim25.FileFaultFaultMsg;
import com.vmware.vim25.InvalidDatastoreFaultMsg;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.VirtualCdrom;
import com.vmware.vim25.VirtualDevice;
import com.vmware.vim25.VirtualDeviceConfigSpec;
import com.vmware.vim25.VirtualDeviceConfigSpecOperation;
import com.vmware.vim25.VirtualDisk;
import com.vmware.vim25.VirtualFloppy;
import com.vmware.vim25.VirtualMachineConfigSpec;
import com.vmware.vim25.VirtualMachineDefinedProfileSpec;
import com.vmware.vim25.VirtualSCSIController;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.ServiceHost;


/**
 * A simple client for vsphere which handles compute day 2 disk related operation. Consist of a
 * valid connection and some context. This class does blocking IO but doesn't talk back to xenon.
 */
public class InstanceDiskClient extends BaseHelper {
    private final VSphereVMDiskContext context;
    private final DiskService.DiskStateExpanded diskState;
    private final GetMoRef get;
    private final ManagedObjectReference vm;
    private final Finder finder;
    private final ServiceHost host;
    private static final String ISO_UPLOAD_URL =
            "https://%s/folder/%s/%s?dcPath=ha-datacenter&dsName=%s";
    private static final String FULL_PATH = "[%s] %s/%s";
    private static final String PARENT_DIR = "[%s] %s";
    private static final String ISO_EXTENSION = ".iso";
    private static final String ISO_FILE = "isoFile";
    private static final String ISO_FOLDER = "ISOUploadFolder";


    public InstanceDiskClient(Connection connection, VSphereVMDiskContext context,
            ServiceHost host) {
        super(connection);
        this.context = context;
        this.diskState = this.context.diskState;
        this.get = new GetMoRef(this.connection);
        this.vm = VimUtils.convertStringToMoRef(CustomProperties.of(this.context.computeDesc)
                .getString(CustomProperties.MOREF));
        this.finder = new Finder(connection, this.context.datacenterMoRef);
        this.host = host;
    }

    public void attachDiskToVM() throws Exception {
        ArrayOfVirtualDevice devices = this.get
                .entityProp(this.vm, VimPath.vm_config_hardware_device);

        String diskPath = VimUtils.uriToDatastorePath(this.diskState.sourceImageReference);
        String diskFullPath = CustomProperties.of(this.diskState).getString(DISK_FULL_PATH, null);
        Boolean insertCdRom = CustomProperties.of(this.diskState).getBoolean(INSERT_CDROM, false);

        VirtualDeviceConfigSpec deviceConfigSpec = null;
        VirtualSCSIController scsiController = getFirstScsiController(devices);

        // Get available free unit numbers for the given scsi controller.
        Integer[] scsiUnits = findFreeScsiUnit(scsiController, devices.getVirtualDevice());
        if (this.diskState.type == DiskService.DiskType.HDD) {
            List<VirtualMachineDefinedProfileSpec> pbmSpec = getPbmProfileSpec(this.diskState);
            deviceConfigSpec = createHdd(scsiController.getKey(), scsiUnits[0],
                    this.diskState, diskFullPath,
                    this.finder.datastore(CustomProperties.of(this.diskState)
                            .getString(DISK_DATASTORE_NAME)).object, pbmSpec, false);
        } else if (this.diskState.type == DiskService.DiskType.CDROM) {
            if (insertCdRom) {
                if (diskPath == null) {
                    throw new IllegalStateException(
                            String.format("Cannot insert empty iso file into CD-ROM"));
                }
                // Find first available CD ROM to insert the iso file
                VirtualCdrom cdrom = devices.getVirtualDevice().stream()
                        .filter(d -> d instanceof VirtualCdrom)
                        .map(d -> (VirtualCdrom) d).findFirst().orElse(null);
                if (cdrom == null) {
                    throw new IllegalStateException(
                            String.format("Could not find Virtual CD ROM to insert %s.", diskPath));
                }
                insertCdrom(cdrom, diskPath);

                deviceConfigSpec = new VirtualDeviceConfigSpec();
                deviceConfigSpec.setDevice(cdrom);
                deviceConfigSpec.setOperation(VirtualDeviceConfigSpecOperation.EDIT);
            } else {
                VirtualDevice ideController = getFirstIdeController(devices);
                int ideUnit = findFreeUnit(ideController, devices.getVirtualDevice());

                int availableUnitNumber = nextUnitNumber(ideUnit);
                deviceConfigSpec = createCdrom(ideController, availableUnitNumber);
                fillInControllerUnitNumber(this.diskState, availableUnitNumber);

                if (diskPath != null) {
                    // mount iso image
                    insertCdrom((VirtualCdrom) deviceConfigSpec.getDevice(), diskPath);
                }
                // Power off is needed to attach cd-rom
                powerOffVM(this.connection, getVimPort(), this.vm);
            }
        } else if (this.diskState.type == DiskService.DiskType.FLOPPY) {
            VirtualDevice sioController = getFirstSioController(devices);
            int sioUnit = findFreeUnit(sioController, devices.getVirtualDevice());

            int availableUnitNumber = nextUnitNumber(sioUnit);
            deviceConfigSpec = createFloppy(sioController, availableUnitNumber);
            fillInControllerUnitNumber(this.diskState, availableUnitNumber);
            if (diskPath != null) {
                insertFloppy((VirtualFloppy) deviceConfigSpec.getDevice(), diskPath);
            }
            // Power off is needed to attach floppy
            powerOffVM(this.connection, getVimPort(), this.vm);
        }
        VirtualMachineConfigSpec spec = new VirtualMachineConfigSpec();
        spec.getDeviceChange().add(deviceConfigSpec);

        ManagedObjectReference reconfigureTask = getVimPort().reconfigVMTask(this.vm, spec);
        TaskInfo info = VimUtils.waitTaskEnd(this.connection, reconfigureTask);
        if (info.getState() == TaskInfoState.ERROR) {
            VimUtils.rethrow(info.getError());
        }
        // Update the diskState
        if (this.diskState.type == DiskService.DiskType.HDD) {
            this.diskState.sourceImageReference = VimUtils.datastorePathToUri(diskFullPath);
            devices = this.get.entityProp(this.vm, VimPath.vm_config_hardware_device);
            VirtualDisk vd = findMatchingVirtualDisk(devices);
            if (vd != null) {
                updateDiskStateFromVirtualDisk(vd, this.diskState);
            }
        } else {
            // This means it is CDROM or Floppy. Hence power on the VM as it is powered off to
            // perform the operation
            if (!insertCdRom) {
                powerOnVM(this.connection, getVimPort(), this.vm);
            }
            this.diskState.status = DiskService.DiskStatus.ATTACHED;
        }
    }

    public void detachDiskFromVM() throws Exception {
        ArrayOfVirtualDevice devices = this.get
                .entityProp(this.vm, VimPath.vm_config_hardware_device);
        VirtualDisk vd = findMatchingVirtualDisk(devices);
        if (vd == null) {
            throw new IllegalStateException(
                    String.format(
                            "Matching Virtual Disk is not for disk %s.",
                            this.diskState.documentSelfLink));
        }
        // Detach the disk from VM.
        VirtualDeviceConfigSpec deviceConfigSpec = new VirtualDeviceConfigSpec();
        deviceConfigSpec.setOperation(VirtualDeviceConfigSpecOperation.REMOVE);
        deviceConfigSpec.setDevice(vd);

        VirtualMachineConfigSpec spec = new VirtualMachineConfigSpec();
        spec.getDeviceChange().add(deviceConfigSpec);

        ManagedObjectReference reconfigureTask = getVimPort().reconfigVMTask(this.vm, spec);
        TaskInfo info = VimUtils.waitTaskEnd(this.connection, reconfigureTask);
        if (info.getState() == TaskInfoState.ERROR) {
            VimUtils.rethrow(info.getError());
        }
    }

    /**
     * Uploads ISO content into the chosen datastore
     */
    public DeferredResult<DiskService.DiskStateExpanded> uploadISOContents(byte[] contentToUpload)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg, InvalidDatastoreFaultMsg,
            FileFaultFaultMsg, FinderException {
        try {
            // 1) fetch data store for the disk
            String dsName = this.context.datastoreName;
            if (dsName == null || dsName.isEmpty()) {
                dsName = ClientUtils.getDefaultDatastore(this.finder);
            }
            String dataStoreName = dsName;
            List<Element> datastoreList = this.finder.datastoreList(dataStoreName);
            ManagedObjectReference dsFromSp;
            Optional<Element> datastoreOpt = datastoreList.stream().findFirst();
            if (datastoreOpt.isPresent()) {
                dsFromSp = datastoreOpt.get().object;
            } else {
                throw new IllegalArgumentException(
                        String.format("No Datastore [%s] present on datacenter", dataStoreName));
            }

            // 2) Get available hosts for direct upload
            String hostName = null;

            ArrayOfDatastoreHostMount dsHosts = this.get.entityProp(dsFromSp, VimPath.res_host);
            if (dsHosts != null && dsHosts.getDatastoreHostMount() != null) {
                DatastoreHostMount dsHost = dsHosts.getDatastoreHostMount().stream()
                        .filter(hostMount -> hostMount.getMountInfo() != null && hostMount
                                .getMountInfo()
                                .isAccessible() && hostMount.getMountInfo().isMounted())
                        .findFirst().orElse(null);
                if (dsHost != null) {
                    hostName = this.get
                            .entityProp(dsHost.getKey(), VimPath.host_summary_config_name);
                }
            }

            if (hostName == null) {
                throw new IllegalStateException(String.format("No host found to upload ISO content "
                        + "for Data Store Disk %s", dataStoreName));
            }

            // 3) Choose some unique filename
            String filename = ClientUtils.getUniqueName(ISO_FILE) + ISO_EXTENSION;

            // 4 ) Choose some unique folder name and create it.
            String folderName = ClientUtils.getUniqueName(ISO_FOLDER);

            ClientUtils.createFolder(this.connection, this.context.datacenterMoRef,
                    String.format(VM_PATH_FORMAT, dataStoreName, folderName));

            // 5) form the upload url and acquire generic service ticket for it
            String isoUrl = String.format(ISO_UPLOAD_URL, hostName, folderName, filename,
                    dataStoreName);

            String ticket = this.connection.getGenericServiceTicket(isoUrl);

            // 6) create external client that accepts all certificates
            TrustManager[] trustManagers = new TrustManager[] {
                    ClientUtils.getDefaultTrustManager() };

            ServiceClient serviceClient = ClientUtils
                    .getCustomServiceClient(trustManagers, this.host,
                            URI.create(isoUrl), this.getClass().getSimpleName());

            // 7) PUT operation for the iso content

            Operation putISO = Operation.createPut(URI.create(isoUrl));
            putISO.setContentType(MEDIA_TYPE_APPLICATION_OCTET_STREAM)
                    .setContentLength(contentToUpload.length)
                    .addRequestHeader("Cookie", "vmware_cgi_ticket=" + ticket)
                    .setBody(contentToUpload)
                    .setReferer(this.host.getUri());

            return serviceClient.sendWithDeferredResult(putISO)
                    .thenApply(op -> {
                        String diskFullPath = String
                                .format(FULL_PATH, dataStoreName, folderName, filename);
                        // Update the details of the disk
                        CustomProperties.of(this.diskState)
                                .put(DISK_FULL_PATH, diskFullPath)
                                .put(DISK_PARENT_DIRECTORY, String.format(PARENT_DIR,
                                        dataStoreName, folderName))
                                .put(DISK_DATASTORE_NAME, dataStoreName);
                        this.diskState.sourceImageReference = VimUtils
                                .datastorePathToUri(diskFullPath);
                        return this.diskState;
                    });
        } catch (Exception e) {
            return DeferredResult.failed(e);
        }
    }


    /**
     * Find matching VirtualDisk for the given disk information using its controller unit number
     * filled in during creation of the disk.
     */
    private VirtualDisk findMatchingVirtualDisk(ArrayOfVirtualDevice devices) {
        return devices.getVirtualDevice().stream()
                .filter(d -> d instanceof VirtualDisk)
                .map(d -> (VirtualDisk) d)
                .filter(d -> d.getUnitNumber() == getDiskControllerUnitNumber(this.diskState))
                .findFirst()
                .orElse(null);
    }
}
