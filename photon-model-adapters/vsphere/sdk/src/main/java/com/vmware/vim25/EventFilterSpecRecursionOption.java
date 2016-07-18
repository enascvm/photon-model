
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for EventFilterSpecRecursionOption.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="EventFilterSpecRecursionOption"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="self"/&gt;
 *     &lt;enumeration value="children"/&gt;
 *     &lt;enumeration value="all"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "EventFilterSpecRecursionOption")
@XmlEnum
public enum EventFilterSpecRecursionOption {

    @XmlEnumValue("self")
    SELF("self"),
    @XmlEnumValue("children")
    CHILDREN("children"),
    @XmlEnumValue("all")
    ALL("all");
    private final String value;

    EventFilterSpecRecursionOption(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static EventFilterSpecRecursionOption fromValue(String v) {
        for (EventFilterSpecRecursionOption c: EventFilterSpecRecursionOption.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
