/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

import java.util.List;
import java.util.stream.Collectors;

import com.microsoft.azure.management.resources.implementation.LocationInner;

import com.vmware.photon.controller.model.adapterapi.RegionEnumerationResponse;
import com.vmware.photon.controller.model.adapterapi.RegionEnumerationResponse.RegionInfo;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.endpoint.AzureEndpointAdapterService;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureSdkClients;
import com.vmware.photon.controller.model.adapters.util.EndpointAdapterUtils;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class AzureRegionEnumerationAdapterService extends StatelessService {

    public static final String SELF_LINK = AzureUriPaths.AZURE_REGION_ENUMERATION_ADAPTER_SERVICE;

    @Override
    public void handlePost(Operation post) {
        if (!post.hasBody()) {
            post.fail(new IllegalArgumentException("body is required"));
            return;
        }

        EndpointState request = post.getBody(EndpointState.class);

        DeferredResult<AuthCredentialsServiceState> credentialsDr;
        if (request.authCredentialsLink == null) {
            credentialsDr = new DeferredResult<>();
            credentialsDr.complete(new AuthCredentialsServiceState());
        } else {
            Operation getCredentials = Operation.createGet(this, request.authCredentialsLink);
            credentialsDr = sendWithDeferredResult(getCredentials,
                    AuthCredentialsServiceState.class);
        }

        credentialsDr.thenCompose(creds -> {
            EndpointAdapterUtils.Retriever retriever = EndpointAdapterUtils.Retriever
                    .of(request.endpointProperties);

            AzureEndpointAdapterService.credentials().accept(creds, retriever);

            AzureSdkClients clients = new AzureSdkClients(getHost().allocateExecutor(this), creds);

            AzureDeferredResultServiceCallback<List<LocationInner>> callback = new AzureDeferredResultServiceCallback<List<LocationInner>>(
                    this, "Retrieving locations for subscription with id " + creds.userLink) {

                @Override
                protected DeferredResult<List<LocationInner>> consumeSuccess(
                        List<LocationInner> result) {
                    return DeferredResult.completed(result);
                }
            };

            clients.getSubscriptionClientImpl().subscriptions()
                    .listLocationsAsync(creds.userLink, callback);

            return callback.toDeferredResult();
        })
                .whenComplete((locations, t) -> {
                    if (t != null) {
                        post.fail(t);
                        return;
                    }

                    List<RegionInfo> regions = locations.stream()
                            .map(location -> new RegionInfo(location.displayName(),
                                    location.name()))
                            .collect(
                                    Collectors.toList());

                    RegionEnumerationResponse result = new RegionEnumerationResponse();
                    result.regions = regions;

                    post.setBody(result);
                    post.complete();
                });
    }
}
