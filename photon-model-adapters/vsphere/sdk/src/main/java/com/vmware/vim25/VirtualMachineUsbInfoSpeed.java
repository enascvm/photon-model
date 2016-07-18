
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualMachineUsbInfoSpeed.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VirtualMachineUsbInfoSpeed"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="low"/&gt;
 *     &lt;enumeration value="full"/&gt;
 *     &lt;enumeration value="high"/&gt;
 *     &lt;enumeration value="superSpeed"/&gt;
 *     &lt;enumeration value="unknownSpeed"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VirtualMachineUsbInfoSpeed")
@XmlEnum
public enum VirtualMachineUsbInfoSpeed {

    @XmlEnumValue("low")
    LOW("low"),
    @XmlEnumValue("full")
    FULL("full"),
    @XmlEnumValue("high")
    HIGH("high"),
    @XmlEnumValue("superSpeed")
    SUPER_SPEED("superSpeed"),
    @XmlEnumValue("unknownSpeed")
    UNKNOWN_SPEED("unknownSpeed");
    private final String value;

    VirtualMachineUsbInfoSpeed(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VirtualMachineUsbInfoSpeed fromValue(String v) {
        for (VirtualMachineUsbInfoSpeed c: VirtualMachineUsbInfoSpeed.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
