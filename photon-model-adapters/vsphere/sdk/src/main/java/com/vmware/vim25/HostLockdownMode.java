
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostLockdownMode.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="HostLockdownMode"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="lockdownDisabled"/&gt;
 *     &lt;enumeration value="lockdownNormal"/&gt;
 *     &lt;enumeration value="lockdownStrict"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "HostLockdownMode")
@XmlEnum
public enum HostLockdownMode {

    @XmlEnumValue("lockdownDisabled")
    LOCKDOWN_DISABLED("lockdownDisabled"),
    @XmlEnumValue("lockdownNormal")
    LOCKDOWN_NORMAL("lockdownNormal"),
    @XmlEnumValue("lockdownStrict")
    LOCKDOWN_STRICT("lockdownStrict");
    private final String value;

    HostLockdownMode(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static HostLockdownMode fromValue(String v) {
        for (HostLockdownMode c: HostLockdownMode.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
