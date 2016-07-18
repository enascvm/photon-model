
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualMachineNeedSecondaryReason.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VirtualMachineNeedSecondaryReason"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="initializing"/&gt;
 *     &lt;enumeration value="divergence"/&gt;
 *     &lt;enumeration value="lostConnection"/&gt;
 *     &lt;enumeration value="partialHardwareFailure"/&gt;
 *     &lt;enumeration value="userAction"/&gt;
 *     &lt;enumeration value="checkpointError"/&gt;
 *     &lt;enumeration value="other"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VirtualMachineNeedSecondaryReason")
@XmlEnum
public enum VirtualMachineNeedSecondaryReason {

    @XmlEnumValue("initializing")
    INITIALIZING("initializing"),
    @XmlEnumValue("divergence")
    DIVERGENCE("divergence"),
    @XmlEnumValue("lostConnection")
    LOST_CONNECTION("lostConnection"),
    @XmlEnumValue("partialHardwareFailure")
    PARTIAL_HARDWARE_FAILURE("partialHardwareFailure"),
    @XmlEnumValue("userAction")
    USER_ACTION("userAction"),
    @XmlEnumValue("checkpointError")
    CHECKPOINT_ERROR("checkpointError"),
    @XmlEnumValue("other")
    OTHER("other");
    private final String value;

    VirtualMachineNeedSecondaryReason(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VirtualMachineNeedSecondaryReason fromValue(String v) {
        for (VirtualMachineNeedSecondaryReason c: VirtualMachineNeedSecondaryReason.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
