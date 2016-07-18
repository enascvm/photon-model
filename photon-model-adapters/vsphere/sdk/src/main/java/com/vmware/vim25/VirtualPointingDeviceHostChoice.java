
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualPointingDeviceHostChoice.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VirtualPointingDeviceHostChoice"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="autodetect"/&gt;
 *     &lt;enumeration value="intellimouseExplorer"/&gt;
 *     &lt;enumeration value="intellimousePs2"/&gt;
 *     &lt;enumeration value="logitechMouseman"/&gt;
 *     &lt;enumeration value="microsoft_serial"/&gt;
 *     &lt;enumeration value="mouseSystems"/&gt;
 *     &lt;enumeration value="mousemanSerial"/&gt;
 *     &lt;enumeration value="ps2"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VirtualPointingDeviceHostChoice")
@XmlEnum
public enum VirtualPointingDeviceHostChoice {

    @XmlEnumValue("autodetect")
    AUTODETECT("autodetect"),
    @XmlEnumValue("intellimouseExplorer")
    INTELLIMOUSE_EXPLORER("intellimouseExplorer"),
    @XmlEnumValue("intellimousePs2")
    INTELLIMOUSE_PS_2("intellimousePs2"),
    @XmlEnumValue("logitechMouseman")
    LOGITECH_MOUSEMAN("logitechMouseman"),
    @XmlEnumValue("microsoft_serial")
    MICROSOFT_SERIAL("microsoft_serial"),
    @XmlEnumValue("mouseSystems")
    MOUSE_SYSTEMS("mouseSystems"),
    @XmlEnumValue("mousemanSerial")
    MOUSEMAN_SERIAL("mousemanSerial"),
    @XmlEnumValue("ps2")
    PS_2("ps2");
    private final String value;

    VirtualPointingDeviceHostChoice(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VirtualPointingDeviceHostChoice fromValue(String v) {
        for (VirtualPointingDeviceHostChoice c: VirtualPointingDeviceHostChoice.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
