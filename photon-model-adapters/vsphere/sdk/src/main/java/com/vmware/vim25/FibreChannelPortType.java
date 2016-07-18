
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for FibreChannelPortType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="FibreChannelPortType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="fabric"/&gt;
 *     &lt;enumeration value="loop"/&gt;
 *     &lt;enumeration value="pointToPoint"/&gt;
 *     &lt;enumeration value="unknown"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "FibreChannelPortType")
@XmlEnum
public enum FibreChannelPortType {

    @XmlEnumValue("fabric")
    FABRIC("fabric"),
    @XmlEnumValue("loop")
    LOOP("loop"),
    @XmlEnumValue("pointToPoint")
    POINT_TO_POINT("pointToPoint"),
    @XmlEnumValue("unknown")
    UNKNOWN("unknown");
    private final String value;

    FibreChannelPortType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static FibreChannelPortType fromValue(String v) {
        for (FibreChannelPortType c: FibreChannelPortType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
