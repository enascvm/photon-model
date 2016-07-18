
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for MetricAlarmOperator.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="MetricAlarmOperator"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="isAbove"/&gt;
 *     &lt;enumeration value="isBelow"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "MetricAlarmOperator")
@XmlEnum
public enum MetricAlarmOperator {

    @XmlEnumValue("isAbove")
    IS_ABOVE("isAbove"),
    @XmlEnumValue("isBelow")
    IS_BELOW("isBelow");
    private final String value;

    MetricAlarmOperator(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static MetricAlarmOperator fromValue(String v) {
        for (MetricAlarmOperator c: MetricAlarmOperator.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
