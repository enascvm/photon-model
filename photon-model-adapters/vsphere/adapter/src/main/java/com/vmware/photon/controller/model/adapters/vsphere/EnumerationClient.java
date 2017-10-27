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
import java.util.Iterator;
import java.util.List;

import com.vmware.pbm.InvalidArgumentFaultMsg;
import com.vmware.pbm.PbmFaultFaultMsg;
import com.vmware.pbm.PbmProfile;
import com.vmware.pbm.PbmProfileId;
import com.vmware.pbm.PbmProfileResourceType;
import com.vmware.pbm.PbmProfileResourceTypeEnum;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimPath;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BaseHelper;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
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
import com.vmware.vim25.TraversalSpec;
import com.vmware.vim25.UpdateSet;
import com.vmware.vim25.WaitOptions;

/**
 * A client is bound to a datacenter.
 */
public class EnumerationClient extends BaseHelper {
    public static final int DEFAULT_FETCH_PAGE_SIZE = 100;

    private final ManagedObjectReference datacenter;

    public EnumerationClient(Connection connection, ComputeStateWithDescription parent) {
        this(connection, parent, null);
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
    }

    public ManagedObjectReference getDatacenter() {
        return this.datacenter;
    }

    private ManagedObjectReference createPropertyCollector() throws RuntimeFaultFaultMsg {
        ManagedObjectReference pc = this.connection.getServiceContent().getPropertyCollector();
        return getVimPort().createPropertyCollector(pc);
    }

    private ManagedObjectReference createPropertyCollectorWithFilter(PropertyFilterSpec spec)
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
                VimPath.vm_snapshot_rootSnapshotList
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
                VimPath.host_parent,
                VimPath.host_datastore,
                VimPath.host_network,
                VimNames.PROPERTY_NAME
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

        PropertyFilterSpec filterSpec = new PropertyFilterSpec();
        filterSpec.getObjectSet().add(ospec);
        filterSpec.getPropSet().add(hostSpec);
        filterSpec.getPropSet().add(rpSpec);
        filterSpec.getPropSet().add(clusterSpec);
        filterSpec.getPropSet().add(dsSpec);
        filterSpec.getPropSet().add(netSpec);
        filterSpec.getPropSet().add(pgSpec);
        filterSpec.getPropSet().add(dvsSpec);
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
                .pbmQueryProfile(profileMgr, pbmProfileResourceType, null);

        // 3 Retrieve the list of storage profiles.
        if (profileIds != null && !profileIds.isEmpty()) {
            return this.connection.getPbmPort().pbmRetrieveContent(profileMgr, profileIds);
        }

        return new ArrayList<>();
    }

    /**
     * Retrieves list of datastore names that are compatible with the storage policy.
     */
    public List<String> getDatastores(PbmProfileId pbmProfileId)
            throws com.vmware.pbm.RuntimeFaultFaultMsg, PbmFaultFaultMsg {
        return ClientUtils.getDatastores(this.connection, pbmProfileId);
    }

    private void destroyCollectorQuietly(ManagedObjectReference pc) {
        try {
            getVimPort().destroyCollector(pc);
        } catch (Throwable ignore) {

        }
    }

    public Iterable<UpdateSet> pollForUpdates(PropertyFilterSpec spec)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        ManagedObjectReference pc = createPropertyCollectorWithFilter(spec);

        return () -> new ObjectUpdateIterator(pc);
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
                    destroyCollectorQuietly(this.pc);
                    throw e;
                } catch (Exception e) {
                    destroyCollectorQuietly(this.pc);
                    throw new RuntimeException(e);
                }

                return this.result != null ? this.result.getObjects() : Collections.emptyList();
            }

            try {
                this.result = getVimPort()
                        .continueRetrievePropertiesEx(this.pc, this.result.getToken());
            } catch (RuntimeException e) {
                destroyCollectorQuietly(this.pc);
                throw e;
            } catch (Exception e) {
                destroyCollectorQuietly(this.pc);
                throw new RuntimeException(e);
            }

            return this.result.getObjects();
        }
    }

    private class ObjectUpdateIterator implements Iterator<UpdateSet> {
        private final ManagedObjectReference pc;

        private final WaitOptions opts;

        private String since;

        ObjectUpdateIterator(ManagedObjectReference pc) {
            this.pc = pc;

            // don't fetch too much data or block for too long
            this.opts = new WaitOptions();
            this.opts.setMaxWaitSeconds(10);
            this.opts.setMaxObjectUpdates(DEFAULT_FETCH_PAGE_SIZE);
        }

        @Override
        public boolean hasNext() {
            // updates are never exhausted, one must break the loop in other way
            return true;
        }

        @Override
        public UpdateSet next() {
            try {
                UpdateSet result = getVimPort().waitForUpdatesEx(this.pc, this.since, this.opts);
                this.since = result.getVersion();
                return result;
            } catch (Exception e) {
                destroyCollectorQuietly(this.pc);
                throw new RuntimeException(e);
            }
        }
    }
}
