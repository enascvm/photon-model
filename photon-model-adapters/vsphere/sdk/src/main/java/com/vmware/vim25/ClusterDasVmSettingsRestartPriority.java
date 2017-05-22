
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ClusterDasVmSettingsRestartPriority.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="ClusterDasVmSettingsRestartPriority"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="disabled"/&gt;
 *     &lt;enumeration value="lowest"/&gt;
 *     &lt;enumeration value="low"/&gt;
 *     &lt;enumeration value="medium"/&gt;
 *     &lt;enumeration value="high"/&gt;
 *     &lt;enumeration value="highest"/&gt;
 *     &lt;enumeration value="clusterRestartPriority"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "ClusterDasVmSettingsRestartPriority")
@XmlEnum
public enum ClusterDasVmSettingsRestartPriority {

    @XmlEnumValue("disabled")
    DISABLED("disabled"),
    @XmlEnumValue("lowest")
    LOWEST("lowest"),
    @XmlEnumValue("low")
    LOW("low"),
    @XmlEnumValue("medium")
    MEDIUM("medium"),
    @XmlEnumValue("high")
    HIGH("high"),
    @XmlEnumValue("highest")
    HIGHEST("highest"),
    @XmlEnumValue("clusterRestartPriority")
    CLUSTER_RESTART_PRIORITY("clusterRestartPriority");
    private final String value;

    ClusterDasVmSettingsRestartPriority(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ClusterDasVmSettingsRestartPriority fromValue(String v) {
        for (ClusterDasVmSettingsRestartPriority c: ClusterDasVmSettingsRestartPriority.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
