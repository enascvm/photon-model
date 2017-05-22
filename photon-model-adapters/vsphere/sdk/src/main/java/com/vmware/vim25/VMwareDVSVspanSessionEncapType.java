
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VMwareDVSVspanSessionEncapType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VMwareDVSVspanSessionEncapType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="gre"/&gt;
 *     &lt;enumeration value="erspan2"/&gt;
 *     &lt;enumeration value="erspan3"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VMwareDVSVspanSessionEncapType")
@XmlEnum
public enum VMwareDVSVspanSessionEncapType {

    @XmlEnumValue("gre")
    GRE("gre"),
    @XmlEnumValue("erspan2")
    ERSPAN_2("erspan2"),
    @XmlEnumValue("erspan3")
    ERSPAN_3("erspan3");
    private final String value;

    VMwareDVSVspanSessionEncapType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VMwareDVSVspanSessionEncapType fromValue(String v) {
        for (VMwareDVSVspanSessionEncapType c: VMwareDVSVspanSessionEncapType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
