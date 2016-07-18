
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VmFaultToleranceIssue complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VmFaultToleranceIssue"&gt;
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
@XmlType(name = "VmFaultToleranceIssue")
@XmlSeeAlso({
    CannotChangeDrsBehaviorForFtSecondary.class,
    CannotChangeHaSettingsForFtSecondary.class,
    CannotComputeFTCompatibleHosts.class,
    FaultToleranceNotLicensed.class,
    FaultTolerancePrimaryPowerOnNotAttempted.class,
    FtIssuesOnHost.class,
    HostIncompatibleForFaultTolerance.class,
    IncompatibleHostForFtSecondary.class,
    InvalidOperationOnSecondaryVm.class,
    NoHostSuitableForFtSecondary.class,
    NotSupportedDeviceForFT.class,
    PowerOnFtSecondaryFailed.class,
    SecondaryVmAlreadyDisabled.class,
    SecondaryVmAlreadyEnabled.class,
    SecondaryVmAlreadyRegistered.class,
    SecondaryVmNotRegistered.class,
    VmFaultToleranceConfigIssue.class,
    VmFaultToleranceConfigIssueWrapper.class,
    VmFaultToleranceInvalidFileBacking.class,
    VmFaultToleranceOpIssuesList.class
})
public class VmFaultToleranceIssue
    extends VimFault
{


}
