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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.pbm.InvalidArgumentFaultMsg;
import com.vmware.pbm.PbmFaultFaultMsg;
import com.vmware.pbm.PbmProfile;
import com.vmware.pbm.PbmProfileCategoryEnum;
import com.vmware.pbm.PbmProfileId;
import com.vmware.pbm.PbmProfileResourceType;
import com.vmware.pbm.PbmProfileResourceTypeEnum;
import com.vmware.pbm.PbmServerObjectRef;
import com.vmware.photon.controller.model.adapters.vsphere.constants.VSphereConstants;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimPath;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BaseHelper;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.GetMoRef;
import com.vmware.photon.controller.model.adapters.vsphere.util.finders.Finder;
import com.vmware.photon.controller.model.adapters.vsphere.util.finders.FinderException;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.vim25.ArrayOfHostDatastoreBrowserSearchResults;
import com.vmware.vim25.ArrayOfHostFileSystemMountInfo;
import com.vmware.vim25.FileFaultFaultMsg;
import com.vmware.vim25.FileQueryFlags;
import com.vmware.vim25.HostDatastoreBrowserSearchSpec;
import com.vmware.vim25.HostVmfsVolume;
import com.vmware.vim25.InvalidCollectorVersionFaultMsg;
import com.vmware.vim25.InvalidDatastoreFaultMsg;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.ObjectContent;
import com.vmware.vim25.ObjectSpec;
import com.vmware.vim25.PropertyFilterSpec;
import com.vmware.vim25.PropertySpec;
import com.vmware.vim25.RetrieveOptions;
import com.vmware.vim25.RetrieveResult;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.SelectionSpec;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TraversalSpec;
import com.vmware.vim25.UpdateSet;
import com.vmware.vim25.VimPortType;
import com.vmware.vim25.VmDiskFileQuery;
import com.vmware.vim25.VmDiskFileQueryFilter;
import com.vmware.vim25.WaitOptions;
import com.vmware.xenon.common.Utils;


/**
 * A client is bound to a datacenter.
 */
public class EnumerationClient extends BaseHelper {
    private static final Logger logger = LoggerFactory.getLogger(EnumerationClient.class);
    public static final int DEFAULT_FETCH_PAGE_SIZE = 100;
    private static final String HOST_DS_MOUNT_INFO = "config.fileSystemVolume.mountInfo";

    private ManagedObjectReference datacenter;
    private GetMoRef getMoRef;
    private Finder finder;

    public EnumerationClient(Connection connection) {
        super(connection);
        this.getMoRef = new GetMoRef(connection);
    }

    public EnumerationClient(Connection connection, ComputeStateWithDescription parent,
                             ManagedObjectReference datacenter) {
        super(connection);

        if (datacenter == null) {
            // / the regionId is used as a ref to a vSphere datacenter name
            this.datacenter = VimUtils.convertStringToMoRef(parent.description.regionId);
        } else {
            this.datacenter = datacenter;
        }

        if (this.datacenter == null) {
            throw new IllegalStateException("Datacenter cannot be extracted from compute resources"
                    + " and is not explicitly provided");
        }
        this.getMoRef = new GetMoRef(connection);
        this.finder = new Finder(connection, datacenter);
    }

    public ManagedObjectReference getDatacenter() {
        return this.datacenter;
    }

    /**
     * Get the mount info of all the datastores that are connected to a given host.
     */
    public Set<String> getDatastoresHostMountInfo(HostSystemOverlay hs)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        Set<String> sharedDs = new HashSet<>();
        ArrayOfHostFileSystemMountInfo mountInfo = this.getMoRef.entityProp(hs.getId(), HOST_DS_MOUNT_INFO);
        if (mountInfo != null) {
            mountInfo.getHostFileSystemMountInfo().stream()
                    .filter(fsMountInfo -> fsMountInfo.getVolume() instanceof HostVmfsVolume)
                    .forEach(fsMountInfo -> {
                        HostVmfsVolume vmfsVol = (HostVmfsVolume) fsMountInfo.getVolume();
                        if (!vmfsVol.isLocal()) {
                            sharedDs.add(vmfsVol.getName());
                        }
                    });
        }
        return sharedDs;
    }

    public ManagedObjectReference getParentSwitchForDVPortGroup(ManagedObjectReference portGroupMoref)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        ManagedObjectReference parent = this.getMoRef.entityProp(portGroupMoref, VimPath.pg_config_distributedVirtualSwitch);
        return parent;
    }

    public ManagedObjectReference getParentOfFolder(ManagedObjectReference folderRef)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        return this.getMoRef.entityProp(folderRef, VimNames.PROPERTY_PARENT);
    }

    public String getUUIDForDVS(NetworkOverlay net) throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        String uuid = this.getMoRef.entityProp(net.getParentSwitch(), VimPath.dvs_uuid);
        return uuid;
    }

    private ManagedObjectReference createPropertyCollector() throws RuntimeFaultFaultMsg {
        ManagedObjectReference pc = this.connection.getServiceContent().getPropertyCollector();
        return getVimPort().createPropertyCollector(pc);
    }

    public ManagedObjectReference createPropertyCollectorWithFilter(PropertyFilterSpec spec)
            throws RuntimeFaultFaultMsg, InvalidPropertyFaultMsg {
        ManagedObjectReference pc = createPropertyCollector();
        boolean partialUpdates = false;
        getVimPort().createFilter(pc, spec, partialUpdates);
        return pc;
    }

    private SelectionSpec getSelectionSpec(String name) {
        SelectionSpec genericSpec = new SelectionSpec();
        genericSpec.setName(name);
        return genericSpec;
    }

    /**
     * @return An array of SelectionSpec covering VM, Host, Resource pool,
     * Cluster Compute Resource and Datastore.
     */
    public List<SelectionSpec> buildFullTraversal() {
        // Terminal traversal specs

        // RP -> VM
        TraversalSpec rpToVm = new TraversalSpec();
        rpToVm.setType(VimNames.TYPE_RESOURCE_POOL);
        rpToVm.setName("rpToVm");
        rpToVm.setPath("vm");
        rpToVm.setSkip(Boolean.FALSE);

        // vApp -> VM
        TraversalSpec vAppToVM = new TraversalSpec();
        vAppToVM.setType(VimNames.TYPE_VAPP);
        vAppToVM.setName("vAppToVM");
        vAppToVM.setPath("vm");

        // HostSystem -> VM
        TraversalSpec hToVm = new TraversalSpec();
        hToVm.setType(VimNames.TYPE_HOST);
        hToVm.setPath("vm");
        hToVm.setName("hToVm");
        hToVm.getSelectSet().add(getSelectionSpec("VisitFolders"));
        hToVm.setSkip(Boolean.FALSE);

        // DC -> DS
        TraversalSpec dcToDs = new TraversalSpec();
        dcToDs.setType(VimNames.TYPE_DATACENTER);
        dcToDs.setPath("datastore");
        dcToDs.setName("dcToDs");
        dcToDs.setSkip(Boolean.FALSE);

        // Recurse through all ResourcePools
        TraversalSpec rpToRp = new TraversalSpec();
        rpToRp.setType(VimNames.TYPE_RESOURCE_POOL);
        rpToRp.setPath("resourcePool");
        rpToRp.setSkip(Boolean.FALSE);
        rpToRp.setName("rpToRp");
        rpToRp.getSelectSet().add(getSelectionSpec("rpToRp"));

        TraversalSpec crToRp = new TraversalSpec();
        crToRp.setType(VimNames.TYPE_COMPUTE_RESOURCE);
        crToRp.setPath("resourcePool");
        crToRp.setSkip(Boolean.FALSE);
        crToRp.setName("crToRp");
        crToRp.getSelectSet().add(getSelectionSpec("rpToRp"));

        TraversalSpec crToH = new TraversalSpec();
        crToH.setType(VimNames.TYPE_COMPUTE_RESOURCE);
        crToH.setSkip(Boolean.FALSE);
        crToH.setPath("host");
        crToH.setName("crToH");

        TraversalSpec dcToHf = new TraversalSpec();
        dcToHf.setType(VimNames.TYPE_DATACENTER);
        dcToHf.setSkip(Boolean.FALSE);
        dcToHf.setPath("hostFolder");
        dcToHf.setName("dcToHf");
        dcToHf.getSelectSet().add(getSelectionSpec("VisitFolders"));

        TraversalSpec vAppToRp = new TraversalSpec();
        vAppToRp.setType(VimNames.TYPE_VAPP);
        vAppToRp.setName("vAppToRp");
        vAppToRp.setPath("resourcePool");
        vAppToRp.getSelectSet().add(getSelectionSpec("rpToRp"));

        TraversalSpec dcToVmf = new TraversalSpec();
        dcToVmf.setType(VimNames.TYPE_DATACENTER);
        dcToVmf.setSkip(Boolean.FALSE);
        dcToVmf.setPath("vmFolder");
        dcToVmf.setName("dcToVmf");
        dcToVmf.getSelectSet().add(getSelectionSpec("VisitFolders"));

        TraversalSpec dcToNetf = new TraversalSpec();
        dcToNetf.setType(VimNames.TYPE_DATACENTER);
        dcToNetf.setSkip(Boolean.FALSE);
        dcToNetf.setPath("networkFolder");
        dcToNetf.setName("dcToNetf");
        dcToNetf.getSelectSet().add(getSelectionSpec("VisitFolders"));

        TraversalSpec dcToNet = new TraversalSpec();
        dcToNet.setType(VimNames.TYPE_DATACENTER);
        dcToNet.setSkip(Boolean.FALSE);
        dcToNet.setPath("network");
        dcToNet.setName("dcToNet");

        // For Folder -> Folder recursion
        TraversalSpec visitFolders = new TraversalSpec();
        visitFolders.setType(VimNames.TYPE_FOLDER);
        visitFolders.setPath("childEntity");
        visitFolders.setSkip(Boolean.FALSE);
        visitFolders.setName("VisitFolders");

        List<SelectionSpec> sspecarrvf = new ArrayList<>();
        sspecarrvf.add(getSelectionSpec("crToRp"));
        sspecarrvf.add(getSelectionSpec("crToH"));
        sspecarrvf.add(getSelectionSpec("dcToVmf"));
        sspecarrvf.add(getSelectionSpec("dcToHf"));
        sspecarrvf.add(getSelectionSpec("vAppToRp"));
        sspecarrvf.add(getSelectionSpec("vAppToVM"));
        sspecarrvf.add(getSelectionSpec("dcToDs"));
        sspecarrvf.add(getSelectionSpec("hToVm"));
        sspecarrvf.add(getSelectionSpec("rpToVm"));
        sspecarrvf.add(getSelectionSpec("dcToNet"));
        sspecarrvf.add(getSelectionSpec("dcToNetf"));
        sspecarrvf.add(getSelectionSpec("VisitFolders"));

        visitFolders.getSelectSet().addAll(sspecarrvf);

        List<SelectionSpec> resultspec = new ArrayList<>();
        resultspec.add(visitFolders);
        resultspec.add(crToRp);
        resultspec.add(crToH);
        resultspec.add(dcToVmf);
        resultspec.add(dcToHf);
        resultspec.add(vAppToRp);
        resultspec.add(vAppToVM);
        resultspec.add(dcToDs);
        resultspec.add(hToVm);
        resultspec.add(rpToVm);
        resultspec.add(rpToRp);
        resultspec.add(dcToNet);
        resultspec.add(dcToNetf);

        return resultspec;
    }

    public PropertyFilterSpec createVmFilterSpec(ManagedObjectReference dc) {
        ObjectSpec ospec = new ObjectSpec();
        ospec.setObj(dc);
        ospec.setSkip(false);

        ospec.getSelectSet().addAll(buildFullTraversal());

        PropertySpec vmSpec = new PropertySpec();
        vmSpec.setType(VimNames.TYPE_VM);
        vmSpec.getPathSet().addAll(Arrays.asList(
                VimPath.vm_config_name,
                VimPath.vm_config_instanceUuid,
                VimPath.vm_config_changeVersion,
                VimPath.vm_config_hardware_device,
                VimPath.vm_config_hardware_memoryMB,
                VimPath.vm_summary_config_numCpu,
                VimPath.vm_config_template,
                VimPath.vm_runtime_host,
                VimPath.vm_guest_net,
                VimPath.vm_guest_hostName,
                VimPath.vm_runtime_powerState,
                VimPath.vm_runtime_maxCpuUsage,
                VimPath.vm_runtime_maxMemoryUsage,
                VimPath.vm_summary_guest_ipAddress,
                VimPath.vm_summary_guest_hostName,
                VimPath.vm_snapshot_rootSnapshotList,
                VimPath.res_resourcePool,
                VimPath.vm_summary_config_guestFullName,
                VimPath.vm_summary_config_guestId
        ));

        PropertyFilterSpec filterSpec = new PropertyFilterSpec();
        filterSpec.getObjectSet().add(ospec);
        filterSpec.getPropSet().add(vmSpec);

        return filterSpec;
    }

    public PropertyFilterSpec createResourcesFilterSpec() {
        ObjectSpec ospec = new ObjectSpec();
        ospec.setObj(this.datacenter);
        ospec.setSkip(false);

        ospec.getSelectSet().addAll(buildFullTraversal());

        PropertySpec hostSpec = new PropertySpec();
        hostSpec.setType(VimNames.TYPE_HOST);
        hostSpec.getPathSet().addAll(Arrays.asList(
                VimPath.host_summary_hardware_memorySize,
                VimPath.host_summary_hardware_cpuMhz,
                VimPath.host_summary_hardware_numCpuCores,
                VimPath.host_summary_hardware_uuid,
                VimPath.host_summary_runtime_inMaintenanceMode,
                VimPath.host_parent,
                VimPath.host_datastore,
                VimPath.host_network,
                VimNames.PROPERTY_NAME,
                VimPath.host_summary_hardware_numNics,
                VimPath.host_summary_hardware_numCpuPkgs,
                VimPath.host_summary_hardware_vendor,
                VimPath.host_summary_hardware_model,
                VimPath.host_summary_hardware_cpuModel,
                VimPath.host_config_network_pnic,
                VimPath.host_config_hyperThread_active,
                VimPath.host_config_hyperThread_available
        ));

        PropertySpec rpSpec = new PropertySpec();
        rpSpec.setType(VimNames.TYPE_RESOURCE_POOL);
        rpSpec.getPathSet().addAll(Arrays.asList(
                VimPath.rp_summary_config_memoryAllocation_limit,
                VimPath.rp_runtime_memory_reservationUsed,
                VimPath.rp_runtime_memory_maxUsage,
                VimPath.rp_runtime_cpu_reservationUsed,
                VimPath.rp_runtime_cpu_maxUsage,
                VimNames.PROPERTY_PARENT,
                VimNames.PROPERTY_OWNER,
                VimNames.PROPERTY_NAME
        ));

        PropertySpec clusterSpec = new PropertySpec();
        clusterSpec.setType(VimNames.TYPE_COMPUTE_RESOURCE);
        clusterSpec.getPathSet().addAll(Arrays.asList(
                VimPath.res_summary_numCpuCores,
                VimPath.res_summary_totalCpu,
                VimPath.res_summary_totalMemory,
                VimPath.res_resourcePool,
                VimPath.res_configurationEx,
                VimPath.res_host,
                VimPath.res_datastore,
                VimPath.res_network,
                VimNames.PROPERTY_NAME
        ));

        PropertySpec dsSpec = new PropertySpec();
        dsSpec.setType(VimNames.TYPE_DATASTORE);
        dsSpec.getPathSet().addAll(Arrays.asList(
                VimPath.ds_summary_type,
                VimPath.ds_summary_freeSpace,
                VimPath.ds_summary_capacity,
                VimPath.ds_summary_url,
                VimNames.PROPERTY_NAME
        ));

        PropertySpec netSpec = new PropertySpec();
        netSpec.setType(VimNames.TYPE_NETWORK);
        netSpec.getPathSet().addAll(Arrays.asList(
                VimNames.PROPERTY_NAME,
                VimPath.net_summary
        ));

        PropertySpec pgSpec = new PropertySpec();
        pgSpec.setType(VimNames.TYPE_PORTGROUP);
        pgSpec.getPathSet().addAll(Arrays.asList(
                VimPath.pg_config_distributedVirtualSwitch,
                VimPath.pg_config_key,
                VimNames.PROPERTY_NAME
        ));

        PropertySpec dvsSpec = new PropertySpec();
        dvsSpec.setType(VimNames.TYPE_DVS);
        dvsSpec.getPathSet().addAll(Arrays.asList(
                VimNames.PROPERTY_NAME,
                VimPath.dvs_uuid
        ));

        PropertySpec folderSpec = new PropertySpec();
        folderSpec.setType(VimNames.TYPE_FOLDER);
        // TODO: Select only required properties
        folderSpec.setAll(true);

        PropertyFilterSpec filterSpec = new PropertyFilterSpec();
        filterSpec.getObjectSet().add(ospec);
        filterSpec.getPropSet().add(hostSpec);
        filterSpec.getPropSet().add(rpSpec);
        filterSpec.getPropSet().add(clusterSpec);
        filterSpec.getPropSet().add(dsSpec);
        filterSpec.getPropSet().add(netSpec);
        filterSpec.getPropSet().add(pgSpec);
        filterSpec.getPropSet().add(dvsSpec);
        filterSpec.getPropSet().add(folderSpec);
        return filterSpec;
    }

    public Iterable<List<ObjectContent>> retrieveObjects(
            PropertyFilterSpec spec) throws RuntimeFaultFaultMsg {
        ManagedObjectReference pc = createPropertyCollector();

        return () -> new ObjectContentIterator(pc, spec);
    }

    /**
     * Retrieve the list of storage profiles from the server.
     */
    public List<PbmProfile> retrieveStoragePolicies()
            throws com.vmware.pbm.RuntimeFaultFaultMsg, InvalidArgumentFaultMsg {
        // 1 Get PBM Profile Manager
        ManagedObjectReference profileMgr = this.connection.getPbmServiceInstanceContent()
                .getProfileManager();

        // 2 Retrieve the list of profile identifiers.
        PbmProfileResourceType pbmProfileResourceType = new PbmProfileResourceType();
        pbmProfileResourceType.setResourceType(PbmProfileResourceTypeEnum.STORAGE.value());
        List<PbmProfileId> profileIds = this.connection.getPbmPort()
                .pbmQueryProfile(profileMgr, pbmProfileResourceType, PbmProfileCategoryEnum.REQUIREMENT.value());

        // 3 Retrieve the list of storage profiles.
        if (profileIds != null && !profileIds.isEmpty()) {
            return this.connection.getPbmPort().pbmRetrieveContent(profileMgr, profileIds);
        }

        return new ArrayList<>();
    }

    /**
     * Retrieves the storage policies the given disk is associated with.
     * The query to pbm service needs vm moref and disk key to get the storage policies.
     * This method can be used when we have less number of disks whose storage policies
     * needs to be retrieved.
     *
     * @param vmMoref the moref of the vm in which disk is present
     * @param diskKey the integer disk key
     * @return the list of pbmprofile objects or an empty list if there's no associated storage policy.
     * @throws com.vmware.pbm.RuntimeFaultFaultMsg if there's any error while querying the policies.
     * @throws PbmFaultFaultMsg if there's any error while querying the policies.
     * @throws InvalidArgumentFaultMsg if there's any error while converting profile ids to profiles.
     */
    public List<PbmProfile> retrieveStoragePoliciesforDisk(ManagedObjectReference vmMoref, int diskKey)
            throws com.vmware.pbm.RuntimeFaultFaultMsg, PbmFaultFaultMsg, InvalidArgumentFaultMsg {
        List<PbmProfile> result = new ArrayList<>();
        // 1 Get PBM Profile Manager
        ManagedObjectReference profileMgr = this.connection.getPbmServiceInstanceContent()
                .getProfileManager();
        PbmServerObjectRef pbmServerObjectRef = new PbmServerObjectRef();
        pbmServerObjectRef.setKey(ClientUtils.createVMDiskkey(vmMoref,diskKey));
        pbmServerObjectRef.setObjectType(VSphereConstants.VSPHERE_VIRTUAL_DISK_ID);
        List<PbmProfileId> profileIds = this.connection.getPbmPort().pbmQueryAssociatedProfile(profileMgr, pbmServerObjectRef);

        if (CollectionUtils.isNotEmpty(profileIds)) {
            result = this.connection.getPbmPort().pbmRetrieveContent(profileMgr, profileIds);
        }
        return result;
    }

    /**
     * Retrieves the disks associated with a given storage policy.
     * The retrieved disks follow a formet :vm-moref:disk-key. for ex, vm-1235:1200
     * This method is useful when we are processing storage policies and need all disks
     * associated to a storage policy.
     *
     * @param profile the storage policy object
     * @return the list of disk keys as per the format given above or an empty list if there's no disk
     * associated.
     * @throws com.vmware.pbm.RuntimeFaultFaultMsg if there's any error while querying disks
     * @throws PbmFaultFaultMsg if there's any error while querying disks
     */
    public List<String> getAssociatedDisksForStoragePolicy(PbmProfile profile)
            throws com.vmware.pbm.RuntimeFaultFaultMsg, PbmFaultFaultMsg {
        List<String> disks = new ArrayList<>();
        // 1 Get PBM Profile Manager
        ManagedObjectReference profileMgr = this.connection.getPbmServiceInstanceContent()
                .getProfileManager();
        // 2 Query associated virtual disks
        List<PbmServerObjectRef> objects =
                this.connection.getPbmPort().pbmQueryAssociatedEntity(profileMgr, profile.getProfileId(), VSphereConstants.VSPHERE_VIRTUAL_DISK_ID);
        if (CollectionUtils.isNotEmpty(objects)) {
            for (PbmServerObjectRef serverObjectRef : objects) {
                disks.add(serverObjectRef.getKey());
            }
        }
        return disks;
    }

    /**
     * Retrieves list of datastore names that are compatible with the storage policy.
     */
    public List<String> getDatastores(PbmProfileId pbmProfileId)
            throws com.vmware.pbm.RuntimeFaultFaultMsg, PbmFaultFaultMsg {
        return ClientUtils.getDatastores(this.connection, pbmProfileId);
    }

    /**
     * Destroys the property collector associated with the vimPort type.
     *
     * @param pc      the property collector managed object reference
     * @param vimPort the vimport type binding.
     */
    private static void destroyCollectorQuietly(ManagedObjectReference pc, VimPortType vimPort) {
        try {
            vimPort.destroyCollector(pc);
        } catch (Throwable ignore) {

        }
    }

    /**
     * closes underlying connection. All objects associated with this client will become
     * invalid.
     */
    public void close() {
        this.connection.close();
    }

    private class ObjectContentIterator implements Iterator<List<ObjectContent>> {
        private final RetrieveOptions opts;
        private final ManagedObjectReference pc;
        private final PropertyFilterSpec spec;

        private boolean initialRetrievalCompleted = false;
        private RetrieveResult result;

        ObjectContentIterator(ManagedObjectReference pc, PropertyFilterSpec spec) {
            this.pc = pc;
            this.spec = spec;

            this.opts = new RetrieveOptions();
            this.opts.setMaxObjects(DEFAULT_FETCH_PAGE_SIZE);
        }

        @Override
        public boolean hasNext() {
            if (!this.initialRetrievalCompleted) {
                // has to check, may still return an empty first page
                return true;
            }

            return this.result != null && this.result.getToken() != null;
        }

        @Override
        public List<ObjectContent> next() {
            if (!this.initialRetrievalCompleted) {
                try {
                    this.result = getVimPort()
                            .retrievePropertiesEx(this.pc, Collections.singletonList(this.spec), this.opts);
                    this.initialRetrievalCompleted = true;
                } catch (RuntimeException e) {
                    destroyCollectorQuietly(this.pc, getVimPort());
                    throw e;
                } catch (Exception e) {
                    destroyCollectorQuietly(this.pc, getVimPort());
                    throw new RuntimeException(e);
                }

                return this.result != null ? this.result.getObjects() : Collections.emptyList();
            }

            try {
                this.result = getVimPort()
                        .continueRetrievePropertiesEx(this.pc, this.result.getToken());
            } catch (RuntimeException e) {
                destroyCollectorQuietly(this.pc, getVimPort());
                throw e;
            } catch (Exception e) {
                destroyCollectorQuietly(this.pc, getVimPort());
                throw new RuntimeException(e);
            }

            return this.result.getObjects();
        }
    }

    public static class ObjectUpdateIterator implements Iterator<UpdateSet> {
        private final ManagedObjectReference pc;

        private final WaitOptions opts;

        private String since;
        private UpdateSet lastResult;
        private boolean initialRetrievalCompleted;
        private VimPortType vimPort;

        ObjectUpdateIterator(ManagedObjectReference pc, VimPortType vimPort, String since) {
            this.pc = pc;
            this.vimPort = vimPort;
            this.since = since;
            // don't fetch too much data or block for too long
            this.opts = new WaitOptions();
            this.opts.setMaxWaitSeconds(1);
            this.opts.setMaxObjectUpdates(DEFAULT_FETCH_PAGE_SIZE);
        }

        public String getVersion() {
            return this.since;
        }

        public ManagedObjectReference getPropertyCollector() {
            return this.pc;
        }

        @Override
        public boolean hasNext() {
            if (!this.initialRetrievalCompleted) {
                // has to check, may still return an empty first page
                return true;
            }
            return (null != this.lastResult && null != this.lastResult.isTruncated() &&
                    this.lastResult.isTruncated());
        }

        @Override
        public UpdateSet next() {
            try {
                UpdateSet result = this.vimPort.waitForUpdatesEx(this.pc, this.since, this.opts);
                if (null != result) {
                    this.since = result.getVersion();
                }
                this.initialRetrievalCompleted = true;
                this.lastResult = result;
                return result != null ? result : new UpdateSet();
            } catch (Exception e) {
                destroyCollectorQuietly(this.pc, this.vimPort);
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Utility method that crosschecks the availability of independent disks in vSphere.
     */
    public List<String> queryDisksAvailabilityinVSphere(Map<String, Object> diskInfoInLocalIndex) {

        final List<String> unAvailableDisks = new ArrayList<>();

        diskInfoInLocalIndex.entrySet().stream().forEach(entry -> {
            DiskService.DiskState diskState = Utils.fromJson(entry.getValue(), DiskService.DiskState.class);

            String diskDirectoryPath = diskState.customProperties.get(CustomProperties.DISK_PARENT_DIRECTORY);
            String datastoreName = diskState.customProperties.get(CustomProperties.DISK_DATASTORE_NAME);


            HostDatastoreBrowserSearchSpec searchSpec = createHostDatastoreBrowserSearchSpecForDisk(diskState.id);

            try {
                this.getMoRef.entityProps(this.finder.datastore(datastoreName).object, "browser").entrySet().stream().forEach(item -> {
                    try {
                        ManagedObjectReference hostBrowser = (ManagedObjectReference) item.getValue();

                        ManagedObjectReference task = connection.getVimPort().searchDatastoreSubFoldersTask
                                (hostBrowser, diskDirectoryPath, searchSpec);

                        TaskInfo info = VimUtils.waitTaskEnd(connection, task);
                        ArrayOfHostDatastoreBrowserSearchResults searchResult =
                                (ArrayOfHostDatastoreBrowserSearchResults) info.getResult();

                        if (searchResult == null) {
                            // Folder is deleted.
                            unAvailableDisks.add(entry.getKey());
                        } else {
                            searchResult.getHostDatastoreBrowserSearchResults().stream().forEach(result -> {
                                // Folder is present but the vmdk file is deleted.
                                if (CollectionUtils.isEmpty(result.getFile())) {
                                    unAvailableDisks.add(entry.getKey());
                                }
                            });
                        }
                    } catch (InvalidPropertyFaultMsg | RuntimeFaultFaultMsg | InvalidCollectorVersionFaultMsg | FileFaultFaultMsg | InvalidDatastoreFaultMsg ex) {
                        logger.info("Unable to get the availability status for " + entry.getKey());
                    }
                });
            } catch (InvalidPropertyFaultMsg | RuntimeFaultFaultMsg | FinderException ex) {
                logger.info("Unable to find the datastore : " + datastoreName);
            }
        });
        return unAvailableDisks;
    }

    /**
     * Create search specification that searches for exact disk name.
     */
    private HostDatastoreBrowserSearchSpec createHostDatastoreBrowserSearchSpecForDisk(String diskName) {
        VmDiskFileQueryFilter vdiskFilter = new VmDiskFileQueryFilter();
        VmDiskFileQuery fQuery = new VmDiskFileQuery();
        fQuery.setFilter(vdiskFilter);


        HostDatastoreBrowserSearchSpec searchSpec = new HostDatastoreBrowserSearchSpec();
        searchSpec.getQuery().add(fQuery);
        FileQueryFlags flag = new FileQueryFlags();
        flag.setFileOwner(true);
        flag.setFileSize(true);
        flag.setFileType(true);
        flag.setModification(true);
        searchSpec.setDetails(flag);
        searchSpec.getMatchPattern().add(diskName + ".vmdk");

        return searchSpec;
    }
}
