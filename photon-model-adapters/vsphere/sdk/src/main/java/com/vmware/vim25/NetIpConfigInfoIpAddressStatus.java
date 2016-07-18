
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for NetIpConfigInfoIpAddressStatus.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="NetIpConfigInfoIpAddressStatus"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="preferred"/&gt;
 *     &lt;enumeration value="deprecated"/&gt;
 *     &lt;enumeration value="invalid"/&gt;
 *     &lt;enumeration value="inaccessible"/&gt;
 *     &lt;enumeration value="unknown"/&gt;
 *     &lt;enumeration value="tentative"/&gt;
 *     &lt;enumeration value="duplicate"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "NetIpConfigInfoIpAddressStatus")
@XmlEnum
public enum NetIpConfigInfoIpAddressStatus {

    @XmlEnumValue("preferred")
    PREFERRED("preferred"),
    @XmlEnumValue("deprecated")
    DEPRECATED("deprecated"),
    @XmlEnumValue("invalid")
    INVALID("invalid"),
    @XmlEnumValue("inaccessible")
    INACCESSIBLE("inaccessible"),
    @XmlEnumValue("unknown")
    UNKNOWN("unknown"),
    @XmlEnumValue("tentative")
    TENTATIVE("tentative"),
    @XmlEnumValue("duplicate")
    DUPLICATE("duplicate");
    private final String value;

    NetIpConfigInfoIpAddressStatus(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static NetIpConfigInfoIpAddressStatus fromValue(String v) {
        for (NetIpConfigInfoIpAddressStatus c: NetIpConfigInfoIpAddressStatus.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
