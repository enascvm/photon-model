
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VchaClusterMode.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VchaClusterMode"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="enabled"/&gt;
 *     &lt;enumeration value="disabled"/&gt;
 *     &lt;enumeration value="maintenance"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VchaClusterMode")
@XmlEnum
public enum VchaClusterMode {

    @XmlEnumValue("enabled")
    ENABLED("enabled"),
    @XmlEnumValue("disabled")
    DISABLED("disabled"),
    @XmlEnumValue("maintenance")
    MAINTENANCE("maintenance");
    private final String value;

    VchaClusterMode(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VchaClusterMode fromValue(String v) {
        for (VchaClusterMode c: VchaClusterMode.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
