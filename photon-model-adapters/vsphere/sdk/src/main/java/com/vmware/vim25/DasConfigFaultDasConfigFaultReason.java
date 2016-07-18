
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DasConfigFaultDasConfigFaultReason.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="DasConfigFaultDasConfigFaultReason"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="HostNetworkMisconfiguration"/&gt;
 *     &lt;enumeration value="HostMisconfiguration"/&gt;
 *     &lt;enumeration value="InsufficientPrivileges"/&gt;
 *     &lt;enumeration value="NoPrimaryAgentAvailable"/&gt;
 *     &lt;enumeration value="Other"/&gt;
 *     &lt;enumeration value="NoDatastoresConfigured"/&gt;
 *     &lt;enumeration value="CreateConfigVvolFailed"/&gt;
 *     &lt;enumeration value="VSanNotSupportedOnHost"/&gt;
 *     &lt;enumeration value="DasNetworkMisconfiguration"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "DasConfigFaultDasConfigFaultReason")
@XmlEnum
public enum DasConfigFaultDasConfigFaultReason {

    @XmlEnumValue("HostNetworkMisconfiguration")
    HOST_NETWORK_MISCONFIGURATION("HostNetworkMisconfiguration"),
    @XmlEnumValue("HostMisconfiguration")
    HOST_MISCONFIGURATION("HostMisconfiguration"),
    @XmlEnumValue("InsufficientPrivileges")
    INSUFFICIENT_PRIVILEGES("InsufficientPrivileges"),
    @XmlEnumValue("NoPrimaryAgentAvailable")
    NO_PRIMARY_AGENT_AVAILABLE("NoPrimaryAgentAvailable"),
    @XmlEnumValue("Other")
    OTHER("Other"),
    @XmlEnumValue("NoDatastoresConfigured")
    NO_DATASTORES_CONFIGURED("NoDatastoresConfigured"),
    @XmlEnumValue("CreateConfigVvolFailed")
    CREATE_CONFIG_VVOL_FAILED("CreateConfigVvolFailed"),
    @XmlEnumValue("VSanNotSupportedOnHost")
    V_SAN_NOT_SUPPORTED_ON_HOST("VSanNotSupportedOnHost"),
    @XmlEnumValue("DasNetworkMisconfiguration")
    DAS_NETWORK_MISCONFIGURATION("DasNetworkMisconfiguration");
    private final String value;

    DasConfigFaultDasConfigFaultReason(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static DasConfigFaultDasConfigFaultReason fromValue(String v) {
        for (DasConfigFaultDasConfigFaultReason c: DasConfigFaultDasConfigFaultReason.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
