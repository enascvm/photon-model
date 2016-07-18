
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VMwareDvsLacpApiVersion.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VMwareDvsLacpApiVersion"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="singleLag"/&gt;
 *     &lt;enumeration value="multipleLag"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VMwareDvsLacpApiVersion")
@XmlEnum
public enum VMwareDvsLacpApiVersion {

    @XmlEnumValue("singleLag")
    SINGLE_LAG("singleLag"),
    @XmlEnumValue("multipleLag")
    MULTIPLE_LAG("multipleLag");
    private final String value;

    VMwareDvsLacpApiVersion(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VMwareDvsLacpApiVersion fromValue(String v) {
        for (VMwareDvsLacpApiVersion c: VMwareDvsLacpApiVersion.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
