
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ClusterHostInfraUpdateHaModeActionOperationType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="ClusterHostInfraUpdateHaModeActionOperationType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="enterQuarantine"/&gt;
 *     &lt;enumeration value="exitQuarantine"/&gt;
 *     &lt;enumeration value="enterMaintenance"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "ClusterHostInfraUpdateHaModeActionOperationType")
@XmlEnum
public enum ClusterHostInfraUpdateHaModeActionOperationType {

    @XmlEnumValue("enterQuarantine")
    ENTER_QUARANTINE("enterQuarantine"),
    @XmlEnumValue("exitQuarantine")
    EXIT_QUARANTINE("exitQuarantine"),
    @XmlEnumValue("enterMaintenance")
    ENTER_MAINTENANCE("enterMaintenance");
    private final String value;

    ClusterHostInfraUpdateHaModeActionOperationType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ClusterHostInfraUpdateHaModeActionOperationType fromValue(String v) {
        for (ClusterHostInfraUpdateHaModeActionOperationType c: ClusterHostInfraUpdateHaModeActionOperationType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
