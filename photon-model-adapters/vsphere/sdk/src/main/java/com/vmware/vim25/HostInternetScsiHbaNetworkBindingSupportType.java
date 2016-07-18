
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostInternetScsiHbaNetworkBindingSupportType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="HostInternetScsiHbaNetworkBindingSupportType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="notsupported"/&gt;
 *     &lt;enumeration value="optional"/&gt;
 *     &lt;enumeration value="required"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "HostInternetScsiHbaNetworkBindingSupportType")
@XmlEnum
public enum HostInternetScsiHbaNetworkBindingSupportType {

    @XmlEnumValue("notsupported")
    NOTSUPPORTED("notsupported"),
    @XmlEnumValue("optional")
    OPTIONAL("optional"),
    @XmlEnumValue("required")
    REQUIRED("required");
    private final String value;

    HostInternetScsiHbaNetworkBindingSupportType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static HostInternetScsiHbaNetworkBindingSupportType fromValue(String v) {
        for (HostInternetScsiHbaNetworkBindingSupportType c: HostInternetScsiHbaNetworkBindingSupportType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
