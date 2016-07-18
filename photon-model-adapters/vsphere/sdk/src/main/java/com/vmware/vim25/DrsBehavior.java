
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DrsBehavior.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="DrsBehavior"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="manual"/&gt;
 *     &lt;enumeration value="partiallyAutomated"/&gt;
 *     &lt;enumeration value="fullyAutomated"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "DrsBehavior")
@XmlEnum
public enum DrsBehavior {

    @XmlEnumValue("manual")
    MANUAL("manual"),
    @XmlEnumValue("partiallyAutomated")
    PARTIALLY_AUTOMATED("partiallyAutomated"),
    @XmlEnumValue("fullyAutomated")
    FULLY_AUTOMATED("fullyAutomated");
    private final String value;

    DrsBehavior(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static DrsBehavior fromValue(String v) {
        for (DrsBehavior c: DrsBehavior.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
