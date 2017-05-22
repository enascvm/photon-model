
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PbmComplianceStatus.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="PbmComplianceStatus"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="compliant"/&gt;
 *     &lt;enumeration value="nonCompliant"/&gt;
 *     &lt;enumeration value="unknown"/&gt;
 *     &lt;enumeration value="notApplicable"/&gt;
 *     &lt;enumeration value="outOfDate"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "PbmComplianceStatus")
@XmlEnum
public enum PbmComplianceStatus {

    @XmlEnumValue("compliant")
    COMPLIANT("compliant"),
    @XmlEnumValue("nonCompliant")
    NON_COMPLIANT("nonCompliant"),
    @XmlEnumValue("unknown")
    UNKNOWN("unknown"),
    @XmlEnumValue("notApplicable")
    NOT_APPLICABLE("notApplicable"),
    @XmlEnumValue("outOfDate")
    OUT_OF_DATE("outOfDate");
    private final String value;

    PbmComplianceStatus(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static PbmComplianceStatus fromValue(String v) {
        for (PbmComplianceStatus c: PbmComplianceStatus.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
