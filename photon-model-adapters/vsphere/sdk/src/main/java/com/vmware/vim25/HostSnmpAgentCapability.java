
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostSnmpAgentCapability.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="HostSnmpAgentCapability"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="COMPLETE"/&gt;
 *     &lt;enumeration value="DIAGNOSTICS"/&gt;
 *     &lt;enumeration value="CONFIGURATION"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "HostSnmpAgentCapability")
@XmlEnum
public enum HostSnmpAgentCapability {

    COMPLETE,
    DIAGNOSTICS,
    CONFIGURATION;

    public String value() {
        return name();
    }

    public static HostSnmpAgentCapability fromValue(String v) {
        return valueOf(v);
    }

}
