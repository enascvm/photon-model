
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostVirtualNicManagerNicType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="HostVirtualNicManagerNicType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="vmotion"/&gt;
 *     &lt;enumeration value="faultToleranceLogging"/&gt;
 *     &lt;enumeration value="vSphereReplication"/&gt;
 *     &lt;enumeration value="vSphereReplicationNFC"/&gt;
 *     &lt;enumeration value="management"/&gt;
 *     &lt;enumeration value="vsan"/&gt;
 *     &lt;enumeration value="vSphereProvisioning"/&gt;
 *     &lt;enumeration value="vsanWitness"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "HostVirtualNicManagerNicType")
@XmlEnum
public enum HostVirtualNicManagerNicType {

    @XmlEnumValue("vmotion")
    VMOTION("vmotion"),
    @XmlEnumValue("faultToleranceLogging")
    FAULT_TOLERANCE_LOGGING("faultToleranceLogging"),
    @XmlEnumValue("vSphereReplication")
    V_SPHERE_REPLICATION("vSphereReplication"),
    @XmlEnumValue("vSphereReplicationNFC")
    V_SPHERE_REPLICATION_NFC("vSphereReplicationNFC"),
    @XmlEnumValue("management")
    MANAGEMENT("management"),
    @XmlEnumValue("vsan")
    VSAN("vsan"),
    @XmlEnumValue("vSphereProvisioning")
    V_SPHERE_PROVISIONING("vSphereProvisioning"),
    @XmlEnumValue("vsanWitness")
    VSAN_WITNESS("vsanWitness");
    private final String value;

    HostVirtualNicManagerNicType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static HostVirtualNicManagerNicType fromValue(String v) {
        for (HostVirtualNicManagerNicType c: HostVirtualNicManagerNicType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
