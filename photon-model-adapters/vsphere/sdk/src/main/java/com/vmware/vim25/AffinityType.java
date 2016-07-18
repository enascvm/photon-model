
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for AffinityType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="AffinityType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="memory"/&gt;
 *     &lt;enumeration value="cpu"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "AffinityType")
@XmlEnum
public enum AffinityType {

    @XmlEnumValue("memory")
    MEMORY("memory"),
    @XmlEnumValue("cpu")
    CPU("cpu");
    private final String value;

    AffinityType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static AffinityType fromValue(String v) {
        for (AffinityType c: AffinityType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
