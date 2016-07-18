
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VAppAutoStartAction.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VAppAutoStartAction"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="none"/&gt;
 *     &lt;enumeration value="powerOn"/&gt;
 *     &lt;enumeration value="powerOff"/&gt;
 *     &lt;enumeration value="guestShutdown"/&gt;
 *     &lt;enumeration value="suspend"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VAppAutoStartAction")
@XmlEnum
public enum VAppAutoStartAction {

    @XmlEnumValue("none")
    NONE("none"),
    @XmlEnumValue("powerOn")
    POWER_ON("powerOn"),
    @XmlEnumValue("powerOff")
    POWER_OFF("powerOff"),
    @XmlEnumValue("guestShutdown")
    GUEST_SHUTDOWN("guestShutdown"),
    @XmlEnumValue("suspend")
    SUSPEND("suspend");
    private final String value;

    VAppAutoStartAction(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VAppAutoStartAction fromValue(String v) {
        for (VAppAutoStartAction c: VAppAutoStartAction.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
