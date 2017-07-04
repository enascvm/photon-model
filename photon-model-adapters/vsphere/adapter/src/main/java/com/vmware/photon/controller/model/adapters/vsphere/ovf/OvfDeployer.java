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

package com.vmware.photon.controller.model.adapters.vsphere.ovf;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.photon.controller.model.adapters.vsphere.VimUtils;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BaseHelper;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.GetMoRef;
import com.vmware.photon.controller.model.adapters.vsphere.util.finders.FinderException;
import com.vmware.vim25.HttpNfcLeaseDeviceUrl;
import com.vmware.vim25.HttpNfcLeaseInfo;
import com.vmware.vim25.KeyValue;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.OvfCreateImportSpecParams;
import com.vmware.vim25.OvfCreateImportSpecParamsDiskProvisioningType;
import com.vmware.vim25.OvfCreateImportSpecResult;
import com.vmware.vim25.OvfFileItem;
import com.vmware.vim25.OvfNetworkMapping;
import com.vmware.vim25.VirtualMachineConfigSpec;
import com.vmware.vim25.VmConfigSpec;
import com.vmware.xenon.common.Operation;

public class OvfDeployer extends BaseHelper {
    public static final String CONTENT_TYPE_VMDK = "application/x-vnd.vmware-streamVmdk";

    private static final Logger logger = LoggerFactory.getLogger(OvfDeployer.class.getName());
    private static final String PROP_INFO = "info";

    public static final String TRANSPORT_GUESTINFO = "com.vmware.guestInfo";
    public static final String TRANSPORT_ISO = "iso";
    private OvfRetriever ovfRetriever;

    public OvfDeployer(Connection connection) throws FinderException {
        super(connection);

        CloseableHttpClient client = OvfRetriever.newInsecureClient();
        this.ovfRetriever = new OvfRetriever(client);
    }

    private long getImportSizeBytes(OvfCreateImportSpecResult importResult) {
        List<OvfFileItem> items = importResult.getFileItem();
        if (items == null) {
            return 0;
        }

        long totalBytes = 0;
        for (OvfFileItem fi : items) {
            totalBytes += fi.getSize();
        }

        return totalBytes;
    }

    public ManagedObjectReference deployOvf(
            URI ovfUri,
            ManagedObjectReference host,
            ManagedObjectReference vmFolder,
            String vmName,
            List<OvfNetworkMapping> networks,
            ManagedObjectReference datastore,
            Collection<KeyValue> ovfProps,
            String deploymentConfig,
            ManagedObjectReference resourcePool) throws Exception {

        String ovfDescriptor = getRetriever().retrieveAsString(ovfUri);

        OvfCreateImportSpecParams params = new OvfCreateImportSpecParams();
        params.setHostSystem(host);
        params.setLocale("US");
        params.setEntityName(vmName);

        if (deploymentConfig == null) {
            deploymentConfig = "";
        }
        params.setDeploymentOption(deploymentConfig);

        params.getNetworkMapping().addAll(networks);
        params.setDiskProvisioning(OvfCreateImportSpecParamsDiskProvisioningType.THIN.name());

        if (ovfProps != null) {
            params.getPropertyMapping().addAll(ovfProps);
        }

        ManagedObjectReference ovfManager = this.connection.getServiceContent().getOvfManager();

        OvfCreateImportSpecResult importSpecResult = getVimPort().createImportSpec(
                ovfManager,
                ovfDescriptor,
                resourcePool,
                datastore,
                params);

        if (!importSpecResult.getError().isEmpty()) {
            return VimUtils.rethrow(importSpecResult.getError().get(0));
        }

        long totalBytes = getImportSizeBytes(importSpecResult);

        ManagedObjectReference lease = getVimPort().importVApp(
                resourcePool,
                importSpecResult.getImportSpec(),
                vmFolder,
                host);

        LeaseProgressUpdater leaseUpdater = new LeaseProgressUpdater(this.connection, lease,
                totalBytes);

        GetMoRef get = new GetMoRef(this.connection);
        HttpNfcLeaseInfo httpNfcLeaseInfo;

        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
        try {
            leaseUpdater.awaitReady();
            logger.info("Lease ready");

            // start updating the lease
            leaseUpdater.start(executorService);

            httpNfcLeaseInfo = get.entityProp(lease, PROP_INFO);

            List<HttpNfcLeaseDeviceUrl> deviceUrls = httpNfcLeaseInfo.getDeviceUrl();

            String ip = this.connection.getURI().getHost();

            String basePath = extractBasePath(ovfUri);

            for (HttpNfcLeaseDeviceUrl deviceUrl : deviceUrls) {
                String deviceKey = deviceUrl.getImportKey();

                for (OvfFileItem ovfFileItem : importSpecResult.getFileItem()) {
                    if (deviceKey.equals(ovfFileItem.getDeviceId())) {
                        logger.debug("Importing device id: {}", deviceKey);
                        String sourceUri = computeDiskSourceUri(basePath, ovfFileItem);
                        String uploadUri = makUploadUri(ip, deviceUrl);
                        uploadVmdkFile(ovfFileItem, sourceUri, uploadUri, leaseUpdater, this.ovfRetriever.getClient());
                        logger.info("Completed uploading VMDK file {}", sourceUri);
                    }
                }
            }

            // complete lease
            leaseUpdater.complete();
        } catch (Exception e) {
            leaseUpdater.abort(VimUtils.convertExceptionToFault(e));
            logger.info("Error importing ovf", e);
            throw e;
        } finally {
            executorService.shutdown();
        }

        httpNfcLeaseInfo = get.entityProp(lease, PROP_INFO);
        ManagedObjectReference entity = httpNfcLeaseInfo.getEntity();

        // as this is an OVF it makes sense to enable the OVF transport
        // only the guestInfo is enabled by default
        VmConfigSpec spec = new VmConfigSpec();
        spec.getOvfEnvironmentTransport().add(TRANSPORT_GUESTINFO);
        spec.getOvfEnvironmentTransport().add(TRANSPORT_ISO);
        VirtualMachineConfigSpec reconfig = new VirtualMachineConfigSpec();
        reconfig.setVAppConfig(spec);

        ManagedObjectReference reconfigTask = getVimPort().reconfigVMTask(entity, reconfig);
        VimUtils.waitTaskEnd(this.connection, reconfigTask);

        return entity;
    }

    protected String computeDiskSourceUri(String basePath, OvfFileItem ovfFileItem) {
        String s = ovfFileItem.getPath();
        if (s.startsWith("https://") ||
                s.startsWith("http://") ||
                s.startsWith("file://")) {
            return s;
        }

        return basePath + s;
    }

    private String makUploadUri(String ip, HttpNfcLeaseDeviceUrl deviceUrl) {
        return deviceUrl.getUrl().replace("*", ip);
    }

    private String extractBasePath(URI ovfUri) {
        return ovfUri.toString()
                .substring(0, ovfUri.toString().lastIndexOf("/") + 1);
    }

    private void uploadVmdkFile(
            OvfFileItem ovfFileItem,
            String sourceUri,
            String uploadUri,
            LeaseProgressUpdater leaseUpdater,
            HttpClient client) throws IOException {

        //prepare upload method
        HttpEntityEnclosingRequestBase upload;
        if (ovfFileItem.isCreate()) {
            upload = new HttpPut(uploadUri);
        } else {
            upload = new HttpPost(uploadUri);
        }

        upload.setHeader(Operation.CONTENT_TYPE_HEADER, CONTENT_TYPE_VMDK);

        HttpEntity entityToUpload;

        if (sourceUri.startsWith("file:/")) {
            entityToUpload = new FileEntity(new File(URI.create(sourceUri)));
        } else {
            //prepare download method
            HttpGet download = new HttpGet(sourceUri);

            //start download
            HttpResponse downloadResponse = client.execute(download);
            entityToUpload = downloadResponse.getEntity();
        }

        //chain download to upload
        upload.setEntity(newCountingEntity(entityToUpload, leaseUpdater));

        //start chained upload
        HttpResponse uploadResponse = client.execute(upload);

        //block until upload completes
        EntityUtils.consume(uploadResponse.getEntity());
    }

    private CountingEntityWrapper newCountingEntity(HttpEntity httpEntity,
            LeaseProgressUpdater leaseUpdater) {
        return new CountingEntityWrapper(httpEntity, leaseUpdater);
    }

    public OvfRetriever getRetriever() {
        return this.ovfRetriever;
    }
}
