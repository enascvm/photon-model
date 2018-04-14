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

package com.vmware.photon.controller.discovery.csp;

/**
 * Object holding the parameters to login to CSP and get a oauth token
 * We will either need one of username and password or apiKey getting
 * a oauth token from CSP.
 */
public class CspAuthData {

    public String username;

    public String password;

    public String refreshToken;

    public String orgId;

    public boolean isEmpty() {
        if ((this.username == null
                || this.password == null)
                && this.refreshToken == null) {
            return true;
        }
        return false;
    }
}
