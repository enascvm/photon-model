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

import static com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType.aws;

import org.junit.Assert;
import org.junit.Test;

import com.vmware.xenon.services.common.AuthCredentialsService;

public class TestCredentials {

    @Test
    public void testCreateCredentials() {
        // AWS
        Credentials credentials = TestEndpointUtils.createAwsCredentials("foo", "bar");
        Assert.assertEquals("foo", credentials.aws.accessKeyId);
        Assert.assertEquals("bar", credentials.aws.secretAccessKey);
        Assert.assertNull(credentials.azure);
        Assert.assertNull(credentials.vsphere);

        // Azure
        credentials = TestEndpointUtils.createAzureCredentials("foo", "bar", "foo-subscription", "bar-tenant");
        Assert.assertEquals("foo", credentials.azure.clientId);
        Assert.assertEquals("bar", credentials.azure.clientKey);
        Assert.assertEquals("foo-subscription", credentials.azure.subscriptionId);
        Assert.assertEquals("bar-tenant", credentials.azure.tenantId);
        Assert.assertNull(credentials.aws);
        Assert.assertNull(credentials.vsphere);

        // vSphere
        credentials = TestEndpointUtils.createVSphereCredentials("foo", "bar");
        Assert.assertEquals("foo", credentials.vsphere.username);
        Assert.assertEquals("bar", credentials.vsphere.password);
        Assert.assertNull(credentials.aws);
        Assert.assertNull(credentials.azure);
    }

    @Test
    public void testIsEmpty() {
        // AWS
        AuthCredentialsService.AuthCredentialsServiceState authState = new AuthCredentialsService.AuthCredentialsServiceState();
        authState.privateKeyId = "foo";
        authState.privateKey = "bar";

        Credentials credentials = Credentials.createCredentials(aws.name(), authState, null);
        Assert.assertFalse(credentials.isEmpty());

        credentials.aws.secretAccessKey = null;
        Assert.assertTrue(credentials.isEmpty());

        credentials.aws.secretAccessKey = "bar";
        credentials.aws.accessKeyId = null;
        Assert.assertTrue(credentials.isEmpty());

        credentials.aws.secretAccessKey = null;
        Assert.assertTrue(credentials.isEmpty());

        credentials.aws = null;
        Assert.assertTrue(credentials.isEmpty());

        // Azure
        credentials.azure = new Credentials.AzureCredential();
        Assert.assertTrue(credentials.isEmpty());

        credentials.azure.tenantId = "foo-tenant";
        Assert.assertTrue(credentials.isEmpty());

        credentials.azure.subscriptionId = "bar-subscription";
        Assert.assertTrue(credentials.isEmpty());

        credentials.azure.clientKey = "bar";
        Assert.assertTrue(credentials.isEmpty());

        credentials.azure.clientId = "foo";
        Assert.assertFalse(credentials.isEmpty());

        credentials.azure = null;
        Assert.assertTrue(credentials.isEmpty());

        // vSphere
        credentials.vsphere = new Credentials.VsphereCredential();
        Assert.assertTrue(credentials.isEmpty());

        credentials.vsphere.username = "foo";
        Assert.assertTrue(credentials.isEmpty());

        credentials.vsphere.password = "bar";
        Assert.assertFalse(credentials.isEmpty());
    }
}
