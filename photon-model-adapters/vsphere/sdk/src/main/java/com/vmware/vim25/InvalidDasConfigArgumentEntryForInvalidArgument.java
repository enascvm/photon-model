
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for InvalidDasConfigArgumentEntryForInvalidArgument.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="InvalidDasConfigArgumentEntryForInvalidArgument"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="admissionControl"/&gt;
 *     &lt;enumeration value="userHeartbeatDs"/&gt;
 *     &lt;enumeration value="vmConfig"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "InvalidDasConfigArgumentEntryForInvalidArgument")
@XmlEnum
public enum InvalidDasConfigArgumentEntryForInvalidArgument {

    @XmlEnumValue("admissionControl")
    ADMISSION_CONTROL("admissionControl"),
    @XmlEnumValue("userHeartbeatDs")
    USER_HEARTBEAT_DS("userHeartbeatDs"),
    @XmlEnumValue("vmConfig")
    VM_CONFIG("vmConfig");
    private final String value;

    InvalidDasConfigArgumentEntryForInvalidArgument(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static InvalidDasConfigArgumentEntryForInvalidArgument fromValue(String v) {
        for (InvalidDasConfigArgumentEntryForInvalidArgument c: InvalidDasConfigArgumentEntryForInvalidArgument.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
