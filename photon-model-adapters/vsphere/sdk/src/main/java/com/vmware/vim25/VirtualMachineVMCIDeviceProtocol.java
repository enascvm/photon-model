
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualMachineVMCIDeviceProtocol.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VirtualMachineVMCIDeviceProtocol"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="hypervisor"/&gt;
 *     &lt;enumeration value="doorbell"/&gt;
 *     &lt;enumeration value="queuepair"/&gt;
 *     &lt;enumeration value="datagram"/&gt;
 *     &lt;enumeration value="stream"/&gt;
 *     &lt;enumeration value="anyProtocol"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VirtualMachineVMCIDeviceProtocol")
@XmlEnum
public enum VirtualMachineVMCIDeviceProtocol {

    @XmlEnumValue("hypervisor")
    HYPERVISOR("hypervisor"),
    @XmlEnumValue("doorbell")
    DOORBELL("doorbell"),
    @XmlEnumValue("queuepair")
    QUEUEPAIR("queuepair"),
    @XmlEnumValue("datagram")
    DATAGRAM("datagram"),
    @XmlEnumValue("stream")
    STREAM("stream"),
    @XmlEnumValue("anyProtocol")
    ANY_PROTOCOL("anyProtocol");
    private final String value;

    VirtualMachineVMCIDeviceProtocol(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VirtualMachineVMCIDeviceProtocol fromValue(String v) {
        for (VirtualMachineVMCIDeviceProtocol c: VirtualMachineVMCIDeviceProtocol.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
