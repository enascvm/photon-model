
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DvsEventPortBlockState.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="DvsEventPortBlockState"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="unset"/&gt;
 *     &lt;enumeration value="blocked"/&gt;
 *     &lt;enumeration value="unblocked"/&gt;
 *     &lt;enumeration value="unknown"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "DvsEventPortBlockState")
@XmlEnum
public enum DvsEventPortBlockState {

    @XmlEnumValue("unset")
    UNSET("unset"),
    @XmlEnumValue("blocked")
    BLOCKED("blocked"),
    @XmlEnumValue("unblocked")
    UNBLOCKED("unblocked"),
    @XmlEnumValue("unknown")
    UNKNOWN("unknown");
    private final String value;

    DvsEventPortBlockState(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static DvsEventPortBlockState fromValue(String v) {
        for (DvsEventPortBlockState c: DvsEventPortBlockState.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
