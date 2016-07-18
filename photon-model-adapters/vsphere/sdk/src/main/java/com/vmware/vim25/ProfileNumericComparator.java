
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ProfileNumericComparator.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="ProfileNumericComparator"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="lessThan"/&gt;
 *     &lt;enumeration value="lessThanEqual"/&gt;
 *     &lt;enumeration value="equal"/&gt;
 *     &lt;enumeration value="notEqual"/&gt;
 *     &lt;enumeration value="greaterThanEqual"/&gt;
 *     &lt;enumeration value="greaterThan"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "ProfileNumericComparator")
@XmlEnum
public enum ProfileNumericComparator {

    @XmlEnumValue("lessThan")
    LESS_THAN("lessThan"),
    @XmlEnumValue("lessThanEqual")
    LESS_THAN_EQUAL("lessThanEqual"),
    @XmlEnumValue("equal")
    EQUAL("equal"),
    @XmlEnumValue("notEqual")
    NOT_EQUAL("notEqual"),
    @XmlEnumValue("greaterThanEqual")
    GREATER_THAN_EQUAL("greaterThanEqual"),
    @XmlEnumValue("greaterThan")
    GREATER_THAN("greaterThan");
    private final String value;

    ProfileNumericComparator(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ProfileNumericComparator fromValue(String v) {
        for (ProfileNumericComparator c: ProfileNumericComparator.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
