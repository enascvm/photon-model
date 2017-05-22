
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostGraphicsConfigGraphicsType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="HostGraphicsConfigGraphicsType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="shared"/&gt;
 *     &lt;enumeration value="sharedDirect"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "HostGraphicsConfigGraphicsType")
@XmlEnum
public enum HostGraphicsConfigGraphicsType {

    @XmlEnumValue("shared")
    SHARED("shared"),
    @XmlEnumValue("sharedDirect")
    SHARED_DIRECT("sharedDirect");
    private final String value;

    HostGraphicsConfigGraphicsType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static HostGraphicsConfigGraphicsType fromValue(String v) {
        for (HostGraphicsConfigGraphicsType c: HostGraphicsConfigGraphicsType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
