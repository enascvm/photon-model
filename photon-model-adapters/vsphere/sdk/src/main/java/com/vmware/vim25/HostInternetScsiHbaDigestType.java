
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostInternetScsiHbaDigestType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="HostInternetScsiHbaDigestType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="digestProhibited"/&gt;
 *     &lt;enumeration value="digestDiscouraged"/&gt;
 *     &lt;enumeration value="digestPreferred"/&gt;
 *     &lt;enumeration value="digestRequired"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "HostInternetScsiHbaDigestType")
@XmlEnum
public enum HostInternetScsiHbaDigestType {

    @XmlEnumValue("digestProhibited")
    DIGEST_PROHIBITED("digestProhibited"),
    @XmlEnumValue("digestDiscouraged")
    DIGEST_DISCOURAGED("digestDiscouraged"),
    @XmlEnumValue("digestPreferred")
    DIGEST_PREFERRED("digestPreferred"),
    @XmlEnumValue("digestRequired")
    DIGEST_REQUIRED("digestRequired");
    private final String value;

    HostInternetScsiHbaDigestType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static HostInternetScsiHbaDigestType fromValue(String v) {
        for (HostInternetScsiHbaDigestType c: HostInternetScsiHbaDigestType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
