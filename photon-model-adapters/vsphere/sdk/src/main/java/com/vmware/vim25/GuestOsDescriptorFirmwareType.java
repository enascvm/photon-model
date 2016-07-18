
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for GuestOsDescriptorFirmwareType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="GuestOsDescriptorFirmwareType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="bios"/&gt;
 *     &lt;enumeration value="efi"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "GuestOsDescriptorFirmwareType")
@XmlEnum
public enum GuestOsDescriptorFirmwareType {

    @XmlEnumValue("bios")
    BIOS("bios"),
    @XmlEnumValue("efi")
    EFI("efi");
    private final String value;

    GuestOsDescriptorFirmwareType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static GuestOsDescriptorFirmwareType fromValue(String v) {
        for (GuestOsDescriptorFirmwareType c: GuestOsDescriptorFirmwareType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
