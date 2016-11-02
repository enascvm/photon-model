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

import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.junit.After;
import org.junit.Before;

import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BasicConnection;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService.ProvisionComputeTaskState;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService.ResourceEnumerationTaskState;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService.ResourceRemovalTaskState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.ExampleService;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Builder;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskService.TaskServiceState;

public class BaseVSphereAdapterTest {

    public static final String DEFAULT_AUTH_TYPE = "Username/Password";

    public String vcUrl = System.getProperty(TestProperties.VC_URL);
    public String vcUsername = System.getProperty(TestProperties.VC_USERNAME);
    public String vcPassword = System.getProperty(TestProperties.VC_PASSWORD);

    public String datacenterId = System.getProperty(TestProperties.VC_DATECENTER_ID);
    public String dataStoreId = System.getProperty(TestProperties.VC_DATASTORE_ID);
    public String networkId = System.getProperty(TestProperties.VC_NETWORK_ID);

    public String vcFolder = System.getProperty(TestProperties.VC_FOLDER);

    protected VerificationHost host;
    protected AuthCredentialsServiceState auth;
    protected ResourcePoolState resourcePool;

    @Before
    public void setUp() throws Throwable {
        this.host = VerificationHost.create(0);

        this.host.start();
        this.host.waitForServiceAvailable(ExampleService.FACTORY_LINK);

        // TODO: VSYM-992 - improve test/fix arbitrary timeout
        this.host.setTimeoutSeconds(600);

        try {
            PhotonModelServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            VSphereAdapters.startServices(this.host);

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
            this.host.waitForServiceAvailable(VSphereAdapters.LINKS);
        } catch (Throwable e) {
            this.host.log("Error starting up services for the test %s", e.getMessage());
            throw new Exception(e);
        }

        if (this.vcUrl == null) {
            this.vcUrl = "http://not-configured";
        }
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
        inPool.name = "resourcePool-" + UUID.randomUUID().toString();
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

    protected void deleteVmAndWait(ComputeState vm) throws Throwable {
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

        computeDesc.instanceAdapterReference = AdapterUriUtil.buildAdapterUri(this.host,
                VSphereUriPaths.INSTANCE_SERVICE);
        computeDesc.enumerationAdapterReference = AdapterUriUtil.buildAdapterUri(this.host,
                VSphereUriPaths.ENUMERATION_SERVICE);
        computeDesc.statsAdapterReference = AdapterUriUtil.buildAdapterUri(this.host,
                VSphereUriPaths.STATS_SERVICE);
        computeDesc.powerAdapterReference = AdapterUriUtil.buildAdapterUri(this.host,
                VSphereUriPaths.POWER_SERVICE);

        computeDesc.authCredentialsLink = this.auth.documentSelfLink;

        computeDesc.regionId = this.datacenterId;

        return TestUtils.doPost(this.host, computeDesc,
                ComputeDescription.class,
                UriUtils.buildUri(this.host, ComputeDescriptionService.FACTORY_LINK));
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

        ComputeState returnState = TestUtils.doPost(this.host, computeState,
                ComputeState.class,
                UriUtils.buildUri(this.host, ComputeService.FACTORY_LINK));
        return returnState;
    }

    protected void enumerateComputes(ComputeState computeHost) throws Throwable {
        ResourceEnumerationTaskState task = new ResourceEnumerationTaskState();
        task.adapterManagementReference = computeHost.adapterManagementReference;

        if (isMock()) {
            task.options = EnumSet.of(TaskOption.IS_MOCK);
        }
        task.enumerationAction = EnumerationAction.REFRESH;
        task.parentComputeLink = computeHost.documentSelfLink;
        task.resourcePoolLink = this.resourcePool.documentSelfLink;

        ResourceEnumerationTaskState outTask = TestUtils.doPost(this.host,
                task,
                ResourceEnumerationTaskState.class,
                UriUtils.buildUri(this.host,
                        ResourceEnumerationTaskService.FACTORY_LINK));

        this.host.waitForFinishedTask(ResourceEnumerationTaskState.class, outTask.documentSelfLink);
    }

    protected Query createQueryForResourcePoolOwner() {
        return Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        CustomProperties.TYPE, VimNames.TYPE_CLUSTER_COMPUTE_RESOURCE)
                .addFieldClause(QuerySpecification.buildCompositeFieldName(
                        ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        CustomProperties.RESOURCE_POOL_MOREF),
                        "*", MatchType.WILDCARD)
                .build();
    }

    protected <T extends ServiceDocument> T findFirstMatching(Query q, Class<T> type) {
        QueryTask qt = Builder.createDirectTask()
                .setQuery(q)
                .addOption(QueryOption.EXPAND_CONTENT)
                .build();

        Operation result = this.host.waitForResponse(
                Operation.createPost(UriUtils.buildUri(this.host, ServiceUriPaths.CORE_QUERY_TASKS))
                        .setBody(qt));

        qt = result.getBody(QueryTask.class);
        if (qt.results.documents.isEmpty()) {
            throw new IllegalStateException(
                    "Nothing found for " + Utils.toJsonHtml(q) + ". Expected at least one result");
        }

        // TODO rewrite using QueryResultsProcessor
        return Utils.fromJson(qt.results.documents.values().iterator().next(), type);
    }
}
