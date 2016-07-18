
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for LinkDiscoveryProtocolConfigProtocolType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="LinkDiscoveryProtocolConfigProtocolType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="cdp"/&gt;
 *     &lt;enumeration value="lldp"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "LinkDiscoveryProtocolConfigProtocolType")
@XmlEnum
public enum LinkDiscoveryProtocolConfigProtocolType {

    @XmlEnumValue("cdp")
    CDP("cdp"),
    @XmlEnumValue("lldp")
    LLDP("lldp");
    private final String value;

    LinkDiscoveryProtocolConfigProtocolType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static LinkDiscoveryProtocolConfigProtocolType fromValue(String v) {
        for (LinkDiscoveryProtocolConfigProtocolType c: LinkDiscoveryProtocolConfigProtocolType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
