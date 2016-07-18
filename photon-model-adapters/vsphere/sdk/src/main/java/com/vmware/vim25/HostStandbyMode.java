
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostStandbyMode.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="HostStandbyMode"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="entering"/&gt;
 *     &lt;enumeration value="exiting"/&gt;
 *     &lt;enumeration value="in"/&gt;
 *     &lt;enumeration value="none"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "HostStandbyMode")
@XmlEnum
public enum HostStandbyMode {

    @XmlEnumValue("entering")
    ENTERING("entering"),
    @XmlEnumValue("exiting")
    EXITING("exiting"),
    @XmlEnumValue("in")
    IN("in"),
    @XmlEnumValue("none")
    NONE("none");
    private final String value;

    HostStandbyMode(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static HostStandbyMode fromValue(String v) {
        for (HostStandbyMode c: HostStandbyMode.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
