
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VMwareUplinkLacpMode.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VMwareUplinkLacpMode"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="active"/&gt;
 *     &lt;enumeration value="passive"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VMwareUplinkLacpMode")
@XmlEnum
public enum VMwareUplinkLacpMode {

    @XmlEnumValue("active")
    ACTIVE("active"),
    @XmlEnumValue("passive")
    PASSIVE("passive");
    private final String value;

    VMwareUplinkLacpMode(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VMwareUplinkLacpMode fromValue(String v) {
        for (VMwareUplinkLacpMode c: VMwareUplinkLacpMode.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
