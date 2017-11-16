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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.After;
import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration
        .AWSMissingResourcesEnumerationService;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration
        .AWSMissingResourcesEnumerationService.Request;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription
        .ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

public class TestAWSMissingEnumerationResourcesService extends BaseModelTest {

    public static Map<String, ComputeDescription> linkedAccountDescriptions = new HashMap<>();

    @Override
    protected void startRequiredServices() throws Throwable {

        CommandLineArgumentParser.parseFromProperties(this);
        super.startRequiredServices();
        PhotonModelTaskServices.startServices(this.host);
        PhotonModelServices.startServices(this.host);
        this.host.startService(
                Operation.createPost(
                        UriUtils.buildUri(this.host, AWSMissingResourcesEnumerationService.class)),
                new AWSMissingResourcesEnumerationService());
        this.host.waitForServiceAvailable(AWSMissingResourcesEnumerationService.SELF_LINK);
        this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
        this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
        this.host.setTimeoutSeconds(900);
    }

    @After
    public void tearDown() throws Exception {
        if (this.host == null) {
            return;
        }
        this.host.tearDownInProcessPeers();
        this.host.toggleNegativeTestMode(false);
        this.host.tearDown();
    }

    @Test
    public void testComputeStateAndComputeDescriptionCreation() throws Throwable {

        AWSMissingResourcesEnumerationService.Request request = new
                AWSMissingResourcesEnumerationService.Request();
        List<String> missingLinkedAccounts = new ArrayList<>();
        missingLinkedAccounts.add("123456");
        missingLinkedAccounts.add("7891011");
        request.missingLinkedAccountIds = missingLinkedAccounts;
        request.primaryAccountCompute = createPrimaryRootCompute();
        this.host.sendAndWaitExpectSuccess(Operation.createPost(UriUtils.buildUri(
                this.host, AWSMissingResourcesEnumerationService.SELF_LINK))
                .setBody(request)
                .setReferer(this.host.getUri()));
        getLinkedAccountComputeDescs(request, 2);
        verifyLinkedAccountsCreation(request, 2);

    }

    private void getLinkedAccountComputeDescs(Request request, int expectedCount) {
        this.host.waitFor("Timeout waiting for getLinkedAccountComputeDescriptions()", () -> {

            ServiceDocumentQueryResult result = this.host.createAndWaitSimpleDirectQuery(
                    getQuerySpecForComputeDesc(request), expectedCount, expectedCount);
            Collection<Object> values = result.documents.values();
            if (values.size() != expectedCount) {
                return false;
            }
            for (Object computeDesc : result.documents.values()) {
                ComputeDescription cDesc = Utils.fromJson(computeDesc, ComputeDescription.class);
                linkedAccountDescriptions.put(cDesc.name, cDesc);
                if (!cDesc.endpointLink.equals(request.primaryAccountCompute.endpointLink)) {
                    return false;
                }
            }
            return true;
        });
    }

    private QueryTask.QuerySpecification getQuerySpecForComputeDesc(Request request) {
        QueryTask.QuerySpecification querySpec = new QueryTask.QuerySpecification();
        querySpec.query = QueryTask.Query.Builder.create()
                .addKindFieldClause(ComputeDescription.class)
                .addFieldClause(ComputeState.FIELD_NAME_ENDPOINT_LINK, request
                                .primaryAccountCompute.endpointLink,
                        QueryTask.Query.Occurance.MUST_OCCUR).build();
        querySpec.options.add(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);
        return querySpec;
    }

    private QueryTask.QuerySpecification getQuerySpecForComputeStates(Request request) {
        QueryTask.QuerySpecification querySpec = new QueryTask.QuerySpecification();
        querySpec.query = QueryTask.Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_ENDPOINT_LINK, request
                                .primaryAccountCompute.endpointLink,
                        QueryTask.Query.Occurance.MUST_OCCUR)
                .addFieldClause(ComputeState.FIELD_NAME_TYPE, ComputeType.ENDPOINT_HOST)
                .build();
        querySpec.options.add(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);
        return querySpec;
    }

    private void verifyLinkedAccountsCreation(Request request, int expectedCount) {

        this.host.waitFor("Timeout while waiting for verifyLinkedAccountsCreation()", ()
                -> {
            ServiceDocumentQueryResult result = this.host
                    .createAndWaitSimpleDirectQuery(getQuerySpecForComputeStates(request),
                            expectedCount, expectedCount);
            Collection<Object> values = result.documents.values();
            if (values.size() != expectedCount) {
                return false;
            }
            for (Object computeState : result.documents.values()) {
                ComputeState cs = Utils.fromJson(computeState, ComputeState.class);
                if (!cs.descriptionLink.equals(linkedAccountDescriptions.get(cs.name)
                        .documentSelfLink)) {
                    return false;
                }
                if (!cs.endpointLink.equals(request.primaryAccountCompute.endpointLink)) {
                    return false;
                }
                if (cs.creationTimeMicros == null) {
                    return false;
                }
            }
            return true;
        });
    }

    public ComputeStateWithDescription createPrimaryRootCompute() {
        ComputeStateWithDescription primaryRootCompute = new ComputeStateWithDescription();
        primaryRootCompute.id = "456";
        primaryRootCompute.name = "MOCK_ACC";
        primaryRootCompute.type = ComputeType.ENDPOINT_HOST;
        primaryRootCompute.documentSelfLink = "selfLink";
        primaryRootCompute.description = new ComputeDescription();
        Set<URI> statsAdapterReferences = new HashSet<>();
        statsAdapterReferences.add(UriUtils
                .buildUri("stats-adapter-references"));
        primaryRootCompute.description.statsAdapterReferences = statsAdapterReferences;
        primaryRootCompute.creationTimeMicros = 1492610429910002L;
        primaryRootCompute.endpointLink = UriUtils
                .buildUriPath(ComputeDescriptionService.FACTORY_LINK,
                        generateUuidFromStr("endpointLink"));
        primaryRootCompute.adapterManagementReference = UriUtils.buildUri("amazonaws.com");
        primaryRootCompute.customProperties = new HashMap<>();
        primaryRootCompute.customProperties.put(EndpointAllocationTaskService
                .CUSTOM_PROP_ENPOINT_TYPE, EndpointType.aws.name());
        primaryRootCompute.resourcePoolLink = "resourcePoolLink";
        return primaryRootCompute;
    }

    private String generateUuidFromStr(String linkedAccountId) {
        return UUID.nameUUIDFromBytes(linkedAccountId.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
    }
}

