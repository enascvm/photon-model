
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for EventCategory.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="EventCategory"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="info"/&gt;
 *     &lt;enumeration value="warning"/&gt;
 *     &lt;enumeration value="error"/&gt;
 *     &lt;enumeration value="user"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "EventCategory")
@XmlEnum
public enum EventCategory {

    @XmlEnumValue("info")
    INFO("info"),
    @XmlEnumValue("warning")
    WARNING("warning"),
    @XmlEnumValue("error")
    ERROR("error"),
    @XmlEnumValue("user")
    USER("user");
    private final String value;

    EventCategory(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static EventCategory fromValue(String v) {
        for (EventCategory c: EventCategory.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
