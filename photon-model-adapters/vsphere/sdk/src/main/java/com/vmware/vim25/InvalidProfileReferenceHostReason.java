
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for InvalidProfileReferenceHostReason.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="InvalidProfileReferenceHostReason"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="incompatibleVersion"/&gt;
 *     &lt;enumeration value="missingReferenceHost"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "InvalidProfileReferenceHostReason")
@XmlEnum
public enum InvalidProfileReferenceHostReason {

    @XmlEnumValue("incompatibleVersion")
    INCOMPATIBLE_VERSION("incompatibleVersion"),
    @XmlEnumValue("missingReferenceHost")
    MISSING_REFERENCE_HOST("missingReferenceHost");
    private final String value;

    InvalidProfileReferenceHostReason(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static InvalidProfileReferenceHostReason fromValue(String v) {
        for (InvalidProfileReferenceHostReason c: InvalidProfileReferenceHostReason.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
