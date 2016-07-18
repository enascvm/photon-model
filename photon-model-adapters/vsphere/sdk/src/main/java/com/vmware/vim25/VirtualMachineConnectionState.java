
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualMachineConnectionState.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VirtualMachineConnectionState"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="connected"/&gt;
 *     &lt;enumeration value="disconnected"/&gt;
 *     &lt;enumeration value="orphaned"/&gt;
 *     &lt;enumeration value="inaccessible"/&gt;
 *     &lt;enumeration value="invalid"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VirtualMachineConnectionState")
@XmlEnum
public enum VirtualMachineConnectionState {

    @XmlEnumValue("connected")
    CONNECTED("connected"),
    @XmlEnumValue("disconnected")
    DISCONNECTED("disconnected"),
    @XmlEnumValue("orphaned")
    ORPHANED("orphaned"),
    @XmlEnumValue("inaccessible")
    INACCESSIBLE("inaccessible"),
    @XmlEnumValue("invalid")
    INVALID("invalid");
    private final String value;

    VirtualMachineConnectionState(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VirtualMachineConnectionState fromValue(String v) {
        for (VirtualMachineConnectionState c: VirtualMachineConnectionState.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
