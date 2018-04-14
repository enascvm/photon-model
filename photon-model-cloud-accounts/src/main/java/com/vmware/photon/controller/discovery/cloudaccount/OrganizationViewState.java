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

import com.vmware.xenon.common.ServiceDocument.Documentation;

/**
 * An API-friendly representation of the Organization, that a Cloud Account belongs to
 */
public class OrganizationViewState {
    public static final String FIELD_NAME_ORG_ID = "id";
    public static final String FIELD_NAME_ORG_LINK = "orgLink";

    private OrganizationViewState() {}

    @Documentation(description = "CSP Org ID")
    public String id;

    @Documentation(description = "org Link")
    public String orgLink;

    /**
     * Utility method to construct a {@link OrganizationViewState}
     *
     * @param orgId - The CSP Org ID for the cloud account
     * @param orgLink - The orgLink for the cloud account
     * @return A new {@link OrganizationViewState} instance
     */
    public static OrganizationViewState createOrganizationView(String orgId, String orgLink) {
        OrganizationViewState organizationViewState = new OrganizationViewState();
        organizationViewState.id = orgId;
        organizationViewState.orgLink = orgLink;
        return organizationViewState;
    }
}
