
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PbmComplianceResultComplianceTaskStatus.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="PbmComplianceResultComplianceTaskStatus"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="inProgress"/&gt;
 *     &lt;enumeration value="success"/&gt;
 *     &lt;enumeration value="failed"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "PbmComplianceResultComplianceTaskStatus")
@XmlEnum
public enum PbmComplianceResultComplianceTaskStatus {

    @XmlEnumValue("inProgress")
    IN_PROGRESS("inProgress"),
    @XmlEnumValue("success")
    SUCCESS("success"),
    @XmlEnumValue("failed")
    FAILED("failed");
    private final String value;

    PbmComplianceResultComplianceTaskStatus(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static PbmComplianceResultComplianceTaskStatus fromValue(String v) {
        for (PbmComplianceResultComplianceTaskStatus c: PbmComplianceResultComplianceTaskStatus.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
