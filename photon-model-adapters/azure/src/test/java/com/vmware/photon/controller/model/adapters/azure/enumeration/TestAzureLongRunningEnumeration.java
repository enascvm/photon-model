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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.vmware.photon.controller.model.ModelUtils.createSecurityGroup;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AzureResourceType.azure_net_interface;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AzureResourceType.azure_subnet;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AzureResourceType.azure_vnet;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.AZURE_SECURITY_GROUP_NAME;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.assertResourceExists;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultAuthCredentials;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultComputeHost;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultDiskState;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultEndpointState;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultResourceGroupState;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultResourcePool;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultStorageAccountDescription;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultVMResource;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.deleteServiceDocument;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.deleteVMs;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.generateName;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.getAzureSecurityGroup;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.getAzureVMCount;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.getAzureVirtualMachine;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.getAzureVirtualNetwork;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.initializeNicSpecs;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.randomString;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.runEnumeration;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.updateAzureSecurityGroup;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.updateAzureVirtualMachine;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.updateAzureVirtualNetwork;
import static com.vmware.photon.controller.model.adapters.util.TagsUtil.newTagState;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.TAG_KEY_TYPE;
import static com.vmware.photon.controller.model.query.QueryUtils.QueryTemplate.waitToComplete;
import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.createServiceURI;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.compute.implementation.ComputeManagementClientImpl;
import com.microsoft.azure.management.compute.implementation.VirtualMachineInner;
import com.microsoft.azure.management.network.implementation.NetworkManagementClientImpl;
import com.microsoft.azure.management.network.implementation.NetworkSecurityGroupInner;
import com.microsoft.azure.management.network.implementation.VirtualNetworkInner;
import com.microsoft.azure.management.resources.implementation.ResourceManagementClientImpl;
import com.microsoft.azure.management.storage.implementation.StorageManagementClientImpl;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapters.azure.AzureAdapters;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.ResourceGroupStateType;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.AzureNicSpecs;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.AzureNicSpecs.NicSpec;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.query.QueryStrategy;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTop;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState.SubStage;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.ServiceHostManagementService;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * A long-running test dedicated to checking and verifying our enumeration cycles in retaining valid
 * and necessary updates only to documents created from Azure endpoints.
 *
 * This test provisions a few VMs ({@link #numOfVMsToTest}, runs a handful of enumeration cycles at
 * intervals of {@link #enumerationFrequencyInMinutes} minutes over a span of
 * {@link #testRunDurationInMinutes} minutes. It then validates that compute documents are updated
 * only as necessary, and that no changes are made over that time (as expected). It additionally
 * checks and logs node memory profile to verify that several enumeration cycles do not take too
 * much load on the host's memory.
 */
public class TestAzureLongRunningEnumeration extends BaseModelTest {

    private static final String NETWORK_TAG_TYPE_VALUE = azure_vnet.toString();
    private static final String SUBNET_TAG_TYPE_VALUE = azure_subnet.toString();
    private static final String NETWORK_INTERFACE_TAG_TYPE_VALUE = azure_net_interface.toString();

    static {
        // Set the size of returned pages
        System.setProperty(QueryByPages.PROPERTY_NAME_MAX_PAGE_SIZE, Integer.toString(5));
        AzureConstants.setQueryResultLimit(5);
    }

    public static final String STALE_RG_NAME_PREFIX = "stalerg-";
    public static final String STALE_VM_NAME_PREFIX = "stalevm-";
    public static final String STALE_SA_NAME_PREFIX = "stalesa-";
    public static final String STALE_CONTAINER_NAME_PREFIX = "stalecontainer-";
    public static final String STALE_BLOB_NAME_PREFIX = "staleblob-";
    public static final String STALE_SG_NAME_PREFIX = "stalesg-";

    private static final int STALE_RG_COUNT = 3;
    private static final int STALE_VM_RESOURCES_COUNT = 100;
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

    private int numOfEnumerationsRan = 0;

    private String mockedStorageAccountName = randomString(15);

    // object counts
    private int vmCount = 0;

    // Stats Data
    private URI nodeStatsUri;
    private double maxMemoryInMb;
    private static final double BYTES_TO_MB = 1024 * 1024;
    private double availableMemoryPercentage;
    private Level loggingLevelForMemory;
    private static final String SEPARATOR = ": ";
    private static final int MEMORY_THRESHOLD_SEVERE = 40;
    private static final int MEMORY_THRESHOLD_WARNING = 60;
    private static final String STAT_NAME_MEMORY_AVAILABLE_IN_PERCENT = "MemoryAvailablePercent";

    // fields that are used across method calls, stash them as private fields
    private List<ComputeState> vmStates = new ArrayList<>();
    private List<StorageDescription> storageDescriptions = new ArrayList<>();
    private List<ResourceGroupState> resourceGroupStates = new ArrayList<>();
    private List<DiskState> diskStates = new ArrayList<>();

    private ComputeManagementClientImpl computeManagementClient;
    private ResourceManagementClientImpl resourceManagementClient;
    @SuppressWarnings("unused")
    private StorageManagementClientImpl storageManagementClient;
    private NetworkManagementClientImpl networkManagementClient;

    private static List<String> azureVMNames;
    private static List<AzureNicSpecs> nicSpecs;

    // Configurable options for the test.
    public boolean isMock = true;
    public String clientID = "clientID";
    public String clientKey = "clientKey";
    public String subscriptionId = "subscriptionId";
    public String tenantId = "tenantId";

    public static int numOfVMsToTest = 2;
    public int enumerationFrequencyInMinutes = 1;
    public int testRunDurationInMinutes = 3;
    public int timeoutSeconds = 1200;

    public static String azureVMNamePrefix = "az-lrt-";

    private static final List<Class> resourcesList = new ArrayList<>(
            Arrays.asList(ResourceGroupState.class,
                    ComputeState.class,
                    ComputeDescription.class,
                    DiskState.class,
                    StorageDescription.class,
                    NetworkState.class,
                    NetworkInterfaceState.class,
                    NetworkInterfaceDescription.class,
                    SubnetState.class,
                    TagState.class,
                    SecurityGroupState.class));

    private static final Map<Class, String> internalTagResourcesMap = new LinkedHashMap<>();

    private Map<String, Long> resourcesCountAfterFirstEnumeration = new HashMap<>();
    private Map<String, Long> resourcesCountAfterMultipleEnumerations = new HashMap<>();
    private Map<String, Double> resourceDeltaMap = new HashMap<>();
    public long resourceDeltaValue = 10;

    private boolean resourceCountAssertError = true;

    @BeforeClass
    public static void setupClass() {
        azureVMNames = new ArrayList<>();
        nicSpecs = new ArrayList<>();
    }

    @Override
    @Before
    public void setUp() throws Exception {
        for (int i = 0; i < numOfVMsToTest; i++) {
            String azureName = generateName(azureVMNamePrefix);
            azureVMNames.add(azureName);
            nicSpecs.add(initializeNicSpecs(azureName, false, true, false));
        }

        try {
            /*
             * Init Class-specific (shared between test runs) vars.
             *
             * NOTE: Ultimately this should go to @BeforeClass, BUT BasicReusableHostTestCase.HOST
             * is not accessible.
             */
            if (computeHost == null) {
                PhotonModelServices.startServices(this.host);
                PhotonModelTaskServices.startServices(this.host);
                PhotonModelAdaptersRegistryAdapters.startServices(this.host);
                AzureAdapters.startServices(this.host);

                this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
                this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
                this.host.waitForServiceAvailable(PhotonModelAdaptersRegistryAdapters.LINKS);
                this.host.waitForServiceAvailable(AzureAdapters.LINKS);

                // TODO: VSYM-992 - improve test/fix arbitrary timeout
                this.host.setTimeoutSeconds(this.timeoutSeconds);

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

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
            this.host.waitForServiceAvailable(AzureAdapters.LINKS);

            this.nodeStatsUri = UriUtils.buildUri(this.host.getUri(), ServiceUriPaths.CORE_MANAGEMENT);
            this.maxMemoryInMb = this.host.getState().systemInfo.maxMemoryByteCount / BYTES_TO_MB;

            internalTagResourcesMap.put(NetworkState.class, NETWORK_TAG_TYPE_VALUE);
            internalTagResourcesMap.put(SubnetState.class, SUBNET_TAG_TYPE_VALUE);
            internalTagResourcesMap
                    .put(NetworkInterfaceState.class, NETWORK_INTERFACE_TAG_TYPE_VALUE);

            if (!this.isMock) {
                ApplicationTokenCredentials credentials = new ApplicationTokenCredentials(
                        this.clientID,
                        this.tenantId, this.clientKey, AzureEnvironment.AZURE);
                this.computeManagementClient = new ComputeManagementClientImpl(credentials)
                        .withSubscriptionId(this.subscriptionId);

                this.resourceManagementClient = new ResourceManagementClientImpl(credentials)
                        .withSubscriptionId(this.subscriptionId);

                this.storageManagementClient = new StorageManagementClientImpl(credentials)
                        .withSubscriptionId(this.subscriptionId);

                this.networkManagementClient = new NetworkManagementClientImpl(credentials)
                        .withSubscriptionId(this.subscriptionId);
            }
        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    @After
    public void tearDown() throws InterruptedException {
        for (int i = 0; i < numOfVMsToTest; i++) {
            // try to delete the VMs
            if (this.vmStates.get(i) != null) {
                try {
                    int baselineCount = getAzureVMCount(this.computeManagementClient) + 1;
                    deleteVMs(this.host, this.vmStates.get(i).documentSelfLink, this.isMock, baselineCount);
                } catch (Throwable deleteEx) {
                    // just log and move on
                    this.host.log(Level.WARNING, "Exception deleting VM - %s",
                            deleteEx.getMessage());
                }
            }

            // try to delete the storage accounts
            if (this.storageDescriptions.get(i) != null) {
                try {
                    deleteServiceDocument(this.host, this.storageDescriptions.get(i).documentSelfLink);
                } catch (Throwable deleteEx) {
                    this.host.log(Level.WARNING, "Exception deleting storage accounts - %s",
                            deleteEx.getMessage());
                }
            }

            // try to delete the storage containers
            if (this.resourceGroupStates.get(i) != null) {
                try {
                    deleteServiceDocument(this.host, this.resourceGroupStates.get(i).documentSelfLink);
                } catch (Throwable deleteEx) {
                    this.host.log(Level.WARNING, "Exception deleting storage containers - %s",
                            deleteEx.getMessage());
                }
            }

            // try to delete the blobs
            if (this.diskStates.get(i) != null) {
                try {
                    deleteServiceDocument(this.host, this.diskStates.get(i).documentSelfLink);
                } catch (Throwable deleteEx) {
                    this.host.log(Level.WARNING, "Exception deleting disk states - %s",
                            deleteEx.getMessage());
                }
            }
        }
    }

    @Test
    public void testLongRunEnumeration() throws Throwable {
        // Log node stats at the beginning of the test
        logNodeStats(this.host.getServiceStats(this.nodeStatsUri));

        // 1. Provision VMs

        List<ProvisionComputeTaskState> taskStates = new ArrayList<>();
        for (int i = 0; i < numOfVMsToTest; i++) {
            this.storageDescriptions.add(createDefaultStorageAccountDescription(this.host,
                    this.mockedStorageAccountName, computeHost, endpointState));

            this.resourceGroupStates.add(createDefaultResourceGroupState(this.host,
                    this.mockedStorageAccountName, computeHost, endpointState,
                    ResourceGroupStateType.AzureResourceGroup));

            this.diskStates.add(createDefaultDiskState(this.host, this.mockedStorageAccountName,
                    this.mockedStorageAccountName, computeHost, endpointState));

            // create an Azure VM compute resource (this also creates a disk and a storage account)
            this.vmStates.add(createDefaultVMResource(this.host, azureVMNames.get(i),
                    computeHost, endpointState, nicSpecs.get(i)));

            // kick off a provision task to do the actual VM creation
            ProvisionComputeTaskState provisionTask = new ProvisionComputeTaskState();

            provisionTask.computeLink = this.vmStates.get(i).documentSelfLink;
            provisionTask.isMockRequest = this.isMock;
            provisionTask.taskSubStage = SubStage.CREATING_HOST;

            taskStates.add(TestUtils.doPost(this.host, provisionTask,
                    ProvisionComputeTaskState.class,
                    UriUtils.buildUri(this.host, ProvisionComputeTaskService.FACTORY_LINK)));
        }

        for (ProvisionComputeTaskState taskState : taskStates) {
            this.host.waitForFinishedTask(ProvisionComputeTaskState.class,
                    taskState.documentSelfLink);
        }

        this.host.log(Level.INFO,"VMs provisioned successfully.");

        // Check resources have been created
        // expected VM count = numOfVMsToTest + 1 (1 compute host instance + numOfVMsToTest vm compute state)
        ProvisioningUtils.queryDocumentsAndAssertExpectedCount(this.host, numOfVMsToTest + 1,
                ComputeService.FACTORY_LINK, false);
        ProvisioningUtils.queryDocumentsAndAssertExpectedCount(this.host, numOfVMsToTest,
                StorageDescriptionService.FACTORY_LINK, false);
        ProvisioningUtils.queryDocumentsAndAssertExpectedCount(this.host, numOfVMsToTest,
                ResourceGroupService.FACTORY_LINK, false);
        ProvisioningUtils.queryDocumentsAndAssertExpectedCount(this.host, numOfVMsToTest,
                DiskService.FACTORY_LINK, false);

        Map<String, ComputeState> azLrtComputeStates = getVMComputeStatesWithPrefix(azureVMNamePrefix);
        Assert.assertEquals(numOfVMsToTest, azLrtComputeStates.size());

        if (this.isMock) {

            runEnumeration(this.host, computeHost.documentSelfLink, computeHost.resourcePoolLink,
                    endpointState, this.isMock);

            for (int i = 0; i < numOfVMsToTest; i++) {
                deleteVMs(this.host, this.vmStates.get(i).documentSelfLink, this.isMock, 1);
                this.vmStates.set(i, null);
                ProvisioningUtils.queryDocumentsAndAssertExpectedCount(this.host, numOfVMsToTest - i,
                        ComputeService.FACTORY_LINK, false);

                deleteServiceDocument(this.host, this.storageDescriptions.get(i).documentSelfLink);
                this.storageDescriptions.set(i, null);
                ProvisioningUtils.queryDocumentsAndAssertExpectedCount(this.host, numOfVMsToTest - i - 1,
                        StorageDescriptionService.FACTORY_LINK, true);

                deleteServiceDocument(this.host, this.resourceGroupStates.get(i).documentSelfLink);
                this.resourceGroupStates.set(i, null);
                ProvisioningUtils.queryDocumentsAndAssertExpectedCount(this.host, numOfVMsToTest - i - 1,
                        ResourceGroupService.FACTORY_LINK, false);

                deleteServiceDocument(this.host, this.diskStates.get(i).documentSelfLink);
                this.diskStates.set(i, null);
                ProvisioningUtils.queryDocumentsAndAssertExpectedCount(this.host, numOfVMsToTest - i - 1,
                        DiskService.FACTORY_LINK, false);
            }
            return;
        }

        // 2. Create extra resources

        createStaleResource();
        tagAzureResources();

        // stale resources + 1 compute host instance + 1 vm compute state
        ProvisioningUtils.queryDocumentsAndAssertExpectedCount(this.host,
                STALE_VM_RESOURCES_COUNT + (numOfVMsToTest + 1),
                ComputeService.FACTORY_LINK, false);
        ProvisioningUtils.queryDocumentsAndAssertExpectedCount(this.host,
                STALE_STORAGE_ACCOUNTS_COUNT + numOfVMsToTest,
                StorageDescriptionService.FACTORY_LINK, false);
        ProvisioningUtils.queryDocumentsAndAssertExpectedCount(this.host,
                STALE_CONTAINERS_COUNT + STALE_RG_COUNT + 1,
                ResourceGroupService.FACTORY_LINK, false);
        ProvisioningUtils.queryDocumentsAndAssertExpectedCount(this.host,
                STALE_BLOBS_COUNT + numOfVMsToTest, DiskService.FACTORY_LINK, false);

        ProvisioningUtils.queryDocumentsAndAssertExpectedCount(this.host,
                STALE_SECURITY_GROUPS_COUNT, SecurityGroupService.FACTORY_LINK, false);

        this.vmCount = getAzureVMCount(this.computeManagementClient);
        this.host.log(Level.INFO, "Initial VM Count: %d", this.vmCount);

        // 3. Run multiple enumerations over a period of time.

        this.host.log(Level.INFO, "Waiting for multiple enumeration runs...");
        ScheduledFuture<?> enums =  runEnumerationAndLogNodeStatsPeriodically();
        this.host.waitFor("Timeout while waiting for test run duration", () -> {
            TimeUnit.MINUTES.sleep(this.testRunDurationInMinutes);
            enums.cancel(false);
            return true;
        });

        this.host.waitFor("Timeout while waiting for last enumeration to clear out.", () -> {
            TimeUnit.MINUTES.sleep(1);
            return true;
        });

        if (this.resourceCountAssertError) {
            this.host.log(Level.SEVERE, "Resource count assertions failed.");
            fail("Resource count assertions failed.");
        }

        // 4. Validate extra resources
        assertRemoteResources();
        assertStaleResources();

        ServiceDocumentQueryResult result = ProvisioningUtils.queryDocumentsAndAssertExpectedCount(
                this.host, this.vmCount, ComputeService.FACTORY_LINK, false);

        // validate type field for enumerated VMs
        result.documents.entrySet().stream()
                .map(e -> Utils.fromJson(e.getValue(), ComputeState.class))
                .filter(c -> !c.documentSelfLink.equals(computeHost.documentSelfLink))
                .forEach(c -> assertEquals(ComputeType.VM_GUEST, c.type));

        // validate internal tags for enumerated VMs
        TagService.TagState expectedInternalTypeTag = newTagState(TAG_KEY_TYPE,
                AzureConstants.AzureResourceType.azure_vm.toString(), false,
                endpointState.tenantLinks);
        result.documents.entrySet().stream()
                .map(e -> Utils.fromJson(e.getValue(), ComputeState.class))
                .filter(c -> c.type.equals(ComputeType.VM_GUEST))
                .forEach(c -> {
                    assertNotNull(c.tagLinks);
                    assertTrue(c.tagLinks.contains(expectedInternalTypeTag.documentSelfLink));
                });

        // validate environment name field for enumerated VMs
        result.documents.entrySet().stream()
                .map(e -> Utils.fromJson(e.getValue(), ComputeState.class))
                .forEach(c -> assertEquals(ComputeDescription.ENVIRONMENT_NAME_AZURE,
                        c.environmentName));

        // 5. Validate enumerated compute states have not changed.

        Map<String, ComputeState> azLrtComputeStatesEnd = getVMComputeStatesWithPrefix(azureVMNamePrefix);
        Assert.assertEquals(numOfVMsToTest, azLrtComputeStatesEnd.size());
        assertComputeStatesEqual(azLrtComputeStates, azLrtComputeStatesEnd);

        for (String azureVmName : azLrtComputeStates.keySet()) {
            ComputeState original = azLrtComputeStates.get(azureVmName);
            ComputeState later = azLrtComputeStatesEnd.get(azureVmName);

            // Validate number of document version changes are less than number of enumerations ran.
            assertTrue(later.documentVersion - original.documentVersion <
                    this.numOfEnumerationsRan);
        }

        // 1 network per each stale vm resource + 1 network for original vm compute state.
        ServiceDocumentQueryResult networkResults = ProvisioningUtils
                .queryAllFactoryResources(this.host, NetworkService.FACTORY_LINK);

        // validate internal tags for enumerated networks
        TagService.TagState expectedNetworkInternalTypeTag = newTagState(TAG_KEY_TYPE,
                NETWORK_TAG_TYPE_VALUE, false, endpointState.tenantLinks);
        networkResults.documents.entrySet().stream()
                .map(e -> Utils.fromJson(e.getValue(), NetworkState.class))
                .forEach(c -> {
                    assertNotNull(c.tagLinks);
                    assertTrue(
                            c.tagLinks.contains(expectedNetworkInternalTypeTag.documentSelfLink));
                });

        // 1 subnet per network, 1 network per each stale vm resource + 1 subnet for the original
        // compute state.
        ServiceDocumentQueryResult subnetResults = ProvisioningUtils
                .queryAllFactoryResources(this.host, SubnetService.FACTORY_LINK);

        // validate internal tags for enumerated subnets
        TagService.TagState expectedSubnetInternalTypeTag = newTagState(TAG_KEY_TYPE,
                SUBNET_TAG_TYPE_VALUE, false, endpointState.tenantLinks);
        subnetResults.documents.entrySet().stream()
                .map(e -> Utils.fromJson(e.getValue(), SubnetState.class))
                .forEach(c -> {
                    assertNotNull(c.tagLinks);
                    assertTrue(
                            c.tagLinks.contains(expectedSubnetInternalTypeTag.documentSelfLink));
                });

        // 6. Delete VMs and update documents via enumeration.

        for (int i = 0; i < numOfVMsToTest; i++) {
            this.host.log(Level.INFO, "Deleting vm: %s", azureVMNames.get(i));
            this.computeManagementClient.virtualMachines()
                    .beginDelete(azureVMNames.get(i), azureVMNames.get(i));
        }

        runEnumeration(this.host, computeHost.documentSelfLink, computeHost.resourcePoolLink,
                endpointState, this.isMock);

        for (int i = 0; i < numOfVMsToTest; i++) {
            assertResourceExists(this.host, ComputeService.FACTORY_LINK, azureVMNames.get(i), false);

            // clean up
            this.vmStates.set(i, null);
            this.resourceManagementClient.resourceGroups().beginDelete(azureVMNames.get(i));
            this.host.log(Level.INFO, "Deleting vm resource group %s", azureVMNames.get(i));
        }

        // Log node stats at the end of the test
        logNodeStats(this.host.getServiceStats(this.nodeStatsUri));
    }

    /**
     * Assert that relevant portions of compute states have not been changed.
     * @param originalComputes The original set of ComputeStates.
     * @param newComputes The set of ComputeStates to compare.
     */
    private void assertComputeStatesEqual(Map<String, ComputeState> originalComputes,
            Map<String, ComputeState> newComputes) {
        for (String azureVmName : originalComputes.keySet()) {
            ComputeState initDocument = originalComputes.get(azureVmName);
            ComputeState newDoc = newComputes.get(azureVmName);

            if (newDoc == null) {
                this.host.log(Level.SEVERE,
                        "Test failed - %s not found in later stages of enumeration.", azureVmName);
                fail();
            }

            Assert.assertEquals(initDocument.name, newDoc.name);
            Assert.assertEquals(initDocument.descriptionLink, newDoc.descriptionLink);
            Assert.assertEquals(initDocument.address, newDoc.address);
            Assert.assertEquals(initDocument.type, newDoc.type);
            Assert.assertEquals(initDocument.environmentName, newDoc.environmentName);
            Assert.assertEquals(initDocument.primaryMAC, newDoc.primaryMAC);
            Assert.assertEquals(initDocument.instanceType, newDoc.instanceType);
            Assert.assertEquals(initDocument.cpuCount, newDoc.cpuCount);
            Assert.assertEquals(initDocument.cpuMhzPerCore, newDoc.cpuMhzPerCore);
            Assert.assertEquals(initDocument.gpuCount, newDoc.gpuCount);
            Assert.assertEquals(initDocument.totalMemoryBytes, newDoc.totalMemoryBytes);
            Assert.assertEquals(initDocument.regionId, newDoc.regionId);
            Assert.assertEquals(initDocument.zoneId, newDoc.zoneId);
            Assert.assertEquals(initDocument.lifecycleState, newDoc.lifecycleState);
            Assert.assertEquals(initDocument.parentLink, newDoc.parentLink);
            Assert.assertEquals(initDocument.adapterManagementReference, newDoc.adapterManagementReference);

            for (int i = 0; i < initDocument.diskLinks.size(); i++) {
                Assert.assertEquals(initDocument.diskLinks.get(i), newDoc.diskLinks.get(i));
            }

            for (int i = 0; i < initDocument.networkInterfaceLinks.size(); i++) {
                Assert.assertEquals(initDocument.networkInterfaceLinks.get(i), newDoc.networkInterfaceLinks.get(i));
            }

            Assert.assertEquals(initDocument.endpointLink, newDoc.endpointLink);
            Assert.assertEquals(initDocument.hostName, newDoc.hostName);

            this.host.log(Level.INFO, "VM %s has not changed after running enumerations.", azureVmName);
        }
    }

    /**
     * Filters and returns all compute states with a VM name starting with a particular prefix.
     * @param vmPrefix A VM name prefix for distinguishing relevant compute states.
     * @return A map of ComputeStates with a prefix, with each VM name as the key.
     */
    private Map<String, ComputeState> getVMComputeStatesWithPrefix(String vmPrefix) {
        URI queryUri = UriUtils.buildExpandLinksQueryUri(createServiceURI(host,
                host.getUri(), ComputeService.FACTORY_LINK));

        // add limit, otherwise the query will not return if there are too many docs or versions
        queryUri = UriUtils.extendUriWithQuery(queryUri, UriUtils.URI_PARAM_ODATA_LIMIT,
                String.valueOf(numOfVMsToTest * 2));
        ServiceDocumentQueryResult res = host.getFactoryState(queryUri);

        // Add compute states with the prefix to map.
        Map<String, ComputeService.ComputeState> filteredComputeStates = new HashMap<>();
        for (String key : res.documents.keySet()) {
            String json = Utils.toJson(res.documents.get(key));
            ComputeService.ComputeState doc = Utils.fromJson(json,
                    ComputeService.ComputeState.class);
            if (doc.name.startsWith(vmPrefix)) {
                filteredComputeStates.put(doc.name, doc);
            }
        }

        return filteredComputeStates;
    }

    /**
     * Periodically runs enumeration, verifies resources counts and logs node stats.
     */
    private ScheduledFuture<?> runEnumerationAndLogNodeStatsPeriodically() {
        return this.host.getScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                runEnumeration(this.host, computeHost.documentSelfLink, computeHost.resourcePoolLink,
                        endpointState, this.isMock);
                this.numOfEnumerationsRan++;

                // Print node memory usage
                logNodeStats(this.host.getServiceStats(this.nodeStatsUri));
            } catch (Throwable e) {
                this.host.log(Level.WARNING, "Error running enumeration in test" + e.getMessage());
            }
            // perform check on resource counts after each enumeration
            generateResourcesCounts();
            // assert check on resources count after first and last enumeration.
            verifyResourcesCount();
            // assert check on resources count for internal tags
            verifyInternalTagsResourceCount();
        }, 0, this.enumerationFrequencyInMinutes, TimeUnit.MINUTES);
    }

    /**
     * Fetch the document count for resources
     */
    private void generateResourcesCounts() {
        if (this.numOfEnumerationsRan == 1) {
            for (Class resource : resourcesList) {
                this.resourcesCountAfterFirstEnumeration
                        .put(resource.toString(), getDocumentCount(resource));
            }

            // populate error delta margin for resources.
            populateResourceDelta();
        } else {
            for (Class resource : resourcesList) {
                this.resourcesCountAfterMultipleEnumerations
                        .put(resource.toString(), getDocumentCount(resource));
            }
        }
    }

    /**
     * Verify document count of resources after first enumeration and later enumerations.
     */
    private void verifyResourcesCount() {
        if (this.numOfEnumerationsRan > 1) {
            this.host.log(Level.INFO, "Verifying Resources counts...");

            for (Class resource : resourcesList) {
                this.host.log(Level.INFO, "resource values are: [resource: %s],"
                        + "[1st enumeration value: %d], "
                        + "[latest enumeration value: %d],"
                        + "[delta value: %f]",
                        resource.toString(),
                        this.resourcesCountAfterFirstEnumeration.get(resource.toString()),
                        this.resourcesCountAfterMultipleEnumerations.get(resource.toString()),
                        this.resourceDeltaMap.get(resource.toString()));

                assertTrue((this.resourcesCountAfterFirstEnumeration.get(resource.toString())
                        + this.resourceDeltaMap.get(resource.toString()))
                        >= this.resourcesCountAfterMultipleEnumerations.get(resource.toString()));
            }
            this.resourceCountAssertError = false;
            this.host.log(Level.INFO, "Resources count assertions successful.");
        }
    }

    /**
     * Populate delta error margin for resources counts.
     */
    private void populateResourceDelta() {
        for (Class resource : resourcesList) {
            this.resourceDeltaMap.put(resource.toString(), Math.ceil(
                    this.resourcesCountAfterFirstEnumeration.get(resource.toString()) *
                            this.resourceDeltaValue / 100));
        }
    }

    /**
     * Verifies expected internal tags are created for resources.
     */
    private void verifyInternalTagsResourceCount() {
        List<TagState> tagStates = getInternalTypeTagsList();
        if (this.numOfEnumerationsRan >= 1) {
            for (Map.Entry<Class, String> resourceEntry : internalTagResourcesMap.entrySet()) {
                if (this.resourcesCountAfterFirstEnumeration.get(resourceEntry.getKey().toString()) > 0) {
                    assertTrue(isInternalTagPresent(tagStates, resourceEntry.getValue()));
                }
            }
        }
        this.host.log("Internal tags verification successful...");
    }

    private boolean isInternalTagPresent(List<TagState> tagStates, String expectedValue) {
        for (TagState tagState : tagStates) {
            if (tagState.value.equalsIgnoreCase(expectedValue)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add tags, that later should be discovered as part of first enumeration cycle.
     */
    private void tagAzureResources() throws Exception {
        for (int i = 0; i < numOfVMsToTest; i++) {
            // tag v-Net
            VirtualNetworkInner vmNetwUpdate = getAzureVirtualNetwork(this.networkManagementClient,
                    azureVMNames.get(i), nicSpecs.get(i).network.name);

            Map<String, String> vmNetwTags = new HashMap<>();
            vmNetwTags.put(NETWORK_TAG_KEY_PREFIX + azureVMNames.get(i), NETWORK_TAG_VALUE);
            vmNetwUpdate.withTags(vmNetwTags);

            updateAzureVirtualNetwork(this.networkManagementClient, azureVMNames.get(i),
                    nicSpecs.get(i).network.name, vmNetwUpdate);

            // tag VM
            VirtualMachineInner vmUpdate = getAzureVirtualMachine(
                    this.computeManagementClient, azureVMNames.get(i), azureVMNames.get(i));

            Map<String, String> vmTags = new HashMap<>();
            vmTags.put(VM_TAG_KEY_PREFIX + azureVMNames.get(i), VM_TAG_VALUE);
            vmUpdate.withTags(vmTags);

            updateAzureVirtualMachine(this.computeManagementClient, azureVMNames.get(i),
                    azureVMNames.get(i), vmUpdate);

            // tag Security Group
            NetworkSecurityGroupInner sgUpdate = getAzureSecurityGroup(this.networkManagementClient,
                    azureVMNames.get(i), AZURE_SECURITY_GROUP_NAME);

            Map<String, String> sgTags = new HashMap<>();
            sgTags.put(SG_TAG_KEY_PREFIX + azureVMNames.get(i), SG_TAG_VALUE);
            sgUpdate.withTags(sgTags);
            sgUpdate.withLocation(AzureTestUtil.AZURE_RESOURCE_GROUP_LOCATION);

            updateAzureSecurityGroup(this.networkManagementClient, azureVMNames.get(i),
                    AZURE_SECURITY_GROUP_NAME, sgUpdate);
        }
    }

    /**
     * Creates stale resource states for deletion. These should be deleted as part of the first
     * enumeration cycle.
     */
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

    /**
     * Assert that remote resources are enumerated and exist locally.
     */
    private void assertRemoteResources() {
        for (int i = 0; i < numOfVMsToTest; i++) {
            assertResourceExists(this.host, NetworkService.FACTORY_LINK, nicSpecs.get(i).network.name,
                    true);

            for (NicSpec nicSpec : nicSpecs.get(i).nicSpecs) {
                assertResourceExists(this.host, SubnetService.FACTORY_LINK,
                        nicSpec.getSubnetSpec().name, true);
            }

            assertResourceExists(this.host, ResourceGroupService.FACTORY_LINK, azureVMNames.get(i), true);

            assertResourceExists(this.host, SecurityGroupService.FACTORY_LINK,
                    AZURE_SECURITY_GROUP_NAME, true);

            assertResourceExists(this.host, StorageDescriptionService.FACTORY_LINK,
                    (azureVMNames.get(i) + "sa").replace("-", ""), true);

            assertResourceExists(this.host, DiskService.FACTORY_LINK,
                    azureVMNames.get(i) + "-boot-disk", true);

            // Tags
            final Map<String, String> expectedTags = new HashMap<>();
            expectedTags.put(NETWORK_TAG_KEY_PREFIX + azureVMNames.get(i), NETWORK_TAG_VALUE);
            expectedTags.put(VM_TAG_KEY_PREFIX + azureVMNames.get(i), VM_TAG_VALUE);
            expectedTags.put(SG_TAG_KEY_PREFIX + azureVMNames.get(i), SG_TAG_VALUE);

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

            List<TagState> tagStates =
                    waitToComplete(queryLocalTags.collectDocuments(Collectors.toList()));

            assertEquals("TagStates were not discovered.", expectedTags.size(), tagStates.size());
            for (TagState tag : tagStates) {
                assertEquals(expectedTags.get(tag.key), tag.value);
            }
        }
    }

    /**
     * Assert that stale resources were cleaned up.
     */
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
     * Creates a set of Azure Resource Groups.
     * @param numOfResourceGroups The number of resource groups to create.
     */
    private void createAzureResourceGroups(int numOfResourceGroups) throws Throwable {
        for (int i = 0; i < numOfResourceGroups; i++) {
            String staleResourceGroupName = STALE_RG_NAME_PREFIX + i;
            createDefaultResourceGroupState(this.host, staleResourceGroupName,
                    computeHost, endpointState,
                    ResourceGroupStateType.AzureResourceGroup);
        }
    }

    /**
     * Creates a set of Azure VM resources.
     * @param numOfVMs The number of VM resources to create.
     */
    private void createAzureVMResources(int numOfVMs) throws Throwable {
        for (int i = 0; i < numOfVMs; i++) {
            String staleVMName = STALE_VM_NAME_PREFIX + i;
            for (int j = 0; j < numOfVMsToTest; j++) {
                createDefaultVMResource(this.host, staleVMName, computeHost, endpointState, nicSpecs.get(j));
            }
        }
    }

    /**
     * Creates Azure storage accounts.
     * @param numOfAccts The number of storage accounts to create.
     */
    private void createAzureStorageAccounts(int numOfAccts) throws Throwable {
        for (int i = 0; i < numOfAccts; i++) {
            String staleAcctName = STALE_SA_NAME_PREFIX + i;
            createDefaultStorageAccountDescription(this.host, staleAcctName,
                    computeHost, endpointState);
        }
    }

    /**
     * Creates Azure storage containers.
     * @param numOfCont The number of storage containers to create.
     */
    private void createAzureStorageContainers(int numOfCont) throws Throwable {
        for (int i = 0; i < numOfCont; i++) {
            String staleContName = STALE_CONTAINER_NAME_PREFIX + i;
            createDefaultResourceGroupState(this.host, staleContName,
                    computeHost, endpointState,
                    ResourceGroupStateType.AzureStorageContainer);
        }
    }

    /**
     * Creates Azure blobs.
     * @param numOfBlobs The number of blobs to create.
     */
    private void createAzureBlobs(int numOfBlobs) throws Throwable {
        for (int i = 0; i < numOfBlobs; i++) {
            String staleBlobName = STALE_BLOB_NAME_PREFIX + i;
            createDefaultDiskState(this.host, staleBlobName, computeHost.documentSelfLink,
                    computeHost, endpointState);
        }
    }

    /**
     * Creates Azure security groups.
     * @param numOfSecurityGroups The number of security groups to create.
     */
    private void createAzureSecurityGroups(int numOfSecurityGroups) throws Throwable {
        for (int i = 0; i < numOfSecurityGroups; i++) {
            String staleSecurityGroupName = STALE_SG_NAME_PREFIX + i;
            createSecurityGroup(this, staleSecurityGroupName,
                    computeHost, endpointState);
        }
    }

    /**
     * Prints logs for node stats (Memory usage).
     * @param statsMap Map containing node stats.
     */
    private void logNodeStats(Map<String, ServiceStat> statsMap) {
        this.host.log(Level.INFO, "===== COLLECTING NODE STATS =====");
        // In case getServiceStats method fails or returns null.
        if (statsMap == null || statsMap.isEmpty()) {
            this.host.log(Level.WARNING, "Error getting memory usage.");
            return;
        }

        this.availableMemoryPercentage = (statsMap.get(
                ServiceHostManagementService.STAT_NAME_AVAILABLE_MEMORY_BYTES_PER_HOUR)
                .latestValue / BYTES_TO_MB) / this.maxMemoryInMb * 100;

        // Increase logging level if available Memory is less than expected.
        if (this.availableMemoryPercentage > MEMORY_THRESHOLD_WARNING) {
            this.loggingLevelForMemory = Level.INFO;
        } else if (this.availableMemoryPercentage > MEMORY_THRESHOLD_SEVERE) {
            this.loggingLevelForMemory = Level.WARNING;
        } else {
            this.loggingLevelForMemory = Level.SEVERE;
        }

        this.host.log(this.loggingLevelForMemory, STAT_NAME_MEMORY_AVAILABLE_IN_PERCENT
                + SEPARATOR + this.availableMemoryPercentage);
    }

    /**
     * Returns the count of resource documents for given Resource type.
     */
    private long getDocumentCount(Class <? extends ServiceDocument> T) {
        QueryTask.Query.Builder qBuilder = QueryTask.Query.Builder.create()
                .addKindFieldClause(T);

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryTask.QuerySpecification.QueryOption.COUNT)
                .setQuery(qBuilder.build())
                .build();

        this.host.createQueryTaskService(queryTask, false, true, queryTask, null);
        return queryTask.results.documentCount;
    }

    private List<TagState> getInternalTypeTagsList() {
        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(TagState.class)
                .addFieldClause(TagState.FIELD_NAME_KEY, PhotonModelConstants.TAG_KEY_TYPE)
                .addFieldClause(TagState.FIELD_NAME_EXTERNAL, Boolean.FALSE.toString());

        QueryStrategy<TagState> queryLocalTags = new QueryTop<>(
                getHost(), qBuilder.build(), TagState.class, null)
                .setMaxResultsLimit(Integer.MAX_VALUE);

        List<TagState> tagStates = waitToComplete(
                queryLocalTags.collectDocuments(Collectors.toList()));
        return tagStates;
    }
}