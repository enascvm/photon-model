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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.DEFAULT_ALLOWED_NETWORK;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.DEFAULT_PROTOCOL;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.zoneId;
import static com.vmware.photon.controller.model.tasks.QueryUtils.QueryByPages.waitToComplete;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;

import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.tasks.QueryUtils.QueryForReferrers;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.TenantService;

public class TestUtils {

    public static AmazonEC2AsyncClient getClient(String privateKeyId, String privateKey,
            String region, boolean isMockRequest) {
        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
        creds.privateKey = privateKey;
        creds.privateKeyId = privateKeyId;
        return AWSUtils.getAsyncClient(creds, region, getExecutor());
    }

    // validate that the passed items are not null
    public static boolean isNull(String... options) {
        for (String option : options) {
            if (option == null) {
                return false;
            }
        }
        return true;
    }

    public static ArrayList<Rule> getAllowIngressRules() {
        ArrayList<Rule> rules = new ArrayList<>();

        Rule ssh = new Rule();
        ssh.protocol = DEFAULT_PROTOCOL;
        ssh.ipRangeCidr = DEFAULT_ALLOWED_NETWORK;
        ssh.ports = "22";
        rules.add(ssh);

        Rule http = new Rule();
        http.protocol = DEFAULT_PROTOCOL;
        http.ipRangeCidr = DEFAULT_ALLOWED_NETWORK;
        http.ports = "80";
        rules.add(http);

        Rule range = new Rule();
        range.protocol = DEFAULT_PROTOCOL;
        range.ipRangeCidr = DEFAULT_ALLOWED_NETWORK;
        range.ports = "41000-42000";
        rules.add(range);

        return rules;
    }

    public static ArrayList<Rule> getAllowEgressRules(String subnet) {
        ArrayList<Rule> rules = new ArrayList<>();

        Rule out = new Rule();
        out.protocol = DEFAULT_PROTOCOL;
        out.ipRangeCidr = subnet;
        out.ports = "1-65535";
        rules.add(out);

        return rules;
    }

    public static void postSecurityGroup(VerificationHost host, SecurityGroupState state, Operation
            response)
            throws Throwable {
        URI firewallFactory = UriUtils.buildUri(host, SecurityGroupService.FACTORY_LINK);
        host.testStart(1);
        Operation startPost = Operation.createPost(firewallFactory)
                .setBody(state)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    response.setBody(o.getBody(SecurityGroupState.class));
                    host.completeIteration();
                });
        host.send(startPost);
        host.testWait();
    }


    public static void postNetwork(VerificationHost host, NetworkState state, Operation response)
            throws Throwable {
        URI networkFactory = UriUtils.buildUri(host, NetworkService.FACTORY_LINK);
        host.testStart(1);
        Operation startPost = Operation.createPost(networkFactory)
                .setBody(state)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    response.setBody(o.getBody(NetworkState.class));
                    host.completeIteration();
                });
        host.send(startPost);
        host.testWait();
    }

    public static NetworkState buildNetworkState(VerificationHost host) {
        URI tenantFactoryURI = UriUtils.buildFactoryUri(host, TenantService.class);

        NetworkState network = new NetworkState();
        network.regionId = zoneId;
        network.id = UUID.randomUUID().toString();
        network.subnetCIDR = "10.1.0.0/16";
        network.tenantLinks = new ArrayList<>();
        network.tenantLinks.add(UriUtils.buildUriPath(tenantFactoryURI.getPath(), "tenantA"));
        return network;
    }

    public static void postCredentials(VerificationHost host, Operation response, String privateKey, String privateKeyId) throws Throwable {
        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
        creds.privateKey = privateKey;
        creds.privateKeyId = privateKeyId;

        URI authFactory = UriUtils.buildFactoryUri(host, AuthCredentialsService.class);

        host.testStart(1);
        Operation startPost = Operation.createPost(authFactory)
                .setBody(creds)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    response.setBody(o.getBody(AuthCredentialsServiceState.class));
                    host.completeIteration();
                });
        host.send(startPost);
        host.testWait();

    }

    public static void postResourcePool(VerificationHost host,Operation response) throws Throwable {
        URI poolFactory = UriUtils.buildUri(host, ResourcePoolService.FACTORY_LINK);
        ResourcePoolState pool = new ResourcePoolState();
        pool.name = "test-aws";
        host.testStart(1);
        Operation startPost = Operation.createPost(poolFactory)
                .setBody(pool)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    response.setBody(o.getBody(ResourcePoolState.class));
                    host.completeIteration();
                });
        host.send(startPost);
        host.testWait();

    }

    public static void getNetworkState(VerificationHost host, String networkLink,Operation response) throws Throwable {

        host.testStart(1);
        URI networkURI = UriUtils.buildUri(host,networkLink);
        Operation startGet = Operation.createGet(networkURI)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    response.setBody(o.getBody(NetworkState.class));
                    host.completeIteration();
                });
        host.send(startGet);
        host.testWait();

    }

    /**
     * Get all SubnetStates within passed NetworkState. In other words, get all subnet states that
     * refer the network state passed.
     */
    public static List<SubnetState> getSubnetStates(
            VerificationHost host,
            NetworkState networkState) throws Throwable {

        QueryForReferrers<SubnetState> querySubnetStatesReferrers = new QueryForReferrers<>(
                host,
                networkState.documentSelfLink,
                SubnetState.class,
                SubnetState.FIELD_NAME_NETWORK_LINK,
                Collections.emptyList());

        DeferredResult<List<SubnetState>> subnetDR =
                querySubnetStatesReferrers.collectDocuments(Collectors.toList());

        return waitToComplete(subnetDR);
    }

    public static ExecutorService getExecutor() {
        return Executors.newFixedThreadPool(Utils.DEFAULT_THREAD_COUNT,
                r -> new Thread(r, "test/" + Utils.getNowMicrosUtc()));
    }
}
