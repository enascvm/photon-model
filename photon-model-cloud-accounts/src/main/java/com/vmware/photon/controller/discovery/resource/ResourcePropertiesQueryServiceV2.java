/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.discovery.resource;

import static com.vmware.photon.controller.model.UriPaths.RESOURCE_PROPERTIES_SERVICE_V2;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.vmware.photon.controller.discovery.common.ResourceProperties;
import com.vmware.photon.controller.discovery.common.ResourceProperty;
import com.vmware.photon.controller.discovery.resource.ResourcesApiService.ResourceViewState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.RequestRouter.Route;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.TypeName;
import com.vmware.xenon.common.StatelessService;

/**
 * Query service to get the properties in resources that can be used for filtering.
 * Performs introspection on all the entities that are returned as part of the Resource
 * List; finds the common set of properties across the board and returns them.
 * Does some additional processing in terms of skipping static fields and fields some heirarchical
 * classes like ServiceDocument.class.
 */
public class ResourcePropertiesQueryServiceV2 extends StatelessService {
    public static final String SELF_LINK = RESOURCE_PROPERTIES_SERVICE_V2;
    private static final String DOT = ".";
    private static final String STRING_TYPE = "String";
    private static final String LONG_TYPE = "long";
    private static final String MAP_TYPE = "Map";
    static final Class<?>[] resourceClasses = { ResourceViewState.class };
    static final List<Class<?>> classSkipList = new ArrayList<>(
            Arrays.asList(ServiceDocument.class));
    // The set of strings on which filtering is supported in the Resource List API
    static final Set<String> filterList = new HashSet<>();
    static final Set<String> sortList = new HashSet<>(
            Arrays.asList(ResourceViewState.FIELD_NAME_NAME,
                    ResourceViewState.FIELD_NAME_LAST_UPDATED_TIME));
    // Map that maintains a translation key from standard class names to a user-friendly
    // representation
    private static final Map<String, String> typeMap;

    static {
        Map<String, String> map = new HashMap<String, String>();
        map.put(STRING_TYPE, TypeName.STRING.name());
        map.put(LONG_TYPE, TypeName.LONG.name());
        map.put(MAP_TYPE, TypeName.MAP.name());
        typeMap = Collections.unmodifiableMap(map);
        filterList.addAll(ResourcesQueryTaskServiceV4.SUPPORTED_FILTER_MAP.keySet());
        filterList.addAll(ResourcesQueryTaskServiceV4.SUPPORTED_ENDPOINT_FIELDS_FILTER_MAP.keySet());
        filterList.addAll(ResourcesQueryTaskServiceV4.SUPPORTED_TAGS_FIELDS_FILTER_MAP.keySet());
        filterList.addAll(ResourcesQueryTaskServiceV4.SUPPORTED_TYPE_FILTER_MAP.keySet());
    }

    private static ResourceProperties cachedResourceProperties = null;

    /**
     * Static block for one time initialization of the properties that can be used for filtering.
     */
    static {
        List<ResourceProperties> resourcePropertiesListByClass = new ArrayList<>();
        for (Class<?> c : resourceClasses) {
            ResourceProperties resourcePropertiesForClass = new ResourceProperties();
            parsePropertiesInClass(c, resourcePropertiesForClass, null);
            resourcePropertiesListByClass.add(resourcePropertiesForClass);
        }
        findCommonPropertiesAcrossClasses(resourcePropertiesListByClass);
    }

    @Override
    public void handleGet(Operation op) {
        op.setBody(cachedResourceProperties);
        op.complete();
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument d = super.getDocumentTemplate();
        d.documentDescription.name = "Resource Properties";
        d.documentDescription.description = "Query for list of properties a user can apply on resource listing API";
        d.documentDescription.serviceRequestRoutes = new HashMap<>();
        Route route = new Route();
        route.action = Action.GET;
        route.description = "Get properties for resources";
        route.responseType = ResourceProperty.class;
        d.documentDescription.serviceRequestRoutes
                .put(route.action, Collections.singletonList(route));
        return d;
    }

    /**
     * Selects the common properties from the sets of properties passed into the method.
     */
    public static List<ResourceProperty> findCommonPropertiesAcrossClasses(
            List<ResourceProperties> resourcePropertiesListByClass) {
        cachedResourceProperties = new ResourceProperties();
        List<ResourceProperty> commonList = resourcePropertiesListByClass
                .get(0).results;
        for (int i = 0; i < resourcePropertiesListByClass.size() - 1; i++) {
            commonList.retainAll(resourcePropertiesListByClass.get(i + 1).results);
        }

        Collections.sort(commonList, new ResourceProperty.NameComparator());
        cachedResourceProperties.results = commonList;
        cachedResourceProperties.documentCount = commonList.size();
        return commonList;
    }

    /**
     * Accepts a given class and looks through all the properties that exist in it.
     * It does not pick up the static fields or fields that belong to a skipped class
     * that does not require to be surfaced up.
     *
     * @param classType The class for which the properties are to be returned.
     * @param resourceProperties The list of properties to be created.
     */
    private static void parsePropertiesInClass(Class<?> classType,
            ResourceProperties resourceProperties, String propertyPrefix) {
        // Return all the non-static fields in the class
        for (Field field : classType.getFields()) {
            int modifiers = field.getModifiers();
            // Skipping static fields and any hierarchical classes that should be excluded from the
            // filtering support.
            if (!Modifier.isStatic(modifiers)
                    && !classSkipList.contains(field.getDeclaringClass())) {
                ResourceProperty resourceProperty = new ResourceProperty(
                        derivePropertyName(propertyPrefix, field.getName()),
                        mapTypes(field.getType().getSimpleName()));
                if (field.getType().isEnum()) {
                    Object[] values = field.getType().getEnumConstants();
                    for (Object o : values) {
                        resourceProperty.values.add(o.toString());
                    }
                }
                // Expand any nested objects that are part of the PODO. Right now the check is to
                // include ServiceDocument type classes. Can be changed to be more inclusive of
                // other types as the data model evolves.
                if (ServiceDocument.class.isAssignableFrom(field.getType())) {
                    Type elementType = field.getType();
                    parsePropertiesInClass((Class<?>) elementType, resourceProperties,
                            field.getName());
                    continue;

                }
                if (filterList.contains(resourceProperty.name)) {
                    resourceProperty.isFilterable = true;
                }
                if (sortList.contains(resourceProperty.name)) {
                    resourceProperty.isSortable = true;
                }
                resourceProperties.results.add(resourceProperty);
            }
        }
    }

    /**
     * Prepends the name of the property with the prefix provided. Used for scoping the name of
     * the properties with the name of the class when navigating relationships.
     */
    private static String derivePropertyName(String propertyPrefix, String name) {
        return (propertyPrefix != null ? propertyPrefix + DOT + name
                : name);
    }

    /**
     * Looks up the alternate representation for a given type. This is to enable user friendly
     * representations of standard Java types. For e.g the String data type could be represented
     * as "STRING" to enable the end user to use this property type in the defined property type
     * for the sorting filter.
     */
    private static String mapTypes(String simpleName) {
        if (!typeMap.containsKey(simpleName)) {
            return simpleName;
        } else {
            return typeMap.get(simpleName);
        }
    }
}
