
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualSerialPortEndPoint.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VirtualSerialPortEndPoint"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="client"/&gt;
 *     &lt;enumeration value="server"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VirtualSerialPortEndPoint")
@XmlEnum
public enum VirtualSerialPortEndPoint {

    @XmlEnumValue("client")
    CLIENT("client"),
    @XmlEnumValue("server")
    SERVER("server");
    private final String value;

    VirtualSerialPortEndPoint(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VirtualSerialPortEndPoint fromValue(String v) {
        for (VirtualSerialPortEndPoint c: VirtualSerialPortEndPoint.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
