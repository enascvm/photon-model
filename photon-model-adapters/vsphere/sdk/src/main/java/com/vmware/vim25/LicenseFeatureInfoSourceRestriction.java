
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for LicenseFeatureInfoSourceRestriction.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="LicenseFeatureInfoSourceRestriction"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="unrestricted"/&gt;
 *     &lt;enumeration value="served"/&gt;
 *     &lt;enumeration value="file"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "LicenseFeatureInfoSourceRestriction")
@XmlEnum
public enum LicenseFeatureInfoSourceRestriction {

    @XmlEnumValue("unrestricted")
    UNRESTRICTED("unrestricted"),
    @XmlEnumValue("served")
    SERVED("served"),
    @XmlEnumValue("file")
    FILE("file");
    private final String value;

    LicenseFeatureInfoSourceRestriction(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static LicenseFeatureInfoSourceRestriction fromValue(String v) {
        for (LicenseFeatureInfoSourceRestriction c: LicenseFeatureInfoSourceRestriction.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
