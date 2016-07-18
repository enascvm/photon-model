
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ClusterVmComponentProtectionSettingsStorageVmReaction.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="ClusterVmComponentProtectionSettingsStorageVmReaction"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="disabled"/&gt;
 *     &lt;enumeration value="warning"/&gt;
 *     &lt;enumeration value="restartConservative"/&gt;
 *     &lt;enumeration value="restartAggressive"/&gt;
 *     &lt;enumeration value="clusterDefault"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "ClusterVmComponentProtectionSettingsStorageVmReaction")
@XmlEnum
public enum ClusterVmComponentProtectionSettingsStorageVmReaction {

    @XmlEnumValue("disabled")
    DISABLED("disabled"),
    @XmlEnumValue("warning")
    WARNING("warning"),
    @XmlEnumValue("restartConservative")
    RESTART_CONSERVATIVE("restartConservative"),
    @XmlEnumValue("restartAggressive")
    RESTART_AGGRESSIVE("restartAggressive"),
    @XmlEnumValue("clusterDefault")
    CLUSTER_DEFAULT("clusterDefault");
    private final String value;

    ClusterVmComponentProtectionSettingsStorageVmReaction(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ClusterVmComponentProtectionSettingsStorageVmReaction fromValue(String v) {
        for (ClusterVmComponentProtectionSettingsStorageVmReaction c: ClusterVmComponentProtectionSettingsStorageVmReaction.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
