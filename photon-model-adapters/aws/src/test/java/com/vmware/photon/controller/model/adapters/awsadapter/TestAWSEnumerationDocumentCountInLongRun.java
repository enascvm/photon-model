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

package com.vmware.photon.controller.model.adapters.awsadapter;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSAuthentication;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSComputeHost;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSResourcePool;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.deleteNICDirectlyWithEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.deleteVMsOnThisEndpoint;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.deleteVMsUsingEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.enumerateResources;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.provisionAWSVMWithEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.regionId;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.setAwsClientMockInfo;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.setUpTestVpc;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.tearDownTestVpc;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.waitForInstancesToBeTerminated;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.waitForProvisioningToComplete;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getExecutor;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.xenon.common.BasicTestCase;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceHostManagementService;
import com.vmware.xenon.services.common.ServiceUriPaths;


/**
 * Test to check correctness and consistency of number of documents generated by AWS enumeration.
 * The test provisions and terminates AWS instances during the run and periodically runs enumeration.
 * It verifies counts of following resources in context of the test (i.e. the resources created/enumerated/deleted
 * during the test run):
 * ResourcePoolState
 * NetworkInterfaceState
 * SecurityGroupState
 * SubnetState
 */
public class TestAWSEnumerationDocumentCountInLongRun extends BasicTestCase {

    private static final String TEST_CASE_INITIAL = "Initial Run ";
    private static final String TEST_CASE_DELETE_VM = "Delete VM ";
    private static final String TEST_CASE_MOCK_MODE = "Mock Mode ";
    private static final String T2_NANO_INSTANCE_TYPE = "t2.nano";
    private static final String SEPARATOR = ": ";
    private static final String FIELD_NAME_ID = "id";
    private static final String STAT_NAME_MEMORY_AVAILABLE_IN_PERCENT = "MemoryAvailablePercent";
    private static final int MEMORY_THRESHOLD_SEVERE = 60;
    private static final int MEMORY_THRESHOLD_WARNING = 40;
    private static final int EXECUTOR_TERMINATION_WAIT_DURATION_MINUTES = 2;
    private static final double BYTES_TO_MB = 1024 * 1024;

    // Sets for storing document links and ids
    private Set<String> computeStateLinks;
    private Set<String> resourcePoolLinks;
    private Set<String> networkInterfaceLinks;
    private Set<String> securityGroupLinks;
    private Set<String> subnetLinks;
    private Set<String> resourcePoolIds;
    private Set<String> networkInterfaceIds;
    private Set<String> securityGroupIds;
    private Set<String> subnetIds;

    private ComputeState computeHost;
    private EndpointState endpointState;

    private Level loggingLevelForMemory;
    private double availableMemoryPercentage;
    private double maxMemoryInMb;
    private ArrayList<String> instancesToCleanUp;
    private List<String> instanceIds;
    private String nicToCleanUp = null;
    private URI nodeStatsUri = null;
    private AmazonEC2AsyncClient client;
    private boolean postDeletion = false;

    public boolean isAwsClientMock = false;
    public String awsMockEndpointReference = null;
    public boolean useAllRegions = false;
    public boolean isMock = true;
    public String accessKey = "accessKey";
    public String secretKey = "secretKey";
    public int timeoutSeconds = 1200;
    public int enumerationFrequencyInMinutes = 1;
    public int testRunDurationInMinutes = 3;
    public int numberOfInstancesToProvision = 4;

    private Map<String, Object> awsTestContext;
    private String subnetId;
    private String securityGroupId;

    private int numOfEnumerationsRan = 0;

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

    private Map<String, Long> resourcesCountAfterFirstEnumeration = new HashMap<>();
    private Map<String, Long> resourcesCountAfterMultipleEnumerations = new HashMap<>();
    private Map<String, Double> resourceDeltaMap = new HashMap<>();
    public long resourceDeltaValue = 10;

    private boolean resourceCountAssertError = true;

    @Rule
    public TestName currentTestName = new TestName();

    @Before
    public void setUp() throws Throwable {
        CommandLineArgumentParser.parseFromProperties(this);

        this.instancesToCleanUp = new ArrayList<>();
        this.computeStateLinks = new HashSet<>();
        this.resourcePoolLinks = new HashSet<>();
        this.networkInterfaceLinks = new HashSet<>();
        this.securityGroupLinks = new HashSet<>();
        this.subnetLinks = new HashSet<>();
        this.resourcePoolIds = new HashSet<>();
        this.networkInterfaceIds = new HashSet<>();
        this.securityGroupIds = new HashSet<>();
        this.subnetIds = new HashSet<>();

        setAwsClientMockInfo(this.isAwsClientMock, this.awsMockEndpointReference);
        // create credentials
        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
        creds.privateKey = this.secretKey;
        creds.privateKeyId = this.accessKey;
        this.client = AWSUtils.getAsyncClient(creds, regionId, getExecutor());

        this.awsTestContext = new HashMap<>();
        setUpTestVpc(this.client, this.awsTestContext, this.isMock);
        this.subnetId = (String) this.awsTestContext.get(TestAWSSetupUtils.SUBNET_KEY);
        this.securityGroupId = (String) this.awsTestContext.get(TestAWSSetupUtils.SECURITY_GROUP_KEY);

        try {
            PhotonModelServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            PhotonModelAdaptersRegistryAdapters.startServices(this.host);
            AWSAdapters.startServices(this.host);

            this.host.setTimeoutSeconds(this.timeoutSeconds);

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelAdaptersRegistryAdapters.LINKS);
            this.host.waitForServiceAvailable(AWSAdapters.LINKS);
        } catch (Throwable e) {
            this.host.log("Error starting up services for the test %s", e.getMessage());
            throw new Exception(e);
        }

        this.nodeStatsUri = UriUtils.buildUri(this.host.getUri(), ServiceUriPaths.CORE_MANAGEMENT);

        this.maxMemoryInMb = this.host.getState().systemInfo.maxMemoryByteCount / BYTES_TO_MB;

        // create the compute host, resource pool and the VM state to be used in the test.
        initResourcePoolAndComputeHost();
    }

    @After
    public void tearDown() throws Throwable {
        if (this.host == null) {
            return;
        }
        tearDownAwsVMs();
        tearDownTestVpc(this.client, this.host, this.awsTestContext, this.isMock);
        this.client.shutdown();
        setAwsClientMockInfo(false, null);
    }

    /**
     * Test flow:
     * 1. Provision instances on AWS
     * 2. Periodically run enumeration for a specified duration.
     * 3. Assert that there is only one document each for the all the resources enumerated during the test.
     * 4. Delete all the instances that were provisioned at the start of the test.
     * 5. Run enumeration.
     * 6. Assert that all the documents enumerated during the test got deleted.
     * @throws Throwable
     */
    @Test
    public void testDocumentCountsDuringEnumeration() throws Throwable {

        this.host.log("Running test: " + this.currentTestName);

        if (this.isMock) {
            // Just make a call to the enumeration service and make sure that the adapter patches
            // the parent with completion.
            enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
                    TEST_CASE_MOCK_MODE);
            return;
        }

        // Provision instances on AWS
        this.instanceIds = provisionAWSVMWithEC2Client(this.client,
                this.host, this.numberOfInstancesToProvision, T2_NANO_INSTANCE_TYPE, this.subnetId,
                this.securityGroupId);
        this.instancesToCleanUp.addAll(this.instanceIds);
        waitForProvisioningToComplete(this.instanceIds, this.host, this.client, 0);

        // Periodically run enumeration, specified by enumerationFrequencyInMinutes parameter.
        runEnumerationAndLogNodeStatsPeriodically();

        this.host.log(Level.INFO, "Waiting for multiple enumeration runs...");

        // Keep the host running for some time, specified by testRunDurationInMinutes parameter.
        this.host.waitFor("Timeout while waiting for test run duration", () -> {
            TimeUnit.MINUTES.sleep(this.testRunDurationInMinutes);
            this.host.getScheduledExecutor().shutdown();
            this.host.getScheduledExecutor().awaitTermination(EXECUTOR_TERMINATION_WAIT_DURATION_MINUTES,
                    TimeUnit.MINUTES);
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

        // Store document links and ids for enumerated resources to obtain expected number of documents
        // and actual number of documents.
        storeDocumentLinksAndIds(this.instanceIds);

        // Asserts to check if the document counts are correct after multiple enumerations
        assertDocumentCounts();

        // Delete AWS resources that were provisioned
        deleteVMsUsingEC2Client(this.client, this.host, this.instanceIds);

        // Run enumeration after deleting instances
        enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
                TEST_CASE_DELETE_VM);

        // Clear the document links and ids stored previously
        clearStoredDocumentLinksAndIds();

        // Store document links and ids for enumerated resources to obtain expected number of documents
        // and actual number of documents.
        storeDocumentLinksAndIds(this.instanceIds);

        // Asserts to check if all the documents enumerated during test are deleted deleting instances.
        this.postDeletion = true;
        assertDocumentCounts();
    }

    /**
     * Periodically runs enumeration, verifies resources counts and logs node stats.
     */
    private void runEnumerationAndLogNodeStatsPeriodically() {
        this.host.getScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                this.host.log(Level.INFO, "Running enumeration...");
                enumerateResources(this.host, this.computeHost, this.endpointState, this.isMock,
                        TEST_CASE_INITIAL);
                this.numOfEnumerationsRan++;

                // Print node CPU Utilization and Memory usages
                logNodeStats(this.host.getServiceStats(this.nodeStatsUri));
            } catch (Throwable e) {
                this.host.log(Level.WARNING, "Error running enumeration in test" + e.getMessage());
            }
            // perform check on resource counts after each enumeration
            generateResourcesCounts();
            // assert check on resources count after first and last enumeration.
            verifyResourcesCount();
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

    /**
     * Verify document count of resources after first enumeration and later enumerations.
     */
    private void verifyResourcesCount() {
        if (this.numOfEnumerationsRan > 1) {
            this.host.log(Level.INFO, "Verifying Resources counts...");

            for (Class resource : resourcesList) {
                assertTrue((this.resourcesCountAfterFirstEnumeration.get(resource.toString())
                        + this.resourceDeltaMap.get(resource.toString()))
                        >= this.resourcesCountAfterMultipleEnumerations.get(resource.toString()));
            }
            this.resourceCountAssertError = false;
            this.host.log(Level.INFO, "Resources count assertions successful.");
        }
    }

    /**
     * Prints logs for node stats (CPU usage and Memory usage).
     * @param statsMap Map containing node stats.
     */
    private void logNodeStats(Map<String, ServiceStat> statsMap) {
        // In case getServiceStats method fails or returns null.
        if (statsMap == null || statsMap.isEmpty()) {
            this.host.log(Level.WARNING, "Error getting CPU utilization and Memory usage.");
            return;
        }

        if (statsMap.get(
                ServiceHostManagementService.STAT_NAME_AVAILABLE_MEMORY_BYTES_PER_HOUR) != null) {
            this.availableMemoryPercentage = (statsMap.get(
                    ServiceHostManagementService.STAT_NAME_AVAILABLE_MEMORY_BYTES_PER_HOUR)
                    .latestValue / BYTES_TO_MB) / this.maxMemoryInMb * 100;

            this.loggingLevelForMemory = Level.INFO;

            // Increase logging level if available Memory is less than expected.
            if (this.availableMemoryPercentage > MEMORY_THRESHOLD_SEVERE) {
                this.loggingLevelForMemory = Level.SEVERE;
            } else if (this.availableMemoryPercentage > MEMORY_THRESHOLD_WARNING) {
                this.loggingLevelForMemory = Level.WARNING;
            }

            this.host.log(this.loggingLevelForMemory, STAT_NAME_MEMORY_AVAILABLE_IN_PERCENT
                    + SEPARATOR + this.availableMemoryPercentage);
        }
    }

    /**
     * Calls the methods to:
     * 1. Get and store resource pool links and network interface links by
     *    querying given instance IDs.
     * 2. Get and store network interface ids from network interface links and security group links and subnet
     *    links by querying network interface ids.
     * 3. Get and store IDs by querying, resource pool links, subnet links and security group links.
     * @param instanceIdList List of instance IDs provisioned by the test
     */
    private void storeDocumentLinksAndIds(List<String> instanceIdList) {
        storeDocumentLinksFromComputeStates(instanceIdList);

        storeDocumentLinksFromNetworkInterfaceStates();

        // Stores document ids obtained from document links of the resources
        storeEnumeratedDocumentIdsByQueryingLinks(this.resourcePoolLinks, this.resourcePoolIds,
                ResourcePoolState.class);

        if (!this.networkInterfaceLinks.isEmpty()) {
            storeEnumeratedDocumentIdsByQueryingLinks(this.securityGroupLinks, this.securityGroupIds,
                    SecurityGroupState.class);
            storeEnumeratedDocumentIdsByQueryingLinks(this.subnetLinks, this.subnetIds, SubnetState.class);
        }
    }

    /**
     * Gets and stores resource pool links and network interface links by querying
     * given instance IDs.
     * @param instanceIdList List of instance IDs provisioned by the test
     */
    private void storeDocumentLinksFromComputeStates(List<String> instanceIdList) {
        // Query to get all compute state documents associated with list of instance IDs.
        QueryTask.Query computeStateQuery = QueryTask.Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addInClause(ComputeState.FIELD_NAME_ID, instanceIdList)
                .build();
        QueryTask q = QueryTask.Builder.createDirectTask()
                .setQuery(computeStateQuery)
                .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
                .build();

        Operation queryComputeState = Operation.createPost(UriUtils.buildUri(this.host.getUri(),
                ServiceUriPaths.CORE_LOCAL_QUERY_TASKS)).setReferer(this.host.getUri()).setBody(q);

        Operation queryResponse = this.host.waitForResponse(queryComputeState);

        Assert.assertTrue("Error retrieving compute states",
                queryResponse.getStatusCode() == 200);

        QueryTask qt = queryResponse.getBody(QueryTask.class);

        // Store all compute links
        this.computeStateLinks.addAll(qt.results.documentLinks);

        // Store resource pool links and network links from all compute states.
        for (String documentLink : this.computeStateLinks) {

            ComputeState cs = Utils.fromJson(qt.results.documents.get(
                    documentLink), ComputeState.class);

            this.resourcePoolLinks.add(cs.resourcePoolLink);
            this.networkInterfaceLinks.addAll(cs.networkInterfaceLinks);
        }
    }

    /**
     * Gets and stores network interface ids from network interface links and security group links, subnet
     * links by querying network interface ids.
     */
    private void storeDocumentLinksFromNetworkInterfaceStates() {
        // If there are no network interface links, return.
        if (this.networkInterfaceLinks.isEmpty()) {
            return;
        }

        // Get network interface IDs from network interface links.
        for (String s : this.networkInterfaceLinks) {
            Operation op = Operation.createGet(UriUtils.buildUri(this.host.getUri(), s))
                    .setReferer(this.host.getUri());
            Operation response = this.host.waitForResponse(op);
            Assert.assertTrue("Error retrieving network interface IDs",
                    response.getStatusCode() == 200);
            NetworkInterfaceState state = response
                    .getBody(NetworkInterfaceState.class);
            this.networkInterfaceIds.add(state.id);
        }

        // Query all network interface documents associated with list of network interface IDs.
        QueryTask.Query networkInterfaceQuery = QueryTask.Query.Builder.create()
                .addKindFieldClause(NetworkInterfaceState.class)
                .addInClause(NetworkInterfaceState.FIELD_NAME_ID, this.networkInterfaceIds)
                .build();
        QueryTask q = QueryTask.Builder.createDirectTask()
                .setQuery(networkInterfaceQuery)
                .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
                .build();

        Operation queryNetworkInterface = Operation.createPost(UriUtils.buildUri(this.host.getUri(),
                ServiceUriPaths.CORE_LOCAL_QUERY_TASKS)).setReferer(this.host.getUri()).setBody(q);

        Operation queryResponse = this.host.waitForResponse(queryNetworkInterface);

        Assert.assertTrue("Error retrieving network interface states",
                queryResponse.getStatusCode() == 200);

        QueryTask qt = queryResponse.getBody(QueryTask.class);

        // Store security group links and subnet links.
        for (String documentLink : qt.results.documentLinks) {
            NetworkInterfaceState nis = Utils.fromJson(qt.results.documents.get(
                    documentLink), NetworkInterfaceState.class);

            this.securityGroupLinks.addAll(nis.securityGroupLinks);
            this.subnetLinks.add(nis.subnetLink);
        }
    }

    /**
     * Stores the set of IDs associated with set of document links for resources by doing GETs.
     * @param selfLinks Set of document links for the resource.
     * @param idSet Set to store the resource IDs.
     * @param classType Type of resource.
     */
    private <T> void storeEnumeratedDocumentIdsByQueryingLinks (Set<String> selfLinks, Set<String> idSet,
                                                                Class<T> classType) {
        for (String s : selfLinks) {
            Operation.createGet(UriUtils.buildUri(this.host.getUri(), s)).setReferer(this.host.getUri())
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            this.host.log(Level.SEVERE, "Error getting resource document ids");
                        }
                        Object obj = o.getBody(classType);
                        if (obj instanceof ResourceState) {
                            ResourceState state = (ResourceState) obj;
                            idSet.add(state.id);
                        }
                    }).sendWith(this.host);
        }
    }

    /**
     * Query and obtain the total number of documents associated with set of ids for every resource.
     * For every resource the number of document ids and number of total documents
     * associated with those ids should be the same.
     */
    public void assertDocumentCounts() {
        // If asserting after deleting provisioned instances, there should be 0 compute state links.
        if (this.postDeletion) {
            Assert.assertTrue("Compute document count mismatch during enumeration",
                    0 == this.computeStateLinks.size());
        } else {
            Assert.assertTrue("Compute document count mismatch during enumeration after deletion",
                    this.instanceIds.size() == this.computeStateLinks.size());
        }

        Assert.assertTrue("Resource pool document count mismatch during enumeration",
                getEnumeratedDocumentCountByQueryingIds(this.resourcePoolIds,
                ResourcePoolState.class) == this.resourcePoolIds.size());
        Assert.assertTrue("Security group document count mismatch during enumeration",
                getEnumeratedDocumentCountByQueryingIds(this.securityGroupIds,
                SecurityGroupState.class) == this.securityGroupIds.size());
        Assert.assertTrue("Subnet document count mismatch during enumeration",
                getEnumeratedDocumentCountByQueryingIds(this.subnetIds,
                SubnetState.class) == this.subnetIds.size());
    }

    /**
     * Returns the total count of documents associated with set of resource IDs provided by querying
     * resources using document links.
     * @param idSet Set of IDs for the resource.
     * @param classType Type of resource.
     * @return Count of documents associated with set of IDs.
     */
    private <T extends ServiceDocument> int getEnumeratedDocumentCountByQueryingIds(Set<String> idSet,
                                                                                    Class<T> classType) {
        if (idSet.isEmpty()) {
            return 0;
        }

        QueryTask.Query documentCountQuery = QueryTask.Query.Builder.create()
                .addKindFieldClause(classType)
                .addInClause(FIELD_NAME_ID, idSet)
                .build();
        QueryTask q = QueryTask.Builder.createDirectTask()
                .setQuery(documentCountQuery)
                .build();

        Operation queryDocumentCount = Operation.createPost(UriUtils.buildUri(this.host.getUri(),
                ServiceUriPaths.CORE_LOCAL_QUERY_TASKS)).setReferer(this.host.getUri()).setBody(q);

        Operation queryResponse = this.host.waitForResponse(queryDocumentCount);

        Assert.assertTrue("Error retrieving enumerated documents",
                queryResponse.getStatusCode() == 200);

        QueryTask qt = queryResponse.getBody(QueryTask.class);
        return qt.results.documentLinks.size();
    }

    /**
     * Clear all sets to reuse them and store document links and ids after deletion.
     */
    private void clearStoredDocumentLinksAndIds() {
        this.computeStateLinks.clear();
        this.resourcePoolLinks.clear();
        this.networkInterfaceLinks.clear();
        this.securityGroupLinks.clear();
        this.subnetLinks.clear();
        this.resourcePoolIds.clear();
        this.networkInterfaceIds.clear();
        this.securityGroupIds.clear();
        this.subnetIds.clear();
    }

    /**
     * Creates the state associated with the resource pool, compute host and the VM to be created.
     *
     * @throws Throwable
     */
    private void initResourcePoolAndComputeHost() throws Throwable {
        // Create a resource pool where the VM will be housed
        ResourcePoolState resourcePool = createAWSResourcePool(this.host);

        AuthCredentialsServiceState auth = createAWSAuthentication(this.host, this.accessKey, this.secretKey);

        this.endpointState = TestAWSSetupUtils.createAWSEndpointState(this.host, auth.documentSelfLink, resourcePool.documentSelfLink);

        // create a compute host for the AWS EC2 VM
        this.computeHost = createAWSComputeHost(this.host,
                this.endpointState,
                null /*zoneId*/, this.useAllRegions ? null : regionId,
                this.isAwsClientMock,
                this.awsMockEndpointReference, null /*tags*/);
    }

    /**
     * Delete the VMs and NICs created during the test.
     */
    private void tearDownAwsVMs() {
        try {
            // Delete all vms from the endpoint that were provisioned from the test.
            this.host.log("Deleting %d instance created from the test ",
                    this.instancesToCleanUp.size());
            if (this.instancesToCleanUp.size() >= 0) {
                deleteVMsOnThisEndpoint(this.host, this.isMock,
                        this.computeHost.documentSelfLink, this.instancesToCleanUp);
                // Check that all the instances that are required to be deleted are in
                // terminated state on AWS
                waitForInstancesToBeTerminated(this.client, this.host, this.instancesToCleanUp);
                this.instancesToCleanUp.clear();
            }
            //Delete newly created NIC
            deleteNICDirectlyWithEC2Client(this.client, this.host, this.nicToCleanUp);
        } catch (Throwable deleteEx) {
            // just log and move on
            this.host.log(Level.WARNING, "Exception deleting VMs - %s, instance ids - %s",
                    deleteEx.getMessage(), this.instancesToCleanUp);
        }
    }
}