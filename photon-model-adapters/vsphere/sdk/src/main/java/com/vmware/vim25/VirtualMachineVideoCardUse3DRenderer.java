
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualMachineVideoCardUse3dRenderer.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VirtualMachineVideoCardUse3dRenderer"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="automatic"/&gt;
 *     &lt;enumeration value="software"/&gt;
 *     &lt;enumeration value="hardware"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VirtualMachineVideoCardUse3dRenderer")
@XmlEnum
public enum VirtualMachineVideoCardUse3DRenderer {

    @XmlEnumValue("automatic")
    AUTOMATIC("automatic"),
    @XmlEnumValue("software")
    SOFTWARE("software"),
    @XmlEnumValue("hardware")
    HARDWARE("hardware");
    private final String value;

    VirtualMachineVideoCardUse3DRenderer(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VirtualMachineVideoCardUse3DRenderer fromValue(String v) {
        for (VirtualMachineVideoCardUse3DRenderer c: VirtualMachineVideoCardUse3DRenderer.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
