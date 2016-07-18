
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostCpuPowerManagementInfoPolicyType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="HostCpuPowerManagementInfoPolicyType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="off"/&gt;
 *     &lt;enumeration value="staticPolicy"/&gt;
 *     &lt;enumeration value="dynamicPolicy"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "HostCpuPowerManagementInfoPolicyType")
@XmlEnum
public enum HostCpuPowerManagementInfoPolicyType {

    @XmlEnumValue("off")
    OFF("off"),
    @XmlEnumValue("staticPolicy")
    STATIC_POLICY("staticPolicy"),
    @XmlEnumValue("dynamicPolicy")
    DYNAMIC_POLICY("dynamicPolicy");
    private final String value;

    HostCpuPowerManagementInfoPolicyType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static HostCpuPowerManagementInfoPolicyType fromValue(String v) {
        for (HostCpuPowerManagementInfoPolicyType c: HostCpuPowerManagementInfoPolicyType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
