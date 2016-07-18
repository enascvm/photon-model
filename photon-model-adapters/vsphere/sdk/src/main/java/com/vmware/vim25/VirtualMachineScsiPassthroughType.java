
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualMachineScsiPassthroughType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VirtualMachineScsiPassthroughType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="disk"/&gt;
 *     &lt;enumeration value="tape"/&gt;
 *     &lt;enumeration value="printer"/&gt;
 *     &lt;enumeration value="processor"/&gt;
 *     &lt;enumeration value="worm"/&gt;
 *     &lt;enumeration value="cdrom"/&gt;
 *     &lt;enumeration value="scanner"/&gt;
 *     &lt;enumeration value="optical"/&gt;
 *     &lt;enumeration value="media"/&gt;
 *     &lt;enumeration value="com"/&gt;
 *     &lt;enumeration value="raid"/&gt;
 *     &lt;enumeration value="unknown"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VirtualMachineScsiPassthroughType")
@XmlEnum
public enum VirtualMachineScsiPassthroughType {

    @XmlEnumValue("disk")
    DISK("disk"),
    @XmlEnumValue("tape")
    TAPE("tape"),
    @XmlEnumValue("printer")
    PRINTER("printer"),
    @XmlEnumValue("processor")
    PROCESSOR("processor"),
    @XmlEnumValue("worm")
    WORM("worm"),
    @XmlEnumValue("cdrom")
    CDROM("cdrom"),
    @XmlEnumValue("scanner")
    SCANNER("scanner"),
    @XmlEnumValue("optical")
    OPTICAL("optical"),
    @XmlEnumValue("media")
    MEDIA("media"),
    @XmlEnumValue("com")
    COM("com"),
    @XmlEnumValue("raid")
    RAID("raid"),
    @XmlEnumValue("unknown")
    UNKNOWN("unknown");
    private final String value;

    VirtualMachineScsiPassthroughType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VirtualMachineScsiPassthroughType fromValue(String v) {
        for (VirtualMachineScsiPassthroughType c: VirtualMachineScsiPassthroughType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
