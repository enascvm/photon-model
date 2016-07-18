
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostInternetScsiHbaChapAuthenticationType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="HostInternetScsiHbaChapAuthenticationType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="chapProhibited"/&gt;
 *     &lt;enumeration value="chapDiscouraged"/&gt;
 *     &lt;enumeration value="chapPreferred"/&gt;
 *     &lt;enumeration value="chapRequired"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "HostInternetScsiHbaChapAuthenticationType")
@XmlEnum
public enum HostInternetScsiHbaChapAuthenticationType {

    @XmlEnumValue("chapProhibited")
    CHAP_PROHIBITED("chapProhibited"),
    @XmlEnumValue("chapDiscouraged")
    CHAP_DISCOURAGED("chapDiscouraged"),
    @XmlEnumValue("chapPreferred")
    CHAP_PREFERRED("chapPreferred"),
    @XmlEnumValue("chapRequired")
    CHAP_REQUIRED("chapRequired");
    private final String value;

    HostInternetScsiHbaChapAuthenticationType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static HostInternetScsiHbaChapAuthenticationType fromValue(String v) {
        for (HostInternetScsiHbaChapAuthenticationType c: HostInternetScsiHbaChapAuthenticationType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
