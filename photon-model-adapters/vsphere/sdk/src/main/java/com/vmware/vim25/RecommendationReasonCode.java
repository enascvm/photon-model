
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for RecommendationReasonCode.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="RecommendationReasonCode"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="fairnessCpuAvg"/&gt;
 *     &lt;enumeration value="fairnessMemAvg"/&gt;
 *     &lt;enumeration value="jointAffin"/&gt;
 *     &lt;enumeration value="antiAffin"/&gt;
 *     &lt;enumeration value="hostMaint"/&gt;
 *     &lt;enumeration value="enterStandby"/&gt;
 *     &lt;enumeration value="reservationCpu"/&gt;
 *     &lt;enumeration value="reservationMem"/&gt;
 *     &lt;enumeration value="powerOnVm"/&gt;
 *     &lt;enumeration value="powerSaving"/&gt;
 *     &lt;enumeration value="increaseCapacity"/&gt;
 *     &lt;enumeration value="checkResource"/&gt;
 *     &lt;enumeration value="unreservedCapacity"/&gt;
 *     &lt;enumeration value="vmHostHardAffinity"/&gt;
 *     &lt;enumeration value="vmHostSoftAffinity"/&gt;
 *     &lt;enumeration value="balanceDatastoreSpaceUsage"/&gt;
 *     &lt;enumeration value="balanceDatastoreIOLoad"/&gt;
 *     &lt;enumeration value="balanceDatastoreIOPSReservation"/&gt;
 *     &lt;enumeration value="datastoreMaint"/&gt;
 *     &lt;enumeration value="virtualDiskJointAffin"/&gt;
 *     &lt;enumeration value="virtualDiskAntiAffin"/&gt;
 *     &lt;enumeration value="datastoreSpaceOutage"/&gt;
 *     &lt;enumeration value="storagePlacement"/&gt;
 *     &lt;enumeration value="iolbDisabledInternal"/&gt;
 *     &lt;enumeration value="xvmotionPlacement"/&gt;
 *     &lt;enumeration value="networkBandwidthReservation"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "RecommendationReasonCode")
@XmlEnum
public enum RecommendationReasonCode {

    @XmlEnumValue("fairnessCpuAvg")
    FAIRNESS_CPU_AVG("fairnessCpuAvg"),
    @XmlEnumValue("fairnessMemAvg")
    FAIRNESS_MEM_AVG("fairnessMemAvg"),
    @XmlEnumValue("jointAffin")
    JOINT_AFFIN("jointAffin"),
    @XmlEnumValue("antiAffin")
    ANTI_AFFIN("antiAffin"),
    @XmlEnumValue("hostMaint")
    HOST_MAINT("hostMaint"),
    @XmlEnumValue("enterStandby")
    ENTER_STANDBY("enterStandby"),
    @XmlEnumValue("reservationCpu")
    RESERVATION_CPU("reservationCpu"),
    @XmlEnumValue("reservationMem")
    RESERVATION_MEM("reservationMem"),
    @XmlEnumValue("powerOnVm")
    POWER_ON_VM("powerOnVm"),
    @XmlEnumValue("powerSaving")
    POWER_SAVING("powerSaving"),
    @XmlEnumValue("increaseCapacity")
    INCREASE_CAPACITY("increaseCapacity"),
    @XmlEnumValue("checkResource")
    CHECK_RESOURCE("checkResource"),
    @XmlEnumValue("unreservedCapacity")
    UNRESERVED_CAPACITY("unreservedCapacity"),
    @XmlEnumValue("vmHostHardAffinity")
    VM_HOST_HARD_AFFINITY("vmHostHardAffinity"),
    @XmlEnumValue("vmHostSoftAffinity")
    VM_HOST_SOFT_AFFINITY("vmHostSoftAffinity"),
    @XmlEnumValue("balanceDatastoreSpaceUsage")
    BALANCE_DATASTORE_SPACE_USAGE("balanceDatastoreSpaceUsage"),
    @XmlEnumValue("balanceDatastoreIOLoad")
    BALANCE_DATASTORE_IO_LOAD("balanceDatastoreIOLoad"),
    @XmlEnumValue("balanceDatastoreIOPSReservation")
    BALANCE_DATASTORE_IOPS_RESERVATION("balanceDatastoreIOPSReservation"),
    @XmlEnumValue("datastoreMaint")
    DATASTORE_MAINT("datastoreMaint"),
    @XmlEnumValue("virtualDiskJointAffin")
    VIRTUAL_DISK_JOINT_AFFIN("virtualDiskJointAffin"),
    @XmlEnumValue("virtualDiskAntiAffin")
    VIRTUAL_DISK_ANTI_AFFIN("virtualDiskAntiAffin"),
    @XmlEnumValue("datastoreSpaceOutage")
    DATASTORE_SPACE_OUTAGE("datastoreSpaceOutage"),
    @XmlEnumValue("storagePlacement")
    STORAGE_PLACEMENT("storagePlacement"),
    @XmlEnumValue("iolbDisabledInternal")
    IOLB_DISABLED_INTERNAL("iolbDisabledInternal"),
    @XmlEnumValue("xvmotionPlacement")
    XVMOTION_PLACEMENT("xvmotionPlacement"),
    @XmlEnumValue("networkBandwidthReservation")
    NETWORK_BANDWIDTH_RESERVATION("networkBandwidthReservation");
    private final String value;

    RecommendationReasonCode(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static RecommendationReasonCode fromValue(String v) {
        for (RecommendationReasonCode c: RecommendationReasonCode.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
