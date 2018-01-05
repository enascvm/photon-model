/*
 * Copyright (c) 2018-2019 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.adapters.awsadapter.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSStorageType;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSStorageType.EBS;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSStorageType.INSTANCE_STORE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSSupportedOS;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSSupportedOS.LINUX;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSSupportedOS.WINDOWS;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSSupportedVirtualizationTypes;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSSupportedVirtualizationTypes.DEFAULT;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSSupportedVirtualizationTypes.HVM;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSSupportedVirtualizationTypes.PARAVIRTUAL;

/**
 * Utility class that generates a mapping of block device names as specified by AWS.
 *
 * @see <a href="http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/device_naming.html">Device Naming on Linux Instances</a>
 * @see <a href="http://docs.aws.amazon.com/AWSEC2/latest/WindowsGuide/device_naming.html">Device Naming on Windows Instances</a>
 */
public class AWSBlockDeviceNameMapper {
    private static final String DEFAULT_INSTANCE_TYPE = "_default";

    private static final Pattern regexPattern = Pattern.compile("(?<=\\[).+?(?=\\])");

    private static final Map<AWSSupportedOS,
            Map<AWSSupportedVirtualizationTypes,
                    Map<AWSStorageType,
                            Map<String, List<String>>>>> OS_STORAGE_TYPE_MAPPINGS = new HashMap<>();

    private static final Map<String, List<String>> EXPANDED_NAMES = new HashMap<>();

    static {
        // Linux mappings
        addMapping(LINUX,
                PARAVIRTUAL,
                EBS,
                DEFAULT_INSTANCE_TYPE,
                Arrays.asList(new String[]{"/dev/sd[f-p]",
                        "/dev/sd[f-p][1-6]"}));

        addMapping(LINUX,
                PARAVIRTUAL,
                INSTANCE_STORE,
                DEFAULT_INSTANCE_TYPE,
                Arrays.asList(new String[]{"/dev/sd[b-e]"}));

        addMapping(LINUX,
                PARAVIRTUAL,
                INSTANCE_STORE,
                "hs1.8xlarge",
                Arrays.asList(new String[]{"/dev/sd[b-y]"}));

        addMapping(LINUX,
                HVM,
                EBS,
                DEFAULT_INSTANCE_TYPE,
                Arrays.asList(new String[]{"/dev/sd[f-p]"}));

        addMapping(LINUX,
                HVM,
                INSTANCE_STORE,
                DEFAULT_INSTANCE_TYPE,
                Arrays.asList(new String[]{"/dev/sd[b-e]"}));

        addMapping(LINUX,
                HVM,
                EBS,
                "hs1.8xlarge",
                Arrays.asList(new String[]{"/dev/sd[b-y]"}));

        addMapping(LINUX,
                HVM,
                EBS,
                "d2.8xlarge",
                Arrays.asList(new String[]{"/dev/sd[b-y]"}));

        addMapping(LINUX,
                HVM,
                EBS,
                "i2.8xlarge",
                Arrays.asList(new String[]{"/dev/sd[b-i]"}));

        // Windows mappings
        addMapping(WINDOWS,
                DEFAULT,
                EBS,
                DEFAULT_INSTANCE_TYPE,
                Arrays.asList(new String[]{"xvd[f-p]"}));

        addMapping(WINDOWS,
                DEFAULT,
                INSTANCE_STORE,
                DEFAULT_INSTANCE_TYPE,
                Arrays.asList(new String[]{"xvd[a-e]"}));

        addMapping(WINDOWS,
                DEFAULT,
                EBS,
                "hs1.8xlarge",
                Arrays.asList(new String[]{"xvdc[a-x]"}));
    }

    public static void addMapping(AWSSupportedOS operatingSystem,
                                  AWSSupportedVirtualizationTypes virtualizationType,
                                  AWSStorageType storageType,
                                  String instanceType,
                                  List<String> names) {

        instanceType = Optional.ofNullable(instanceType).orElse(DEFAULT_INSTANCE_TYPE);

        Map<AWSSupportedVirtualizationTypes,
                Map<AWSStorageType,
                        Map<String, List<String>>>> virtualizationMappingsByOS = OS_STORAGE_TYPE_MAPPINGS.getOrDefault(operatingSystem, new HashMap<>());

        // add new OS level mapping if absent
        OS_STORAGE_TYPE_MAPPINGS.putIfAbsent(operatingSystem, virtualizationMappingsByOS);

        Map<AWSStorageType,
                Map<String, List<String>>> storageTypeMappingsByVirtualization = virtualizationMappingsByOS.getOrDefault(virtualizationType, new HashMap<>());
        // add new virtualization mapping (Paravirtual/ HVM) if absent
        virtualizationMappingsByOS.putIfAbsent(virtualizationType, storageTypeMappingsByVirtualization);

        Map<String, List<String>> instanceMappingsByVolumeType =
                storageTypeMappingsByVirtualization.getOrDefault(storageType, new HashMap<>());

        // add new storage mapping (EBS/ Instance Store) if absent
        storageTypeMappingsByVirtualization.putIfAbsent(storageType, instanceMappingsByVolumeType);

        // add new name mapping
        instanceMappingsByVolumeType.putIfAbsent(instanceType, names);

        // cached entries of expanded names for character classes
        names.stream().forEach(name -> {
            EXPANDED_NAMES.computeIfAbsent(name, AWSBlockDeviceNameMapper::expand);
        });

    }

    /**
     * Expands the range : xvd[a-z] -> [xvda, xvdb, xvdc..... xvdz]
     */
    private static List<String> expand(String range) {
        String prefix = range.substring(0, range.indexOf("["));
        String suffix = range.substring(range.lastIndexOf("]") + 1);
        List<String> results = Arrays.asList(new String[]{prefix});
        Matcher m = regexPattern.matcher(range);
        while (m.find()) {
            results = cartesian(expandCharacterClass(m.group()), results);
        }
        return Stream.of(results)
                .flatMap(Collection::parallelStream)
                .map(x -> x + suffix)
                .collect(Collectors.toList());
    }

    /**
     * Expands the regex char class [a-b] into a list [a,b,c..z]
     * Expands the regex char class [0-9] into a list [0,1,2..9]
     */
    private static List<String> expandCharacterClass(String str) {
        List<String> wildcards = new ArrayList<>();
        String[] boundaries = str.split("-");
        int s = (int) boundaries[0].charAt(0);
        int e = (int) boundaries[1].charAt(0);
        int total = e - s;
        for (int i = 0; i <= total; i++) {
            char c = (char) (s + i);
            wildcards.add(String.valueOf(c));
        }
        return wildcards;
    }

    /**
     * listA: [a, b, c]
     * listB: [x, y]
     * outputs: [ax, ay, bx, by, cx, cy]
     */
    private static List<String> cartesian(List<String> listA, final List<String> listB) {
        List<String> output = new ArrayList<>();
        listA.stream().forEach(w -> {
            listB.stream().forEach(r -> {
                output.add(r + w);
            });
        });
        return output;
    }

    /**
     * Returns a list of available names
     */
    public static List<String> getAvailableNames(AWSSupportedOS operatingSystem,
                                                 AWSSupportedVirtualizationTypes virtualizationType,
                                                 AWSStorageType storageType,
                                                 String instanceType) {
        /**
         *  @see com.amazonaws.services.ec2.model.PlatformValues
         *  AWS provides the platform value only if its Windows and null otherwise, hence set it to LINUX if its null
         */
        if (operatingSystem == null) {
            operatingSystem = LINUX;
        }

        /**
         *  Presently theres no provision to figure out the Driver PV for Windows Platform
         *  So if OS = WINDOWS , then set the virtualization to DEFAULT as all mappings are stored for DEFAULT key.
         */
        if (operatingSystem == WINDOWS) {
            virtualizationType = DEFAULT;
        }

        List<String> availableNamesPattern = OS_STORAGE_TYPE_MAPPINGS.getOrDefault(operatingSystem, Collections.emptyMap())
                .getOrDefault(virtualizationType, Collections.emptyMap())
                .getOrDefault(storageType, Collections.emptyMap())
                .getOrDefault(instanceType, Collections.emptyList());

        if (availableNamesPattern.isEmpty()) {
            // no mapping present for the given instanceType hence get the default list.
            availableNamesPattern = OS_STORAGE_TYPE_MAPPINGS.getOrDefault(operatingSystem, Collections.emptyMap())
                    .getOrDefault(virtualizationType, Collections.emptyMap())
                    .getOrDefault(storageType, Collections.emptyMap())
                    .getOrDefault(DEFAULT_INSTANCE_TYPE, Collections.emptyList());
        }

        List<String> availableNames = new ArrayList<>();
        availableNamesPattern.forEach(pattern -> {
            availableNames.addAll(EXPANDED_NAMES.get(pattern)
                    .stream()
                    .collect(Collectors.toList()));
        });

        return availableNames;
    }

    /**
     * Returns a list of available names which isn't present in the namesToExclude
     */
    public static List<String> getAvailableNames(AWSSupportedOS operatingSystem,
                                                 AWSSupportedVirtualizationTypes virtualizationType,
                                                 AWSStorageType storageType,
                                                 String instanceType,
                                                 final List<String> namesToExclude) {
        List<String> availableNames = getAvailableNames(operatingSystem, virtualizationType, storageType, instanceType);

        availableNames.removeAll(namesToExclude);

        if (availableNames.isEmpty()) {
            throw new RuntimeException("All recommended names are used up");
        }
        return availableNames;
    }
}
