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

package com.vmware.photon.controller.model.adapters.azure.utils;

import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_CORE_MANAGEMENT_URI;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_EA_BASE_URI;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_MOCK_HOST_SYSTEM_PROPERTY;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNTS;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_KEY;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_URI;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_TYPE;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.CUSTOM_PROP_ENDPOINT_LINK;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.storage.StorageAccountKey;
import com.microsoft.azure.management.storage.implementation.StorageAccountInner;
import com.microsoft.azure.management.storage.implementation.StorageAccountListKeysResultInner;
import com.microsoft.azure.serializer.AzureJacksonAdapter;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageCredentials;
import com.microsoft.rest.LogLevel;
import com.microsoft.rest.RestClient;
import com.microsoft.rest.ServiceResponseBuilder.Factory;

import okhttp3.OkHttpClient;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.ResourceGroupStateType;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureInstanceContext;
import com.vmware.photon.controller.model.adapters.azure.model.network.VirtualNetwork;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.query.QueryStrategy;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTop;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.security.util.EncryptionUtils;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 * Utility methods.
 */
public class AzureUtils {

    private static final Pattern RESOURCE_GROUP_NAME_PATTERN = Pattern
            .compile(".*/resourcegroups/([^/]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern VIRTUAL_NETWORK_GATEWAY_PATTERN = Pattern
            .compile("/subscriptions/.*/virtualNetworkGateways/([^/]*)", Pattern.CASE_INSENSITIVE);

    private static final String VIRTUAL_MACHINE_ID_FORMAT =
            "/subscriptions/%s/resourceGroups/%s/providers/Microsoft.Compute/virtualMachines/%s";

    //azure-subscriptionId
    private static final String COMPUTES_NAME_FORMAT = "%s-%s";

    /**
     * Flag to use azure-mock, will be set in test files. Azure-mock is a tool for testing
     * Azure services in a mock environment.
     **/
    private static boolean IS_AZURE_CLIENT_MOCK = false;

    /**
     * Mock Host and port http://<ip-address>:<port> of aws-mock, will be set in test files.
     */
    private static String azureMockHost = null;

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
     * {@link RetryStrategy} implementation that increases the period for each retry attempt using
     * the exponential function.
     */
    public static class ExponentialRetryStrategy extends RetryStrategy {

        /**
         * The max delay. Defaults to 10 seconds.
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
     * "/subscriptions/[Id]/resourceGroups/TestRG/providers/Microsoft.Network/virtualNetworks/vNet"
     * <p>
     * The id of the resource group that will be returned is:
     * "/subscriptions/[Id]/resourceGroups/TestRG"
     *
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
     * Returns the id of a virtual network gateway for a specified Azure virtual network. The id is
     * extracted from the id of ipConfiguration.
     * <p>
     * Example of Azure virtual network with configured gateway. Note: To be brief, here is only a
     * snippet of the subnet that contains the ipConfiguration for the Virtual Network Gateway.
     * <p>
     * "subnets": [ { ... }, { "name": "GatewaySubnet", "id":
     * "/subscriptions/[id]/resourceGroups/[id]/providers/Microsoft
     * .Network/virtualNetworks/[id]/subnets/GatewaySubnet", "etag": "...", "properties": {
     * "provisioningState": "Succeeded", "addressPrefix": "10.6.1.0/24", "ipConfigurations": [ {
     * "id": "/subscriptions/[id]/resourceGroups/[id]/providers/Microsoft
     * .Network/virtualNetworkGateways/vNetGateway/ipConfigurations/default" } ] } }
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
                .filter(subnet -> subnet.properties != null
                        && subnet.properties.ipConfigurations != null)
                .flatMap(sub -> sub.properties.ipConfigurations.stream())
                .map(ipConfiguration -> VIRTUAL_NETWORK_GATEWAY_PATTERN.matcher(ipConfiguration.id))
                .filter(m -> m.find())
                .findFirst();

        return matcher.isPresent() ? matcher.get().group(0) : null;
    }

    /**
     * @return Generate a virtual machine id based on subscription id, resource group name and
     * vm name.
     */
    public static String getVirtualMachineId(String subscriptionId, String resourceGroupName,
            String vmName) {
        String vmId = String.format(VIRTUAL_MACHINE_ID_FORMAT,
                subscriptionId, resourceGroupName, vmName);
        return vmId.toLowerCase();
    }

    public static StorageDescription constructStorageDescription(ServiceHost host,
            String serviceSelfLink, StorageAccountInner sa,
            AzureInstanceContext ctx, StorageAccountListKeysResultInner keys) {
        return constructStorageDescription(sa, host, serviceSelfLink, ctx.parent, ctx.storageAccount,
                keys);
    }


    public static DeferredResult<AuthCredentialsServiceState> storeKeys(ServiceHost host,
            StorageAccountListKeysResultInner keys, String endpointLink, List<String> tenantLinks) {
        AuthCredentialsServiceState storageAuth = new AuthCredentialsServiceState();
        storageAuth.documentSelfLink = UUID.randomUUID().toString();
        storageAuth.customProperties = new HashMap<>();
        for (StorageAccountKey key : keys.keys()) {
            storageAuth.customProperties.put(getStorageAccountKeyName(storageAuth.customProperties), key.value());
        }
        storageAuth.tenantLinks = tenantLinks;
        if (endpointLink != null) {
            storageAuth.customProperties.put(CUSTOM_PROP_ENDPOINT_LINK,
                    endpointLink);
        }

        Operation storageAuthOp = Operation
                .createPost(host, AuthCredentialsService.FACTORY_LINK)
                .setReferer(host.getPublicUri())
                .setBody(storageAuth);
        return host.sendWithDeferredResult(storageAuthOp, AuthCredentialsServiceState.class);
    }

    public static StorageDescription constructStorageDescription(
            ComputeStateWithDescription parentCompute, ComputeEnumerateResourceRequest request,
            com.vmware.photon.controller.model.adapters.azure.model.storage.StorageAccount storageAccount,
            String keysAuthLink) {

        StorageDescription storageDescription = new StorageDescription();
        storageDescription.id = storageAccount.id;
        storageDescription.regionId = storageAccount.location;
        storageDescription.name = storageAccount.name;
        storageDescription.authCredentialsLink = keysAuthLink;
        storageDescription.resourcePoolLink = request.resourcePoolLink;
        storageDescription.documentSelfLink = UUID.randomUUID().toString();
        storageDescription.endpointLink = request.endpointLink;
        storageDescription.computeHostLink = parentCompute.documentSelfLink;
        storageDescription.customProperties = new HashMap<>();
        storageDescription.customProperties.put(AZURE_STORAGE_TYPE, AZURE_STORAGE_ACCOUNTS);
        storageDescription.customProperties
                .put(AZURE_STORAGE_ACCOUNT_URI, storageAccount.properties.primaryEndpoints.blob);
        storageDescription.tenantLinks = parentCompute.tenantLinks;
        storageDescription.regionId = storageAccount.location;
        storageDescription.type = storageAccount.sku.name; // Set type of azure storage account
        storageDescription.supportsEncryption = storageAccount.properties.encryption != null ?
                storageAccount.properties.encryption.services.blob.enabled : false; //check if
        // SSE is enabled on Azure storage account
        return storageDescription;
    }

    private static StorageDescription constructStorageDescription(StorageAccountInner sa, ServiceHost host,
            String serviceSelfLink, ComputeStateWithDescription parent,
            StorageAccountInner contextStorage,
            StorageAccountListKeysResultInner keys) {
        AuthCredentialsServiceState storageAuth = new AuthCredentialsServiceState();
        storageAuth.documentSelfLink = UUID.randomUUID().toString();
        storageAuth.customProperties = new HashMap<>();
        for (StorageAccountKey key : keys.keys()) {
            storageAuth.customProperties.put(getStorageAccountKeyName(storageAuth.customProperties), key.value());
        }
        storageAuth.tenantLinks = parent.tenantLinks;
        if (parent.endpointLink != null) {
            storageAuth.customProperties.put(CUSTOM_PROP_ENDPOINT_LINK,
                    parent.endpointLink);
        }

        Operation storageAuthOp = Operation
                .createPost(host, AuthCredentialsService.FACTORY_LINK)
                .setBody(storageAuth);
        storageAuthOp.setReferer(UriUtils.buildUri(host.getPublicUri(), serviceSelfLink));
        host.sendRequest(storageAuthOp);

        String storageAuthLink = UriUtils.buildUriPath(AuthCredentialsService.FACTORY_LINK,
                storageAuth.documentSelfLink);
        StorageDescription storageDescription = new StorageDescription();
        storageDescription.id = contextStorage.id();
        storageDescription.regionId = contextStorage.location();
        storageDescription.name = contextStorage.name();
        storageDescription.authCredentialsLink = storageAuthLink;
        storageDescription.resourcePoolLink = parent.resourcePoolLink;
        storageDescription.documentSelfLink = UUID.randomUUID().toString();
        storageDescription.endpointLink = parent.endpointLink;
        storageDescription.computeHostLink = parent.documentSelfLink;
        storageDescription.customProperties = new HashMap<>();
        storageDescription.customProperties.put(AZURE_STORAGE_TYPE, AZURE_STORAGE_ACCOUNTS);
        storageDescription.customProperties.put(AZURE_STORAGE_ACCOUNT_URI, null);
        storageDescription.tenantLinks = parent.tenantLinks;
        storageDescription.type = contextStorage.sku().name().toString();
        if (sa != null && sa.creationTime() != null) {
            storageDescription.creationTimeMicros = TimeUnit.MILLISECONDS
                    .toMicros(sa.creationTime().getMillis());
        }
        return storageDescription;
    }

    public static ComputeState constructAzureSubscriptionComputeState(String endpointLink,
            String descriptionLink, List<String> tenantLinks, String subscriptionId,
            String resourcePoolLink, Map<String, String> customProperties, String name) {
        ComputeState cs = new ComputeState();
        cs.id = UUID.randomUUID().toString();
        cs.name = name != null ? name :
                String.format(COMPUTES_NAME_FORMAT, EndpointType.azure.name(), subscriptionId);
        cs.tenantLinks = tenantLinks;
        cs.endpointLink = endpointLink;
        if (customProperties == null) {
            customProperties = new HashMap<>();
        }
        customProperties.put(EndpointAllocationTaskService.CUSTOM_PROP_ENPOINT_TYPE,
                EndpointType.azure.name());
        cs.customProperties = customProperties;
        cs.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;
        cs.type = ComputeType.VM_HOST;
        cs.descriptionLink = descriptionLink;
        cs.resourcePoolLink = resourcePoolLink;
        return cs;
    }

    public static ComputeDescription constructAzureSubscriptionComputeDescription(
            String endpointLink, List<String> tenantLinks, String subscriptionId, String name,
            Map<String, String> customProperties) {
        ComputeDescription cd = new ComputeDescription();
        cd.tenantLinks = tenantLinks;
        cd.endpointLink = endpointLink;
        cd.name = name != null ? name :
                String.format(COMPUTES_NAME_FORMAT, EndpointType.azure.name(), subscriptionId);
        cd.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;
        cd.id = UUID.randomUUID().toString();
        if (customProperties == null) {
            customProperties = new HashMap<>();
        }
        customProperties.put(EndpointAllocationTaskService.CUSTOM_PROP_ENPOINT_TYPE,
                EndpointType.azure.name());
        cd.customProperties = customProperties;
        return cd;
    }

    /**
     * Increments and returns the key name for next key being added (key1, key2 and so on) based
     * on the number of keys already present in the map.
     * @param map CustomProperties map
     * @return Next storage account key identifier in map.
     */
    public static String getStorageAccountKeyName(Map<String, String> map) {
        int count = 1;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (entry.getKey().startsWith(AZURE_STORAGE_ACCOUNT_KEY)) {
                count++;
            }
        }
        return AZURE_STORAGE_ACCOUNT_KEY + Integer.toString(count);
    }

    public static void setAzureMockHost (String mockHost) {
        azureMockHost = mockHost;
    }

    public static boolean isAzureClientMock() {
        return System.getProperty(AZURE_MOCK_HOST_SYSTEM_PROPERTY) == null ? IS_AZURE_CLIENT_MOCK : true;
    }

    public static void setAzureClientMock(boolean isAzureClientMock) {
        IS_AZURE_CLIENT_MOCK = isAzureClientMock;
    }

    /**
     * If either azureMockHost or the system property for mock host is set then return it, otherwise return
     * real Azure base URI.
     */
    public static String getAzureBaseUri() {
        if (System.getProperty(AZURE_MOCK_HOST_SYSTEM_PROPERTY) == null && azureMockHost == null) {
            return AZURE_CORE_MANAGEMENT_URI;
        }

        return System.getProperty(AZURE_MOCK_HOST_SYSTEM_PROPERTY) == null ? azureMockHost
                : System.getProperty(AZURE_MOCK_HOST_SYSTEM_PROPERTY);

    }

    /**
     * If either azureMockHost or the system property for mock host is set then return it, otherwise return
     * real Azure EA base URI.
     */
    public static String getAzureEaBaseUri() {
        if (System.getProperty(AZURE_MOCK_HOST_SYSTEM_PROPERTY) == null && azureMockHost == null) {
            return AZURE_EA_BASE_URI;
        }

        return System.getProperty(AZURE_MOCK_HOST_SYSTEM_PROPERTY) == null ? azureMockHost
                : System.getProperty(AZURE_MOCK_HOST_SYSTEM_PROPERTY);
    }

    /**
     * Gets the Azure storage SDK client.
     */
    public static CloudStorageAccount getAzureStorageClient(String connectionString) throws Exception {
        if (AzureUtils.isAzureClientMock()) {
            URI blobServiceUri = UriUtils.buildUri(AzureUtils.getAzureBaseUri() + "blob-storage-service");
            URI queueServiceUri = UriUtils.buildUri(AzureUtils.getAzureBaseUri() + "queue-storage-service");
            URI tableServiceUri = UriUtils.buildUri(AzureUtils.getAzureBaseUri() + "table-storage-service");
            URI fileServiceUri = UriUtils.buildUri(AzureUtils.getAzureBaseUri() + "file-storage-service");

            return new CloudStorageAccount(StorageCredentials.tryParseCredentials(connectionString), blobServiceUri,
                    queueServiceUri, tableServiceUri, fileServiceUri);
        } else {
            return CloudStorageAccount.parse(connectionString);
        }
    }

    /**
     * Configures authentication credential for Azure.
     */
    public static ApplicationTokenCredentials getAzureConfig(
            AuthCredentialsServiceState parentAuth) {

        String clientId = parentAuth.privateKeyId;
        String clientKey = EncryptionUtils.decrypt(parentAuth.privateKey);
        String tenantId = parentAuth.customProperties.get(AzureConstants.AZURE_TENANT_ID);

        AzureEnvironment azureEnvironment = AzureEnvironment.AZURE;

        if (AzureUtils.isAzureClientMock()) {
            azureEnvironment.endpoints().put(AzureEnvironment.Endpoint.ACTIVE_DIRECTORY.toString(),
                    AzureUtils.getAzureBaseUri());
        }

        return new ApplicationTokenCredentials(clientId, tenantId, clientKey, azureEnvironment);
    }

    /**
     * Create Azure RestClient with specified executor and credentials.
     * @param credentials Azure credentials
     * @param executorService Reference to executor
     * @return Azure RestClient
     */
    public static RestClient buildRestClient(ApplicationTokenCredentials credentials, ExecutorService executorService) {
        RestClient.Builder restClientBuilder = new RestClient.Builder();

        restClientBuilder.withBaseUrl(AzureUtils.getAzureBaseUri());
        restClientBuilder.withCredentials(credentials);
        restClientBuilder.withSerializerAdapter(new AzureJacksonAdapter());
        restClientBuilder.withLogLevel(LogLevel.NONE);
        if (executorService != null) {
            restClientBuilder.withCallbackExecutor(executorService);
        }
        restClientBuilder.withResponseBuilderFactory(new Factory());

        return restClientBuilder.build();
    }

    /**
     * Clean up Azure SDK HTTP client.
     */
    public static void cleanUpHttpClient(OkHttpClient httpClient) {
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
     * Utility method to handle failures of operations executed parallely.
     * @param service  Instance of service calling this method
     * @param exs  Map of operationIds to exceptions
     * @param parentOp Parent operation which invoked the parallel operations
     */
    public static void handleFailure(Service service, Map<Long, Throwable> exs,
                                      Operation parentOp) {
        long firstKey = exs.keySet().iterator().next();
        exs.values()
                .forEach(ex -> service.getHost().log(Level.WARNING, String.format("Error: %s",
                        ex.getMessage())));
        parentOp.fail(exs.get(firstKey));
    }

    /**
     * Utility method for filtering resource group list by type, and returning the first one, which
     * is of ResourceGroupStateType.AzureResourceGroup type.
     */
    public static DeferredResult<ResourceGroupState> filterRGsByType(ServiceHost serviceHost,
            Set<String> groupLinks, String endpointLink, List<String> tenantLinks) {

        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(ResourceGroupState.class)
                .addInClause(ResourceState.FIELD_NAME_SELF_LINK, groupLinks)
                .addCompositeFieldClause(
                        ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                        ComputeProperties.RESOURCE_TYPE_KEY,
                        ResourceGroupStateType.AzureResourceGroup.name());

        QueryStrategy<ResourceGroupState> queryByPages = new QueryTop<>(
                serviceHost,
                qBuilder.build(),
                ResourceGroupState.class,
                tenantLinks,
                endpointLink)
                // only one group is required
                .setMaxResultsLimit(1);

        return queryByPages
                .collectDocuments(Collectors.toList())
                .thenApply(rgStates -> rgStates.stream().findFirst().orElse(null));
    }

}