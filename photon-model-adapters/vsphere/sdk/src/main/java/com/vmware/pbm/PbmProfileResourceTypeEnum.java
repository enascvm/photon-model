
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PbmProfileResourceTypeEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="PbmProfileResourceTypeEnum"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="STORAGE"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "PbmProfileResourceTypeEnum")
@XmlEnum
public enum PbmProfileResourceTypeEnum {

    STORAGE;

    public String value() {
        return name();
    }

    public static PbmProfileResourceTypeEnum fromValue(String v) {
        return valueOf(v);
    }

}
