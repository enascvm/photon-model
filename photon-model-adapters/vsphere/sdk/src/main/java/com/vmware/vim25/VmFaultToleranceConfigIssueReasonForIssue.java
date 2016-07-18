
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VmFaultToleranceConfigIssueReasonForIssue.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VmFaultToleranceConfigIssueReasonForIssue"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="haNotEnabled"/&gt;
 *     &lt;enumeration value="moreThanOneSecondary"/&gt;
 *     &lt;enumeration value="recordReplayNotSupported"/&gt;
 *     &lt;enumeration value="replayNotSupported"/&gt;
 *     &lt;enumeration value="templateVm"/&gt;
 *     &lt;enumeration value="multipleVCPU"/&gt;
 *     &lt;enumeration value="hostInactive"/&gt;
 *     &lt;enumeration value="ftUnsupportedHardware"/&gt;
 *     &lt;enumeration value="ftUnsupportedProduct"/&gt;
 *     &lt;enumeration value="missingVMotionNic"/&gt;
 *     &lt;enumeration value="missingFTLoggingNic"/&gt;
 *     &lt;enumeration value="thinDisk"/&gt;
 *     &lt;enumeration value="verifySSLCertificateFlagNotSet"/&gt;
 *     &lt;enumeration value="hasSnapshots"/&gt;
 *     &lt;enumeration value="noConfig"/&gt;
 *     &lt;enumeration value="ftSecondaryVm"/&gt;
 *     &lt;enumeration value="hasLocalDisk"/&gt;
 *     &lt;enumeration value="esxAgentVm"/&gt;
 *     &lt;enumeration value="video3dEnabled"/&gt;
 *     &lt;enumeration value="hasUnsupportedDisk"/&gt;
 *     &lt;enumeration value="insufficientBandwidth"/&gt;
 *     &lt;enumeration value="hasNestedHVConfiguration"/&gt;
 *     &lt;enumeration value="hasVFlashConfiguration"/&gt;
 *     &lt;enumeration value="unsupportedProduct"/&gt;
 *     &lt;enumeration value="cpuHvUnsupported"/&gt;
 *     &lt;enumeration value="cpuHwmmuUnsupported"/&gt;
 *     &lt;enumeration value="cpuHvDisabled"/&gt;
 *     &lt;enumeration value="hasEFIFirmware"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VmFaultToleranceConfigIssueReasonForIssue")
@XmlEnum
public enum VmFaultToleranceConfigIssueReasonForIssue {

    @XmlEnumValue("haNotEnabled")
    HA_NOT_ENABLED("haNotEnabled"),
    @XmlEnumValue("moreThanOneSecondary")
    MORE_THAN_ONE_SECONDARY("moreThanOneSecondary"),
    @XmlEnumValue("recordReplayNotSupported")
    RECORD_REPLAY_NOT_SUPPORTED("recordReplayNotSupported"),
    @XmlEnumValue("replayNotSupported")
    REPLAY_NOT_SUPPORTED("replayNotSupported"),
    @XmlEnumValue("templateVm")
    TEMPLATE_VM("templateVm"),
    @XmlEnumValue("multipleVCPU")
    MULTIPLE_VCPU("multipleVCPU"),
    @XmlEnumValue("hostInactive")
    HOST_INACTIVE("hostInactive"),
    @XmlEnumValue("ftUnsupportedHardware")
    FT_UNSUPPORTED_HARDWARE("ftUnsupportedHardware"),
    @XmlEnumValue("ftUnsupportedProduct")
    FT_UNSUPPORTED_PRODUCT("ftUnsupportedProduct"),
    @XmlEnumValue("missingVMotionNic")
    MISSING_V_MOTION_NIC("missingVMotionNic"),
    @XmlEnumValue("missingFTLoggingNic")
    MISSING_FT_LOGGING_NIC("missingFTLoggingNic"),
    @XmlEnumValue("thinDisk")
    THIN_DISK("thinDisk"),
    @XmlEnumValue("verifySSLCertificateFlagNotSet")
    VERIFY_SSL_CERTIFICATE_FLAG_NOT_SET("verifySSLCertificateFlagNotSet"),
    @XmlEnumValue("hasSnapshots")
    HAS_SNAPSHOTS("hasSnapshots"),
    @XmlEnumValue("noConfig")
    NO_CONFIG("noConfig"),
    @XmlEnumValue("ftSecondaryVm")
    FT_SECONDARY_VM("ftSecondaryVm"),
    @XmlEnumValue("hasLocalDisk")
    HAS_LOCAL_DISK("hasLocalDisk"),
    @XmlEnumValue("esxAgentVm")
    ESX_AGENT_VM("esxAgentVm"),
    @XmlEnumValue("video3dEnabled")
    VIDEO_3_D_ENABLED("video3dEnabled"),
    @XmlEnumValue("hasUnsupportedDisk")
    HAS_UNSUPPORTED_DISK("hasUnsupportedDisk"),
    @XmlEnumValue("insufficientBandwidth")
    INSUFFICIENT_BANDWIDTH("insufficientBandwidth"),
    @XmlEnumValue("hasNestedHVConfiguration")
    HAS_NESTED_HV_CONFIGURATION("hasNestedHVConfiguration"),
    @XmlEnumValue("hasVFlashConfiguration")
    HAS_V_FLASH_CONFIGURATION("hasVFlashConfiguration"),
    @XmlEnumValue("unsupportedProduct")
    UNSUPPORTED_PRODUCT("unsupportedProduct"),
    @XmlEnumValue("cpuHvUnsupported")
    CPU_HV_UNSUPPORTED("cpuHvUnsupported"),
    @XmlEnumValue("cpuHwmmuUnsupported")
    CPU_HWMMU_UNSUPPORTED("cpuHwmmuUnsupported"),
    @XmlEnumValue("cpuHvDisabled")
    CPU_HV_DISABLED("cpuHvDisabled"),
    @XmlEnumValue("hasEFIFirmware")
    HAS_EFI_FIRMWARE("hasEFIFirmware");
    private final String value;

    VmFaultToleranceConfigIssueReasonForIssue(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VmFaultToleranceConfigIssueReasonForIssue fromValue(String v) {
        for (VmFaultToleranceConfigIssueReasonForIssue c: VmFaultToleranceConfigIssueReasonForIssue.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
