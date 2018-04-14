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

package com.vmware.photon.controller.discovery.csp;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

/**
 * A custom deserializer for the SetOrString class. It interrupts default deserialization by checking
 * to see if the response type is either a single String, or a Set of Strings, and returns an object
 * with a body of a Set of Strings.
 *
 * If the payload response is a Set of Strings, it is mirrored in the values, and if the payload is
 * a single String, then the values is a Set of Strings with that single value.
 */
public enum SetOrStringDeserializer implements JsonDeserializer<SetOrStringDeserializer.SetOrString> {

    INSTANCE;

    @Override
    public SetOrString deserialize(JsonElement setOrStringJson, Type T,
            JsonDeserializationContext context) throws JsonParseException {
        SetOrString setOrString = new SetOrString();
        setOrString.values = new HashSet<>();

        // If the json element is a single string, add it as a value to the values object and return.
        if (isJsonElementAString(setOrStringJson) && !setOrStringJson.getAsString().isEmpty()) {
            setOrString.values.add(setOrStringJson.getAsString());
            return setOrString;
        }

        // If the json element is an array of Strings, parse each and add to the values object
        // before returning.
        if (setOrStringJson.isJsonArray()) {
            setOrStringJson.getAsJsonArray().forEach(e -> {
                if (isJsonElementAString(e) && !e.getAsString().isEmpty()) {
                    setOrString.values.add(e.getAsString());
                }
            });
        }

        return setOrString;
    }

    private boolean isJsonElementAString(JsonElement element) {
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isString();
    }

    public class SetOrString {
        public Set<String> values;
    }
}
