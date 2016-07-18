
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualMachineMetadataManagerVmMetadataOp.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VirtualMachineMetadataManagerVmMetadataOp"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="Update"/&gt;
 *     &lt;enumeration value="Remove"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VirtualMachineMetadataManagerVmMetadataOp")
@XmlEnum
public enum VirtualMachineMetadataManagerVmMetadataOp {

    @XmlEnumValue("Update")
    UPDATE("Update"),
    @XmlEnumValue("Remove")
    REMOVE("Remove");
    private final String value;

    VirtualMachineMetadataManagerVmMetadataOp(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VirtualMachineMetadataManagerVmMetadataOp fromValue(String v) {
        for (VirtualMachineMetadataManagerVmMetadataOp c: VirtualMachineMetadataManagerVmMetadataOp.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
