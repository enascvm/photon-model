
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualMachineUsbInfoFamily.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VirtualMachineUsbInfoFamily"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="audio"/&gt;
 *     &lt;enumeration value="hid"/&gt;
 *     &lt;enumeration value="hid_bootable"/&gt;
 *     &lt;enumeration value="physical"/&gt;
 *     &lt;enumeration value="communication"/&gt;
 *     &lt;enumeration value="imaging"/&gt;
 *     &lt;enumeration value="printer"/&gt;
 *     &lt;enumeration value="storage"/&gt;
 *     &lt;enumeration value="hub"/&gt;
 *     &lt;enumeration value="smart_card"/&gt;
 *     &lt;enumeration value="security"/&gt;
 *     &lt;enumeration value="video"/&gt;
 *     &lt;enumeration value="wireless"/&gt;
 *     &lt;enumeration value="bluetooth"/&gt;
 *     &lt;enumeration value="wusb"/&gt;
 *     &lt;enumeration value="pda"/&gt;
 *     &lt;enumeration value="vendor_specific"/&gt;
 *     &lt;enumeration value="other"/&gt;
 *     &lt;enumeration value="unknownFamily"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VirtualMachineUsbInfoFamily")
@XmlEnum
public enum VirtualMachineUsbInfoFamily {

    @XmlEnumValue("audio")
    AUDIO("audio"),
    @XmlEnumValue("hid")
    HID("hid"),
    @XmlEnumValue("hid_bootable")
    HID_BOOTABLE("hid_bootable"),
    @XmlEnumValue("physical")
    PHYSICAL("physical"),
    @XmlEnumValue("communication")
    COMMUNICATION("communication"),
    @XmlEnumValue("imaging")
    IMAGING("imaging"),
    @XmlEnumValue("printer")
    PRINTER("printer"),
    @XmlEnumValue("storage")
    STORAGE("storage"),
    @XmlEnumValue("hub")
    HUB("hub"),
    @XmlEnumValue("smart_card")
    SMART_CARD("smart_card"),
    @XmlEnumValue("security")
    SECURITY("security"),
    @XmlEnumValue("video")
    VIDEO("video"),
    @XmlEnumValue("wireless")
    WIRELESS("wireless"),
    @XmlEnumValue("bluetooth")
    BLUETOOTH("bluetooth"),
    @XmlEnumValue("wusb")
    WUSB("wusb"),
    @XmlEnumValue("pda")
    PDA("pda"),
    @XmlEnumValue("vendor_specific")
    VENDOR_SPECIFIC("vendor_specific"),
    @XmlEnumValue("other")
    OTHER("other"),
    @XmlEnumValue("unknownFamily")
    UNKNOWN_FAMILY("unknownFamily");
    private final String value;

    VirtualMachineUsbInfoFamily(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VirtualMachineUsbInfoFamily fromValue(String v) {
        for (VirtualMachineUsbInfoFamily c: VirtualMachineUsbInfoFamily.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
