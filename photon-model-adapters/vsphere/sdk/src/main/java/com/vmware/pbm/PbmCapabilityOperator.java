
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PbmCapabilityOperator.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="PbmCapabilityOperator"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="NOT"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "PbmCapabilityOperator")
@XmlEnum
public enum PbmCapabilityOperator {

    NOT;

    public String value() {
        return name();
    }

    public static PbmCapabilityOperator fromValue(String v) {
        return valueOf(v);
    }

}
