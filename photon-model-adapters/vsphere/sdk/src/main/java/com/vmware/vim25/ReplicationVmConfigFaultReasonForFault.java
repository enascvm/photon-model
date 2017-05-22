
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ReplicationVmConfigFaultReasonForFault.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="ReplicationVmConfigFaultReasonForFault"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="incompatibleHwVersion"/&gt;
 *     &lt;enumeration value="invalidVmReplicationId"/&gt;
 *     &lt;enumeration value="invalidGenerationNumber"/&gt;
 *     &lt;enumeration value="outOfBoundsRpoValue"/&gt;
 *     &lt;enumeration value="invalidDestinationIpAddress"/&gt;
 *     &lt;enumeration value="invalidDestinationPort"/&gt;
 *     &lt;enumeration value="invalidExtraVmOptions"/&gt;
 *     &lt;enumeration value="staleGenerationNumber"/&gt;
 *     &lt;enumeration value="reconfigureVmReplicationIdNotAllowed"/&gt;
 *     &lt;enumeration value="cannotRetrieveVmReplicationConfiguration"/&gt;
 *     &lt;enumeration value="replicationAlreadyEnabled"/&gt;
 *     &lt;enumeration value="invalidPriorConfiguration"/&gt;
 *     &lt;enumeration value="replicationNotEnabled"/&gt;
 *     &lt;enumeration value="replicationConfigurationFailed"/&gt;
 *     &lt;enumeration value="encryptedVm"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "ReplicationVmConfigFaultReasonForFault")
@XmlEnum
public enum ReplicationVmConfigFaultReasonForFault {

    @XmlEnumValue("incompatibleHwVersion")
    INCOMPATIBLE_HW_VERSION("incompatibleHwVersion"),
    @XmlEnumValue("invalidVmReplicationId")
    INVALID_VM_REPLICATION_ID("invalidVmReplicationId"),
    @XmlEnumValue("invalidGenerationNumber")
    INVALID_GENERATION_NUMBER("invalidGenerationNumber"),
    @XmlEnumValue("outOfBoundsRpoValue")
    OUT_OF_BOUNDS_RPO_VALUE("outOfBoundsRpoValue"),
    @XmlEnumValue("invalidDestinationIpAddress")
    INVALID_DESTINATION_IP_ADDRESS("invalidDestinationIpAddress"),
    @XmlEnumValue("invalidDestinationPort")
    INVALID_DESTINATION_PORT("invalidDestinationPort"),
    @XmlEnumValue("invalidExtraVmOptions")
    INVALID_EXTRA_VM_OPTIONS("invalidExtraVmOptions"),
    @XmlEnumValue("staleGenerationNumber")
    STALE_GENERATION_NUMBER("staleGenerationNumber"),
    @XmlEnumValue("reconfigureVmReplicationIdNotAllowed")
    RECONFIGURE_VM_REPLICATION_ID_NOT_ALLOWED("reconfigureVmReplicationIdNotAllowed"),
    @XmlEnumValue("cannotRetrieveVmReplicationConfiguration")
    CANNOT_RETRIEVE_VM_REPLICATION_CONFIGURATION("cannotRetrieveVmReplicationConfiguration"),
    @XmlEnumValue("replicationAlreadyEnabled")
    REPLICATION_ALREADY_ENABLED("replicationAlreadyEnabled"),
    @XmlEnumValue("invalidPriorConfiguration")
    INVALID_PRIOR_CONFIGURATION("invalidPriorConfiguration"),
    @XmlEnumValue("replicationNotEnabled")
    REPLICATION_NOT_ENABLED("replicationNotEnabled"),
    @XmlEnumValue("replicationConfigurationFailed")
    REPLICATION_CONFIGURATION_FAILED("replicationConfigurationFailed"),
    @XmlEnumValue("encryptedVm")
    ENCRYPTED_VM("encryptedVm");
    private final String value;

    ReplicationVmConfigFaultReasonForFault(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ReplicationVmConfigFaultReasonForFault fromValue(String v) {
        for (ReplicationVmConfigFaultReasonForFault c: ReplicationVmConfigFaultReasonForFault.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
