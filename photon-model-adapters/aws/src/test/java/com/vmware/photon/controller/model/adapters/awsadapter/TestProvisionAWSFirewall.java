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

package com.vmware.photon.controller.model.adapters.awsadapter;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getExecutor;

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapterapi.FirewallInstanceRequest;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ProvisionFirewallTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionFirewallTaskService.ProvisionFirewallTaskState;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.TenantService;

public class TestProvisionAWSFirewall {

    /*
     * This test requires the following 3 command line variables. If they are not present the tests
     * will be ignored. Pass them into the test with the -Dxenon.variable=value syntax i.e
     * -Dxenon.subnet="10.1.0.0/16"
     *
     * privateKey & privateKeyId are credentials to an AWS VPC account region is the ec2 region
     * where the tests should be run (us-east-1) subnet is the RFC-1918 subnet of the default VPC
     */
    public String privateKey;
    public String privateKeyId;
    public String region;
    public String subnet;

    private VerificationHost host;
    private URI provisionFirewallFactory;

    @Before
    public void setUp() throws Exception {
        CommandLineArgumentParser.parseFromProperties(this);

        // ignore if any of the required properties are missing
        org.junit.Assume.assumeTrue(
                TestUtils.isNull(this.privateKey, this.privateKeyId, this.region, this.subnet));
        this.host = VerificationHost.create(0);
        try {
            this.host.start();
            PhotonModelServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            // start the aws fw service
            this.host.startService(
                    Operation.createPost(UriUtils.buildUri(this.host, AWSFirewallService.class)),
                    new AWSFirewallService());

            this.provisionFirewallFactory = UriUtils.buildUri(this.host,
                    ProvisionFirewallTaskService.FACTORY_LINK);
        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    @After
    public void tearDown() throws InterruptedException {
        if (this.host == null) {
            return;
        }
        this.host.tearDownInProcessPeers();
        this.host.toggleNegativeTestMode(false);
        this.host.tearDown();
    }

    @Test
    public void testProvisionAWSFirewall() throws Throwable {
        // create credentials
        Operation authResponse = new Operation();
        TestUtils.postCredentials(this.host, authResponse, this.privateKey, this.privateKeyId);
        AuthCredentialsServiceState creds = authResponse.getBody(AuthCredentialsServiceState.class);

        // create resource pool
        Operation poolResponse = new Operation();
        TestUtils.postResourcePool(this.host, poolResponse);
        ResourcePoolState pool = poolResponse.getBody(ResourcePoolState.class);

        // create fw service
        Operation securityGroupResponse = new Operation();
        SecurityGroupState initialSecurityGroupState = buildSecurityGroupState();
        initialSecurityGroupState.ingress = getGlobalSSHRule();
        initialSecurityGroupState.egress = getGlobalSSHRule();
        initialSecurityGroupState.egress.get(0).ipRangeCidr = this.subnet;
        initialSecurityGroupState.authCredentialsLink = creds.documentSelfLink;
        initialSecurityGroupState.authCredentialsLink = creds.documentSelfLink;
        initialSecurityGroupState.resourcePoolLink = pool.documentSelfLink;
        initialSecurityGroupState.regionId = this.region;
        initialSecurityGroupState.instanceAdapterReference = UriUtils.buildUri(ServiceHost.LOCAL_HOST,
                this.host.getPort(),
                AWSUriPaths.AWS_FIREWALL_ADAPTER,
                null);

        TestUtils.postSecurityGroup(this.host, initialSecurityGroupState, securityGroupResponse);
        SecurityGroupState securityGroupState = securityGroupResponse.getBody(SecurityGroupState
                .class);

        // set up firewall task state
        ProvisionFirewallTaskState task = new ProvisionFirewallTaskState();
        task.requestType = FirewallInstanceRequest.InstanceRequestType.CREATE;
        task.firewallDescriptionLink = securityGroupState.documentSelfLink;

        Operation provision = new Operation();
        provisionFirewall(task, provision);
        ProvisionFirewallTaskState ps = provision.getBody(ProvisionFirewallTaskState.class);
        waitForTaskCompletion(this.host, UriUtils.buildUri(this.host, ps.documentSelfLink));
        validateAWSArtifacts(securityGroupState.documentSelfLink, creds);

        // reuse previous task, but switch to a delete
        task.requestType = FirewallInstanceRequest.InstanceRequestType.DELETE;
        Operation remove = new Operation();
        provisionFirewall(task, remove);
        ProvisionFirewallTaskState removeTask = remove.getBody(ProvisionFirewallTaskState.class);
        waitForTaskCompletion(this.host, UriUtils.buildUri(this.host, removeTask.documentSelfLink));

        // verify custom property is now set to no value
        SecurityGroupState removedFW = getSecurityGroupState(securityGroupState.documentSelfLink);
        assertTrue(removedFW.customProperties.get(AWSFirewallService.SECURITY_GROUP_ID)
                .equalsIgnoreCase(AWSUtils.NO_VALUE));

    }

    @Test
    public void testInvalidAuthAWSFirewall() throws Throwable {
        // create credentials
        Operation authResponse = new Operation();
        TestUtils.postCredentials(this.host, authResponse, this.privateKey, "invalid");
        AuthCredentialsServiceState creds = authResponse.getBody(AuthCredentialsServiceState.class);

        // create resource pool
        Operation poolResponse = new Operation();
        TestUtils.postResourcePool(this.host, poolResponse);
        ResourcePoolState pool = poolResponse.getBody(ResourcePoolState.class);

        // create fw service
        Operation securityGroupResponse = new Operation();
        SecurityGroupState securityGroupInitialState = buildSecurityGroupState();
        securityGroupInitialState.ingress = getGlobalSSHRule();
        securityGroupInitialState.egress = getGlobalSSHRule();
        securityGroupInitialState.authCredentialsLink = creds.documentSelfLink;
        securityGroupInitialState.resourcePoolLink = pool.documentSelfLink;
        securityGroupInitialState.regionId = this.region;
        securityGroupInitialState.instanceAdapterReference = UriUtils.buildUri(ServiceHost.LOCAL_HOST,
                this.host.getPort(),
                AWSUriPaths.AWS_FIREWALL_ADAPTER,
                null);

        TestUtils.postSecurityGroup(this.host, securityGroupInitialState, securityGroupResponse);
        SecurityGroupState securityGroupState = securityGroupResponse.getBody(SecurityGroupState.class);

        // set up firewall task state
        ProvisionFirewallTaskState task = new ProvisionFirewallTaskState();
        task.requestType = FirewallInstanceRequest.InstanceRequestType.CREATE;
        task.firewallDescriptionLink = securityGroupState.documentSelfLink;

        Operation provision = new Operation();
        provisionFirewall(task, provision);
        ProvisionFirewallTaskState ps = provision.getBody(ProvisionFirewallTaskState.class);
        waitForTaskFailure(this.host, UriUtils.buildUri(this.host, ps.documentSelfLink));

    }

    private void validateAWSArtifacts(String firewallDescriptionLink,
            AuthCredentialsServiceState creds) throws Throwable {

        SecurityGroupState securityGroup = getSecurityGroupState(firewallDescriptionLink);

        AWSFirewallService fwSVC = new AWSFirewallService();
        AmazonEC2AsyncClient client = AWSUtils.getAsyncClient(creds, this.region, getExecutor());
        // if any artifact is not present then an error will be thrown
        assertNotNull(fwSVC.getSecurityGroupByID(client,
                securityGroup.customProperties.get(AWSFirewallService.SECURITY_GROUP_ID)));
    }

    private SecurityGroupState getSecurityGroupState(String firewallLink) throws Throwable {
        Operation response = new Operation();
        getFirewallState(firewallLink, response);
        return response.getBody(SecurityGroupState.class);
    }

    private void provisionFirewall(ProvisionFirewallTaskState ps, Operation response)
            throws Throwable {
        this.host.testStart(1);
        Operation startPost = Operation.createPost(this.provisionFirewallFactory)
                .setBody(ps)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        this.host.failIteration(e);
                        return;
                    }
                    response.setBody(o.getBody(ProvisionFirewallTaskState.class));
                    this.host.completeIteration();
                });
        this.host.send(startPost);
        this.host.testWait();

    }

    private void getFirewallState(String firewallLink, Operation response) throws Throwable {

        this.host.testStart(1);
        URI firewallURI = UriUtils.buildUri(this.host, firewallLink);
        Operation startGet = Operation.createGet(firewallURI)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        this.host.failIteration(e);
                        return;
                    }
                    response.setBody(o.getBody(SecurityGroupState.class));
                    this.host.completeIteration();
                });
        this.host.send(startGet);
        this.host.testWait();

    }

    private SecurityGroupState buildSecurityGroupState() {
        URI tenantFactoryURI = UriUtils.buildFactoryUri(this.host, TenantService.class);
        SecurityGroupState securityGroup = new SecurityGroupState();
        securityGroup.id = UUID.randomUUID().toString();

        securityGroup.tenantLinks = new ArrayList<>();
        securityGroup.tenantLinks.add(UriUtils.buildUriPath(tenantFactoryURI.getPath(), "tenantA"));
        return securityGroup;
    }

    public static void waitForTaskCompletion(VerificationHost host, URI provisioningTaskUri)
            throws Throwable {

        Date expiration = host.getTestExpiration();

        ProvisionFirewallTaskState provisioningTask;

        do {
            provisioningTask = host.getServiceState(null,
                    ProvisionFirewallTaskState.class,
                    provisioningTaskUri);

            if (provisioningTask.taskInfo.stage == TaskState.TaskStage.FAILED) {
                throw new IllegalStateException(
                        "Task failed:" + Utils.toJsonHtml(provisioningTask));
            }

            if (provisioningTask.taskInfo.stage == TaskState.TaskStage.FINISHED) {
                return;
            }

            Thread.sleep(1000);
        } while (new Date().before(expiration));

        host.log("Pending task:\n%s", Utils.toJsonHtml(provisioningTask));

        throw new TimeoutException("Some tasks never finished");
    }

    public static void waitForTaskFailure(VerificationHost host, URI provisioningTaskUri)
            throws Throwable {

        Date expiration = host.getTestExpiration();

        ProvisionFirewallTaskState provisioningTask;

        do {
            provisioningTask = host.getServiceState(null,
                    ProvisionFirewallTaskState.class,
                    provisioningTaskUri);

            if (provisioningTask.taskInfo.stage == TaskState.TaskStage.FAILED) {
                return;
            }

            Thread.sleep(1000);
        } while (new Date().before(expiration));

        host.log("Pending task:\n%s", Utils.toJsonHtml(provisioningTask));

        throw new TimeoutException("Some tasks never finished");
    }

    private static ArrayList<Rule> getGlobalSSHRule() {
        ArrayList<Rule> rules = new ArrayList<>();

        Rule ssh = new Rule();
        ssh.name = "ssh-allow";
        ssh.protocol = "tcp";
        ssh.ipRangeCidr = "0.0.0.0/0";
        ssh.ports = "22";
        rules.add(ssh);

        return rules;
    }

}
