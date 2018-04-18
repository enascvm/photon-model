/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.discovery.cloudaccount;

import static com.vmware.photon.controller.model.UriPaths.CUSTOM_QUERY_PAGE_FORWARDING_SERVICE;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

/**
 * CustomQueryPageForwardingService is a wrapper to handle forwarding page requests for a custom set
 * of factories that may need to utilize paging. It forwards each page request to a specific node
 * before returning that node's response back.
 */
public class CustomQueryPageForwardingService extends StatelessService {

    public static final String SELF_LINK = CUSTOM_QUERY_PAGE_FORWARDING_SERVICE;

    private static final String FIELD_SELF_LINK_PREFIX = "SELF_LINK_PREFIX";

    /**
     * Stores service URI paths that are authorized to utilize this custom paging service
     */
    private Collection<String> supportedFactories;

    /**
     * Link to the node that should service page forward requests.
     */
    private String nodeSelectorLink;

    /**
     * Constructor for the {@link CustomQueryPageForwardingService}.
     *
     * @param nodeSelectorLink     The link to specify the node that should handle page requests.
     * @param supportedPageClasses A collection of classes that are allowed to use this custom
     *                             forwarding service.
     */
    public CustomQueryPageForwardingService(String nodeSelectorLink,
            Collection<Class> supportedPageClasses) {
        super();
        this.nodeSelectorLink = nodeSelectorLink;
        this.supportedFactories = Collections.unmodifiableCollection(supportedPageClasses.stream()
                .map(pageClass -> {
                    try {
                        Field selfLinkPrefixField = pageClass.getDeclaredField(FIELD_SELF_LINK_PREFIX);
                        return selfLinkPrefixField.get(pageClass).toString();
                    } catch (Exception e) {
                        throw new IllegalStateException(pageClass + ": doesn't not have a field: " +
                                FIELD_SELF_LINK_PREFIX);
                    }
                })
                .collect(Collectors.toList()));
    }

    @Override
    public void authorizeRequest(Operation op) {
        op.complete();
    }

    @Override
    public void handleRequest(Operation op) {
        Map<String, String> params = UriUtils.parseUriQueryParams(op.getUri());
        String peer = params.get(UriUtils.FORWARDING_URI_PARAM_NAME_PEER);
        if (peer == null) {
            if (params.isEmpty() && op.getAction() == Action.DELETE) {
                super.handleRequest(op);
                return;
            }
            op.fail(new IllegalArgumentException("peer uri parameter is required"));
            return;
        }

        String path = params.get(UriUtils.FORWARDING_URI_PARAM_NAME_PATH);
        String parentPath = UriUtils.getParentPath(path);
        if (!this.supportedFactories.contains(parentPath)) {
            op.fail(new IllegalArgumentException(
                    String.format("[path=%s] uri parameter is not supported", path)));
            return;
        }

        URI targetService = UriUtils.buildUri(getHost(), path);
        URI forwardURI = UriUtils.buildForwardToPeerUri(targetService, peer, this.nodeSelectorLink, null);

        Operation forwardedOp = new Operation()
                .setUri(forwardURI)
                .setAction(op.getAction())
                .setReferer(op.getUri());
        sendWithDeferredResult(forwardedOp)
                .thenAccept(operation -> op.setBody(operation.getBodyRaw()))
                .whenCompleteNotify(op);
    }
}
