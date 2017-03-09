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

package com.vmware.photon.controller.model.adapters.azure.utils;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.credentials.AzureEnvironment;

import okhttp3.OkHttpClient;

import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.model.network.VirtualNetwork;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.security.util.EncryptionUtils;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;


/**
 * Utility methods.
 */
public class AzureUtils {

    private static final Pattern RESOURCE_GROUP_NAME_PATTERN =
            Pattern.compile(".*/resourcegroups/([^/]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern VIRTUAL_NETWORK_GATEWAY_PATTERN =
            Pattern.compile("/subscriptions/.*/virtualNetworkGateways/([^/]*)", Pattern
                    .CASE_INSENSITIVE);

    /**
     * Strategy to control period between retry attempts.
     */
    public abstract static class RetryStrategy {

        /**
         * Indicates the max number of re-tries has reached.
         */
        public static final long EXHAUSTED = -1;

        /**
         * The maximum period to wait. Defaults to 10 minutes.
         */
        public long maxWaitMillis = TimeUnit.MINUTES.toMillis(10);

        /**
         * The initial delay. Defaults to 250 millis.
         */
        public long delayMillis = TimeUnit.MILLISECONDS.toMillis(250);

        // The accumulated wait time so far.
        private long currentWaitMillis = 0;

        public final long nextDelayMillis() {
            if (this.currentWaitMillis > this.maxWaitMillis) {
                // Indicate maxWaitMillis was exceeded!
                return EXHAUSTED;
            }

            // Delegate to descendants to calculate next delay
            long nextDelayMillis = calculateNextDelayMillis();

            // Update wait time so far
            this.currentWaitMillis += nextDelayMillis;

            return nextDelayMillis;
        }

        protected abstract long calculateNextDelayMillis();
    }

    /**
     * {@link RetryStrategy} implementation that returns a fixed period of time before next retry.
     */
    public static class FixedRetryStrategy extends RetryStrategy {

        @Override
        protected long calculateNextDelayMillis() {
            return this.delayMillis;
        }
    }

    /**
     * {@link RetryStrategy} implementation that increases the period for each retry attempt
     * using the exponential function.
     */
    public static class ExponentialRetryStrategy extends RetryStrategy {

        /**
         * The max delay. Defaults to 10 seconds.
         *
         * <p>
         * Once we exceed this value no more exponential delays are calculated.
         */
        public long maxDelayMillis = TimeUnit.SECONDS.toMillis(10);

        @Override
        protected long calculateNextDelayMillis() {
            long next = this.delayMillis;
            if (next > this.maxDelayMillis) {
                return this.maxDelayMillis;
            }
            this.delayMillis *= 2;
            return next;
        }
    }

    /**
     * Clean up Azure SDK HTTP client.
     */
    public static void cleanUpHttpClient(StatelessService service, OkHttpClient httpClient) {
        if (httpClient == null) {
            return;
        }

        httpClient.connectionPool().evictAll();
        ExecutorService httpClientExecutor = httpClient.dispatcher().executorService();
        httpClientExecutor.shutdown();

        AdapterUtils.awaitTermination(httpClientExecutor);
        httpClient = null;
    }

    /**
     * Configures authentication credential for Azure.
     */
    public static ApplicationTokenCredentials getAzureConfig(
            AuthCredentialsServiceState parentAuth) {

        String clientId = parentAuth.privateKeyId;
        String clientKey = EncryptionUtils.decrypt(parentAuth.privateKey);
        String tenantId = parentAuth.customProperties.get(AzureConstants.AZURE_TENANT_ID);

        return new ApplicationTokenCredentials(clientId, tenantId, clientKey,
                AzureEnvironment.AZURE);
    }

    /**
     * Returns the resource group name from an arbitrary Azure resource id.
     * <p>
     * Example of Azure virtual network resource id:
     * "/subscriptions/[Id]/resourceGroups/TestRG/providers/Microsoft.Network/virtualNetworks/vNet"
     * <p>
     * The returned name of the resource group is TestRG.
     *
     * @param azureResourceId Azure resource id.
     * @return the resource group name (in lower case) where the resource belong to.
     */
    public static String getResourceGroupName(String azureResourceId) {
        Matcher matcher = RESOURCE_GROUP_NAME_PATTERN.matcher(azureResourceId);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return azureResourceId;
    }

    /**
     * Returns the id of a resource group where an arbitrary Azure resource belongs to.
     * <p>
     * Example of Azure virtual network resource id:
     *  "/subscriptions/[Id]/resourceGroups/TestRG/providers/Microsoft.Network/virtualNetworks/vNet"
     * <p>
     * The id of the resource group that will be returned is:
     * "/subscriptions/[Id]/resourceGroups/TestRG"
     * @param azureResourceId Azure resource id.
     * @return the resource group id where the resource belong to.
     */
    public static String getResourceGroupId(String azureResourceId) {
        Matcher matcher = RESOURCE_GROUP_NAME_PATTERN.matcher(azureResourceId);
        if (matcher.find()) {
            return matcher.group(0);
        }
        return azureResourceId;
    }

    /**
     * Returns the id of a virtual network gateway for a specified Azure virtual network.
     * The id is extracted from the id of ipConfiguration.
     * <p>
     * Example of Azure virtual network with configured gateway.
     * Note: To be brief, here is only a snippet of the subnet that contains the ipConfiguration
     * for the Virtual Network Gateway.
     * <p>
     * "subnets": [
     * {
     * ...
     * },
     * {
     * "name": "GatewaySubnet",
     * "id": "/subscriptions/[id]/resourceGroups/[id]/providers/Microsoft
     * .Network/virtualNetworks/[id]/subnets/GatewaySubnet",
     * "etag": "...",
     * "properties": {
     * "provisioningState": "Succeeded",
     * "addressPrefix": "10.6.1.0/24",
     * "ipConfigurations": [
     * {
     * "id": "/subscriptions/[id]/resourceGroups/[id]/providers/Microsoft
     * .Network/virtualNetworkGateways/vNetGateway/ipConfigurations/default"
     * }
     * ]
     * }
     * }
     * <p>
     * <p>
     * The id of the resource group that will be returned is:
     * "/subscriptions/[Id]/resourceGroups/[id]/providers/Microsoft
     * .Network/virtualNetworkGateways/vNetGateway"
     *
     * @param azureVirtualNetwork Azure virtual network.
     * @return the id of the gateway the virtual network is attached to.
     */
    public static String getVirtualNetworkGatewayId(VirtualNetwork azureVirtualNetwork) {

        Optional<Matcher> matcher = azureVirtualNetwork.properties.subnets.stream()
                .filter(subnet -> subnet.properties != null && subnet.properties.ipConfigurations
                        != null)
                .flatMap(sub -> sub.properties.ipConfigurations.stream())
                .map(ipConfiguration -> VIRTUAL_NETWORK_GATEWAY_PATTERN.matcher(ipConfiguration.id))
                .filter(m -> m.find())
                .findFirst();

        return matcher.isPresent() ? matcher.get().group(0) : null;
    }
}
