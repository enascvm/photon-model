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

import static com.vmware.photon.controller.model.ComputeProperties.RESOURCE_GROUP_NAME;

import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.photon.controller.model.adapters.vsphere.ProvisionContext.NetworkInterfaceStateWithDetails;
import com.vmware.photon.controller.model.adapters.vsphere.ovf.OvfDeployer;
import com.vmware.photon.controller.model.adapters.vsphere.ovf.OvfParser;
import com.vmware.photon.controller.model.adapters.vsphere.ovf.OvfRetriever;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimPath;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BaseHelper;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.GetMoRef;
import com.vmware.photon.controller.model.adapters.vsphere.util.finders.Finder;
import com.vmware.photon.controller.model.adapters.vsphere.util.finders.FinderException;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskState.BootConfig.FileEntry;
import com.vmware.photon.controller.model.resources.DiskService.DiskStatus;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.vim25.ArrayOfManagedObjectReference;
import com.vmware.vim25.ArrayOfVAppPropertyInfo;
import com.vmware.vim25.ArrayOfVirtualDevice;
import com.vmware.vim25.ArrayUpdateOperation;
import com.vmware.vim25.FileAlreadyExists;
import com.vmware.vim25.InvalidCollectorVersionFaultMsg;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.KeyValue;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.MethodFault;
import com.vmware.vim25.OvfNetworkMapping;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.VAppPropertyInfo;
import com.vmware.vim25.VAppPropertySpec;
import com.vmware.vim25.VirtualCdrom;
import com.vmware.vim25.VirtualCdromAtapiBackingInfo;
import com.vmware.vim25.VirtualCdromIsoBackingInfo;
import com.vmware.vim25.VirtualDevice;
import com.vmware.vim25.VirtualDeviceConfigSpec;
import com.vmware.vim25.VirtualDeviceConfigSpecFileOperation;
import com.vmware.vim25.VirtualDeviceConfigSpecOperation;
import com.vmware.vim25.VirtualDeviceConnectInfo;
import com.vmware.vim25.VirtualDisk;
import com.vmware.vim25.VirtualDiskFlatVer2BackingInfo;
import com.vmware.vim25.VirtualDiskMode;
import com.vmware.vim25.VirtualDiskSpec;
import com.vmware.vim25.VirtualE1000;
import com.vmware.vim25.VirtualEthernetCard;
import com.vmware.vim25.VirtualEthernetCardMacType;
import com.vmware.vim25.VirtualEthernetCardNetworkBackingInfo;
import com.vmware.vim25.VirtualFloppy;
import com.vmware.vim25.VirtualFloppyDeviceBackingInfo;
import com.vmware.vim25.VirtualFloppyImageBackingInfo;
import com.vmware.vim25.VirtualIDEController;
import com.vmware.vim25.VirtualLsiLogicController;
import com.vmware.vim25.VirtualMachineCloneSpec;
import com.vmware.vim25.VirtualMachineConfigSpec;
import com.vmware.vim25.VirtualMachineFileInfo;
import com.vmware.vim25.VirtualMachineGuestOsIdentifier;
import com.vmware.vim25.VirtualMachineRelocateSpec;
import com.vmware.vim25.VirtualSCSIController;
import com.vmware.vim25.VirtualSCSISharing;
import com.vmware.vim25.VirtualSIOController;
import com.vmware.vim25.VmConfigSpec;
import com.vmware.xenon.common.Utils;

/**
 * A simple client for vsphere. Consist of a valid connection and some context.
 * This class does blocking IO but doesn't talk back to xenon.
 * A client operates in the context of a datacenter. If the datacenter cannot be determined at
 * construction time a ClientException is thrown.
 */
public class InstanceClient extends BaseHelper {
    private static final Logger logger = LoggerFactory.getLogger(InstanceClient.class.getName());

    private static final String CLOUD_CONFIG_PROPERTY_USER_DATA = "user-data";
    private static final String COREOS_CLOUD_CONFIG_PROPERTY_USER_DATA = "guestinfo.coreos.config.data";
    private static final String COREOS_CLOUD_CONFIG_PROPERTY_USER_DATA_ENCODING = "guestinfo.coreos.config.data.encoding";
    private static final String CLOUD_CONFIG_BASE64_ENCODING = "base64";

    private static final String CLOUD_CONFIG_PROPERTY_HOSTNAME = "hostname";
    private static final String COREOS_CLOUD_CONFIG_PROPERTY_HOSTNAME = "guestinfo.guestinfo.hostname";

    private static final String CLOUD_CONFIG_PROPERTY_PUBLIC_KEYS = "public-keys";
    private static final String OVF_PROPERTY_ENV = "ovf-env";

    private final ComputeStateWithDescription state;
    private final ComputeStateWithDescription parent;
    private final List<DiskState> disks;
    private final List<NetworkInterfaceStateWithDetails> nics;
    private final ManagedObjectReference parentComputeResource;

    private final GetMoRef get;
    private final Finder finder;
    private ManagedObjectReference vm;
    private ManagedObjectReference datastore;
    private ManagedObjectReference resourcePool;
    private ManagedObjectReference host;

    public InstanceClient(Connection connection,
            ComputeStateWithDescription resource,
            ComputeStateWithDescription parent,
            List<DiskState> disks,
            List<NetworkInterfaceStateWithDetails> nics,
            ManagedObjectReference parentComputeResource)
            throws ClientException, FinderException {
        super(connection);

        this.state = resource;
        this.parent = parent;
        this.disks = disks;
        this.nics = nics;
        this.parentComputeResource = parentComputeResource;

        // the regionId is used as a ref to a vSphere datacenter name
        String id = resource.description.regionId;

        try {
            this.finder = new Finder(connection, id);
        } catch (RuntimeFaultFaultMsg | InvalidPropertyFaultMsg e) {
            throw new ClientException(
                    String.format("Error looking for datacenter for id '%s'", id), e);
        }

        this.get = new GetMoRef(this.connection);
    }

    public ComputeState createInstanceFromTemplate(ManagedObjectReference template) throws Exception {
        ManagedObjectReference vm = cloneVm(template);

        VirtualMachineConfigSpec spec = new VirtualMachineConfigSpec();

        // even though this is a clone, hw config from the compute resource
        // is takes precedence
        spec.setNumCPUs((int) this.state.description.cpuCount);
        spec.setGuestId(VirtualMachineGuestOsIdentifier.OTHER_GUEST_64.value());
        spec.setMemoryMB(toMb(this.state.description.totalMemoryBytes));

        // set ovf environment
        ArrayOfVAppPropertyInfo infos = this.get.entityProp(vm, VimPath.vm_config_vAppConfig_property);
        populateCloudConfig(spec, infos);

        ManagedObjectReference task = getVimPort().reconfigVMTask(vm, spec);
        TaskInfo info = waitTaskEnd(task);

        if (info.getState() == TaskInfoState.ERROR) {
            return VimUtils.rethrow(info.getError());
        }

        if (vm == null) {
            // vm was created by someone else
            return null;
        }

        // store reference to created vm for further processing
        this.vm = vm;

        ComputeState state = new ComputeState();
        state.resourcePoolLink = VimUtils
                .firstNonNull(this.state.resourcePoolLink, this.parent.resourcePoolLink);

        return state;
    }

    private ManagedObjectReference cloneVm(ManagedObjectReference template) throws Exception {
        ManagedObjectReference folder = getVmFolder();
        ManagedObjectReference datastore = getDatastore();
        ManagedObjectReference resourcePool = getResourcePool();

        VirtualMachineRelocateSpec relocSpec = new VirtualMachineRelocateSpec();
        relocSpec.setDatastore(datastore);
        relocSpec.setFolder(folder);
        relocSpec.setPool(resourcePool);

        VirtualMachineCloneSpec cloneSpec = new VirtualMachineCloneSpec();
        cloneSpec.setLocation(relocSpec);
        cloneSpec.setPowerOn(false);
        cloneSpec.setTemplate(false);

        String displayName = this.state.name;

        ManagedObjectReference cloneTask = getVimPort()
                .cloneVMTask(template, folder, displayName, cloneSpec);

        TaskInfo info = waitTaskEnd(cloneTask);

        if (info.getState() == TaskInfoState.ERROR) {
            MethodFault fault = info.getError().getFault();
            if (fault instanceof FileAlreadyExists) {
                // a .vmx file already exists, assume someone won the race to create the vm
                return null;
            } else {
                return VimUtils.rethrow(info.getError());
            }
        }

        return (ManagedObjectReference) info.getResult();
    }

    public void deleteInstance() throws Exception {
        ManagedObjectReference vm = CustomProperties.of(this.state)
                .getMoRef(CustomProperties.MOREF);

        TaskInfo info;
        // power off
        ManagedObjectReference task = getVimPort().powerOffVMTask(vm);
        info = waitTaskEnd(task);
        ignoreError("Ignore error powering off VM", info);

        // delete vm
        task = getVimPort().destroyTask(vm);
        info = waitTaskEnd(task);
        ignoreError("Ignore error deleting VM", info);
    }

    private void ignoreError(String s, TaskInfo info) {
        if (info.getState() == TaskInfoState.ERROR) {
            logger.info(s + ": " + info.getError().getLocalizedMessage());
        }
    }

    /**
     * Does provisioning and return a patchable state to patch the resource.
     *
     * @return
     */
    public ComputeState createInstance() throws Exception {
        ManagedObjectReference vm;

        if (isOvfDeploy()) {
            vm = deployOvf();
            this.vm = vm;
        } else {
            vm = createVm();

            if (vm == null) {
                // vm was created by someone else
                return null;
            }

            // store reference to created vm for further processing
            this.vm = vm;
        }

        ComputeState state = new ComputeState();
        state.resourcePoolLink = VimUtils
                .firstNonNull(this.state.resourcePoolLink, this.parent.resourcePoolLink);

        return state;
    }

    private boolean isOvfDeploy() {
        CustomProperties cp = CustomProperties.of(this.state.description);
        return cp.getString(OvfParser.PROP_OVF_URI) != null ||
                cp.getString(OvfParser.PROP_OVF_ARCHIVE_URI) != null;
    }

    @SuppressWarnings("unchecked")
    private ManagedObjectReference deployOvf() throws Exception {
        OvfDeployer deployer = new OvfDeployer(this.connection);
        CustomProperties cust = CustomProperties.of(this.state.description);

        URI ovfUri = cust.getUri(OvfParser.PROP_OVF_URI);

        URI archiveUri = cust.getUri(OvfParser.PROP_OVF_ARCHIVE_URI);
        if (archiveUri != null) {
            logger.info("Prefer ova {} uri to ovf {}", archiveUri, ovfUri);
            OvfRetriever retriever = deployer.getRetriever();
            ovfUri = retriever.downloadIfOva(archiveUri);
        }

        ManagedObjectReference host = null;
        ManagedObjectReference folder = getVmFolder();
        List<OvfNetworkMapping> network = Collections.emptyList();
        ManagedObjectReference ds = getDatastore();
        ManagedObjectReference resourcePool = getResourcePool();

        Map<String, KeyValue> props = new HashMap<>();

        DiskState bootDisk = findBootDisk();
        String ovfEnv = getFileItemByPath(bootDisk, OVF_PROPERTY_ENV);
        if (ovfEnv != null) {
            Map<String, String> map = Utils.fromJson(ovfEnv, Map.class);
            for (Entry<String, String> entry : map.entrySet()) {
                KeyValue kv = new KeyValue();
                kv.setKey(entry.getKey());
                kv.setValue(entry.getValue());
                props.put(kv.getKey(), kv);
            }
        }
        mergeCloudConfigPropsIntoOvfEnv(props, bootDisk);

        String config = cust.getString(OvfParser.PROP_OVF_CONFIGURATION);

        String vmName = this.state.name;

        ManagedObjectReference vm = deployer
                .deployOvf(ovfUri, host, folder, vmName, network, ds, props.values(), config, resourcePool);

        // Sometimes ComputeDescriptions created from an OVF can be modified. For such
        // cases one more reconfiguration is needed to set the cpu/mem correctly.
        reconfigure(vm);

        return vm;
    }

    /**
     * Converts all cloud-config properties from the bootDisk to OVF properties. There are two implementations
     * of cloud config currently, the second being from CoreOS. As a result a single cloud-config prop can be added
     * under different keys.
     * @param props
     * @param bootDisk
     */
    private void mergeCloudConfigPropsIntoOvfEnv(Map<String, KeyValue> props, DiskState bootDisk) {
        String userData = getFileItemByPath(bootDisk, CLOUD_CONFIG_PROPERTY_USER_DATA);
        if (userData != null) {
            String encoded = Base64.getEncoder().encodeToString(userData.getBytes());
            KeyValue kv = new KeyValue();
            kv.setKey(CLOUD_CONFIG_PROPERTY_USER_DATA);
            kv.setValue(encoded);
            props.put(kv.getKey(), kv);

            // CoreOs specific ovf keys
            kv = new KeyValue();
            kv.setKey(COREOS_CLOUD_CONFIG_PROPERTY_USER_DATA);
            kv.setValue(encoded);
            props.put(kv.getKey(), kv);

            kv = new KeyValue();
            kv.setKey(COREOS_CLOUD_CONFIG_PROPERTY_USER_DATA_ENCODING);
            kv.setValue(CLOUD_CONFIG_BASE64_ENCODING);
            props.put(kv.getKey(), kv);
        }

        String sshKeys = getFileItemByPath(bootDisk, CLOUD_CONFIG_PROPERTY_PUBLIC_KEYS);
        if (sshKeys != null) {
            KeyValue kv = new KeyValue();
            kv.setKey(CLOUD_CONFIG_PROPERTY_PUBLIC_KEYS);
            kv.setValue(sshKeys);
            props.put(kv.getKey(), kv);
        }

        String hostname = getFileItemByPath(bootDisk, CLOUD_CONFIG_PROPERTY_HOSTNAME);
        if (hostname != null) {
            KeyValue kv = new KeyValue();
            kv.setKey(CLOUD_CONFIG_PROPERTY_HOSTNAME);
            kv.setValue(hostname);
            props.put(kv.getKey(), kv);

            kv = new KeyValue();
            kv.setKey(COREOS_CLOUD_CONFIG_PROPERTY_HOSTNAME);
            kv.setValue(hostname);
            props.put(kv.getKey(), kv);
        }
    }

    /**
     * The first HDD disk is considered the boot disk.
     * @return
     */
    private DiskState findBootDisk() {
        if (this.disks == null) {
            return null;
        }

        return this.disks.stream()
                .filter(d -> d.type == DiskType.HDD)
                .findFirst()
                .orElse(null);
    }

    /**
     * Sets the cpu count/memory properties of a powered-off VM to the desired values.
     * @param vm
     * @throws Exception
     */
    private void reconfigure(ManagedObjectReference vm) throws Exception {
        VirtualMachineConfigSpec spec = new VirtualMachineConfigSpec();
        spec.setNumCPUs((int) this.state.description.cpuCount);
        spec.setMemoryMB(toMb(this.state.description.totalMemoryBytes));

        ManagedObjectReference reconfigTask = getVimPort().reconfigVMTask(vm, spec);
        TaskInfo taskInfo = VimUtils.waitTaskEnd(this.connection, reconfigTask);
        if (taskInfo.getState() == TaskInfoState.ERROR) {
            VimUtils.rethrow(taskInfo.getError());
        }
    }

    /**
     * Creates disks and attaches them to the vm created by {@link #createInstance()}.
     * The given diskStates are enriched with data from vSphere and can be patched back to xenon.
     */
    public void attachDisks(List<DiskState> diskStates) throws Exception {
        if (isOvfDeploy()) {
            return;
        }

        if (this.vm == null) {
            throw new IllegalStateException("Cannot attach diskStates if VM is not created");
        }

        EnumSet<DiskType> notSupportedTypes = EnumSet.of(DiskType.SSD, DiskType.NETWORK);
        List<DiskState> unsupportedDisks = diskStates.stream()
                .filter(d -> notSupportedTypes.contains(d.type))
                .collect(Collectors.toList());
        if (!unsupportedDisks.isEmpty()) {
            throw new IllegalStateException(
                    "Some diskStates cannot be created: " + unsupportedDisks.stream()
                            .map(d -> d.documentSelfLink).collect(Collectors.toList()));
        }

        // the path to folder holding all vm files
        String dir = this.get.entityProp(this.vm, VimPath.vm_config_files_vmPathName);
        dir = Paths.get(dir).getParent().toString();

        ArrayOfVirtualDevice devices = this.get
                .entityProp(this.vm, VimPath.vm_config_hardware_device);

        VirtualDevice scsiController = getFirstScsiController(devices);
        int scsiUnit = findFreeUnit(scsiController, devices.getVirtualDevice());

        VirtualDevice ideController = getFirstIdeController(devices);
        int ideUnit = findFreeUnit(ideController, devices.getVirtualDevice());

        VirtualDevice sioController = getFirstSioController(devices);
        int sioUnit = findFreeUnit(sioController, devices.getVirtualDevice());

        List<VirtualDeviceConfigSpec> newDisks = new ArrayList<>();

        boolean cdromAdded = false;

        for (DiskState ds : diskStates) {
            String diskPath = VimUtils.uriToDatastorePath(ds.sourceImageReference);

            if (ds.type == DiskType.HDD) {
                if (diskPath != null) {
                    // create full clone of given disk
                    VirtualDeviceConfigSpec hdd = createFullCloneAndAttach(diskPath, ds, dir,
                            scsiController, scsiUnit);
                    newDisks.add(hdd);
                } else {
                    VirtualDeviceConfigSpec hdd = createHdd(scsiController, ds, dir, scsiUnit);
                    newDisks.add(hdd);
                }

                scsiUnit = nextUnitNumber(scsiUnit);
            }
            if (ds.type == DiskType.CDROM) {
                VirtualDeviceConfigSpec cdrom = createCdrom(ideController, ideUnit);
                ideUnit = nextUnitNumber(ideUnit);
                if (diskPath != null) {
                    // mount iso image
                    insertCdrom((VirtualCdrom) cdrom.getDevice(), diskPath);
                }
                newDisks.add(cdrom);
                cdromAdded = true;
            }
            if (ds.type == DiskType.FLOPPY) {
                VirtualDeviceConfigSpec floppy = createFloppy(sioController, sioUnit);
                sioUnit = nextUnitNumber(sioUnit);
                if (diskPath != null) {
                    // mount iso image
                    insertFloppy((VirtualFloppy) floppy.getDevice(), diskPath);
                }
                newDisks.add(floppy);
            }

            // mark disk as attached
            ds.status = DiskStatus.ATTACHED;
        }

        // add a cdrom so that ovf transport works
        if (!cdromAdded) {
            VirtualDeviceConfigSpec cdrom = createCdrom(ideController, ideUnit);
            newDisks.add(cdrom);
        }

        // add disks one at a time
        for (VirtualDeviceConfigSpec newDisk : newDisks) {
            VirtualMachineConfigSpec spec = new VirtualMachineConfigSpec();
            spec.getDeviceChange().add(newDisk);

            ManagedObjectReference reconfigureTask = getVimPort().reconfigVMTask(this.vm, spec);
            TaskInfo info = waitTaskEnd(reconfigureTask);
            if (info.getState() == TaskInfoState.ERROR) {
                VimUtils.rethrow(info.getError());
            }
        }
    }

    private TaskInfo waitTaskEnd(ManagedObjectReference task)
            throws InvalidCollectorVersionFaultMsg, InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        return VimUtils.waitTaskEnd(this.connection, task);
    }

    private VirtualDeviceConfigSpec createFullCloneAndAttach(String sourcePath, DiskState ds,
            String dir, VirtualDevice scsiController, int unitNumber)
            throws Exception {

        ManagedObjectReference diskManager = this.connection.getServiceContent()
                .getVirtualDiskManager();

        // put full clone in the vm folder
        String destName = makePathToVmdkFile(ds, dir);

        // all ops are withing a datacenter
        ManagedObjectReference sourceDc = this.finder.getDatacenter().object;
        ManagedObjectReference destDc = sourceDc;

        Boolean force = true;

        // spec is not supported, should use null for now
        VirtualDiskSpec spec = null;

        ManagedObjectReference task = getVimPort()
                .copyVirtualDiskTask(diskManager, sourcePath, sourceDc, destName, destDc, spec,
                        force);

        // wait for the disk to be copied
        TaskInfo taskInfo = waitTaskEnd(task);
        if (taskInfo.getState() == TaskInfoState.ERROR) {
            return VimUtils.rethrow(taskInfo.getError());
        }

        VirtualDisk disk = new VirtualDisk();

        VirtualDiskFlatVer2BackingInfo backing = new VirtualDiskFlatVer2BackingInfo();
        backing.setDiskMode(VirtualDiskMode.PERSISTENT.value());
        backing.setThinProvisioned(true);
        backing.setFileName(destName);
        backing.setDatastore(getDatastore());

        disk.setBacking(backing);
        disk.setControllerKey(scsiController.getKey());
        disk.setUnitNumber(unitNumber);
        disk.setKey(-1);

        VirtualDeviceConfigSpec change = new VirtualDeviceConfigSpec();
        change.setDevice(disk);
        change.setOperation(VirtualDeviceConfigSpecOperation.ADD);

        return change;
    }

    private VirtualDeviceConfigSpec createCdrom(VirtualDevice ideController, int unitNumber) {
        VirtualCdrom cdrom = new VirtualCdrom();

        cdrom.setControllerKey(ideController.getKey());
        cdrom.setUnitNumber(unitNumber);

        VirtualDeviceConnectInfo info = new VirtualDeviceConnectInfo();
        info.setAllowGuestControl(true);
        info.setConnected(true);
        info.setStartConnected(true);
        cdrom.setConnectable(info);

        VirtualCdromAtapiBackingInfo backing = new VirtualCdromAtapiBackingInfo();
        backing.setDeviceName(String.format("cdrom-%d-%d", ideController.getKey(), unitNumber));
        backing.setUseAutoDetect(false);
        cdrom.setBacking(backing);

        VirtualDeviceConfigSpec spec = new VirtualDeviceConfigSpec();
        spec.setDevice(cdrom);
        spec.setOperation(VirtualDeviceConfigSpecOperation.ADD);

        return spec;
    }

    /**
     * Changes to backing of the cdrom to an iso-backed one.
     *
     * @param cdrom
     * @param imagePath path to iso on disk, sth. like "[datastore] /images/ubuntu-16.04-amd64.iso"
     */
    private void insertCdrom(VirtualCdrom cdrom, String imagePath) {
        VirtualCdromIsoBackingInfo backing = new VirtualCdromIsoBackingInfo();
        backing.setFileName(imagePath);

        cdrom.setBacking(backing);
    }

    /**
     * Changes to backing of the floppy to an image-backed one.
     *
     * @param floppy
     * @param imagePath
     */
    private void insertFloppy(VirtualFloppy floppy, String imagePath) {
        VirtualFloppyImageBackingInfo backingInfo = new VirtualFloppyImageBackingInfo();
        backingInfo.setFileName(imagePath);
        floppy.setBacking(backingInfo);
    }

    private VirtualDeviceConfigSpec createFloppy(VirtualDevice sioController, int unitNumber) {
        VirtualFloppy floppy = new VirtualFloppy();

        floppy.setControllerKey(sioController.getKey());
        floppy.setUnitNumber(unitNumber);

        VirtualDeviceConnectInfo info = new VirtualDeviceConnectInfo();
        info.setAllowGuestControl(true);
        info.setConnected(true);
        info.setStartConnected(true);
        floppy.setConnectable(info);

        VirtualFloppyDeviceBackingInfo backing = new VirtualFloppyDeviceBackingInfo();
        backing.setDeviceName(String.format("floppy-%d", unitNumber));
        floppy.setBacking(backing);

        VirtualDeviceConfigSpec spec = new VirtualDeviceConfigSpec();
        spec.setDevice(floppy);
        spec.setOperation(VirtualDeviceConfigSpecOperation.ADD);
        return spec;
    }

    private VirtualSIOController getFirstSioController(ArrayOfVirtualDevice devices) {
        for (VirtualDevice dev : devices.getVirtualDevice()) {
            if (dev instanceof VirtualSIOController) {
                return (VirtualSIOController) dev;
            }
        }

        throw new IllegalStateException("No SIO controller found");
    }

    private int findFreeUnit(VirtualDevice controller, List<VirtualDevice> devices) {
        // TODO better find the first free slot
        int max = 0;
        for (VirtualDevice dev : devices) {
            if (dev.getControllerKey() != null && controller.getKey() == dev
                    .getControllerKey()) {
                max = Math.max(dev.getUnitNumber(), max);
            }
        }

        return max;
    }

    /**
     * Increments the given unit number. Skips the number 6 which is reserved in scsi. IDE and SIO
     * go up to 2 so it is safe to use this method for all types of controllers.
     *
     * @param unitNumber
     * @return
     */
    private int nextUnitNumber(int unitNumber) {
        if (unitNumber == 6) {
            // unit 7 is reserved
            return 8;
        }
        return unitNumber + 1;
    }

    private VirtualDeviceConfigSpec createHdd(VirtualDevice scsiController, DiskState ds,
            String dir, int unitNumber)
            throws FinderException, InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        String diskName = makePathToVmdkFile(ds, dir);

        VirtualDisk disk = new VirtualDisk();
        disk.setCapacityInKB(toKb(ds.capacityMBytes));

        VirtualDiskFlatVer2BackingInfo backing = new VirtualDiskFlatVer2BackingInfo();
        backing.setDiskMode(VirtualDiskMode.PERSISTENT.value());
        backing.setThinProvisioned(true);
        backing.setFileName(diskName);
        backing.setDatastore(getDatastore());

        disk.setBacking(backing);
        disk.setControllerKey(scsiController.getKey());
        disk.setUnitNumber(unitNumber);
        disk.setKey(-1);

        VirtualDeviceConfigSpec change = new VirtualDeviceConfigSpec();
        change.setDevice(disk);
        change.setOperation(VirtualDeviceConfigSpecOperation.ADD);
        change.setFileOperation(VirtualDeviceConfigSpecFileOperation.CREATE);

        return change;
    }

    private String makePathToVmdkFile(DiskState ds, String dir) {
        String diskName = Paths.get(dir, ds.id).toString();
        if (!diskName.endsWith(".vmdk")) {
            diskName += ".vmdk";
        }
        return diskName;
    }

    private VirtualIDEController getFirstIdeController(ArrayOfVirtualDevice devices) {
        for (VirtualDevice dev : devices.getVirtualDevice()) {
            if (dev instanceof VirtualIDEController) {
                return (VirtualIDEController) dev;
            }
        }

        throw new IllegalStateException("No IDE controller found");
    }

    private VirtualSCSIController getFirstScsiController(ArrayOfVirtualDevice devices) {
        for (VirtualDevice dev : devices.getVirtualDevice()) {
            if (dev instanceof VirtualSCSIController) {
                return (VirtualSCSIController) dev;
            }
        }

        throw new IllegalStateException("No SCSI controller found");
    }

    /**
     * Once a vm is provisioned this method collects vsphere-assigned properties and stores them
     * in the {@link ComputeState#customProperties}
     *
     * @param state
     * @throws InvalidPropertyFaultMsg
     * @throws RuntimeFaultFaultMsg
     */
    public VmOverlay enrichStateFromVm(ComputeState state)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        Map<String, Object> props = this.get.entityProps(this.vm,
                VimPath.vm_config_instanceUuid,
                VimPath.vm_config_name,
                VimPath.vm_config_hardware_device,
                VimPath.vm_runtime_powerState,
                VimPath.vm_runtime_host,
                VimPath.vm_guest_net,
                VimPath.vm_summary_guest_ipAddress,
                VimPath.vm_summary_guest_hostName);

        VmOverlay overlay = new VmOverlay(this.vm, props);
        state.id = overlay.getInstanceUuid();
        state.primaryMAC = overlay.getPrimaryMac();
        state.powerState = overlay.getPowerState();
        state.address = overlay.guessPublicIpV4Address();
        state.name = overlay.getName();

        CustomProperties.of(state)
                .put(CustomProperties.MOREF, this.vm)
                .put(CustomProperties.TYPE, VimNames.TYPE_VM);

        return overlay;
    }

    /**
     * Creates a VM in vsphere. This method will block until the CreateVM_Task completes.
     * The path to the .vmx file is explicitly set and its existence is iterpreted as if the VM has
     * been successfully created and returns null.
     *
     * @return
     * @throws FinderException
     * @throws Exception
     */
    private ManagedObjectReference createVm() throws Exception {
        ManagedObjectReference folder = getVmFolder();
        ManagedObjectReference datastore = getDatastore();
        ManagedObjectReference resourcePool = getResourcePool();
        ManagedObjectReference host = getHost();

        String datastoreName = this.get.entityProp(datastore, "name");
        VirtualMachineConfigSpec spec = buildVirtualMachineConfigSpec(datastoreName);

        populateCloudConfig(spec, null);
        ManagedObjectReference vmTask = getVimPort().createVMTask(folder, spec, resourcePool, host);

        TaskInfo info = waitTaskEnd(vmTask);

        if (info.getState() == TaskInfoState.ERROR) {
            MethodFault fault = info.getError().getFault();
            if (fault instanceof FileAlreadyExists) {
                // a .vmx file already exists, assume someone won the race to create the vm
                return null;
            } else {
                return VimUtils.rethrow(info.getError());
            }
        }

        return (ManagedObjectReference) info.getResult();
    }

    /**
     * Puts the cloud-config user data in the OVF environment
     * @param spec
     * @param currentProps
     */
    private boolean populateCloudConfig(VirtualMachineConfigSpec spec, ArrayOfVAppPropertyInfo currentProps) {
        if (this.disks == null || this.disks.size() == 0) {
            return false;
        }

        DiskState bootDisk = findBootDisk();

        if (bootDisk == null) {
            return false;
        }

        boolean customizationsApplied = false;
        int nextKey = 1;
        if (currentProps != null) {
            nextKey = currentProps.getVAppPropertyInfo().stream()
                    .mapToInt(VAppPropertyInfo::getKey)
                    .max()
                    .orElse(1);
        }

        VmConfigSpec configSpec = new VmConfigSpec();
        configSpec.getOvfEnvironmentTransport().add(OvfDeployer.TRANSPORT_ISO);

        String cloudConfig = getFileItemByPath(bootDisk, CLOUD_CONFIG_PROPERTY_USER_DATA);
        if (cloudConfig != null) {
            VAppPropertySpec propertySpec = new VAppPropertySpec();

            VAppPropertyInfo userDataInfo = null;
            if (currentProps != null) {
                userDataInfo = currentProps.getVAppPropertyInfo().stream()
                        .filter(p -> p.getId().equals(CLOUD_CONFIG_PROPERTY_USER_DATA))
                        .findFirst()
                        .orElse(null);
                if (userDataInfo == null) {
                    // try coreOS key
                    userDataInfo = currentProps.getVAppPropertyInfo().stream()
                            .filter(p -> p.getId().equals(COREOS_CLOUD_CONFIG_PROPERTY_USER_DATA))
                            .findFirst()
                            .orElse(null);
                }
            }

            if (userDataInfo != null) {
                propertySpec.setOperation(ArrayUpdateOperation.EDIT);
            } else {
                userDataInfo = new VAppPropertyInfo();
                userDataInfo.setId(CLOUD_CONFIG_PROPERTY_USER_DATA);
                userDataInfo.setType("string");
                userDataInfo.setKey(nextKey++);
                propertySpec.setOperation(ArrayUpdateOperation.ADD);
            }
            String encodedUserData = Base64.getEncoder().encodeToString(cloudConfig.getBytes());
            userDataInfo.setValue(encodedUserData);

            propertySpec.setInfo(userDataInfo);
            configSpec.getProperty().add(propertySpec);
            customizationsApplied = true;
        }

        String publicKeys = getFileItemByPath(bootDisk, CLOUD_CONFIG_PROPERTY_PUBLIC_KEYS);
        if (publicKeys != null) {
            VAppPropertySpec propertySpec = new VAppPropertySpec();

            VAppPropertyInfo sshKeyInfo = null;
            if (currentProps != null) {
                sshKeyInfo = currentProps.getVAppPropertyInfo().stream()
                        .filter(p -> p.getId().equals(CLOUD_CONFIG_PROPERTY_PUBLIC_KEYS))
                        .findFirst()
                        .orElse(null);
            }
            if (sshKeyInfo != null) {
                propertySpec.setOperation(ArrayUpdateOperation.EDIT);
            } else {
                sshKeyInfo = new VAppPropertyInfo();
                sshKeyInfo.setType("string");
                sshKeyInfo.setId(CLOUD_CONFIG_PROPERTY_PUBLIC_KEYS);
                sshKeyInfo.setKey(nextKey++);
                propertySpec.setOperation(ArrayUpdateOperation.ADD);
            }
            sshKeyInfo.setValue(publicKeys);

            propertySpec.setInfo(sshKeyInfo);
            configSpec.getProperty().add(propertySpec);
            customizationsApplied = true;
        }

        if (customizationsApplied) {
            spec.setVAppConfig(configSpec);
        }

        return customizationsApplied;
    }

    private String getFileItemByPath(DiskState bootDisk, String fileName) {
        if (bootDisk != null && bootDisk.bootConfig != null && bootDisk.bootConfig.files != null) {
            for (FileEntry e : bootDisk.bootConfig.files) {
                if (Objects.equals(fileName, e.path)) {
                    return e.contents;
                }
            }
        }

        return null;
    }

    /**
     * Decides in which folder to put the newly created vm.
     *
     * @return
     * @throws InvalidPropertyFaultMsg
     * @throws RuntimeFaultFaultMsg
     * @throws FinderException
     */
    private ManagedObjectReference getVmFolder()
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg, FinderException {

        // look for a configured folder in compute state
        String folderPath = CustomProperties.of(this.state)
                .getString(RESOURCE_GROUP_NAME);

        if (folderPath == null) {
            // look for a configured folder in parent
            folderPath = CustomProperties.of(this.parent)
                    .getString(RESOURCE_GROUP_NAME);
        }

        if (folderPath == null) {
            return this.finder.vmFolder().object;
        } else {
            return this.finder.folder(folderPath).object;
        }
    }

    /**
     * Creates a spec used to create the VM.
     *
     * @param datastoreName
     * @return
     * @throws InvalidPropertyFaultMsg
     * @throws FinderException
     * @throws RuntimeFaultFaultMsg
     */
    private VirtualMachineConfigSpec buildVirtualMachineConfigSpec(String datastoreName)
            throws InvalidPropertyFaultMsg, FinderException, RuntimeFaultFaultMsg {
        String displayName = this.state.name;

        VirtualMachineConfigSpec spec = new VirtualMachineConfigSpec();
        spec.setName(displayName);
        spec.setNumCPUs((int) this.state.description.cpuCount);
        spec.setGuestId(VirtualMachineGuestOsIdentifier.OTHER_GUEST_64.value());
        spec.setMemoryMB(toMb(this.state.description.totalMemoryBytes));

        VirtualMachineFileInfo files = new VirtualMachineFileInfo();
        // Use a full path to the config file to avoid creating a VM with the same name
        String path = String.format("[%s] %s/%s.vmx", datastoreName, displayName, displayName);
        files.setVmPathName(path);
        spec.setFiles(files);

        for (NetworkInterfaceStateWithDetails ni : this.nics) {
            VirtualDevice nic = createNic(ni.network.name);
            addDeviceToVm(spec, nic);
        }

        VirtualDevice scsi = createScsiController();
        addDeviceToVm(spec, scsi);

        return spec;
    }

    private void addDeviceToVm(VirtualMachineConfigSpec spec, VirtualDevice dev) {
        VirtualDeviceConfigSpec change = new VirtualDeviceConfigSpec();
        change.setDevice(dev);
        change.setOperation(VirtualDeviceConfigSpecOperation.ADD);
        spec.getDeviceChange().add(change);
    }

    private VirtualDevice createScsiController() {
        VirtualLsiLogicController scsiCtrl = new VirtualLsiLogicController();
        scsiCtrl.setBusNumber(0);
        scsiCtrl.setKey(-1);
        scsiCtrl.setSharedBus(VirtualSCSISharing.NO_SHARING);

        return scsiCtrl;
    }

    private VirtualEthernetCard createNic(String networkName)
            throws FinderException, InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        VirtualEthernetCard nic = new VirtualE1000();
        nic.setAddressType(VirtualEthernetCardMacType.GENERATED.value());
        nic.setKey(-1);

        VirtualEthernetCardNetworkBackingInfo backing = new VirtualEthernetCardNetworkBackingInfo();
        backing.setDeviceName(networkName);

        nic.setBacking(backing);

        return nic;
    }

    private Long toMb(long bytes) {
        return bytes / 1024 / 1024;
    }

    private Long toKb(long mb) {
        return mb * 1024;
    }

    /**
     * Finds the datastore to use for the VM from the ComputeState.description.datastoreId.
     *
     * @return
     * @throws RuntimeFaultFaultMsg
     * @throws InvalidPropertyFaultMsg
     * @throws FinderException
     */
    private ManagedObjectReference getDatastore()
            throws RuntimeFaultFaultMsg, InvalidPropertyFaultMsg, FinderException {
        if (this.datastore != null) {
            return this.datastore;
        }

        String datastorePath = this.state.description.dataStoreId;

        if (datastorePath == null) {
            ArrayOfManagedObjectReference datastores = this.get
                    .entityProp(this.parentComputeResource, VimPath.res_datastore);
            if (datastores == null || datastores.getManagedObjectReference().isEmpty()) {
                this.datastore = this.finder.defaultDatastore().object;
            } else {
                this.datastore = datastores.getManagedObjectReference().get(0);
            }
        } else {
            this.datastore = this.finder.datastore(datastorePath).object;
        }

        return this.datastore;
    }

    public ManagedObjectReference getResourcePool()
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        if (this.resourcePool != null) {
            return this.resourcePool;
        }

        if (VimNames.TYPE_HOST.equals(this.parentComputeResource.getType())) {
            ManagedObjectReference parentCompute = this.get.entityProp(this.parentComputeResource,
                    VimPath.host_parent);
            this.resourcePool = this.get.entityProp(parentCompute, VimPath.res_resourcePool);
        } else {
            this.resourcePool = this.get.entityProp(this.parentComputeResource,
                    VimPath.res_resourcePool);

        }

        return this.resourcePool;
    }

    public ManagedObjectReference getHost()
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        if (this.host != null) {
            return this.host;
        }

        if (VimNames.TYPE_HOST.equals(this.parentComputeResource.getType())) {
            this.host = this.parentComputeResource;
        }

        return this.host;
    }

    public ManagedObjectReference getVm() {
        return this.vm;
    }

    public static class ClientException extends Exception {
        private static final long serialVersionUID = 1L;

        public ClientException(String message, Throwable cause) {
            super(message, cause);
        }

        public ClientException(Throwable cause) {
            super(cause);
        }

        public ClientException(String message) {
            super(message);
        }
    }
}
