/*
 * Copyright (c) 2015-2017 VMware, Inc. All Rights Reserved.
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

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import com.amazonaws.services.ec2.model.InstanceType;

import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.support.InstanceTypeList;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

/**
 * Service that returns a list of instance types that are supported by AWS.
 * The caller is required to provide a valid endpointLink in the "endpoint"
 * uri parameter.
 */
public class AWSInstanceTypeService extends StatelessService {
    public static final String SELF_LINK = AWSUriPaths.AWS_INSTANCE_TYPE_ADAPTER;

    private static final String URI_PARAM_ENDPOINT = "endpoint";

    private static class Context {
        final String endpointLink;
        EndpointState endpointState;
        InstanceTypeList instanceTypes;

        Context(String endpointLink) {
            this.endpointLink = endpointLink;
        }
    }

    @Override
    public void handleGet(Operation op) {

        Map<String, String> queryParams = UriUtils.parseUriQueryParams(op.getUri());

        String endpointLink = queryParams.get(URI_PARAM_ENDPOINT);

        DeferredResult.completed(new Context(endpointLink))
                .thenCompose(this::getEndpointState)
                .thenCompose(this::getInstanceTypes)
                .whenComplete((context, err) -> {
                    if (err != null) {
                        op.fail(err);
                        return;
                    }

                    op.setBodyNoCloning(context.instanceTypes).complete();
                });
    }

    /**
     * Retrieve endpoint state by the provided endpointLink.
     */
    private DeferredResult<Context> getEndpointState(Context context) {
        AssertUtil.assertNotEmpty(context.endpointLink, "endpointLink is required.");

        Operation endpointOp = Operation.createGet(UriUtils.buildUri(getHost(), context
                .endpointLink));

        return sendWithDeferredResult(endpointOp, EndpointState.class)
                .thenApply(endpointState -> {
                    // Store endpoint state in the context.
                    context.endpointState = endpointState;
                    return context;
                });
    }

    /**
     * Return the instance types by loading them from AWS SDK {@link InstanceType}.
     */
    private DeferredResult<Context> getInstanceTypes(Context context) {
        AssertUtil.assertNotNull(context.endpointState, "Endpoint state was not retrieved.");

        context.instanceTypes = new InstanceTypeList();
        // Set tenant links as specified in the endpoint.
        context.instanceTypes.tenantLinks = context.endpointState.tenantLinks;
        context.instanceTypes.instanceTypes =
                Arrays.stream(InstanceType.values())
                        .map(instanceType -> new InstanceTypeList.InstanceType(
                                instanceType.toString(), instanceType.toString()))
                        .collect(Collectors.toList());

        return DeferredResult.completed(context);
    }
}
