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

import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.DISK_MODE_PERSISTENT;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.LIMIT_IOPS;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.MOREF;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.PROVISION_TYPE;
import static com.vmware.photon.controller.model.adapters.vsphere.CustomProperties.SHARES_LEVEL;
import static com.vmware.photon.controller.model.adapters.vsphere.VSphereAdapterResizeComputeService.COMPUTE_CPU_COUNT;
import static com.vmware.photon.controller.model.adapters.vsphere.VSphereAdapterResizeComputeService.REBOOT_VM_FLAG;
import static com.vmware.photon.controller.model.adapters.vsphere.VSphereEndpointAdapterService.HOST_NAME_KEY;
import static com.vmware.photon.controller.model.tasks.TestUtils.doPost;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapterapi.ResourceOperationResponse;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperation;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationRequest;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.vsphere.constants.VSphereConstants;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimPath;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BasicConnection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.GetMoRef;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.SnapshotService.SnapshotState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.security.PhotonModelSecurityServices;
import com.vmware.photon.controller.model.security.service.SslTrustCertificateService;
import com.vmware.photon.controller.model.security.service.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.photon.controller.model.security.ssl.ServerX509TrustManager;
import com.vmware.photon.controller.model.security.ssl.X509TrustManagerResolver;
import com.vmware.photon.controller.model.security.util.CertificateUtil;
import com.vmware.photon.controller.model.support.LifecycleState;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService.EndpointAllocationTaskState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService.ResourceEnumerationTaskState;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService.ResourceRemovalTaskState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.vim25.ArrayOfVirtualDevice;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.vim25.SharesLevel;
import com.vmware.vim25.VirtualDisk;
import com.vmware.vim25.VirtualDiskType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.QueryResultsProcessor;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.TestProperty;
import com.vmware.xenon.common.test.TestRequestSender;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.ExampleService;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Builder;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskService.TaskServiceState;

public class BaseVSphereAdapterTest {

    public static final String DEFAULT_AUTH_TYPE = "Username/Password";

    public String vcUrl = System.getProperty(TestProperties.VC_URL);
    public String vcUsername = System.getProperty(TestProperties.VC_USERNAME);
    public String vcPassword = System.getProperty(TestProperties.VC_PASSWORD);

    public String datacenterId = System.getProperty(TestProperties.VC_DATECENTER_ID);
    public String dataStoreId = System.getProperty(TestProperties.VC_DATASTORE_ID);
    public String diskDataStoreId = System.getProperty(TestProperties.VC_DISK_DATASTORE_ID);
    public String networkId = System.getProperty(TestProperties.VC_NETWORK_ID);

    public String vcFolder = System.getProperty(TestProperties.VC_FOLDER);
    public String spName = System.getProperty(TestProperties.VC_STORAGE_POLICY_NAME, "");
    public String spId = System.getProperty(TestProperties.VC_STORAGE_POLICY_ID, "");

    protected VerificationHost host;
    protected AuthCredentialsServiceState auth;
    protected ResourcePoolState resourcePool;
    public NetworkInterfaceDescription nicDescription;
    public SubnetState subnet;

    protected static final long ADDITIONAL_DISK_SIZE = 1024;

    @Rule
    public TestName testName = new TestName();

    protected EndpointState createEndpoint(Consumer<ComputeState> cs,
            Consumer<ComputeDescription> desc) throws Throwable {
        EndpointAllocationTaskState validateEndpoint = new EndpointAllocationTaskState();
        EndpointState endpoint = new EndpointState();
        validateEndpoint.endpointState = endpoint;

        endpoint.endpointType = PhotonModelConstants.EndpointType.vsphere.name();
        endpoint.name = PhotonModelConstants.EndpointType.vsphere.name();
        endpoint.regionId = this.datacenterId;

        endpoint.endpointProperties = new HashMap<>();
        endpoint.endpointProperties.put(PRIVATE_KEYID_KEY,
                this.vcUsername != null ? this.vcUsername : "username");
        endpoint.endpointProperties.put(PRIVATE_KEY_KEY,
                this.vcPassword != null ? this.vcPassword : "password");
        endpoint.endpointProperties.put(HOST_NAME_KEY,
                this.vcUrl != null ? URI.create(this.vcUrl).toURL().getHost() : "hostname");
        validateEndpoint.options = isMock() ? EnumSet.of(TaskOption.IS_MOCK) : null;
        configureEndpoint(endpoint);

        EndpointAllocationTaskState outTask = TestUtils
                .doPost(this.host, validateEndpoint,
                        EndpointAllocationTaskState.class,
                        UriUtils.buildUri(this.host, EndpointAllocationTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(
                EndpointAllocationTaskState.class,
                outTask.documentSelfLink);

        EndpointAllocationTaskState taskState = this.host.getServiceState(EnumSet.noneOf(TestProperty.class),
                EndpointAllocationTaskState.class,
                UriUtils.buildUri(this.host, outTask.documentSelfLink));

        if (cs != null) {
            cs.accept(this.host.getServiceState(
                    EnumSet.noneOf(TestProperty.class),
                    ComputeState.class,
                    UriUtils.buildUri(this.host, taskState.endpointState.computeLink)
            ));
        }

        if (desc != null) {
            desc.accept(this.host.getServiceState(
                    EnumSet.noneOf(TestProperty.class),
                    ComputeDescription.class,
                    UriUtils.buildUri(this.host, taskState.endpointState.computeDescriptionLink)
            ));
        }

        this.resourcePool = this.host.getServiceState(
                EnumSet.noneOf(TestProperty.class),
                ResourcePoolState.class,
                UriUtils.buildUri(this.host, taskState.endpointState.resourcePoolLink));
        this.auth = this.host.getServiceState(
                EnumSet.noneOf(TestProperty.class),
                AuthCredentialsServiceState.class,
                UriUtils.buildUri(this.host, taskState.endpointState.authCredentialsLink));

        return taskState.endpointState;
    }

    @Before
    public void setUp() throws Throwable {
        this.host = VerificationHost.create(Integer.getInteger(TestProperties.HOST_PREFERRED_PORT, 0));

        String bindingAddress = System.getProperty(TestProperties.HOST_BINDING_ADDRESS);
        if (!StringUtils.isEmpty(bindingAddress)) {
            this.host.setBindAddress(bindingAddress);
        }
        this.host.start();
        this.host.waitForServiceAvailable(ExampleService.FACTORY_LINK);

        // TODO: VSYM-992 - improve test/fix arbitrary timeout
        // must be at least 15min as default timeout to get an IP is 10min
        this.host.setTimeoutSeconds(15 * 60);

        try {
            PhotonModelAdaptersRegistryAdapters.startServices(this.host);
            PhotonModelServices.startServices(this.host);
            PhotonModelMetricServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);

            PhotonModelSecurityServices.startServices(this.host);

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelSecurityServices.LINKS);

            startAdditionalServices();
            ServerX509TrustManager.create(this.host);
        } catch (Throwable e) {
            this.host.log("Error starting up services for the test %s", e.getMessage());
            throw new Exception(e);
        }

        if (this.vcUrl == null) {
            this.vcUrl = "http://not-configured";
        } else {
            X509TrustManagerResolver resolver = CertificateUtil.resolveCertificate(URI.create(this.vcUrl), 20000);
            if (!resolver.isCertsTrusted()) {
                SslTrustCertificateState certState = new SslTrustCertificateState();

                certState.certificate = CertificateUtil.toPEMformat(resolver.getCertificate());
                SslTrustCertificateState.populateCertificateProperties(
                        certState,
                        resolver.getCertificate());

                Operation op = Operation.createPost(this.host, SslTrustCertificateService.FACTORY_LINK)
                        .setReferer(this.host.getReferer())
                        .setBody(certState);

                this.host.waitForResponse(op);
            }
        }

        if (this.dataStoreId != null) {
            this.dataStoreId = this.dataStoreId
                    .substring(this.dataStoreId.lastIndexOf("/") + 1, this.dataStoreId.length());
        }
        doSetup();
    }

    /**
     * Hook to customized endoint before it is sibmitted for creation.
     * @param endpoint
     */
    protected void configureEndpoint(EndpointState endpoint) {
    }

    protected void startAdditionalServices() throws Throwable {
        VSphereAdapters.startServices(this.host);
        this.host.waitForServiceAvailable(VSphereAdapters.CONFIG_LINK);
    }

    protected void doSetup() {

    }

    @After
    public void tearDown() throws InterruptedException {
        if (this.host == null) {
            return;
        }

        this.host.tearDownInProcessPeers();
        this.host.toggleNegativeTestMode(false);
        this.host.tearDown();
    }

    public boolean isMock() {
        return this.vcUsername == null || this.vcUsername.length() == 0;
    }

    protected void assertInternalPropertiesSet(ComputeState vm) {
        CustomProperties props = CustomProperties.of(vm);
        assertNotNull(props.getMoRef(CustomProperties.MOREF));
        assertNotNull(props.getString(CustomProperties.TYPE));
    }

    public BasicConnection createConnection() {
        if (isMock()) {
            throw new IllegalStateException("Cannot create connection in while mock is true");
        }

        BasicConnection connection = new BasicConnection();
        connection.setIgnoreSslErrors(true);
        connection.setUsername(this.vcUsername);
        connection.setPassword(this.vcPassword);
        connection.setURI(URI.create(this.vcUrl));
        connection.connect();
        return connection;
    }

    protected ProvisionComputeTaskState createProvisionTask(ComputeState vm) throws Throwable {
        ProvisionComputeTaskState provisionTask = new ProvisionComputeTaskState();

        provisionTask.computeLink = vm.documentSelfLink;
        provisionTask.isMockRequest = isMock();
        provisionTask.taskSubStage = ProvisionComputeTaskState.SubStage.CREATING_HOST;

        ProvisionComputeTaskState outTask = TestUtils.doPost(this.host,
                provisionTask,
                ProvisionComputeTaskState.class,
                UriUtils.buildUri(this.host,
                        ProvisionComputeTaskService.FACTORY_LINK));

        return outTask;
    }

    protected ComputeState getComputeState(ComputeState vm) throws Throwable {
        return this.host.getServiceState(null, ComputeState.class,
                UriUtils.buildUri(this.host, vm.documentSelfLink));
    }

    protected NetworkState getNetworkState(NetworkState net) throws Throwable {
        return this.host.getServiceState(null, NetworkState.class,
                UriUtils.buildUri(this.host, net.documentSelfLink));
    }

    protected SubnetState getSubnetState(SubnetState net) throws Throwable {
        return this.host.getServiceState(null, SubnetState.class,
                UriUtils.buildUri(this.host, net.documentSelfLink));
    }

    protected void snapshotFactoryState(String tag, Class<? extends StatefulService> factoryClass)
            throws ExecutionException, InterruptedException, IOException {
        URI uri = UriUtils.buildFactoryUri(this.host, factoryClass);
        uri = UriUtils.extendUriWithQuery(uri, "expand", "true");
        Operation res = this.host
                .sendWithFuture(Operation.createGet(uri).setReferer(this.host.getPublicUri()))
                .get();

        File out = new File("target", factoryClass.getSimpleName() + "-" + tag + ".json");
        try (FileWriter writer = new FileWriter(out)) {
            writer.write(Utils.toJsonHtml(res.getBody(ServiceDocumentQueryResult.class)));
        }
    }

    protected ResourcePoolState createResourcePool()
            throws Throwable {
        ResourcePoolState inPool = new ResourcePoolState();
        inPool.name = nextName("rp");
        inPool.id = inPool.name;

        inPool.minCpuCount = 1L;
        inPool.minMemoryBytes = 1024L;

        ResourcePoolState returnPool =
                TestUtils.doPost(this.host, inPool, ResourcePoolState.class,
                        UriUtils.buildUri(this.host, ResourcePoolService.FACTORY_LINK));

        return returnPool;
    }

    protected void awaitTaskEnd(TaskServiceState outTask) throws Throwable {
        this.host.waitForFinishedTask(outTask.getClass(), outTask.documentSelfLink);
    }

    protected AuthCredentialsServiceState createAuth() throws Throwable {
        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.type = DEFAULT_AUTH_TYPE;
        auth.privateKeyId = this.vcUsername;
        auth.privateKey = this.vcPassword;
        auth.documentSelfLink = UUID.randomUUID().toString();

        AuthCredentialsServiceState result = TestUtils
                .doPost(this.host, auth, AuthCredentialsServiceState.class,
                        UriUtils.buildUri(this.host, AuthCredentialsService.FACTORY_LINK));
        return result;
    }

    protected NetworkState createNetwork(String name) throws Throwable {
        if (name == null) {
            name = "name-not-defined";
        }
        NetworkState net = new NetworkState();
        net.adapterManagementReference = getAdapterManagementReference();
        net.authCredentialsLink = this.auth.documentSelfLink;
        net.name = name;
        net.instanceAdapterReference = UriUtils
                .buildUri(this.host, VSphereUriPaths.INSTANCE_SERVICE);
        net.subnetCIDR = "0.0.0.0/0";
        net.resourcePoolLink = this.resourcePool.documentSelfLink;

        if (this.datacenterId == null) {
            net.regionId = "datacenter-not-defined";
        } else {
            net.regionId = this.datacenterId;
        }
        ManagedObjectReference ref = new ManagedObjectReference();
        ref.setValue("network-3");
        ref.setType(VimNames.TYPE_NETWORK);
        CustomProperties.of(net)
                .put(CustomProperties.TYPE, VimNames.TYPE_NETWORK)
                .put(CustomProperties.MOREF, ref);

        return TestUtils.doPost(this.host, net, NetworkState.class,
                UriUtils.buildUri(this.host, NetworkService.FACTORY_LINK));
    }

    protected void deleteVmAndWait(ComputeState vm) {
        try {
            ResourceRemovalTaskState deletionState = new ResourceRemovalTaskState();
            deletionState.isMockRequest = isMock();
            QuerySpecification resourceQuerySpec = new QuerySpecification();
            resourceQuerySpec.query
                    .setTermPropertyName(ServiceDocument.FIELD_NAME_SELF_LINK)
                    .setTermMatchValue(vm.documentSelfLink);

            deletionState.resourceQuerySpec = resourceQuerySpec;
            ResourceRemovalTaskState outDelete = TestUtils.doPost(this.host,
                    deletionState,
                    ResourceRemovalTaskState.class,
                    UriUtils.buildUri(this.host,
                            ResourceRemovalTaskService.FACTORY_LINK));

            awaitTaskEnd(outDelete);
        } catch (Throwable e) {
            this.host.log("Error deleting VM %s", e.getMessage());
        }
    }

    protected void rebootVSphereVMAndWait(ComputeState computeState) {
        String taskLink = UUID.randomUUID().toString();

        ResourceOperationRequest rebootVMRequest = getResourceOperationRequest("Reboot",
                computeState.documentSelfLink, taskLink);
        Operation rebootOp = Operation
                .createPatch(UriUtils.buildUri(this.host, VSphereAdapterD2PowerOpsService.SELF_LINK))
                .setBody(rebootVMRequest)
                .setReferer(this.host.getReferer())
                .setCompletion((o, e) -> {
                    Assert.assertNull(e);
                });

        TestRequestSender sender = new TestRequestSender(this.host);
        sender.sendRequest(rebootOp);
        this.host.log("Waiting for the machine to be rebooted");

        ComputeState[] cState = new ComputeState[1];

        this.host.waitFor("Reboot request failed", () -> {
            cState[0] = this.host.getServiceState(null, ComputeState.class,
                    UriUtils.buildUri(this.host, computeState.documentSelfLink));
            if (cState[0].powerState.equals(ComputeService.PowerState.ON)) {
                return true;
            } else {
                return false;
            }
        });
        assertEquals(ComputeService.PowerState.ON, cState[0].powerState);
        assertNotNull(cState[0].address);
        this.host.log("Reboot operation completed successfully");
    }

    protected void suspendVSphereVM(ComputeState computeState) {
        String taskLink = UUID.randomUUID().toString();

        ResourceOperationRequest suspendVMRequest = getResourceOperationRequest("Suspend",
                computeState.documentSelfLink, taskLink);

        TestContext ctx = this.host.testCreate(1);
        createTaskResultListener(this.host, taskLink, (u) -> {
            if (u.getAction() != Service.Action.PATCH) {
                return false;
            }
            ResourceOperationResponse response = u.getBody(ResourceOperationResponse.class);
            if (TaskState.isFailed(response.taskInfo)) {
                ctx.failIteration(
                        new IllegalStateException(response.taskInfo.failure.message));
            } else {
                ctx.completeIteration();
            }
            return true;
        });
        TestContext ctx2 = this.host.testCreate(1);
        Operation suspendOp = Operation
                .createPatch(UriUtils.buildUri(this.host, VSphereAdapterD2PowerOpsService.SELF_LINK))
                .setBody(suspendVMRequest)
                .setReferer(this.host.getReferer())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        ctx2.failIteration(e);
                        return;
                    }
                    ctx2.completeIteration();
                });
        this.host.send(suspendOp);
        ctx2.await();

        ComputeState[] cstate = new ComputeState[1];

        this.host.waitFor("Suspend request failed", () -> {
            cstate[0] = this.host.getServiceState(null, ComputeState.class,
                    UriUtils.buildUri(this.host, computeState.documentSelfLink));
            if (cstate[0].powerState.equals(ComputeService.PowerState.SUSPEND)) {
                assertTrue(cstate[0].address.isEmpty());
                return true;
            } else {
                return false;
            }
        });
        assertEquals(ComputeService.PowerState.SUSPEND, cstate[0].powerState);
    }

    protected void shutdownGuestOS(ComputeState computeState) {
        String taskLink = UUID.randomUUID().toString();

        ResourceOperationRequest shutdownGuestRequest = getResourceOperationRequest("Shutdown",
                computeState.documentSelfLink, taskLink);

        TestContext ctx = this.host.testCreate(1);
        createTaskResultListener(this.host, taskLink, (u) -> {
            if (u.getAction() != Service.Action.PATCH) {
                return false;
            }
            ResourceOperationResponse response = u.getBody(ResourceOperationResponse.class);
            if (TaskState.isFailed(response.taskInfo)) {
                ctx.failIteration(
                        new IllegalStateException(response.taskInfo.failure.message));
            } else {
                ctx.completeIteration();
            }
            return true;
        });
        TestContext ctx2 = this.host.testCreate(1);
        Operation shutdownOp = Operation
                .createPatch(UriUtils.buildUri(this.host, VSphereAdapterD2PowerOpsService.SELF_LINK))
                .setBody(shutdownGuestRequest)
                .setReferer(this.host.getReferer())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        ctx2.failIteration(e);
                        return;
                    }
                    ctx2.completeIteration();
                });
        this.host.send(shutdownOp);
        ctx2.await();

        ComputeState[] cState = new ComputeState[1];
        this.host.waitFor("Guest shutdown request failed", () -> {
            cState[0] = this.host.getServiceState(null, ComputeState.class,
                    UriUtils.buildUri(this.host, computeState.documentSelfLink));

            if (cState[0].powerState.equals(ComputeService.PowerState.OFF)) {
                assertTrue(cState[0].address.isEmpty());
                return true;
            } else {
                return false;
            }
        });
        assertEquals(ComputeService.PowerState.OFF, cState[0].powerState);
    }

    protected void resetVSphereVM(ComputeState computeState) {
        String taskLink = UUID.randomUUID().toString();

        ResourceOperationRequest resetVMRequest = getResourceOperationRequest("Reset",
                computeState.documentSelfLink, taskLink);

        TestContext ctx = this.host.testCreate(1);
        createTaskResultListener(this.host, taskLink, (u) -> {
            if (u.getAction() != Service.Action.PATCH) {
                return false;
            }
            ResourceOperationResponse response = u.getBody(ResourceOperationResponse.class);
            if (TaskState.isFailed(response.taskInfo)) {
                ctx.failIteration(
                        new IllegalStateException(response.taskInfo.failure.message));
            } else {
                ctx.completeIteration();
            }
            return true;
        });
        TestContext ctx2 = this.host.testCreate(1);
        Operation resetOp = Operation
                .createPatch(UriUtils.buildUri(this.host, VSphereAdapterD2PowerOpsService.SELF_LINK))
                .setBody(resetVMRequest)
                .setReferer(this.host.getReferer())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        ctx2.failIteration(e);
                        return;
                    }
                    ctx2.completeIteration();
                });
        this.host.send(resetOp);
        ctx2.await();

        ComputeState[] cState = new ComputeState[1];
        this.host.waitFor("Reset VM request failed", () -> {
            cState[0] = this.host.getServiceState(null, ComputeState.class,
                    UriUtils.buildUri(this.host, computeState.documentSelfLink));

            if (ComputeService.PowerState.ON.equals(cState[0].powerState)) {
                return true;
            } else {
                return false;
            }
        });
        assertEquals(ComputeService.PowerState.ON, cState[0].powerState);
    }

    protected void createSnapshotAndWait(ComputeState vm, Boolean isSnapshottedAgain) throws Throwable {

        String taskLink = UUID.randomUUID().toString();

        ResourceOperationRequest snapshotRequest = getCreateSnapshotRequest(ResourceOperation.CREATE_SNAPSHOT.operation,
                vm.documentSelfLink, taskLink);

        Operation createSnapshotOp = Operation
                .createPatch(UriUtils.buildUri(this.host, VSphereAdapterSnapshotService.SELF_LINK))
                .setBody(snapshotRequest)
                .setReferer(this.host.getReferer())
                .setCompletion((o, e) -> Assert.assertNull(e));

        TestRequestSender sender = new TestRequestSender(this.host);
        sender.sendRequest(createSnapshotOp);
        this.host.log("Waiting for the snapshot to be created");

        this.host.waitFor("Create snapshot request failed", () -> {
            ComputeState cState = this.host.getServiceState(null, ComputeState.class,
                    UriUtils.buildUri(this.host, vm.documentSelfLink));
            SnapshotState snapshotState = querySnapshotState(vm.documentSelfLink, true);
            String hasSnapshot = cState.customProperties.get(ComputeProperties.CUSTOM_PROP_COMPUTE_HAS_SNAPSHOTS);
            if (!isSnapshottedAgain) {
                if (hasSnapshot != null && Boolean.parseBoolean(hasSnapshot) &&
                        snapshotState.parentLink == null) {
                    return true;
                } else {
                    return false;
                }
            } else {
                if (hasSnapshot != null && Boolean.parseBoolean(hasSnapshot) &&
                        snapshotState.parentLink != null) {
                    return true;
                } else {
                    return false;
                }
            }
        });
        this.host.log("Create snapshot operation completed successfully");
    }

    protected void createSnapshotAndWaitFailure(ComputeState vm) throws Throwable {
        String taskLink = UUID.randomUUID().toString();
        ResourceOperationRequest snapshotRequest = getCreateSnapshotRequest(ResourceOperation.CREATE_SNAPSHOT.operation,
                vm.documentSelfLink, taskLink);

        Operation createSnapshotOp = Operation
                .createPatch(UriUtils.buildUri(this.host, VSphereAdapterSnapshotService.SELF_LINK))
                .setBody(snapshotRequest)
                .setReferer(this.host.getReferer())
                .setCompletion((o, e) -> Assert.assertNull(e));
        TestContext ctx2 = this.host.testCreate(1);
        createTaskResultListener(this.host, taskLink, (u) -> {
            if (u.getAction() != Service.Action.PATCH) {
                return false;
            }
            ResourceOperationResponse response = u.getBody(ResourceOperationResponse.class);
            if (TaskState.isFailed(response.taskInfo)) {
                ctx2.completeIteration();
                return true;
            } else {
                ctx2.completeIteration();
                return false;
            }
        });
        this.host.send(createSnapshotOp);
        ctx2.await();
    }

    protected SnapshotState getSnapshots(ComputeState computeState) throws Throwable {
        return querySnapshotState(computeState.documentSelfLink, true);
    }

    protected void deleteSnapshotAndWait(ComputeState computeState) throws Throwable {

        // Get the snapshot associated with the compute
        SnapshotState snapshotState = querySnapshotState(computeState.documentSelfLink, true);

        String taskLink = UUID.randomUUID().toString();

        ResourceOperationRequest snapshotRequest = getDeleteOrRevertSnapshotRequest(
                ResourceOperation.DELETE_SNAPSHOT.operation, "DELETE", snapshotState.documentSelfLink, taskLink);

        Operation deleteSnapshotOp = Operation
                .createPatch(UriUtils.buildUri(this.host, VSphereAdapterSnapshotService.SELF_LINK))
                .setBody(snapshotRequest)
                .setReferer(this.host.getReferer())
                .setCompletion((o, e) -> Assert.assertNull(e));

        TestRequestSender sender = new TestRequestSender(this.host);
        sender.sendRequest(deleteSnapshotOp);
        this.host.log("Waiting for the snapshot to be deleted");

        this.host.waitFor("Delete snapshot request failed", () -> {
            SnapshotState finalSnapshotState = querySnapshotState(computeState.documentSelfLink, true);
            ComputeState finalComputeState = this.host.getServiceState(null, ComputeState.class,
                    UriUtils.buildUri(this.host, computeState.documentSelfLink));
            String hasSnapshot = finalComputeState.customProperties
                    .get(ComputeProperties.CUSTOM_PROP_COMPUTE_HAS_SNAPSHOTS);
            // Check for the snapshot state and _hasSnapshots flag in compute. Since we are deleting
            // the current snapshot, the vm is left with one more snapshot (which is the child of the deleted )
            // snapshot. So hasSnapshot will still be true.
            if (hasSnapshot != null && Boolean.parseBoolean(hasSnapshot)
                    && finalSnapshotState == null) {
                return true;
            } else {
                return false;
            }
        });
        this.host.log("Delete snapshot operation completed successfully");
    }

    protected void revertToSnapshotAndWait(ComputeState computeState) throws Throwable {

        // Get the snapshot to revert to
        SnapshotState snapshotStateToRevertTo = querySnapshotState(computeState.documentSelfLink, false);

        String taskLink = UUID.randomUUID().toString();

        ResourceOperationRequest snapshotRequest = getDeleteOrRevertSnapshotRequest(
                ResourceOperation.REVERT_SNAPSHOT.operation, "REVERT", snapshotStateToRevertTo.documentSelfLink,
                taskLink);

        Operation revertSnapshotOp = Operation
                .createPatch(UriUtils.buildUri(this.host, VSphereAdapterSnapshotService.SELF_LINK))
                .setBody(snapshotRequest)
                .setReferer(this.host.getReferer())
                .setCompletion((o, e) -> Assert.assertNull(e));

        TestRequestSender sender = new TestRequestSender(this.host);
        sender.sendRequest(revertSnapshotOp);
        this.host.log("Waiting for the snapshot to be reverted");

        this.host.waitFor("Revert snapshot request failed", () -> {
            SnapshotState finalSnapshotState = querySnapshotState(computeState.documentSelfLink, true);
            ComputeState finalComputeState = this.host.getServiceState(null, ComputeState.class,
                    UriUtils.buildUri(this.host, computeState.documentSelfLink));
            String hasSnapshot = finalComputeState.customProperties
                    .get(ComputeProperties.CUSTOM_PROP_COMPUTE_HAS_SNAPSHOTS);
            // Check for the snapshot state and _hasSnapshots flag in compute
            if (hasSnapshot != null && Boolean.parseBoolean(hasSnapshot)
                    && finalSnapshotState.documentSelfLink.equals(snapshotStateToRevertTo.documentSelfLink)) {
                return true;
            } else {
                return false;
            }
        });
        this.host.log("Revert to snapshot operation completed successfully");
    }

    protected void resizeVM(ComputeState vm) {
        String taskLink = UUID.randomUUID().toString();
        ResourceOperationRequest resizeRequest = getResizeComputeRequest(ResourceOperation.RESIZE.operation,
                vm.documentSelfLink, taskLink);
        Operation resizeComputeOperation = Operation
                .createPatch(UriUtils.buildUri(this.host, VSphereAdapterResizeComputeService.SELF_LINK))
                .setBody(resizeRequest)
                .setReferer(this.host.getReferer())
                .setCompletion((o, e) -> Assert.assertNull(e));

        TestRequestSender sender = new TestRequestSender(this.host);
        sender.sendRequest(resizeComputeOperation);
        this.host.log("Waiting for the resource resize to complete");
        ComputeState cState = this.host.getServiceState(null, ComputeState.class,
                UriUtils.buildUri(this.host, vm.documentSelfLink));
        this.host.waitFor("Resize compute request failed", () -> {
            Operation op = this.host
                    .waitForResponse(Operation.createGet(this.host, cState.documentSelfLink + "?expand"));
            ComputeService.ComputeStateWithDescription cDesc = op
                    .getBody(ComputeService.ComputeStateWithDescription.class);
            if (cDesc != null && cDesc.description.cpuCount == 4L) {
                return true;
            } else {
                return false;
            }
        });
        this.host.log("Resize compute operation completed successfully");
    }

    protected void verifySnapshotCleanUpAfterVmDelete(ComputeState computeState) {
        this.host.log("Collecting Snapshot states before deleting Vm");
        final List<SnapshotState> list = queryAllSnapshotStates(computeState.documentSelfLink);

        this.host.waitFor("Snapshots not properly updated", () -> {
            // As we created two snapshots
            System.out.println(list.size());
            if (list.size() == 2) {
                return true;
            } else {
                return false;
            }
        });

        deleteVmAndWait(computeState);

        this.host.log("Collecting Snapshot states after deleting Vm");
        final List<SnapshotState> list2 = queryAllSnapshotStates(computeState.documentSelfLink);

        this.host.waitFor("Snapshots not properly deleted", () -> {
            if (list2.isEmpty()) {
                return true;
            } else {
                return false;
            }
        });
    }

    private ResourceOperationRequest getResourceOperationRequest(String operation, String documentSelfLink,
            String taskLink) {
        ResourceOperationRequest resourceOperationRequest = new ResourceOperationRequest();
        resourceOperationRequest.operation = operation;
        resourceOperationRequest.isMockRequest = isMock();
        resourceOperationRequest.resourceReference = UriUtils.buildUri(this.host, documentSelfLink);
        resourceOperationRequest.taskReference = UriUtils.buildUri(this.host, taskLink);
        resourceOperationRequest.payload = new HashMap<>();
        return resourceOperationRequest;
    }

    protected void createTaskResultListener(VerificationHost host, String taskLink,
            Function<Operation, Boolean> h) {
        StatelessService service = new StatelessService() {
            @Override
            public void handleRequest(Operation update) {
                if (!h.apply(update)) {
                    super.handleRequest(update);
                }
            }
        };

        Operation startOp = Operation
                .createPost(host, taskLink)
                .setCompletion(this.host.getCompletion())
                .setReferer(this.host.getReferer());
        this.host.startService(startOp, service);
    }

    protected URI getAdapterManagementReference() {
        return UriUtils.buildUri(this.vcUrl);
    }

    protected ComputeDescription createComputeDescription() throws Throwable {
        ComputeDescription computeDesc = new ComputeDescription();

        computeDesc.id = UUID.randomUUID().toString();
        computeDesc.name = computeDesc.id;
        computeDesc.documentSelfLink = computeDesc.id;
        computeDesc.supportedChildren = new ArrayList<>();
        computeDesc.supportedChildren.add(ComputeType.VM_GUEST.name());

        configureAdapters(computeDesc);

        computeDesc.authCredentialsLink = this.auth.documentSelfLink;

        computeDesc.regionId = this.datacenterId;

        return TestUtils.doPost(this.host, computeDesc,
                ComputeDescription.class,
                UriUtils.buildUri(this.host, ComputeDescriptionService.FACTORY_LINK));
    }

    /**
     * Hook for subclasses to define  alternative adapters.
     *
     * @param computeDesc
     */
    protected void configureAdapters(ComputeDescription computeDesc) {
        computeDesc.instanceAdapterReference = AdapterUriUtil.buildAdapterUri(this.host,
                VSphereUriPaths.INSTANCE_SERVICE);
        computeDesc.enumerationAdapterReference = AdapterUriUtil.buildAdapterUri(this.host,
                VSphereUriPaths.ENUMERATION_SERVICE);
        computeDesc.statsAdapterReference = AdapterUriUtil.buildAdapterUri(this.host,
                VSphereUriPaths.STATS_SERVICE);
        computeDesc.powerAdapterReference = AdapterUriUtil.buildAdapterUri(this.host,
                VSphereUriPaths.POWER_SERVICE);
        computeDesc.diskAdapterReference = AdapterUriUtil.buildAdapterUri(this.host,
                VSphereUriPaths.DISK_SERVICE);
    }

    /**
     * Create a compute host representing a vcenter server
     */
    protected ComputeState createComputeHost(ComputeDescription computeHostDescription)
            throws Throwable {
        ComputeState computeState = new ComputeState();
        computeState.id = UUID.randomUUID().toString();
        computeState.name = computeHostDescription.name;
        computeState.documentSelfLink = computeState.id;
        computeState.descriptionLink = computeHostDescription.documentSelfLink;
        computeState.resourcePoolLink = this.resourcePool.documentSelfLink;
        computeState.adapterManagementReference = getAdapterManagementReference();
        computeState.tenantLinks = Collections.singletonList("/a/tenant");
        computeState.regionId = computeHostDescription.regionId;

        ComputeState returnState = TestUtils.doPost(this.host, computeState,
                ComputeState.class,
                UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
        return returnState;
    }

    protected void enumerateComputes(ComputeState computeHost) throws Throwable {
        enumerateComputes(computeHost, null, null);
    }

    protected void enumerateComputes(ComputeState computeHost, EndpointState endpointState) throws Throwable {
        enumerateComputes(computeHost, endpointState, null);
    }


    protected void enumerateComputes(ComputeState computeHost, EndpointState endpointState,
            EnumSet<TaskOption> options) throws Throwable {
        ResourceEnumerationTaskState task = new ResourceEnumerationTaskState();
        task.adapterManagementReference = computeHost.adapterManagementReference;

        task.enumerationAction = EnumerationAction.REFRESH;
        task.parentComputeLink = computeHost.documentSelfLink;
        task.resourcePoolLink = this.resourcePool.documentSelfLink;
        if (endpointState != null) {
            task.endpointLink = endpointState.documentSelfLink;
        } else {
            task.endpointLink = "/some/endpoint/link";
        }
        task.options = options;

        if (isMock()) {
            if (task.options == null) {
                task.options = EnumSet.of(TaskOption.IS_MOCK);
            } else {
                task.options.add(TaskOption.IS_MOCK);
            }
        }

        ResourceEnumerationTaskState outTask = TestUtils.doPost(this.host,
                task,
                ResourceEnumerationTaskState.class,
                UriUtils.buildUri(this.host,
                        ResourceEnumerationTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(ResourceEnumerationTaskState.class, outTask.documentSelfLink);
    }

    protected Query createQueryForComputeResource() {
        return createQueryForComputeResource(null);
    }

    protected Query createQueryForComputeResource(String vimType) {
        List<String> vimTypes = new ArrayList<>();
        if (vimType != null) {
            vimTypes.add(vimType);
        } else {
            // by default use cluster
            vimTypes.add(VimNames.TYPE_CLUSTER_COMPUTE_RESOURCE);
        }

        return Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addInClause(QuerySpecification.buildCompositeFieldName(
                        ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        CustomProperties.TYPE), vimTypes)
                .build();
    }

    protected <T extends ServiceDocument> T findFirstMatching(Query q, Class<T> type) {
        QueryTask qt = Builder.createDirectTask()
                .setQuery(q)
                .addOption(QueryOption.EXPAND_CONTENT)
                .build();

        Operation result = this.host.waitForResponse(
                Operation.createPost(UriUtils.buildUri(this.host, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
                        .setBody(qt));

        QueryResultsProcessor rp = QueryResultsProcessor.create(result);
        if (!rp.hasResults()) {
            throw new IllegalStateException(
                    "Nothing found for " + Utils.toJsonHtml(q) + ". Expected at least one result");
        }

        return rp.streamDocuments(type).findFirst().orElse(null);
    }

    protected String createNic(String name, String networkLink) throws Throwable {
        // Create Subnet
        this.subnet = new SubnetState();
        this.subnet.networkLink = networkLink;
        this.subnet.endpointLink = "/some/endpoint/link";
        this.subnet.lifecycleState = LifecycleState.READY;
        this.subnet.id = this.subnet.name = this.networkId;
        ManagedObjectReference ref = new ManagedObjectReference();
        ref.setValue("network-4127");
        ref.setType(VimNames.TYPE_NETWORK);
        CustomProperties.of(this.subnet)
                .put(CustomProperties.TYPE, VimNames.TYPE_NETWORK)
                .put(CustomProperties.MOREF, ref);
        this.subnet = TestUtils.doPost(this.host, this.subnet,
                SubnetState.class,
                UriUtils.buildUri(this.host, SubnetService.FACTORY_LINK));

        this.nicDescription = new NetworkInterfaceDescription();
        this.nicDescription.assignment = NetworkInterfaceDescriptionService.IpAssignment.DYNAMIC;
        this.nicDescription = TestUtils.doPost(this.host, this.nicDescription,
                NetworkInterfaceDescription.class,
                UriUtils.buildUri(this.host, NetworkInterfaceDescriptionService.FACTORY_LINK));
        this.nicDescription.subnetLink = this.subnet.documentSelfLink;

        NetworkInterfaceState nic = new NetworkInterfaceState();
        nic.name = name;
        nic.subnetLink = this.subnet.documentSelfLink;
        nic.networkInterfaceDescriptionLink = this.nicDescription.documentSelfLink;

        nic = TestUtils.doPost(this.host, nic,
                NetworkInterfaceState.class,
                UriUtils.buildUri(this.host, NetworkInterfaceService.FACTORY_LINK));

        return nic.documentSelfLink;
    }

    protected String nextName() {
        return nextName(null);
    }

    /**
     * Returns a unique mnemonic string.
     * @param prefix
     * @return
     */
    protected String nextName(String prefix) {
        if (prefix == null || prefix.length() == 0) {
            prefix = "";
        } else {
            prefix = prefix + "-";
        }

        String now = DateTimeFormatter.ofPattern("D_HH_mm_ssSSS").format(LocalDateTime.now());
        return prefix + System.getProperty("user.name") + "-" + now;
    }

    /**
     * Create a new disk state to attach it to the virual machine.
     */
    protected DiskState createDisk(String alias, DiskService.DiskType type, int bootOrder,
            URI sourceImageReference, long capacityMBytes, HashMap<String, String>
            customProperties) throws Throwable {
        DiskState diskState = constructDiskState(alias, type, bootOrder, sourceImageReference,
                capacityMBytes, customProperties);
        return doPost(this.host, diskState, DiskState.class,
                UriUtils.buildUri(this.host, DiskService.FACTORY_LINK));
    }

    /**
     * Create a new disk state to attach it to the virual machine.
     */
    protected DiskState createDiskWithStoragePolicy(String alias, DiskService.DiskType type, int bootOrder,
            URI sourceImageReference, long capacityMBytes, HashMap<String, String>
            customProperties) throws Throwable {
        DiskState diskState = constructDiskState(alias, type, bootOrder, sourceImageReference,
                capacityMBytes, customProperties);
        diskState.groupLinks = new HashSet<>();
        diskState.groupLinks.add(createResourceGroupState().documentSelfLink);
        return doPost(this.host, diskState, DiskState.class,
                UriUtils.buildUri(this.host, DiskService.FACTORY_LINK));
    }

    /**
     * Create a new disk state to attach it to the virtual machine.
     */
    protected DiskState createDiskWithDatastore(String alias, DiskService.DiskType type, int bootOrder,
            URI sourceImageReference, long capacityMBytes, HashMap<String, String>
            customProperties) throws Throwable {
        DiskState diskState = constructDiskState(alias, type, bootOrder, sourceImageReference,
                capacityMBytes, customProperties);
        diskState.storageDescriptionLink = createStorageDescriptionState().documentSelfLink;
        return doPost(this.host, diskState, DiskState.class,
                UriUtils.buildUri(this.host, DiskService.FACTORY_LINK));
    }

    /**
     * Create a new CD ROM disk state with ISO Image data.
     */
    protected DiskState createCDROMwithISO(String alias, DiskService.DiskType type, int bootOrder,
            URI sourceImageReference, long capacityMBytes, HashMap<String, String>
            customProperties) throws Throwable {
        DiskState diskState = constructDiskState(alias, type, bootOrder, sourceImageReference,
                capacityMBytes, customProperties);

        String isoContent = "Some content to be uploaded";
        CustomProperties.of(diskState).put(PhotonModelConstants.DISK_CONTENT_BASE_64, isoContent);

        diskState.storageDescriptionLink = createStorageDescriptionState().documentSelfLink;
        return doPost(this.host, diskState, DiskState.class,
                UriUtils.buildUri(this.host, DiskService.FACTORY_LINK));
    }

    protected HashMap<String, String> buildCustomProperties() {
        HashMap<String, String> customProperties = new HashMap<>();

        customProperties.put(DISK_MODE_PERSISTENT, "true");
        customProperties.put(PROVISION_TYPE, VirtualDiskType.THIN.value());
        customProperties.put(SHARES_LEVEL, SharesLevel.HIGH.value());
        customProperties.put(LIMIT_IOPS, "100");

        return customProperties;
    }

    /**
     * Verify that the boot disk is resized.
     */
    protected void verifyDiskSize(ComputeState vm, GetMoRef get, long size)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        VirtualDisk vd = fetchVirtualDisk(vm, get);

        long diskSizeinMb = vd.getCapacityInBytes() / 1024 / 1024;
        assertEquals(size, diskSizeinMb);
    }

    /**
     * Get the reference to Virtual Disk from VM.
     */
    protected VirtualDisk fetchVirtualDisk(ComputeState vm, GetMoRef get)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        ManagedObjectReference vmMoRef = CustomProperties.of(vm).getMoRef(MOREF);
        ArrayOfVirtualDevice devices = get.entityProp(vmMoRef, VimPath.vm_config_hardware_device);
        VirtualDisk vd = devices.getVirtualDevice().stream()
                .filter(d -> d instanceof VirtualDisk)
                .map(d -> (VirtualDisk) d).findFirst().orElse(null);
        return vd;
    }

    /**
     * Get the reference to Virtual Disk from VM.
     */
    protected List<VirtualDisk> fetchAllVirtualDisks(ComputeState vm, GetMoRef get)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        ManagedObjectReference vmMoRef = CustomProperties.of(vm).getMoRef(MOREF);
        ArrayOfVirtualDevice devices = get.entityProp(vmMoRef, VimPath.vm_config_hardware_device);
        return devices.getVirtualDevice().stream()
                .filter(d -> d instanceof VirtualDisk)
                .map(d -> (VirtualDisk) d).collect(Collectors.toList());
    }

    /**
     * Creates storage policy as resource group state
     */
    protected ResourceGroupState createResourceGroupState() throws Throwable {
        ResourceGroupState rg = new ResourceGroupState();
        rg.id = this.spId != null ? this.spId : "testRG";
        rg.name = this.spName != null ? this.spName : "testRG";
        rg = TestUtils.doPost(this.host, rg,
                ResourceGroupState.class,
                UriUtils.buildUri(this.host, ResourceGroupService.FACTORY_LINK));
        return rg;
    }

    /**
     * Creates datastore as storage description state.
     */
    protected StorageDescription createStorageDescriptionState() throws Throwable {
        StorageDescription sd = new StorageDescription();
        String datastoreId = this.diskDataStoreId != null ? this.diskDataStoreId : this.dataStoreId;
        sd.name = sd.id = datastoreId != null ? datastoreId : "testDatastore";
        sd = TestUtils.doPost(this.host, sd,
                StorageDescription.class,
                UriUtils.buildUri(this.host, StorageDescriptionService.FACTORY_LINK));
        return sd;
    }

    protected DiskState constructDiskState(String alias, DiskService.DiskType type, int bootOrder,
            URI sourceImageReference, long capacityMBytes, HashMap<String, String> customProperties) {
        DiskState res = new DiskState();
        res.capacityMBytes = capacityMBytes;
        if (bootOrder > 0) {
            res.bootOrder = bootOrder;
        }
        res.type = type;
        res.id = res.name = "disk-" + alias;

        res.sourceImageReference = sourceImageReference;

        res.customProperties = customProperties;
        return res;
    }

    private ResourceOperationRequest getCreateSnapshotRequest(String operation, String documentSelfLink,
            String taskLink) {
        Map<String, String> payload = new HashMap<>();
        payload.put(VSphereConstants.VSPHERE_SNAPSHOT_REQUEST_TYPE, "CREATE");
        payload.put(VSphereConstants.VSPHERE_SNAPSHOT_MEMORY, "false");
        ResourceOperationRequest resourceOperationRequest = new ResourceOperationRequest();
        resourceOperationRequest.operation = operation;
        resourceOperationRequest.isMockRequest = isMock();
        resourceOperationRequest.resourceReference = UriUtils.buildUri(this.host, documentSelfLink);
        resourceOperationRequest.taskReference = UriUtils.buildUri(this.host, taskLink);
        resourceOperationRequest.payload = payload;
        return resourceOperationRequest;
    }

    private ResourceOperationRequest getResizeComputeRequest(String operation, String documentSelfLink,
            String taskLink) {
        Map<String, String> payload = new HashMap<>();
        payload.put(COMPUTE_CPU_COUNT, "4"); //update "__cpuCount" to 4 from 2
        payload.put(REBOOT_VM_FLAG,
                "true"); //reboot vm flag to true (not expecting hot-plug to be enabled in the provisioned machine)
        ResourceOperationRequest resourceOperationRequest = new ResourceOperationRequest();
        resourceOperationRequest.operation = operation;
        resourceOperationRequest.isMockRequest = isMock();
        resourceOperationRequest.resourceReference = UriUtils.buildUri(this.host, documentSelfLink);
        resourceOperationRequest.taskReference = UriUtils.buildUri(this.host, taskLink);
        resourceOperationRequest.payload = payload;
        return resourceOperationRequest;
    }

    private ResourceOperationRequest getDeleteOrRevertSnapshotRequest(String operationId, String operationType,
            String documentSelfLink,
            String taskLink) {
        Map<String, String> payload = new HashMap<>();
        payload.put(VSphereConstants.VSPHERE_SNAPSHOT_REQUEST_TYPE, operationType);
        ResourceOperationRequest resourceOperationRequest = new ResourceOperationRequest();
        resourceOperationRequest.operation = operationId;
        resourceOperationRequest.isMockRequest = isMock();
        resourceOperationRequest.resourceReference = UriUtils.buildUri(this.host, documentSelfLink);
        resourceOperationRequest.taskReference = UriUtils.buildUri(this.host, taskLink);
        resourceOperationRequest.payload = payload;
        return resourceOperationRequest;
    }

    private SnapshotState querySnapshotState(String computeReferenceLink, Boolean isCurrent) {
        List<SnapshotState> snapshotStates = new ArrayList<>();
        QueryTask.Query querySnapshot = QueryTask.Query.Builder.create()
                .addKindFieldClause(SnapshotState.class)
                .addFieldClause(SnapshotState.FIELD_NAME_COMPUTE_LINK, computeReferenceLink)
                .addFieldClause(SnapshotState.FIELD_NAME_IS_CURRENT, isCurrent)
                .build();

        QueryTask qTask = QueryTask.Builder.createDirectTask()
                .setQuery(querySnapshot)
                .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
                .build();

        Operation postOperation = Operation
                .createPost(UriUtils.buildUri(this.host, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
                .setBody(qTask);

        TestRequestSender sender = new TestRequestSender(this.host);
        Operation responseOp = sender.sendAndWait(postOperation);
        QueryResultsProcessor rp = QueryResultsProcessor.create(responseOp);
        if (rp.hasResults()) {
            snapshotStates.addAll(rp.streamDocuments(SnapshotState.class)
                    .collect(Collectors.toList()));
        }

        if (!snapshotStates.isEmpty()) {
            return snapshotStates.get(0);
        } else {
            return null;
        }
    }

    private List<SnapshotState> queryAllSnapshotStates(String computeReferenceLink) {
        List<SnapshotState> snapshotStates = new ArrayList<>();
        QueryTask.Query querySnapshot = QueryTask.Query.Builder.create()
                .addKindFieldClause(SnapshotState.class)
                .addFieldClause(SnapshotState.FIELD_NAME_COMPUTE_LINK, computeReferenceLink)
                .build();

        QueryTask qTask = QueryTask.Builder.createDirectTask()
                .setQuery(querySnapshot)
                .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
                .build();

        Operation postOperation = Operation
                .createPost(UriUtils.buildUri(this.host, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
                .setBody(qTask);

        TestRequestSender sender = new TestRequestSender(this.host);
        Operation responseOp = sender.sendAndWait(postOperation);
        QueryResultsProcessor rp = QueryResultsProcessor.create(responseOp);
        if (rp.hasResults()) {
            snapshotStates.addAll(rp.streamDocuments(SnapshotState.class)
                    .collect(Collectors.toList()));
        }
        return snapshotStates;
    }
}
