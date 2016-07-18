
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualMachineFlagInfoMonitorType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VirtualMachineFlagInfoMonitorType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="release"/&gt;
 *     &lt;enumeration value="debug"/&gt;
 *     &lt;enumeration value="stats"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VirtualMachineFlagInfoMonitorType")
@XmlEnum
public enum VirtualMachineFlagInfoMonitorType {

    @XmlEnumValue("release")
    RELEASE("release"),
    @XmlEnumValue("debug")
    DEBUG("debug"),
    @XmlEnumValue("stats")
    STATS("stats");
    private final String value;

    VirtualMachineFlagInfoMonitorType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VirtualMachineFlagInfoMonitorType fromValue(String v) {
        for (VirtualMachineFlagInfoMonitorType c: VirtualMachineFlagInfoMonitorType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
