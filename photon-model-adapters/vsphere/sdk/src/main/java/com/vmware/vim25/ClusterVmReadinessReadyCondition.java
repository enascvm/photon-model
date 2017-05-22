
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ClusterVmReadinessReadyCondition.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="ClusterVmReadinessReadyCondition"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="none"/&gt;
 *     &lt;enumeration value="poweredOn"/&gt;
 *     &lt;enumeration value="guestHbStatusGreen"/&gt;
 *     &lt;enumeration value="appHbStatusGreen"/&gt;
 *     &lt;enumeration value="useClusterDefault"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "ClusterVmReadinessReadyCondition")
@XmlEnum
public enum ClusterVmReadinessReadyCondition {

    @XmlEnumValue("none")
    NONE("none"),
    @XmlEnumValue("poweredOn")
    POWERED_ON("poweredOn"),
    @XmlEnumValue("guestHbStatusGreen")
    GUEST_HB_STATUS_GREEN("guestHbStatusGreen"),
    @XmlEnumValue("appHbStatusGreen")
    APP_HB_STATUS_GREEN("appHbStatusGreen"),
    @XmlEnumValue("useClusterDefault")
    USE_CLUSTER_DEFAULT("useClusterDefault");
    private final String value;

    ClusterVmReadinessReadyCondition(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ClusterVmReadinessReadyCondition fromValue(String v) {
        for (ClusterVmReadinessReadyCondition c: ClusterVmReadinessReadyCondition.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
