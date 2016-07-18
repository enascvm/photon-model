
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for EventAlarmExpressionComparisonOperator.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="EventAlarmExpressionComparisonOperator"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="equals"/&gt;
 *     &lt;enumeration value="notEqualTo"/&gt;
 *     &lt;enumeration value="startsWith"/&gt;
 *     &lt;enumeration value="doesNotStartWith"/&gt;
 *     &lt;enumeration value="endsWith"/&gt;
 *     &lt;enumeration value="doesNotEndWith"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "EventAlarmExpressionComparisonOperator")
@XmlEnum
public enum EventAlarmExpressionComparisonOperator {

    @XmlEnumValue("equals")
    EQUALS("equals"),
    @XmlEnumValue("notEqualTo")
    NOT_EQUAL_TO("notEqualTo"),
    @XmlEnumValue("startsWith")
    STARTS_WITH("startsWith"),
    @XmlEnumValue("doesNotStartWith")
    DOES_NOT_START_WITH("doesNotStartWith"),
    @XmlEnumValue("endsWith")
    ENDS_WITH("endsWith"),
    @XmlEnumValue("doesNotEndWith")
    DOES_NOT_END_WITH("doesNotEndWith");
    private final String value;

    EventAlarmExpressionComparisonOperator(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static EventAlarmExpressionComparisonOperator fromValue(String v) {
        for (EventAlarmExpressionComparisonOperator c: EventAlarmExpressionComparisonOperator.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
