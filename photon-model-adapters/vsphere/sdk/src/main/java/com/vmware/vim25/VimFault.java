
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VimFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VimFault"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}MethodFault"&gt;
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
@XmlType(name = "VimFault")
@XmlSeeAlso({
    AlreadyExists.class,
    AlreadyUpgraded.class,
    AnswerFileUpdateFailed.class,
    AuthMinimumAdminPermission.class,
    CannotAccessLocalSource.class,
    CannotDisconnectHostWithFaultToleranceVm.class,
    CannotEnableVmcpForCluster.class,
    CannotMoveFaultToleranceVm.class,
    CannotMoveHostWithFaultToleranceVm.class,
    CannotPlaceWithoutPrerequisiteMoves.class,
    ConcurrentAccess.class,
    DasConfigFault.class,
    DrsDisabledOnVm.class,
    DuplicateName.class,
    ExtendedFault.class,
    FaultToleranceVmNotDasProtected.class,
    FcoeFault.class,
    GenericDrsFault.class,
    EVCConfigFault.class,
    HostHasComponentFailure.class,
    HostIncompatibleForRecordReplay.class,
    IORMNotSupportedHostOnDatastore.class,
    InaccessibleVFlashSource.class,
    InsufficientStorageIops.class,
    InvalidAffinitySettingFault.class,
    InvalidBmcRole.class,
    InvalidEvent.class,
    InvalidIpmiLoginInfo.class,
    InvalidIpmiMacAddress.class,
    InvalidLicense.class,
    InvalidLocale.class,
    InvalidLogin.class,
    InvalidName.class,
    InvalidPrivilege.class,
    IscsiFault.class,
    LicenseEntityNotFound.class,
    LicenseServerUnavailable.class,
    LimitExceeded.class,
    LogBundlingFailed.class,
    MismatchedBundle.class,
    MissingBmcSupport.class,
    NamespaceFull.class,
    NamespaceLimitReached.class,
    NamespaceWriteProtected.class,
    NetworkDisruptedAndConfigRolledBack.class,
    NoClientCertificate.class,
    NoCompatibleDatastore.class,
    NoCompatibleHost.class,
    NoConnectedDatastore.class,
    NoDiskFound.class,
    NoSubjectName.class,
    ActiveDirectoryFault.class,
    NotFound.class,
    NotSupportedHostForChecksum.class,
    OutOfBounds.class,
    OvfFault.class,
    PatchBinariesNotFound.class,
    PatchMetadataInvalid.class,
    PatchNotApplicable.class,
    ProfileUpdateFailed.class,
    RebootRequired.class,
    RecordReplayDisabled.class,
    RemoveFailed.class,
    ReplicationFault.class,
    ResourceInUse.class,
    ResourceNotAvailable.class,
    SSPIChallenge.class,
    ShrinkDiskFault.class,
    SsdDiskNotAvailable.class,
    StorageDrsCannotMoveDiskInMultiWriterMode.class,
    StorageDrsCannotMoveFTVm.class,
    StorageDrsCannotMoveIndependentDisk.class,
    StorageDrsCannotMoveManuallyPlacedSwapFile.class,
    StorageDrsCannotMoveManuallyPlacedVm.class,
    StorageDrsCannotMoveSharedDisk.class,
    StorageDrsCannotMoveTemplate.class,
    StorageDrsCannotMoveVmInUserFolder.class,
    StorageDrsCannotMoveVmWithMountedCDROM.class,
    StorageDrsCannotMoveVmWithNoFilesInLayout.class,
    StorageDrsDatacentersCannotShareDatastore.class,
    StorageDrsDisabledOnVm.class,
    StorageDrsHbrDiskNotMovable.class,
    StorageDrsHmsMoveInProgress.class,
    StorageDrsHmsUnreachable.class,
    StorageDrsIolbDisabledInternally.class,
    StorageDrsRelocateDisabled.class,
    StorageDrsStaleHmsCollection.class,
    StorageDrsUnableToMoveFiles.class,
    InvalidDatastore.class,
    SwapDatastoreUnset.class,
    Timedout.class,
    TooManyConsecutiveOverrides.class,
    GuestOperationsFault.class,
    HostConnectFault.class,
    FileFault.class,
    SnapshotFault.class,
    ToolsUnavailable.class,
    UnrecognizedHost.class,
    UnsupportedVimApiVersion.class,
    UserNotFound.class,
    VAppConfigFault.class,
    TaskInProgress.class,
    VFlashModuleVersionIncompatible.class,
    InvalidFolder.class,
    VmFaultToleranceIssue.class,
    VmMetadataManagerFault.class,
    VmMonitorIncompatibleForFaultTolerance.class,
    InvalidState.class,
    InsufficientResourcesFault.class,
    VmToolsUpgradeFault.class,
    VmValidateMaxDevice.class,
    HostConfigFault.class,
    CustomizationFault.class,
    VsanFault.class,
    DvsFault.class,
    VmConfigFault.class,
    HostPowerOpFailed.class,
    MigrationFault.class,
    WipeDiskFault.class
})
public class VimFault
    extends MethodFault
{


}
