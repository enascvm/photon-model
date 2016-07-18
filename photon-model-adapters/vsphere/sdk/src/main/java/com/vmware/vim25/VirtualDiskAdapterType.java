
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualDiskAdapterType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VirtualDiskAdapterType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="ide"/&gt;
 *     &lt;enumeration value="busLogic"/&gt;
 *     &lt;enumeration value="lsiLogic"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VirtualDiskAdapterType")
@XmlEnum
public enum VirtualDiskAdapterType {

    @XmlEnumValue("ide")
    IDE("ide"),
    @XmlEnumValue("busLogic")
    BUS_LOGIC("busLogic"),
    @XmlEnumValue("lsiLogic")
    LSI_LOGIC("lsiLogic");
    private final String value;

    VirtualDiskAdapterType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VirtualDiskAdapterType fromValue(String v) {
        for (VirtualDiskAdapterType c: VirtualDiskAdapterType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
