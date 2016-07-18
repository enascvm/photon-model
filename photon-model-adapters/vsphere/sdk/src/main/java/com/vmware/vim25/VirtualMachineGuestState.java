
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualMachineGuestState.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VirtualMachineGuestState"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="running"/&gt;
 *     &lt;enumeration value="shuttingDown"/&gt;
 *     &lt;enumeration value="resetting"/&gt;
 *     &lt;enumeration value="standby"/&gt;
 *     &lt;enumeration value="notRunning"/&gt;
 *     &lt;enumeration value="unknown"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VirtualMachineGuestState")
@XmlEnum
public enum VirtualMachineGuestState {

    @XmlEnumValue("running")
    RUNNING("running"),
    @XmlEnumValue("shuttingDown")
    SHUTTING_DOWN("shuttingDown"),
    @XmlEnumValue("resetting")
    RESETTING("resetting"),
    @XmlEnumValue("standby")
    STANDBY("standby"),
    @XmlEnumValue("notRunning")
    NOT_RUNNING("notRunning"),
    @XmlEnumValue("unknown")
    UNKNOWN("unknown");
    private final String value;

    VirtualMachineGuestState(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VirtualMachineGuestState fromValue(String v) {
        for (VirtualMachineGuestState c: VirtualMachineGuestState.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
