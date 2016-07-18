
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ComplianceResultStatus.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="ComplianceResultStatus"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="compliant"/&gt;
 *     &lt;enumeration value="nonCompliant"/&gt;
 *     &lt;enumeration value="unknown"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "ComplianceResultStatus")
@XmlEnum
public enum ComplianceResultStatus {

    @XmlEnumValue("compliant")
    COMPLIANT("compliant"),
    @XmlEnumValue("nonCompliant")
    NON_COMPLIANT("nonCompliant"),
    @XmlEnumValue("unknown")
    UNKNOWN("unknown");
    private final String value;

    ComplianceResultStatus(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ComplianceResultStatus fromValue(String v) {
        for (ComplianceResultStatus c: ComplianceResultStatus.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
