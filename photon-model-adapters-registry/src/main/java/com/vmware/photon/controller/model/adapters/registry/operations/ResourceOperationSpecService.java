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

package com.vmware.photon.controller.model.adapters.registry.operations;

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.OPTIONAL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.net.URI;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.constants.ReleaseConstants;
import com.vmware.photon.controller.model.data.Schema;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;

/**
 * Represents an resource operation specification.
 */
public class ResourceOperationSpecService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.CONFIG + "/resource-operation";

    public static final String ADAPTER_PATH_STEP_OPERATION = "operation";

    /**
     * possible resource types
     */
    public enum ResourceType {
        COMPUTE, NETWORK, STORAGE
    }

    /**
     * This class represents the document state associated with a
     * {@link ResourceOperationSpecService}.
     */
    public static class ResourceOperationSpec extends ServiceDocument {
        public static final String FIELD_NAME_OPERATION = "operation";
        public static final String FIELD_NAME_ENDPOINT_TYPE = "endpointType";
        public static final String FIELD_NAME_RESOURCE_TYPE = "resourceType";
        public static final String FIELD_NAME_NAME = "name";
        public static final String FIELD_NAME_DESCRIPTION = "description";
        public static final String FIELD_NAME_ADAPTER_REFERENCE = "adapterReference";
        public static final String FIELD_NAME_TARGET_CRITERIA = "targetCriteria";
        public static final String FIELD_NAME_SCHEMA = "schema";

        @Documentation(description = "The operation technical name.",
                exampleString = "powerOff, powerOn, snapshot, reconfigure, etc.")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, REQUIRED },
                indexing = { PropertyIndexingOption.FIXED_ITEM_NAME, PropertyIndexingOption.SORT })
        public String operation;

        @Documentation(description = "Endpoint type for which the resource operation is defined",
                exampleString = "azure, aws, gcp, vsphere, openstack, virtustream, etc.")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, REQUIRED },
                indexing = { PropertyIndexingOption.FIXED_ITEM_NAME, PropertyIndexingOption.SORT })
        public String endpointType;

        @Documentation(description = "Resource type for which the resource operation is applicable")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, REQUIRED },
                indexing = PropertyIndexingOption.FIXED_ITEM_NAME)
        public ResourceType resourceType;

        /**
         * Name of the photon model adapter instance
         */
        @Documentation(description = "Name of the photon model adapter configuration.",
                exampleString = "Openstack, Virtustream, etc.")
        @PropertyOptions(usage = AUTO_MERGE_IF_NOT_NULL,
                indexing = { PropertyIndexingOption.CASE_INSENSITIVE, PropertyIndexingOption.SORT })
        public String name;

        /**
         * User-friendly description of the resource operation.
         */
        @PropertyOptions(usage = AUTO_MERGE_IF_NOT_NULL,
                indexing = PropertyIndexingOption.CASE_INSENSITIVE)
        public String description;

        /**
         * URI reference to the adapter used to power-on this host.
         */
        @UsageOption(option = AUTO_MERGE_IF_NOT_NULL)
        public URI adapterReference;

        /**
         * Target criteria, as JavaScript source, for this resource operation.
         * <p>
         * Callers shall evaluate the targetCriteria in the context of the resource for which the
         * operation is activated, e.g. for Compute this shall be the {@link
         * com.vmware.photon.controller.model.resources.ComputeService.ComputeState}
         * <p>
         * example: {@code ResourceOperationUtils.SCRIPT_CONTEXT_RESOURCE +
         * ".hostName.startsWith('myPrefix') && "
         * + ResourceOperationUtils.SCRIPT_CONTEXT_RESOURCE + ".cpuCount==4"}
         */
        @PropertyOptions(usage = AUTO_MERGE_IF_NOT_NULL,
                indexing = PropertyIndexingOption.STORE_ONLY)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_14)
        public String targetCriteria;

        @Documentation(
                description = "Optional schema describing the expected by the resource operation payload")
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL, OPTIONAL },
                indexing = PropertyIndexingOption.EXPAND)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_18)
        public Schema schema;

        @Override
        public String toString() {
            return String.format("%s["
                            + "operation=%s, endpointType=%s, resourceType=%s, "
                            + "adapterReference=%s, "
                            + "name=%s, description=%s, "
                            + "documentSelfLink=%s, "
                            + "targetCriteria=%s,"
                            + "schema=%s]",
                    getClass().getSimpleName(),
                    this.operation, this.endpointType, this.resourceType,
                    this.adapterReference,
                    this.name, this.description,
                    this.documentSelfLink,
                    this.targetCriteria,
                    this.schema);
        }
    }

    public ResourceOperationSpecService() {
        super(ResourceOperationSpec.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        ServiceUtils.setRetentionLimit(template);
        return template;
    }

    public static String buildDefaultAdapterLink(String endpointType,
            ResourceType resourceType,
            String operation) {
        AssertUtil.assertNotEmpty(endpointType, "'endpointType' must be set.");
        AssertUtil.assertNotNull(resourceType, "'resourceType' must be set.");
        AssertUtil.assertNotEmpty(operation, "'operation' must be set.");
        return UriUtils.buildUriPath(
                UriPaths.PROVISIONING,
                endpointType,
                ADAPTER_PATH_STEP_OPERATION,
                resourceType.toString().toLowerCase(),
                operation.toLowerCase() + "-adapter");
    }

}