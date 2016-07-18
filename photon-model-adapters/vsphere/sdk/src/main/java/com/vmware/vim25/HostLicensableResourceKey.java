
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostLicensableResourceKey.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="HostLicensableResourceKey"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="numCpuPackages"/&gt;
 *     &lt;enumeration value="numCpuCores"/&gt;
 *     &lt;enumeration value="memorySize"/&gt;
 *     &lt;enumeration value="memoryForVms"/&gt;
 *     &lt;enumeration value="numVmsStarted"/&gt;
 *     &lt;enumeration value="numVmsStarting"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "HostLicensableResourceKey")
@XmlEnum
public enum HostLicensableResourceKey {

    @XmlEnumValue("numCpuPackages")
    NUM_CPU_PACKAGES("numCpuPackages"),
    @XmlEnumValue("numCpuCores")
    NUM_CPU_CORES("numCpuCores"),
    @XmlEnumValue("memorySize")
    MEMORY_SIZE("memorySize"),
    @XmlEnumValue("memoryForVms")
    MEMORY_FOR_VMS("memoryForVms"),
    @XmlEnumValue("numVmsStarted")
    NUM_VMS_STARTED("numVmsStarted"),
    @XmlEnumValue("numVmsStarting")
    NUM_VMS_STARTING("numVmsStarting");
    private final String value;

    HostLicensableResourceKey(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static HostLicensableResourceKey fromValue(String v) {
        for (HostLicensableResourceKey c: HostLicensableResourceKey.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
