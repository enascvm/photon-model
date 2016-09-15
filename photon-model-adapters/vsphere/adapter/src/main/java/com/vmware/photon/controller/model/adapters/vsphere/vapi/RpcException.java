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

package com.vmware.photon.controller.model.adapters.vsphere.vapi;

import org.codehaus.jackson.node.ObjectNode;

/**
 * A generic rpc error thrown whenever the "error" field is present in an RPC response.
 */
public class RpcException extends Exception {
    private static final long serialVersionUID = 0;

    private final ObjectNode details;

    public RpcException(String msg) {
        this(msg, null);
    }

    public ObjectNode getDetails() {
        return this.details;
    }

    public RpcException(String msg, ObjectNode details) {
        super(msg);
        this.details = details;
    }
}
