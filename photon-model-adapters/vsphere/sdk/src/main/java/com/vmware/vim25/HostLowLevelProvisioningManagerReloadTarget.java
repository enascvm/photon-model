
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostLowLevelProvisioningManagerReloadTarget.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="HostLowLevelProvisioningManagerReloadTarget"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="currentConfig"/&gt;
 *     &lt;enumeration value="snapshotConfig"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "HostLowLevelProvisioningManagerReloadTarget")
@XmlEnum
public enum HostLowLevelProvisioningManagerReloadTarget {

    @XmlEnumValue("currentConfig")
    CURRENT_CONFIG("currentConfig"),
    @XmlEnumValue("snapshotConfig")
    SNAPSHOT_CONFIG("snapshotConfig");
    private final String value;

    HostLowLevelProvisioningManagerReloadTarget(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static HostLowLevelProvisioningManagerReloadTarget fromValue(String v) {
        for (HostLowLevelProvisioningManagerReloadTarget c: HostLowLevelProvisioningManagerReloadTarget.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
