
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PbmSystemCreatedProfileType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="PbmSystemCreatedProfileType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="VsanDefaultProfile"/&gt;
 *     &lt;enumeration value="VVolDefaultProfile"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "PbmSystemCreatedProfileType")
@XmlEnum
public enum PbmSystemCreatedProfileType {

    @XmlEnumValue("VsanDefaultProfile")
    VSAN_DEFAULT_PROFILE("VsanDefaultProfile"),
    @XmlEnumValue("VVolDefaultProfile")
    V_VOL_DEFAULT_PROFILE("VVolDefaultProfile");
    private final String value;

    PbmSystemCreatedProfileType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static PbmSystemCreatedProfileType fromValue(String v) {
        for (PbmSystemCreatedProfileType c: PbmSystemCreatedProfileType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
