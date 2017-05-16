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

package com.vmware.photon.controller.model.adapters.azure.enumeration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.model.ModelUtils.createSecurityGroup;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.AZURE_SECURITY_GROUP_NAME;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.SHARED_NETWORK_NIC_SPEC;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.assertResourceExists;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultAuthCredentials;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultComputeHost;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultDiskState;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultEndpointState;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultResourceGroupState;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultResourcePool;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultStorageAccountDescription;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultVMResource;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createResourceGroupWithSharedNetwork;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.deleteServiceDocument;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.deleteVMs;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.generateName;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.getAzureSecurityGroup;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.getAzureVMCount;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.getAzureVirtualMachine;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.getAzureVirtualNetwork;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.initializeNicSpecs;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.randomString;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.updateAzureSecurityGroup;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.updateAzureVirtualMachine;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.updateAzureVirtualNetwork;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.STORAGE_USED_BYTES;
import static com.vmware.photon.controller.model.query.QueryUtils.QueryTemplate.waitToComplete;

import java.net.URI;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.credentials.AzureEnvironment;
import com.microsoft.azure.management.compute.ComputeManagementClient;
import com.microsoft.azure.management.compute.ComputeManagementClientImpl;
import com.microsoft.azure.management.compute.models.VirtualMachine;
import com.microsoft.azure.management.network.NetworkManagementClient;
import com.microsoft.azure.management.network.NetworkManagementClientImpl;
import com.microsoft.azure.management.network.models.NetworkSecurityGroup;
import com.microsoft.azure.management.network.models.VirtualNetwork;
import com.microsoft.azure.management.resources.ResourceManagementClient;
import com.microsoft.azure.management.resources.ResourceManagementClientImpl;
import com.microsoft.azure.management.storage.StorageManagementClient;
import com.microsoft.azure.management.storage.StorageManagementClientImpl;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.azure.AzureAdapters;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.ResourceGroupStateType;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.AzureNicSpecs;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.AzureNicSpecs.NetSpec;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService;
import com.vmware.photon.controller.model.query.QueryStrategy;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTop;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState.SubStage;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService.ResourceEnumerationTaskState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceTaskCollectionStage;
import com.vmware.photon.controller.model.tasks.monitoring.StatsUtil;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 * PRE-REQUISITE: An Azure Resource Manager VM named <b>EnumTestVM-DoNotDelete</b>, with diagnostics
 * enabled, is required for the stats collection on compute host to be successful.
 * <p>
 * NOTE: Testing pagination related changes requires manual setup due to account limits, slowness of
 * vm creation on azure (this slowness is on azure), and cost associated.
 * <p>
 * For manual tests use Azure CLI to create multiple VMs using this bash command line:
 * <p>
 * for i in {1..55}; do azure vm quick-create resourcegroup vm$i westus linux
 * canonical:UbuntuServer:12.04.3-LTS:12.04.201401270 azureuser Pa$$word% -z Standard_A0; done
 */
public class TestAzureEnumerationTask extends BaseModelTest {
    public static final String STALE_RG_NAME_PREFIX = "stalerg-";
    public static final String STALE_VM_NAME_PREFIX = "stalevm-";
    public static final String STALE_SA_NAME_PREFIX = "stalesa-";
    public static final String STALE_CONTAINER_NAME_PREFIX = "stalecontainer-";
    public static final String STALE_BLOB_NAME_PREFIX = "staleblob-";
    public static final String STALE_SG_NAME_PREFIX = "stalesg-";

    private static final int STALE_RG_COUNT = 3;
    private static final int STALE_VM_RESOURCES_COUNT = 5;
    private static final int STALE_STORAGE_ACCOUNTS_COUNT = 5;
    private static final int STALE_CONTAINERS_COUNT = 5;
    private static final int STALE_BLOBS_COUNT = 5;
    private static final int STALE_SECURITY_GROUPS_COUNT = 4;

    private static final String NETWORK_TAG_KEY_PREFIX = "vNetTagKey";
    private static final String NETWORK_TAG_VALUE = "vNetTagValue";
    private static final String VM_TAG_KEY_PREFIX = "VMTagKey";
    private static final String VM_TAG_VALUE = "VMTagValue";
    private static final String SG_TAG_KEY_PREFIX = "SGTagKey";
    private static final String SG_TAG_VALUE = "SGTagValue";

    // Shared Compute Host / End-point between test runs.
    private static ComputeState computeHost;
    private static EndpointState endpointState;

    public String clientID = "clientID";
    public String clientKey = "clientKey";
    public String subscriptionId = "subscriptionId";
    public String tenantId = "tenantId";

    public static String azureVMNamePrefix = "enumtest-";
    public static String azureVMName;
    public boolean isMock = true;
    public String mockedStorageAccountName = randomString(15);

    // object counts
    public int vmCount = 0;
    public int numberOfVMsToDelete = 0;

    // fields that are used across method calls, stash them as private fields
    private ComputeState vmState;
    private StorageDescription storageDescription;
    private ResourceGroupState resourceGroupState;
    private DiskState diskState;

    private ComputeManagementClient computeManagementClient;
    private ResourceManagementClient resourceManagementClient;
    private StorageManagementClient storageManagementClient;
    private NetworkManagementClient networkManagementClient;

    private String enumeratedComputeLink;

    private static final String CUSTOM_DIAGNOSTIC_ENABLED_VM = "EnumTestVM-DoNotDelete";

    private static AzureNicSpecs NIC_SPEC;

    @BeforeClass
    public static void setupClass() {
        azureVMName = generateName(azureVMNamePrefix);

        NIC_SPEC = initializeNicSpecs(azureVMName, false, true);
    }

    @Override
    @Before
    public void setUp() throws Exception {
        try {
            /*
             * Init Class-specific (shared between test runs) vars.
             *
             * NOTE: Ultimately this should go to @BeforeClass, BUT BasicReusableHostTestCase.HOST
             * is not accessible.
             */
            if (computeHost == null) {
                PhotonModelServices.startServices(this.host);
                PhotonModelMetricServices.startServices(this.host);
                PhotonModelTaskServices.startServices(this.host);
                PhotonModelAdaptersRegistryAdapters.startServices(this.host);
                AzureAdapters.startServices(this.host);

                this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
                this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
                this.host.waitForServiceAvailable(PhotonModelAdaptersRegistryAdapters.LINKS);
                this.host.waitForServiceAvailable(AzureAdapters.LINKS);

                // TODO: VSYM-992 - improve test/fix arbitrary timeout
                this.host.setTimeoutSeconds(600);

                // Create a resource pool where the VMs will be housed
                ResourcePoolState resourcePool = createDefaultResourcePool(this.host);

                AuthCredentialsServiceState authCredentials = createDefaultAuthCredentials(
                        this.host,
                        this.clientID,
                        this.clientKey,
                        this.subscriptionId,
                        this.tenantId);

                endpointState = createDefaultEndpointState(
                        this.host, authCredentials.documentSelfLink);

                // create a compute host for the Azure
                computeHost = createDefaultComputeHost(this.host, resourcePool.documentSelfLink,
                        endpointState);
            }

            azureVMName = azureVMName == null
                    ? generateName(azureVMNamePrefix)
                    : azureVMName;

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
            this.host.waitForServiceAvailable(AzureAdapters.LINKS);

            if (!this.isMock) {
                ApplicationTokenCredentials credentials = new ApplicationTokenCredentials(
                        this.clientID,
                        this.tenantId, this.clientKey, AzureEnvironment.AZURE);
                this.computeManagementClient = new ComputeManagementClientImpl(credentials);
                this.computeManagementClient.setSubscriptionId(this.subscriptionId);

                this.resourceManagementClient = new ResourceManagementClientImpl(credentials);
                this.resourceManagementClient.setSubscriptionId(this.subscriptionId);

                this.storageManagementClient = new StorageManagementClientImpl(credentials);
                this.storageManagementClient.setSubscriptionId(this.subscriptionId);

                this.networkManagementClient = new NetworkManagementClientImpl(credentials);
                this.networkManagementClient.setSubscriptionId(this.subscriptionId);
            }
        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    @After
    public void tearDown() throws InterruptedException {
        // try to delete the VMs
        if (this.vmState != null) {
            try {
                int baselineCount = getAzureVMCount(this.computeManagementClient) + 1;
                deleteVMs(this.host, this.vmState.documentSelfLink, this.isMock, baselineCount);
            } catch (Throwable deleteEx) {
                // just log and move on
                this.host.log(Level.WARNING, "Exception deleting VM - %s", deleteEx.getMessage());
            }
        }

        // try to delete the storage accounts
        if (this.storageDescription != null) {
            try {
                deleteServiceDocument(this.host, this.storageDescription.documentSelfLink);
            } catch (Throwable deleteEx) {
                this.host.log(Level.WARNING, "Exception deleting storage accounts - %s",
                        deleteEx.getMessage());
            }
        }

        // try to delete the storage containers
        if (this.resourceGroupState != null) {
            try {
                deleteServiceDocument(this.host, this.resourceGroupState.documentSelfLink);
            } catch (Throwable deleteEx) {
                this.host.log(Level.WARNING, "Exception deleting storage containers - %s",
                        deleteEx.getMessage());
            }
        }

        // try to delete the blobs
        if (this.diskState != null) {
            try {
                deleteServiceDocument(this.host, this.diskState.documentSelfLink);
            } catch (Throwable deleteEx) {
                this.host.log(Level.WARNING, "Exception deleting disk states - %s",
                        deleteEx.getMessage());
            }
        }
    }

    @Test
    public void testEnumeration() throws Throwable {

        this.storageDescription = createDefaultStorageAccountDescription(this.host,
                this.mockedStorageAccountName, computeHost, endpointState);

        this.resourceGroupState = createDefaultResourceGroupState(this.host,
                this.mockedStorageAccountName, computeHost, endpointState,
                ResourceGroupStateType.AzureResourceGroup);

        this.diskState = createDefaultDiskState(this.host, this.mockedStorageAccountName,
                this.mockedStorageAccountName, computeHost, endpointState);

        // create an Azure VM compute resource (this also creates a disk and a storage account)
        this.vmState = createDefaultVMResource(this.host, azureVMName,
                computeHost, endpointState, NIC_SPEC);

        // kick off a provision task to do the actual VM creation
        ProvisionComputeTaskState provisionTask = new ProvisionComputeTaskState();

        provisionTask.computeLink = this.vmState.documentSelfLink;
        provisionTask.isMockRequest = this.isMock;
        provisionTask.taskSubStage = SubStage.CREATING_HOST;

        ProvisionComputeTaskState outTask = TestUtils
                .doPost(this.host, provisionTask, ProvisionComputeTaskState.class,
                        UriUtils.buildUri(this.host, ProvisionComputeTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(ProvisionComputeTaskState.class, outTask.documentSelfLink);

        // Check resources have been created
        // expected VM count = 2 (1 compute host instance + 1 vm compute state)
        ProvisioningUtils.queryDocumentsAndAssertExpectedCount(this.host, 2,
                ComputeService.FACTORY_LINK, false);
        ProvisioningUtils.queryDocumentsAndAssertExpectedCount(this.host, 1,
                StorageDescriptionService.FACTORY_LINK, false);
        ProvisioningUtils.queryDocumentsAndAssertExpectedCount(this.host, 1,
                ResourceGroupService.FACTORY_LINK, false);
        ProvisioningUtils.queryDocumentsAndAssertExpectedCount(this.host, 1,
                DiskService.FACTORY_LINK, false);

        this.numberOfVMsToDelete++;

        if (this.isMock) {
            runEnumeration();
            deleteVMs(this.host, this.vmState.documentSelfLink, this.isMock, 1);
            this.vmState = null;
            ProvisioningUtils.queryDocumentsAndAssertExpectedCount(this.host, 1,
                    ComputeService.FACTORY_LINK, false);

            deleteServiceDocument(this.host, this.storageDescription.documentSelfLink);
            this.storageDescription = null;
            ProvisioningUtils.queryDocumentsAndAssertExpectedCount(this.host, 0,
                    StorageDescriptionService.FACTORY_LINK, true);

            deleteServiceDocument(this.host, this.resourceGroupState.documentSelfLink);
            this.resourceGroupState = null;
            ProvisioningUtils.queryDocumentsAndAssertExpectedCount(this.host, 0,
                    ResourceGroupService.FACTORY_LINK, false);

            deleteServiceDocument(this.host, this.diskState.documentSelfLink);
            this.diskState = null;
            ProvisioningUtils.queryDocumentsAndAssertExpectedCount(this.host, 0,
                    DiskService.FACTORY_LINK, false);
            return;
        }

        createStaleResource();

        tagAzureResources();

        // stale resources + 1 compute host instance + 1 vm compute state
        ProvisioningUtils
                .queryDocumentsAndAssertExpectedCount(this.host, STALE_VM_RESOURCES_COUNT + 2,
                        ComputeService.FACTORY_LINK, false);
        ProvisioningUtils.queryDocumentsAndAssertExpectedCount(this.host,
                STALE_STORAGE_ACCOUNTS_COUNT + 1, StorageDescriptionService.FACTORY_LINK, false);
        ProvisioningUtils.queryDocumentsAndAssertExpectedCount(this.host,
                STALE_CONTAINERS_COUNT + STALE_RG_COUNT + 1, ResourceGroupService.FACTORY_LINK,
                false);
        ProvisioningUtils.queryDocumentsAndAssertExpectedCount(this.host, STALE_BLOBS_COUNT + 1,
                DiskService.FACTORY_LINK, false);
        // 1 network per each stale vm resource + 1 network for original vm compute state.
        ProvisioningUtils.queryDocumentsAndAssertExpectedCount(this.host,
                STALE_VM_RESOURCES_COUNT + 1,
                NetworkService.FACTORY_LINK, false);
        // 1 subnet per network, 1 network per each stale vm resource + 1 subnet for the original
        // compute state.
        ProvisioningUtils.queryDocumentsAndAssertExpectedCount(this.host,
                STALE_VM_RESOURCES_COUNT + 1,
                SubnetService.FACTORY_LINK, false);
        ProvisioningUtils.queryDocumentsAndAssertExpectedCount(this.host,
                STALE_SECURITY_GROUPS_COUNT,
                SecurityGroupService.FACTORY_LINK, false);

        this.vmCount = getAzureVMCount(this.computeManagementClient);
        this.host.log(Level.INFO, "Initial VM Count: %d", this.vmCount);

        runEnumeration();

        assertRemoteResources();
        assertStaleResources();

        // VM count + 1 compute host instance
        this.vmCount = this.vmCount + 1;
        ServiceDocumentQueryResult result = ProvisioningUtils.queryDocumentsAndAssertExpectedCount(
                this.host, this.vmCount, ComputeService.FACTORY_LINK, false);

        // validate type field for enumerated VMs
        result.documents.entrySet().stream()
                .map(e -> Utils.fromJson(e.getValue(), ComputeState.class))
                .filter(c -> !c.documentSelfLink.equals(computeHost.documentSelfLink))
                .forEach(c -> assertEquals(ComputeType.VM_GUEST, c.type));

        // validate environment name field for enumerated VMs
        result.documents.entrySet().stream()
                .map(e -> Utils.fromJson(e.getValue(), ComputeState.class))
                .forEach(c -> assertEquals(ComputeDescription.ENVIRONMENT_NAME_AZURE,
                        c.environmentName));

        for (Entry<String, Object> key : result.documents.entrySet()) {
            ComputeState document = Utils.fromJson(key.getValue(), ComputeState.class);
            if (document.type == ComputeType.VM_GUEST) {
                assertNotNull(document.creationTimeMicros);
            } else if (document.type == ComputeType.VM_HOST) {
                assertNull(document.creationTimeMicros);
            }
            if (!document.documentSelfLink.equals(computeHost.documentSelfLink)
                    && !document.documentSelfLink.equals(this.vmState.documentSelfLink)
                    && document.id.toLowerCase()
                            .contains(CUSTOM_DIAGNOSTIC_ENABLED_VM.toLowerCase())) {
                this.enumeratedComputeLink = document.documentSelfLink;
                break;
            }
        }

        try {
            // Test stats for the VM that was just enumerated from Azure.
            this.host.log(Level.INFO, "Collecting stats for VM [%s]-[%s]",
                    CUSTOM_DIAGNOSTIC_ENABLED_VM, this.enumeratedComputeLink);
            this.host.setTimeoutSeconds(300);
            if (this.enumeratedComputeLink != null) {
                this.host.waitFor("Error waiting for VM stats", () -> {
                    try {
                        issueStatsRequest(this.enumeratedComputeLink, false);
                    } catch (Throwable t) {
                        return false;
                    }
                    return true;
                });
            }

            // Test stats for the compute host.
            this.host.log(Level.INFO, "Collecting stats for host [%s]",
                    computeHost.documentSelfLink);
            this.host.waitFor("Error waiting for host stats", () -> {
                try {
                    issueStatsRequest(computeHost.documentSelfLink, true);
                } catch (Throwable t) {
                    return false;
                }
                return true;
            });
        } catch (Throwable te) {
            this.host.log(Level.SEVERE, te.getMessage());
        }

        // delete vm directly on azure
        this.computeManagementClient.getVirtualMachinesOperations()
                .beginDelete(azureVMName, azureVMName);

        runEnumeration();

        assertResourceExists(this.host, ComputeService.FACTORY_LINK, azureVMName, false);

        // clean up
        this.vmState = null;
        this.resourceManagementClient.getResourceGroupsOperations().beginDelete(azureVMName);
    }

    // Add tags, that later should be discovered as part of first enumeration cycle.
    private void tagAzureResources() throws Exception {

        // tag v-Net
        VirtualNetwork vmNetwUpdate = getAzureVirtualNetwork(this.networkManagementClient,
                azureVMName,
                NIC_SPEC.network.name);

        Map<String, String> vmNetwTags = new HashMap<>();
        vmNetwTags.put(NETWORK_TAG_KEY_PREFIX + azureVMName, NETWORK_TAG_VALUE);
        vmNetwUpdate.setTags(vmNetwTags);

        updateAzureVirtualNetwork(this.networkManagementClient, azureVMName,
                NIC_SPEC.network.name, vmNetwUpdate);

        // tag VM
        VirtualMachine vmUpdate = getAzureVirtualMachine(
                this.computeManagementClient, azureVMName, azureVMName);

        Map<String, String> vmTags = new HashMap<>();
        vmTags.put(VM_TAG_KEY_PREFIX + azureVMName, VM_TAG_VALUE);
        vmUpdate.setTags(vmTags);

        updateAzureVirtualMachine(this.computeManagementClient, azureVMName,
                azureVMName, vmUpdate);

        // tag Security Group
        NetworkSecurityGroup sgUpdate = getAzureSecurityGroup(this.networkManagementClient,
                azureVMName, AZURE_SECURITY_GROUP_NAME);

        Map<String, String> sgTags = new HashMap<>();
        sgTags.put(SG_TAG_KEY_PREFIX + azureVMName, SG_TAG_VALUE);
        sgUpdate.setTags(sgTags);
        sgUpdate.setLocation(AzureTestUtil.AZURE_RESOURCE_GROUP_LOCATION);

        updateAzureSecurityGroup(this.networkManagementClient, azureVMName,
                AZURE_SECURITY_GROUP_NAME, sgUpdate);
    }

    // create stale resource states for deletion
    // these should be deleted as part of first enumeration cycle.
    private void createStaleResource() throws Throwable {

        createAzureResourceGroups(STALE_RG_COUNT);
        // No need to create stale networks or subnets since createAzureVMResources will create
        // such for us.
        createAzureVMResources(STALE_VM_RESOURCES_COUNT);
        createAzureStorageAccounts(STALE_STORAGE_ACCOUNTS_COUNT);
        createAzureStorageContainers(STALE_CONTAINERS_COUNT);
        createAzureBlobs(STALE_BLOBS_COUNT);
        createAzureSecurityGroups(STALE_SECURITY_GROUPS_COUNT);

    }

    // Assert that remote resources are enumerated and exists locally.
    private void assertRemoteResources() {
        assertResourceExists(this.host, NetworkService.FACTORY_LINK, NIC_SPEC.network.name,
                true);

        for (NetSpec subnet : NIC_SPEC.subnets) {
            assertResourceExists(this.host, SubnetService.FACTORY_LINK, subnet.name, true);
        }

        assertResourceExists(this.host, ResourceGroupService.FACTORY_LINK, azureVMName, true);

        assertResourceExists(this.host, SecurityGroupService.FACTORY_LINK,
                AZURE_SECURITY_GROUP_NAME, true);

        assertResourceExists(this.host, StorageDescriptionService.FACTORY_LINK, (azureVMName +
                "sa").replace("-", ""), true);

        assertResourceExists(this.host, DiskService.FACTORY_LINK, azureVMName + "-boot-disk",
                true);

        // Tags
        final Map<String, String> expectedTags = new HashMap<>();
        expectedTags.put(NETWORK_TAG_KEY_PREFIX + azureVMName, NETWORK_TAG_VALUE);
        expectedTags.put(VM_TAG_KEY_PREFIX + azureVMName, VM_TAG_VALUE);
        expectedTags.put(SG_TAG_KEY_PREFIX + azureVMName, SG_TAG_VALUE);

        final List<String> keysToLowerCase = expectedTags.keySet().stream()
                .map(String::toLowerCase)
                .collect(Collectors.toList());

        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(TagState.class)
                .addInClause(TagState.FIELD_NAME_KEY, keysToLowerCase)
                .addFieldClause(TagState.FIELD_NAME_EXTERNAL, Boolean.TRUE.toString());

        QueryStrategy<TagState> queryLocalTags = new QueryTop<>(
                this.host,
                qBuilder.build(),
                TagState.class,
                null)
                        .setMaxResultsLimit(expectedTags.size() + 1);

        List<TagState> tagStates = waitToComplete(
                queryLocalTags.collectDocuments(Collectors.toList()));

        assertEquals("TagStates were not discovered.", expectedTags.size(), tagStates.size());
        for (TagState tag : tagStates) {
            assertEquals(expectedTags.get(tag.key), tag.value);
        }
    }

    // Assert that stale resources were cleaned up.
    private void assertStaleResources() {
        // Resource groups.
        for (int i = 0; i < STALE_RG_COUNT; i++) {
            assertResourceExists(this.host, ResourceGroupService.FACTORY_LINK,
                    STALE_RG_NAME_PREFIX + i,
                    false);
        }

        // VMs
        for (int i = 0; i < STALE_VM_RESOURCES_COUNT; i++) {
            assertResourceExists(this.host, ComputeService.FACTORY_LINK, STALE_VM_NAME_PREFIX + i,
                    false);
        }

        // Storage accounts
        for (int i = 0; i < STALE_STORAGE_ACCOUNTS_COUNT; i++) {
            assertResourceExists(this.host, StorageDescriptionService.FACTORY_LINK,
                    STALE_SA_NAME_PREFIX
                            + i,
                    false);
        }

        // Storage containers
        for (int i = 0; i < STALE_CONTAINERS_COUNT; i++) {
            assertResourceExists(this.host, ResourceGroupService.FACTORY_LINK,
                    STALE_CONTAINER_NAME_PREFIX + i, false);
        }

        // Blobs
        for (int i = 0; i < STALE_BLOBS_COUNT; i++) {
            assertResourceExists(this.host, DiskService.FACTORY_LINK,
                    STALE_BLOB_NAME_PREFIX + i, false);
        }

        // Security Groups
        for (int i = 0; i < STALE_SECURITY_GROUPS_COUNT; i++) {
            assertResourceExists(this.host, SecurityGroupService.FACTORY_LINK,
                    STALE_SG_NAME_PREFIX + i, false);
        }
    }

    /**
     * Tests Azure virtual gateway enumeration using pre-created shared resource group with virtual
     * network and gateway.
     *
     * @throws Throwable
     *             throwable
     */
    @Test
    public void testEnumerateNetworkWithGateway() throws Throwable {

        // The test is only suitable for real (non-mocking env).
        Assume.assumeFalse(this.isMock);

        createResourceGroupWithSharedNetwork(this.resourceManagementClient,
                this.networkManagementClient, SHARED_NETWORK_NIC_SPEC);

        runEnumeration();

        // Post enumeration validate Gateway information is attached in NetworkState custom property
        validateVirtualNetworkGateways(SHARED_NETWORK_NIC_SPEC);
    }

    private void createAzureResourceGroups(int numOfResourceGroups) throws Throwable {
        for (int i = 0; i < numOfResourceGroups; i++) {
            String staleResourceGroupName = STALE_RG_NAME_PREFIX + i;
            createDefaultResourceGroupState(this.host, staleResourceGroupName,
                    computeHost, endpointState,
                    ResourceGroupStateType.AzureResourceGroup);
        }
    }

    private void createAzureVMResources(int numOfVMs) throws Throwable {
        for (int i = 0; i < numOfVMs; i++) {
            String staleVMName = STALE_VM_NAME_PREFIX + i;
            createDefaultVMResource(this.host, staleVMName, computeHost, endpointState, NIC_SPEC);
        }
    }

    private void runEnumeration() throws Throwable {
        ResourceEnumerationTaskState enumerationTaskState = new ResourceEnumerationTaskState();

        enumerationTaskState.endpointLink = endpointState.documentSelfLink;
        enumerationTaskState.tenantLinks = endpointState.tenantLinks;
        enumerationTaskState.parentComputeLink = computeHost.documentSelfLink;
        enumerationTaskState.enumerationAction = EnumerationAction.START;
        enumerationTaskState.adapterManagementReference = UriUtils
                .buildUri(AzureEnumerationAdapterService.SELF_LINK);
        enumerationTaskState.resourcePoolLink = computeHost.resourcePoolLink;
        if (this.isMock) {
            enumerationTaskState.options = EnumSet.of(TaskOption.IS_MOCK);
        }

        ResourceEnumerationTaskState enumTask = TestUtils
                .doPost(this.host, enumerationTaskState, ResourceEnumerationTaskState.class,
                        UriUtils.buildUri(this.host, ResourceEnumerationTaskService.FACTORY_LINK));

        this.host.waitFor("Error waiting for enumeration task", () -> {
            try {
                ResourceEnumerationTaskState state = this.host
                        .waitForFinishedTask(ResourceEnumerationTaskState.class,
                                enumTask.documentSelfLink);
                if (state != null) {
                    return true;
                }
            } catch (Throwable e) {
                return false;
            }
            return false;
        });
    }

    private void issueStatsRequest(String selfLink, boolean isComputeHost) throws Throwable {
        // spin up a stateless service that acts as the parent link to patch back to
        StatelessService parentService = new StatelessService() {
            @Override
            public void handleRequest(Operation op) {
                if (op.getAction() == Action.PATCH) {
                    if (!TestAzureEnumerationTask.this.isMock) {
                        ComputeStatsResponse resp = op.getBody(ComputeStatsResponse.class);
                        if (resp == null) {
                            TestAzureEnumerationTask.this.host.failIteration(
                                    new IllegalStateException("response was null."));
                            return;
                        }
                        if (resp.statsList.size() != 1) {
                            TestAzureEnumerationTask.this.host.failIteration(
                                    new IllegalStateException("response size was incorrect."));
                            return;
                        }
                        if (resp.statsList.get(0).statValues.size() == 0) {
                            TestAzureEnumerationTask.this.host
                                    .failIteration(new IllegalStateException(
                                            "incorrect number of metrics received."));
                            return;
                        }
                        if (!resp.statsList.get(0).computeLink.equals(selfLink)) {
                            TestAzureEnumerationTask.this.host
                                    .failIteration(new IllegalStateException(
                                            "Incorrect resourceReference returned."));
                            return;
                        }
                        // Verify all the stats are obtained
                        verifyStats(resp, isComputeHost);
                        // Persist stats on Verification Host for testing the computeHost stats.
                        URI persistStatsUri = UriUtils.buildUri(getHost(),
                                ResourceMetricsService.FACTORY_LINK);
                        ResourceMetricsService.ResourceMetrics resourceMetric = new ResourceMetricsService.ResourceMetrics();
                        resourceMetric.documentSelfLink = StatsUtil.getMetricKey(selfLink,
                                Utils.getNowMicrosUtc());
                        resourceMetric.entries = new HashMap<>();
                        resourceMetric.timestampMicrosUtc = Utils.getNowMicrosUtc();
                        for (String key : resp.statsList.get(0).statValues.keySet()) {
                            List<ServiceStat> stats = resp.statsList.get(0).statValues.get(key);
                            for (ServiceStat stat : stats) {
                                if (stat == null) {
                                    continue;
                                }
                                resourceMetric.entries.put(key, stat.latestValue);
                            }
                        }
                        TestAzureEnumerationTask.this.host.sendRequest(Operation
                                .createPost(persistStatsUri)
                                .setReferer(TestAzureEnumerationTask.this.host.getUri())
                                .setBodyNoCloning(resourceMetric));
                    }
                    TestAzureEnumerationTask.this.host.completeIteration();
                }
            }
        };
        String servicePath = UUID.randomUUID().toString();
        Operation startOp = Operation.createPost(UriUtils.buildUri(this.host, servicePath));
        this.host.startService(startOp, parentService);
        ComputeStatsRequest statsRequest = new ComputeStatsRequest();
        statsRequest.resourceReference = UriUtils.buildUri(this.host, selfLink);
        statsRequest.nextStage = SingleResourceTaskCollectionStage.UPDATE_STATS.name();
        statsRequest.isMockRequest = this.isMock;
        statsRequest.taskReference = UriUtils.buildUri(this.host, servicePath);
        this.host.sendAndWait(Operation.createPatch(UriUtils.buildUri(
                this.host, AzureUriPaths.AZURE_STATS_ADAPTER))
                .setBody(statsRequest)
                .setReferer(this.host.getUri()));
    }

    private void verifyStats(ComputeStatsResponse resp, boolean isComputeHost) {
        Set<String> obtainedMetricKeys = resp.statsList.get(0).statValues.keySet();
        // Check if at least one metric was returned by Azure.
        Assert.assertTrue("No metrics were returned.", obtainedMetricKeys.size() > 0);
        if (isComputeHost) {
            Assert.assertTrue(obtainedMetricKeys.contains(STORAGE_USED_BYTES));
        }
    }

    private void createAzureStorageAccounts(int numOfAccts) throws Throwable {
        for (int i = 0; i < numOfAccts; i++) {
            String staleAcctName = STALE_SA_NAME_PREFIX + i;
            createDefaultStorageAccountDescription(this.host, staleAcctName,
                    computeHost, endpointState);
        }
    }

    private void createAzureStorageContainers(int numOfCont) throws Throwable {
        for (int i = 0; i < numOfCont; i++) {
            String staleContName = STALE_CONTAINER_NAME_PREFIX + i;
            createDefaultResourceGroupState(this.host, staleContName,
                    computeHost, endpointState,
                    ResourceGroupStateType.AzureStorageContainer);
        }
    }

    private void createAzureBlobs(int numOfBlobs) throws Throwable {
        for (int i = 0; i < numOfBlobs; i++) {
            String staleBlobName = STALE_BLOB_NAME_PREFIX + i;
            createDefaultDiskState(this.host, staleBlobName, computeHost.documentSelfLink,
                    computeHost, endpointState);
        }
    }

    private void createAzureSecurityGroups(int numOfSecurityGroups) throws Throwable {
        for (int i = 0; i < numOfSecurityGroups; i++) {
            String staleSecurityGroupName = STALE_SG_NAME_PREFIX + i;
            createSecurityGroup(this, staleSecurityGroupName,
                    computeHost, endpointState);
        }
    }

    /**
     * Validates that the Gateway information discovered from Azure has been propagated to the
     * NetworkState custom properties.
     */
    private void validateVirtualNetworkGateways(AzureNicSpecs nicSpecs) throws Throwable {
        if (this.isMock) {
            return;
        }

        // Query all network states in the system
        Map<String, NetworkState> networkStatesMap = ProvisioningUtils.getResourceStates(this.host,
                NetworkService.FACTORY_LINK, NetworkState.class);

        AtomicBoolean isGatewayFound = new AtomicBoolean(false);

        networkStatesMap.values().forEach(networkState -> {
            if (networkState.name.contains(nicSpecs.network.name)) {

                List<SubnetState> subnetStates = getSubnetStates(this.host, networkState);
                assertFalse(subnetStates.isEmpty());

                subnetStates.stream()
                        .filter(subnetState -> AzureConstants.GATEWAY_SUBNET_NAME
                                .equalsIgnoreCase(subnetState.name))
                        .forEach(subnetState -> {
                            this.host.log(Level.INFO, "Validating gateway for network" +
                                    "(name %s, id: %s)", networkState.name, networkState.id);
                            assertNotNull("Custom properties are null.",
                                    networkState.customProperties);
                            assertNotNull("Virtual gateway property not found.",
                                    networkState.customProperties
                                            .get(ComputeProperties.FIELD_VIRTUAL_GATEWAY));
                            assertNotNull("SubnetState custom properties are null.",
                                    subnetState.customProperties);
                            assertEquals("Gateway SubnetState is not marked currectly with "
                                    + "infrastructure use custom property.",
                                    Boolean.TRUE.toString(),
                                    subnetState.customProperties.get(
                                            ComputeProperties.INFRASTRUCTURE_USE_PROP_NAME));
                            isGatewayFound.set(true);
                        });
            }
        });

        assertTrue("Gateway custom property was not found at all.", isGatewayFound.get());
    }

    /**
     * Get all SubnetStates within passed NetworkState. In other words, get all subnet states that
     * refer the network state passed.
     */
    // TODO : Duplicated from AWS TestUtils. Please advice where to put common test utils
    public static List<SubnetState> getSubnetStates(
            VerificationHost host,
            NetworkState networkState) {

        Query queryForReferrers = QueryUtils.queryForReferrers(
                networkState.documentSelfLink,
                SubnetState.class,
                SubnetState.FIELD_NAME_NETWORK_LINK);

        QueryByPages<SubnetState> querySubnetStatesReferrers = new QueryByPages<>(
                host,
                queryForReferrers,
                SubnetState.class,
                networkState.tenantLinks,
                networkState.endpointLink);

        DeferredResult<List<SubnetState>> subnetDR = querySubnetStatesReferrers
                .collectDocuments(Collectors.toList());

        return waitToComplete(subnetDR);
    }

}