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

package com.vmware.photon.controller.model.adapters.gcp;

import static com.vmware.photon.controller.model.ComputeProperties.CUSTOM_DISPLAY_NAME;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.CPU_PLATFORM;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.DEFAULT_AUTH_TYPE;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.DEFAULT_CPU_PLATFORM;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.DEFAULT_IMAGE_REFERENCE;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.DISK_TYPE_PERSISTENT;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.OPERATION_STATUS_DONE;
import static com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ENVIRONMENT_NAME_GCP;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeScopes;
import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.AttachedDiskInitializeParams;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceList;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.ServiceAccount;

import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.ServiceUriPaths;

public class GCPTestUtil {
    private static final String ENUMERATION_TEST_INSTANCE = "enumeration-test-instance-";
    private static final String ENUMERATION_TEST_MACHINE_TYPE = "https://www.googleapis" +
            ".com/compute/v1/projects/%s/zones/%s/machineTypes/f1-micro";
    private static final String NETWORK_INTERFACE = "https://www.googleapis" +
            ".com/compute/v1/projects/%s/global/networks/default";
    private static final String NETWORK_INTERFACE_CONFIG = "ONE_TO_ONE_NAT";
    private static final String NETWORK_ACCESS_CONFIG = "External NAT";
    private static final String SOURCE_IMAGE = "https://www.googleapis.com/compute/v1/" +
            "projects/ubuntu-os-cloud/global/images/family/ubuntu-1404-lts";
    private static final String BOOT_DISK_NAME_SUFFIX = "-boot-disk";
    private static final String DISK_TYPE = "https://www.googleapis" +
            ".com/compute/v1/projects/%s/zones/%s/diskTypes/pd-standard";
    private static final long WAIT_INTERVAL = 5000;
    private static final int MIN_CPU_COUNT = 1;
    private static final int MIN_MEMORY_BYTES = 1024;

    private static volatile boolean queryResult = false;

    /**
     * Create a resource pool where the VM will be housed
     * @param host The test host service.
     * @param projectId The GCP project ID.
     * @return The default resource pool.
     * @throws Throwable The exception during creating resource pool.
     */
    public static ResourcePoolService.ResourcePoolState createDefaultResourcePool(
            VerificationHost host,
            String projectId)
            throws Throwable {
        ResourcePoolService.ResourcePoolState inPool = new ResourcePoolService.ResourcePoolState();

        inPool.name = UUID.randomUUID().toString();
        inPool.id = inPool.name;
        inPool.projectName = projectId;
        inPool.minCpuCount = MIN_CPU_COUNT;
        inPool.minMemoryBytes = MIN_MEMORY_BYTES;

        return TestUtils.doPost(host, inPool, ResourcePoolService.ResourcePoolState.class,
                UriUtils.buildUri(host, ResourcePoolService.FACTORY_LINK));
    }

    /**
     * Create a compute host description for a GCP instance
     * @param host The test host service.
     * @param userEmail The service account's client email.
     * @param privateKey The service account's private key.
     * @param zoneId The GCP project's zone ID.
     * @param resourcePoolLink The default resource pool's link.
     * @return The default compute host.
     * @throws Throwable The exception during creating compute host.
     */
    public static ComputeService.ComputeState createDefaultComputeHost(VerificationHost host,
                                                                       String userEmail,
                                                                       String privateKey,
                                                                       String zoneId,
                                                                       String resourcePoolLink)
            throws Throwable {
        AuthCredentialsService.AuthCredentialsServiceState auth =
                new AuthCredentialsService.AuthCredentialsServiceState();

        auth.type = DEFAULT_AUTH_TYPE;
        auth.userEmail = userEmail;
        auth.privateKey = privateKey;
        auth.documentSelfLink = UUID.randomUUID().toString();

        TestUtils.doPost(host, auth, AuthCredentialsService.AuthCredentialsServiceState.class,
                UriUtils.buildUri(host, AuthCredentialsService.FACTORY_LINK));
        String authLink = UriUtils.buildUriPath(AuthCredentialsService.FACTORY_LINK,
                auth.documentSelfLink);

        ComputeDescriptionService.ComputeDescription gcpHostDescription = new
                ComputeDescriptionService.ComputeDescription();
        gcpHostDescription.id = UUID.randomUUID().toString();
        gcpHostDescription.documentSelfLink = gcpHostDescription.id;
        gcpHostDescription.enumerationAdapterReference = UriUtils.buildUri(host,
                GCPUriPaths.GCP_ENUMERATION_ADAPTER);
        gcpHostDescription.zoneId = zoneId;
        gcpHostDescription.authCredentialsLink = authLink;

        TestUtils.doPost(host, gcpHostDescription,
                ComputeDescriptionService.ComputeDescription.class,
                UriUtils.buildUri(host, ComputeDescriptionService.FACTORY_LINK));

        ComputeService.ComputeState gcpComputeHost = new ComputeService.ComputeState();
        gcpComputeHost.id = UUID.randomUUID().toString();
        gcpComputeHost.documentSelfLink = gcpComputeHost.id;
        gcpComputeHost.descriptionLink = UriUtils.buildUriPath(
                ComputeDescriptionService.FACTORY_LINK, gcpHostDescription.id);
        gcpComputeHost.resourcePoolLink = resourcePoolLink;

        return TestUtils.doPost(host, gcpComputeHost,
                ComputeService.ComputeState.class, UriUtils.buildUri(host,
                        ComputeService.FACTORY_LINK));
    }

    /**
     * Create a GCP VM compute resource
     * @param host The test host service.
     * @param userEmail The service account's client email.
     * @param privateKey The service account's private key.
     * @param zoneId The GCP project's zone ID.
     * @param gcpVMName The default name of the VM.
     * @param parentLink The default compute host's link.
     * @param resourcePoolLink The default resource pool's link.
     * @return The default VM.
     * @throws Throwable The exception during creating vm.
     */
    public static ComputeService.ComputeState createDefaultVMResource(VerificationHost host,
                                                                      String userEmail,
                                                                      String privateKey,
                                                                      String zoneId,
                                                                      String gcpVMName,
                                                                      String parentLink,
                                                                      String resourcePoolLink)
            throws Throwable {
        AuthCredentialsService.AuthCredentialsServiceState auth =
                new AuthCredentialsService.AuthCredentialsServiceState();

        auth.type = DEFAULT_AUTH_TYPE;
        auth.userEmail = userEmail;
        auth.privateKey = privateKey;
        auth.documentSelfLink = UUID.randomUUID().toString();

        TestUtils.doPost(host, auth, AuthCredentialsService.AuthCredentialsServiceState.class,
                UriUtils.buildUri(host, AuthCredentialsService.FACTORY_LINK));
        String authLink = UriUtils.buildUriPath(AuthCredentialsService.FACTORY_LINK,
                auth.documentSelfLink);

        // Create a VM desc
        ComputeDescriptionService.ComputeDescription gcpVMDesc = new ComputeDescriptionService
                .ComputeDescription();
        gcpVMDesc.id = UUID.randomUUID().toString();
        gcpVMDesc.name = gcpVMDesc.id;
        gcpVMDesc.documentSelfLink = gcpVMDesc.id;
        gcpVMDesc.zoneId = zoneId;
        gcpVMDesc.authCredentialsLink = authLink;
        gcpVMDesc.environmentName = ENVIRONMENT_NAME_GCP;
        gcpVMDesc.customProperties = new HashMap<>();
        gcpVMDesc.customProperties.put(CPU_PLATFORM, DEFAULT_CPU_PLATFORM);

        ComputeDescriptionService.ComputeDescription vmComputeDesc = TestUtils
                .doPost(host, gcpVMDesc, ComputeDescriptionService.ComputeDescription.class,
                        UriUtils.buildUri(host, ComputeDescriptionService.FACTORY_LINK));

        List<String> vmDisks = new ArrayList<>();
        DiskService.DiskState rootDisk = new DiskService.DiskState();
        rootDisk.name = gcpVMName + BOOT_DISK_NAME_SUFFIX;
        rootDisk.id = UUID.randomUUID().toString();
        rootDisk.documentSelfLink = rootDisk.id;
        rootDisk.type = DiskService.DiskType.HDD;
        rootDisk.sourceImageReference = URI.create(DEFAULT_IMAGE_REFERENCE);
        rootDisk.bootOrder = 1;
        rootDisk.documentSelfLink = rootDisk.id;

        TestUtils.doPost(host, rootDisk, DiskService.DiskState.class,
                UriUtils.buildUri(host, DiskService.FACTORY_LINK));
        vmDisks.add(UriUtils.buildUriPath(DiskService.FACTORY_LINK, rootDisk.id));

        ComputeService.ComputeState resource = new ComputeService.ComputeState();
        resource.id = String.valueOf(new Random().nextLong());
        resource.documentUpdateTimeMicros = Utils.getNowMicrosUtc();
        resource.parentLink = parentLink;
        resource.descriptionLink = vmComputeDesc.documentSelfLink;
        resource.resourcePoolLink = resourcePoolLink;
        resource.diskLinks = vmDisks;
        resource.documentSelfLink = resource.id;
        resource.customProperties = new HashMap<>();
        resource.customProperties.put(CUSTOM_DISPLAY_NAME, gcpVMName);

        return TestUtils.doPost(host, resource, ComputeService.ComputeState.class,
                UriUtils.buildUri(host, ComputeService.FACTORY_LINK));
    }

    /**
     * Delete the document by specified docuemnt link.
     * @param host The test host service.
     * @param documentToDelete The document link of the document to be deleted.
     * @throws Throwable The exception during deleting document.
     */
    public static void deleteDocument(VerificationHost host, String documentToDelete)
            throws Throwable {
        host.testStart(1);
        host.send(Operation
                .createDelete(
                        UriUtils.buildUri(host,
                                documentToDelete))
                .setBody(new ServiceDocument())
                .setCompletion(host.getCompletion()));
        host.testWait();
    }

    /**
     * Stop the instances of specified project and zone.
     * @param compute The Google Compute Engine client.
     * @param projectId The GCP project ID.
     * @param zoneId The GCP project's zone ID.
     * @throws IOException The IO exception during stopping remote instances.
     */
    public static void stopInstances(Compute compute, String projectId, String zoneId, VerificationHost host)
            throws IOException {
        InstanceList instances = compute.instances().list(projectId, zoneId).execute();
        if (instances.getItems() != null) {
            instances.getItems().forEach(instance -> {
                try {
                    com.google.api.services.compute.model.Operation op =
                            compute.instances().stop(projectId, zoneId, instance.getName()).execute();
                    String zone = op.getZone();
                    zone = extractZoneFromZoneUri(zone);

                    String opId = op.getName();

                    waitForOperationsDone(compute, projectId,
                            new com.google.api.services.compute.model.Operation[] {op},
                            new String[] {zone}, new String[] {opId}, host);
                } catch (IOException e) {
                    host.log(Level.WARNING, String.format("Error: %s", e.getMessage()));
                }
            });
        }
    }

    /**
     * Delete the instances of specified project and zone.
     * @param compute The Google Compute Engine client.
     * @param projectId The GCP project ID.
     * @param zoneId The GCP project's zone ID.
     * @throws IOException The IO exception during deleting remote instances.
     */
    public static void deleteInstances(Compute compute, String projectId, String zoneId, VerificationHost host)
            throws IOException {
        InstanceList instances = compute.instances().list(projectId, zoneId).execute();
        if (instances.getItems() != null) {
            instances.getItems().forEach(instance -> {
                try {
                    com.google.api.services.compute.model.Operation op =
                            compute.instances().delete(projectId, zoneId, instance.getName()).execute();
                    String zone = op.getZone();
                    zone = extractZoneFromZoneUri(zone);

                    String opId = op.getName();

                    waitForOperationsDone(compute, projectId,
                            new com.google.api.services.compute.model.Operation[] {op},
                            new String[] {zone}, new String[] {opId}, host);
                } catch (IOException e) {
                    host.log(Level.WARNING, String.format("Error: %s", e.getMessage()));
                }
            });
        }
    }

    /**
     * Provision the instances of specified project and zone.
     * @param compute The Google Compute Engine client.
     * @param userEmail The service account's client email.
     * @param projectId The GCP project ID.
     * @param zoneId The GCP project's zone ID.
     * @param n The number of instances to provision.
     * @throws Throwable The exception during provisioning remote instances.
     */
    public static void provisionInstances(Compute compute, String userEmail, String projectId,
            String zoneId, int n, VerificationHost host) throws Throwable {
        com.google.api.services.compute.model.Operation[] ops =
                new com.google.api.services.compute.model.Operation[n];
        String[] zones = new String[n];
        String[] opIds = new String[n];
        List<String> scopes = Collections.singletonList(ComputeScopes.CLOUD_PLATFORM);

        for (int i = 0;i < n;i++) {
            ops[i] = provisionOneInstance(userEmail, projectId, zoneId,
                    ENUMERATION_TEST_INSTANCE + i, scopes, compute);
            zones[i] = ops[i].getZone();
            zones[i] = extractZoneFromZoneUri(zones[i]);

            opIds[i] = ops[i].getName();
        }

        waitForOperationsDone(compute, projectId, ops, zones, opIds, host);
    }

    /**
     * Provision one instance of the specified project and zone.
     * @param userEmail The service account's client email.
     * @param projectId The service account's project ID.
     * @param zoneId The GCP project's zone ID.
     * @param instanceName The random instance name of the instance.
     * @param scopes The scopes of the service account.
     * @param compute The compute service used to call GCE APIs.
     * @return The Operation of provisioning the instance.
     * @throws Throwable The exception during creating the instance.
     */
    private static com.google.api.services.compute.model.Operation provisionOneInstance(
            String userEmail, String projectId, String zoneId, String instanceName,
            List<String> scopes, Compute compute) throws Throwable {
        Instance instance = new Instance();
        instance.setName(instanceName);
        instance.setMachineType(String.format(ENUMERATION_TEST_MACHINE_TYPE, projectId, zoneId));

        NetworkInterface ifc = new NetworkInterface();
        ifc.setNetwork(String.format(NETWORK_INTERFACE, projectId));
        List<AccessConfig> configs = new ArrayList<>();
        AccessConfig config = new AccessConfig();
        config.setType(NETWORK_INTERFACE_CONFIG);
        config.setName(NETWORK_ACCESS_CONFIG);
        configs.add(config);
        ifc.setAccessConfigs(configs);
        instance.setNetworkInterfaces(Collections.singletonList(ifc));

        AttachedDisk disk = new AttachedDisk();
        disk.setBoot(true);
        disk.setAutoDelete(true);
        disk.setType(DISK_TYPE_PERSISTENT);
        AttachedDiskInitializeParams params = new AttachedDiskInitializeParams();
        params.setDiskName(instanceName);
        params.setSourceImage(SOURCE_IMAGE);
        params.setDiskType(String.format(DISK_TYPE, projectId, zoneId));
        disk.setInitializeParams(params);
        instance.setDisks(Collections.singletonList(disk));

        ServiceAccount account = new ServiceAccount();
        account.setEmail(userEmail);
        account.setScopes(scopes);
        instance.setServiceAccounts(Collections.singletonList(account));

        Compute.Instances.Insert insert = compute.instances().insert(projectId, zoneId, instance);
        return insert.execute();
    }

    /**
     * Generate a random name with the specified prefix.
     * @param prefix The prefix of the random name.
     * @return The generated name.
     */
    public static String generateRandomName(String prefix) {
        StringBuilder sb = new StringBuilder();
        Random random = new Random();

        sb.append(prefix);

        for (int i = 0;i < 5;i++) {
            sb.append('a' + random.nextInt(26));
        }

        return sb.toString();
    }

    /**
     * Query if there are desired number of compute states
     * with desired power states.
     * @param host The host server of the test.
     * @param resourcePool The default resource pool.
     * @param parentCompute The default compute host.
     * @param powerState The desired power state.
     * @param num The desired number of compute states.
     * @throws TimeoutException
     */
    public static void syncQueryComputeStatesWithPowerState(VerificationHost host,
                                                            ResourcePoolState resourcePool,
                                                            ComputeState parentCompute,
                                                            PowerState powerState,
                                                            int num)
            throws TimeoutException {
        Date expiration = host.getTestExpiration();
        do {
            queryComputeStatesWithPowerState(host, resourcePool, parentCompute,
                    powerState, num);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                host.log(Level.WARNING, String.format("Error: %s", e.getMessage()));
            }
            if (queryResult) {
                queryResult = false;
                return;
            }
        } while (new Date().before(expiration));
        throw new TimeoutException("Desired number of compute states with power state "
                + powerState.toString() + " not found.");
    }

    private static void queryComputeStatesWithPowerState(VerificationHost host,
                                                        ResourcePoolState resourcePool,
                                                        ComputeState parentCompute,
                                                        PowerState powerState,
                                                        int num) {
        Query query = QueryTask.Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_RESOURCE_POOL_LINK,
                        resourcePool.documentSelfLink)
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK,
                        parentCompute.documentSelfLink)
                .build();

        QueryTask q = QueryTask.Builder.createDirectTask()
                .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
                .setQuery(query)
                .build();

        host.sendRequest(Operation
                .createPost(host, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(q)
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.log(Level.WARNING, String.format("Error: %s", e.getMessage()));
                        return;
                    }
                    QueryTask queryTask = o.getBody(QueryTask.class);
                    if (queryTask.results.documentCount == num) {
                        for (Object s : queryTask.results.documents.values()) {
                            ComputeState computeState = Utils.fromJson(s, ComputeState.class);
                            if (!computeState.powerState.equals(powerState)) {
                                return;
                            }
                        }
                        queryResult = true;
                    }
                }));
    }

    /**
     * Wait for all the operations to be done.
     * @param compute The Google Compute Engine client.
     * @param projectId The project id.
     * @param ops The operations.
     * @param zones The zones.
     * @param opIds The operation ids.
     * @throws IOException
     */
    private static void waitForOperationsDone(Compute compute,
                                              String projectId,
                                              com.google.api.services.compute.model.Operation[] ops,
                                              String[] zones,
                                              String[] opIds,
                                              VerificationHost host) throws IOException {
        for (int i = 0;i < ops.length;i++) {
            while (ops[i] != null && !ops[i].getStatus().equals(OPERATION_STATUS_DONE)) {
                try {
                    Thread.sleep(WAIT_INTERVAL);
                } catch (Exception e) {
                    host.log(Level.WARNING, String.format("Error: %s", e.getMessage()));
                }

                if (zones[i] != null) {
                    Compute.ZoneOperations.Get get = compute.zoneOperations().get(projectId,
                            zones[i], opIds[i]);
                    ops[i] = get.execute();
                    continue;
                }
                Compute.GlobalOperations.Get get = compute.globalOperations().get(projectId,
                        opIds[i]);
                ops[i] = get.execute();
            }
        }
    }

    /**
     * Extract the zone name from the zone uri.
     * @param zoneUri The zone uri in response.
     * @return The zone name.
     */
    private static String extractZoneFromZoneUri(String zoneUri) {
        if (zoneUri != null) {
            String[] bits = zoneUri.split("/");
            zoneUri = bits[bits.length - 1];
        }
        return zoneUri;
    }
}
