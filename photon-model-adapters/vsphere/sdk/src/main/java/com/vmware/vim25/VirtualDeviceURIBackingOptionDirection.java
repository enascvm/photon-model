
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualDeviceURIBackingOptionDirection.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VirtualDeviceURIBackingOptionDirection"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="server"/&gt;
 *     &lt;enumeration value="client"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VirtualDeviceURIBackingOptionDirection")
@XmlEnum
public enum VirtualDeviceURIBackingOptionDirection {

    @XmlEnumValue("server")
    SERVER("server"),
    @XmlEnumValue("client")
    CLIENT("client");
    private final String value;

    VirtualDeviceURIBackingOptionDirection(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VirtualDeviceURIBackingOptionDirection fromValue(String v) {
        for (VirtualDeviceURIBackingOptionDirection c: VirtualDeviceURIBackingOptionDirection.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
