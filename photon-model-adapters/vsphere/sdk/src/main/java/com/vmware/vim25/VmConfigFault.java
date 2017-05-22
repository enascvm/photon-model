
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VmConfigFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VmConfigFault"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VimFault"&gt;
 *       &lt;sequence&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VmConfigFault")
@XmlSeeAlso({
    CannotDisableSnapshot.class,
    CannotUseNetwork.class,
    CpuHotPlugNotSupported.class,
    DeltaDiskFormatNotSupported.class,
    EightHostLimitViolated.class,
    FaultToleranceCannotEditMem.class,
    GenericVmConfigFault.class,
    InvalidFormat.class,
    LargeRDMNotSupportedOnDatastore.class,
    MemoryHotPlugNotSupported.class,
    NoCompatibleHardAffinityHost.class,
    NoCompatibleSoftAffinityHost.class,
    NumVirtualCpusIncompatible.class,
    OvfConsumerValidationFault.class,
    QuarantineModeFault.class,
    RDMNotSupportedOnDatastore.class,
    RuleViolation.class,
    SoftRuleVioCorrectionDisallowed.class,
    SoftRuleVioCorrectionImpact.class,
    UnsupportedDatastore.class,
    UnsupportedVmxLocation.class,
    VAppNotRunning.class,
    VAppPropertyFault.class,
    VFlashCacheHotConfigNotSupported.class,
    VFlashModuleNotSupported.class,
    CannotAccessVmComponent.class,
    VmConfigIncompatibleForFaultTolerance.class,
    VmConfigIncompatibleForRecordReplay.class,
    VmHostAffinityRuleViolation.class,
    InvalidVmConfig.class,
    VirtualHardwareCompatibilityIssue.class
})
public class VmConfigFault
    extends VimFault
{


}
