
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualMachineAppHeartbeatStatusType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VirtualMachineAppHeartbeatStatusType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="appStatusGray"/&gt;
 *     &lt;enumeration value="appStatusGreen"/&gt;
 *     &lt;enumeration value="appStatusRed"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VirtualMachineAppHeartbeatStatusType")
@XmlEnum
public enum VirtualMachineAppHeartbeatStatusType {

    @XmlEnumValue("appStatusGray")
    APP_STATUS_GRAY("appStatusGray"),
    @XmlEnumValue("appStatusGreen")
    APP_STATUS_GREEN("appStatusGreen"),
    @XmlEnumValue("appStatusRed")
    APP_STATUS_RED("appStatusRed");
    private final String value;

    VirtualMachineAppHeartbeatStatusType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VirtualMachineAppHeartbeatStatusType fromValue(String v) {
        for (VirtualMachineAppHeartbeatStatusType c: VirtualMachineAppHeartbeatStatusType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
