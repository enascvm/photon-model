
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for NetIpStackInfoPreference.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="NetIpStackInfoPreference"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="reserved"/&gt;
 *     &lt;enumeration value="low"/&gt;
 *     &lt;enumeration value="medium"/&gt;
 *     &lt;enumeration value="high"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "NetIpStackInfoPreference")
@XmlEnum
public enum NetIpStackInfoPreference {

    @XmlEnumValue("reserved")
    RESERVED("reserved"),
    @XmlEnumValue("low")
    LOW("low"),
    @XmlEnumValue("medium")
    MEDIUM("medium"),
    @XmlEnumValue("high")
    HIGH("high");
    private final String value;

    NetIpStackInfoPreference(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static NetIpStackInfoPreference fromValue(String v) {
        for (NetIpStackInfoPreference c: NetIpStackInfoPreference.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
