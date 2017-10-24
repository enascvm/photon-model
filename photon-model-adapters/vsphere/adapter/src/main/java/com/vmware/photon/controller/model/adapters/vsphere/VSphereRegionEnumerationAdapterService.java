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

import static com.vmware.photon.controller.model.adapters.vsphere.VSphereEndpointAdapterService.HOST_NAME_KEY;

import java.net.URI;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.adapterapi.RegionEnumerationResponse;
import com.vmware.photon.controller.model.adapters.util.EndpointAdapterUtils;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BasicConnection;
import com.vmware.photon.controller.model.adapters.vsphere.util.finders.DatacenterLister;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.security.util.EncryptionUtils;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class VSphereRegionEnumerationAdapterService extends StatelessService {
    public static final String SELF_LINK = VSphereUriPaths.VSPHERE_REGION_ENUMERATION_ADAPTER_SERVICE;

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

        credentialsDr.whenComplete(
                (AuthCredentialsServiceState creds, Throwable t) -> {
                    if (t != null) {
                        post.fail(t);
                        return;
                    }
                    VSphereIOThreadPoolAllocator.getPool(this).submit(() -> {
                        BasicConnection connection = new BasicConnection();
                        try {
                            EndpointAdapterUtils.Retriever retriever = EndpointAdapterUtils.Retriever
                                    .of(request.endpointProperties);

                            VSphereEndpointAdapterService.endpoint().accept(request, retriever);
                            VSphereEndpointAdapterService.credentials().accept(creds, retriever);

                            connection.setURI(URI.create("https://" + request.endpointProperties
                                    .get(HOST_NAME_KEY) + "/sdk"));
                            connection.setUsername(creds.privateKeyId);
                            connection.setPassword(EncryptionUtils.decrypt(creds.privateKey));
                            connection.setIgnoreSslErrors(true);
                            connection.connect();

                            DatacenterLister lister = new DatacenterLister(connection);

                            RegionEnumerationResponse res = new RegionEnumerationResponse();
                            res.regions = lister.listAllDatacenters().stream()
                                    .map(dc -> new RegionEnumerationResponse.RegionInfo(
                                            dc.path,
                                            VimUtils.convertMoRefToString(dc.object))).collect(
                                            Collectors.toList());

                            post.setBody(res);
                            post.complete();
                        } catch (Exception e) {
                            post.fail(e);
                        } finally {
                            connection.closeQuietly();
                        }
                    });
                });
    }
}
