
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ClusterInfraUpdateHaConfigInfoBehaviorType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="ClusterInfraUpdateHaConfigInfoBehaviorType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="Manual"/&gt;
 *     &lt;enumeration value="Automated"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "ClusterInfraUpdateHaConfigInfoBehaviorType")
@XmlEnum
public enum ClusterInfraUpdateHaConfigInfoBehaviorType {

    @XmlEnumValue("Manual")
    MANUAL("Manual"),
    @XmlEnumValue("Automated")
    AUTOMATED("Automated");
    private final String value;

    ClusterInfraUpdateHaConfigInfoBehaviorType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ClusterInfraUpdateHaConfigInfoBehaviorType fromValue(String v) {
        for (ClusterInfraUpdateHaConfigInfoBehaviorType c: ClusterInfraUpdateHaConfigInfoBehaviorType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
