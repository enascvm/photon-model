
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostHardwareElementStatus.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="HostHardwareElementStatus"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="Unknown"/&gt;
 *     &lt;enumeration value="Green"/&gt;
 *     &lt;enumeration value="Yellow"/&gt;
 *     &lt;enumeration value="Red"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "HostHardwareElementStatus")
@XmlEnum
public enum HostHardwareElementStatus {

    @XmlEnumValue("Unknown")
    UNKNOWN("Unknown"),
    @XmlEnumValue("Green")
    GREEN("Green"),
    @XmlEnumValue("Yellow")
    YELLOW("Yellow"),
    @XmlEnumValue("Red")
    RED("Red");
    private final String value;

    HostHardwareElementStatus(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static HostHardwareElementStatus fromValue(String v) {
        for (HostHardwareElementStatus c: HostHardwareElementStatus.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
