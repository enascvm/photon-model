
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VchaNodeRole.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VchaNodeRole"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="active"/&gt;
 *     &lt;enumeration value="passive"/&gt;
 *     &lt;enumeration value="witness"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VchaNodeRole")
@XmlEnum
public enum VchaNodeRole {

    @XmlEnumValue("active")
    ACTIVE("active"),
    @XmlEnumValue("passive")
    PASSIVE("passive"),
    @XmlEnumValue("witness")
    WITNESS("witness");
    private final String value;

    VchaNodeRole(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VchaNodeRole fromValue(String v) {
        for (VchaNodeRole c: VchaNodeRole.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
