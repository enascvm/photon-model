
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PbmProfileCategoryEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="PbmProfileCategoryEnum"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="REQUIREMENT"/&gt;
 *     &lt;enumeration value="RESOURCE"/&gt;
 *     &lt;enumeration value="DATA_SERVICE_POLICY"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "PbmProfileCategoryEnum")
@XmlEnum
public enum PbmProfileCategoryEnum {

    REQUIREMENT,
    RESOURCE,
    DATA_SERVICE_POLICY;

    public String value() {
        return name();
    }

    public static PbmProfileCategoryEnum fromValue(String v) {
        return valueOf(v);
    }

}
