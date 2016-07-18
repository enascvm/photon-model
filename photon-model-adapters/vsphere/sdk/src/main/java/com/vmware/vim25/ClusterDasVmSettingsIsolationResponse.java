
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ClusterDasVmSettingsIsolationResponse.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="ClusterDasVmSettingsIsolationResponse"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="none"/&gt;
 *     &lt;enumeration value="powerOff"/&gt;
 *     &lt;enumeration value="shutdown"/&gt;
 *     &lt;enumeration value="clusterIsolationResponse"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "ClusterDasVmSettingsIsolationResponse")
@XmlEnum
public enum ClusterDasVmSettingsIsolationResponse {

    @XmlEnumValue("none")
    NONE("none"),
    @XmlEnumValue("powerOff")
    POWER_OFF("powerOff"),
    @XmlEnumValue("shutdown")
    SHUTDOWN("shutdown"),
    @XmlEnumValue("clusterIsolationResponse")
    CLUSTER_ISOLATION_RESPONSE("clusterIsolationResponse");
    private final String value;

    ClusterDasVmSettingsIsolationResponse(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ClusterDasVmSettingsIsolationResponse fromValue(String v) {
        for (ClusterDasVmSettingsIsolationResponse c: ClusterDasVmSettingsIsolationResponse.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
