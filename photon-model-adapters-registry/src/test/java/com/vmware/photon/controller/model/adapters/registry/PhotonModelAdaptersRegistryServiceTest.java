/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.adapters.registry;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;

import org.junit.Assert;
import org.junit.Test;

import com.vmware.photon.controller.model.UriPaths.AdapterTypePath;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService.PhotonModelAdapterConfig;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;

public class PhotonModelAdaptersRegistryServiceTest extends BaseAdaptersRegistryServiceTest {

    private static final String ICON = "icon";

    @Test
    public void testRegisterAdapter() throws Exception {

        PhotonModelAdapterConfig config = getPhotonModelAdapterConfig(
                "testRegisterAdapter",
                "Test Register Adapter",
                "testRegisterAdapter.png",
                AdapterTypePath.ENUMERATION_ADAPTER.key,
                AdapterTypePath.INSTANCE_ADAPTER.key,
                AdapterTypePath.ENDPOINT_CONFIG_ADAPTER.key);

        Operation registerOp = Operation.createPost(
                super.host,
                PhotonModelAdaptersRegistryService.FACTORY_LINK)
                .setBody(config);
        Operation response = super.host.waitForResponse(registerOp);
        Assert.assertNotNull(response);
        this.logger.info("Response: " + response);
        PhotonModelAdapterConfig body = response.getBody(PhotonModelAdapterConfig.class);
        Operation getAdapterConfig = Operation.createGet(super.host, body.documentSelfLink);
        PhotonModelAdapterConfig registered = super.host.waitForResponse(getAdapterConfig)
                .getBody(PhotonModelAdapterConfig.class);
        Assert.assertNotNull(registered);
        Assert.assertEquals(
                config.name,
                registered.name);
        Assert.assertEquals(
                config.customProperties.get(ICON),
                registered.customProperties.get(ICON));
        Assert.assertEquals(
                config.adapterEndpoints.get(AdapterTypePath.ENDPOINT_CONFIG_ADAPTER.key),
                registered.adapterEndpoints.get(AdapterTypePath.ENDPOINT_CONFIG_ADAPTER.key));
    }

    @Test
    public void testGetByEndpointType() throws Exception {

        String k1 = "k1";
        String k2 = "k2";
        String k3 = "k3";

        PhotonModelAdapterConfig config1 = getPhotonModelAdapterConfig(
                "testGetByEndpointType-1",
                "Test Get By Endpoint Type 1",
                "testGetByEndpointType-1.png",
                k1, k3);

        Operation registerOp1 = Operation.createPost(
                super.host,
                PhotonModelAdaptersRegistryService.FACTORY_LINK)
                .setBody(config1);
        super.host.waitForResponse(registerOp1);

        PhotonModelAdapterConfig config2 = getPhotonModelAdapterConfig(
                "testGetByEndpointType-2",
                "Test Get By Endpoint Type 2",
                "testGetByEndpointType-2.png",
                k2, k3);

        Operation registerOp2 = Operation.createPost(
                super.host,
                PhotonModelAdaptersRegistryService.FACTORY_LINK)
                .setBody(config2);
        super.host.waitForResponse(registerOp2);

        Query query = Query.Builder.create()
                .addKindFieldClause(PhotonModelAdapterConfig.class)
                // .addFieldClause(PhotonModelAdapterConfig.FIELD_NAME_ID, "testGetByEndpointType-2")
                .addFieldClause(
                        QuerySpecification.buildCompositeFieldName(
                                PhotonModelAdapterConfig.FIELD_NAME_ADAPTER_ENDPOINTS,
                                k2),
                        "*", MatchType.WILDCARD)
                .build();
        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .setQuery(query).build();

        Operation queryOp = Operation.createPost(super.host, ServiceUriPaths.CORE_QUERY_TASKS)
                .setReferer(getClass().getSimpleName())
                .setBody(queryTask);
        Operation response = super.host.waitForResponse(queryOp);
        QueryTask qTask = response.getBody(QueryTask.class);
        Collection<Object> values = qTask.results.documents.values();
        Assert.assertEquals(1, values.size());
        JsonObject k2Config = (JsonObject) values.iterator().next();

        Assert.assertEquals(config2.id,
                k2Config.getAsJsonPrimitive(PhotonModelAdapterConfig.FIELD_NAME_ID).getAsString());
    }

    @Test
    public void testCreateDeleteCreate() {
        PhotonModelAdapterConfig config = getPhotonModelAdapterConfig(
                "testCreateDeleteCreate",
                "Test CreateDelete Create",
                "testCreateDeleteCreate.png",
                AdapterTypePath.ENUMERATION_ADAPTER.key,
                AdapterTypePath.INSTANCE_ADAPTER.key,
                AdapterTypePath.ENDPOINT_CONFIG_ADAPTER.key);

        this.logger.info("register: " + config);
        Operation registerOp = super.host.waitForResponse(
                Operation.createPost(
                        super.host,
                        PhotonModelAdaptersRegistryService.FACTORY_LINK)
                        .setBody(config)
        );
        Assert.assertNotNull(registerOp);
        PhotonModelAdapterConfig body = registerOp.getBody(PhotonModelAdapterConfig.class);

        this.logger.info("delete: " + body.documentSelfLink);
        Operation deleteAdapterConfig = super.host.waitForResponse(
                Operation.createDelete(super.host, body.documentSelfLink));
        Assert.assertEquals(200, deleteAdapterConfig.getStatusCode());

        this.logger.info("get: " + body.documentSelfLink);
        Operation getAdapterConfig = super.host.waitForResponse(
                Operation.createGet(super.host, body.documentSelfLink));
        Assert.assertEquals(404, getAdapterConfig.getStatusCode());

        {
            this.logger.info("register again: " + config);
            registerOp = super.host.waitForResponse(Operation.createPost(
                    super.host,
                    PhotonModelAdaptersRegistryService.FACTORY_LINK)
                    .setBody(config));
            Assert.assertNotNull(registerOp);
            Assert.assertEquals(200, registerOp.getStatusCode());
        }
    }

    private PhotonModelAdapterConfig getPhotonModelAdapterConfig(
            String id,
            String name,
            String icon,
            String... keys) {
        PhotonModelAdapterConfig config = new PhotonModelAdapterConfig();
        config.id = id;
        config.name = name;
        config.documentSelfLink = config.id;
        Map<String, String> customProperties = new HashMap<>();
        customProperties.put(ICON, icon);
        config.customProperties = customProperties;
        Map<String, String> endpoints = new HashMap<>();
        for (String key : keys) {
            endpoints.put(key, key + "-" + id);

        }
        config.adapterEndpoints = endpoints;
        return config;
    }

}