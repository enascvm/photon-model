
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for CannotPowerOffVmInClusterOperation.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="CannotPowerOffVmInClusterOperation"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="suspend"/&gt;
 *     &lt;enumeration value="powerOff"/&gt;
 *     &lt;enumeration value="guestShutdown"/&gt;
 *     &lt;enumeration value="guestSuspend"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "CannotPowerOffVmInClusterOperation")
@XmlEnum
public enum CannotPowerOffVmInClusterOperation {

    @XmlEnumValue("suspend")
    SUSPEND("suspend"),
    @XmlEnumValue("powerOff")
    POWER_OFF("powerOff"),
    @XmlEnumValue("guestShutdown")
    GUEST_SHUTDOWN("guestShutdown"),
    @XmlEnumValue("guestSuspend")
    GUEST_SUSPEND("guestSuspend");
    private final String value;

    CannotPowerOffVmInClusterOperation(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static CannotPowerOffVmInClusterOperation fromValue(String v) {
        for (CannotPowerOffVmInClusterOperation c: CannotPowerOffVmInClusterOperation.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
