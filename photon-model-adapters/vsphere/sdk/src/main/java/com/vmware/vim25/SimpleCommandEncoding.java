
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for SimpleCommandEncoding.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="SimpleCommandEncoding"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="CSV"/&gt;
 *     &lt;enumeration value="HEX"/&gt;
 *     &lt;enumeration value="STRING"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "SimpleCommandEncoding")
@XmlEnum
public enum SimpleCommandEncoding {

    CSV,
    HEX,
    STRING;

    public String value() {
        return name();
    }

    public static SimpleCommandEncoding fromValue(String v) {
        return valueOf(v);
    }

}
