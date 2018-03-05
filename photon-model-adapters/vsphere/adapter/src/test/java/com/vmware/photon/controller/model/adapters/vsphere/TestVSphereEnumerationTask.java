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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.model.ComputeProperties.CUSTOM_PROP_STORAGE_SHARED;
import static com.vmware.photon.controller.model.ComputeProperties.RESOURCE_TYPE_KEY;

import java.io.IOException;
import java.net.URI;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.junit.Assume;
import org.junit.Test;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.RegionEnumerationResponse;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.LifecycleState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

public class TestVSphereEnumerationTask extends BaseVSphereAdapterTest {

    private static final long QUERY_TASK_EXPIRY_MICROS = TimeUnit.MINUTES.toMicros(2);
    private ComputeDescription computeHostDescription;

    protected ComputeState computeHost;
    protected EndpointState endpoint;

    @Test
    public void testRefresh() throws Throwable {
        // Create a resource pool where the VM will be housed
        this.resourcePool = createResourcePool();

        this.auth = createAuth();

        this.computeHostDescription = createComputeDescription();
        this.computeHost = createComputeHost(this.computeHostDescription);

        // Always create endpoint, so that there is no bogus
        // endpointLink. This enumeration task is started on a real
        // endpoint. This will also help tango based adapters
        // as the existence of endpoint is more critical there to
        // extract data collector identifier.
        EndpointState ep = createEndpointState(this.computeHost, this.computeHostDescription);
        this.endpoint = TestUtils.doPost(this.host, ep, EndpointState.class,
                UriUtils.buildUri(this.host, EndpointService.FACTORY_LINK));

        refreshAndRetire();

        if (!isMock()) {
            ComputeState vm = findRandomVm();
            assertInternalPropertiesSet(vm);
            assertNotNull(vm.endpointLink);
            assertNotNull(vm.tenantLinks);
        }

        captureFactoryState("initial");

        String aComputeLink = null;
        String anUsedHostLink = null;
        String anUnusedHostLink = null;

        if (!isMock()) {
            // clone a random compute and save it under different id
            ComputeState randVm = findRandomVm();

            ComputeState vm = Utils.clone(randVm);
            vm.documentSelfLink = null;
            vm.id = "fake-vm-" + vm.id;
            vm.documentSelfLink = null;

            vm = TestUtils.doPost(this.host, vm,
                    ComputeState.class,
                    UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
            aComputeLink = vm.documentSelfLink;

            ComputeState randomHost = findRandomHost();

            {
                ComputeState host = Utils.clone(randomHost);
                host.documentSelfLink = null;
                host.powerState = PowerState.ON;
                host.id = "fake-host-" + host.id;
                host.documentSelfLink = null;

                host = TestUtils.doPost(this.host, host,
                        ComputeState.class,
                        UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
                anUsedHostLink = host.documentSelfLink;

                ComputeState update = new ComputeState();
                update.customProperties = new HashMap<>();
                update.customProperties.put(ComputeProperties.PLACEMENT_LINK, host.documentSelfLink);

                TestUtils.doPatch(this.host, update, ComputeState.class,
                        UriUtils.buildUri(this.host, randVm.documentSelfLink));
            }

            {
                ComputeState host = Utils.clone(randomHost);
                host.documentSelfLink = null;
                host.powerState = PowerState.ON;
                host.id = "fake-host-unused" + host.id;
                host.documentSelfLink = null;
                host = TestUtils.doPost(this.host, host,
                        ComputeState.class,
                        UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
                anUnusedHostLink = host.documentSelfLink;
            }

        }
        // do a second refresh to test update path
        refreshAndRetire();

        captureFactoryState("updated");
        snapshotFactoryState("metrics", ResourceMetricsService.class);

        if (aComputeLink != null) {
            // the second enumeration marked the fake vm as retired
            Operation op = Operation.createGet(this.host, aComputeLink);
            op = this.host.waitForResponse(op);
            ComputeState compute = op.getBody(ComputeState.class);
            assertEquals(compute.lifecycleState, LifecycleState.RETIRED);
            assertEquals(PowerState.OFF, compute.powerState);
        }

        if (anUsedHostLink != null) {
            // the second enumeration marked the fake vm as retired
            Operation op = Operation.createGet(this.host, anUsedHostLink);
            op = this.host.waitForResponse(op);
            ComputeState compute = op.getBody(ComputeState.class);
            assertEquals(compute.lifecycleState, LifecycleState.RETIRED);
            assertEquals(PowerState.OFF, compute.powerState);
        }

        if (anUnusedHostLink != null) {
            // the unused host is wiped out unconditionally
            Operation op = Operation.createGet(this.host, anUnusedHostLink);
            op = this.host.waitForResponse(op);
            assertEquals(op.getStatusCode(), 404);
        }

        verifyDatastoreAndStoragePolicy();
        if (!isMock()) {
            verifyCIGapForComputeResourcesAndVMs();
            verifyCIGapForDatacenterOrFolder(VimNames.TYPE_DATACENTER);
            verifyCIGapForDatacenterOrFolder(VimNames.TYPE_FOLDER);
            verifyCIGapForDatastore();
            verifyCIGapForComputeResource();
        }
    }

    @Test
    public void testGetAvailableRegions() throws Throwable {
        Assume.assumeFalse(isMock());

        startAdditionalServices();

        this.host.waitForServiceAvailable(VSphereUriPaths.VSPHERE_REGION_ENUMERATION_ADAPTER_SERVICE);

        URI uri = UriUtils.buildUri(
                ServiceHost.LOCAL_HOST,
                host.getPort(),
                UriPaths.AdapterTypePath.REGION_ENUMERATION_ADAPTER.adapterLink(
                        PhotonModelConstants.EndpointType.vsphere.toString().toLowerCase()), null);

        Operation post = Operation.createPost(uri);
        post.setBody(createEndpoint((a) -> { }, (b) -> { }));

        Operation operation = host.getTestRequestSender().sendAndWait(post);
        RegionEnumerationResponse result = operation.getBody(RegionEnumerationResponse.class);

        assertTrue(!result.regions.isEmpty());
    }

    private void verifyDatastoreAndStoragePolicy() throws Throwable {
        if (!isMock()) {
            // Query for storage description
            withTaskResults(queryForDatastore(), result -> {
                if (result.documentLinks.isEmpty()) {
                    assertTrue("Could not enumerate datastores", !result.documentLinks.isEmpty());
                } else {
                    StorageDescriptionService.StorageDescription sd = Utils
                            .fromJson(result.documents.get(result.documentLinks.get(0)),
                                    StorageDescriptionService.StorageDescription.class);
                    assertNotNull(sd.tagLinks);
                    assertNotNull(sd.customProperties.get(CUSTOM_PROP_STORAGE_SHARED));
                }
            });

            // Query for storage policy resource group
            withTaskResults(queryForStoragePolicy(), result -> {
                if (result.documentLinks.isEmpty()) {
                    assertTrue("Could not enumerate storage policy", !result.documentLinks.isEmpty());
                } else {
                    ResourceGroupService.ResourceGroupState rg = Utils
                            .fromJson(result.documents.get(result.documentLinks.get(0)),
                                    ResourceGroupService.ResourceGroupState.class);
                    assertNotNull(rg.name);
                    assertNotNull(rg.customProperties);
                    assertNotNull(rg.customProperties.get(RESOURCE_TYPE_KEY));
                    assertNotNull(rg.customProperties.get(ComputeProperties.ENDPOINT_LINK_PROP_NAME));
                }
            });
        }
    }

    private void verifyCIGapForComputeResourcesAndVMs() throws Throwable {
        ComputeState cd = findRandomVm();
        assertNotNull(cd.customProperties.get(CustomProperties.VM_SOFTWARE_NAME));
        ComputeState host = findRandomHost();
        assertNotNull(host.customProperties.get(CustomProperties.MODEL_NAME));
        assertNotNull(host.customProperties.get(CustomProperties.MANUFACTURER));
    }

    private void verifyCIGapForDatacenterOrFolder(String type) {
        Query.Builder builder = Query.Builder.create()
                .addKindFieldClause(ResourceGroupService.ResourceGroupState.class)
                .addCompositeFieldClause(ResourceGroupService.ResourceGroupState.FIELD_NAME_CUSTOM_PROPERTIES,
                        CustomProperties.TYPE, type);

        QueryTask task = QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();

        withTaskResults(task, result -> assertTrue(result.documentCount != 0));
    }

    private void verifyCIGapForDatastore() {
        withTaskResults(queryForDatastore(), result -> {
            if (result.documentLinks.isEmpty()) {
                assertTrue("Could not enumerate Datastore", !result.documentLinks.isEmpty());
            } else {
                int randInt = getRandomIntWithinBounds(result.documentCount.intValue());
                StorageDescriptionService.StorageDescription sd = Utils
                        .fromJson(result.documents.get(result.documentLinks.get(randInt)),
                                StorageDescriptionService.StorageDescription.class);

                assertNotNull(sd.customProperties.get(CustomProperties.DS_PATH), sd.customProperties.get(CustomProperties.DS_PATH));
                assertNotNull(sd.customProperties.get(CustomProperties.PROPERTY_NAME), sd.customProperties.get(CustomProperties.PROPERTY_NAME));
            }
        });
    }

    private void verifyCIGapForComputeResource() {
        Query.Builder builder = Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        CustomProperties.TYPE, VimNames.TYPE_CLUSTER_COMPUTE_RESOURCE);
        QueryTask task = QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();
        withTaskResults(task, result -> {
            if (result.documentLinks.isEmpty()) {
                assertTrue("Could not enumerate Compute Cluster Resource", !result.documentLinks.isEmpty());
            } else {
                int randInt = getRandomIntWithinBounds(result.documentCount.intValue());
                ComputeState sd = Utils
                        .fromJson(result.documents.get(result.documentLinks.get(randInt)),
                                ComputeState.class);

                assertNotNull(sd.customProperties.get(CustomProperties.CR_VSAN_CONFIG_ID), sd.customProperties.get(CustomProperties.CR_VSAN_CONFIG_ID));
                assertNotNull(sd.customProperties.get(CustomProperties.CR_IS_VSAN_ENABLED), sd.customProperties.get(CustomProperties.CR_IS_VSAN_ENABLED));
            }
        });
    }

    protected void captureFactoryState(String marker)
            throws ExecutionException, InterruptedException, IOException {
        snapshotFactoryState(marker, ComputeService.class);
        snapshotFactoryState(marker, ComputeDescriptionService.class);
        snapshotFactoryState(marker, ResourcePoolService.class);
        snapshotFactoryState(marker, StorageDescriptionService.class);
        snapshotFactoryState(marker, NetworkService.class);
        snapshotFactoryState(marker, TagService.class);
        snapshotFactoryState(marker, NetworkInterfaceService.class);
        snapshotFactoryState(marker, SubnetService.class);
        snapshotFactoryState(marker, ResourceGroupService.class);
    }

    private ComputeState findRandomVm()
            throws InterruptedException, TimeoutException, ExecutionException {
        Query q = Query.Builder.create()
                .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES, CustomProperties.TYPE,
                        VimNames.TYPE_VM)
                .addKindFieldClause(ComputeState.class)
                .build();

        QueryTask qt = QueryTask.Builder.createDirectTask()
                .setQuery(q)
                .addOption(QueryOption.EXPAND_CONTENT)
                .build();

        Operation op = QueryUtils.createQueryTaskOperation(this.host, qt, ServiceTypeCluster
                .INVENTORY_SERVICE);

        QueryTask result = this.host.waitForResponse(op).getBody(QueryTask.class);

        Object firstResult = result.results.documents.values().iterator().next();
        return Utils.fromJson(firstResult, ComputeState.class);
    }

    private ComputeState findRandomHost()
            throws InterruptedException, TimeoutException, ExecutionException {
        Query q = Query.Builder.create()
                .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES, CustomProperties.TYPE,
                        VimNames.TYPE_HOST)
                .addKindFieldClause(ComputeState.class)
                .build();

        QueryTask qt = QueryTask.Builder.createDirectTask()
                .setQuery(q)
                .addOption(QueryOption.EXPAND_CONTENT)
                .build();

        Operation op = QueryUtils.createQueryTaskOperation(this.host, qt, ServiceTypeCluster
                .INVENTORY_SERVICE);

        QueryTask result = this.host.waitForResponse(op).getBody(QueryTask.class);

        Object firstResult = result.results.documents.values().iterator().next();
        return Utils.fromJson(firstResult, ComputeState.class);
    }

    protected void refreshAndRetire() throws Throwable {
        enumerateComputes(this.computeHost, this.endpoint, EnumSet.of(TaskOption.PRESERVE_MISSING_RESOUCES));
    }

    private QueryTask queryForDatastore() {
        Query.Builder builder = Query.Builder.create()
                .addFieldClause(StorageDescriptionService.StorageDescription.FIELD_NAME_ADAPTER_REFERENCE,
                        getAdapterManagementReference())
                .addFieldClause(StorageDescriptionService.StorageDescription
                        .FIELD_NAME_REGION_ID, this.datacenterId)
                .addCaseInsensitiveFieldClause(StorageDescriptionService.StorageDescription.FIELD_NAME_NAME,
                        getDataStoreName(),
                        QueryTask.QueryTerm.MatchType.TERM, Query.Occurance.MUST_OCCUR);
        QueryUtils.addEndpointLink(builder, StorageDescriptionService.StorageDescription.class,
                this.computeHost.endpointLink);
        QueryUtils.addTenantLinks(builder, this.computeHost.tenantLinks);

        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();
    }

    private QueryTask queryForStoragePolicy() {
        Query.Builder builder = Query.Builder.create()
                .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        RESOURCE_TYPE_KEY, "STORAGE")
                .addKindFieldClause(ResourceGroupService.ResourceGroupState.class);
        QueryUtils.addTenantLinks(builder, this.computeHost.tenantLinks);

        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();
    }

    private void withTaskResults(QueryTask task, Consumer<ServiceDocumentQueryResult> handler) {
        task.querySpec.options = EnumSet.of(QueryOption.EXPAND_CONTENT);
        task.documentExpirationTimeMicros = Utils.fromNowMicrosUtc(QUERY_TASK_EXPIRY_MICROS);

        Operation op = QueryUtils.createQueryTaskOperation(this.host, task, ServiceTypeCluster
                .INVENTORY_SERVICE);

        QueryTask result = this.host.waitForResponse(op).getBody(QueryTask.class);
        handler.accept(result.results);
    }

    private String getDataStoreName() {
        if (this.dataStoreId != null) {
            return this.dataStoreId
                    .substring(this.dataStoreId.lastIndexOf("/") + 1, this.dataStoreId.length());
        }
        return "";
    }

    private int getRandomIntWithinBounds(int bound) {
        return new Random().nextInt(bound);
    }
}
