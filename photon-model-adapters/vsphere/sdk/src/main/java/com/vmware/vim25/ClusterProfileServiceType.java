
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ClusterProfileServiceType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="ClusterProfileServiceType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="DRS"/&gt;
 *     &lt;enumeration value="HA"/&gt;
 *     &lt;enumeration value="DPM"/&gt;
 *     &lt;enumeration value="FT"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "ClusterProfileServiceType")
@XmlEnum
public enum ClusterProfileServiceType {

    DRS,
    HA,
    DPM,
    FT;

    public String value() {
        return name();
    }

    public static ClusterProfileServiceType fromValue(String v) {
        return valueOf(v);
    }

}
