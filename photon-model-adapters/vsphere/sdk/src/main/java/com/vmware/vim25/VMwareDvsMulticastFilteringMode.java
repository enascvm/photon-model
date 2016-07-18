
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VMwareDvsMulticastFilteringMode.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VMwareDvsMulticastFilteringMode"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="legacyFiltering"/&gt;
 *     &lt;enumeration value="snooping"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VMwareDvsMulticastFilteringMode")
@XmlEnum
public enum VMwareDvsMulticastFilteringMode {

    @XmlEnumValue("legacyFiltering")
    LEGACY_FILTERING("legacyFiltering"),
    @XmlEnumValue("snooping")
    SNOOPING("snooping");
    private final String value;

    VMwareDvsMulticastFilteringMode(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VMwareDvsMulticastFilteringMode fromValue(String v) {
        for (VMwareDvsMulticastFilteringMode c: VMwareDvsMulticastFilteringMode.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
