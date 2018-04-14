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
 * Object representation of CSP JWT token payload data
 */
public class CspTokenData {

    /**
     * Registered fields per https://tools.ietf.org/html/rfc7519
     */

    public String jti;

    public long exp;

    public long iat;

    public String iss;

    public SetOrStringDeserializer.SetOrString aud;

    public String sub;

    /**
     * Authorized party - the party to which the ID Token was issued.
     */
    public String azp;

    /**
     * The client id.
     */
    public String cid;

    /**
     * The username associated with the token.
     */
    public String username;

    /**
     * Additional fields available from CSP.
     */

    /**
     * context_name is the organization ID a CSP token is in the context of. If not a specific
     * organization ID, it may be "default", which returns a complete list of organizations a token
     * may connect to.
     */
    public String context_name;
}
