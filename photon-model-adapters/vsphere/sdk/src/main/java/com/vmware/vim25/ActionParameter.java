
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ActionParameter.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="ActionParameter"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="targetName"/&gt;
 *     &lt;enumeration value="alarmName"/&gt;
 *     &lt;enumeration value="oldStatus"/&gt;
 *     &lt;enumeration value="newStatus"/&gt;
 *     &lt;enumeration value="triggeringSummary"/&gt;
 *     &lt;enumeration value="declaringSummary"/&gt;
 *     &lt;enumeration value="eventDescription"/&gt;
 *     &lt;enumeration value="target"/&gt;
 *     &lt;enumeration value="alarm"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "ActionParameter")
@XmlEnum
public enum ActionParameter {

    @XmlEnumValue("targetName")
    TARGET_NAME("targetName"),
    @XmlEnumValue("alarmName")
    ALARM_NAME("alarmName"),
    @XmlEnumValue("oldStatus")
    OLD_STATUS("oldStatus"),
    @XmlEnumValue("newStatus")
    NEW_STATUS("newStatus"),
    @XmlEnumValue("triggeringSummary")
    TRIGGERING_SUMMARY("triggeringSummary"),
    @XmlEnumValue("declaringSummary")
    DECLARING_SUMMARY("declaringSummary"),
    @XmlEnumValue("eventDescription")
    EVENT_DESCRIPTION("eventDescription"),
    @XmlEnumValue("target")
    TARGET("target"),
    @XmlEnumValue("alarm")
    ALARM("alarm");
    private final String value;

    ActionParameter(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ActionParameter fromValue(String v) {
        for (ActionParameter c: ActionParameter.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
