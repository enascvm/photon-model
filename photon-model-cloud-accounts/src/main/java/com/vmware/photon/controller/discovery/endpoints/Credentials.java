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

package com.vmware.photon.controller.discovery.endpoints;

import static com.vmware.photon.controller.discovery.endpoints.Credentials.AwsCredential.AuthType.ARN;
import static com.vmware.photon.controller.discovery.endpoints.Credentials.AwsCredential.AuthType.KEYS;
import static com.vmware.photon.controller.discovery.endpoints.EndpointConstants.EMPTY_STRING;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.ARN_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.EXTERNAL_ID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.USER_LINK_KEY;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_TENANT_ID;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.xenon.common.ServiceDocument.Documentation;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class Credentials {

    private static final List<String> VSPHERE_TYPES = Arrays.asList(
            EndpointType.vsphere.name(), EndpointUtils.VSPHERE_ON_PREM_ADAPTER);

    public static final String FIELD_NAME_VSPHERE_CREDENTIAL = "vsphere";

    private Credentials() {}

    @Documentation(description = "The AWS credentials")
    public AwsCredential aws;

    @Documentation(description = "The Basic Azure credentials")
    public AzureCredential azure;

    @Documentation(description = "The Azure Enterprise Agreement (EA) credentials")
    public AzureEaCredential azure_ea;

    @Documentation(description = "The on-prem vSphere credentials")
    public VsphereCredential vsphere;

    public static class AwsCredential {
        @Documentation(description = "The access key id")
        public String accessKeyId;

        @Documentation(description = "The secret access key")
        public String secretAccessKey;

        @Documentation(description =
                "A role arn that can be used for giving permissions to Discovery")
        public String arn;

        @Documentation(description =
                "The external ID Discovery may connect to for a specific arn")
        public String externalId;

        @Documentation(description = "The auth type for AWS credentials")
        public AuthType authType;

        public enum AuthType {
            @Documentation(
                    name = "Access Key",
                    description = "Authentication type for AWS is via Access Key/Secret Key")
            KEYS,

            @Documentation(
                    name = "ARN (Amazon Resource Name)",
                    description = "Authentication type for AWS is via Amazon Resource Name")
            ARN
        }
    }

    public static class AzureCredential {
        @Documentation(description = "The tenant id")
        public String tenantId;

        @Documentation(description = "The subscription id")
        public String subscriptionId;

        @Documentation(description = "The client id")
        public String clientId;

        @Documentation(description = "The client key")
        public String clientKey;
    }

    public static class AzureEaCredential {
        @Documentation(description = "The enrollment number")
        public String enrollmentNumber;

        @Documentation(description = "The secret API access key")
        public String accessKey;
    }

    public static class VsphereCredential {

        public static final String FIELD_NAME_USER_NAME = "username";
        public static final String FIELD_NAME_PASSWORD = "password";

        @Documentation(description = "The vCenter username")
        public String username;

        @Documentation(description = "The vCenter password")
        public String password;
    }

    /**
     * Helper to determine if the *complete* contents of a certain credential type are fulfilled or
     * not - i.e., if aws is non-null but its parameters are, it is considered empty.
     * @return true if not-complete for at least one set of credentials, false otherwise.3
     */
    public boolean isEmpty() {
        return !((this.aws != null &&
                ((this.aws.accessKeyId != null && this.aws.secretAccessKey != null) || this.aws.arn != null)) ||
                (this.azure != null && this.azure.clientId != null && this.azure.clientKey != null
                        && this.azure.subscriptionId != null && this.azure.tenantId != null) ||
                (this.azure_ea != null && this.azure_ea.enrollmentNumber != null
                        && this.azure_ea.accessKey != null) ||
                (this.vsphere != null && this.vsphere.username != null && this.vsphere.password != null));
    }

    public static Credentials createCredentials(String type, AuthCredentialsServiceState authState,
            Map<String, String> endpointProperties) {
        Credentials credential = new Credentials();
        if (type.equals(EndpointType.aws.name())) {
            credential.aws = new AwsCredential();
            if (authState != null) {
                credential.aws.accessKeyId = authState.privateKeyId;
                credential.aws.secretAccessKey = authState.privateKey;
            }
            credential.aws.arn = endpointProperties != null ?
                    endpointProperties.get(ARN_KEY) : null;
            credential.aws.externalId = endpointProperties != null ?
                    endpointProperties.get(EXTERNAL_ID_KEY) : null;
            if (credential.aws.arn != null) {
                credential.aws.authType = ARN;
            } else {
                credential.aws.authType = KEYS;
            }
        } else if (type.equals(EndpointType.azure.name())) {
            credential.azure = new AzureCredential();
            credential.azure.clientKey = authState.privateKey;
            credential.azure.clientId = authState.privateKeyId;
            credential.azure.subscriptionId = endpointProperties.get(USER_LINK_KEY);
            credential.azure.tenantId = endpointProperties.get(AZURE_TENANT_ID);
        } else if (type.equals(EndpointType.azure_ea.name())) {
            credential.azure_ea = new AzureEaCredential();
            credential.azure_ea.enrollmentNumber = authState.privateKeyId;
            credential.azure_ea.accessKey = authState.privateKey;
        } else if (VSPHERE_TYPES.contains(type)) {
            credential.vsphere = new VsphereCredential();
            credential.vsphere.username = authState.privateKeyId;
            credential.vsphere.password = authState.privateKey;
        } else {
            throw new IllegalArgumentException(type + ": is not a supported credential type");
        }
        return credential;
    }

    public static Credentials mergeCredentials(Credentials credentials, Map<String, String>
            endpointProperties) {
        if (credentials.azure_ea != null && credentials.azure_ea.enrollmentNumber == null) {
            credentials.azure_ea.enrollmentNumber = endpointProperties.get(PRIVATE_KEYID_KEY);
        }

        if (credentials.azure != null) {
            if (credentials.azure.subscriptionId == null) {
                credentials.azure.subscriptionId = endpointProperties.get(USER_LINK_KEY);
            }
            if (credentials.azure.tenantId == null) {
                credentials.azure.tenantId = endpointProperties.get(AZURE_TENANT_ID);
            }
        }

        // Expected the complete vCenter credential for patching. Otherwise complete the vCenter
        // credential field with empty string. Then it would fail the validation step later.
        if (credentials.vsphere != null) {
            if (credentials.vsphere.password == null) {
                credentials.vsphere.password = EMPTY_STRING;
            }
            if (credentials.vsphere.username == null) {
                credentials.vsphere.username = EMPTY_STRING;
            }
        }
        return credentials;
    }
}
