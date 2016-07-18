
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualMachineGuestOsFamily.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VirtualMachineGuestOsFamily"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="windowsGuest"/&gt;
 *     &lt;enumeration value="linuxGuest"/&gt;
 *     &lt;enumeration value="netwareGuest"/&gt;
 *     &lt;enumeration value="solarisGuest"/&gt;
 *     &lt;enumeration value="darwinGuestFamily"/&gt;
 *     &lt;enumeration value="otherGuestFamily"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VirtualMachineGuestOsFamily")
@XmlEnum
public enum VirtualMachineGuestOsFamily {

    @XmlEnumValue("windowsGuest")
    WINDOWS_GUEST("windowsGuest"),
    @XmlEnumValue("linuxGuest")
    LINUX_GUEST("linuxGuest"),
    @XmlEnumValue("netwareGuest")
    NETWARE_GUEST("netwareGuest"),
    @XmlEnumValue("solarisGuest")
    SOLARIS_GUEST("solarisGuest"),
    @XmlEnumValue("darwinGuestFamily")
    DARWIN_GUEST_FAMILY("darwinGuestFamily"),
    @XmlEnumValue("otherGuestFamily")
    OTHER_GUEST_FAMILY("otherGuestFamily");
    private final String value;

    VirtualMachineGuestOsFamily(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VirtualMachineGuestOsFamily fromValue(String v) {
        for (VirtualMachineGuestOsFamily c: VirtualMachineGuestOsFamily.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
