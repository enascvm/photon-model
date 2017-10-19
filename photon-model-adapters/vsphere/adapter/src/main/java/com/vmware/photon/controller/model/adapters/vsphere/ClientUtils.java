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

import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.DISK_CONTROLLER_NUMBER;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.DISK_MODE_INDEPENDENT;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.DISK_MODE_PERSISTENT;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.LIMIT_IOPS;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.PROVIDER_DISK_UNIQUE_ID;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.PROVISION_TYPE;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.SHARES;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.SHARES_LEVEL;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.EnumSet;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.pbm.PbmFaultFaultMsg;
import com.vmware.pbm.PbmPlacementHub;
import com.vmware.pbm.PbmProfileId;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.adapters.vsphere.util.finders.Element;
import com.vmware.photon.controller.model.adapters.vsphere.util.finders.Finder;
import com.vmware.photon.controller.model.adapters.vsphere.util.finders.FinderException;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.util.PhotonModelUriUtils;
import com.vmware.vim25.ArrayOfVirtualDevice;
import com.vmware.vim25.FileFaultFaultMsg;
import com.vmware.vim25.FileNotFoundFaultMsg;
import com.vmware.vim25.InvalidDatastoreFaultMsg;
import com.vmware.vim25.InvalidDatastorePathFaultMsg;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.SharesInfo;
import com.vmware.vim25.SharesLevel;
import com.vmware.vim25.StorageIOAllocationInfo;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.VimPortType;
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
import com.vmware.vim25.VirtualDiskType;
import com.vmware.vim25.VirtualFloppy;
import com.vmware.vim25.VirtualFloppyDeviceBackingInfo;
import com.vmware.vim25.VirtualFloppyImageBackingInfo;
import com.vmware.vim25.VirtualIDEController;
import com.vmware.vim25.VirtualMachineDefinedProfileSpec;
import com.vmware.vim25.VirtualSCSIController;
import com.vmware.vim25.VirtualSIOController;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.http.netty.NettyHttpServiceClient;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Utility methods that are common for all the clients like InstanceClient, EnumerationClient etc,
 */
public class ClientUtils {

    private static final Logger logger = LoggerFactory.getLogger(ClientUtils.class.getName());
    private static final long SINCE_TIME = new GregorianCalendar(2016, Calendar.JANUARY, 1)
            .getTime().getTime();
    public static final String VM_PATH_FORMAT = "[%s] %s";

    /**
     * Retrieves list of datastore names that are compatible with the storage policy.
     */
    public static List<String> getDatastores(final Connection connection,
            final PbmProfileId pbmProfileId)
            throws com.vmware.pbm.RuntimeFaultFaultMsg, PbmFaultFaultMsg {
        List<PbmPlacementHub> hubs = connection.getPbmPort().pbmQueryMatchingHub(
                connection.getPbmServiceInstanceContent().getPlacementSolver(), null,
                pbmProfileId);
        List<String> dataStoreNames = new ArrayList<>();
        if (hubs != null && !hubs.isEmpty()) {
            hubs.stream().filter(hub -> hub.getHubType().equals(VimNames.TYPE_DATASTORE))
                    .forEach(hub -> dataStoreNames.add(hub.getHubId()));
        }
        return dataStoreNames;
    }

    public static Long toKb(long mb) {
        return mb * 1024;
    }

    /**
     * Get the provisioning type of the disk
     */
    public static VirtualDiskType getDiskProvisioningType(DiskService.DiskStateExpanded diskState)
            throws IllegalArgumentException {
        try {
            // Return null as default so that what ever defaults picked by vc will be honored
            // instead of we setting to default THIN.
            return diskState.customProperties != null
                    && diskState.customProperties.get(PROVISION_TYPE) != null ?
                    VirtualDiskType.fromValue(diskState.customProperties.get(PROVISION_TYPE)) :
                    null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Construct storage policy profile spec for a profile Id
     */
    public static List<VirtualMachineDefinedProfileSpec> getPbmProfileSpec(
            DiskService.DiskStateExpanded diskState) {
        if (diskState == null || diskState.resourceGroupStates == null
                || diskState.resourceGroupStates.isEmpty()) {
            return null;
        }
        List<VirtualMachineDefinedProfileSpec> profileSpecs = diskState.resourceGroupStates.stream()
                .map(rg -> {
                    VirtualMachineDefinedProfileSpec spbmProfile = new VirtualMachineDefinedProfileSpec();
                    spbmProfile.setProfileId(rg.id);
                    return spbmProfile;
                }).collect(Collectors.toList());
        return profileSpecs;
    }

    /**
     * Make file path to the vmdk file
     */
    public static String makePathToVmdkFile(String name, String dir) {
        String diskName = Paths.get(dir, name).toString();
        if (!diskName.endsWith(".vmdk")) {
            diskName += ".vmdk";
        }
        return diskName;
    }

    /**
     * Get one of the datastore compatible with storage policy
     */
    public static ManagedObjectReference getDatastoreFromStoragePolicy(final Connection connection,
            List<VirtualMachineDefinedProfileSpec> pbmSpec)
            throws FinderException, InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        if (pbmSpec != null) {
            for (VirtualMachineDefinedProfileSpec sp : pbmSpec) {
                try {
                    PbmProfileId pbmProfileId = new PbmProfileId();
                    pbmProfileId.setUniqueId(sp.getProfileId());
                    List<String> datastoreNames = ClientUtils.getDatastores(connection,
                            pbmProfileId);
                    String dsName = datastoreNames.stream().findFirst().orElse(null);
                    if (dsName != null) {
                        ManagedObjectReference dsFromSp = new ManagedObjectReference();
                        dsFromSp.setType(VimNames.TYPE_DATASTORE);
                        dsFromSp.setValue(dsName);
                        return dsFromSp;
                    }
                } catch (Exception runtimeFaultFaultMsg) {
                    // Just ignore. No need to log, as there are alternative paths.
                }
            }
        }
        return null;
    }

    /**
     * Datastore name if any specified for the disk, if not fall back to the default datastore.
     */
    public static String getDatastorePathForDisk(DiskService.DiskStateExpanded diskState,
            String defaultDsPath)
            throws Exception {
        String dsPath = defaultDsPath;
        if (diskState.storageDescription != null) {
            String vmName = defaultDsPath.substring(defaultDsPath.indexOf(']') + 2, defaultDsPath
                    .length());
            dsPath = String.format(VM_PATH_FORMAT, diskState.storageDescription.id, vmName);
        }
        return dsPath;
    }

    /**
     * Disk mode is determined based on the disk state properties.
     */
    public static String getDiskMode(DiskService.DiskStateExpanded diskState) {
        if (diskState.customProperties == null) {
            return VirtualDiskMode.PERSISTENT.value();
        }

        boolean isIndependent = Boolean.valueOf(diskState.customProperties.get(DISK_MODE_INDEPENDENT));
        boolean isPersistent = Boolean.valueOf(diskState.customProperties.get(DISK_MODE_PERSISTENT));

        return isIndependent ?
                (isPersistent ?
                        VirtualDiskMode.INDEPENDENT_PERSISTENT.value() :
                        VirtualDiskMode.INDEPENDENT_NONPERSISTENT.value()) :
                VirtualDiskMode.PERSISTENT.value();
    }

    /**
     * Constructs storage IO allocation if this is not already dictated by the storage policy that
     * is chosen.
     */
    public static StorageIOAllocationInfo getStorageIOAllocationInfo(
            DiskService.DiskStateExpanded diskState) throws
            NumberFormatException {
        if (diskState.customProperties != null) {
            String sharesLevel = diskState.customProperties.get(SHARES_LEVEL);
            // If the value is null or wrong value sent by the caller for SharesLevel then don't
            // set anything on the API for this. Hence default to null.
            if (sharesLevel != null) {
                try {
                    StorageIOAllocationInfo allocationInfo = new StorageIOAllocationInfo();
                    SharesInfo sharesInfo = new SharesInfo();
                    sharesInfo.setLevel(SharesLevel.fromValue(sharesLevel));
                    if (sharesInfo.getLevel() == SharesLevel.CUSTOM) {
                        // Set shares value
                        String sharesVal = diskState.customProperties.get(SHARES);
                        if (sharesVal == null || sharesVal.isEmpty()) {
                            // Reset to normal as nothing is specified for the shares
                            sharesInfo.setLevel(SharesLevel.NORMAL);
                        } else {
                            sharesInfo.setShares(Integer.parseInt(sharesVal));
                        }
                    }
                    allocationInfo.setShares(sharesInfo);
                    String limitIops = diskState.customProperties.get(LIMIT_IOPS);
                    if (limitIops != null && !limitIops.isEmpty()) {
                        allocationInfo.setLimit(Long.parseLong(limitIops));
                    }
                    return allocationInfo;
                } catch (Exception e) {
                    logger.warn("Ignoring the storage IO allocation customization values due to {}",
                            e.getMessage());
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Find available free unit number for scsi controller.
     */
    public static Integer[] findFreeScsiUnit(VirtualSCSIController controller, List<VirtualDevice>
            devices) {
        // Max scsi controller number is 16, but supported runtime value could be fetched from
        // VirtualHardwareOption
        int[] slots = new int[16];
        // Unit 7 is reserved
        slots[7] = 1;

        Map<Integer, VirtualDevice> deviceMap = new HashMap<>();
        devices.stream().forEach(device -> deviceMap.put(device.getKey(), device));
        controller.getDevice().stream().forEach(deviceKey -> {
            if (deviceMap.get(deviceKey).getUnitNumber() != null) {
                slots[deviceMap.get(deviceKey).getUnitNumber()] = 1;
            }
        });

        List<Integer> freeUnitNumbers = new ArrayList<>();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != 1) {
                freeUnitNumbers.add(i);
            }
        }
        // Default to start from 0
        if (freeUnitNumbers.isEmpty()) {
            freeUnitNumbers.add(0);
        }
        Integer[] unitNumbersArray = new Integer[freeUnitNumbers.size()];
        return freeUnitNumbers.toArray(unitNumbersArray);
    }

    /**
     * Creates HDD virtual disk
     */
    public static VirtualDeviceConfigSpec createHdd(Integer controllerKey, int unitNumber,
            DiskService.DiskStateExpanded ds, String diskName, ManagedObjectReference datastore,
            List<VirtualMachineDefinedProfileSpec> pbmSpec)
            throws FinderException, InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {

        return createHdd(controllerKey, unitNumber, ds, diskName, datastore, pbmSpec, true);
    }

    /**
     * Creates HDD virtual disk
     */
    public static VirtualDeviceConfigSpec createHdd(Integer controllerKey, int unitNumber,
            DiskService.DiskStateExpanded ds, String diskName, ManagedObjectReference datastore,
            List<VirtualMachineDefinedProfileSpec> pbmSpec, boolean isCreateFile)
            throws FinderException, InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {

        VirtualDiskFlatVer2BackingInfo backing = new VirtualDiskFlatVer2BackingInfo();
        backing.setDiskMode(getDiskMode(ds));
        VirtualDiskType provisionType = getDiskProvisioningType(ds);
        if (provisionType != null) {
            backing.setThinProvisioned(provisionType == VirtualDiskType.THIN);
            backing.setEagerlyScrub(provisionType == VirtualDiskType.EAGER_ZEROED_THICK);
        }
        backing.setFileName(diskName);
        backing.setDatastore(datastore);

        VirtualDisk disk = new VirtualDisk();
        disk.setCapacityInKB(toKb(ds.capacityMBytes));
        disk.setBacking(backing);
        disk.setStorageIOAllocation(getStorageIOAllocationInfo(ds));
        disk.setControllerKey(controllerKey);
        disk.setUnitNumber(unitNumber);
        fillInControllerUnitNumber(ds, unitNumber);
        disk.setKey(-1);

        VirtualDeviceConfigSpec change = new VirtualDeviceConfigSpec();
        change.setDevice(disk);
        if (pbmSpec != null) {
            // Add storage policy spec
            pbmSpec.stream().forEach(sp -> {
                change.getProfile().add(sp);
            });
        }
        change.setOperation(VirtualDeviceConfigSpecOperation.ADD);
        if (isCreateFile) {
            change.setFileOperation(VirtualDeviceConfigSpecFileOperation.CREATE);
        }

        return change;
    }

    /**
     * Fill in the scsi controller unit number into the custom properties of disk state so that we
     * can update the details of the disk once the provisioning is complete.
     */
    public static void fillInControllerUnitNumber(DiskService.DiskStateExpanded ds,
            int unitNumber) {
        CustomProperties.of(ds).put(DISK_CONTROLLER_NUMBER, unitNumber);
    }

    /**
     * Get disk unit number
     */
    public static Integer getDiskControllerUnitNumber(DiskService.DiskStateExpanded ds) {
        String unitNumber = CustomProperties.of(ds).getString(DISK_CONTROLLER_NUMBER, "0");
        return Integer.parseInt(unitNumber);
    }

    public static VirtualSCSIController getFirstScsiController(ArrayOfVirtualDevice devices) {
        for (VirtualDevice dev : devices.getVirtualDevice()) {
            if (dev instanceof VirtualSCSIController) {
                return (VirtualSCSIController) dev;
            }
        }

        throw new IllegalStateException("No SCSI controller found");
    }

    public static VirtualDeviceConfigSpec createCdrom(VirtualDevice ideController, int unitNumber) {
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

    public static VirtualIDEController getFirstIdeController(ArrayOfVirtualDevice devices) {
        for (VirtualDevice dev : devices.getVirtualDevice()) {
            if (dev instanceof VirtualIDEController) {
                return (VirtualIDEController) dev;
            }
        }

        throw new IllegalStateException("No IDE controller found");
    }

    public static int findFreeUnit(VirtualDevice controller, List<VirtualDevice> devices) {
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
     */
    public static int nextUnitNumber(int unitNumber) {
        if (unitNumber == 6) {
            // unit 7 is reserved
            return 8;
        }
        return unitNumber + 1;
    }

    /**
     * Changes to backing of the cdrom to an iso-backed one.
     *
     * @param cdrom
     * @param imagePath
     *            path to iso on disk, sth. like "[datastore] /images/ubuntu-16.04-amd64.iso"
     */
    public static void insertCdrom(VirtualCdrom cdrom, String imagePath) {
        VirtualCdromIsoBackingInfo backing = new VirtualCdromIsoBackingInfo();
        backing.setFileName(imagePath);

        cdrom.setBacking(backing);
    }

    public static VirtualDeviceConfigSpec createFloppy(VirtualDevice sioController, int unitNumber) {
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

    /**
     * Changes to backing of the floppy to an image-backed one.
     */
    public static void insertFloppy(VirtualFloppy floppy, String imagePath) {
        VirtualFloppyImageBackingInfo backingInfo = new VirtualFloppyImageBackingInfo();
        backingInfo.setFileName(imagePath);
        floppy.setBacking(backingInfo);
    }

    public static VirtualSIOController getFirstSioController(ArrayOfVirtualDevice devices) {
        for (VirtualDevice dev : devices.getVirtualDevice()) {
            if (dev instanceof VirtualSIOController) {
                return (VirtualSIOController) dev;
            }
        }

        throw new IllegalStateException("No SIO controller found");
    }

    /**
     * Capture virtual disk attributes in the disk state for reference.
     */
    public static void updateDiskStateFromVirtualDisk(VirtualDisk vd, DiskService.DiskState disk) {
        disk.status = DiskService.DiskStatus.ATTACHED;
        disk.id = vd.getDiskObjectId();
        CustomProperties.of(disk)
                .put(PROVIDER_DISK_UNIQUE_ID, vd.getDeviceInfo().getLabel());
    }


    /**
     * Power off virtual machine
     */
    public static void powerOffVM(final Connection connection, final VimPortType vimPort,
            final ManagedObjectReference vm) throws Exception {
        ManagedObjectReference powerTask = vimPort.powerOffVMTask(vm);
        TaskInfo info = VimUtils.waitTaskEnd(connection, powerTask);
        if (info.getState() == TaskInfoState.ERROR) {
            VimUtils.rethrow(info.getError());
        }
    }

    /**
     * Power on virtual machine
     */
    public static void powerOnVM(final Connection connection, final VimPortType vimPort,
            final ManagedObjectReference vm) throws Exception {
        ManagedObjectReference powerTask = vimPort.powerOnVMTask(vm, null);
        TaskInfo info = VimUtils.waitTaskEnd(connection, powerTask);
        if (info.getState() == TaskInfoState.ERROR) {
            VimUtils.rethrow(info.getError());
        }
    }

    /**
     * Get a unique name for the disk by appending a portion of a UUID.
     */
    public static String getUniqueName(String prefix) {
        // Construct current time milli
        long timestamp = System.currentTimeMillis() - SINCE_TIME;
        String uniqueName = prefix + "-" + timestamp;
        return uniqueName;
    }


    /**
     * Create a directory at the specified path.
     */
    public static void createFolder(Connection connection, ManagedObjectReference datacenterMoRef,
            String path) throws FileFaultFaultMsg, InvalidDatastoreFaultMsg, RuntimeFaultFaultMsg {

        ManagedObjectReference fileManager = connection.getServiceContent().getFileManager();
        connection.getVimPort().makeDirectory(fileManager, path, datacenterMoRef, true);
    }

    /**
     * Delete a directory at the specified path.
     */
    public static void deleteFolder(Connection connection, ManagedObjectReference datacenterMoRef,
            String path) throws FileFaultFaultMsg, InvalidDatastoreFaultMsg, RuntimeFaultFaultMsg,
            FileNotFoundFaultMsg, InvalidDatastorePathFaultMsg {

        ManagedObjectReference fileManager = connection.getServiceContent().getFileManager();
        connection.getVimPort().deleteDatastoreFileTask(fileManager, path, datacenterMoRef);
    }

    /**
     * Create a external client
     */
    public static ServiceClient getCustomServiceClient (TrustManager[] trustManagers,
            ServiceHost host, URI uri,
            String userAgent) {
        SSLContext clientContext;
        try {
            // supply a scheduled executor for re-use by the client, but do not supply our
            // regular executor, since the I/O threads might take up all threads
            ScheduledExecutorService scheduledExecutor = Executors
                    .newScheduledThreadPool(Utils.DEFAULT_THREAD_COUNT,
                            r -> new Thread(r, uri.toString()));
            ServiceClient externalClient = NettyHttpServiceClient.create(userAgent,
                    null,
                    scheduledExecutor, host
            );
            clientContext = SSLContext.getInstance(ServiceClient.TLS_PROTOCOL_NAME);
            clientContext.init(null, trustManagers, null);
            externalClient.setSSLContext(clientContext);
            externalClient.start();
            return externalClient;
        } catch (NoSuchAlgorithmException | KeyManagementException | URISyntaxException e) {
            if (e.getMessage() != null) {
                host.log(Level.SEVERE, e.getMessage());
            } else {
                host.log(Level.SEVERE, "Could not create custom service client. Returning default host client.");
            }
            return host.getClient();
        }
    }

    public static TrustManager getDefaultTrustManager() {
        // currently accepts all certificates.
        return new X509TrustManager() {

            @Override public void checkClientTrusted(
                    java.security.cert.X509Certificate[] x509Certificates, String s)
                    throws java.security.cert.CertificateException {

            }

            @Override public void checkServerTrusted(
                    java.security.cert.X509Certificate[] x509Certificates, String s)
                    throws java.security.cert.CertificateException {

            }

            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        };
    }

    public static String getDefaultDatastore(Finder finder)
            throws FinderException, InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {

        List<Element> datastoreList = finder.datastoreList("*");
        String defaultDatastore = datastoreList.stream().map(o -> o.path.substring(o.path
                .lastIndexOf("/") + 1))
                .findFirst()
                .orElse(null);
        return defaultDatastore;
    }

    public static void getDatastoresForProfile(Service service, String storagePolicyLink,
            String endpointLink, List<String> tenantLinks, Consumer<Throwable> failure,
            Consumer<ServiceDocumentQueryResult> handler) {
        QueryTask.Query.Builder builder = QueryTask.Query.Builder.create()
                .addKindFieldClause(StorageDescriptionService.StorageDescription.class);
        builder.addCollectionItemClause(
                StorageDescriptionService.StorageDescription.FIELD_NAME_GROUP_LINKS,
                storagePolicyLink);

        QueryUtils.addEndpointLink(builder, StorageDescriptionService.StorageDescription.class,
                endpointLink);
        QueryUtils.addTenantLinks(builder, tenantLinks);

        QueryTask task = QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();
        task.querySpec.options = EnumSet
                .of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);
        Operation.createPost(
                PhotonModelUriUtils.createDiscoveryUri(service.getHost(),
                        ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
                .setBody(task)
                .setConnectionSharing(true)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        service.getHost().log(Level.WARNING, "Error processing task %s",
                                task.documentSelfLink);

                        failure.accept(e);
                        return;
                    }
                    QueryTask body = o.getBody(QueryTask.class);
                    handler.accept(body.results);
                }).sendWith(service);
    }

}
