
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PbmVvolType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="PbmVvolType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="Config"/&gt;
 *     &lt;enumeration value="Data"/&gt;
 *     &lt;enumeration value="Swap"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "PbmVvolType")
@XmlEnum
public enum PbmVvolType {

    @XmlEnumValue("Config")
    CONFIG("Config"),
    @XmlEnumValue("Data")
    DATA("Data"),
    @XmlEnumValue("Swap")
    SWAP("Swap");
    private final String value;

    PbmVvolType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static PbmVvolType fromValue(String v) {
        for (PbmVvolType c: PbmVvolType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
