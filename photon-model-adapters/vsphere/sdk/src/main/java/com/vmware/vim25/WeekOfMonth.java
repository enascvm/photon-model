
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for WeekOfMonth.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="WeekOfMonth"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="first"/&gt;
 *     &lt;enumeration value="second"/&gt;
 *     &lt;enumeration value="third"/&gt;
 *     &lt;enumeration value="fourth"/&gt;
 *     &lt;enumeration value="last"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "WeekOfMonth")
@XmlEnum
public enum WeekOfMonth {

    @XmlEnumValue("first")
    FIRST("first"),
    @XmlEnumValue("second")
    SECOND("second"),
    @XmlEnumValue("third")
    THIRD("third"),
    @XmlEnumValue("fourth")
    FOURTH("fourth"),
    @XmlEnumValue("last")
    LAST("last");
    private final String value;

    WeekOfMonth(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static WeekOfMonth fromValue(String v) {
        for (WeekOfMonth c: WeekOfMonth.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
