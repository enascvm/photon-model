
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DVPortStatusVmDirectPathGen2InactiveReasonNetwork.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="DVPortStatusVmDirectPathGen2InactiveReasonNetwork"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="portNptIncompatibleDvs"/&gt;
 *     &lt;enumeration value="portNptNoCompatibleNics"/&gt;
 *     &lt;enumeration value="portNptNoVirtualFunctionsAvailable"/&gt;
 *     &lt;enumeration value="portNptDisabledForPort"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "DVPortStatusVmDirectPathGen2InactiveReasonNetwork")
@XmlEnum
public enum DVPortStatusVmDirectPathGen2InactiveReasonNetwork {

    @XmlEnumValue("portNptIncompatibleDvs")
    PORT_NPT_INCOMPATIBLE_DVS("portNptIncompatibleDvs"),
    @XmlEnumValue("portNptNoCompatibleNics")
    PORT_NPT_NO_COMPATIBLE_NICS("portNptNoCompatibleNics"),
    @XmlEnumValue("portNptNoVirtualFunctionsAvailable")
    PORT_NPT_NO_VIRTUAL_FUNCTIONS_AVAILABLE("portNptNoVirtualFunctionsAvailable"),
    @XmlEnumValue("portNptDisabledForPort")
    PORT_NPT_DISABLED_FOR_PORT("portNptDisabledForPort");
    private final String value;

    DVPortStatusVmDirectPathGen2InactiveReasonNetwork(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static DVPortStatusVmDirectPathGen2InactiveReasonNetwork fromValue(String v) {
        for (DVPortStatusVmDirectPathGen2InactiveReasonNetwork c: DVPortStatusVmDirectPathGen2InactiveReasonNetwork.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
