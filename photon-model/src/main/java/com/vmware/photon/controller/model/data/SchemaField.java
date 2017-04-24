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

import com.google.gson.annotations.SerializedName;

/**
 * Describe a data schema field
 */
public class SchemaField {
    /**
     * constant for the {@literal string} dataType
     * <p>
     * instance value is of type {@link String}
     * Reference: <a href="https://tools.ietf.org/html/rfc7159#section-7">RFC 7159 Strings</a>
     */
    public static final String DATATYPE_STRING = "string";
    /**
     * constant for the {@literal integer} dataType
     * <p>
     * instance value is of type {@link Integer}
     * Reference: <a href="https://tools.ietf.org/html/rfc7159#section-6">RFC 7159 Numbers</a>
     */
    public static final String DATATYPE_INTEGER = "integer";
    /**
     * constant for the {@literal decimal} dataType
     * <p>
     * instance value is of type {@link Double}
     * Reference: <a href="https://tools.ietf.org/html/rfc7159#section-6">RFC 7159 Numbers</a>
     */
    public static final String DATATYPE_DECIMAL = "decimal";
    /**
     * constant for the {@literal boolean} dataType
     * <p>
     * instance value is of type {@link Boolean}
     * Possible values are either {@literal true} or {@literal false}
     */
    public static final String DATATYPE_BOOLEAN = "boolean";
    /**
     * constant for the {@literal dateTime} dataType <p>Reference:
     * <a href="https://tools.ietf.org/html/rfc3339">Date and Time on the Internet:Timestamps</a>
     * <ul>Example:
     * <li>1985-04-12T23:20:50.52Z</li>
     * <li>1996-12-19T16:39:57-08:00</li>
     * </ul>
     */
    public static final String DATATYPE_DATETIME = "dateTime";

    /**
     * possible field types.
     */
    public enum Type {
        /**
         * This is the default one, specifies the field has single value of {@link #dataType}
         */
        @SerializedName("value")VALUE,
        /**
         * Specifies the field is a list of values of {@link #dataType}
         */
        @SerializedName("list")LIST,
        /**
         * Specified the field is map of entries which key is of type {@literal string} and
         * the value is of type specified in the {@link #dataType}
         * property
         */
        @SerializedName("map")MAP
    }

    public enum Constraint {
        /**
         * read only flag.
         * <p>
         * expect boolean value
         */
        readOnly,
        /**
         * mandatory flag
         * <p>
         * expect boolean value
         */
        mandatory,
        /**
         * list of permissible values
         * <p>
         * list values shall be of same type as for the {@link #dataType} property
         */
        permissibleValues
    }

    /**
     * A short, user-friendly name used for presenting this field to end-users.
     * <p/>
     * Optional.
     */
    public String label;

    /**
     * The description of this field.
     * <p/>
     * Optional.
     */
    public String description;

    /**
     * The type of data supported and expected by this field.
     * <p/>
     * <b>Optional</b>. If {@code dataType} not specified, the field is considered of data type
     * {@literal string}
     */
    public String dataType;

    /**
     * Describes the schema of complex data type for the field.
     * <p>
     * <b>Optional</b>. Value of the property is discarded If {@link #dataType} is specified
     */
    public Schema schema;
    /**
     * Optional property specifying the instance type of the current field.
     */
    public Type type;

    /**
     * Field constraints.
     */
    public Map<Constraint, Object> constraints;
}
