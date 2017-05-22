
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualMachineToolsInstallType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VirtualMachineToolsInstallType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="guestToolsTypeUnknown"/&gt;
 *     &lt;enumeration value="guestToolsTypeMSI"/&gt;
 *     &lt;enumeration value="guestToolsTypeTar"/&gt;
 *     &lt;enumeration value="guestToolsTypeOSP"/&gt;
 *     &lt;enumeration value="guestToolsTypeOpenVMTools"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VirtualMachineToolsInstallType")
@XmlEnum
public enum VirtualMachineToolsInstallType {

    @XmlEnumValue("guestToolsTypeUnknown")
    GUEST_TOOLS_TYPE_UNKNOWN("guestToolsTypeUnknown"),
    @XmlEnumValue("guestToolsTypeMSI")
    GUEST_TOOLS_TYPE_MSI("guestToolsTypeMSI"),
    @XmlEnumValue("guestToolsTypeTar")
    GUEST_TOOLS_TYPE_TAR("guestToolsTypeTar"),
    @XmlEnumValue("guestToolsTypeOSP")
    GUEST_TOOLS_TYPE_OSP("guestToolsTypeOSP"),
    @XmlEnumValue("guestToolsTypeOpenVMTools")
    GUEST_TOOLS_TYPE_OPEN_VM_TOOLS("guestToolsTypeOpenVMTools");
    private final String value;

    VirtualMachineToolsInstallType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VirtualMachineToolsInstallType fromValue(String v) {
        for (VirtualMachineToolsInstallType c: VirtualMachineToolsInstallType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
