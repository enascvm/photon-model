
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostGraphicsInfoGraphicsType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="HostGraphicsInfoGraphicsType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="basic"/&gt;
 *     &lt;enumeration value="shared"/&gt;
 *     &lt;enumeration value="direct"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "HostGraphicsInfoGraphicsType")
@XmlEnum
public enum HostGraphicsInfoGraphicsType {

    @XmlEnumValue("basic")
    BASIC("basic"),
    @XmlEnumValue("shared")
    SHARED("shared"),
    @XmlEnumValue("direct")
    DIRECT("direct");
    private final String value;

    HostGraphicsInfoGraphicsType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static HostGraphicsInfoGraphicsType fromValue(String v) {
        for (HostGraphicsInfoGraphicsType c: HostGraphicsInfoGraphicsType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
