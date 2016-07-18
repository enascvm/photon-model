
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ReplicationVmFaultReasonForFault.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="ReplicationVmFaultReasonForFault"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="notConfigured"/&gt;
 *     &lt;enumeration value="poweredOff"/&gt;
 *     &lt;enumeration value="suspended"/&gt;
 *     &lt;enumeration value="poweredOn"/&gt;
 *     &lt;enumeration value="offlineReplicating"/&gt;
 *     &lt;enumeration value="invalidState"/&gt;
 *     &lt;enumeration value="invalidInstanceId"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "ReplicationVmFaultReasonForFault")
@XmlEnum
public enum ReplicationVmFaultReasonForFault {

    @XmlEnumValue("notConfigured")
    NOT_CONFIGURED("notConfigured"),
    @XmlEnumValue("poweredOff")
    POWERED_OFF("poweredOff"),
    @XmlEnumValue("suspended")
    SUSPENDED("suspended"),
    @XmlEnumValue("poweredOn")
    POWERED_ON("poweredOn"),
    @XmlEnumValue("offlineReplicating")
    OFFLINE_REPLICATING("offlineReplicating"),
    @XmlEnumValue("invalidState")
    INVALID_STATE("invalidState"),
    @XmlEnumValue("invalidInstanceId")
    INVALID_INSTANCE_ID("invalidInstanceId");
    private final String value;

    ReplicationVmFaultReasonForFault(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ReplicationVmFaultReasonForFault fromValue(String v) {
        for (ReplicationVmFaultReasonForFault c: ReplicationVmFaultReasonForFault.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
