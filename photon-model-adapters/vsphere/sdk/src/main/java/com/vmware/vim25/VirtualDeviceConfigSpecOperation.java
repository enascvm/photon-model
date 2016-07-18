
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualDeviceConfigSpecOperation.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VirtualDeviceConfigSpecOperation"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="add"/&gt;
 *     &lt;enumeration value="remove"/&gt;
 *     &lt;enumeration value="edit"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VirtualDeviceConfigSpecOperation")
@XmlEnum
public enum VirtualDeviceConfigSpecOperation {

    @XmlEnumValue("add")
    ADD("add"),
    @XmlEnumValue("remove")
    REMOVE("remove"),
    @XmlEnumValue("edit")
    EDIT("edit");
    private final String value;

    VirtualDeviceConfigSpecOperation(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VirtualDeviceConfigSpecOperation fromValue(String v) {
        for (VirtualDeviceConfigSpecOperation c: VirtualDeviceConfigSpecOperation.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
