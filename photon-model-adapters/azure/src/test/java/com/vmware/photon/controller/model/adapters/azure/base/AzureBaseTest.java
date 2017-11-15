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

package com.vmware.photon.controller.model.adapters.azure.base;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;

import com.vmware.photon.controller.model.adapters.azure.AzureAdapters;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureSdkClients;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Base class for testing azure adapter services
 */
public abstract class AzureBaseTest extends BaseModelTest {

    // Azure Credential properties that get injected
    public String clientID = "clientID";
    public String clientKey = "clientKey";
    public String subscriptionId = "subscriptionId";
    public String tenantId = "tenantId";
    public boolean isMock = true;
    // }}

    public AuthCredentialsServiceState authState;
    public EndpointState endpointState;

    public ResourcePoolState resourcePool;
    public ComputeStateWithDescription computeHost;

    /**
     * @see #getAzureSdkClients()
     */
    private AzureSdkClients azureSdkClients;

    @Rule
    public TestName currentTestName = new TestName();

    /**
     * Mark the method as {@code final} intentionally so descendant classes are enforced to provide
     * their own {@code @Before} method with different name thus preventing from accidental
     * override.
     */
    @Before
    public final void beforeTest() throws Throwable {

        // Configure from sys props
        CommandLineArgumentParser.parseFromProperties(this);

        // Pre-create shared/required entities
        this.authState = createAuthCredentialsState();
        this.endpointState = createEndpointState(this.authState.documentSelfLink);
        this.resourcePool = AzureTestUtil.createDefaultResourcePool(getHost());
        this.computeHost = createComputeHostWithDescription();
    }

    /**
     * Mark the method as {@code final} intentionally so descendant classes are enforced to provide
     * their own {@code @After} method with different name thus preventing from accidental override.
     */
    @After
    public final void afterTest() throws Throwable {

        releaseAzureSdkClients();
    }

    @Override
    protected void startRequiredServices() throws Throwable {

        // Start PhotonModelServices
        super.startRequiredServices();

        PhotonModelTaskServices.startServices(getHost());
        getHost().waitForServiceAvailable(PhotonModelTaskServices.LINKS);

        PhotonModelAdaptersRegistryAdapters.startServices(getHost());

        AzureAdapters.startServices(getHost());

        getHost().waitForServiceAvailable(AzureAdapters.CONFIG_LINK);
    }

    protected AzureSdkClients getAzureSdkClients() {
        if (this.isMock) {
            throw new IllegalStateException("AzureSdkClients is not available in mock mode");
        }

        if (this.azureSdkClients == null) {
            this.azureSdkClients = new AzureSdkClients(this.authState);
        }

        return this.azureSdkClients;
    }

    protected void releaseAzureSdkClients() {
        if (this.azureSdkClients != null) {
            this.azureSdkClients.close();
            this.azureSdkClients = null;
        }
    }

    /**
     * Create EndpointState (of passed type) with auto-created AuthCredentialsServiceState.
     *
     * @see AzureTestUtil#createEndpointState(com.vmware.xenon.common.test.VerificationHost, String,
     *      EndpointType)
     */
    protected final EndpointState createEndpointState(EndpointType endpointType) throws Throwable {

        return AzureTestUtil.createEndpointState(
                getHost(), createAuthCredentialsState().documentSelfLink, endpointType);
    }

    /**
     * Create Azure EndpointState with auto-created AuthCredentialsServiceState.
     *
     * @see #createEndpointState(String)
     */
    protected final EndpointState createEndpointState() throws Throwable {

        return createEndpointState(createAuthCredentialsState().documentSelfLink);
    }

    /**
     * Create Azure EndpointState with passed auth link.
     *
     * @see AzureTestUtil#createDefaultEndpointState(com.vmware.xenon.common.test.VerificationHost,
     *      String)
     */
    protected final EndpointState createEndpointState(String authLink) throws Throwable {

        return AzureTestUtil.createDefaultEndpointState(getHost(), authLink);
    }

    /**
     * Create Azure Auth.
     *
     * @see AzureTestUtil#createDefaultAuthCredentials(com.vmware.xenon.common.test.VerificationHost,
     *      String, String, String, String)
     */
    protected final AuthCredentialsServiceState createAuthCredentialsState() throws Throwable {

        return AzureTestUtil.createDefaultAuthCredentials(
                getHost(),
                this.clientID,
                this.clientKey,
                this.subscriptionId,
                this.tenantId);
    }

    /**
     * Create Azure compute host per {@link #resourcePool} and {@link #endpointState}.
     */
    protected ComputeStateWithDescription createComputeHostWithDescription() throws Throwable {

        ComputeState computeHost = AzureTestUtil.createDefaultComputeHost(
                getHost(), this.resourcePool.documentSelfLink, this.endpointState);

        ComputeDescription computeDescription = getServiceSynchronously(
                computeHost.descriptionLink, ComputeDescription.class);

        return ComputeStateWithDescription.create(computeDescription, computeHost);
    }
}
