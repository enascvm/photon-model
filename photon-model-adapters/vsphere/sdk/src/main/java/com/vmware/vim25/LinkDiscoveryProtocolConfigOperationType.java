
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for LinkDiscoveryProtocolConfigOperationType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="LinkDiscoveryProtocolConfigOperationType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="none"/&gt;
 *     &lt;enumeration value="listen"/&gt;
 *     &lt;enumeration value="advertise"/&gt;
 *     &lt;enumeration value="both"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "LinkDiscoveryProtocolConfigOperationType")
@XmlEnum
public enum LinkDiscoveryProtocolConfigOperationType {

    @XmlEnumValue("none")
    NONE("none"),
    @XmlEnumValue("listen")
    LISTEN("listen"),
    @XmlEnumValue("advertise")
    ADVERTISE("advertise"),
    @XmlEnumValue("both")
    BOTH("both");
    private final String value;

    LinkDiscoveryProtocolConfigOperationType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static LinkDiscoveryProtocolConfigOperationType fromValue(String v) {
        for (LinkDiscoveryProtocolConfigOperationType c: LinkDiscoveryProtocolConfigOperationType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
