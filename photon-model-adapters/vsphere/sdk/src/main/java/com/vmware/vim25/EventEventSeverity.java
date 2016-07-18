
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for EventEventSeverity.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="EventEventSeverity"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="error"/&gt;
 *     &lt;enumeration value="warning"/&gt;
 *     &lt;enumeration value="info"/&gt;
 *     &lt;enumeration value="user"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "EventEventSeverity")
@XmlEnum
public enum EventEventSeverity {

    @XmlEnumValue("error")
    ERROR("error"),
    @XmlEnumValue("warning")
    WARNING("warning"),
    @XmlEnumValue("info")
    INFO("info"),
    @XmlEnumValue("user")
    USER("user");
    private final String value;

    EventEventSeverity(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static EventEventSeverity fromValue(String v) {
        for (EventEventSeverity c: EventEventSeverity.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
