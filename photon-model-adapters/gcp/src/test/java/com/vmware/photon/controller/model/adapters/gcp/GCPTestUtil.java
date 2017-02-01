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

import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.CPU_PLATFORM;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.DEFAULT_AUTH_TYPE;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.DEFAULT_CPU_PLATFORM;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.DEFAULT_IMAGE_REFERENCE;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.DISK_TYPE_PERSISTENT;
import static com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants.OPERATION_STATUS_DONE;
import static com.vmware.photon.controller.model.adapters.gcp.utils.GCPUtils.privateKeyFromPkcs8;
import static com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ENVIRONMENT_NAME_GCP;
import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.createServiceURI;
import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.getVMCount;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.Compute.Instances;
import com.google.api.services.compute.ComputeScopes;
import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.AttachedDiskInitializeParams;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceList;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.ServiceAccount;

import com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants;
import com.vmware.photon.controller.model.adapters.gcp.enumeration.GCPEnumerationAdapterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService.ResourceEnumerationTaskState;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.ServiceUriPaths;

public class GCPTestUtil {
    private static final String ADAPTER_TEST_INSTANCE = "adapter-test-instance-";
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
    private static final long ONE_HOUR_DIFFERENCE_MICROS = TimeUnit.HOURS.toMicros(1);
    private static final long WAIT_INTERVAL = 1000;
    private static final long MIN_CPU_COUNT = 1;
    private static final long MIN_MEMORY_BYTES = 1024;
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    /**
     * Create a resource pool where the VM will be housed.
     * @param host The test host service.
     * @return The default resource pool.
     * @throws Throwable The exception during creating resource pool.
     */
    public static ResourcePoolService.ResourcePoolState createDefaultResourcePool(
            VerificationHost host)
            throws Throwable {
        ResourcePoolService.ResourcePoolState inPool = new ResourcePoolService.ResourcePoolState();

        inPool.name = UUID.randomUUID().toString();
        inPool.id = inPool.name;
        inPool.minCpuCount = MIN_CPU_COUNT;
        inPool.minMemoryBytes = MIN_MEMORY_BYTES;

        return TestUtils.doPost(host, inPool, ResourcePoolService.ResourcePoolState.class,
                UriUtils.buildUri(host, ResourcePoolService.FACTORY_LINK));
    }

    /**
     * Create a resource group for a GCP project.
     * @param host The test host service.
     * @param projectId The GCP project ID.
     * @return The default resource group.
     * @throws Throwable The exception during creating compute host.
     */
    public static ResourceGroupState createDefaultResourceGroup(
            VerificationHost host,
            String projectId)
            throws Throwable {
        ResourceGroupState resourceGroup = new ResourceGroupState();

        resourceGroup.name = projectId;

        return TestUtils.doPost(host, resourceGroup, ResourceGroupState.class,
                UriUtils.buildUri(host, ResourceGroupService.FACTORY_LINK));
    }

    /**
     * Create a compute host description for a GCP instance
     * @param host The test host service.
     * @param userEmail The service account's client email.
     * @param privateKey The service account's private key.
     * @param zoneId The GCP project's zone ID.
     * @param resourcePoolLink The default resource pool's link.
     * @param resourceGroupLink The default resource group's link.
     * @return The default compute host.
     * @throws Throwable The exception during creating compute host.
     */
    public static ComputeService.ComputeState createDefaultComputeHost(VerificationHost host,
                                                                       String userEmail,
                                                                       String privateKey,
                                                                       String zoneId,
                                                                       String resourcePoolLink,
                                                                       String resourceGroupLink)
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
        gcpHostDescription.name = gcpHostDescription.id;
        gcpHostDescription.documentSelfLink = gcpHostDescription.id;
        gcpHostDescription.enumerationAdapterReference = UriUtils.buildUri(host,
                GCPUriPaths.GCP_ENUMERATION_ADAPTER);
        gcpHostDescription.statsAdapterReference = UriUtils.buildUri(host,
                GCPUriPaths.GCP_STATS_ADAPTER);
        gcpHostDescription.zoneId = zoneId;
        gcpHostDescription.authCredentialsLink = authLink;
        gcpHostDescription.groupLinks = new HashSet<>();
        gcpHostDescription.groupLinks.add(resourceGroupLink);

        TestUtils.doPost(host, gcpHostDescription,
                ComputeDescriptionService.ComputeDescription.class,
                UriUtils.buildUri(host, ComputeDescriptionService.FACTORY_LINK));

        ComputeService.ComputeState gcpComputeHost = new ComputeService.ComputeState();
        gcpComputeHost.id = UUID.randomUUID().toString();
        gcpComputeHost.type = ComputeType.VM_HOST;
        gcpComputeHost.environmentName = ComputeDescription.ENVIRONMENT_NAME_GCP;
        gcpComputeHost.name = gcpHostDescription.name;
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
        resource.type = ComputeType.VM_GUEST;
        resource.environmentName = ComputeDescription.ENVIRONMENT_NAME_GCP;
        resource.name = gcpVMName;
        resource.documentUpdateTimeMicros = Utils.getNowMicrosUtc();
        resource.parentLink = parentLink;
        resource.descriptionLink = vmComputeDesc.documentSelfLink;
        resource.resourcePoolLink = resourcePoolLink;
        resource.diskLinks = vmDisks;
        resource.documentSelfLink = resource.id;
        resource.customProperties = new HashMap<>();

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
     * @param host The test service host.
     * @param compute The Google Compute Engine client.
     * @param projectId The GCP project ID.
     * @param zoneId The GCP project's zone ID.
     * @param instanceNames The list of instances' names to be stopped.
     * @throws IOException The IO exception during stopping remote instances.
     */
    public static void stopInstances(VerificationHost host, Compute compute, String projectId, String zoneId,
                                     List<String> instanceNames, int batchSize, long interval) throws Throwable {
        if (batchSize <= 0) {
            throw new Exception("batch size cannot be less or equal to zero.");
        }
        if (interval <= 0) {
            throw new Exception("waiting interval cannot be less or equal to zero");
        }
        if (instanceNames != null) {
            int num = instanceNames.size();
            com.google.api.services.compute.model.Operation[] ops =
                    new com.google.api.services.compute.model.Operation[num];
            String[] zones = new String[num];
            String[] opIds = new String[num];
            for (int i = 0;i < num;i++) {
                String instanceName = instanceNames.get(i);
                try {
                    ops[i] = compute.instances().stop(projectId, zoneId, instanceName).execute();
                    zones[i] = ops[i].getZone();
                    zones[i] = extractZoneFromZoneUri(zones[i]);
                    opIds[i] = ops[i].getName();
                    // There is an upper bound for the frequency of making Google API calls.
                    // This is to prevent making too much API calls in a very short time.
                    if ((i + 1) % batchSize == 0) {
                        TimeUnit.MILLISECONDS.sleep(interval);
                    }
                } catch (Exception e) {
                    host.log(Level.WARNING, "Error when stopping instances: " + e.getMessage());
                }
            }
            waitForOperationsDone(host, compute, projectId, ops, zones, opIds);
        }
    }

    /**
     * Delete the instances of specified project and zone.
     * @param host The test service host.
     * @param compute The Google Compute Engine client.
     * @param projectId The GCP project ID.
     * @param zoneId The GCP project's zone ID.
     * @param instanceNames The list of instances' names to be deleted.
     * @param batchSize The batch size.
     * @param interval The waiting interval.
     * @return The list of instances to be cleaned up.
     * @throws IOException The IO exception during deleting remote instances.
     */
    public static List<String> deleteInstances(VerificationHost host, Compute compute, String projectId, String zoneId,
                                               List<String> instanceNames, int batchSize, long interval)
            throws Throwable {
        if (batchSize <= 0) {
            throw new Exception("batch size cannot be less or equal to zero.");
        }
        if (interval <= 0) {
            throw new Exception("waiting interval cannot be less or equal to zero");
        }
        List<String> instancesToCleanUp = new ArrayList<>();
        if (instanceNames != null) {
            int num = instanceNames.size();
            com.google.api.services.compute.model.Operation[] ops =
                    new com.google.api.services.compute.model.Operation[num];
            String[] zones = new String[num];
            String[] opIds = new String[num];
            for (int i = 0;i < num;i++) {
                String instanceName = instanceNames.get(i);
                try {
                    ops[i] = compute.instances().delete(projectId, zoneId, instanceName).execute();
                    zones[i] = ops[i].getZone();
                    zones[i] = extractZoneFromZoneUri(zones[i]);
                    opIds[i] = ops[i].getName();
                    // There is an upper bound for the frequency of making Google API calls.
                    // This is to prevent making too much API calls in a very short time.
                    if ((i + 1) % batchSize == 0) {
                        TimeUnit.MILLISECONDS.sleep(interval);
                    }
                } catch (Exception e) {
                    host.log(Level.WARNING, "Error when deleting instances: " + e.getMessage());
                    e.printStackTrace();
                    instancesToCleanUp.add(instanceName);
                }
            }
            waitForOperationsDone(host, compute, projectId, ops, zones, opIds);
        }
        return instancesToCleanUp;
    }

    /**
     * Provision the given number of instances in specified project and zone.
     * @param host The test service host.
     * @param compute The Google Compute Engine client.
     * @param userEmail The service account's client email.
     * @param projectId The GCP project ID.
     * @param zoneId The GCP project's zone ID.
     * @param n The number of instances to provision.
     * @param batchSize The batch size.
     * @param interval The waiting interval.
     * @throws Throwable The exception during provisioning remote instances.
     */
    public static List<String> provisionInstances(VerificationHost host, Compute compute, String userEmail,
                                                  String projectId, String zoneId, int n, int batchSize, long interval)
            throws Throwable {
        if (n < 0) {
            throw new IllegalArgumentException("the number of instances to be provisioned cannot be negative.");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batch size cannot be less or equal to zero.");
        }
        if (interval <= 0) {
            throw new IllegalArgumentException("waiting interval cannot be less or equal to zero");
        }
        List<String> scopes = Collections.singletonList(ComputeScopes.CLOUD_PLATFORM);
        List<String> instanceNames = new ArrayList<>();
        com.google.api.services.compute.model.Operation[] ops =
                new com.google.api.services.compute.model.Operation[n];
        String[] zones = new String[n];
        String[] opIds = new String[n];
        Instance instance = createInstanceTemplate(userEmail, projectId, zoneId, scopes);

        for (int i = 0;i < n;i++) {
            String instanceName = ADAPTER_TEST_INSTANCE + UUID.randomUUID().toString();
            instanceNames.add(instanceName);
            ops[i] = provisionOneInstance(compute, instance, instanceName, projectId, zoneId);
            zones[i] = ops[i].getZone();
            zones[i] = extractZoneFromZoneUri(zones[i]);
            opIds[i] = ops[i].getName();
            // There is an upper bound for the frequency of making Google API calls.
            // This is to prevent making too much API calls in a very short time.
            if ((i + 1) % batchSize == 0) {
                TimeUnit.MILLISECONDS.sleep(interval);
            }
        }

        waitForOperationsDone(host, compute, projectId, ops, zones, opIds);

        return instanceNames;
    }

    /**
     * Provision one instance of the specified project and zone.
     * @param compute The compute service used to call GCE APIs.
     * @param projectId The service account's project ID.
     * @param zoneId The GCP project's zone ID.
     * @return The Operation of provisioning the instance.
     * @throws Throwable The exception during creating the instance.
     */
    private static com.google.api.services.compute.model.Operation provisionOneInstance(Compute compute,
                                                                                        Instance instance,
                                                                                        String instanceName,
                                                                                        String projectId,
                                                                                        String zoneId)
            throws Throwable {
        instance.setName(instanceName);
        instance.getDisks().get(0).getInitializeParams().setDiskName(instanceName);
        Compute.Instances.Insert insert = compute.instances().insert(projectId, zoneId, instance);
        return insert.execute();
    }

    /**
     * Create an instance template for later provisioning.
     * @param userEmail The service account's client email.
     * @param projectId The project id.
     * @param zoneId The zone id.
     * @param scopes The priority scopes.
     * @return The instance template.
     */
    private static Instance createInstanceTemplate(String userEmail, String projectId, String zoneId,
                                                   List<String> scopes) {
        Instance instance = new Instance();
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
        params.setSourceImage(SOURCE_IMAGE);
        params.setDiskType(String.format(DISK_TYPE, projectId, zoneId));
        disk.setInitializeParams(params);
        instance.setDisks(Collections.singletonList(disk));

        ServiceAccount account = new ServiceAccount();
        account.setEmail(userEmail);
        account.setScopes(scopes);
        instance.setServiceAccounts(Collections.singletonList(account));

        return instance;
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
     * Get a Google Compute Engine client object.
     * @param userEmail The service account's client email.
     * @param privateKey The service account's private key.
     * @param scopes The scopes used in the compute client object.
     * @param applicationName The application name.
     * @return The created Compute Engine client object.
     * @throws GeneralSecurityException Exception when creating http transport.
     * @throws IOException Exception when creating http transport.
     */
    public static Compute getGoogleComputeClient(String userEmail,
                                                 String privateKey,
                                                 List<String> scopes,
                                                 String applicationName)
            throws GeneralSecurityException, IOException {
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        GoogleCredential credential = new GoogleCredential.Builder()
                .setTransport(httpTransport)
                .setJsonFactory(JSON_FACTORY)
                .setServiceAccountId(userEmail)
                .setServiceAccountScopes(scopes)
                .setServiceAccountPrivateKey(privateKeyFromPkcs8(privateKey))
                .build();
        return new Compute.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(applicationName)
                .build();
    }

    /**
     * Method to perform compute resource enumeration on the GCP endpoint.
     * @param host The test service host.
     * @param peerURI The peer uri.
     * @param enumTask The enumeration task.
     * @return The enumeration task state.
     * @throws Throwable Exception when sending enumeration request.
     */
    public static ResourceEnumerationTaskState performResourceEnumeration(VerificationHost host,
                                                                          URI peerURI,
                                                                          ResourceEnumerationTaskState enumTask)
            throws Throwable {
        URI uri = createServiceURI(host, peerURI, ResourceEnumerationTaskService.FACTORY_LINK);
        return TestUtils.doPost(host, enumTask, ResourceEnumerationTaskState.class, uri);
    }

    /**
     * Get the number of instances on specified GCP project and zone.
     * @param compute The GCE client object.
     * @param projectId The project id.
     * @param zoneId The zone id.
     * @return The number of instances.
     * @throws Throwable Exception during querying the instances
     */
    public static int getInstanceNumber(Compute compute, String projectId, String zoneId) throws Throwable {
        Instances instanceList = compute.instances();
        Instances.List list = instanceList.list(projectId, zoneId);
        InstanceList ins = list.execute();
        List<Instance> instances = ins.getItems();
        if (instances == null) {
            return 0;
        }
        return instances.size();
    }

    /**
     * Get the names of all stale instances remaining from previous runs due to read timeout
     * failures.
     * @param compute The GCE client object.
     * @param projectId The project id.
     * @param zoneId The zone id.
     * @return Names of stale instances
     * @throws Throwable Exception during querying the instances
     */
    public static List<String> getStaleInstanceNames(Compute compute, String projectId,
            String zoneId) throws Throwable {
        Instances instanceList = compute.instances();
        Instances.List list = instanceList.list(projectId, zoneId);
        InstanceList ins = list.execute();
        List<Instance> instances = ins.getItems();
        List<String> names = new ArrayList<String>();

        if (instances == null) {
            return null;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat(GCPConstants.VM_CREATION_TIMESTAMP_FORMAT);
        dateFormat.setTimeZone(TimeZone.getTimeZone(GCPConstants.UTC_TIMEZONE_ID));

        for (Instance i : instances) {
            Date date = dateFormat.parse(i.getCreationTimestamp());
            long time = TimeUnit.MILLISECONDS.toMicros(date.getTime());
            if (Utils.getNowMicrosUtc() - time > ONE_HOUR_DIFFERENCE_MICROS && i.getName()
                    .startsWith("adapter-test-instance")) {
                names.add(i.getName());
            }
        }

        return names;
    }

    /**
     * Method to run enumeration on GCP endpoint.
     * @param host The test service host.
     * @param peerURI The peer uri.
     * @param enumTask The enumeration task to run.
     * @param testCase The test case name.
     * @throws Throwable Exception when running enumeration tasks.
     */
    public static void enumerateResources(VerificationHost host, URI peerURI,
                                          ResourceEnumerationTaskState enumTask, String testCase)
            throws Throwable {
        // Perform resource enumeration on the GCP end point.
        // Pass the references to the GCP compute host.
        host.log("Performing resource enumeration");
        ResourceEnumerationTaskState enumTaskState = performResourceEnumeration(host, peerURI, enumTask);

        // Wait for the enumeration task to be completed.
        host.waitForFinishedTask(ResourceEnumerationTaskState.class,
                createServiceURI(host, peerURI, enumTaskState.documentSelfLink));

        host.log("\n==%s==Total Time Spent in Enumeration==\n", testCase + getVMCount(host, peerURI));
        ServiceStats enumerationStats = host.getServiceState(null, ServiceStats.class, UriUtils
                .buildStatsUri(createServiceURI(host, peerURI, GCPEnumerationAdapterService.SELF_LINK)));
        host.log(Utils.toJsonHtml(enumerationStats));
    }

    /**
     * Query if there are desired number of compute states
     * with desired power states.
     * @param host The host server of the test.
     * @param resourcePool The default resource pool.
     * @param parentCompute The default compute host.
     * @param powerState The desired power state.
     * @param instanceNames The desired names of compute states.
     * @throws Throwable When reach the time limit.
     */
    public static void syncQueryComputeStatesWithPowerState(VerificationHost host,
                                                            ResourcePoolState resourcePool,
                                                            ComputeState parentCompute,
                                                            PowerState powerState,
                                                            Set<String> instanceNames)
            throws Throwable {
        host.waitFor("Waiting for changes of power statuses", () -> {
            queryComputeStatesWithPowerState(host, resourcePool, parentCompute, powerState, instanceNames);
            return instanceNames.isEmpty();
        });
    }

    /**
     * Method to query the number of local compute states with given power state.
     * @param host The test service host.
     * @param resourcePool The default resource pool.
     * @param parentCompute The default compute host.
     * @param powerState The given power state.
     * @param instanceNames The assumed names of compute states.
     */
    private static void queryComputeStatesWithPowerState(VerificationHost host,
                                                        ResourcePoolState resourcePool,
                                                        ComputeState parentCompute,
                                                        PowerState powerState,
                                                         Set<String> instanceNames) {
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
                .createPost(host, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS)
                .setBody(q)
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.log(Level.WARNING, String.format("Error: %s", e.getMessage()));
                        return;
                    }
                    QueryTask queryTask = o.getBody(QueryTask.class);
                    if (queryTask.results.documentCount > 0) {
                        queryTask.results.documents.values().forEach(s -> {
                            ComputeState computeState = Utils.fromJson(s, ComputeState.class);
                            if (computeState.powerState == powerState) {
                                instanceNames.remove(computeState.name);
                            }
                        });
                    }
                }));
    }

    /**
     * Wait for all the operations to be done.
     * @param host The test service host.
     * @param compute The Google Compute Engine client.
     * @param projectId The project id.
     * @param ops The operations.
     * @param zones The zones.
     * @param opIds The operation ids.
     * @throws IOException When operation fails on fetch status.
     */
    private static void waitForOperationsDone(VerificationHost host,
                                              Compute compute,
                                              String projectId,
                                              com.google.api.services.compute.model.Operation[] ops,
                                              String[] zones,
                                              String[] opIds) throws IOException {
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
