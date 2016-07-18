
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for SharesLevel.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="SharesLevel"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="low"/&gt;
 *     &lt;enumeration value="normal"/&gt;
 *     &lt;enumeration value="high"/&gt;
 *     &lt;enumeration value="custom"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "SharesLevel")
@XmlEnum
public enum SharesLevel {

    @XmlEnumValue("low")
    LOW("low"),
    @XmlEnumValue("normal")
    NORMAL("normal"),
    @XmlEnumValue("high")
    HIGH("high"),
    @XmlEnumValue("custom")
    CUSTOM("custom");
    private final String value;

    SharesLevel(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static SharesLevel fromValue(String v) {
        for (SharesLevel c: SharesLevel.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
