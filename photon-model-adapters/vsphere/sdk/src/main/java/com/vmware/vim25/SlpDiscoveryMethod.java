
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for SlpDiscoveryMethod.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="SlpDiscoveryMethod"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="slpDhcp"/&gt;
 *     &lt;enumeration value="slpAutoUnicast"/&gt;
 *     &lt;enumeration value="slpAutoMulticast"/&gt;
 *     &lt;enumeration value="slpManual"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "SlpDiscoveryMethod")
@XmlEnum
public enum SlpDiscoveryMethod {

    @XmlEnumValue("slpDhcp")
    SLP_DHCP("slpDhcp"),
    @XmlEnumValue("slpAutoUnicast")
    SLP_AUTO_UNICAST("slpAutoUnicast"),
    @XmlEnumValue("slpAutoMulticast")
    SLP_AUTO_MULTICAST("slpAutoMulticast"),
    @XmlEnumValue("slpManual")
    SLP_MANUAL("slpManual");
    private final String value;

    SlpDiscoveryMethod(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static SlpDiscoveryMethod fromValue(String v) {
        for (SlpDiscoveryMethod c: SlpDiscoveryMethod.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
