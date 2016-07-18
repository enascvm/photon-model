
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PortGroupConnecteeType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="PortGroupConnecteeType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="virtualMachine"/&gt;
 *     &lt;enumeration value="systemManagement"/&gt;
 *     &lt;enumeration value="host"/&gt;
 *     &lt;enumeration value="unknown"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "PortGroupConnecteeType")
@XmlEnum
public enum PortGroupConnecteeType {

    @XmlEnumValue("virtualMachine")
    VIRTUAL_MACHINE("virtualMachine"),
    @XmlEnumValue("systemManagement")
    SYSTEM_MANAGEMENT("systemManagement"),
    @XmlEnumValue("host")
    HOST("host"),
    @XmlEnumValue("unknown")
    UNKNOWN("unknown");
    private final String value;

    PortGroupConnecteeType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static PortGroupConnecteeType fromValue(String v) {
        for (PortGroupConnecteeType c: PortGroupConnecteeType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
