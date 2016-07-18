
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VMotionCompatibilityType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VMotionCompatibilityType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="cpu"/&gt;
 *     &lt;enumeration value="software"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VMotionCompatibilityType")
@XmlEnum
public enum VMotionCompatibilityType {

    @XmlEnumValue("cpu")
    CPU("cpu"),
    @XmlEnumValue("software")
    SOFTWARE("software");
    private final String value;

    VMotionCompatibilityType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VMotionCompatibilityType fromValue(String v) {
        for (VMotionCompatibilityType c: VMotionCompatibilityType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
