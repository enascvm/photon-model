
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for CustomizationLicenseDataMode.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="CustomizationLicenseDataMode"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="perServer"/&gt;
 *     &lt;enumeration value="perSeat"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "CustomizationLicenseDataMode")
@XmlEnum
public enum CustomizationLicenseDataMode {

    @XmlEnumValue("perServer")
    PER_SERVER("perServer"),
    @XmlEnumValue("perSeat")
    PER_SEAT("perSeat");
    private final String value;

    CustomizationLicenseDataMode(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static CustomizationLicenseDataMode fromValue(String v) {
        for (CustomizationLicenseDataMode c: CustomizationLicenseDataMode.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
