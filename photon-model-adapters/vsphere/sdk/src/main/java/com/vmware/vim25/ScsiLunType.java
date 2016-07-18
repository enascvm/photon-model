
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ScsiLunType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="ScsiLunType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="disk"/&gt;
 *     &lt;enumeration value="tape"/&gt;
 *     &lt;enumeration value="printer"/&gt;
 *     &lt;enumeration value="processor"/&gt;
 *     &lt;enumeration value="worm"/&gt;
 *     &lt;enumeration value="cdrom"/&gt;
 *     &lt;enumeration value="scanner"/&gt;
 *     &lt;enumeration value="opticalDevice"/&gt;
 *     &lt;enumeration value="mediaChanger"/&gt;
 *     &lt;enumeration value="communications"/&gt;
 *     &lt;enumeration value="storageArrayController"/&gt;
 *     &lt;enumeration value="enclosure"/&gt;
 *     &lt;enumeration value="unknown"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "ScsiLunType")
@XmlEnum
public enum ScsiLunType {

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
    @XmlEnumValue("opticalDevice")
    OPTICAL_DEVICE("opticalDevice"),
    @XmlEnumValue("mediaChanger")
    MEDIA_CHANGER("mediaChanger"),
    @XmlEnumValue("communications")
    COMMUNICATIONS("communications"),
    @XmlEnumValue("storageArrayController")
    STORAGE_ARRAY_CONTROLLER("storageArrayController"),
    @XmlEnumValue("enclosure")
    ENCLOSURE("enclosure"),
    @XmlEnumValue("unknown")
    UNKNOWN("unknown");
    private final String value;

    ScsiLunType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ScsiLunType fromValue(String v) {
        for (ScsiLunType c: ScsiLunType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
