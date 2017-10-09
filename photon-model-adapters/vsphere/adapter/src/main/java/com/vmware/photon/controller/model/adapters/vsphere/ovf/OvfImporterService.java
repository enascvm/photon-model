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

package com.vmware.photon.controller.model.adapters.vsphere.ovf;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.apache.http.client.HttpClient;
import org.w3c.dom.Document;

import com.vmware.photon.controller.model.adapters.vsphere.CustomProperties;
import com.vmware.photon.controller.model.adapters.vsphere.VSphereIOThreadPoolAllocator;
import com.vmware.photon.controller.model.adapters.vsphere.VSphereUriPaths;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.util.PhotonModelUriUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.StatelessService;

/**
 * Given a link to an OVF descriptor this service creates a ComputeDescription
 */
public class OvfImporterService extends StatelessService {

    public static final String SELF_LINK = VSphereUriPaths.OVF_IMPORTER;

    @Override
    public void handlePatch(Operation patch) {
        if (!patch.hasBody()) {
            patch.fail(new IllegalArgumentException("body is required"));
            return;
        }

        ImportOvfRequest req = patch.getBody(ImportOvfRequest.class);

        VSphereIOThreadPoolAllocator.getPool(getHost()).submit(createImportTask(req, patch));
    }

    private Runnable createImportTask(ImportOvfRequest req, Operation patch) {
        return () -> {
            URI ovfUri;

            HttpClient client = OvfRetriever.newInsecureClient();
            OvfRetriever retriever = new OvfRetriever(client);
            OvfParser parser = new OvfParser();

            Document doc;
            try {
                ovfUri = retriever.downloadIfOva(req.ovfUri);
                doc = parser.retrieveDescriptor(ovfUri);
            } catch (Exception e) {
                patch.fail(e);
                return;
            }

            CustomProperties.of(req.template)
                    .put(OvfParser.PROP_OVF_URI, ovfUri);

            if (!Objects.equals(ovfUri, req.ovfUri)) {
                // this is an ova archive, store original OVA uri
                CustomProperties.of(req.template)
                        .put(OvfParser.PROP_OVF_ARCHIVE_URI, req.ovfUri);
            }

            List<ComputeDescription> ovfDescriptions = parser.parse(doc, req.template);
            Stream<Operation> opStream = ovfDescriptions.stream().map(desc -> Operation
                    .createPost(PhotonModelUriUtils.createDiscoveryUri(getHost(), ComputeDescriptionService.FACTORY_LINK))
                    .setBodyNoCloning(desc));

            OperationJoin join = OperationJoin.create()
                    .setOperations(opStream)
                    .setCompletion((os, es) -> {
                        if (es != null && !es.isEmpty()) {
                            patch.fail(es.values().iterator().next());
                        } else {
                            patch.complete();
                        }
                    });

            join.sendWith(this);
        };
    }
}
