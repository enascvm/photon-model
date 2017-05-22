
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PbmBuiltinType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="PbmBuiltinType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="XSD_LONG"/&gt;
 *     &lt;enumeration value="XSD_SHORT"/&gt;
 *     &lt;enumeration value="XSD_INTEGER"/&gt;
 *     &lt;enumeration value="XSD_INT"/&gt;
 *     &lt;enumeration value="XSD_STRING"/&gt;
 *     &lt;enumeration value="XSD_BOOLEAN"/&gt;
 *     &lt;enumeration value="XSD_DOUBLE"/&gt;
 *     &lt;enumeration value="XSD_DATETIME"/&gt;
 *     &lt;enumeration value="VMW_TIMESPAN"/&gt;
 *     &lt;enumeration value="VMW_POLICY"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "PbmBuiltinType")
@XmlEnum
public enum PbmBuiltinType {

    XSD_LONG,
    XSD_SHORT,
    XSD_INTEGER,
    XSD_INT,
    XSD_STRING,
    XSD_BOOLEAN,
    XSD_DOUBLE,
    XSD_DATETIME,
    VMW_TIMESPAN,
    VMW_POLICY;

    public String value() {
        return name();
    }

    public static PbmBuiltinType fromValue(String v) {
        return valueOf(v);
    }

}
