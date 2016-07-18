
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualAppVAppState.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VirtualAppVAppState"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="started"/&gt;
 *     &lt;enumeration value="stopped"/&gt;
 *     &lt;enumeration value="starting"/&gt;
 *     &lt;enumeration value="stopping"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VirtualAppVAppState")
@XmlEnum
public enum VirtualAppVAppState {

    @XmlEnumValue("started")
    STARTED("started"),
    @XmlEnumValue("stopped")
    STOPPED("stopped"),
    @XmlEnumValue("starting")
    STARTING("starting"),
    @XmlEnumValue("stopping")
    STOPPING("stopping");
    private final String value;

    VirtualAppVAppState(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VirtualAppVAppState fromValue(String v) {
        for (VirtualAppVAppState c: VirtualAppVAppState.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
