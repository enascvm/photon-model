
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostNasVolumeSecurityType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="HostNasVolumeSecurityType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="AUTH_SYS"/&gt;
 *     &lt;enumeration value="SEC_KRB5"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "HostNasVolumeSecurityType")
@XmlEnum
public enum HostNasVolumeSecurityType {

    AUTH_SYS("AUTH_SYS"),
    @XmlEnumValue("SEC_KRB5")
    SEC_KRB_5("SEC_KRB5");
    private final String value;

    HostNasVolumeSecurityType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static HostNasVolumeSecurityType fromValue(String v) {
        for (HostNasVolumeSecurityType c: HostNasVolumeSecurityType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
