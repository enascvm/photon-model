
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PbmBuiltinGenericType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="PbmBuiltinGenericType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="VMW_RANGE"/&gt;
 *     &lt;enumeration value="VMW_SET"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "PbmBuiltinGenericType")
@XmlEnum
public enum PbmBuiltinGenericType {

    VMW_RANGE,
    VMW_SET;

    public String value() {
        return name();
    }

    public static PbmBuiltinGenericType fromValue(String v) {
        return valueOf(v);
    }

}
