
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for MigrationFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="MigrationFault"&gt;
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
@XmlType(name = "MigrationFault")
@XmlSeeAlso({
    AffinityConfigured.class,
    CannotModifyConfigCpuRequirements.class,
    CannotMoveVmWithDeltaDisk.class,
    CannotMoveVmWithNativeDeltaDisk.class,
    CloneFromSnapshotNotSupported.class,
    DatacenterMismatch.class,
    DisallowedMigrationDeviceAttached.class,
    DiskMoveTypeNotSupported.class,
    FaultToleranceAntiAffinityViolated.class,
    FaultToleranceNeedsThickDisk.class,
    FaultToleranceNotSameBuild.class,
    HAErrorsAtDest.class,
    IncompatibleDefaultDevice.class,
    LargeRDMConversionNotSupported.class,
    MaintenanceModeFileMove.class,
    MigrationDisabled.class,
    MigrationNotReady.class,
    MismatchedNetworkPolicies.class,
    MismatchedVMotionNetworkNames.class,
    NetworksMayNotBeTheSame.class,
    NoGuestHeartbeat.class,
    RDMConversionNotSupported.class,
    RDMNotPreserved.class,
    ReadOnlyDisksWithLegacyDestination.class,
    SnapshotCopyNotSupported.class,
    SnapshotRevertIssue.class,
    SuspendedRelocateNotSupported.class,
    TooManyDisksOnLegacyHost.class,
    ToolsInstallationInProgress.class,
    UncommittedUndoableDisk.class,
    MigrationFeatureNotSupported.class,
    VMotionInterfaceIssue.class,
    VMotionProtocolIncompatible.class,
    WillLoseHAProtection.class,
    WillModifyConfigCpuRequirements.class,
    WillResetSnapshotDirectory.class
})
public class MigrationFault
    extends VimFault
{


}
