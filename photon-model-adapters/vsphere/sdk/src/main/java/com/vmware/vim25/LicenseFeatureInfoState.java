
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for LicenseFeatureInfoState.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="LicenseFeatureInfoState"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="enabled"/&gt;
 *     &lt;enumeration value="disabled"/&gt;
 *     &lt;enumeration value="optional"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "LicenseFeatureInfoState")
@XmlEnum
public enum LicenseFeatureInfoState {

    @XmlEnumValue("enabled")
    ENABLED("enabled"),
    @XmlEnumValue("disabled")
    DISABLED("disabled"),
    @XmlEnumValue("optional")
    OPTIONAL("optional");
    private final String value;

    LicenseFeatureInfoState(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static LicenseFeatureInfoState fromValue(String v) {
        for (LicenseFeatureInfoState c: LicenseFeatureInfoState.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
