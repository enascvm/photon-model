
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DistributedVirtualSwitchHostInfrastructureTrafficClass.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="DistributedVirtualSwitchHostInfrastructureTrafficClass"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="management"/&gt;
 *     &lt;enumeration value="faultTolerance"/&gt;
 *     &lt;enumeration value="vmotion"/&gt;
 *     &lt;enumeration value="virtualMachine"/&gt;
 *     &lt;enumeration value="iSCSI"/&gt;
 *     &lt;enumeration value="nfs"/&gt;
 *     &lt;enumeration value="hbr"/&gt;
 *     &lt;enumeration value="vsan"/&gt;
 *     &lt;enumeration value="vdp"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "DistributedVirtualSwitchHostInfrastructureTrafficClass")
@XmlEnum
public enum DistributedVirtualSwitchHostInfrastructureTrafficClass {

    @XmlEnumValue("management")
    MANAGEMENT("management"),
    @XmlEnumValue("faultTolerance")
    FAULT_TOLERANCE("faultTolerance"),
    @XmlEnumValue("vmotion")
    VMOTION("vmotion"),
    @XmlEnumValue("virtualMachine")
    VIRTUAL_MACHINE("virtualMachine"),
    @XmlEnumValue("iSCSI")
    I_SCSI("iSCSI"),
    @XmlEnumValue("nfs")
    NFS("nfs"),
    @XmlEnumValue("hbr")
    HBR("hbr"),
    @XmlEnumValue("vsan")
    VSAN("vsan"),
    @XmlEnumValue("vdp")
    VDP("vdp");
    private final String value;

    DistributedVirtualSwitchHostInfrastructureTrafficClass(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static DistributedVirtualSwitchHostInfrastructureTrafficClass fromValue(String v) {
        for (DistributedVirtualSwitchHostInfrastructureTrafficClass c: DistributedVirtualSwitchHostInfrastructureTrafficClass.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
