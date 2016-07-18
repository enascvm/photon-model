
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for WillLoseHAProtectionResolution.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="WillLoseHAProtectionResolution"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="svmotion"/&gt;
 *     &lt;enumeration value="relocate"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "WillLoseHAProtectionResolution")
@XmlEnum
public enum WillLoseHAProtectionResolution {

    @XmlEnumValue("svmotion")
    SVMOTION("svmotion"),
    @XmlEnumValue("relocate")
    RELOCATE("relocate");
    private final String value;

    WillLoseHAProtectionResolution(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static WillLoseHAProtectionResolution fromValue(String v) {
        for (WillLoseHAProtectionResolution c: WillLoseHAProtectionResolution.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
