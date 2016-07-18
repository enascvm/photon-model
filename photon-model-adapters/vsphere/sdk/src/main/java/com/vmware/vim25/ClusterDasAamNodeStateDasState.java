
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ClusterDasAamNodeStateDasState.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="ClusterDasAamNodeStateDasState"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="uninitialized"/&gt;
 *     &lt;enumeration value="initialized"/&gt;
 *     &lt;enumeration value="configuring"/&gt;
 *     &lt;enumeration value="unconfiguring"/&gt;
 *     &lt;enumeration value="running"/&gt;
 *     &lt;enumeration value="error"/&gt;
 *     &lt;enumeration value="agentShutdown"/&gt;
 *     &lt;enumeration value="nodeFailed"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "ClusterDasAamNodeStateDasState")
@XmlEnum
public enum ClusterDasAamNodeStateDasState {

    @XmlEnumValue("uninitialized")
    UNINITIALIZED("uninitialized"),
    @XmlEnumValue("initialized")
    INITIALIZED("initialized"),
    @XmlEnumValue("configuring")
    CONFIGURING("configuring"),
    @XmlEnumValue("unconfiguring")
    UNCONFIGURING("unconfiguring"),
    @XmlEnumValue("running")
    RUNNING("running"),
    @XmlEnumValue("error")
    ERROR("error"),
    @XmlEnumValue("agentShutdown")
    AGENT_SHUTDOWN("agentShutdown"),
    @XmlEnumValue("nodeFailed")
    NODE_FAILED("nodeFailed");
    private final String value;

    ClusterDasAamNodeStateDasState(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ClusterDasAamNodeStateDasState fromValue(String v) {
        for (ClusterDasAamNodeStateDasState c: ClusterDasAamNodeStateDasState.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
