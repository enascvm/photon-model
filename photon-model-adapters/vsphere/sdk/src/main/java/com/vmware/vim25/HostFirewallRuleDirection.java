
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostFirewallRuleDirection.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="HostFirewallRuleDirection"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="inbound"/&gt;
 *     &lt;enumeration value="outbound"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "HostFirewallRuleDirection")
@XmlEnum
public enum HostFirewallRuleDirection {

    @XmlEnumValue("inbound")
    INBOUND("inbound"),
    @XmlEnumValue("outbound")
    OUTBOUND("outbound");
    private final String value;

    HostFirewallRuleDirection(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static HostFirewallRuleDirection fromValue(String v) {
        for (HostFirewallRuleDirection c: HostFirewallRuleDirection.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
