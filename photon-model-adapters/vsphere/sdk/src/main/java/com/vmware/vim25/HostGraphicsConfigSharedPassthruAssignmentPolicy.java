
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostGraphicsConfigSharedPassthruAssignmentPolicy.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="HostGraphicsConfigSharedPassthruAssignmentPolicy"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="performance"/&gt;
 *     &lt;enumeration value="consolidation"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "HostGraphicsConfigSharedPassthruAssignmentPolicy")
@XmlEnum
public enum HostGraphicsConfigSharedPassthruAssignmentPolicy {

    @XmlEnumValue("performance")
    PERFORMANCE("performance"),
    @XmlEnumValue("consolidation")
    CONSOLIDATION("consolidation");
    private final String value;

    HostGraphicsConfigSharedPassthruAssignmentPolicy(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static HostGraphicsConfigSharedPassthruAssignmentPolicy fromValue(String v) {
        for (HostGraphicsConfigSharedPassthruAssignmentPolicy c: HostGraphicsConfigSharedPassthruAssignmentPolicy.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
