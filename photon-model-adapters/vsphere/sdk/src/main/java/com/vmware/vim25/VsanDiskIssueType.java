
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VsanDiskIssueType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VsanDiskIssueType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="nonExist"/&gt;
 *     &lt;enumeration value="stampMismatch"/&gt;
 *     &lt;enumeration value="unknown"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VsanDiskIssueType")
@XmlEnum
public enum VsanDiskIssueType {

    @XmlEnumValue("nonExist")
    NON_EXIST("nonExist"),
    @XmlEnumValue("stampMismatch")
    STAMP_MISMATCH("stampMismatch"),
    @XmlEnumValue("unknown")
    UNKNOWN("unknown");
    private final String value;

    VsanDiskIssueType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VsanDiskIssueType fromValue(String v) {
        for (VsanDiskIssueType c: VsanDiskIssueType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
