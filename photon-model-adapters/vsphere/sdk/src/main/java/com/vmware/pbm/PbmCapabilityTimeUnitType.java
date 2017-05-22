
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PbmCapabilityTimeUnitType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="PbmCapabilityTimeUnitType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="SECONDS"/&gt;
 *     &lt;enumeration value="MINUTES"/&gt;
 *     &lt;enumeration value="HOURS"/&gt;
 *     &lt;enumeration value="DAYS"/&gt;
 *     &lt;enumeration value="WEEKS"/&gt;
 *     &lt;enumeration value="MONTHS"/&gt;
 *     &lt;enumeration value="YEARS"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "PbmCapabilityTimeUnitType")
@XmlEnum
public enum PbmCapabilityTimeUnitType {

    SECONDS,
    MINUTES,
    HOURS,
    DAYS,
    WEEKS,
    MONTHS,
    YEARS;

    public String value() {
        return name();
    }

    public static PbmCapabilityTimeUnitType fromValue(String v) {
        return valueOf(v);
    }

}
