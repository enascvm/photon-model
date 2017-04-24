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

package com.vmware.photon.controller.model.data;

import java.util.Map;

/**
 * Defines a data schema with name, description and map of field definitions
 */
public class Schema {

    /**
     * short schema name
     */
    public String name;

    /**
     * schema description
     */
    public String description;

    /**
     * schema fields
     */
    public Map<String, SchemaField> fields;
}
