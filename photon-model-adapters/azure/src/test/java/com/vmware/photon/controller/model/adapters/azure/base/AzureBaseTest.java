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

import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultAuthCredentials;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultComputeHost;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultResourcePool;

import org.junit.Before;

import com.vmware.photon.controller.model.adapters.azure.AzureAdapters;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryAdapters;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.services.common.AuthCredentialsService;

/**
 * Base class for testing azure adapter services
 */
public class AzureBaseTest extends BaseModelTest {

    //Azure Credential properties that get injected
    public String clientID = "clientID";
    public String clientKey = "clientKey";
    public String subscriptionId = "subscriptionId";
    public String tenantId = "tenantId";
    public boolean isMock = true;

    public AuthCredentialsService.AuthCredentialsServiceState authState;
    public EndpointService.EndpointState endpointState;

    public ResourcePoolService.ResourcePoolState resourcePool;
    public ComputeService.ComputeStateWithDescription computeStateWithDescription;

    @Before
    public final void beforeTest() throws Throwable {

        CommandLineArgumentParser.parseFromProperties(this);
        this.authState = createAuthCredentialsState();
        this.endpointState = createEndpointState(this.authState.documentSelfLink);
        this.resourcePool = createDefaultResourcePool(getHost());
        this.computeStateWithDescription = createComputeHostWithDescription();

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

    /**
     * Create Azure endpoint.
     */
    private EndpointService.EndpointState createEndpointState(String authLink) throws Throwable {

        return AzureTestUtil.createDefaultEndpointState(
                host, authLink);
    }

    /**
     * Create Azure Auth.
     */
    private AuthCredentialsService.AuthCredentialsServiceState createAuthCredentialsState() throws
            Throwable {

        return createDefaultAuthCredentials(
                getHost(),
                this.clientID,
                this.clientKey,
                this.subscriptionId,
                this.tenantId);
    }

    /**
     * Create ComputeStateWithDescription.
     */
    private ComputeService.ComputeStateWithDescription createComputeHostWithDescription() throws
            Throwable {

        ComputeService.ComputeState computeHost = createDefaultComputeHost(getHost(),
                this.resourcePool.documentSelfLink, this.endpointState);
        ComputeDescriptionService.ComputeDescription computeDescription = getServiceSynchronously
                (computeHost.descriptionLink, ComputeDescriptionService.ComputeDescription.class);
        return ComputeService.ComputeStateWithDescription.create(computeDescription, computeHost);
    }
}
