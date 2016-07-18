
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualMachineForkConfigInfoChildType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VirtualMachineForkConfigInfoChildType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="none"/&gt;
 *     &lt;enumeration value="persistent"/&gt;
 *     &lt;enumeration value="nonpersistent"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VirtualMachineForkConfigInfoChildType")
@XmlEnum
public enum VirtualMachineForkConfigInfoChildType {

    @XmlEnumValue("none")
    NONE("none"),
    @XmlEnumValue("persistent")
    PERSISTENT("persistent"),
    @XmlEnumValue("nonpersistent")
    NONPERSISTENT("nonpersistent");
    private final String value;

    VirtualMachineForkConfigInfoChildType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VirtualMachineForkConfigInfoChildType fromValue(String v) {
        for (VirtualMachineForkConfigInfoChildType c: VirtualMachineForkConfigInfoChildType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
