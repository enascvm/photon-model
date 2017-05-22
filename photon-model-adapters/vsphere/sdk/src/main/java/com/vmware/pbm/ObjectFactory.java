
package com.vmware.pbm;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;
import com.vmware.vim25.ActiveDirectoryFault;
import com.vmware.vim25.ActiveVMsBlockingEVC;
import com.vmware.vim25.AdminDisabled;
import com.vmware.vim25.AdminNotDisabled;
import com.vmware.vim25.AffinityConfigured;
import com.vmware.vim25.AgentInstallFailed;
import com.vmware.vim25.AlreadyBeingManaged;
import com.vmware.vim25.AlreadyConnected;
import com.vmware.vim25.AlreadyExists;
import com.vmware.vim25.AlreadyUpgraded;
import com.vmware.vim25.AnswerFileUpdateFailed;
import com.vmware.vim25.ApplicationQuiesceFault;
import com.vmware.vim25.AuthMinimumAdminPermission;
import com.vmware.vim25.BackupBlobReadFailure;
import com.vmware.vim25.BackupBlobWriteFailure;
import com.vmware.vim25.BlockedByFirewall;
import com.vmware.vim25.CAMServerRefusedConnection;
import com.vmware.vim25.CannotAccessFile;
import com.vmware.vim25.CannotAccessLocalSource;
import com.vmware.vim25.CannotAccessNetwork;
import com.vmware.vim25.CannotAccessVmComponent;
import com.vmware.vim25.CannotAccessVmConfig;
import com.vmware.vim25.CannotAccessVmDevice;
import com.vmware.vim25.CannotAccessVmDisk;
import com.vmware.vim25.CannotAddHostWithFTVmAsStandalone;
import com.vmware.vim25.CannotAddHostWithFTVmToDifferentCluster;
import com.vmware.vim25.CannotAddHostWithFTVmToNonHACluster;
import com.vmware.vim25.CannotChangeDrsBehaviorForFtSecondary;
import com.vmware.vim25.CannotChangeHaSettingsForFtSecondary;
import com.vmware.vim25.CannotChangeVsanClusterUuid;
import com.vmware.vim25.CannotChangeVsanNodeUuid;
import com.vmware.vim25.CannotComputeFTCompatibleHosts;
import com.vmware.vim25.CannotCreateFile;
import com.vmware.vim25.CannotDecryptPasswords;
import com.vmware.vim25.CannotDeleteFile;
import com.vmware.vim25.CannotDisableDrsOnClustersWithVApps;
import com.vmware.vim25.CannotDisableSnapshot;
import com.vmware.vim25.CannotDisconnectHostWithFaultToleranceVm;
import com.vmware.vim25.CannotEnableVmcpForCluster;
import com.vmware.vim25.CannotModifyConfigCpuRequirements;
import com.vmware.vim25.CannotMoveFaultToleranceVm;
import com.vmware.vim25.CannotMoveHostWithFaultToleranceVm;
import com.vmware.vim25.CannotMoveVmWithDeltaDisk;
import com.vmware.vim25.CannotMoveVmWithNativeDeltaDisk;
import com.vmware.vim25.CannotMoveVsanEnabledHost;
import com.vmware.vim25.CannotPlaceWithoutPrerequisiteMoves;
import com.vmware.vim25.CannotPowerOffVmInCluster;
import com.vmware.vim25.CannotReconfigureVsanWhenHaEnabled;
import com.vmware.vim25.CannotUseNetwork;
import com.vmware.vim25.ClockSkew;
import com.vmware.vim25.CloneFromSnapshotNotSupported;
import com.vmware.vim25.CollectorAddressUnset;
import com.vmware.vim25.ConcurrentAccess;
import com.vmware.vim25.ConflictingConfiguration;
import com.vmware.vim25.ConflictingDatastoreFound;
import com.vmware.vim25.ConnectedIso;
import com.vmware.vim25.CpuCompatibilityUnknown;
import com.vmware.vim25.CpuHotPlugNotSupported;
import com.vmware.vim25.CpuIncompatible;
import com.vmware.vim25.CpuIncompatible1ECX;
import com.vmware.vim25.CpuIncompatible81EDX;
import com.vmware.vim25.CustomizationFault;
import com.vmware.vim25.CustomizationPending;
import com.vmware.vim25.DVPortNotSupported;
import com.vmware.vim25.DasConfigFault;
import com.vmware.vim25.DatabaseError;
import com.vmware.vim25.DatacenterMismatch;
import com.vmware.vim25.DatastoreNotWritableOnHost;
import com.vmware.vim25.DeltaDiskFormatNotSupported;
import com.vmware.vim25.DestinationSwitchFull;
import com.vmware.vim25.DestinationVsanDisabled;
import com.vmware.vim25.DeviceBackingNotSupported;
import com.vmware.vim25.DeviceControllerNotSupported;
import com.vmware.vim25.DeviceHotPlugNotSupported;
import com.vmware.vim25.DeviceNotFound;
import com.vmware.vim25.DeviceNotSupported;
import com.vmware.vim25.DeviceUnsupportedForVmPlatform;
import com.vmware.vim25.DeviceUnsupportedForVmVersion;
import com.vmware.vim25.DigestNotSupported;
import com.vmware.vim25.DirectoryNotEmpty;
import com.vmware.vim25.DisableAdminNotSupported;
import com.vmware.vim25.DisallowedChangeByService;
import com.vmware.vim25.DisallowedDiskModeChange;
import com.vmware.vim25.DisallowedMigrationDeviceAttached;
import com.vmware.vim25.DisallowedOperationOnFailoverHost;
import com.vmware.vim25.DisconnectedHostsBlockingEVC;
import com.vmware.vim25.DiskHasPartitions;
import com.vmware.vim25.DiskIsLastRemainingNonSSD;
import com.vmware.vim25.DiskIsNonLocal;
import com.vmware.vim25.DiskIsUSB;
import com.vmware.vim25.DiskMoveTypeNotSupported;
import com.vmware.vim25.DiskNotSupported;
import com.vmware.vim25.DiskTooSmall;
import com.vmware.vim25.DomainNotFound;
import com.vmware.vim25.DrsDisabledOnVm;
import com.vmware.vim25.DrsVmotionIncompatibleFault;
import com.vmware.vim25.DuplicateDisks;
import com.vmware.vim25.DuplicateName;
import com.vmware.vim25.DuplicateVsanNetworkInterface;
import com.vmware.vim25.DvsApplyOperationFault;
import com.vmware.vim25.DvsFault;
import com.vmware.vim25.DvsNotAuthorized;
import com.vmware.vim25.DvsOperationBulkFault;
import com.vmware.vim25.DvsScopeViolated;
import com.vmware.vim25.EVCAdmissionFailed;
import com.vmware.vim25.EVCAdmissionFailedCPUFeaturesForMode;
import com.vmware.vim25.EVCAdmissionFailedCPUModel;
import com.vmware.vim25.EVCAdmissionFailedCPUModelForMode;
import com.vmware.vim25.EVCAdmissionFailedCPUVendor;
import com.vmware.vim25.EVCAdmissionFailedCPUVendorUnknown;
import com.vmware.vim25.EVCAdmissionFailedHostDisconnected;
import com.vmware.vim25.EVCAdmissionFailedHostSoftware;
import com.vmware.vim25.EVCAdmissionFailedHostSoftwareForMode;
import com.vmware.vim25.EVCAdmissionFailedVmActive;
import com.vmware.vim25.EVCConfigFault;
import com.vmware.vim25.EVCModeIllegalByVendor;
import com.vmware.vim25.EVCModeUnsupportedByHosts;
import com.vmware.vim25.EVCUnsupportedByHostHardware;
import com.vmware.vim25.EVCUnsupportedByHostSoftware;
import com.vmware.vim25.EightHostLimitViolated;
import com.vmware.vim25.ExpiredAddonLicense;
import com.vmware.vim25.ExpiredEditionLicense;
import com.vmware.vim25.ExpiredFeatureLicense;
import com.vmware.vim25.ExtendedFault;
import com.vmware.vim25.FailToEnableSPBM;
import com.vmware.vim25.FailToLockFaultToleranceVMs;
import com.vmware.vim25.FaultToleranceAntiAffinityViolated;
import com.vmware.vim25.FaultToleranceCannotEditMem;
import com.vmware.vim25.FaultToleranceCpuIncompatible;
import com.vmware.vim25.FaultToleranceNeedsThickDisk;
import com.vmware.vim25.FaultToleranceNotLicensed;
import com.vmware.vim25.FaultToleranceNotSameBuild;
import com.vmware.vim25.FaultTolerancePrimaryPowerOnNotAttempted;
import com.vmware.vim25.FaultToleranceVmNotDasProtected;
import com.vmware.vim25.FcoeFault;
import com.vmware.vim25.FcoeFaultPnicHasNoPortSet;
import com.vmware.vim25.FeatureRequirementsNotMet;
import com.vmware.vim25.FileAlreadyExists;
import com.vmware.vim25.FileBackedPortNotSupported;
import com.vmware.vim25.FileFault;
import com.vmware.vim25.FileLocked;
import com.vmware.vim25.FileNameTooLong;
import com.vmware.vim25.FileNotFound;
import com.vmware.vim25.FileNotWritable;
import com.vmware.vim25.FileTooLarge;
import com.vmware.vim25.FilesystemQuiesceFault;
import com.vmware.vim25.FilterInUse;
import com.vmware.vim25.FtIssuesOnHost;
import com.vmware.vim25.FullStorageVMotionNotSupported;
import com.vmware.vim25.GatewayConnectFault;
import com.vmware.vim25.GatewayHostNotReachable;
import com.vmware.vim25.GatewayNotFound;
import com.vmware.vim25.GatewayNotReachable;
import com.vmware.vim25.GatewayOperationRefused;
import com.vmware.vim25.GatewayToHostAuthFault;
import com.vmware.vim25.GatewayToHostConnectFault;
import com.vmware.vim25.GatewayToHostTrustVerifyFault;
import com.vmware.vim25.GenericDrsFault;
import com.vmware.vim25.GenericVmConfigFault;
import com.vmware.vim25.GuestAuthenticationChallenge;
import com.vmware.vim25.GuestComponentsOutOfDate;
import com.vmware.vim25.GuestMultipleMappings;
import com.vmware.vim25.GuestOperationsFault;
import com.vmware.vim25.GuestOperationsUnavailable;
import com.vmware.vim25.GuestPermissionDenied;
import com.vmware.vim25.GuestProcessNotFound;
import com.vmware.vim25.GuestRegistryFault;
import com.vmware.vim25.GuestRegistryKeyAlreadyExists;
import com.vmware.vim25.GuestRegistryKeyFault;
import com.vmware.vim25.GuestRegistryKeyHasSubkeys;
import com.vmware.vim25.GuestRegistryKeyInvalid;
import com.vmware.vim25.GuestRegistryKeyParentVolatile;
import com.vmware.vim25.GuestRegistryValueFault;
import com.vmware.vim25.GuestRegistryValueNotFound;
import com.vmware.vim25.HAErrorsAtDest;
import com.vmware.vim25.HeterogenousHostsBlockingEVC;
import com.vmware.vim25.HostAccessRestrictedToManagementServer;
import com.vmware.vim25.HostCommunication;
import com.vmware.vim25.HostConfigFailed;
import com.vmware.vim25.HostConfigFault;
import com.vmware.vim25.HostConnectFault;
import com.vmware.vim25.HostHasComponentFailure;
import com.vmware.vim25.HostInDomain;
import com.vmware.vim25.HostIncompatibleForFaultTolerance;
import com.vmware.vim25.HostIncompatibleForRecordReplay;
import com.vmware.vim25.HostInventoryFull;
import com.vmware.vim25.HostNotConnected;
import com.vmware.vim25.HostNotReachable;
import com.vmware.vim25.HostPowerOpFailed;
import com.vmware.vim25.HostSpecificationOperationFailed;
import com.vmware.vim25.HotSnapshotMoveNotSupported;
import com.vmware.vim25.IDEDiskNotSupported;
import com.vmware.vim25.IORMNotSupportedHostOnDatastore;
import com.vmware.vim25.ImportHostAddFailure;
import com.vmware.vim25.ImportOperationBulkFault;
import com.vmware.vim25.InUseFeatureManipulationDisallowed;
import com.vmware.vim25.InaccessibleDatastore;
import com.vmware.vim25.InaccessibleFTMetadataDatastore;
import com.vmware.vim25.InaccessibleVFlashSource;
import com.vmware.vim25.IncompatibleDefaultDevice;
import com.vmware.vim25.IncompatibleHostForFtSecondary;
import com.vmware.vim25.IncompatibleHostForVmReplication;
import com.vmware.vim25.IncompatibleSetting;
import com.vmware.vim25.IncorrectFileType;
import com.vmware.vim25.IncorrectHostInformation;
import com.vmware.vim25.IndependentDiskVMotionNotSupported;
import com.vmware.vim25.InsufficientAgentVmsDeployed;
import com.vmware.vim25.InsufficientCpuResourcesFault;
import com.vmware.vim25.InsufficientDisks;
import com.vmware.vim25.InsufficientFailoverResourcesFault;
import com.vmware.vim25.InsufficientGraphicsResourcesFault;
import com.vmware.vim25.InsufficientHostCapacityFault;
import com.vmware.vim25.InsufficientHostCpuCapacityFault;
import com.vmware.vim25.InsufficientHostMemoryCapacityFault;
import com.vmware.vim25.InsufficientMemoryResourcesFault;
import com.vmware.vim25.InsufficientNetworkCapacity;
import com.vmware.vim25.InsufficientNetworkResourcePoolCapacity;
import com.vmware.vim25.InsufficientPerCpuCapacity;
import com.vmware.vim25.InsufficientResourcesFault;
import com.vmware.vim25.InsufficientStandbyCpuResource;
import com.vmware.vim25.InsufficientStandbyMemoryResource;
import com.vmware.vim25.InsufficientStandbyResource;
import com.vmware.vim25.InsufficientStorageIops;
import com.vmware.vim25.InsufficientStorageSpace;
import com.vmware.vim25.InsufficientVFlashResourcesFault;
import com.vmware.vim25.InvalidAffinitySettingFault;
import com.vmware.vim25.InvalidArgument;
import com.vmware.vim25.InvalidBmcRole;
import com.vmware.vim25.InvalidBundle;
import com.vmware.vim25.InvalidCAMCertificate;
import com.vmware.vim25.InvalidCAMServer;
import com.vmware.vim25.InvalidClientCertificate;
import com.vmware.vim25.InvalidCollectorVersion;
import com.vmware.vim25.InvalidController;
import com.vmware.vim25.InvalidDasConfigArgument;
import com.vmware.vim25.InvalidDasRestartPriorityForFtVm;
import com.vmware.vim25.InvalidDatastore;
import com.vmware.vim25.InvalidDatastorePath;
import com.vmware.vim25.InvalidDatastoreState;
import com.vmware.vim25.InvalidDeviceBacking;
import com.vmware.vim25.InvalidDeviceOperation;
import com.vmware.vim25.InvalidDeviceSpec;
import com.vmware.vim25.InvalidDiskFormat;
import com.vmware.vim25.InvalidDrsBehaviorForFtVm;
import com.vmware.vim25.InvalidEditionLicense;
import com.vmware.vim25.InvalidEvent;
import com.vmware.vim25.InvalidFolder;
import com.vmware.vim25.InvalidFormat;
import com.vmware.vim25.InvalidGuestLogin;
import com.vmware.vim25.InvalidHostConnectionState;
import com.vmware.vim25.InvalidHostName;
import com.vmware.vim25.InvalidHostState;
import com.vmware.vim25.InvalidIndexArgument;
import com.vmware.vim25.InvalidIpfixConfig;
import com.vmware.vim25.InvalidIpmiLoginInfo;
import com.vmware.vim25.InvalidIpmiMacAddress;
import com.vmware.vim25.InvalidLicense;
import com.vmware.vim25.InvalidLocale;
import com.vmware.vim25.InvalidLogin;
import com.vmware.vim25.InvalidName;
import com.vmware.vim25.InvalidNasCredentials;
import com.vmware.vim25.InvalidNetworkInType;
import com.vmware.vim25.InvalidNetworkResource;
import com.vmware.vim25.InvalidOperationOnSecondaryVm;
import com.vmware.vim25.InvalidPowerState;
import com.vmware.vim25.InvalidPrivilege;
import com.vmware.vim25.InvalidProfileReferenceHost;
import com.vmware.vim25.InvalidProperty;
import com.vmware.vim25.InvalidPropertyType;
import com.vmware.vim25.InvalidPropertyValue;
import com.vmware.vim25.InvalidRequest;
import com.vmware.vim25.InvalidResourcePoolStructureFault;
import com.vmware.vim25.InvalidSnapshotFormat;
import com.vmware.vim25.InvalidState;
import com.vmware.vim25.InvalidType;
import com.vmware.vim25.InvalidVmConfig;
import com.vmware.vim25.InvalidVmState;
import com.vmware.vim25.InventoryHasStandardAloneHosts;
import com.vmware.vim25.IpHostnameGeneratorError;
import com.vmware.vim25.IscsiFault;
import com.vmware.vim25.IscsiFaultInvalidVnic;
import com.vmware.vim25.IscsiFaultPnicInUse;
import com.vmware.vim25.IscsiFaultVnicAlreadyBound;
import com.vmware.vim25.IscsiFaultVnicHasActivePaths;
import com.vmware.vim25.IscsiFaultVnicHasMultipleUplinks;
import com.vmware.vim25.IscsiFaultVnicHasNoUplinks;
import com.vmware.vim25.IscsiFaultVnicHasWrongUplink;
import com.vmware.vim25.IscsiFaultVnicInUse;
import com.vmware.vim25.IscsiFaultVnicIsLastPath;
import com.vmware.vim25.IscsiFaultVnicNotBound;
import com.vmware.vim25.IscsiFaultVnicNotFound;
import com.vmware.vim25.LargeRDMConversionNotSupported;
import com.vmware.vim25.LargeRDMNotSupportedOnDatastore;
import com.vmware.vim25.LegacyNetworkInterfaceInUse;
import com.vmware.vim25.LicenseAssignmentFailed;
import com.vmware.vim25.LicenseDowngradeDisallowed;
import com.vmware.vim25.LicenseEntityNotFound;
import com.vmware.vim25.LicenseExpired;
import com.vmware.vim25.LicenseKeyEntityMismatch;
import com.vmware.vim25.LicenseRestricted;
import com.vmware.vim25.LicenseServerUnavailable;
import com.vmware.vim25.LicenseSourceUnavailable;
import com.vmware.vim25.LimitExceeded;
import com.vmware.vim25.LinuxVolumeNotClean;
import com.vmware.vim25.LogBundlingFailed;
import com.vmware.vim25.MaintenanceModeFileMove;
import com.vmware.vim25.ManagedObjectNotFound;
import com.vmware.vim25.MemoryFileFormatNotSupportedByDatastore;
import com.vmware.vim25.MemoryHotPlugNotSupported;
import com.vmware.vim25.MemorySizeNotRecommended;
import com.vmware.vim25.MemorySizeNotSupported;
import com.vmware.vim25.MemorySizeNotSupportedByDatastore;
import com.vmware.vim25.MemorySnapshotOnIndependentDisk;
import com.vmware.vim25.MethodAlreadyDisabledFault;
import com.vmware.vim25.MethodDisabled;
import com.vmware.vim25.MethodFault;
import com.vmware.vim25.MethodNotFound;
import com.vmware.vim25.MigrationDisabled;
import com.vmware.vim25.MigrationFault;
import com.vmware.vim25.MigrationFeatureNotSupported;
import com.vmware.vim25.MigrationNotReady;
import com.vmware.vim25.MismatchedBundle;
import com.vmware.vim25.MismatchedNetworkPolicies;
import com.vmware.vim25.MismatchedVMotionNetworkNames;
import com.vmware.vim25.MissingBmcSupport;
import com.vmware.vim25.MissingController;
import com.vmware.vim25.MissingIpPool;
import com.vmware.vim25.MissingLinuxCustResources;
import com.vmware.vim25.MissingNetworkIpConfig;
import com.vmware.vim25.MissingPowerOffConfiguration;
import com.vmware.vim25.MissingPowerOnConfiguration;
import com.vmware.vim25.MissingWindowsCustResources;
import com.vmware.vim25.MksConnectionLimitReached;
import com.vmware.vim25.MountError;
import com.vmware.vim25.MultiWriterNotSupported;
import com.vmware.vim25.MultipleCertificatesVerifyFault;
import com.vmware.vim25.MultipleSnapshotsNotSupported;
import com.vmware.vim25.NamespaceFull;
import com.vmware.vim25.NamespaceLimitReached;
import com.vmware.vim25.NamespaceWriteProtected;
import com.vmware.vim25.NasConfigFault;
import com.vmware.vim25.NasConnectionLimitReached;
import com.vmware.vim25.NasSessionCredentialConflict;
import com.vmware.vim25.NasVolumeNotMounted;
import com.vmware.vim25.NetworkCopyFault;
import com.vmware.vim25.NetworkDisruptedAndConfigRolledBack;
import com.vmware.vim25.NetworkInaccessible;
import com.vmware.vim25.NetworksMayNotBeTheSame;
import com.vmware.vim25.NicSettingMismatch;
import com.vmware.vim25.NoActiveHostInCluster;
import com.vmware.vim25.NoAvailableIp;
import com.vmware.vim25.NoClientCertificate;
import com.vmware.vim25.NoCompatibleDatastore;
import com.vmware.vim25.NoCompatibleHardAffinityHost;
import com.vmware.vim25.NoCompatibleHost;
import com.vmware.vim25.NoCompatibleHostWithAccessToDevice;
import com.vmware.vim25.NoCompatibleSoftAffinityHost;
import com.vmware.vim25.NoConnectedDatastore;
import com.vmware.vim25.NoDiskFound;
import com.vmware.vim25.NoDiskSpace;
import com.vmware.vim25.NoDisksToCustomize;
import com.vmware.vim25.NoGateway;
import com.vmware.vim25.NoGuestHeartbeat;
import com.vmware.vim25.NoHost;
import com.vmware.vim25.NoHostSuitableForFtSecondary;
import com.vmware.vim25.NoLicenseServerConfigured;
import com.vmware.vim25.NoPeerHostFound;
import com.vmware.vim25.NoPermission;
import com.vmware.vim25.NoPermissionOnAD;
import com.vmware.vim25.NoPermissionOnHost;
import com.vmware.vim25.NoPermissionOnNasVolume;
import com.vmware.vim25.NoSubjectName;
import com.vmware.vim25.NoVcManagedIpConfigured;
import com.vmware.vim25.NoVirtualNic;
import com.vmware.vim25.NoVmInVApp;
import com.vmware.vim25.NonADUserRequired;
import com.vmware.vim25.NonHomeRDMVMotionNotSupported;
import com.vmware.vim25.NonPersistentDisksNotSupported;
import com.vmware.vim25.NonVmwareOuiMacNotSupportedHost;
import com.vmware.vim25.NotADirectory;
import com.vmware.vim25.NotAFile;
import com.vmware.vim25.NotAuthenticated;
import com.vmware.vim25.NotEnoughCpus;
import com.vmware.vim25.NotEnoughLicenses;
import com.vmware.vim25.NotEnoughLogicalCpus;
import com.vmware.vim25.NotFound;
import com.vmware.vim25.NotImplemented;
import com.vmware.vim25.NotSupported;
import com.vmware.vim25.NotSupportedDeviceForFT;
import com.vmware.vim25.NotSupportedHost;
import com.vmware.vim25.NotSupportedHostForChecksum;
import com.vmware.vim25.NotSupportedHostForVFlash;
import com.vmware.vim25.NotSupportedHostForVmcp;
import com.vmware.vim25.NotSupportedHostForVmemFile;
import com.vmware.vim25.NotSupportedHostForVsan;
import com.vmware.vim25.NotSupportedHostInCluster;
import com.vmware.vim25.NotSupportedHostInDvs;
import com.vmware.vim25.NotSupportedHostInHACluster;
import com.vmware.vim25.NotUserConfigurableProperty;
import com.vmware.vim25.NumVirtualCoresPerSocketNotSupported;
import com.vmware.vim25.NumVirtualCpusExceedsLimit;
import com.vmware.vim25.NumVirtualCpusIncompatible;
import com.vmware.vim25.NumVirtualCpusNotSupported;
import com.vmware.vim25.OperationDisabledByGuest;
import com.vmware.vim25.OperationDisallowedOnHost;
import com.vmware.vim25.OperationNotSupportedByGuest;
import com.vmware.vim25.OutOfBounds;
import com.vmware.vim25.OvfAttribute;
import com.vmware.vim25.OvfConnectedDevice;
import com.vmware.vim25.OvfConnectedDeviceFloppy;
import com.vmware.vim25.OvfConnectedDeviceIso;
import com.vmware.vim25.OvfConstraint;
import com.vmware.vim25.OvfConsumerCallbackFault;
import com.vmware.vim25.OvfConsumerCommunicationError;
import com.vmware.vim25.OvfConsumerFault;
import com.vmware.vim25.OvfConsumerInvalidSection;
import com.vmware.vim25.OvfConsumerPowerOnFault;
import com.vmware.vim25.OvfConsumerUndeclaredSection;
import com.vmware.vim25.OvfConsumerUndefinedPrefix;
import com.vmware.vim25.OvfConsumerValidationFault;
import com.vmware.vim25.OvfCpuCompatibility;
import com.vmware.vim25.OvfCpuCompatibilityCheckNotSupported;
import com.vmware.vim25.OvfDiskMappingNotFound;
import com.vmware.vim25.OvfDiskOrderConstraint;
import com.vmware.vim25.OvfDuplicateElement;
import com.vmware.vim25.OvfDuplicatedElementBoundary;
import com.vmware.vim25.OvfDuplicatedPropertyIdExport;
import com.vmware.vim25.OvfDuplicatedPropertyIdImport;
import com.vmware.vim25.OvfElement;
import com.vmware.vim25.OvfElementInvalidValue;
import com.vmware.vim25.OvfExport;
import com.vmware.vim25.OvfExportFailed;
import com.vmware.vim25.OvfFault;
import com.vmware.vim25.OvfHardwareCheck;
import com.vmware.vim25.OvfHardwareExport;
import com.vmware.vim25.OvfHostResourceConstraint;
import com.vmware.vim25.OvfHostValueNotParsed;
import com.vmware.vim25.OvfImport;
import com.vmware.vim25.OvfImportFailed;
import com.vmware.vim25.OvfInternalError;
import com.vmware.vim25.OvfInvalidPackage;
import com.vmware.vim25.OvfInvalidValue;
import com.vmware.vim25.OvfInvalidValueConfiguration;
import com.vmware.vim25.OvfInvalidValueEmpty;
import com.vmware.vim25.OvfInvalidValueFormatMalformed;
import com.vmware.vim25.OvfInvalidValueReference;
import com.vmware.vim25.OvfInvalidVmName;
import com.vmware.vim25.OvfMappedOsId;
import com.vmware.vim25.OvfMissingAttribute;
import com.vmware.vim25.OvfMissingElement;
import com.vmware.vim25.OvfMissingElementNormalBoundary;
import com.vmware.vim25.OvfMissingHardware;
import com.vmware.vim25.OvfNetworkMappingNotSupported;
import com.vmware.vim25.OvfNoHostNic;
import com.vmware.vim25.OvfNoSpaceOnController;
import com.vmware.vim25.OvfNoSupportedHardwareFamily;
import com.vmware.vim25.OvfProperty;
import com.vmware.vim25.OvfPropertyExport;
import com.vmware.vim25.OvfPropertyNetwork;
import com.vmware.vim25.OvfPropertyNetworkExport;
import com.vmware.vim25.OvfPropertyQualifier;
import com.vmware.vim25.OvfPropertyQualifierDuplicate;
import com.vmware.vim25.OvfPropertyQualifierIgnored;
import com.vmware.vim25.OvfPropertyType;
import com.vmware.vim25.OvfPropertyValue;
import com.vmware.vim25.OvfSystemFault;
import com.vmware.vim25.OvfToXmlUnsupportedElement;
import com.vmware.vim25.OvfUnableToExportDisk;
import com.vmware.vim25.OvfUnexpectedElement;
import com.vmware.vim25.OvfUnknownDevice;
import com.vmware.vim25.OvfUnknownDeviceBacking;
import com.vmware.vim25.OvfUnknownEntity;
import com.vmware.vim25.OvfUnsupportedAttribute;
import com.vmware.vim25.OvfUnsupportedAttributeValue;
import com.vmware.vim25.OvfUnsupportedDeviceBackingInfo;
import com.vmware.vim25.OvfUnsupportedDeviceBackingOption;
import com.vmware.vim25.OvfUnsupportedDeviceExport;
import com.vmware.vim25.OvfUnsupportedDiskProvisioning;
import com.vmware.vim25.OvfUnsupportedElement;
import com.vmware.vim25.OvfUnsupportedElementValue;
import com.vmware.vim25.OvfUnsupportedPackage;
import com.vmware.vim25.OvfUnsupportedSection;
import com.vmware.vim25.OvfUnsupportedSubType;
import com.vmware.vim25.OvfUnsupportedType;
import com.vmware.vim25.OvfWrongElement;
import com.vmware.vim25.OvfWrongNamespace;
import com.vmware.vim25.OvfXmlFormat;
import com.vmware.vim25.PatchAlreadyInstalled;
import com.vmware.vim25.PatchBinariesNotFound;
import com.vmware.vim25.PatchInstallFailed;
import com.vmware.vim25.PatchIntegrityError;
import com.vmware.vim25.PatchMetadataCorrupted;
import com.vmware.vim25.PatchMetadataInvalid;
import com.vmware.vim25.PatchMetadataNotFound;
import com.vmware.vim25.PatchMissingDependencies;
import com.vmware.vim25.PatchNotApplicable;
import com.vmware.vim25.PatchSuperseded;
import com.vmware.vim25.PhysCompatRDMNotSupported;
import com.vmware.vim25.PlatformConfigFault;
import com.vmware.vim25.PowerOnFtSecondaryFailed;
import com.vmware.vim25.PowerOnFtSecondaryTimedout;
import com.vmware.vim25.ProfileUpdateFailed;
import com.vmware.vim25.QuarantineModeFault;
import com.vmware.vim25.QuestionPending;
import com.vmware.vim25.QuiesceDatastoreIOForHAFailed;
import com.vmware.vim25.RDMConversionNotSupported;
import com.vmware.vim25.RDMNotPreserved;
import com.vmware.vim25.RDMNotSupported;
import com.vmware.vim25.RDMNotSupportedOnDatastore;
import com.vmware.vim25.RDMPointsToInaccessibleDisk;
import com.vmware.vim25.RawDiskNotSupported;
import com.vmware.vim25.ReadHostResourcePoolTreeFailed;
import com.vmware.vim25.ReadOnlyDisksWithLegacyDestination;
import com.vmware.vim25.RebootRequired;
import com.vmware.vim25.RecordReplayDisabled;
import com.vmware.vim25.RemoteDeviceNotSupported;
import com.vmware.vim25.RemoveFailed;
import com.vmware.vim25.ReplicationConfigFault;
import com.vmware.vim25.ReplicationDiskConfigFault;
import com.vmware.vim25.ReplicationFault;
import com.vmware.vim25.ReplicationIncompatibleWithFT;
import com.vmware.vim25.ReplicationInvalidOptions;
import com.vmware.vim25.ReplicationNotSupportedOnHost;
import com.vmware.vim25.ReplicationVmConfigFault;
import com.vmware.vim25.ReplicationVmFault;
import com.vmware.vim25.ReplicationVmInProgressFault;
import com.vmware.vim25.RequestCanceled;
import com.vmware.vim25.ResourceInUse;
import com.vmware.vim25.ResourceNotAvailable;
import com.vmware.vim25.RestrictedByAdministrator;
import com.vmware.vim25.RestrictedVersion;
import com.vmware.vim25.RollbackFailure;
import com.vmware.vim25.RuleViolation;
import com.vmware.vim25.RuntimeFault;
import com.vmware.vim25.SSLDisabledFault;
import com.vmware.vim25.SSLVerifyFault;
import com.vmware.vim25.SSPIChallenge;
import com.vmware.vim25.SecondaryVmAlreadyDisabled;
import com.vmware.vim25.SecondaryVmAlreadyEnabled;
import com.vmware.vim25.SecondaryVmAlreadyRegistered;
import com.vmware.vim25.SecondaryVmNotRegistered;
import com.vmware.vim25.SecurityError;
import com.vmware.vim25.SharedBusControllerNotSupported;
import com.vmware.vim25.ShrinkDiskFault;
import com.vmware.vim25.SnapshotCloneNotSupported;
import com.vmware.vim25.SnapshotCopyNotSupported;
import com.vmware.vim25.SnapshotDisabled;
import com.vmware.vim25.SnapshotFault;
import com.vmware.vim25.SnapshotIncompatibleDeviceInVm;
import com.vmware.vim25.SnapshotLocked;
import com.vmware.vim25.SnapshotMoveFromNonHomeNotSupported;
import com.vmware.vim25.SnapshotMoveNotSupported;
import com.vmware.vim25.SnapshotMoveToNonHomeNotSupported;
import com.vmware.vim25.SnapshotNoChange;
import com.vmware.vim25.SnapshotRevertIssue;
import com.vmware.vim25.SoftRuleVioCorrectionDisallowed;
import com.vmware.vim25.SoftRuleVioCorrectionImpact;
import com.vmware.vim25.SsdDiskNotAvailable;
import com.vmware.vim25.StorageDrsCannotMoveDiskInMultiWriterMode;
import com.vmware.vim25.StorageDrsCannotMoveFTVm;
import com.vmware.vim25.StorageDrsCannotMoveIndependentDisk;
import com.vmware.vim25.StorageDrsCannotMoveManuallyPlacedSwapFile;
import com.vmware.vim25.StorageDrsCannotMoveManuallyPlacedVm;
import com.vmware.vim25.StorageDrsCannotMoveSharedDisk;
import com.vmware.vim25.StorageDrsCannotMoveTemplate;
import com.vmware.vim25.StorageDrsCannotMoveVmInUserFolder;
import com.vmware.vim25.StorageDrsCannotMoveVmWithMountedCDROM;
import com.vmware.vim25.StorageDrsCannotMoveVmWithNoFilesInLayout;
import com.vmware.vim25.StorageDrsDatacentersCannotShareDatastore;
import com.vmware.vim25.StorageDrsDisabledOnVm;
import com.vmware.vim25.StorageDrsHbrDiskNotMovable;
import com.vmware.vim25.StorageDrsHmsMoveInProgress;
import com.vmware.vim25.StorageDrsHmsUnreachable;
import com.vmware.vim25.StorageDrsIolbDisabledInternally;
import com.vmware.vim25.StorageDrsRelocateDisabled;
import com.vmware.vim25.StorageDrsStaleHmsCollection;
import com.vmware.vim25.StorageDrsUnableToMoveFiles;
import com.vmware.vim25.StorageVMotionNotSupported;
import com.vmware.vim25.StorageVmotionIncompatible;
import com.vmware.vim25.SuspendedRelocateNotSupported;
import com.vmware.vim25.SwapDatastoreNotWritableOnHost;
import com.vmware.vim25.SwapDatastoreUnset;
import com.vmware.vim25.SwapPlacementOverrideNotSupported;
import com.vmware.vim25.SwitchIpUnset;
import com.vmware.vim25.SwitchNotInUpgradeMode;
import com.vmware.vim25.SystemError;
import com.vmware.vim25.TaskInProgress;
import com.vmware.vim25.ThirdPartyLicenseAssignmentFailed;
import com.vmware.vim25.Timedout;
import com.vmware.vim25.TooManyConcurrentNativeClones;
import com.vmware.vim25.TooManyConsecutiveOverrides;
import com.vmware.vim25.TooManyDevices;
import com.vmware.vim25.TooManyDisksOnLegacyHost;
import com.vmware.vim25.TooManyGuestLogons;
import com.vmware.vim25.TooManyHosts;
import com.vmware.vim25.TooManyNativeCloneLevels;
import com.vmware.vim25.TooManyNativeClonesOnFile;
import com.vmware.vim25.TooManySnapshotLevels;
import com.vmware.vim25.ToolsAlreadyUpgraded;
import com.vmware.vim25.ToolsAutoUpgradeNotSupported;
import com.vmware.vim25.ToolsImageCopyFailed;
import com.vmware.vim25.ToolsImageNotAvailable;
import com.vmware.vim25.ToolsImageSignatureCheckFailed;
import com.vmware.vim25.ToolsInstallationInProgress;
import com.vmware.vim25.ToolsUnavailable;
import com.vmware.vim25.ToolsUpgradeCancelled;
import com.vmware.vim25.UnSupportedDatastoreForVFlash;
import com.vmware.vim25.UncommittedUndoableDisk;
import com.vmware.vim25.UnconfiguredPropertyValue;
import com.vmware.vim25.UncustomizableGuest;
import com.vmware.vim25.UnexpectedCustomizationFault;
import com.vmware.vim25.UnexpectedFault;
import com.vmware.vim25.UnrecognizedHost;
import com.vmware.vim25.UnsharedSwapVMotionNotSupported;
import com.vmware.vim25.UnsupportedDatastore;
import com.vmware.vim25.UnsupportedGuest;
import com.vmware.vim25.UnsupportedVimApiVersion;
import com.vmware.vim25.UnsupportedVmxLocation;
import com.vmware.vim25.UnusedVirtualDiskBlocksNotScrubbed;
import com.vmware.vim25.UserNotFound;
import com.vmware.vim25.VAppConfigFault;
import com.vmware.vim25.VAppNotRunning;
import com.vmware.vim25.VAppOperationInProgress;
import com.vmware.vim25.VAppPropertyFault;
import com.vmware.vim25.VAppTaskInProgress;
import com.vmware.vim25.VFlashCacheHotConfigNotSupported;
import com.vmware.vim25.VFlashModuleNotSupported;
import com.vmware.vim25.VFlashModuleVersionIncompatible;
import com.vmware.vim25.VMINotSupported;
import com.vmware.vim25.VMOnConflictDVPort;
import com.vmware.vim25.VMOnVirtualIntranet;
import com.vmware.vim25.VMotionAcrossNetworkNotSupported;
import com.vmware.vim25.VMotionInterfaceIssue;
import com.vmware.vim25.VMotionLinkCapacityLow;
import com.vmware.vim25.VMotionLinkDown;
import com.vmware.vim25.VMotionNotConfigured;
import com.vmware.vim25.VMotionNotLicensed;
import com.vmware.vim25.VMotionNotSupported;
import com.vmware.vim25.VMotionProtocolIncompatible;
import com.vmware.vim25.VimFault;
import com.vmware.vim25.VirtualDiskBlocksNotFullyProvisioned;
import com.vmware.vim25.VirtualDiskModeNotSupported;
import com.vmware.vim25.VirtualEthernetCardNotSupported;
import com.vmware.vim25.VirtualHardwareCompatibilityIssue;
import com.vmware.vim25.VirtualHardwareVersionNotSupported;
import com.vmware.vim25.VmAlreadyExistsInDatacenter;
import com.vmware.vim25.VmConfigFault;
import com.vmware.vim25.VmConfigIncompatibleForFaultTolerance;
import com.vmware.vim25.VmConfigIncompatibleForRecordReplay;
import com.vmware.vim25.VmFaultToleranceConfigIssue;
import com.vmware.vim25.VmFaultToleranceConfigIssueWrapper;
import com.vmware.vim25.VmFaultToleranceInvalidFileBacking;
import com.vmware.vim25.VmFaultToleranceIssue;
import com.vmware.vim25.VmFaultToleranceOpIssuesList;
import com.vmware.vim25.VmFaultToleranceTooManyFtVcpusOnHost;
import com.vmware.vim25.VmFaultToleranceTooManyVMsOnHost;
import com.vmware.vim25.VmHostAffinityRuleViolation;
import com.vmware.vim25.VmLimitLicense;
import com.vmware.vim25.VmMetadataManagerFault;
import com.vmware.vim25.VmMonitorIncompatibleForFaultTolerance;
import com.vmware.vim25.VmPowerOnDisabled;
import com.vmware.vim25.VmSmpFaultToleranceTooManyVMsOnHost;
import com.vmware.vim25.VmToolsUpgradeFault;
import com.vmware.vim25.VmValidateMaxDevice;
import com.vmware.vim25.VmWwnConflict;
import com.vmware.vim25.VmfsAlreadyMounted;
import com.vmware.vim25.VmfsAmbiguousMount;
import com.vmware.vim25.VmfsMountFault;
import com.vmware.vim25.VmotionInterfaceNotEnabled;
import com.vmware.vim25.VolumeEditorError;
import com.vmware.vim25.VramLimitLicense;
import com.vmware.vim25.VsanClusterUuidMismatch;
import com.vmware.vim25.VsanDiskFault;
import com.vmware.vim25.VsanFault;
import com.vmware.vim25.VsanIncompatibleDiskMapping;
import com.vmware.vim25.VspanDestPortConflict;
import com.vmware.vim25.VspanPortConflict;
import com.vmware.vim25.VspanPortMoveFault;
import com.vmware.vim25.VspanPortPromiscChangeFault;
import com.vmware.vim25.VspanPortgroupPromiscChangeFault;
import com.vmware.vim25.VspanPortgroupTypeChangeFault;
import com.vmware.vim25.VspanPromiscuousPortNotSupported;
import com.vmware.vim25.VspanSameSessionPortConflict;
import com.vmware.vim25.WakeOnLanNotSupported;
import com.vmware.vim25.WakeOnLanNotSupportedByVmotionNIC;
import com.vmware.vim25.WillLoseHAProtection;
import com.vmware.vim25.WillModifyConfigCpuRequirements;
import com.vmware.vim25.WillResetSnapshotDirectory;
import com.vmware.vim25.WipeDiskFault;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the com.vmware.pbm package. 
 * <p>An ObjectFactory allows you to programatically 
 * construct new instances of the Java representation 
 * for XML content. The Java representation of XML 
 * content can consist of schema derived interfaces 
 * and classes representing the binding of schema 
 * type definitions, element declarations and model 
 * groups.  Factory methods for each of these are 
 * provided in this class.
 * 
 */
@XmlRegistry
public class ObjectFactory {

    private final static QName _VersionURI_QNAME = new QName("urn:pbm", "versionURI");
    private final static QName _ActiveDirectoryFaultFault_QNAME = new QName("urn:pbm", "ActiveDirectoryFaultFault");
    private final static QName _ActiveVMsBlockingEVCFault_QNAME = new QName("urn:pbm", "ActiveVMsBlockingEVCFault");
    private final static QName _AdminDisabledFault_QNAME = new QName("urn:pbm", "AdminDisabledFault");
    private final static QName _AdminNotDisabledFault_QNAME = new QName("urn:pbm", "AdminNotDisabledFault");
    private final static QName _AffinityConfiguredFault_QNAME = new QName("urn:pbm", "AffinityConfiguredFault");
    private final static QName _AgentInstallFailedFault_QNAME = new QName("urn:pbm", "AgentInstallFailedFault");
    private final static QName _AlreadyBeingManagedFault_QNAME = new QName("urn:pbm", "AlreadyBeingManagedFault");
    private final static QName _AlreadyConnectedFault_QNAME = new QName("urn:pbm", "AlreadyConnectedFault");
    private final static QName _AlreadyExistsFault_QNAME = new QName("urn:pbm", "AlreadyExistsFault");
    private final static QName _AlreadyUpgradedFault_QNAME = new QName("urn:pbm", "AlreadyUpgradedFault");
    private final static QName _AnswerFileUpdateFailedFault_QNAME = new QName("urn:pbm", "AnswerFileUpdateFailedFault");
    private final static QName _ApplicationQuiesceFaultFault_QNAME = new QName("urn:pbm", "ApplicationQuiesceFaultFault");
    private final static QName _AuthMinimumAdminPermissionFault_QNAME = new QName("urn:pbm", "AuthMinimumAdminPermissionFault");
    private final static QName _BackupBlobReadFailureFault_QNAME = new QName("urn:pbm", "BackupBlobReadFailureFault");
    private final static QName _BackupBlobWriteFailureFault_QNAME = new QName("urn:pbm", "BackupBlobWriteFailureFault");
    private final static QName _BlockedByFirewallFault_QNAME = new QName("urn:pbm", "BlockedByFirewallFault");
    private final static QName _CAMServerRefusedConnectionFault_QNAME = new QName("urn:pbm", "CAMServerRefusedConnectionFault");
    private final static QName _CannotAccessFileFault_QNAME = new QName("urn:pbm", "CannotAccessFileFault");
    private final static QName _CannotAccessLocalSourceFault_QNAME = new QName("urn:pbm", "CannotAccessLocalSourceFault");
    private final static QName _CannotAccessNetworkFault_QNAME = new QName("urn:pbm", "CannotAccessNetworkFault");
    private final static QName _CannotAccessVmComponentFault_QNAME = new QName("urn:pbm", "CannotAccessVmComponentFault");
    private final static QName _CannotAccessVmConfigFault_QNAME = new QName("urn:pbm", "CannotAccessVmConfigFault");
    private final static QName _CannotAccessVmDeviceFault_QNAME = new QName("urn:pbm", "CannotAccessVmDeviceFault");
    private final static QName _CannotAccessVmDiskFault_QNAME = new QName("urn:pbm", "CannotAccessVmDiskFault");
    private final static QName _CannotAddHostWithFTVmAsStandaloneFault_QNAME = new QName("urn:pbm", "CannotAddHostWithFTVmAsStandaloneFault");
    private final static QName _CannotAddHostWithFTVmToDifferentClusterFault_QNAME = new QName("urn:pbm", "CannotAddHostWithFTVmToDifferentClusterFault");
    private final static QName _CannotAddHostWithFTVmToNonHAClusterFault_QNAME = new QName("urn:pbm", "CannotAddHostWithFTVmToNonHAClusterFault");
    private final static QName _CannotChangeDrsBehaviorForFtSecondaryFault_QNAME = new QName("urn:pbm", "CannotChangeDrsBehaviorForFtSecondaryFault");
    private final static QName _CannotChangeHaSettingsForFtSecondaryFault_QNAME = new QName("urn:pbm", "CannotChangeHaSettingsForFtSecondaryFault");
    private final static QName _CannotChangeVsanClusterUuidFault_QNAME = new QName("urn:pbm", "CannotChangeVsanClusterUuidFault");
    private final static QName _CannotChangeVsanNodeUuidFault_QNAME = new QName("urn:pbm", "CannotChangeVsanNodeUuidFault");
    private final static QName _CannotComputeFTCompatibleHostsFault_QNAME = new QName("urn:pbm", "CannotComputeFTCompatibleHostsFault");
    private final static QName _CannotCreateFileFault_QNAME = new QName("urn:pbm", "CannotCreateFileFault");
    private final static QName _CannotDecryptPasswordsFault_QNAME = new QName("urn:pbm", "CannotDecryptPasswordsFault");
    private final static QName _CannotDeleteFileFault_QNAME = new QName("urn:pbm", "CannotDeleteFileFault");
    private final static QName _CannotDisableDrsOnClustersWithVAppsFault_QNAME = new QName("urn:pbm", "CannotDisableDrsOnClustersWithVAppsFault");
    private final static QName _CannotDisableSnapshotFault_QNAME = new QName("urn:pbm", "CannotDisableSnapshotFault");
    private final static QName _CannotDisconnectHostWithFaultToleranceVmFault_QNAME = new QName("urn:pbm", "CannotDisconnectHostWithFaultToleranceVmFault");
    private final static QName _CannotEnableVmcpForClusterFault_QNAME = new QName("urn:pbm", "CannotEnableVmcpForClusterFault");
    private final static QName _CannotModifyConfigCpuRequirementsFault_QNAME = new QName("urn:pbm", "CannotModifyConfigCpuRequirementsFault");
    private final static QName _CannotMoveFaultToleranceVmFault_QNAME = new QName("urn:pbm", "CannotMoveFaultToleranceVmFault");
    private final static QName _CannotMoveHostWithFaultToleranceVmFault_QNAME = new QName("urn:pbm", "CannotMoveHostWithFaultToleranceVmFault");
    private final static QName _CannotMoveVmWithDeltaDiskFault_QNAME = new QName("urn:pbm", "CannotMoveVmWithDeltaDiskFault");
    private final static QName _CannotMoveVmWithNativeDeltaDiskFault_QNAME = new QName("urn:pbm", "CannotMoveVmWithNativeDeltaDiskFault");
    private final static QName _CannotMoveVsanEnabledHostFault_QNAME = new QName("urn:pbm", "CannotMoveVsanEnabledHostFault");
    private final static QName _CannotPlaceWithoutPrerequisiteMovesFault_QNAME = new QName("urn:pbm", "CannotPlaceWithoutPrerequisiteMovesFault");
    private final static QName _CannotPowerOffVmInClusterFault_QNAME = new QName("urn:pbm", "CannotPowerOffVmInClusterFault");
    private final static QName _CannotReconfigureVsanWhenHaEnabledFault_QNAME = new QName("urn:pbm", "CannotReconfigureVsanWhenHaEnabledFault");
    private final static QName _CannotUseNetworkFault_QNAME = new QName("urn:pbm", "CannotUseNetworkFault");
    private final static QName _ClockSkewFault_QNAME = new QName("urn:pbm", "ClockSkewFault");
    private final static QName _CloneFromSnapshotNotSupportedFault_QNAME = new QName("urn:pbm", "CloneFromSnapshotNotSupportedFault");
    private final static QName _CollectorAddressUnsetFault_QNAME = new QName("urn:pbm", "CollectorAddressUnsetFault");
    private final static QName _ConcurrentAccessFault_QNAME = new QName("urn:pbm", "ConcurrentAccessFault");
    private final static QName _ConflictingConfigurationFault_QNAME = new QName("urn:pbm", "ConflictingConfigurationFault");
    private final static QName _ConflictingDatastoreFoundFault_QNAME = new QName("urn:pbm", "ConflictingDatastoreFoundFault");
    private final static QName _ConnectedIsoFault_QNAME = new QName("urn:pbm", "ConnectedIsoFault");
    private final static QName _CpuCompatibilityUnknownFault_QNAME = new QName("urn:pbm", "CpuCompatibilityUnknownFault");
    private final static QName _CpuHotPlugNotSupportedFault_QNAME = new QName("urn:pbm", "CpuHotPlugNotSupportedFault");
    private final static QName _CpuIncompatibleFault_QNAME = new QName("urn:pbm", "CpuIncompatibleFault");
    private final static QName _CpuIncompatible1ECXFault_QNAME = new QName("urn:pbm", "CpuIncompatible1ECXFault");
    private final static QName _CpuIncompatible81EDXFault_QNAME = new QName("urn:pbm", "CpuIncompatible81EDXFault");
    private final static QName _CustomizationFaultFault_QNAME = new QName("urn:pbm", "CustomizationFaultFault");
    private final static QName _CustomizationPendingFault_QNAME = new QName("urn:pbm", "CustomizationPendingFault");
    private final static QName _DVPortNotSupportedFault_QNAME = new QName("urn:pbm", "DVPortNotSupportedFault");
    private final static QName _DasConfigFaultFault_QNAME = new QName("urn:pbm", "DasConfigFaultFault");
    private final static QName _DatabaseErrorFault_QNAME = new QName("urn:pbm", "DatabaseErrorFault");
    private final static QName _DatacenterMismatchFault_QNAME = new QName("urn:pbm", "DatacenterMismatchFault");
    private final static QName _DatastoreNotWritableOnHostFault_QNAME = new QName("urn:pbm", "DatastoreNotWritableOnHostFault");
    private final static QName _DeltaDiskFormatNotSupportedFault_QNAME = new QName("urn:pbm", "DeltaDiskFormatNotSupportedFault");
    private final static QName _DestinationSwitchFullFault_QNAME = new QName("urn:pbm", "DestinationSwitchFullFault");
    private final static QName _DestinationVsanDisabledFault_QNAME = new QName("urn:pbm", "DestinationVsanDisabledFault");
    private final static QName _DeviceBackingNotSupportedFault_QNAME = new QName("urn:pbm", "DeviceBackingNotSupportedFault");
    private final static QName _DeviceControllerNotSupportedFault_QNAME = new QName("urn:pbm", "DeviceControllerNotSupportedFault");
    private final static QName _DeviceHotPlugNotSupportedFault_QNAME = new QName("urn:pbm", "DeviceHotPlugNotSupportedFault");
    private final static QName _DeviceNotFoundFault_QNAME = new QName("urn:pbm", "DeviceNotFoundFault");
    private final static QName _DeviceNotSupportedFault_QNAME = new QName("urn:pbm", "DeviceNotSupportedFault");
    private final static QName _DeviceUnsupportedForVmPlatformFault_QNAME = new QName("urn:pbm", "DeviceUnsupportedForVmPlatformFault");
    private final static QName _DeviceUnsupportedForVmVersionFault_QNAME = new QName("urn:pbm", "DeviceUnsupportedForVmVersionFault");
    private final static QName _DigestNotSupportedFault_QNAME = new QName("urn:pbm", "DigestNotSupportedFault");
    private final static QName _DirectoryNotEmptyFault_QNAME = new QName("urn:pbm", "DirectoryNotEmptyFault");
    private final static QName _DisableAdminNotSupportedFault_QNAME = new QName("urn:pbm", "DisableAdminNotSupportedFault");
    private final static QName _DisallowedChangeByServiceFault_QNAME = new QName("urn:pbm", "DisallowedChangeByServiceFault");
    private final static QName _DisallowedDiskModeChangeFault_QNAME = new QName("urn:pbm", "DisallowedDiskModeChangeFault");
    private final static QName _DisallowedMigrationDeviceAttachedFault_QNAME = new QName("urn:pbm", "DisallowedMigrationDeviceAttachedFault");
    private final static QName _DisallowedOperationOnFailoverHostFault_QNAME = new QName("urn:pbm", "DisallowedOperationOnFailoverHostFault");
    private final static QName _DisconnectedHostsBlockingEVCFault_QNAME = new QName("urn:pbm", "DisconnectedHostsBlockingEVCFault");
    private final static QName _DiskHasPartitionsFault_QNAME = new QName("urn:pbm", "DiskHasPartitionsFault");
    private final static QName _DiskIsLastRemainingNonSSDFault_QNAME = new QName("urn:pbm", "DiskIsLastRemainingNonSSDFault");
    private final static QName _DiskIsNonLocalFault_QNAME = new QName("urn:pbm", "DiskIsNonLocalFault");
    private final static QName _DiskIsUSBFault_QNAME = new QName("urn:pbm", "DiskIsUSBFault");
    private final static QName _DiskMoveTypeNotSupportedFault_QNAME = new QName("urn:pbm", "DiskMoveTypeNotSupportedFault");
    private final static QName _DiskNotSupportedFault_QNAME = new QName("urn:pbm", "DiskNotSupportedFault");
    private final static QName _DiskTooSmallFault_QNAME = new QName("urn:pbm", "DiskTooSmallFault");
    private final static QName _DomainNotFoundFault_QNAME = new QName("urn:pbm", "DomainNotFoundFault");
    private final static QName _DrsDisabledOnVmFault_QNAME = new QName("urn:pbm", "DrsDisabledOnVmFault");
    private final static QName _DrsVmotionIncompatibleFaultFault_QNAME = new QName("urn:pbm", "DrsVmotionIncompatibleFaultFault");
    private final static QName _DuplicateDisksFault_QNAME = new QName("urn:pbm", "DuplicateDisksFault");
    private final static QName _DuplicateNameFault_QNAME = new QName("urn:pbm", "DuplicateNameFault");
    private final static QName _DuplicateVsanNetworkInterfaceFault_QNAME = new QName("urn:pbm", "DuplicateVsanNetworkInterfaceFault");
    private final static QName _DvsApplyOperationFaultFault_QNAME = new QName("urn:pbm", "DvsApplyOperationFaultFault");
    private final static QName _DvsFaultFault_QNAME = new QName("urn:pbm", "DvsFaultFault");
    private final static QName _DvsNotAuthorizedFault_QNAME = new QName("urn:pbm", "DvsNotAuthorizedFault");
    private final static QName _DvsOperationBulkFaultFault_QNAME = new QName("urn:pbm", "DvsOperationBulkFaultFault");
    private final static QName _DvsScopeViolatedFault_QNAME = new QName("urn:pbm", "DvsScopeViolatedFault");
    private final static QName _EVCAdmissionFailedFault_QNAME = new QName("urn:pbm", "EVCAdmissionFailedFault");
    private final static QName _EVCAdmissionFailedCPUFeaturesForModeFault_QNAME = new QName("urn:pbm", "EVCAdmissionFailedCPUFeaturesForModeFault");
    private final static QName _EVCAdmissionFailedCPUModelFault_QNAME = new QName("urn:pbm", "EVCAdmissionFailedCPUModelFault");
    private final static QName _EVCAdmissionFailedCPUModelForModeFault_QNAME = new QName("urn:pbm", "EVCAdmissionFailedCPUModelForModeFault");
    private final static QName _EVCAdmissionFailedCPUVendorFault_QNAME = new QName("urn:pbm", "EVCAdmissionFailedCPUVendorFault");
    private final static QName _EVCAdmissionFailedCPUVendorUnknownFault_QNAME = new QName("urn:pbm", "EVCAdmissionFailedCPUVendorUnknownFault");
    private final static QName _EVCAdmissionFailedHostDisconnectedFault_QNAME = new QName("urn:pbm", "EVCAdmissionFailedHostDisconnectedFault");
    private final static QName _EVCAdmissionFailedHostSoftwareFault_QNAME = new QName("urn:pbm", "EVCAdmissionFailedHostSoftwareFault");
    private final static QName _EVCAdmissionFailedHostSoftwareForModeFault_QNAME = new QName("urn:pbm", "EVCAdmissionFailedHostSoftwareForModeFault");
    private final static QName _EVCAdmissionFailedVmActiveFault_QNAME = new QName("urn:pbm", "EVCAdmissionFailedVmActiveFault");
    private final static QName _EVCConfigFaultFault_QNAME = new QName("urn:pbm", "EVCConfigFaultFault");
    private final static QName _EVCModeIllegalByVendorFault_QNAME = new QName("urn:pbm", "EVCModeIllegalByVendorFault");
    private final static QName _EVCModeUnsupportedByHostsFault_QNAME = new QName("urn:pbm", "EVCModeUnsupportedByHostsFault");
    private final static QName _EVCUnsupportedByHostHardwareFault_QNAME = new QName("urn:pbm", "EVCUnsupportedByHostHardwareFault");
    private final static QName _EVCUnsupportedByHostSoftwareFault_QNAME = new QName("urn:pbm", "EVCUnsupportedByHostSoftwareFault");
    private final static QName _EightHostLimitViolatedFault_QNAME = new QName("urn:pbm", "EightHostLimitViolatedFault");
    private final static QName _ExpiredAddonLicenseFault_QNAME = new QName("urn:pbm", "ExpiredAddonLicenseFault");
    private final static QName _ExpiredEditionLicenseFault_QNAME = new QName("urn:pbm", "ExpiredEditionLicenseFault");
    private final static QName _ExpiredFeatureLicenseFault_QNAME = new QName("urn:pbm", "ExpiredFeatureLicenseFault");
    private final static QName _ExtendedFaultFault_QNAME = new QName("urn:pbm", "ExtendedFaultFault");
    private final static QName _FailToEnableSPBMFault_QNAME = new QName("urn:pbm", "FailToEnableSPBMFault");
    private final static QName _FailToLockFaultToleranceVMsFault_QNAME = new QName("urn:pbm", "FailToLockFaultToleranceVMsFault");
    private final static QName _FaultToleranceAntiAffinityViolatedFault_QNAME = new QName("urn:pbm", "FaultToleranceAntiAffinityViolatedFault");
    private final static QName _FaultToleranceCannotEditMemFault_QNAME = new QName("urn:pbm", "FaultToleranceCannotEditMemFault");
    private final static QName _FaultToleranceCpuIncompatibleFault_QNAME = new QName("urn:pbm", "FaultToleranceCpuIncompatibleFault");
    private final static QName _FaultToleranceNeedsThickDiskFault_QNAME = new QName("urn:pbm", "FaultToleranceNeedsThickDiskFault");
    private final static QName _FaultToleranceNotLicensedFault_QNAME = new QName("urn:pbm", "FaultToleranceNotLicensedFault");
    private final static QName _FaultToleranceNotSameBuildFault_QNAME = new QName("urn:pbm", "FaultToleranceNotSameBuildFault");
    private final static QName _FaultTolerancePrimaryPowerOnNotAttemptedFault_QNAME = new QName("urn:pbm", "FaultTolerancePrimaryPowerOnNotAttemptedFault");
    private final static QName _FaultToleranceVmNotDasProtectedFault_QNAME = new QName("urn:pbm", "FaultToleranceVmNotDasProtectedFault");
    private final static QName _FcoeFaultFault_QNAME = new QName("urn:pbm", "FcoeFaultFault");
    private final static QName _FcoeFaultPnicHasNoPortSetFault_QNAME = new QName("urn:pbm", "FcoeFaultPnicHasNoPortSetFault");
    private final static QName _FeatureRequirementsNotMetFault_QNAME = new QName("urn:pbm", "FeatureRequirementsNotMetFault");
    private final static QName _FileAlreadyExistsFault_QNAME = new QName("urn:pbm", "FileAlreadyExistsFault");
    private final static QName _FileBackedPortNotSupportedFault_QNAME = new QName("urn:pbm", "FileBackedPortNotSupportedFault");
    private final static QName _FileFaultFault_QNAME = new QName("urn:pbm", "FileFaultFault");
    private final static QName _FileLockedFault_QNAME = new QName("urn:pbm", "FileLockedFault");
    private final static QName _FileNameTooLongFault_QNAME = new QName("urn:pbm", "FileNameTooLongFault");
    private final static QName _FileNotFoundFault_QNAME = new QName("urn:pbm", "FileNotFoundFault");
    private final static QName _FileNotWritableFault_QNAME = new QName("urn:pbm", "FileNotWritableFault");
    private final static QName _FileTooLargeFault_QNAME = new QName("urn:pbm", "FileTooLargeFault");
    private final static QName _FilesystemQuiesceFaultFault_QNAME = new QName("urn:pbm", "FilesystemQuiesceFaultFault");
    private final static QName _FilterInUseFault_QNAME = new QName("urn:pbm", "FilterInUseFault");
    private final static QName _FtIssuesOnHostFault_QNAME = new QName("urn:pbm", "FtIssuesOnHostFault");
    private final static QName _FullStorageVMotionNotSupportedFault_QNAME = new QName("urn:pbm", "FullStorageVMotionNotSupportedFault");
    private final static QName _GatewayConnectFaultFault_QNAME = new QName("urn:pbm", "GatewayConnectFaultFault");
    private final static QName _GatewayHostNotReachableFault_QNAME = new QName("urn:pbm", "GatewayHostNotReachableFault");
    private final static QName _GatewayNotFoundFault_QNAME = new QName("urn:pbm", "GatewayNotFoundFault");
    private final static QName _GatewayNotReachableFault_QNAME = new QName("urn:pbm", "GatewayNotReachableFault");
    private final static QName _GatewayOperationRefusedFault_QNAME = new QName("urn:pbm", "GatewayOperationRefusedFault");
    private final static QName _GatewayToHostAuthFaultFault_QNAME = new QName("urn:pbm", "GatewayToHostAuthFaultFault");
    private final static QName _GatewayToHostConnectFaultFault_QNAME = new QName("urn:pbm", "GatewayToHostConnectFaultFault");
    private final static QName _GatewayToHostTrustVerifyFaultFault_QNAME = new QName("urn:pbm", "GatewayToHostTrustVerifyFaultFault");
    private final static QName _GenericDrsFaultFault_QNAME = new QName("urn:pbm", "GenericDrsFaultFault");
    private final static QName _GenericVmConfigFaultFault_QNAME = new QName("urn:pbm", "GenericVmConfigFaultFault");
    private final static QName _GuestAuthenticationChallengeFault_QNAME = new QName("urn:pbm", "GuestAuthenticationChallengeFault");
    private final static QName _GuestComponentsOutOfDateFault_QNAME = new QName("urn:pbm", "GuestComponentsOutOfDateFault");
    private final static QName _GuestMultipleMappingsFault_QNAME = new QName("urn:pbm", "GuestMultipleMappingsFault");
    private final static QName _GuestOperationsFaultFault_QNAME = new QName("urn:pbm", "GuestOperationsFaultFault");
    private final static QName _GuestOperationsUnavailableFault_QNAME = new QName("urn:pbm", "GuestOperationsUnavailableFault");
    private final static QName _GuestPermissionDeniedFault_QNAME = new QName("urn:pbm", "GuestPermissionDeniedFault");
    private final static QName _GuestProcessNotFoundFault_QNAME = new QName("urn:pbm", "GuestProcessNotFoundFault");
    private final static QName _GuestRegistryFaultFault_QNAME = new QName("urn:pbm", "GuestRegistryFaultFault");
    private final static QName _GuestRegistryKeyAlreadyExistsFault_QNAME = new QName("urn:pbm", "GuestRegistryKeyAlreadyExistsFault");
    private final static QName _GuestRegistryKeyFaultFault_QNAME = new QName("urn:pbm", "GuestRegistryKeyFaultFault");
    private final static QName _GuestRegistryKeyHasSubkeysFault_QNAME = new QName("urn:pbm", "GuestRegistryKeyHasSubkeysFault");
    private final static QName _GuestRegistryKeyInvalidFault_QNAME = new QName("urn:pbm", "GuestRegistryKeyInvalidFault");
    private final static QName _GuestRegistryKeyParentVolatileFault_QNAME = new QName("urn:pbm", "GuestRegistryKeyParentVolatileFault");
    private final static QName _GuestRegistryValueFaultFault_QNAME = new QName("urn:pbm", "GuestRegistryValueFaultFault");
    private final static QName _GuestRegistryValueNotFoundFault_QNAME = new QName("urn:pbm", "GuestRegistryValueNotFoundFault");
    private final static QName _HAErrorsAtDestFault_QNAME = new QName("urn:pbm", "HAErrorsAtDestFault");
    private final static QName _HeterogenousHostsBlockingEVCFault_QNAME = new QName("urn:pbm", "HeterogenousHostsBlockingEVCFault");
    private final static QName _HostAccessRestrictedToManagementServerFault_QNAME = new QName("urn:pbm", "HostAccessRestrictedToManagementServerFault");
    private final static QName _HostConfigFailedFault_QNAME = new QName("urn:pbm", "HostConfigFailedFault");
    private final static QName _HostConfigFaultFault_QNAME = new QName("urn:pbm", "HostConfigFaultFault");
    private final static QName _HostConnectFaultFault_QNAME = new QName("urn:pbm", "HostConnectFaultFault");
    private final static QName _HostHasComponentFailureFault_QNAME = new QName("urn:pbm", "HostHasComponentFailureFault");
    private final static QName _HostInDomainFault_QNAME = new QName("urn:pbm", "HostInDomainFault");
    private final static QName _HostIncompatibleForFaultToleranceFault_QNAME = new QName("urn:pbm", "HostIncompatibleForFaultToleranceFault");
    private final static QName _HostIncompatibleForRecordReplayFault_QNAME = new QName("urn:pbm", "HostIncompatibleForRecordReplayFault");
    private final static QName _HostInventoryFullFault_QNAME = new QName("urn:pbm", "HostInventoryFullFault");
    private final static QName _HostPowerOpFailedFault_QNAME = new QName("urn:pbm", "HostPowerOpFailedFault");
    private final static QName _HostSpecificationOperationFailedFault_QNAME = new QName("urn:pbm", "HostSpecificationOperationFailedFault");
    private final static QName _HotSnapshotMoveNotSupportedFault_QNAME = new QName("urn:pbm", "HotSnapshotMoveNotSupportedFault");
    private final static QName _IDEDiskNotSupportedFault_QNAME = new QName("urn:pbm", "IDEDiskNotSupportedFault");
    private final static QName _IORMNotSupportedHostOnDatastoreFault_QNAME = new QName("urn:pbm", "IORMNotSupportedHostOnDatastoreFault");
    private final static QName _ImportHostAddFailureFault_QNAME = new QName("urn:pbm", "ImportHostAddFailureFault");
    private final static QName _ImportOperationBulkFaultFault_QNAME = new QName("urn:pbm", "ImportOperationBulkFaultFault");
    private final static QName _InUseFeatureManipulationDisallowedFault_QNAME = new QName("urn:pbm", "InUseFeatureManipulationDisallowedFault");
    private final static QName _InaccessibleDatastoreFault_QNAME = new QName("urn:pbm", "InaccessibleDatastoreFault");
    private final static QName _InaccessibleFTMetadataDatastoreFault_QNAME = new QName("urn:pbm", "InaccessibleFTMetadataDatastoreFault");
    private final static QName _InaccessibleVFlashSourceFault_QNAME = new QName("urn:pbm", "InaccessibleVFlashSourceFault");
    private final static QName _IncompatibleDefaultDeviceFault_QNAME = new QName("urn:pbm", "IncompatibleDefaultDeviceFault");
    private final static QName _IncompatibleHostForFtSecondaryFault_QNAME = new QName("urn:pbm", "IncompatibleHostForFtSecondaryFault");
    private final static QName _IncompatibleHostForVmReplicationFault_QNAME = new QName("urn:pbm", "IncompatibleHostForVmReplicationFault");
    private final static QName _IncompatibleSettingFault_QNAME = new QName("urn:pbm", "IncompatibleSettingFault");
    private final static QName _IncorrectFileTypeFault_QNAME = new QName("urn:pbm", "IncorrectFileTypeFault");
    private final static QName _IncorrectHostInformationFault_QNAME = new QName("urn:pbm", "IncorrectHostInformationFault");
    private final static QName _IndependentDiskVMotionNotSupportedFault_QNAME = new QName("urn:pbm", "IndependentDiskVMotionNotSupportedFault");
    private final static QName _InsufficientAgentVmsDeployedFault_QNAME = new QName("urn:pbm", "InsufficientAgentVmsDeployedFault");
    private final static QName _InsufficientCpuResourcesFaultFault_QNAME = new QName("urn:pbm", "InsufficientCpuResourcesFaultFault");
    private final static QName _InsufficientDisksFault_QNAME = new QName("urn:pbm", "InsufficientDisksFault");
    private final static QName _InsufficientFailoverResourcesFaultFault_QNAME = new QName("urn:pbm", "InsufficientFailoverResourcesFaultFault");
    private final static QName _InsufficientGraphicsResourcesFaultFault_QNAME = new QName("urn:pbm", "InsufficientGraphicsResourcesFaultFault");
    private final static QName _InsufficientHostCapacityFaultFault_QNAME = new QName("urn:pbm", "InsufficientHostCapacityFaultFault");
    private final static QName _InsufficientHostCpuCapacityFaultFault_QNAME = new QName("urn:pbm", "InsufficientHostCpuCapacityFaultFault");
    private final static QName _InsufficientHostMemoryCapacityFaultFault_QNAME = new QName("urn:pbm", "InsufficientHostMemoryCapacityFaultFault");
    private final static QName _InsufficientMemoryResourcesFaultFault_QNAME = new QName("urn:pbm", "InsufficientMemoryResourcesFaultFault");
    private final static QName _InsufficientNetworkCapacityFault_QNAME = new QName("urn:pbm", "InsufficientNetworkCapacityFault");
    private final static QName _InsufficientNetworkResourcePoolCapacityFault_QNAME = new QName("urn:pbm", "InsufficientNetworkResourcePoolCapacityFault");
    private final static QName _InsufficientPerCpuCapacityFault_QNAME = new QName("urn:pbm", "InsufficientPerCpuCapacityFault");
    private final static QName _InsufficientResourcesFaultFault_QNAME = new QName("urn:pbm", "InsufficientResourcesFaultFault");
    private final static QName _InsufficientStandbyCpuResourceFault_QNAME = new QName("urn:pbm", "InsufficientStandbyCpuResourceFault");
    private final static QName _InsufficientStandbyMemoryResourceFault_QNAME = new QName("urn:pbm", "InsufficientStandbyMemoryResourceFault");
    private final static QName _InsufficientStandbyResourceFault_QNAME = new QName("urn:pbm", "InsufficientStandbyResourceFault");
    private final static QName _InsufficientStorageIopsFault_QNAME = new QName("urn:pbm", "InsufficientStorageIopsFault");
    private final static QName _InsufficientStorageSpaceFault_QNAME = new QName("urn:pbm", "InsufficientStorageSpaceFault");
    private final static QName _InsufficientVFlashResourcesFaultFault_QNAME = new QName("urn:pbm", "InsufficientVFlashResourcesFaultFault");
    private final static QName _InvalidAffinitySettingFaultFault_QNAME = new QName("urn:pbm", "InvalidAffinitySettingFaultFault");
    private final static QName _InvalidBmcRoleFault_QNAME = new QName("urn:pbm", "InvalidBmcRoleFault");
    private final static QName _InvalidBundleFault_QNAME = new QName("urn:pbm", "InvalidBundleFault");
    private final static QName _InvalidCAMCertificateFault_QNAME = new QName("urn:pbm", "InvalidCAMCertificateFault");
    private final static QName _InvalidCAMServerFault_QNAME = new QName("urn:pbm", "InvalidCAMServerFault");
    private final static QName _InvalidClientCertificateFault_QNAME = new QName("urn:pbm", "InvalidClientCertificateFault");
    private final static QName _InvalidControllerFault_QNAME = new QName("urn:pbm", "InvalidControllerFault");
    private final static QName _InvalidDasConfigArgumentFault_QNAME = new QName("urn:pbm", "InvalidDasConfigArgumentFault");
    private final static QName _InvalidDasRestartPriorityForFtVmFault_QNAME = new QName("urn:pbm", "InvalidDasRestartPriorityForFtVmFault");
    private final static QName _InvalidDatastoreFault_QNAME = new QName("urn:pbm", "InvalidDatastoreFault");
    private final static QName _InvalidDatastorePathFault_QNAME = new QName("urn:pbm", "InvalidDatastorePathFault");
    private final static QName _InvalidDatastoreStateFault_QNAME = new QName("urn:pbm", "InvalidDatastoreStateFault");
    private final static QName _InvalidDeviceBackingFault_QNAME = new QName("urn:pbm", "InvalidDeviceBackingFault");
    private final static QName _InvalidDeviceOperationFault_QNAME = new QName("urn:pbm", "InvalidDeviceOperationFault");
    private final static QName _InvalidDeviceSpecFault_QNAME = new QName("urn:pbm", "InvalidDeviceSpecFault");
    private final static QName _InvalidDiskFormatFault_QNAME = new QName("urn:pbm", "InvalidDiskFormatFault");
    private final static QName _InvalidDrsBehaviorForFtVmFault_QNAME = new QName("urn:pbm", "InvalidDrsBehaviorForFtVmFault");
    private final static QName _InvalidEditionLicenseFault_QNAME = new QName("urn:pbm", "InvalidEditionLicenseFault");
    private final static QName _InvalidEventFault_QNAME = new QName("urn:pbm", "InvalidEventFault");
    private final static QName _InvalidFolderFault_QNAME = new QName("urn:pbm", "InvalidFolderFault");
    private final static QName _InvalidFormatFault_QNAME = new QName("urn:pbm", "InvalidFormatFault");
    private final static QName _InvalidGuestLoginFault_QNAME = new QName("urn:pbm", "InvalidGuestLoginFault");
    private final static QName _InvalidHostConnectionStateFault_QNAME = new QName("urn:pbm", "InvalidHostConnectionStateFault");
    private final static QName _InvalidHostNameFault_QNAME = new QName("urn:pbm", "InvalidHostNameFault");
    private final static QName _InvalidHostStateFault_QNAME = new QName("urn:pbm", "InvalidHostStateFault");
    private final static QName _InvalidIndexArgumentFault_QNAME = new QName("urn:pbm", "InvalidIndexArgumentFault");
    private final static QName _InvalidIpfixConfigFault_QNAME = new QName("urn:pbm", "InvalidIpfixConfigFault");
    private final static QName _InvalidIpmiLoginInfoFault_QNAME = new QName("urn:pbm", "InvalidIpmiLoginInfoFault");
    private final static QName _InvalidIpmiMacAddressFault_QNAME = new QName("urn:pbm", "InvalidIpmiMacAddressFault");
    private final static QName _InvalidLicenseFault_QNAME = new QName("urn:pbm", "InvalidLicenseFault");
    private final static QName _InvalidLocaleFault_QNAME = new QName("urn:pbm", "InvalidLocaleFault");
    private final static QName _InvalidLoginFault_QNAME = new QName("urn:pbm", "InvalidLoginFault");
    private final static QName _InvalidNameFault_QNAME = new QName("urn:pbm", "InvalidNameFault");
    private final static QName _InvalidNasCredentialsFault_QNAME = new QName("urn:pbm", "InvalidNasCredentialsFault");
    private final static QName _InvalidNetworkInTypeFault_QNAME = new QName("urn:pbm", "InvalidNetworkInTypeFault");
    private final static QName _InvalidNetworkResourceFault_QNAME = new QName("urn:pbm", "InvalidNetworkResourceFault");
    private final static QName _InvalidOperationOnSecondaryVmFault_QNAME = new QName("urn:pbm", "InvalidOperationOnSecondaryVmFault");
    private final static QName _InvalidPowerStateFault_QNAME = new QName("urn:pbm", "InvalidPowerStateFault");
    private final static QName _InvalidPrivilegeFault_QNAME = new QName("urn:pbm", "InvalidPrivilegeFault");
    private final static QName _InvalidProfileReferenceHostFault_QNAME = new QName("urn:pbm", "InvalidProfileReferenceHostFault");
    private final static QName _InvalidPropertyTypeFault_QNAME = new QName("urn:pbm", "InvalidPropertyTypeFault");
    private final static QName _InvalidPropertyValueFault_QNAME = new QName("urn:pbm", "InvalidPropertyValueFault");
    private final static QName _InvalidResourcePoolStructureFaultFault_QNAME = new QName("urn:pbm", "InvalidResourcePoolStructureFaultFault");
    private final static QName _InvalidSnapshotFormatFault_QNAME = new QName("urn:pbm", "InvalidSnapshotFormatFault");
    private final static QName _InvalidStateFault_QNAME = new QName("urn:pbm", "InvalidStateFault");
    private final static QName _InvalidVmConfigFault_QNAME = new QName("urn:pbm", "InvalidVmConfigFault");
    private final static QName _InvalidVmStateFault_QNAME = new QName("urn:pbm", "InvalidVmStateFault");
    private final static QName _InventoryHasStandardAloneHostsFault_QNAME = new QName("urn:pbm", "InventoryHasStandardAloneHostsFault");
    private final static QName _IpHostnameGeneratorErrorFault_QNAME = new QName("urn:pbm", "IpHostnameGeneratorErrorFault");
    private final static QName _IscsiFaultFault_QNAME = new QName("urn:pbm", "IscsiFaultFault");
    private final static QName _IscsiFaultInvalidVnicFault_QNAME = new QName("urn:pbm", "IscsiFaultInvalidVnicFault");
    private final static QName _IscsiFaultPnicInUseFault_QNAME = new QName("urn:pbm", "IscsiFaultPnicInUseFault");
    private final static QName _IscsiFaultVnicAlreadyBoundFault_QNAME = new QName("urn:pbm", "IscsiFaultVnicAlreadyBoundFault");
    private final static QName _IscsiFaultVnicHasActivePathsFault_QNAME = new QName("urn:pbm", "IscsiFaultVnicHasActivePathsFault");
    private final static QName _IscsiFaultVnicHasMultipleUplinksFault_QNAME = new QName("urn:pbm", "IscsiFaultVnicHasMultipleUplinksFault");
    private final static QName _IscsiFaultVnicHasNoUplinksFault_QNAME = new QName("urn:pbm", "IscsiFaultVnicHasNoUplinksFault");
    private final static QName _IscsiFaultVnicHasWrongUplinkFault_QNAME = new QName("urn:pbm", "IscsiFaultVnicHasWrongUplinkFault");
    private final static QName _IscsiFaultVnicInUseFault_QNAME = new QName("urn:pbm", "IscsiFaultVnicInUseFault");
    private final static QName _IscsiFaultVnicIsLastPathFault_QNAME = new QName("urn:pbm", "IscsiFaultVnicIsLastPathFault");
    private final static QName _IscsiFaultVnicNotBoundFault_QNAME = new QName("urn:pbm", "IscsiFaultVnicNotBoundFault");
    private final static QName _IscsiFaultVnicNotFoundFault_QNAME = new QName("urn:pbm", "IscsiFaultVnicNotFoundFault");
    private final static QName _LargeRDMConversionNotSupportedFault_QNAME = new QName("urn:pbm", "LargeRDMConversionNotSupportedFault");
    private final static QName _LargeRDMNotSupportedOnDatastoreFault_QNAME = new QName("urn:pbm", "LargeRDMNotSupportedOnDatastoreFault");
    private final static QName _LegacyNetworkInterfaceInUseFault_QNAME = new QName("urn:pbm", "LegacyNetworkInterfaceInUseFault");
    private final static QName _LicenseAssignmentFailedFault_QNAME = new QName("urn:pbm", "LicenseAssignmentFailedFault");
    private final static QName _LicenseDowngradeDisallowedFault_QNAME = new QName("urn:pbm", "LicenseDowngradeDisallowedFault");
    private final static QName _LicenseEntityNotFoundFault_QNAME = new QName("urn:pbm", "LicenseEntityNotFoundFault");
    private final static QName _LicenseExpiredFault_QNAME = new QName("urn:pbm", "LicenseExpiredFault");
    private final static QName _LicenseKeyEntityMismatchFault_QNAME = new QName("urn:pbm", "LicenseKeyEntityMismatchFault");
    private final static QName _LicenseRestrictedFault_QNAME = new QName("urn:pbm", "LicenseRestrictedFault");
    private final static QName _LicenseServerUnavailableFault_QNAME = new QName("urn:pbm", "LicenseServerUnavailableFault");
    private final static QName _LicenseSourceUnavailableFault_QNAME = new QName("urn:pbm", "LicenseSourceUnavailableFault");
    private final static QName _LimitExceededFault_QNAME = new QName("urn:pbm", "LimitExceededFault");
    private final static QName _LinuxVolumeNotCleanFault_QNAME = new QName("urn:pbm", "LinuxVolumeNotCleanFault");
    private final static QName _LogBundlingFailedFault_QNAME = new QName("urn:pbm", "LogBundlingFailedFault");
    private final static QName _MaintenanceModeFileMoveFault_QNAME = new QName("urn:pbm", "MaintenanceModeFileMoveFault");
    private final static QName _MemoryFileFormatNotSupportedByDatastoreFault_QNAME = new QName("urn:pbm", "MemoryFileFormatNotSupportedByDatastoreFault");
    private final static QName _MemoryHotPlugNotSupportedFault_QNAME = new QName("urn:pbm", "MemoryHotPlugNotSupportedFault");
    private final static QName _MemorySizeNotRecommendedFault_QNAME = new QName("urn:pbm", "MemorySizeNotRecommendedFault");
    private final static QName _MemorySizeNotSupportedFault_QNAME = new QName("urn:pbm", "MemorySizeNotSupportedFault");
    private final static QName _MemorySizeNotSupportedByDatastoreFault_QNAME = new QName("urn:pbm", "MemorySizeNotSupportedByDatastoreFault");
    private final static QName _MemorySnapshotOnIndependentDiskFault_QNAME = new QName("urn:pbm", "MemorySnapshotOnIndependentDiskFault");
    private final static QName _MethodAlreadyDisabledFaultFault_QNAME = new QName("urn:pbm", "MethodAlreadyDisabledFaultFault");
    private final static QName _MethodDisabledFault_QNAME = new QName("urn:pbm", "MethodDisabledFault");
    private final static QName _MigrationDisabledFault_QNAME = new QName("urn:pbm", "MigrationDisabledFault");
    private final static QName _MigrationFaultFault_QNAME = new QName("urn:pbm", "MigrationFaultFault");
    private final static QName _MigrationFeatureNotSupportedFault_QNAME = new QName("urn:pbm", "MigrationFeatureNotSupportedFault");
    private final static QName _MigrationNotReadyFault_QNAME = new QName("urn:pbm", "MigrationNotReadyFault");
    private final static QName _MismatchedBundleFault_QNAME = new QName("urn:pbm", "MismatchedBundleFault");
    private final static QName _MismatchedNetworkPoliciesFault_QNAME = new QName("urn:pbm", "MismatchedNetworkPoliciesFault");
    private final static QName _MismatchedVMotionNetworkNamesFault_QNAME = new QName("urn:pbm", "MismatchedVMotionNetworkNamesFault");
    private final static QName _MissingBmcSupportFault_QNAME = new QName("urn:pbm", "MissingBmcSupportFault");
    private final static QName _MissingControllerFault_QNAME = new QName("urn:pbm", "MissingControllerFault");
    private final static QName _MissingIpPoolFault_QNAME = new QName("urn:pbm", "MissingIpPoolFault");
    private final static QName _MissingLinuxCustResourcesFault_QNAME = new QName("urn:pbm", "MissingLinuxCustResourcesFault");
    private final static QName _MissingNetworkIpConfigFault_QNAME = new QName("urn:pbm", "MissingNetworkIpConfigFault");
    private final static QName _MissingPowerOffConfigurationFault_QNAME = new QName("urn:pbm", "MissingPowerOffConfigurationFault");
    private final static QName _MissingPowerOnConfigurationFault_QNAME = new QName("urn:pbm", "MissingPowerOnConfigurationFault");
    private final static QName _MissingWindowsCustResourcesFault_QNAME = new QName("urn:pbm", "MissingWindowsCustResourcesFault");
    private final static QName _MksConnectionLimitReachedFault_QNAME = new QName("urn:pbm", "MksConnectionLimitReachedFault");
    private final static QName _MountErrorFault_QNAME = new QName("urn:pbm", "MountErrorFault");
    private final static QName _MultiWriterNotSupportedFault_QNAME = new QName("urn:pbm", "MultiWriterNotSupportedFault");
    private final static QName _MultipleCertificatesVerifyFaultFault_QNAME = new QName("urn:pbm", "MultipleCertificatesVerifyFaultFault");
    private final static QName _MultipleSnapshotsNotSupportedFault_QNAME = new QName("urn:pbm", "MultipleSnapshotsNotSupportedFault");
    private final static QName _NamespaceFullFault_QNAME = new QName("urn:pbm", "NamespaceFullFault");
    private final static QName _NamespaceLimitReachedFault_QNAME = new QName("urn:pbm", "NamespaceLimitReachedFault");
    private final static QName _NamespaceWriteProtectedFault_QNAME = new QName("urn:pbm", "NamespaceWriteProtectedFault");
    private final static QName _NasConfigFaultFault_QNAME = new QName("urn:pbm", "NasConfigFaultFault");
    private final static QName _NasConnectionLimitReachedFault_QNAME = new QName("urn:pbm", "NasConnectionLimitReachedFault");
    private final static QName _NasSessionCredentialConflictFault_QNAME = new QName("urn:pbm", "NasSessionCredentialConflictFault");
    private final static QName _NasVolumeNotMountedFault_QNAME = new QName("urn:pbm", "NasVolumeNotMountedFault");
    private final static QName _NetworkCopyFaultFault_QNAME = new QName("urn:pbm", "NetworkCopyFaultFault");
    private final static QName _NetworkDisruptedAndConfigRolledBackFault_QNAME = new QName("urn:pbm", "NetworkDisruptedAndConfigRolledBackFault");
    private final static QName _NetworkInaccessibleFault_QNAME = new QName("urn:pbm", "NetworkInaccessibleFault");
    private final static QName _NetworksMayNotBeTheSameFault_QNAME = new QName("urn:pbm", "NetworksMayNotBeTheSameFault");
    private final static QName _NicSettingMismatchFault_QNAME = new QName("urn:pbm", "NicSettingMismatchFault");
    private final static QName _NoActiveHostInClusterFault_QNAME = new QName("urn:pbm", "NoActiveHostInClusterFault");
    private final static QName _NoAvailableIpFault_QNAME = new QName("urn:pbm", "NoAvailableIpFault");
    private final static QName _NoClientCertificateFault_QNAME = new QName("urn:pbm", "NoClientCertificateFault");
    private final static QName _NoCompatibleDatastoreFault_QNAME = new QName("urn:pbm", "NoCompatibleDatastoreFault");
    private final static QName _NoCompatibleHardAffinityHostFault_QNAME = new QName("urn:pbm", "NoCompatibleHardAffinityHostFault");
    private final static QName _NoCompatibleHostFault_QNAME = new QName("urn:pbm", "NoCompatibleHostFault");
    private final static QName _NoCompatibleHostWithAccessToDeviceFault_QNAME = new QName("urn:pbm", "NoCompatibleHostWithAccessToDeviceFault");
    private final static QName _NoCompatibleSoftAffinityHostFault_QNAME = new QName("urn:pbm", "NoCompatibleSoftAffinityHostFault");
    private final static QName _NoConnectedDatastoreFault_QNAME = new QName("urn:pbm", "NoConnectedDatastoreFault");
    private final static QName _NoDiskFoundFault_QNAME = new QName("urn:pbm", "NoDiskFoundFault");
    private final static QName _NoDiskSpaceFault_QNAME = new QName("urn:pbm", "NoDiskSpaceFault");
    private final static QName _NoDisksToCustomizeFault_QNAME = new QName("urn:pbm", "NoDisksToCustomizeFault");
    private final static QName _NoGatewayFault_QNAME = new QName("urn:pbm", "NoGatewayFault");
    private final static QName _NoGuestHeartbeatFault_QNAME = new QName("urn:pbm", "NoGuestHeartbeatFault");
    private final static QName _NoHostFault_QNAME = new QName("urn:pbm", "NoHostFault");
    private final static QName _NoHostSuitableForFtSecondaryFault_QNAME = new QName("urn:pbm", "NoHostSuitableForFtSecondaryFault");
    private final static QName _NoLicenseServerConfiguredFault_QNAME = new QName("urn:pbm", "NoLicenseServerConfiguredFault");
    private final static QName _NoPeerHostFoundFault_QNAME = new QName("urn:pbm", "NoPeerHostFoundFault");
    private final static QName _NoPermissionFault_QNAME = new QName("urn:pbm", "NoPermissionFault");
    private final static QName _NoPermissionOnADFault_QNAME = new QName("urn:pbm", "NoPermissionOnADFault");
    private final static QName _NoPermissionOnHostFault_QNAME = new QName("urn:pbm", "NoPermissionOnHostFault");
    private final static QName _NoPermissionOnNasVolumeFault_QNAME = new QName("urn:pbm", "NoPermissionOnNasVolumeFault");
    private final static QName _NoSubjectNameFault_QNAME = new QName("urn:pbm", "NoSubjectNameFault");
    private final static QName _NoVcManagedIpConfiguredFault_QNAME = new QName("urn:pbm", "NoVcManagedIpConfiguredFault");
    private final static QName _NoVirtualNicFault_QNAME = new QName("urn:pbm", "NoVirtualNicFault");
    private final static QName _NoVmInVAppFault_QNAME = new QName("urn:pbm", "NoVmInVAppFault");
    private final static QName _NonADUserRequiredFault_QNAME = new QName("urn:pbm", "NonADUserRequiredFault");
    private final static QName _NonHomeRDMVMotionNotSupportedFault_QNAME = new QName("urn:pbm", "NonHomeRDMVMotionNotSupportedFault");
    private final static QName _NonPersistentDisksNotSupportedFault_QNAME = new QName("urn:pbm", "NonPersistentDisksNotSupportedFault");
    private final static QName _NonVmwareOuiMacNotSupportedHostFault_QNAME = new QName("urn:pbm", "NonVmwareOuiMacNotSupportedHostFault");
    private final static QName _NotADirectoryFault_QNAME = new QName("urn:pbm", "NotADirectoryFault");
    private final static QName _NotAFileFault_QNAME = new QName("urn:pbm", "NotAFileFault");
    private final static QName _NotAuthenticatedFault_QNAME = new QName("urn:pbm", "NotAuthenticatedFault");
    private final static QName _NotEnoughCpusFault_QNAME = new QName("urn:pbm", "NotEnoughCpusFault");
    private final static QName _NotEnoughLogicalCpusFault_QNAME = new QName("urn:pbm", "NotEnoughLogicalCpusFault");
    private final static QName _NotFoundFault_QNAME = new QName("urn:pbm", "NotFoundFault");
    private final static QName _NotSupportedDeviceForFTFault_QNAME = new QName("urn:pbm", "NotSupportedDeviceForFTFault");
    private final static QName _NotSupportedHostFault_QNAME = new QName("urn:pbm", "NotSupportedHostFault");
    private final static QName _NotSupportedHostForChecksumFault_QNAME = new QName("urn:pbm", "NotSupportedHostForChecksumFault");
    private final static QName _NotSupportedHostForVFlashFault_QNAME = new QName("urn:pbm", "NotSupportedHostForVFlashFault");
    private final static QName _NotSupportedHostForVmcpFault_QNAME = new QName("urn:pbm", "NotSupportedHostForVmcpFault");
    private final static QName _NotSupportedHostForVmemFileFault_QNAME = new QName("urn:pbm", "NotSupportedHostForVmemFileFault");
    private final static QName _NotSupportedHostForVsanFault_QNAME = new QName("urn:pbm", "NotSupportedHostForVsanFault");
    private final static QName _NotSupportedHostInClusterFault_QNAME = new QName("urn:pbm", "NotSupportedHostInClusterFault");
    private final static QName _NotSupportedHostInDvsFault_QNAME = new QName("urn:pbm", "NotSupportedHostInDvsFault");
    private final static QName _NotSupportedHostInHAClusterFault_QNAME = new QName("urn:pbm", "NotSupportedHostInHAClusterFault");
    private final static QName _NotUserConfigurablePropertyFault_QNAME = new QName("urn:pbm", "NotUserConfigurablePropertyFault");
    private final static QName _NumVirtualCoresPerSocketNotSupportedFault_QNAME = new QName("urn:pbm", "NumVirtualCoresPerSocketNotSupportedFault");
    private final static QName _NumVirtualCpusExceedsLimitFault_QNAME = new QName("urn:pbm", "NumVirtualCpusExceedsLimitFault");
    private final static QName _NumVirtualCpusIncompatibleFault_QNAME = new QName("urn:pbm", "NumVirtualCpusIncompatibleFault");
    private final static QName _NumVirtualCpusNotSupportedFault_QNAME = new QName("urn:pbm", "NumVirtualCpusNotSupportedFault");
    private final static QName _OperationDisabledByGuestFault_QNAME = new QName("urn:pbm", "OperationDisabledByGuestFault");
    private final static QName _OperationDisallowedOnHostFault_QNAME = new QName("urn:pbm", "OperationDisallowedOnHostFault");
    private final static QName _OperationNotSupportedByGuestFault_QNAME = new QName("urn:pbm", "OperationNotSupportedByGuestFault");
    private final static QName _OutOfBoundsFault_QNAME = new QName("urn:pbm", "OutOfBoundsFault");
    private final static QName _OvfAttributeFault_QNAME = new QName("urn:pbm", "OvfAttributeFault");
    private final static QName _OvfConnectedDeviceFault_QNAME = new QName("urn:pbm", "OvfConnectedDeviceFault");
    private final static QName _OvfConnectedDeviceFloppyFault_QNAME = new QName("urn:pbm", "OvfConnectedDeviceFloppyFault");
    private final static QName _OvfConnectedDeviceIsoFault_QNAME = new QName("urn:pbm", "OvfConnectedDeviceIsoFault");
    private final static QName _OvfConstraintFault_QNAME = new QName("urn:pbm", "OvfConstraintFault");
    private final static QName _OvfConsumerCallbackFaultFault_QNAME = new QName("urn:pbm", "OvfConsumerCallbackFaultFault");
    private final static QName _OvfConsumerCommunicationErrorFault_QNAME = new QName("urn:pbm", "OvfConsumerCommunicationErrorFault");
    private final static QName _OvfConsumerFaultFault_QNAME = new QName("urn:pbm", "OvfConsumerFaultFault");
    private final static QName _OvfConsumerInvalidSectionFault_QNAME = new QName("urn:pbm", "OvfConsumerInvalidSectionFault");
    private final static QName _OvfConsumerPowerOnFaultFault_QNAME = new QName("urn:pbm", "OvfConsumerPowerOnFaultFault");
    private final static QName _OvfConsumerUndeclaredSectionFault_QNAME = new QName("urn:pbm", "OvfConsumerUndeclaredSectionFault");
    private final static QName _OvfConsumerUndefinedPrefixFault_QNAME = new QName("urn:pbm", "OvfConsumerUndefinedPrefixFault");
    private final static QName _OvfConsumerValidationFaultFault_QNAME = new QName("urn:pbm", "OvfConsumerValidationFaultFault");
    private final static QName _OvfCpuCompatibilityFault_QNAME = new QName("urn:pbm", "OvfCpuCompatibilityFault");
    private final static QName _OvfCpuCompatibilityCheckNotSupportedFault_QNAME = new QName("urn:pbm", "OvfCpuCompatibilityCheckNotSupportedFault");
    private final static QName _OvfDiskMappingNotFoundFault_QNAME = new QName("urn:pbm", "OvfDiskMappingNotFoundFault");
    private final static QName _OvfDiskOrderConstraintFault_QNAME = new QName("urn:pbm", "OvfDiskOrderConstraintFault");
    private final static QName _OvfDuplicateElementFault_QNAME = new QName("urn:pbm", "OvfDuplicateElementFault");
    private final static QName _OvfDuplicatedElementBoundaryFault_QNAME = new QName("urn:pbm", "OvfDuplicatedElementBoundaryFault");
    private final static QName _OvfDuplicatedPropertyIdExportFault_QNAME = new QName("urn:pbm", "OvfDuplicatedPropertyIdExportFault");
    private final static QName _OvfDuplicatedPropertyIdImportFault_QNAME = new QName("urn:pbm", "OvfDuplicatedPropertyIdImportFault");
    private final static QName _OvfElementFault_QNAME = new QName("urn:pbm", "OvfElementFault");
    private final static QName _OvfElementInvalidValueFault_QNAME = new QName("urn:pbm", "OvfElementInvalidValueFault");
    private final static QName _OvfExportFault_QNAME = new QName("urn:pbm", "OvfExportFault");
    private final static QName _OvfExportFailedFault_QNAME = new QName("urn:pbm", "OvfExportFailedFault");
    private final static QName _OvfFaultFault_QNAME = new QName("urn:pbm", "OvfFaultFault");
    private final static QName _OvfHardwareCheckFault_QNAME = new QName("urn:pbm", "OvfHardwareCheckFault");
    private final static QName _OvfHardwareExportFault_QNAME = new QName("urn:pbm", "OvfHardwareExportFault");
    private final static QName _OvfHostResourceConstraintFault_QNAME = new QName("urn:pbm", "OvfHostResourceConstraintFault");
    private final static QName _OvfHostValueNotParsedFault_QNAME = new QName("urn:pbm", "OvfHostValueNotParsedFault");
    private final static QName _OvfImportFault_QNAME = new QName("urn:pbm", "OvfImportFault");
    private final static QName _OvfImportFailedFault_QNAME = new QName("urn:pbm", "OvfImportFailedFault");
    private final static QName _OvfInternalErrorFault_QNAME = new QName("urn:pbm", "OvfInternalErrorFault");
    private final static QName _OvfInvalidPackageFault_QNAME = new QName("urn:pbm", "OvfInvalidPackageFault");
    private final static QName _OvfInvalidValueFault_QNAME = new QName("urn:pbm", "OvfInvalidValueFault");
    private final static QName _OvfInvalidValueConfigurationFault_QNAME = new QName("urn:pbm", "OvfInvalidValueConfigurationFault");
    private final static QName _OvfInvalidValueEmptyFault_QNAME = new QName("urn:pbm", "OvfInvalidValueEmptyFault");
    private final static QName _OvfInvalidValueFormatMalformedFault_QNAME = new QName("urn:pbm", "OvfInvalidValueFormatMalformedFault");
    private final static QName _OvfInvalidValueReferenceFault_QNAME = new QName("urn:pbm", "OvfInvalidValueReferenceFault");
    private final static QName _OvfInvalidVmNameFault_QNAME = new QName("urn:pbm", "OvfInvalidVmNameFault");
    private final static QName _OvfMappedOsIdFault_QNAME = new QName("urn:pbm", "OvfMappedOsIdFault");
    private final static QName _OvfMissingAttributeFault_QNAME = new QName("urn:pbm", "OvfMissingAttributeFault");
    private final static QName _OvfMissingElementFault_QNAME = new QName("urn:pbm", "OvfMissingElementFault");
    private final static QName _OvfMissingElementNormalBoundaryFault_QNAME = new QName("urn:pbm", "OvfMissingElementNormalBoundaryFault");
    private final static QName _OvfMissingHardwareFault_QNAME = new QName("urn:pbm", "OvfMissingHardwareFault");
    private final static QName _OvfNetworkMappingNotSupportedFault_QNAME = new QName("urn:pbm", "OvfNetworkMappingNotSupportedFault");
    private final static QName _OvfNoHostNicFault_QNAME = new QName("urn:pbm", "OvfNoHostNicFault");
    private final static QName _OvfNoSpaceOnControllerFault_QNAME = new QName("urn:pbm", "OvfNoSpaceOnControllerFault");
    private final static QName _OvfNoSupportedHardwareFamilyFault_QNAME = new QName("urn:pbm", "OvfNoSupportedHardwareFamilyFault");
    private final static QName _OvfPropertyFault_QNAME = new QName("urn:pbm", "OvfPropertyFault");
    private final static QName _OvfPropertyExportFault_QNAME = new QName("urn:pbm", "OvfPropertyExportFault");
    private final static QName _OvfPropertyNetworkFault_QNAME = new QName("urn:pbm", "OvfPropertyNetworkFault");
    private final static QName _OvfPropertyNetworkExportFault_QNAME = new QName("urn:pbm", "OvfPropertyNetworkExportFault");
    private final static QName _OvfPropertyQualifierFault_QNAME = new QName("urn:pbm", "OvfPropertyQualifierFault");
    private final static QName _OvfPropertyQualifierDuplicateFault_QNAME = new QName("urn:pbm", "OvfPropertyQualifierDuplicateFault");
    private final static QName _OvfPropertyQualifierIgnoredFault_QNAME = new QName("urn:pbm", "OvfPropertyQualifierIgnoredFault");
    private final static QName _OvfPropertyTypeFault_QNAME = new QName("urn:pbm", "OvfPropertyTypeFault");
    private final static QName _OvfPropertyValueFault_QNAME = new QName("urn:pbm", "OvfPropertyValueFault");
    private final static QName _OvfSystemFaultFault_QNAME = new QName("urn:pbm", "OvfSystemFaultFault");
    private final static QName _OvfToXmlUnsupportedElementFault_QNAME = new QName("urn:pbm", "OvfToXmlUnsupportedElementFault");
    private final static QName _OvfUnableToExportDiskFault_QNAME = new QName("urn:pbm", "OvfUnableToExportDiskFault");
    private final static QName _OvfUnexpectedElementFault_QNAME = new QName("urn:pbm", "OvfUnexpectedElementFault");
    private final static QName _OvfUnknownDeviceFault_QNAME = new QName("urn:pbm", "OvfUnknownDeviceFault");
    private final static QName _OvfUnknownDeviceBackingFault_QNAME = new QName("urn:pbm", "OvfUnknownDeviceBackingFault");
    private final static QName _OvfUnknownEntityFault_QNAME = new QName("urn:pbm", "OvfUnknownEntityFault");
    private final static QName _OvfUnsupportedAttributeFault_QNAME = new QName("urn:pbm", "OvfUnsupportedAttributeFault");
    private final static QName _OvfUnsupportedAttributeValueFault_QNAME = new QName("urn:pbm", "OvfUnsupportedAttributeValueFault");
    private final static QName _OvfUnsupportedDeviceBackingInfoFault_QNAME = new QName("urn:pbm", "OvfUnsupportedDeviceBackingInfoFault");
    private final static QName _OvfUnsupportedDeviceBackingOptionFault_QNAME = new QName("urn:pbm", "OvfUnsupportedDeviceBackingOptionFault");
    private final static QName _OvfUnsupportedDeviceExportFault_QNAME = new QName("urn:pbm", "OvfUnsupportedDeviceExportFault");
    private final static QName _OvfUnsupportedDiskProvisioningFault_QNAME = new QName("urn:pbm", "OvfUnsupportedDiskProvisioningFault");
    private final static QName _OvfUnsupportedElementFault_QNAME = new QName("urn:pbm", "OvfUnsupportedElementFault");
    private final static QName _OvfUnsupportedElementValueFault_QNAME = new QName("urn:pbm", "OvfUnsupportedElementValueFault");
    private final static QName _OvfUnsupportedPackageFault_QNAME = new QName("urn:pbm", "OvfUnsupportedPackageFault");
    private final static QName _OvfUnsupportedSectionFault_QNAME = new QName("urn:pbm", "OvfUnsupportedSectionFault");
    private final static QName _OvfUnsupportedSubTypeFault_QNAME = new QName("urn:pbm", "OvfUnsupportedSubTypeFault");
    private final static QName _OvfUnsupportedTypeFault_QNAME = new QName("urn:pbm", "OvfUnsupportedTypeFault");
    private final static QName _OvfWrongElementFault_QNAME = new QName("urn:pbm", "OvfWrongElementFault");
    private final static QName _OvfWrongNamespaceFault_QNAME = new QName("urn:pbm", "OvfWrongNamespaceFault");
    private final static QName _OvfXmlFormatFault_QNAME = new QName("urn:pbm", "OvfXmlFormatFault");
    private final static QName _PatchAlreadyInstalledFault_QNAME = new QName("urn:pbm", "PatchAlreadyInstalledFault");
    private final static QName _PatchBinariesNotFoundFault_QNAME = new QName("urn:pbm", "PatchBinariesNotFoundFault");
    private final static QName _PatchInstallFailedFault_QNAME = new QName("urn:pbm", "PatchInstallFailedFault");
    private final static QName _PatchIntegrityErrorFault_QNAME = new QName("urn:pbm", "PatchIntegrityErrorFault");
    private final static QName _PatchMetadataCorruptedFault_QNAME = new QName("urn:pbm", "PatchMetadataCorruptedFault");
    private final static QName _PatchMetadataInvalidFault_QNAME = new QName("urn:pbm", "PatchMetadataInvalidFault");
    private final static QName _PatchMetadataNotFoundFault_QNAME = new QName("urn:pbm", "PatchMetadataNotFoundFault");
    private final static QName _PatchMissingDependenciesFault_QNAME = new QName("urn:pbm", "PatchMissingDependenciesFault");
    private final static QName _PatchNotApplicableFault_QNAME = new QName("urn:pbm", "PatchNotApplicableFault");
    private final static QName _PatchSupersededFault_QNAME = new QName("urn:pbm", "PatchSupersededFault");
    private final static QName _PhysCompatRDMNotSupportedFault_QNAME = new QName("urn:pbm", "PhysCompatRDMNotSupportedFault");
    private final static QName _PlatformConfigFaultFault_QNAME = new QName("urn:pbm", "PlatformConfigFaultFault");
    private final static QName _PowerOnFtSecondaryFailedFault_QNAME = new QName("urn:pbm", "PowerOnFtSecondaryFailedFault");
    private final static QName _PowerOnFtSecondaryTimedoutFault_QNAME = new QName("urn:pbm", "PowerOnFtSecondaryTimedoutFault");
    private final static QName _ProfileUpdateFailedFault_QNAME = new QName("urn:pbm", "ProfileUpdateFailedFault");
    private final static QName _QuarantineModeFaultFault_QNAME = new QName("urn:pbm", "QuarantineModeFaultFault");
    private final static QName _QuestionPendingFault_QNAME = new QName("urn:pbm", "QuestionPendingFault");
    private final static QName _QuiesceDatastoreIOForHAFailedFault_QNAME = new QName("urn:pbm", "QuiesceDatastoreIOForHAFailedFault");
    private final static QName _RDMConversionNotSupportedFault_QNAME = new QName("urn:pbm", "RDMConversionNotSupportedFault");
    private final static QName _RDMNotPreservedFault_QNAME = new QName("urn:pbm", "RDMNotPreservedFault");
    private final static QName _RDMNotSupportedFault_QNAME = new QName("urn:pbm", "RDMNotSupportedFault");
    private final static QName _RDMNotSupportedOnDatastoreFault_QNAME = new QName("urn:pbm", "RDMNotSupportedOnDatastoreFault");
    private final static QName _RDMPointsToInaccessibleDiskFault_QNAME = new QName("urn:pbm", "RDMPointsToInaccessibleDiskFault");
    private final static QName _RawDiskNotSupportedFault_QNAME = new QName("urn:pbm", "RawDiskNotSupportedFault");
    private final static QName _ReadHostResourcePoolTreeFailedFault_QNAME = new QName("urn:pbm", "ReadHostResourcePoolTreeFailedFault");
    private final static QName _ReadOnlyDisksWithLegacyDestinationFault_QNAME = new QName("urn:pbm", "ReadOnlyDisksWithLegacyDestinationFault");
    private final static QName _RebootRequiredFault_QNAME = new QName("urn:pbm", "RebootRequiredFault");
    private final static QName _RecordReplayDisabledFault_QNAME = new QName("urn:pbm", "RecordReplayDisabledFault");
    private final static QName _RemoteDeviceNotSupportedFault_QNAME = new QName("urn:pbm", "RemoteDeviceNotSupportedFault");
    private final static QName _RemoveFailedFault_QNAME = new QName("urn:pbm", "RemoveFailedFault");
    private final static QName _ReplicationConfigFaultFault_QNAME = new QName("urn:pbm", "ReplicationConfigFaultFault");
    private final static QName _ReplicationDiskConfigFaultFault_QNAME = new QName("urn:pbm", "ReplicationDiskConfigFaultFault");
    private final static QName _ReplicationFaultFault_QNAME = new QName("urn:pbm", "ReplicationFaultFault");
    private final static QName _ReplicationIncompatibleWithFTFault_QNAME = new QName("urn:pbm", "ReplicationIncompatibleWithFTFault");
    private final static QName _ReplicationInvalidOptionsFault_QNAME = new QName("urn:pbm", "ReplicationInvalidOptionsFault");
    private final static QName _ReplicationNotSupportedOnHostFault_QNAME = new QName("urn:pbm", "ReplicationNotSupportedOnHostFault");
    private final static QName _ReplicationVmConfigFaultFault_QNAME = new QName("urn:pbm", "ReplicationVmConfigFaultFault");
    private final static QName _ReplicationVmFaultFault_QNAME = new QName("urn:pbm", "ReplicationVmFaultFault");
    private final static QName _ReplicationVmInProgressFaultFault_QNAME = new QName("urn:pbm", "ReplicationVmInProgressFaultFault");
    private final static QName _ResourceInUseFault_QNAME = new QName("urn:pbm", "ResourceInUseFault");
    private final static QName _ResourceNotAvailableFault_QNAME = new QName("urn:pbm", "ResourceNotAvailableFault");
    private final static QName _RestrictedByAdministratorFault_QNAME = new QName("urn:pbm", "RestrictedByAdministratorFault");
    private final static QName _RestrictedVersionFault_QNAME = new QName("urn:pbm", "RestrictedVersionFault");
    private final static QName _RollbackFailureFault_QNAME = new QName("urn:pbm", "RollbackFailureFault");
    private final static QName _RuleViolationFault_QNAME = new QName("urn:pbm", "RuleViolationFault");
    private final static QName _SSLDisabledFaultFault_QNAME = new QName("urn:pbm", "SSLDisabledFaultFault");
    private final static QName _SSLVerifyFaultFault_QNAME = new QName("urn:pbm", "SSLVerifyFaultFault");
    private final static QName _SSPIChallengeFault_QNAME = new QName("urn:pbm", "SSPIChallengeFault");
    private final static QName _SecondaryVmAlreadyDisabledFault_QNAME = new QName("urn:pbm", "SecondaryVmAlreadyDisabledFault");
    private final static QName _SecondaryVmAlreadyEnabledFault_QNAME = new QName("urn:pbm", "SecondaryVmAlreadyEnabledFault");
    private final static QName _SecondaryVmAlreadyRegisteredFault_QNAME = new QName("urn:pbm", "SecondaryVmAlreadyRegisteredFault");
    private final static QName _SecondaryVmNotRegisteredFault_QNAME = new QName("urn:pbm", "SecondaryVmNotRegisteredFault");
    private final static QName _SharedBusControllerNotSupportedFault_QNAME = new QName("urn:pbm", "SharedBusControllerNotSupportedFault");
    private final static QName _ShrinkDiskFaultFault_QNAME = new QName("urn:pbm", "ShrinkDiskFaultFault");
    private final static QName _SnapshotCloneNotSupportedFault_QNAME = new QName("urn:pbm", "SnapshotCloneNotSupportedFault");
    private final static QName _SnapshotCopyNotSupportedFault_QNAME = new QName("urn:pbm", "SnapshotCopyNotSupportedFault");
    private final static QName _SnapshotDisabledFault_QNAME = new QName("urn:pbm", "SnapshotDisabledFault");
    private final static QName _SnapshotFaultFault_QNAME = new QName("urn:pbm", "SnapshotFaultFault");
    private final static QName _SnapshotIncompatibleDeviceInVmFault_QNAME = new QName("urn:pbm", "SnapshotIncompatibleDeviceInVmFault");
    private final static QName _SnapshotLockedFault_QNAME = new QName("urn:pbm", "SnapshotLockedFault");
    private final static QName _SnapshotMoveFromNonHomeNotSupportedFault_QNAME = new QName("urn:pbm", "SnapshotMoveFromNonHomeNotSupportedFault");
    private final static QName _SnapshotMoveNotSupportedFault_QNAME = new QName("urn:pbm", "SnapshotMoveNotSupportedFault");
    private final static QName _SnapshotMoveToNonHomeNotSupportedFault_QNAME = new QName("urn:pbm", "SnapshotMoveToNonHomeNotSupportedFault");
    private final static QName _SnapshotNoChangeFault_QNAME = new QName("urn:pbm", "SnapshotNoChangeFault");
    private final static QName _SnapshotRevertIssueFault_QNAME = new QName("urn:pbm", "SnapshotRevertIssueFault");
    private final static QName _SoftRuleVioCorrectionDisallowedFault_QNAME = new QName("urn:pbm", "SoftRuleVioCorrectionDisallowedFault");
    private final static QName _SoftRuleVioCorrectionImpactFault_QNAME = new QName("urn:pbm", "SoftRuleVioCorrectionImpactFault");
    private final static QName _SsdDiskNotAvailableFault_QNAME = new QName("urn:pbm", "SsdDiskNotAvailableFault");
    private final static QName _StorageDrsCannotMoveDiskInMultiWriterModeFault_QNAME = new QName("urn:pbm", "StorageDrsCannotMoveDiskInMultiWriterModeFault");
    private final static QName _StorageDrsCannotMoveFTVmFault_QNAME = new QName("urn:pbm", "StorageDrsCannotMoveFTVmFault");
    private final static QName _StorageDrsCannotMoveIndependentDiskFault_QNAME = new QName("urn:pbm", "StorageDrsCannotMoveIndependentDiskFault");
    private final static QName _StorageDrsCannotMoveManuallyPlacedSwapFileFault_QNAME = new QName("urn:pbm", "StorageDrsCannotMoveManuallyPlacedSwapFileFault");
    private final static QName _StorageDrsCannotMoveManuallyPlacedVmFault_QNAME = new QName("urn:pbm", "StorageDrsCannotMoveManuallyPlacedVmFault");
    private final static QName _StorageDrsCannotMoveSharedDiskFault_QNAME = new QName("urn:pbm", "StorageDrsCannotMoveSharedDiskFault");
    private final static QName _StorageDrsCannotMoveTemplateFault_QNAME = new QName("urn:pbm", "StorageDrsCannotMoveTemplateFault");
    private final static QName _StorageDrsCannotMoveVmInUserFolderFault_QNAME = new QName("urn:pbm", "StorageDrsCannotMoveVmInUserFolderFault");
    private final static QName _StorageDrsCannotMoveVmWithMountedCDROMFault_QNAME = new QName("urn:pbm", "StorageDrsCannotMoveVmWithMountedCDROMFault");
    private final static QName _StorageDrsCannotMoveVmWithNoFilesInLayoutFault_QNAME = new QName("urn:pbm", "StorageDrsCannotMoveVmWithNoFilesInLayoutFault");
    private final static QName _StorageDrsDatacentersCannotShareDatastoreFault_QNAME = new QName("urn:pbm", "StorageDrsDatacentersCannotShareDatastoreFault");
    private final static QName _StorageDrsDisabledOnVmFault_QNAME = new QName("urn:pbm", "StorageDrsDisabledOnVmFault");
    private final static QName _StorageDrsHbrDiskNotMovableFault_QNAME = new QName("urn:pbm", "StorageDrsHbrDiskNotMovableFault");
    private final static QName _StorageDrsHmsMoveInProgressFault_QNAME = new QName("urn:pbm", "StorageDrsHmsMoveInProgressFault");
    private final static QName _StorageDrsHmsUnreachableFault_QNAME = new QName("urn:pbm", "StorageDrsHmsUnreachableFault");
    private final static QName _StorageDrsIolbDisabledInternallyFault_QNAME = new QName("urn:pbm", "StorageDrsIolbDisabledInternallyFault");
    private final static QName _StorageDrsRelocateDisabledFault_QNAME = new QName("urn:pbm", "StorageDrsRelocateDisabledFault");
    private final static QName _StorageDrsStaleHmsCollectionFault_QNAME = new QName("urn:pbm", "StorageDrsStaleHmsCollectionFault");
    private final static QName _StorageDrsUnableToMoveFilesFault_QNAME = new QName("urn:pbm", "StorageDrsUnableToMoveFilesFault");
    private final static QName _StorageVMotionNotSupportedFault_QNAME = new QName("urn:pbm", "StorageVMotionNotSupportedFault");
    private final static QName _StorageVmotionIncompatibleFault_QNAME = new QName("urn:pbm", "StorageVmotionIncompatibleFault");
    private final static QName _SuspendedRelocateNotSupportedFault_QNAME = new QName("urn:pbm", "SuspendedRelocateNotSupportedFault");
    private final static QName _SwapDatastoreNotWritableOnHostFault_QNAME = new QName("urn:pbm", "SwapDatastoreNotWritableOnHostFault");
    private final static QName _SwapDatastoreUnsetFault_QNAME = new QName("urn:pbm", "SwapDatastoreUnsetFault");
    private final static QName _SwapPlacementOverrideNotSupportedFault_QNAME = new QName("urn:pbm", "SwapPlacementOverrideNotSupportedFault");
    private final static QName _SwitchIpUnsetFault_QNAME = new QName("urn:pbm", "SwitchIpUnsetFault");
    private final static QName _SwitchNotInUpgradeModeFault_QNAME = new QName("urn:pbm", "SwitchNotInUpgradeModeFault");
    private final static QName _TaskInProgressFault_QNAME = new QName("urn:pbm", "TaskInProgressFault");
    private final static QName _ThirdPartyLicenseAssignmentFailedFault_QNAME = new QName("urn:pbm", "ThirdPartyLicenseAssignmentFailedFault");
    private final static QName _TimedoutFault_QNAME = new QName("urn:pbm", "TimedoutFault");
    private final static QName _TooManyConcurrentNativeClonesFault_QNAME = new QName("urn:pbm", "TooManyConcurrentNativeClonesFault");
    private final static QName _TooManyConsecutiveOverridesFault_QNAME = new QName("urn:pbm", "TooManyConsecutiveOverridesFault");
    private final static QName _TooManyDevicesFault_QNAME = new QName("urn:pbm", "TooManyDevicesFault");
    private final static QName _TooManyDisksOnLegacyHostFault_QNAME = new QName("urn:pbm", "TooManyDisksOnLegacyHostFault");
    private final static QName _TooManyGuestLogonsFault_QNAME = new QName("urn:pbm", "TooManyGuestLogonsFault");
    private final static QName _TooManyHostsFault_QNAME = new QName("urn:pbm", "TooManyHostsFault");
    private final static QName _TooManyNativeCloneLevelsFault_QNAME = new QName("urn:pbm", "TooManyNativeCloneLevelsFault");
    private final static QName _TooManyNativeClonesOnFileFault_QNAME = new QName("urn:pbm", "TooManyNativeClonesOnFileFault");
    private final static QName _TooManySnapshotLevelsFault_QNAME = new QName("urn:pbm", "TooManySnapshotLevelsFault");
    private final static QName _ToolsAlreadyUpgradedFault_QNAME = new QName("urn:pbm", "ToolsAlreadyUpgradedFault");
    private final static QName _ToolsAutoUpgradeNotSupportedFault_QNAME = new QName("urn:pbm", "ToolsAutoUpgradeNotSupportedFault");
    private final static QName _ToolsImageCopyFailedFault_QNAME = new QName("urn:pbm", "ToolsImageCopyFailedFault");
    private final static QName _ToolsImageNotAvailableFault_QNAME = new QName("urn:pbm", "ToolsImageNotAvailableFault");
    private final static QName _ToolsImageSignatureCheckFailedFault_QNAME = new QName("urn:pbm", "ToolsImageSignatureCheckFailedFault");
    private final static QName _ToolsInstallationInProgressFault_QNAME = new QName("urn:pbm", "ToolsInstallationInProgressFault");
    private final static QName _ToolsUnavailableFault_QNAME = new QName("urn:pbm", "ToolsUnavailableFault");
    private final static QName _ToolsUpgradeCancelledFault_QNAME = new QName("urn:pbm", "ToolsUpgradeCancelledFault");
    private final static QName _UnSupportedDatastoreForVFlashFault_QNAME = new QName("urn:pbm", "UnSupportedDatastoreForVFlashFault");
    private final static QName _UncommittedUndoableDiskFault_QNAME = new QName("urn:pbm", "UncommittedUndoableDiskFault");
    private final static QName _UnconfiguredPropertyValueFault_QNAME = new QName("urn:pbm", "UnconfiguredPropertyValueFault");
    private final static QName _UncustomizableGuestFault_QNAME = new QName("urn:pbm", "UncustomizableGuestFault");
    private final static QName _UnexpectedCustomizationFaultFault_QNAME = new QName("urn:pbm", "UnexpectedCustomizationFaultFault");
    private final static QName _UnrecognizedHostFault_QNAME = new QName("urn:pbm", "UnrecognizedHostFault");
    private final static QName _UnsharedSwapVMotionNotSupportedFault_QNAME = new QName("urn:pbm", "UnsharedSwapVMotionNotSupportedFault");
    private final static QName _UnsupportedDatastoreFault_QNAME = new QName("urn:pbm", "UnsupportedDatastoreFault");
    private final static QName _UnsupportedGuestFault_QNAME = new QName("urn:pbm", "UnsupportedGuestFault");
    private final static QName _UnsupportedVimApiVersionFault_QNAME = new QName("urn:pbm", "UnsupportedVimApiVersionFault");
    private final static QName _UnsupportedVmxLocationFault_QNAME = new QName("urn:pbm", "UnsupportedVmxLocationFault");
    private final static QName _UnusedVirtualDiskBlocksNotScrubbedFault_QNAME = new QName("urn:pbm", "UnusedVirtualDiskBlocksNotScrubbedFault");
    private final static QName _UserNotFoundFault_QNAME = new QName("urn:pbm", "UserNotFoundFault");
    private final static QName _VAppConfigFaultFault_QNAME = new QName("urn:pbm", "VAppConfigFaultFault");
    private final static QName _VAppNotRunningFault_QNAME = new QName("urn:pbm", "VAppNotRunningFault");
    private final static QName _VAppOperationInProgressFault_QNAME = new QName("urn:pbm", "VAppOperationInProgressFault");
    private final static QName _VAppPropertyFaultFault_QNAME = new QName("urn:pbm", "VAppPropertyFaultFault");
    private final static QName _VAppTaskInProgressFault_QNAME = new QName("urn:pbm", "VAppTaskInProgressFault");
    private final static QName _VFlashCacheHotConfigNotSupportedFault_QNAME = new QName("urn:pbm", "VFlashCacheHotConfigNotSupportedFault");
    private final static QName _VFlashModuleNotSupportedFault_QNAME = new QName("urn:pbm", "VFlashModuleNotSupportedFault");
    private final static QName _VFlashModuleVersionIncompatibleFault_QNAME = new QName("urn:pbm", "VFlashModuleVersionIncompatibleFault");
    private final static QName _VMINotSupportedFault_QNAME = new QName("urn:pbm", "VMINotSupportedFault");
    private final static QName _VMOnConflictDVPortFault_QNAME = new QName("urn:pbm", "VMOnConflictDVPortFault");
    private final static QName _VMOnVirtualIntranetFault_QNAME = new QName("urn:pbm", "VMOnVirtualIntranetFault");
    private final static QName _VMotionAcrossNetworkNotSupportedFault_QNAME = new QName("urn:pbm", "VMotionAcrossNetworkNotSupportedFault");
    private final static QName _VMotionInterfaceIssueFault_QNAME = new QName("urn:pbm", "VMotionInterfaceIssueFault");
    private final static QName _VMotionLinkCapacityLowFault_QNAME = new QName("urn:pbm", "VMotionLinkCapacityLowFault");
    private final static QName _VMotionLinkDownFault_QNAME = new QName("urn:pbm", "VMotionLinkDownFault");
    private final static QName _VMotionNotConfiguredFault_QNAME = new QName("urn:pbm", "VMotionNotConfiguredFault");
    private final static QName _VMotionNotLicensedFault_QNAME = new QName("urn:pbm", "VMotionNotLicensedFault");
    private final static QName _VMotionNotSupportedFault_QNAME = new QName("urn:pbm", "VMotionNotSupportedFault");
    private final static QName _VMotionProtocolIncompatibleFault_QNAME = new QName("urn:pbm", "VMotionProtocolIncompatibleFault");
    private final static QName _VimFaultFault_QNAME = new QName("urn:pbm", "VimFaultFault");
    private final static QName _VirtualDiskBlocksNotFullyProvisionedFault_QNAME = new QName("urn:pbm", "VirtualDiskBlocksNotFullyProvisionedFault");
    private final static QName _VirtualDiskModeNotSupportedFault_QNAME = new QName("urn:pbm", "VirtualDiskModeNotSupportedFault");
    private final static QName _VirtualEthernetCardNotSupportedFault_QNAME = new QName("urn:pbm", "VirtualEthernetCardNotSupportedFault");
    private final static QName _VirtualHardwareCompatibilityIssueFault_QNAME = new QName("urn:pbm", "VirtualHardwareCompatibilityIssueFault");
    private final static QName _VirtualHardwareVersionNotSupportedFault_QNAME = new QName("urn:pbm", "VirtualHardwareVersionNotSupportedFault");
    private final static QName _VmAlreadyExistsInDatacenterFault_QNAME = new QName("urn:pbm", "VmAlreadyExistsInDatacenterFault");
    private final static QName _VmConfigFaultFault_QNAME = new QName("urn:pbm", "VmConfigFaultFault");
    private final static QName _VmConfigIncompatibleForFaultToleranceFault_QNAME = new QName("urn:pbm", "VmConfigIncompatibleForFaultToleranceFault");
    private final static QName _VmConfigIncompatibleForRecordReplayFault_QNAME = new QName("urn:pbm", "VmConfigIncompatibleForRecordReplayFault");
    private final static QName _VmFaultToleranceConfigIssueFault_QNAME = new QName("urn:pbm", "VmFaultToleranceConfigIssueFault");
    private final static QName _VmFaultToleranceConfigIssueWrapperFault_QNAME = new QName("urn:pbm", "VmFaultToleranceConfigIssueWrapperFault");
    private final static QName _VmFaultToleranceInvalidFileBackingFault_QNAME = new QName("urn:pbm", "VmFaultToleranceInvalidFileBackingFault");
    private final static QName _VmFaultToleranceIssueFault_QNAME = new QName("urn:pbm", "VmFaultToleranceIssueFault");
    private final static QName _VmFaultToleranceOpIssuesListFault_QNAME = new QName("urn:pbm", "VmFaultToleranceOpIssuesListFault");
    private final static QName _VmFaultToleranceTooManyFtVcpusOnHostFault_QNAME = new QName("urn:pbm", "VmFaultToleranceTooManyFtVcpusOnHostFault");
    private final static QName _VmFaultToleranceTooManyVMsOnHostFault_QNAME = new QName("urn:pbm", "VmFaultToleranceTooManyVMsOnHostFault");
    private final static QName _VmHostAffinityRuleViolationFault_QNAME = new QName("urn:pbm", "VmHostAffinityRuleViolationFault");
    private final static QName _VmLimitLicenseFault_QNAME = new QName("urn:pbm", "VmLimitLicenseFault");
    private final static QName _VmMetadataManagerFaultFault_QNAME = new QName("urn:pbm", "VmMetadataManagerFaultFault");
    private final static QName _VmMonitorIncompatibleForFaultToleranceFault_QNAME = new QName("urn:pbm", "VmMonitorIncompatibleForFaultToleranceFault");
    private final static QName _VmPowerOnDisabledFault_QNAME = new QName("urn:pbm", "VmPowerOnDisabledFault");
    private final static QName _VmSmpFaultToleranceTooManyVMsOnHostFault_QNAME = new QName("urn:pbm", "VmSmpFaultToleranceTooManyVMsOnHostFault");
    private final static QName _VmToolsUpgradeFaultFault_QNAME = new QName("urn:pbm", "VmToolsUpgradeFaultFault");
    private final static QName _VmValidateMaxDeviceFault_QNAME = new QName("urn:pbm", "VmValidateMaxDeviceFault");
    private final static QName _VmWwnConflictFault_QNAME = new QName("urn:pbm", "VmWwnConflictFault");
    private final static QName _VmfsAlreadyMountedFault_QNAME = new QName("urn:pbm", "VmfsAlreadyMountedFault");
    private final static QName _VmfsAmbiguousMountFault_QNAME = new QName("urn:pbm", "VmfsAmbiguousMountFault");
    private final static QName _VmfsMountFaultFault_QNAME = new QName("urn:pbm", "VmfsMountFaultFault");
    private final static QName _VmotionInterfaceNotEnabledFault_QNAME = new QName("urn:pbm", "VmotionInterfaceNotEnabledFault");
    private final static QName _VolumeEditorErrorFault_QNAME = new QName("urn:pbm", "VolumeEditorErrorFault");
    private final static QName _VramLimitLicenseFault_QNAME = new QName("urn:pbm", "VramLimitLicenseFault");
    private final static QName _VsanClusterUuidMismatchFault_QNAME = new QName("urn:pbm", "VsanClusterUuidMismatchFault");
    private final static QName _VsanDiskFaultFault_QNAME = new QName("urn:pbm", "VsanDiskFaultFault");
    private final static QName _VsanFaultFault_QNAME = new QName("urn:pbm", "VsanFaultFault");
    private final static QName _VsanIncompatibleDiskMappingFault_QNAME = new QName("urn:pbm", "VsanIncompatibleDiskMappingFault");
    private final static QName _VspanDestPortConflictFault_QNAME = new QName("urn:pbm", "VspanDestPortConflictFault");
    private final static QName _VspanPortConflictFault_QNAME = new QName("urn:pbm", "VspanPortConflictFault");
    private final static QName _VspanPortMoveFaultFault_QNAME = new QName("urn:pbm", "VspanPortMoveFaultFault");
    private final static QName _VspanPortPromiscChangeFaultFault_QNAME = new QName("urn:pbm", "VspanPortPromiscChangeFaultFault");
    private final static QName _VspanPortgroupPromiscChangeFaultFault_QNAME = new QName("urn:pbm", "VspanPortgroupPromiscChangeFaultFault");
    private final static QName _VspanPortgroupTypeChangeFaultFault_QNAME = new QName("urn:pbm", "VspanPortgroupTypeChangeFaultFault");
    private final static QName _VspanPromiscuousPortNotSupportedFault_QNAME = new QName("urn:pbm", "VspanPromiscuousPortNotSupportedFault");
    private final static QName _VspanSameSessionPortConflictFault_QNAME = new QName("urn:pbm", "VspanSameSessionPortConflictFault");
    private final static QName _WakeOnLanNotSupportedFault_QNAME = new QName("urn:pbm", "WakeOnLanNotSupportedFault");
    private final static QName _WakeOnLanNotSupportedByVmotionNICFault_QNAME = new QName("urn:pbm", "WakeOnLanNotSupportedByVmotionNICFault");
    private final static QName _WillLoseHAProtectionFault_QNAME = new QName("urn:pbm", "WillLoseHAProtectionFault");
    private final static QName _WillModifyConfigCpuRequirementsFault_QNAME = new QName("urn:pbm", "WillModifyConfigCpuRequirementsFault");
    private final static QName _WillResetSnapshotDirectoryFault_QNAME = new QName("urn:pbm", "WillResetSnapshotDirectoryFault");
    private final static QName _WipeDiskFaultFault_QNAME = new QName("urn:pbm", "WipeDiskFaultFault");
    private final static QName _InvalidCollectorVersionFault_QNAME = new QName("urn:pbm", "InvalidCollectorVersionFault");
    private final static QName _InvalidPropertyFault_QNAME = new QName("urn:pbm", "InvalidPropertyFault");
    private final static QName _MethodFaultFault_QNAME = new QName("urn:pbm", "MethodFaultFault");
    private final static QName _RuntimeFaultFault_QNAME = new QName("urn:pbm", "RuntimeFaultFault");
    private final static QName _HostCommunicationFault_QNAME = new QName("urn:pbm", "HostCommunicationFault");
    private final static QName _HostNotConnectedFault_QNAME = new QName("urn:pbm", "HostNotConnectedFault");
    private final static QName _HostNotReachableFault_QNAME = new QName("urn:pbm", "HostNotReachableFault");
    private final static QName _InvalidArgumentFault_QNAME = new QName("urn:pbm", "InvalidArgumentFault");
    private final static QName _InvalidRequestFault_QNAME = new QName("urn:pbm", "InvalidRequestFault");
    private final static QName _InvalidTypeFault_QNAME = new QName("urn:pbm", "InvalidTypeFault");
    private final static QName _ManagedObjectNotFoundFault_QNAME = new QName("urn:pbm", "ManagedObjectNotFoundFault");
    private final static QName _MethodNotFoundFault_QNAME = new QName("urn:pbm", "MethodNotFoundFault");
    private final static QName _NotEnoughLicensesFault_QNAME = new QName("urn:pbm", "NotEnoughLicensesFault");
    private final static QName _NotImplementedFault_QNAME = new QName("urn:pbm", "NotImplementedFault");
    private final static QName _NotSupportedFault_QNAME = new QName("urn:pbm", "NotSupportedFault");
    private final static QName _RequestCanceledFault_QNAME = new QName("urn:pbm", "RequestCanceledFault");
    private final static QName _SecurityErrorFault_QNAME = new QName("urn:pbm", "SecurityErrorFault");
    private final static QName _SystemErrorFault_QNAME = new QName("urn:pbm", "SystemErrorFault");
    private final static QName _UnexpectedFaultFault_QNAME = new QName("urn:pbm", "UnexpectedFaultFault");
    private final static QName _PbmRetrieveServiceContent_QNAME = new QName("urn:pbm", "PbmRetrieveServiceContent");
    private final static QName _PbmCheckCompliance_QNAME = new QName("urn:pbm", "PbmCheckCompliance");
    private final static QName _PbmFetchComplianceResult_QNAME = new QName("urn:pbm", "PbmFetchComplianceResult");
    private final static QName _PbmCheckRollupCompliance_QNAME = new QName("urn:pbm", "PbmCheckRollupCompliance");
    private final static QName _PbmFetchRollupComplianceResult_QNAME = new QName("urn:pbm", "PbmFetchRollupComplianceResult");
    private final static QName _PbmQueryByRollupComplianceStatus_QNAME = new QName("urn:pbm", "PbmQueryByRollupComplianceStatus");
    private final static QName _PbmAlreadyExistsFault_QNAME = new QName("urn:pbm", "PbmAlreadyExistsFault");
    private final static QName _PbmCapabilityProfilePropertyMismatchFaultFault_QNAME = new QName("urn:pbm", "PbmCapabilityProfilePropertyMismatchFaultFault");
    private final static QName _PbmCompatibilityCheckFaultFault_QNAME = new QName("urn:pbm", "PbmCompatibilityCheckFaultFault");
    private final static QName _PbmDefaultProfileAppliesFaultFault_QNAME = new QName("urn:pbm", "PbmDefaultProfileAppliesFaultFault");
    private final static QName _PbmDuplicateNameFault_QNAME = new QName("urn:pbm", "PbmDuplicateNameFault");
    private final static QName _PbmIncompatibleVendorSpecificRuleSetFault_QNAME = new QName("urn:pbm", "PbmIncompatibleVendorSpecificRuleSetFault");
    private final static QName _PbmFaultInvalidLoginFault_QNAME = new QName("urn:pbm", "PbmFaultInvalidLoginFault");
    private final static QName _PbmLegacyHubsNotSupportedFault_QNAME = new QName("urn:pbm", "PbmLegacyHubsNotSupportedFault");
    private final static QName _PbmNonExistentHubsFault_QNAME = new QName("urn:pbm", "PbmNonExistentHubsFault");
    private final static QName _PbmFaultNotFoundFault_QNAME = new QName("urn:pbm", "PbmFaultNotFoundFault");
    private final static QName _PbmFaultFault_QNAME = new QName("urn:pbm", "PbmFaultFault");
    private final static QName _PbmFaultProfileStorageFaultFault_QNAME = new QName("urn:pbm", "PbmFaultProfileStorageFaultFault");
    private final static QName _PbmPropertyMismatchFaultFault_QNAME = new QName("urn:pbm", "PbmPropertyMismatchFaultFault");
    private final static QName _PbmResourceInUseFault_QNAME = new QName("urn:pbm", "PbmResourceInUseFault");
    private final static QName _PbmQueryMatchingHub_QNAME = new QName("urn:pbm", "PbmQueryMatchingHub");
    private final static QName _PbmQueryMatchingHubWithSpec_QNAME = new QName("urn:pbm", "PbmQueryMatchingHubWithSpec");
    private final static QName _PbmCheckCompatibility_QNAME = new QName("urn:pbm", "PbmCheckCompatibility");
    private final static QName _PbmCheckCompatibilityWithSpec_QNAME = new QName("urn:pbm", "PbmCheckCompatibilityWithSpec");
    private final static QName _PbmCheckRequirements_QNAME = new QName("urn:pbm", "PbmCheckRequirements");
    private final static QName _PbmFetchResourceType_QNAME = new QName("urn:pbm", "PbmFetchResourceType");
    private final static QName _PbmFetchVendorInfo_QNAME = new QName("urn:pbm", "PbmFetchVendorInfo");
    private final static QName _PbmFetchCapabilityMetadata_QNAME = new QName("urn:pbm", "PbmFetchCapabilityMetadata");
    private final static QName _PbmFetchCapabilitySchema_QNAME = new QName("urn:pbm", "PbmFetchCapabilitySchema");
    private final static QName _PbmCreate_QNAME = new QName("urn:pbm", "PbmCreate");
    private final static QName _PbmUpdate_QNAME = new QName("urn:pbm", "PbmUpdate");
    private final static QName _PbmDelete_QNAME = new QName("urn:pbm", "PbmDelete");
    private final static QName _PbmQueryProfile_QNAME = new QName("urn:pbm", "PbmQueryProfile");
    private final static QName _PbmRetrieveContent_QNAME = new QName("urn:pbm", "PbmRetrieveContent");
    private final static QName _PbmQueryAssociatedProfiles_QNAME = new QName("urn:pbm", "PbmQueryAssociatedProfiles");
    private final static QName _PbmQueryAssociatedProfile_QNAME = new QName("urn:pbm", "PbmQueryAssociatedProfile");
    private final static QName _PbmQueryAssociatedEntity_QNAME = new QName("urn:pbm", "PbmQueryAssociatedEntity");
    private final static QName _PbmQueryDefaultRequirementProfile_QNAME = new QName("urn:pbm", "PbmQueryDefaultRequirementProfile");
    private final static QName _PbmResetDefaultRequirementProfile_QNAME = new QName("urn:pbm", "PbmResetDefaultRequirementProfile");
    private final static QName _PbmAssignDefaultRequirementProfile_QNAME = new QName("urn:pbm", "PbmAssignDefaultRequirementProfile");
    private final static QName _PbmFindApplicableDefaultProfile_QNAME = new QName("urn:pbm", "PbmFindApplicableDefaultProfile");
    private final static QName _PbmQueryDefaultRequirementProfiles_QNAME = new QName("urn:pbm", "PbmQueryDefaultRequirementProfiles");
    private final static QName _PbmResetVSanDefaultProfile_QNAME = new QName("urn:pbm", "PbmResetVSanDefaultProfile");
    private final static QName _PbmQuerySpaceStatsForStorageContainer_QNAME = new QName("urn:pbm", "PbmQuerySpaceStatsForStorageContainer");
    private final static QName _PbmQueryAssociatedEntities_QNAME = new QName("urn:pbm", "PbmQueryAssociatedEntities");
    private final static QName _PbmQueryReplicationGroups_QNAME = new QName("urn:pbm", "PbmQueryReplicationGroups");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: com.vmware.pbm
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link PbmRetrieveServiceContentRequestType }
     * 
     */
    public PbmRetrieveServiceContentRequestType createPbmRetrieveServiceContentRequestType() {
        return new PbmRetrieveServiceContentRequestType();
    }

    /**
     * Create an instance of {@link PbmRetrieveServiceContentResponse }
     * 
     */
    public PbmRetrieveServiceContentResponse createPbmRetrieveServiceContentResponse() {
        return new PbmRetrieveServiceContentResponse();
    }

    /**
     * Create an instance of {@link PbmServiceInstanceContent }
     * 
     */
    public PbmServiceInstanceContent createPbmServiceInstanceContent() {
        return new PbmServiceInstanceContent();
    }

    /**
     * Create an instance of {@link PbmCheckComplianceRequestType }
     * 
     */
    public PbmCheckComplianceRequestType createPbmCheckComplianceRequestType() {
        return new PbmCheckComplianceRequestType();
    }

    /**
     * Create an instance of {@link PbmCheckComplianceResponse }
     * 
     */
    public PbmCheckComplianceResponse createPbmCheckComplianceResponse() {
        return new PbmCheckComplianceResponse();
    }

    /**
     * Create an instance of {@link PbmComplianceResult }
     * 
     */
    public PbmComplianceResult createPbmComplianceResult() {
        return new PbmComplianceResult();
    }

    /**
     * Create an instance of {@link PbmFetchComplianceResultRequestType }
     * 
     */
    public PbmFetchComplianceResultRequestType createPbmFetchComplianceResultRequestType() {
        return new PbmFetchComplianceResultRequestType();
    }

    /**
     * Create an instance of {@link PbmFetchComplianceResultResponse }
     * 
     */
    public PbmFetchComplianceResultResponse createPbmFetchComplianceResultResponse() {
        return new PbmFetchComplianceResultResponse();
    }

    /**
     * Create an instance of {@link PbmCheckRollupComplianceRequestType }
     * 
     */
    public PbmCheckRollupComplianceRequestType createPbmCheckRollupComplianceRequestType() {
        return new PbmCheckRollupComplianceRequestType();
    }

    /**
     * Create an instance of {@link PbmCheckRollupComplianceResponse }
     * 
     */
    public PbmCheckRollupComplianceResponse createPbmCheckRollupComplianceResponse() {
        return new PbmCheckRollupComplianceResponse();
    }

    /**
     * Create an instance of {@link PbmRollupComplianceResult }
     * 
     */
    public PbmRollupComplianceResult createPbmRollupComplianceResult() {
        return new PbmRollupComplianceResult();
    }

    /**
     * Create an instance of {@link PbmFetchRollupComplianceResultRequestType }
     * 
     */
    public PbmFetchRollupComplianceResultRequestType createPbmFetchRollupComplianceResultRequestType() {
        return new PbmFetchRollupComplianceResultRequestType();
    }

    /**
     * Create an instance of {@link PbmFetchRollupComplianceResultResponse }
     * 
     */
    public PbmFetchRollupComplianceResultResponse createPbmFetchRollupComplianceResultResponse() {
        return new PbmFetchRollupComplianceResultResponse();
    }

    /**
     * Create an instance of {@link PbmQueryByRollupComplianceStatusRequestType }
     * 
     */
    public PbmQueryByRollupComplianceStatusRequestType createPbmQueryByRollupComplianceStatusRequestType() {
        return new PbmQueryByRollupComplianceStatusRequestType();
    }

    /**
     * Create an instance of {@link PbmQueryByRollupComplianceStatusResponse }
     * 
     */
    public PbmQueryByRollupComplianceStatusResponse createPbmQueryByRollupComplianceStatusResponse() {
        return new PbmQueryByRollupComplianceStatusResponse();
    }

    /**
     * Create an instance of {@link PbmServerObjectRef }
     * 
     */
    public PbmServerObjectRef createPbmServerObjectRef() {
        return new PbmServerObjectRef();
    }

    /**
     * Create an instance of {@link PbmAlreadyExists }
     * 
     */
    public PbmAlreadyExists createPbmAlreadyExists() {
        return new PbmAlreadyExists();
    }

    /**
     * Create an instance of {@link PbmCapabilityProfilePropertyMismatchFault }
     * 
     */
    public PbmCapabilityProfilePropertyMismatchFault createPbmCapabilityProfilePropertyMismatchFault() {
        return new PbmCapabilityProfilePropertyMismatchFault();
    }

    /**
     * Create an instance of {@link PbmCompatibilityCheckFault }
     * 
     */
    public PbmCompatibilityCheckFault createPbmCompatibilityCheckFault() {
        return new PbmCompatibilityCheckFault();
    }

    /**
     * Create an instance of {@link PbmDefaultProfileAppliesFault }
     * 
     */
    public PbmDefaultProfileAppliesFault createPbmDefaultProfileAppliesFault() {
        return new PbmDefaultProfileAppliesFault();
    }

    /**
     * Create an instance of {@link PbmDuplicateName }
     * 
     */
    public PbmDuplicateName createPbmDuplicateName() {
        return new PbmDuplicateName();
    }

    /**
     * Create an instance of {@link PbmIncompatibleVendorSpecificRuleSet }
     * 
     */
    public PbmIncompatibleVendorSpecificRuleSet createPbmIncompatibleVendorSpecificRuleSet() {
        return new PbmIncompatibleVendorSpecificRuleSet();
    }

    /**
     * Create an instance of {@link PbmFaultInvalidLogin }
     * 
     */
    public PbmFaultInvalidLogin createPbmFaultInvalidLogin() {
        return new PbmFaultInvalidLogin();
    }

    /**
     * Create an instance of {@link PbmLegacyHubsNotSupported }
     * 
     */
    public PbmLegacyHubsNotSupported createPbmLegacyHubsNotSupported() {
        return new PbmLegacyHubsNotSupported();
    }

    /**
     * Create an instance of {@link PbmNonExistentHubs }
     * 
     */
    public PbmNonExistentHubs createPbmNonExistentHubs() {
        return new PbmNonExistentHubs();
    }

    /**
     * Create an instance of {@link PbmFaultNotFound }
     * 
     */
    public PbmFaultNotFound createPbmFaultNotFound() {
        return new PbmFaultNotFound();
    }

    /**
     * Create an instance of {@link PbmFault }
     * 
     */
    public PbmFault createPbmFault() {
        return new PbmFault();
    }

    /**
     * Create an instance of {@link PbmFaultProfileStorageFault }
     * 
     */
    public PbmFaultProfileStorageFault createPbmFaultProfileStorageFault() {
        return new PbmFaultProfileStorageFault();
    }

    /**
     * Create an instance of {@link PbmPropertyMismatchFault }
     * 
     */
    public PbmPropertyMismatchFault createPbmPropertyMismatchFault() {
        return new PbmPropertyMismatchFault();
    }

    /**
     * Create an instance of {@link PbmResourceInUse }
     * 
     */
    public PbmResourceInUse createPbmResourceInUse() {
        return new PbmResourceInUse();
    }

    /**
     * Create an instance of {@link PbmQueryMatchingHubRequestType }
     * 
     */
    public PbmQueryMatchingHubRequestType createPbmQueryMatchingHubRequestType() {
        return new PbmQueryMatchingHubRequestType();
    }

    /**
     * Create an instance of {@link PbmQueryMatchingHubResponse }
     * 
     */
    public PbmQueryMatchingHubResponse createPbmQueryMatchingHubResponse() {
        return new PbmQueryMatchingHubResponse();
    }

    /**
     * Create an instance of {@link PbmPlacementHub }
     * 
     */
    public PbmPlacementHub createPbmPlacementHub() {
        return new PbmPlacementHub();
    }

    /**
     * Create an instance of {@link PbmQueryMatchingHubWithSpecRequestType }
     * 
     */
    public PbmQueryMatchingHubWithSpecRequestType createPbmQueryMatchingHubWithSpecRequestType() {
        return new PbmQueryMatchingHubWithSpecRequestType();
    }

    /**
     * Create an instance of {@link PbmQueryMatchingHubWithSpecResponse }
     * 
     */
    public PbmQueryMatchingHubWithSpecResponse createPbmQueryMatchingHubWithSpecResponse() {
        return new PbmQueryMatchingHubWithSpecResponse();
    }

    /**
     * Create an instance of {@link PbmCheckCompatibilityRequestType }
     * 
     */
    public PbmCheckCompatibilityRequestType createPbmCheckCompatibilityRequestType() {
        return new PbmCheckCompatibilityRequestType();
    }

    /**
     * Create an instance of {@link PbmCheckCompatibilityResponse }
     * 
     */
    public PbmCheckCompatibilityResponse createPbmCheckCompatibilityResponse() {
        return new PbmCheckCompatibilityResponse();
    }

    /**
     * Create an instance of {@link PbmPlacementCompatibilityResult }
     * 
     */
    public PbmPlacementCompatibilityResult createPbmPlacementCompatibilityResult() {
        return new PbmPlacementCompatibilityResult();
    }

    /**
     * Create an instance of {@link PbmCheckCompatibilityWithSpecRequestType }
     * 
     */
    public PbmCheckCompatibilityWithSpecRequestType createPbmCheckCompatibilityWithSpecRequestType() {
        return new PbmCheckCompatibilityWithSpecRequestType();
    }

    /**
     * Create an instance of {@link PbmCheckCompatibilityWithSpecResponse }
     * 
     */
    public PbmCheckCompatibilityWithSpecResponse createPbmCheckCompatibilityWithSpecResponse() {
        return new PbmCheckCompatibilityWithSpecResponse();
    }

    /**
     * Create an instance of {@link PbmCheckRequirementsRequestType }
     * 
     */
    public PbmCheckRequirementsRequestType createPbmCheckRequirementsRequestType() {
        return new PbmCheckRequirementsRequestType();
    }

    /**
     * Create an instance of {@link PbmCheckRequirementsResponse }
     * 
     */
    public PbmCheckRequirementsResponse createPbmCheckRequirementsResponse() {
        return new PbmCheckRequirementsResponse();
    }

    /**
     * Create an instance of {@link PbmFetchResourceTypeRequestType }
     * 
     */
    public PbmFetchResourceTypeRequestType createPbmFetchResourceTypeRequestType() {
        return new PbmFetchResourceTypeRequestType();
    }

    /**
     * Create an instance of {@link PbmFetchResourceTypeResponse }
     * 
     */
    public PbmFetchResourceTypeResponse createPbmFetchResourceTypeResponse() {
        return new PbmFetchResourceTypeResponse();
    }

    /**
     * Create an instance of {@link PbmProfileResourceType }
     * 
     */
    public PbmProfileResourceType createPbmProfileResourceType() {
        return new PbmProfileResourceType();
    }

    /**
     * Create an instance of {@link PbmFetchVendorInfoRequestType }
     * 
     */
    public PbmFetchVendorInfoRequestType createPbmFetchVendorInfoRequestType() {
        return new PbmFetchVendorInfoRequestType();
    }

    /**
     * Create an instance of {@link PbmFetchVendorInfoResponse }
     * 
     */
    public PbmFetchVendorInfoResponse createPbmFetchVendorInfoResponse() {
        return new PbmFetchVendorInfoResponse();
    }

    /**
     * Create an instance of {@link PbmCapabilityVendorResourceTypeInfo }
     * 
     */
    public PbmCapabilityVendorResourceTypeInfo createPbmCapabilityVendorResourceTypeInfo() {
        return new PbmCapabilityVendorResourceTypeInfo();
    }

    /**
     * Create an instance of {@link PbmFetchCapabilityMetadataRequestType }
     * 
     */
    public PbmFetchCapabilityMetadataRequestType createPbmFetchCapabilityMetadataRequestType() {
        return new PbmFetchCapabilityMetadataRequestType();
    }

    /**
     * Create an instance of {@link PbmFetchCapabilityMetadataResponse }
     * 
     */
    public PbmFetchCapabilityMetadataResponse createPbmFetchCapabilityMetadataResponse() {
        return new PbmFetchCapabilityMetadataResponse();
    }

    /**
     * Create an instance of {@link PbmCapabilityMetadataPerCategory }
     * 
     */
    public PbmCapabilityMetadataPerCategory createPbmCapabilityMetadataPerCategory() {
        return new PbmCapabilityMetadataPerCategory();
    }

    /**
     * Create an instance of {@link PbmFetchCapabilitySchemaRequestType }
     * 
     */
    public PbmFetchCapabilitySchemaRequestType createPbmFetchCapabilitySchemaRequestType() {
        return new PbmFetchCapabilitySchemaRequestType();
    }

    /**
     * Create an instance of {@link PbmFetchCapabilitySchemaResponse }
     * 
     */
    public PbmFetchCapabilitySchemaResponse createPbmFetchCapabilitySchemaResponse() {
        return new PbmFetchCapabilitySchemaResponse();
    }

    /**
     * Create an instance of {@link PbmCapabilitySchema }
     * 
     */
    public PbmCapabilitySchema createPbmCapabilitySchema() {
        return new PbmCapabilitySchema();
    }

    /**
     * Create an instance of {@link PbmCreateRequestType }
     * 
     */
    public PbmCreateRequestType createPbmCreateRequestType() {
        return new PbmCreateRequestType();
    }

    /**
     * Create an instance of {@link PbmCreateResponse }
     * 
     */
    public PbmCreateResponse createPbmCreateResponse() {
        return new PbmCreateResponse();
    }

    /**
     * Create an instance of {@link PbmProfileId }
     * 
     */
    public PbmProfileId createPbmProfileId() {
        return new PbmProfileId();
    }

    /**
     * Create an instance of {@link PbmUpdateRequestType }
     * 
     */
    public PbmUpdateRequestType createPbmUpdateRequestType() {
        return new PbmUpdateRequestType();
    }

    /**
     * Create an instance of {@link PbmUpdateResponse }
     * 
     */
    public PbmUpdateResponse createPbmUpdateResponse() {
        return new PbmUpdateResponse();
    }

    /**
     * Create an instance of {@link PbmDeleteRequestType }
     * 
     */
    public PbmDeleteRequestType createPbmDeleteRequestType() {
        return new PbmDeleteRequestType();
    }

    /**
     * Create an instance of {@link PbmDeleteResponse }
     * 
     */
    public PbmDeleteResponse createPbmDeleteResponse() {
        return new PbmDeleteResponse();
    }

    /**
     * Create an instance of {@link PbmProfileOperationOutcome }
     * 
     */
    public PbmProfileOperationOutcome createPbmProfileOperationOutcome() {
        return new PbmProfileOperationOutcome();
    }

    /**
     * Create an instance of {@link PbmQueryProfileRequestType }
     * 
     */
    public PbmQueryProfileRequestType createPbmQueryProfileRequestType() {
        return new PbmQueryProfileRequestType();
    }

    /**
     * Create an instance of {@link PbmQueryProfileResponse }
     * 
     */
    public PbmQueryProfileResponse createPbmQueryProfileResponse() {
        return new PbmQueryProfileResponse();
    }

    /**
     * Create an instance of {@link PbmRetrieveContentRequestType }
     * 
     */
    public PbmRetrieveContentRequestType createPbmRetrieveContentRequestType() {
        return new PbmRetrieveContentRequestType();
    }

    /**
     * Create an instance of {@link PbmRetrieveContentResponse }
     * 
     */
    public PbmRetrieveContentResponse createPbmRetrieveContentResponse() {
        return new PbmRetrieveContentResponse();
    }

    /**
     * Create an instance of {@link PbmProfile }
     * 
     */
    public PbmProfile createPbmProfile() {
        return new PbmProfile();
    }

    /**
     * Create an instance of {@link PbmQueryAssociatedProfilesRequestType }
     * 
     */
    public PbmQueryAssociatedProfilesRequestType createPbmQueryAssociatedProfilesRequestType() {
        return new PbmQueryAssociatedProfilesRequestType();
    }

    /**
     * Create an instance of {@link PbmQueryAssociatedProfilesResponse }
     * 
     */
    public PbmQueryAssociatedProfilesResponse createPbmQueryAssociatedProfilesResponse() {
        return new PbmQueryAssociatedProfilesResponse();
    }

    /**
     * Create an instance of {@link PbmQueryProfileResult }
     * 
     */
    public PbmQueryProfileResult createPbmQueryProfileResult() {
        return new PbmQueryProfileResult();
    }

    /**
     * Create an instance of {@link PbmQueryAssociatedProfileRequestType }
     * 
     */
    public PbmQueryAssociatedProfileRequestType createPbmQueryAssociatedProfileRequestType() {
        return new PbmQueryAssociatedProfileRequestType();
    }

    /**
     * Create an instance of {@link PbmQueryAssociatedProfileResponse }
     * 
     */
    public PbmQueryAssociatedProfileResponse createPbmQueryAssociatedProfileResponse() {
        return new PbmQueryAssociatedProfileResponse();
    }

    /**
     * Create an instance of {@link PbmQueryAssociatedEntityRequestType }
     * 
     */
    public PbmQueryAssociatedEntityRequestType createPbmQueryAssociatedEntityRequestType() {
        return new PbmQueryAssociatedEntityRequestType();
    }

    /**
     * Create an instance of {@link PbmQueryAssociatedEntityResponse }
     * 
     */
    public PbmQueryAssociatedEntityResponse createPbmQueryAssociatedEntityResponse() {
        return new PbmQueryAssociatedEntityResponse();
    }

    /**
     * Create an instance of {@link PbmQueryDefaultRequirementProfileRequestType }
     * 
     */
    public PbmQueryDefaultRequirementProfileRequestType createPbmQueryDefaultRequirementProfileRequestType() {
        return new PbmQueryDefaultRequirementProfileRequestType();
    }

    /**
     * Create an instance of {@link PbmQueryDefaultRequirementProfileResponse }
     * 
     */
    public PbmQueryDefaultRequirementProfileResponse createPbmQueryDefaultRequirementProfileResponse() {
        return new PbmQueryDefaultRequirementProfileResponse();
    }

    /**
     * Create an instance of {@link PbmResetDefaultRequirementProfileRequestType }
     * 
     */
    public PbmResetDefaultRequirementProfileRequestType createPbmResetDefaultRequirementProfileRequestType() {
        return new PbmResetDefaultRequirementProfileRequestType();
    }

    /**
     * Create an instance of {@link PbmResetDefaultRequirementProfileResponse }
     * 
     */
    public PbmResetDefaultRequirementProfileResponse createPbmResetDefaultRequirementProfileResponse() {
        return new PbmResetDefaultRequirementProfileResponse();
    }

    /**
     * Create an instance of {@link PbmAssignDefaultRequirementProfileRequestType }
     * 
     */
    public PbmAssignDefaultRequirementProfileRequestType createPbmAssignDefaultRequirementProfileRequestType() {
        return new PbmAssignDefaultRequirementProfileRequestType();
    }

    /**
     * Create an instance of {@link PbmAssignDefaultRequirementProfileResponse }
     * 
     */
    public PbmAssignDefaultRequirementProfileResponse createPbmAssignDefaultRequirementProfileResponse() {
        return new PbmAssignDefaultRequirementProfileResponse();
    }

    /**
     * Create an instance of {@link PbmFindApplicableDefaultProfileRequestType }
     * 
     */
    public PbmFindApplicableDefaultProfileRequestType createPbmFindApplicableDefaultProfileRequestType() {
        return new PbmFindApplicableDefaultProfileRequestType();
    }

    /**
     * Create an instance of {@link PbmFindApplicableDefaultProfileResponse }
     * 
     */
    public PbmFindApplicableDefaultProfileResponse createPbmFindApplicableDefaultProfileResponse() {
        return new PbmFindApplicableDefaultProfileResponse();
    }

    /**
     * Create an instance of {@link PbmQueryDefaultRequirementProfilesRequestType }
     * 
     */
    public PbmQueryDefaultRequirementProfilesRequestType createPbmQueryDefaultRequirementProfilesRequestType() {
        return new PbmQueryDefaultRequirementProfilesRequestType();
    }

    /**
     * Create an instance of {@link PbmQueryDefaultRequirementProfilesResponse }
     * 
     */
    public PbmQueryDefaultRequirementProfilesResponse createPbmQueryDefaultRequirementProfilesResponse() {
        return new PbmQueryDefaultRequirementProfilesResponse();
    }

    /**
     * Create an instance of {@link PbmDefaultProfileInfo }
     * 
     */
    public PbmDefaultProfileInfo createPbmDefaultProfileInfo() {
        return new PbmDefaultProfileInfo();
    }

    /**
     * Create an instance of {@link PbmResetVSanDefaultProfileRequestType }
     * 
     */
    public PbmResetVSanDefaultProfileRequestType createPbmResetVSanDefaultProfileRequestType() {
        return new PbmResetVSanDefaultProfileRequestType();
    }

    /**
     * Create an instance of {@link PbmResetVSanDefaultProfileResponse }
     * 
     */
    public PbmResetVSanDefaultProfileResponse createPbmResetVSanDefaultProfileResponse() {
        return new PbmResetVSanDefaultProfileResponse();
    }

    /**
     * Create an instance of {@link PbmQuerySpaceStatsForStorageContainerRequestType }
     * 
     */
    public PbmQuerySpaceStatsForStorageContainerRequestType createPbmQuerySpaceStatsForStorageContainerRequestType() {
        return new PbmQuerySpaceStatsForStorageContainerRequestType();
    }

    /**
     * Create an instance of {@link PbmQuerySpaceStatsForStorageContainerResponse }
     * 
     */
    public PbmQuerySpaceStatsForStorageContainerResponse createPbmQuerySpaceStatsForStorageContainerResponse() {
        return new PbmQuerySpaceStatsForStorageContainerResponse();
    }

    /**
     * Create an instance of {@link PbmDatastoreSpaceStatistics }
     * 
     */
    public PbmDatastoreSpaceStatistics createPbmDatastoreSpaceStatistics() {
        return new PbmDatastoreSpaceStatistics();
    }

    /**
     * Create an instance of {@link PbmQueryAssociatedEntitiesRequestType }
     * 
     */
    public PbmQueryAssociatedEntitiesRequestType createPbmQueryAssociatedEntitiesRequestType() {
        return new PbmQueryAssociatedEntitiesRequestType();
    }

    /**
     * Create an instance of {@link PbmQueryAssociatedEntitiesResponse }
     * 
     */
    public PbmQueryAssociatedEntitiesResponse createPbmQueryAssociatedEntitiesResponse() {
        return new PbmQueryAssociatedEntitiesResponse();
    }

    /**
     * Create an instance of {@link PbmQueryReplicationGroupsRequestType }
     * 
     */
    public PbmQueryReplicationGroupsRequestType createPbmQueryReplicationGroupsRequestType() {
        return new PbmQueryReplicationGroupsRequestType();
    }

    /**
     * Create an instance of {@link PbmQueryReplicationGroupsResponse }
     * 
     */
    public PbmQueryReplicationGroupsResponse createPbmQueryReplicationGroupsResponse() {
        return new PbmQueryReplicationGroupsResponse();
    }

    /**
     * Create an instance of {@link PbmQueryReplicationGroupResult }
     * 
     */
    public PbmQueryReplicationGroupResult createPbmQueryReplicationGroupResult() {
        return new PbmQueryReplicationGroupResult();
    }

    /**
     * Create an instance of {@link PbmAboutInfo }
     * 
     */
    public PbmAboutInfo createPbmAboutInfo() {
        return new PbmAboutInfo();
    }

    /**
     * Create an instance of {@link PbmExtendedElementDescription }
     * 
     */
    public PbmExtendedElementDescription createPbmExtendedElementDescription() {
        return new PbmExtendedElementDescription();
    }

    /**
     * Create an instance of {@link ArrayOfPbmServerObjectRef }
     * 
     */
    public ArrayOfPbmServerObjectRef createArrayOfPbmServerObjectRef() {
        return new ArrayOfPbmServerObjectRef();
    }

    /**
     * Create an instance of {@link PbmCapabilityInstance }
     * 
     */
    public PbmCapabilityInstance createPbmCapabilityInstance() {
        return new PbmCapabilityInstance();
    }

    /**
     * Create an instance of {@link ArrayOfPbmCapabilityInstance }
     * 
     */
    public ArrayOfPbmCapabilityInstance createArrayOfPbmCapabilityInstance() {
        return new ArrayOfPbmCapabilityInstance();
    }

    /**
     * Create an instance of {@link PbmCapabilityMetadataUniqueId }
     * 
     */
    public PbmCapabilityMetadataUniqueId createPbmCapabilityMetadataUniqueId() {
        return new PbmCapabilityMetadataUniqueId();
    }

    /**
     * Create an instance of {@link PbmCapabilityMetadata }
     * 
     */
    public PbmCapabilityMetadata createPbmCapabilityMetadata() {
        return new PbmCapabilityMetadata();
    }

    /**
     * Create an instance of {@link ArrayOfPbmCapabilityMetadata }
     * 
     */
    public ArrayOfPbmCapabilityMetadata createArrayOfPbmCapabilityMetadata() {
        return new ArrayOfPbmCapabilityMetadata();
    }

    /**
     * Create an instance of {@link PbmCapabilityConstraintInstance }
     * 
     */
    public PbmCapabilityConstraintInstance createPbmCapabilityConstraintInstance() {
        return new PbmCapabilityConstraintInstance();
    }

    /**
     * Create an instance of {@link ArrayOfPbmCapabilityConstraintInstance }
     * 
     */
    public ArrayOfPbmCapabilityConstraintInstance createArrayOfPbmCapabilityConstraintInstance() {
        return new ArrayOfPbmCapabilityConstraintInstance();
    }

    /**
     * Create an instance of {@link PbmCapabilityGenericTypeInfo }
     * 
     */
    public PbmCapabilityGenericTypeInfo createPbmCapabilityGenericTypeInfo() {
        return new PbmCapabilityGenericTypeInfo();
    }

    /**
     * Create an instance of {@link PbmCapabilityPropertyInstance }
     * 
     */
    public PbmCapabilityPropertyInstance createPbmCapabilityPropertyInstance() {
        return new PbmCapabilityPropertyInstance();
    }

    /**
     * Create an instance of {@link ArrayOfPbmCapabilityPropertyInstance }
     * 
     */
    public ArrayOfPbmCapabilityPropertyInstance createArrayOfPbmCapabilityPropertyInstance() {
        return new ArrayOfPbmCapabilityPropertyInstance();
    }

    /**
     * Create an instance of {@link PbmCapabilityPropertyMetadata }
     * 
     */
    public PbmCapabilityPropertyMetadata createPbmCapabilityPropertyMetadata() {
        return new PbmCapabilityPropertyMetadata();
    }

    /**
     * Create an instance of {@link ArrayOfPbmCapabilityPropertyMetadata }
     * 
     */
    public ArrayOfPbmCapabilityPropertyMetadata createArrayOfPbmCapabilityPropertyMetadata() {
        return new ArrayOfPbmCapabilityPropertyMetadata();
    }

    /**
     * Create an instance of {@link PbmCapabilityTypeInfo }
     * 
     */
    public PbmCapabilityTypeInfo createPbmCapabilityTypeInfo() {
        return new PbmCapabilityTypeInfo();
    }

    /**
     * Create an instance of {@link ArrayOfPbmCapabilityMetadataPerCategory }
     * 
     */
    public ArrayOfPbmCapabilityMetadataPerCategory createArrayOfPbmCapabilityMetadataPerCategory() {
        return new ArrayOfPbmCapabilityMetadataPerCategory();
    }

    /**
     * Create an instance of {@link PbmCapabilitySchemaVendorInfo }
     * 
     */
    public PbmCapabilitySchemaVendorInfo createPbmCapabilitySchemaVendorInfo() {
        return new PbmCapabilitySchemaVendorInfo();
    }

    /**
     * Create an instance of {@link PbmCapabilityNamespaceInfo }
     * 
     */
    public PbmCapabilityNamespaceInfo createPbmCapabilityNamespaceInfo() {
        return new PbmCapabilityNamespaceInfo();
    }

    /**
     * Create an instance of {@link ArrayOfPbmCapabilityVendorResourceTypeInfo }
     * 
     */
    public ArrayOfPbmCapabilityVendorResourceTypeInfo createArrayOfPbmCapabilityVendorResourceTypeInfo() {
        return new ArrayOfPbmCapabilityVendorResourceTypeInfo();
    }

    /**
     * Create an instance of {@link PbmCapabilityVendorNamespaceInfo }
     * 
     */
    public PbmCapabilityVendorNamespaceInfo createPbmCapabilityVendorNamespaceInfo() {
        return new PbmCapabilityVendorNamespaceInfo();
    }

    /**
     * Create an instance of {@link ArrayOfPbmCapabilityVendorNamespaceInfo }
     * 
     */
    public ArrayOfPbmCapabilityVendorNamespaceInfo createArrayOfPbmCapabilityVendorNamespaceInfo() {
        return new ArrayOfPbmCapabilityVendorNamespaceInfo();
    }

    /**
     * Create an instance of {@link ArrayOfPbmCapabilitySchema }
     * 
     */
    public ArrayOfPbmCapabilitySchema createArrayOfPbmCapabilitySchema() {
        return new ArrayOfPbmCapabilitySchema();
    }

    /**
     * Create an instance of {@link PbmLineOfServiceInfo }
     * 
     */
    public PbmLineOfServiceInfo createPbmLineOfServiceInfo() {
        return new PbmLineOfServiceInfo();
    }

    /**
     * Create an instance of {@link PbmPersistenceBasedDataServiceInfo }
     * 
     */
    public PbmPersistenceBasedDataServiceInfo createPbmPersistenceBasedDataServiceInfo() {
        return new PbmPersistenceBasedDataServiceInfo();
    }

    /**
     * Create an instance of {@link PbmVaioDataServiceInfo }
     * 
     */
    public PbmVaioDataServiceInfo createPbmVaioDataServiceInfo() {
        return new PbmVaioDataServiceInfo();
    }

    /**
     * Create an instance of {@link PbmCapabilityDescription }
     * 
     */
    public PbmCapabilityDescription createPbmCapabilityDescription() {
        return new PbmCapabilityDescription();
    }

    /**
     * Create an instance of {@link PbmCapabilityDiscreteSet }
     * 
     */
    public PbmCapabilityDiscreteSet createPbmCapabilityDiscreteSet() {
        return new PbmCapabilityDiscreteSet();
    }

    /**
     * Create an instance of {@link PbmCapabilityRange }
     * 
     */
    public PbmCapabilityRange createPbmCapabilityRange() {
        return new PbmCapabilityRange();
    }

    /**
     * Create an instance of {@link PbmCapabilityTimeSpan }
     * 
     */
    public PbmCapabilityTimeSpan createPbmCapabilityTimeSpan() {
        return new PbmCapabilityTimeSpan();
    }

    /**
     * Create an instance of {@link ArrayOfPbmComplianceResult }
     * 
     */
    public ArrayOfPbmComplianceResult createArrayOfPbmComplianceResult() {
        return new ArrayOfPbmComplianceResult();
    }

    /**
     * Create an instance of {@link PbmComplianceOperationalStatus }
     * 
     */
    public PbmComplianceOperationalStatus createPbmComplianceOperationalStatus() {
        return new PbmComplianceOperationalStatus();
    }

    /**
     * Create an instance of {@link PbmCompliancePolicyStatus }
     * 
     */
    public PbmCompliancePolicyStatus createPbmCompliancePolicyStatus() {
        return new PbmCompliancePolicyStatus();
    }

    /**
     * Create an instance of {@link ArrayOfPbmCompliancePolicyStatus }
     * 
     */
    public ArrayOfPbmCompliancePolicyStatus createArrayOfPbmCompliancePolicyStatus() {
        return new ArrayOfPbmCompliancePolicyStatus();
    }

    /**
     * Create an instance of {@link ArrayOfPbmRollupComplianceResult }
     * 
     */
    public ArrayOfPbmRollupComplianceResult createArrayOfPbmRollupComplianceResult() {
        return new ArrayOfPbmRollupComplianceResult();
    }

    /**
     * Create an instance of {@link PbmPlacementCapabilityConstraintsRequirement }
     * 
     */
    public PbmPlacementCapabilityConstraintsRequirement createPbmPlacementCapabilityConstraintsRequirement() {
        return new PbmPlacementCapabilityConstraintsRequirement();
    }

    /**
     * Create an instance of {@link PbmPlacementCapabilityProfileRequirement }
     * 
     */
    public PbmPlacementCapabilityProfileRequirement createPbmPlacementCapabilityProfileRequirement() {
        return new PbmPlacementCapabilityProfileRequirement();
    }

    /**
     * Create an instance of {@link ArrayOfPbmPlacementCompatibilityResult }
     * 
     */
    public ArrayOfPbmPlacementCompatibilityResult createArrayOfPbmPlacementCompatibilityResult() {
        return new ArrayOfPbmPlacementCompatibilityResult();
    }

    /**
     * Create an instance of {@link PbmPlacementMatchingReplicationResources }
     * 
     */
    public PbmPlacementMatchingReplicationResources createPbmPlacementMatchingReplicationResources() {
        return new PbmPlacementMatchingReplicationResources();
    }

    /**
     * Create an instance of {@link PbmPlacementMatchingResources }
     * 
     */
    public PbmPlacementMatchingResources createPbmPlacementMatchingResources() {
        return new PbmPlacementMatchingResources();
    }

    /**
     * Create an instance of {@link ArrayOfPbmPlacementMatchingResources }
     * 
     */
    public ArrayOfPbmPlacementMatchingResources createArrayOfPbmPlacementMatchingResources() {
        return new ArrayOfPbmPlacementMatchingResources();
    }

    /**
     * Create an instance of {@link ArrayOfPbmPlacementHub }
     * 
     */
    public ArrayOfPbmPlacementHub createArrayOfPbmPlacementHub() {
        return new ArrayOfPbmPlacementHub();
    }

    /**
     * Create an instance of {@link PbmPlacementRequirement }
     * 
     */
    public PbmPlacementRequirement createPbmPlacementRequirement() {
        return new PbmPlacementRequirement();
    }

    /**
     * Create an instance of {@link ArrayOfPbmPlacementRequirement }
     * 
     */
    public ArrayOfPbmPlacementRequirement createArrayOfPbmPlacementRequirement() {
        return new ArrayOfPbmPlacementRequirement();
    }

    /**
     * Create an instance of {@link PbmPlacementResourceUtilization }
     * 
     */
    public PbmPlacementResourceUtilization createPbmPlacementResourceUtilization() {
        return new PbmPlacementResourceUtilization();
    }

    /**
     * Create an instance of {@link ArrayOfPbmPlacementResourceUtilization }
     * 
     */
    public ArrayOfPbmPlacementResourceUtilization createArrayOfPbmPlacementResourceUtilization() {
        return new ArrayOfPbmPlacementResourceUtilization();
    }

    /**
     * Create an instance of {@link PbmCapabilityProfile }
     * 
     */
    public PbmCapabilityProfile createPbmCapabilityProfile() {
        return new PbmCapabilityProfile();
    }

    /**
     * Create an instance of {@link PbmCapabilityProfileCreateSpec }
     * 
     */
    public PbmCapabilityProfileCreateSpec createPbmCapabilityProfileCreateSpec() {
        return new PbmCapabilityProfileCreateSpec();
    }

    /**
     * Create an instance of {@link PbmCapabilityProfileUpdateSpec }
     * 
     */
    public PbmCapabilityProfileUpdateSpec createPbmCapabilityProfileUpdateSpec() {
        return new PbmCapabilityProfileUpdateSpec();
    }

    /**
     * Create an instance of {@link PbmCapabilityConstraints }
     * 
     */
    public PbmCapabilityConstraints createPbmCapabilityConstraints() {
        return new PbmCapabilityConstraints();
    }

    /**
     * Create an instance of {@link PbmDataServiceToPoliciesMap }
     * 
     */
    public PbmDataServiceToPoliciesMap createPbmDataServiceToPoliciesMap() {
        return new PbmDataServiceToPoliciesMap();
    }

    /**
     * Create an instance of {@link PbmDefaultCapabilityProfile }
     * 
     */
    public PbmDefaultCapabilityProfile createPbmDefaultCapabilityProfile() {
        return new PbmDefaultCapabilityProfile();
    }

    /**
     * Create an instance of {@link ArrayOfPbmDefaultProfileInfo }
     * 
     */
    public ArrayOfPbmDefaultProfileInfo createArrayOfPbmDefaultProfileInfo() {
        return new ArrayOfPbmDefaultProfileInfo();
    }

    /**
     * Create an instance of {@link ArrayOfPbmProfile }
     * 
     */
    public ArrayOfPbmProfile createArrayOfPbmProfile() {
        return new ArrayOfPbmProfile();
    }

    /**
     * Create an instance of {@link ArrayOfPbmProfileId }
     * 
     */
    public ArrayOfPbmProfileId createArrayOfPbmProfileId() {
        return new ArrayOfPbmProfileId();
    }

    /**
     * Create an instance of {@link ArrayOfPbmProfileOperationOutcome }
     * 
     */
    public ArrayOfPbmProfileOperationOutcome createArrayOfPbmProfileOperationOutcome() {
        return new ArrayOfPbmProfileOperationOutcome();
    }

    /**
     * Create an instance of {@link PbmProfileType }
     * 
     */
    public PbmProfileType createPbmProfileType() {
        return new PbmProfileType();
    }

    /**
     * Create an instance of {@link ArrayOfPbmProfileType }
     * 
     */
    public ArrayOfPbmProfileType createArrayOfPbmProfileType() {
        return new ArrayOfPbmProfileType();
    }

    /**
     * Create an instance of {@link ArrayOfPbmQueryProfileResult }
     * 
     */
    public ArrayOfPbmQueryProfileResult createArrayOfPbmQueryProfileResult() {
        return new ArrayOfPbmQueryProfileResult();
    }

    /**
     * Create an instance of {@link ArrayOfPbmProfileResourceType }
     * 
     */
    public ArrayOfPbmProfileResourceType createArrayOfPbmProfileResourceType() {
        return new ArrayOfPbmProfileResourceType();
    }

    /**
     * Create an instance of {@link PbmCapabilitySubProfile }
     * 
     */
    public PbmCapabilitySubProfile createPbmCapabilitySubProfile() {
        return new PbmCapabilitySubProfile();
    }

    /**
     * Create an instance of {@link ArrayOfPbmCapabilitySubProfile }
     * 
     */
    public ArrayOfPbmCapabilitySubProfile createArrayOfPbmCapabilitySubProfile() {
        return new ArrayOfPbmCapabilitySubProfile();
    }

    /**
     * Create an instance of {@link PbmCapabilitySubProfileConstraints }
     * 
     */
    public PbmCapabilitySubProfileConstraints createPbmCapabilitySubProfileConstraints() {
        return new PbmCapabilitySubProfileConstraints();
    }

    /**
     * Create an instance of {@link ArrayOfPbmDatastoreSpaceStatistics }
     * 
     */
    public ArrayOfPbmDatastoreSpaceStatistics createArrayOfPbmDatastoreSpaceStatistics() {
        return new ArrayOfPbmDatastoreSpaceStatistics();
    }

    /**
     * Create an instance of {@link ArrayOfPbmQueryReplicationGroupResult }
     * 
     */
    public ArrayOfPbmQueryReplicationGroupResult createArrayOfPbmQueryReplicationGroupResult() {
        return new ArrayOfPbmQueryReplicationGroupResult();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "versionURI")
    public JAXBElement<String> createVersionURI(String value) {
        return new JAXBElement<String>(_VersionURI_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ActiveDirectoryFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ActiveDirectoryFaultFault")
    public JAXBElement<ActiveDirectoryFault> createActiveDirectoryFaultFault(ActiveDirectoryFault value) {
        return new JAXBElement<ActiveDirectoryFault>(_ActiveDirectoryFaultFault_QNAME, ActiveDirectoryFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ActiveVMsBlockingEVC }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ActiveVMsBlockingEVCFault")
    public JAXBElement<ActiveVMsBlockingEVC> createActiveVMsBlockingEVCFault(ActiveVMsBlockingEVC value) {
        return new JAXBElement<ActiveVMsBlockingEVC>(_ActiveVMsBlockingEVCFault_QNAME, ActiveVMsBlockingEVC.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link AdminDisabled }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "AdminDisabledFault")
    public JAXBElement<AdminDisabled> createAdminDisabledFault(AdminDisabled value) {
        return new JAXBElement<AdminDisabled>(_AdminDisabledFault_QNAME, AdminDisabled.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link AdminNotDisabled }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "AdminNotDisabledFault")
    public JAXBElement<AdminNotDisabled> createAdminNotDisabledFault(AdminNotDisabled value) {
        return new JAXBElement<AdminNotDisabled>(_AdminNotDisabledFault_QNAME, AdminNotDisabled.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link AffinityConfigured }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "AffinityConfiguredFault")
    public JAXBElement<AffinityConfigured> createAffinityConfiguredFault(AffinityConfigured value) {
        return new JAXBElement<AffinityConfigured>(_AffinityConfiguredFault_QNAME, AffinityConfigured.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link AgentInstallFailed }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "AgentInstallFailedFault")
    public JAXBElement<AgentInstallFailed> createAgentInstallFailedFault(AgentInstallFailed value) {
        return new JAXBElement<AgentInstallFailed>(_AgentInstallFailedFault_QNAME, AgentInstallFailed.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link AlreadyBeingManaged }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "AlreadyBeingManagedFault")
    public JAXBElement<AlreadyBeingManaged> createAlreadyBeingManagedFault(AlreadyBeingManaged value) {
        return new JAXBElement<AlreadyBeingManaged>(_AlreadyBeingManagedFault_QNAME, AlreadyBeingManaged.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link AlreadyConnected }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "AlreadyConnectedFault")
    public JAXBElement<AlreadyConnected> createAlreadyConnectedFault(AlreadyConnected value) {
        return new JAXBElement<AlreadyConnected>(_AlreadyConnectedFault_QNAME, AlreadyConnected.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link AlreadyExists }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "AlreadyExistsFault")
    public JAXBElement<AlreadyExists> createAlreadyExistsFault(AlreadyExists value) {
        return new JAXBElement<AlreadyExists>(_AlreadyExistsFault_QNAME, AlreadyExists.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link AlreadyUpgraded }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "AlreadyUpgradedFault")
    public JAXBElement<AlreadyUpgraded> createAlreadyUpgradedFault(AlreadyUpgraded value) {
        return new JAXBElement<AlreadyUpgraded>(_AlreadyUpgradedFault_QNAME, AlreadyUpgraded.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link AnswerFileUpdateFailed }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "AnswerFileUpdateFailedFault")
    public JAXBElement<AnswerFileUpdateFailed> createAnswerFileUpdateFailedFault(AnswerFileUpdateFailed value) {
        return new JAXBElement<AnswerFileUpdateFailed>(_AnswerFileUpdateFailedFault_QNAME, AnswerFileUpdateFailed.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ApplicationQuiesceFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ApplicationQuiesceFaultFault")
    public JAXBElement<ApplicationQuiesceFault> createApplicationQuiesceFaultFault(ApplicationQuiesceFault value) {
        return new JAXBElement<ApplicationQuiesceFault>(_ApplicationQuiesceFaultFault_QNAME, ApplicationQuiesceFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link AuthMinimumAdminPermission }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "AuthMinimumAdminPermissionFault")
    public JAXBElement<AuthMinimumAdminPermission> createAuthMinimumAdminPermissionFault(AuthMinimumAdminPermission value) {
        return new JAXBElement<AuthMinimumAdminPermission>(_AuthMinimumAdminPermissionFault_QNAME, AuthMinimumAdminPermission.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link BackupBlobReadFailure }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "BackupBlobReadFailureFault")
    public JAXBElement<BackupBlobReadFailure> createBackupBlobReadFailureFault(BackupBlobReadFailure value) {
        return new JAXBElement<BackupBlobReadFailure>(_BackupBlobReadFailureFault_QNAME, BackupBlobReadFailure.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link BackupBlobWriteFailure }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "BackupBlobWriteFailureFault")
    public JAXBElement<BackupBlobWriteFailure> createBackupBlobWriteFailureFault(BackupBlobWriteFailure value) {
        return new JAXBElement<BackupBlobWriteFailure>(_BackupBlobWriteFailureFault_QNAME, BackupBlobWriteFailure.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link BlockedByFirewall }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "BlockedByFirewallFault")
    public JAXBElement<BlockedByFirewall> createBlockedByFirewallFault(BlockedByFirewall value) {
        return new JAXBElement<BlockedByFirewall>(_BlockedByFirewallFault_QNAME, BlockedByFirewall.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CAMServerRefusedConnection }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CAMServerRefusedConnectionFault")
    public JAXBElement<CAMServerRefusedConnection> createCAMServerRefusedConnectionFault(CAMServerRefusedConnection value) {
        return new JAXBElement<CAMServerRefusedConnection>(_CAMServerRefusedConnectionFault_QNAME, CAMServerRefusedConnection.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotAccessFile }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotAccessFileFault")
    public JAXBElement<CannotAccessFile> createCannotAccessFileFault(CannotAccessFile value) {
        return new JAXBElement<CannotAccessFile>(_CannotAccessFileFault_QNAME, CannotAccessFile.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotAccessLocalSource }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotAccessLocalSourceFault")
    public JAXBElement<CannotAccessLocalSource> createCannotAccessLocalSourceFault(CannotAccessLocalSource value) {
        return new JAXBElement<CannotAccessLocalSource>(_CannotAccessLocalSourceFault_QNAME, CannotAccessLocalSource.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotAccessNetwork }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotAccessNetworkFault")
    public JAXBElement<CannotAccessNetwork> createCannotAccessNetworkFault(CannotAccessNetwork value) {
        return new JAXBElement<CannotAccessNetwork>(_CannotAccessNetworkFault_QNAME, CannotAccessNetwork.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotAccessVmComponent }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotAccessVmComponentFault")
    public JAXBElement<CannotAccessVmComponent> createCannotAccessVmComponentFault(CannotAccessVmComponent value) {
        return new JAXBElement<CannotAccessVmComponent>(_CannotAccessVmComponentFault_QNAME, CannotAccessVmComponent.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotAccessVmConfig }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotAccessVmConfigFault")
    public JAXBElement<CannotAccessVmConfig> createCannotAccessVmConfigFault(CannotAccessVmConfig value) {
        return new JAXBElement<CannotAccessVmConfig>(_CannotAccessVmConfigFault_QNAME, CannotAccessVmConfig.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotAccessVmDevice }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotAccessVmDeviceFault")
    public JAXBElement<CannotAccessVmDevice> createCannotAccessVmDeviceFault(CannotAccessVmDevice value) {
        return new JAXBElement<CannotAccessVmDevice>(_CannotAccessVmDeviceFault_QNAME, CannotAccessVmDevice.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotAccessVmDisk }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotAccessVmDiskFault")
    public JAXBElement<CannotAccessVmDisk> createCannotAccessVmDiskFault(CannotAccessVmDisk value) {
        return new JAXBElement<CannotAccessVmDisk>(_CannotAccessVmDiskFault_QNAME, CannotAccessVmDisk.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotAddHostWithFTVmAsStandalone }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotAddHostWithFTVmAsStandaloneFault")
    public JAXBElement<CannotAddHostWithFTVmAsStandalone> createCannotAddHostWithFTVmAsStandaloneFault(CannotAddHostWithFTVmAsStandalone value) {
        return new JAXBElement<CannotAddHostWithFTVmAsStandalone>(_CannotAddHostWithFTVmAsStandaloneFault_QNAME, CannotAddHostWithFTVmAsStandalone.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotAddHostWithFTVmToDifferentCluster }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotAddHostWithFTVmToDifferentClusterFault")
    public JAXBElement<CannotAddHostWithFTVmToDifferentCluster> createCannotAddHostWithFTVmToDifferentClusterFault(CannotAddHostWithFTVmToDifferentCluster value) {
        return new JAXBElement<CannotAddHostWithFTVmToDifferentCluster>(_CannotAddHostWithFTVmToDifferentClusterFault_QNAME, CannotAddHostWithFTVmToDifferentCluster.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotAddHostWithFTVmToNonHACluster }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotAddHostWithFTVmToNonHAClusterFault")
    public JAXBElement<CannotAddHostWithFTVmToNonHACluster> createCannotAddHostWithFTVmToNonHAClusterFault(CannotAddHostWithFTVmToNonHACluster value) {
        return new JAXBElement<CannotAddHostWithFTVmToNonHACluster>(_CannotAddHostWithFTVmToNonHAClusterFault_QNAME, CannotAddHostWithFTVmToNonHACluster.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotChangeDrsBehaviorForFtSecondary }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotChangeDrsBehaviorForFtSecondaryFault")
    public JAXBElement<CannotChangeDrsBehaviorForFtSecondary> createCannotChangeDrsBehaviorForFtSecondaryFault(CannotChangeDrsBehaviorForFtSecondary value) {
        return new JAXBElement<CannotChangeDrsBehaviorForFtSecondary>(_CannotChangeDrsBehaviorForFtSecondaryFault_QNAME, CannotChangeDrsBehaviorForFtSecondary.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotChangeHaSettingsForFtSecondary }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotChangeHaSettingsForFtSecondaryFault")
    public JAXBElement<CannotChangeHaSettingsForFtSecondary> createCannotChangeHaSettingsForFtSecondaryFault(CannotChangeHaSettingsForFtSecondary value) {
        return new JAXBElement<CannotChangeHaSettingsForFtSecondary>(_CannotChangeHaSettingsForFtSecondaryFault_QNAME, CannotChangeHaSettingsForFtSecondary.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotChangeVsanClusterUuid }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotChangeVsanClusterUuidFault")
    public JAXBElement<CannotChangeVsanClusterUuid> createCannotChangeVsanClusterUuidFault(CannotChangeVsanClusterUuid value) {
        return new JAXBElement<CannotChangeVsanClusterUuid>(_CannotChangeVsanClusterUuidFault_QNAME, CannotChangeVsanClusterUuid.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotChangeVsanNodeUuid }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotChangeVsanNodeUuidFault")
    public JAXBElement<CannotChangeVsanNodeUuid> createCannotChangeVsanNodeUuidFault(CannotChangeVsanNodeUuid value) {
        return new JAXBElement<CannotChangeVsanNodeUuid>(_CannotChangeVsanNodeUuidFault_QNAME, CannotChangeVsanNodeUuid.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotComputeFTCompatibleHosts }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotComputeFTCompatibleHostsFault")
    public JAXBElement<CannotComputeFTCompatibleHosts> createCannotComputeFTCompatibleHostsFault(CannotComputeFTCompatibleHosts value) {
        return new JAXBElement<CannotComputeFTCompatibleHosts>(_CannotComputeFTCompatibleHostsFault_QNAME, CannotComputeFTCompatibleHosts.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotCreateFile }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotCreateFileFault")
    public JAXBElement<CannotCreateFile> createCannotCreateFileFault(CannotCreateFile value) {
        return new JAXBElement<CannotCreateFile>(_CannotCreateFileFault_QNAME, CannotCreateFile.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotDecryptPasswords }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotDecryptPasswordsFault")
    public JAXBElement<CannotDecryptPasswords> createCannotDecryptPasswordsFault(CannotDecryptPasswords value) {
        return new JAXBElement<CannotDecryptPasswords>(_CannotDecryptPasswordsFault_QNAME, CannotDecryptPasswords.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotDeleteFile }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotDeleteFileFault")
    public JAXBElement<CannotDeleteFile> createCannotDeleteFileFault(CannotDeleteFile value) {
        return new JAXBElement<CannotDeleteFile>(_CannotDeleteFileFault_QNAME, CannotDeleteFile.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotDisableDrsOnClustersWithVApps }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotDisableDrsOnClustersWithVAppsFault")
    public JAXBElement<CannotDisableDrsOnClustersWithVApps> createCannotDisableDrsOnClustersWithVAppsFault(CannotDisableDrsOnClustersWithVApps value) {
        return new JAXBElement<CannotDisableDrsOnClustersWithVApps>(_CannotDisableDrsOnClustersWithVAppsFault_QNAME, CannotDisableDrsOnClustersWithVApps.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotDisableSnapshot }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotDisableSnapshotFault")
    public JAXBElement<CannotDisableSnapshot> createCannotDisableSnapshotFault(CannotDisableSnapshot value) {
        return new JAXBElement<CannotDisableSnapshot>(_CannotDisableSnapshotFault_QNAME, CannotDisableSnapshot.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotDisconnectHostWithFaultToleranceVm }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotDisconnectHostWithFaultToleranceVmFault")
    public JAXBElement<CannotDisconnectHostWithFaultToleranceVm> createCannotDisconnectHostWithFaultToleranceVmFault(CannotDisconnectHostWithFaultToleranceVm value) {
        return new JAXBElement<CannotDisconnectHostWithFaultToleranceVm>(_CannotDisconnectHostWithFaultToleranceVmFault_QNAME, CannotDisconnectHostWithFaultToleranceVm.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotEnableVmcpForCluster }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotEnableVmcpForClusterFault")
    public JAXBElement<CannotEnableVmcpForCluster> createCannotEnableVmcpForClusterFault(CannotEnableVmcpForCluster value) {
        return new JAXBElement<CannotEnableVmcpForCluster>(_CannotEnableVmcpForClusterFault_QNAME, CannotEnableVmcpForCluster.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotModifyConfigCpuRequirements }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotModifyConfigCpuRequirementsFault")
    public JAXBElement<CannotModifyConfigCpuRequirements> createCannotModifyConfigCpuRequirementsFault(CannotModifyConfigCpuRequirements value) {
        return new JAXBElement<CannotModifyConfigCpuRequirements>(_CannotModifyConfigCpuRequirementsFault_QNAME, CannotModifyConfigCpuRequirements.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotMoveFaultToleranceVm }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotMoveFaultToleranceVmFault")
    public JAXBElement<CannotMoveFaultToleranceVm> createCannotMoveFaultToleranceVmFault(CannotMoveFaultToleranceVm value) {
        return new JAXBElement<CannotMoveFaultToleranceVm>(_CannotMoveFaultToleranceVmFault_QNAME, CannotMoveFaultToleranceVm.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotMoveHostWithFaultToleranceVm }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotMoveHostWithFaultToleranceVmFault")
    public JAXBElement<CannotMoveHostWithFaultToleranceVm> createCannotMoveHostWithFaultToleranceVmFault(CannotMoveHostWithFaultToleranceVm value) {
        return new JAXBElement<CannotMoveHostWithFaultToleranceVm>(_CannotMoveHostWithFaultToleranceVmFault_QNAME, CannotMoveHostWithFaultToleranceVm.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotMoveVmWithDeltaDisk }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotMoveVmWithDeltaDiskFault")
    public JAXBElement<CannotMoveVmWithDeltaDisk> createCannotMoveVmWithDeltaDiskFault(CannotMoveVmWithDeltaDisk value) {
        return new JAXBElement<CannotMoveVmWithDeltaDisk>(_CannotMoveVmWithDeltaDiskFault_QNAME, CannotMoveVmWithDeltaDisk.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotMoveVmWithNativeDeltaDisk }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotMoveVmWithNativeDeltaDiskFault")
    public JAXBElement<CannotMoveVmWithNativeDeltaDisk> createCannotMoveVmWithNativeDeltaDiskFault(CannotMoveVmWithNativeDeltaDisk value) {
        return new JAXBElement<CannotMoveVmWithNativeDeltaDisk>(_CannotMoveVmWithNativeDeltaDiskFault_QNAME, CannotMoveVmWithNativeDeltaDisk.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotMoveVsanEnabledHost }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotMoveVsanEnabledHostFault")
    public JAXBElement<CannotMoveVsanEnabledHost> createCannotMoveVsanEnabledHostFault(CannotMoveVsanEnabledHost value) {
        return new JAXBElement<CannotMoveVsanEnabledHost>(_CannotMoveVsanEnabledHostFault_QNAME, CannotMoveVsanEnabledHost.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotPlaceWithoutPrerequisiteMoves }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotPlaceWithoutPrerequisiteMovesFault")
    public JAXBElement<CannotPlaceWithoutPrerequisiteMoves> createCannotPlaceWithoutPrerequisiteMovesFault(CannotPlaceWithoutPrerequisiteMoves value) {
        return new JAXBElement<CannotPlaceWithoutPrerequisiteMoves>(_CannotPlaceWithoutPrerequisiteMovesFault_QNAME, CannotPlaceWithoutPrerequisiteMoves.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotPowerOffVmInCluster }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotPowerOffVmInClusterFault")
    public JAXBElement<CannotPowerOffVmInCluster> createCannotPowerOffVmInClusterFault(CannotPowerOffVmInCluster value) {
        return new JAXBElement<CannotPowerOffVmInCluster>(_CannotPowerOffVmInClusterFault_QNAME, CannotPowerOffVmInCluster.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotReconfigureVsanWhenHaEnabled }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotReconfigureVsanWhenHaEnabledFault")
    public JAXBElement<CannotReconfigureVsanWhenHaEnabled> createCannotReconfigureVsanWhenHaEnabledFault(CannotReconfigureVsanWhenHaEnabled value) {
        return new JAXBElement<CannotReconfigureVsanWhenHaEnabled>(_CannotReconfigureVsanWhenHaEnabledFault_QNAME, CannotReconfigureVsanWhenHaEnabled.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CannotUseNetwork }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CannotUseNetworkFault")
    public JAXBElement<CannotUseNetwork> createCannotUseNetworkFault(CannotUseNetwork value) {
        return new JAXBElement<CannotUseNetwork>(_CannotUseNetworkFault_QNAME, CannotUseNetwork.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ClockSkew }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ClockSkewFault")
    public JAXBElement<ClockSkew> createClockSkewFault(ClockSkew value) {
        return new JAXBElement<ClockSkew>(_ClockSkewFault_QNAME, ClockSkew.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CloneFromSnapshotNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CloneFromSnapshotNotSupportedFault")
    public JAXBElement<CloneFromSnapshotNotSupported> createCloneFromSnapshotNotSupportedFault(CloneFromSnapshotNotSupported value) {
        return new JAXBElement<CloneFromSnapshotNotSupported>(_CloneFromSnapshotNotSupportedFault_QNAME, CloneFromSnapshotNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CollectorAddressUnset }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CollectorAddressUnsetFault")
    public JAXBElement<CollectorAddressUnset> createCollectorAddressUnsetFault(CollectorAddressUnset value) {
        return new JAXBElement<CollectorAddressUnset>(_CollectorAddressUnsetFault_QNAME, CollectorAddressUnset.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ConcurrentAccess }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ConcurrentAccessFault")
    public JAXBElement<ConcurrentAccess> createConcurrentAccessFault(ConcurrentAccess value) {
        return new JAXBElement<ConcurrentAccess>(_ConcurrentAccessFault_QNAME, ConcurrentAccess.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ConflictingConfiguration }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ConflictingConfigurationFault")
    public JAXBElement<ConflictingConfiguration> createConflictingConfigurationFault(ConflictingConfiguration value) {
        return new JAXBElement<ConflictingConfiguration>(_ConflictingConfigurationFault_QNAME, ConflictingConfiguration.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ConflictingDatastoreFound }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ConflictingDatastoreFoundFault")
    public JAXBElement<ConflictingDatastoreFound> createConflictingDatastoreFoundFault(ConflictingDatastoreFound value) {
        return new JAXBElement<ConflictingDatastoreFound>(_ConflictingDatastoreFoundFault_QNAME, ConflictingDatastoreFound.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ConnectedIso }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ConnectedIsoFault")
    public JAXBElement<ConnectedIso> createConnectedIsoFault(ConnectedIso value) {
        return new JAXBElement<ConnectedIso>(_ConnectedIsoFault_QNAME, ConnectedIso.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CpuCompatibilityUnknown }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CpuCompatibilityUnknownFault")
    public JAXBElement<CpuCompatibilityUnknown> createCpuCompatibilityUnknownFault(CpuCompatibilityUnknown value) {
        return new JAXBElement<CpuCompatibilityUnknown>(_CpuCompatibilityUnknownFault_QNAME, CpuCompatibilityUnknown.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CpuHotPlugNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CpuHotPlugNotSupportedFault")
    public JAXBElement<CpuHotPlugNotSupported> createCpuHotPlugNotSupportedFault(CpuHotPlugNotSupported value) {
        return new JAXBElement<CpuHotPlugNotSupported>(_CpuHotPlugNotSupportedFault_QNAME, CpuHotPlugNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CpuIncompatible }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CpuIncompatibleFault")
    public JAXBElement<CpuIncompatible> createCpuIncompatibleFault(CpuIncompatible value) {
        return new JAXBElement<CpuIncompatible>(_CpuIncompatibleFault_QNAME, CpuIncompatible.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CpuIncompatible1ECX }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CpuIncompatible1ECXFault")
    public JAXBElement<CpuIncompatible1ECX> createCpuIncompatible1ECXFault(CpuIncompatible1ECX value) {
        return new JAXBElement<CpuIncompatible1ECX>(_CpuIncompatible1ECXFault_QNAME, CpuIncompatible1ECX.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CpuIncompatible81EDX }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CpuIncompatible81EDXFault")
    public JAXBElement<CpuIncompatible81EDX> createCpuIncompatible81EDXFault(CpuIncompatible81EDX value) {
        return new JAXBElement<CpuIncompatible81EDX>(_CpuIncompatible81EDXFault_QNAME, CpuIncompatible81EDX.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CustomizationFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CustomizationFaultFault")
    public JAXBElement<CustomizationFault> createCustomizationFaultFault(CustomizationFault value) {
        return new JAXBElement<CustomizationFault>(_CustomizationFaultFault_QNAME, CustomizationFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CustomizationPending }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "CustomizationPendingFault")
    public JAXBElement<CustomizationPending> createCustomizationPendingFault(CustomizationPending value) {
        return new JAXBElement<CustomizationPending>(_CustomizationPendingFault_QNAME, CustomizationPending.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DVPortNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DVPortNotSupportedFault")
    public JAXBElement<DVPortNotSupported> createDVPortNotSupportedFault(DVPortNotSupported value) {
        return new JAXBElement<DVPortNotSupported>(_DVPortNotSupportedFault_QNAME, DVPortNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DasConfigFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DasConfigFaultFault")
    public JAXBElement<DasConfigFault> createDasConfigFaultFault(DasConfigFault value) {
        return new JAXBElement<DasConfigFault>(_DasConfigFaultFault_QNAME, DasConfigFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DatabaseError }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DatabaseErrorFault")
    public JAXBElement<DatabaseError> createDatabaseErrorFault(DatabaseError value) {
        return new JAXBElement<DatabaseError>(_DatabaseErrorFault_QNAME, DatabaseError.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DatacenterMismatch }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DatacenterMismatchFault")
    public JAXBElement<DatacenterMismatch> createDatacenterMismatchFault(DatacenterMismatch value) {
        return new JAXBElement<DatacenterMismatch>(_DatacenterMismatchFault_QNAME, DatacenterMismatch.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DatastoreNotWritableOnHost }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DatastoreNotWritableOnHostFault")
    public JAXBElement<DatastoreNotWritableOnHost> createDatastoreNotWritableOnHostFault(DatastoreNotWritableOnHost value) {
        return new JAXBElement<DatastoreNotWritableOnHost>(_DatastoreNotWritableOnHostFault_QNAME, DatastoreNotWritableOnHost.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DeltaDiskFormatNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DeltaDiskFormatNotSupportedFault")
    public JAXBElement<DeltaDiskFormatNotSupported> createDeltaDiskFormatNotSupportedFault(DeltaDiskFormatNotSupported value) {
        return new JAXBElement<DeltaDiskFormatNotSupported>(_DeltaDiskFormatNotSupportedFault_QNAME, DeltaDiskFormatNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DestinationSwitchFull }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DestinationSwitchFullFault")
    public JAXBElement<DestinationSwitchFull> createDestinationSwitchFullFault(DestinationSwitchFull value) {
        return new JAXBElement<DestinationSwitchFull>(_DestinationSwitchFullFault_QNAME, DestinationSwitchFull.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DestinationVsanDisabled }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DestinationVsanDisabledFault")
    public JAXBElement<DestinationVsanDisabled> createDestinationVsanDisabledFault(DestinationVsanDisabled value) {
        return new JAXBElement<DestinationVsanDisabled>(_DestinationVsanDisabledFault_QNAME, DestinationVsanDisabled.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DeviceBackingNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DeviceBackingNotSupportedFault")
    public JAXBElement<DeviceBackingNotSupported> createDeviceBackingNotSupportedFault(DeviceBackingNotSupported value) {
        return new JAXBElement<DeviceBackingNotSupported>(_DeviceBackingNotSupportedFault_QNAME, DeviceBackingNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DeviceControllerNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DeviceControllerNotSupportedFault")
    public JAXBElement<DeviceControllerNotSupported> createDeviceControllerNotSupportedFault(DeviceControllerNotSupported value) {
        return new JAXBElement<DeviceControllerNotSupported>(_DeviceControllerNotSupportedFault_QNAME, DeviceControllerNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DeviceHotPlugNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DeviceHotPlugNotSupportedFault")
    public JAXBElement<DeviceHotPlugNotSupported> createDeviceHotPlugNotSupportedFault(DeviceHotPlugNotSupported value) {
        return new JAXBElement<DeviceHotPlugNotSupported>(_DeviceHotPlugNotSupportedFault_QNAME, DeviceHotPlugNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DeviceNotFound }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DeviceNotFoundFault")
    public JAXBElement<DeviceNotFound> createDeviceNotFoundFault(DeviceNotFound value) {
        return new JAXBElement<DeviceNotFound>(_DeviceNotFoundFault_QNAME, DeviceNotFound.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DeviceNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DeviceNotSupportedFault")
    public JAXBElement<DeviceNotSupported> createDeviceNotSupportedFault(DeviceNotSupported value) {
        return new JAXBElement<DeviceNotSupported>(_DeviceNotSupportedFault_QNAME, DeviceNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DeviceUnsupportedForVmPlatform }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DeviceUnsupportedForVmPlatformFault")
    public JAXBElement<DeviceUnsupportedForVmPlatform> createDeviceUnsupportedForVmPlatformFault(DeviceUnsupportedForVmPlatform value) {
        return new JAXBElement<DeviceUnsupportedForVmPlatform>(_DeviceUnsupportedForVmPlatformFault_QNAME, DeviceUnsupportedForVmPlatform.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DeviceUnsupportedForVmVersion }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DeviceUnsupportedForVmVersionFault")
    public JAXBElement<DeviceUnsupportedForVmVersion> createDeviceUnsupportedForVmVersionFault(DeviceUnsupportedForVmVersion value) {
        return new JAXBElement<DeviceUnsupportedForVmVersion>(_DeviceUnsupportedForVmVersionFault_QNAME, DeviceUnsupportedForVmVersion.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DigestNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DigestNotSupportedFault")
    public JAXBElement<DigestNotSupported> createDigestNotSupportedFault(DigestNotSupported value) {
        return new JAXBElement<DigestNotSupported>(_DigestNotSupportedFault_QNAME, DigestNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DirectoryNotEmpty }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DirectoryNotEmptyFault")
    public JAXBElement<DirectoryNotEmpty> createDirectoryNotEmptyFault(DirectoryNotEmpty value) {
        return new JAXBElement<DirectoryNotEmpty>(_DirectoryNotEmptyFault_QNAME, DirectoryNotEmpty.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DisableAdminNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DisableAdminNotSupportedFault")
    public JAXBElement<DisableAdminNotSupported> createDisableAdminNotSupportedFault(DisableAdminNotSupported value) {
        return new JAXBElement<DisableAdminNotSupported>(_DisableAdminNotSupportedFault_QNAME, DisableAdminNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DisallowedChangeByService }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DisallowedChangeByServiceFault")
    public JAXBElement<DisallowedChangeByService> createDisallowedChangeByServiceFault(DisallowedChangeByService value) {
        return new JAXBElement<DisallowedChangeByService>(_DisallowedChangeByServiceFault_QNAME, DisallowedChangeByService.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DisallowedDiskModeChange }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DisallowedDiskModeChangeFault")
    public JAXBElement<DisallowedDiskModeChange> createDisallowedDiskModeChangeFault(DisallowedDiskModeChange value) {
        return new JAXBElement<DisallowedDiskModeChange>(_DisallowedDiskModeChangeFault_QNAME, DisallowedDiskModeChange.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DisallowedMigrationDeviceAttached }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DisallowedMigrationDeviceAttachedFault")
    public JAXBElement<DisallowedMigrationDeviceAttached> createDisallowedMigrationDeviceAttachedFault(DisallowedMigrationDeviceAttached value) {
        return new JAXBElement<DisallowedMigrationDeviceAttached>(_DisallowedMigrationDeviceAttachedFault_QNAME, DisallowedMigrationDeviceAttached.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DisallowedOperationOnFailoverHost }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DisallowedOperationOnFailoverHostFault")
    public JAXBElement<DisallowedOperationOnFailoverHost> createDisallowedOperationOnFailoverHostFault(DisallowedOperationOnFailoverHost value) {
        return new JAXBElement<DisallowedOperationOnFailoverHost>(_DisallowedOperationOnFailoverHostFault_QNAME, DisallowedOperationOnFailoverHost.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DisconnectedHostsBlockingEVC }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DisconnectedHostsBlockingEVCFault")
    public JAXBElement<DisconnectedHostsBlockingEVC> createDisconnectedHostsBlockingEVCFault(DisconnectedHostsBlockingEVC value) {
        return new JAXBElement<DisconnectedHostsBlockingEVC>(_DisconnectedHostsBlockingEVCFault_QNAME, DisconnectedHostsBlockingEVC.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DiskHasPartitions }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DiskHasPartitionsFault")
    public JAXBElement<DiskHasPartitions> createDiskHasPartitionsFault(DiskHasPartitions value) {
        return new JAXBElement<DiskHasPartitions>(_DiskHasPartitionsFault_QNAME, DiskHasPartitions.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DiskIsLastRemainingNonSSD }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DiskIsLastRemainingNonSSDFault")
    public JAXBElement<DiskIsLastRemainingNonSSD> createDiskIsLastRemainingNonSSDFault(DiskIsLastRemainingNonSSD value) {
        return new JAXBElement<DiskIsLastRemainingNonSSD>(_DiskIsLastRemainingNonSSDFault_QNAME, DiskIsLastRemainingNonSSD.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DiskIsNonLocal }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DiskIsNonLocalFault")
    public JAXBElement<DiskIsNonLocal> createDiskIsNonLocalFault(DiskIsNonLocal value) {
        return new JAXBElement<DiskIsNonLocal>(_DiskIsNonLocalFault_QNAME, DiskIsNonLocal.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DiskIsUSB }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DiskIsUSBFault")
    public JAXBElement<DiskIsUSB> createDiskIsUSBFault(DiskIsUSB value) {
        return new JAXBElement<DiskIsUSB>(_DiskIsUSBFault_QNAME, DiskIsUSB.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DiskMoveTypeNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DiskMoveTypeNotSupportedFault")
    public JAXBElement<DiskMoveTypeNotSupported> createDiskMoveTypeNotSupportedFault(DiskMoveTypeNotSupported value) {
        return new JAXBElement<DiskMoveTypeNotSupported>(_DiskMoveTypeNotSupportedFault_QNAME, DiskMoveTypeNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DiskNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DiskNotSupportedFault")
    public JAXBElement<DiskNotSupported> createDiskNotSupportedFault(DiskNotSupported value) {
        return new JAXBElement<DiskNotSupported>(_DiskNotSupportedFault_QNAME, DiskNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DiskTooSmall }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DiskTooSmallFault")
    public JAXBElement<DiskTooSmall> createDiskTooSmallFault(DiskTooSmall value) {
        return new JAXBElement<DiskTooSmall>(_DiskTooSmallFault_QNAME, DiskTooSmall.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DomainNotFound }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DomainNotFoundFault")
    public JAXBElement<DomainNotFound> createDomainNotFoundFault(DomainNotFound value) {
        return new JAXBElement<DomainNotFound>(_DomainNotFoundFault_QNAME, DomainNotFound.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DrsDisabledOnVm }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DrsDisabledOnVmFault")
    public JAXBElement<DrsDisabledOnVm> createDrsDisabledOnVmFault(DrsDisabledOnVm value) {
        return new JAXBElement<DrsDisabledOnVm>(_DrsDisabledOnVmFault_QNAME, DrsDisabledOnVm.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DrsVmotionIncompatibleFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DrsVmotionIncompatibleFaultFault")
    public JAXBElement<DrsVmotionIncompatibleFault> createDrsVmotionIncompatibleFaultFault(DrsVmotionIncompatibleFault value) {
        return new JAXBElement<DrsVmotionIncompatibleFault>(_DrsVmotionIncompatibleFaultFault_QNAME, DrsVmotionIncompatibleFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DuplicateDisks }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DuplicateDisksFault")
    public JAXBElement<DuplicateDisks> createDuplicateDisksFault(DuplicateDisks value) {
        return new JAXBElement<DuplicateDisks>(_DuplicateDisksFault_QNAME, DuplicateDisks.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DuplicateName }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DuplicateNameFault")
    public JAXBElement<DuplicateName> createDuplicateNameFault(DuplicateName value) {
        return new JAXBElement<DuplicateName>(_DuplicateNameFault_QNAME, DuplicateName.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DuplicateVsanNetworkInterface }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DuplicateVsanNetworkInterfaceFault")
    public JAXBElement<DuplicateVsanNetworkInterface> createDuplicateVsanNetworkInterfaceFault(DuplicateVsanNetworkInterface value) {
        return new JAXBElement<DuplicateVsanNetworkInterface>(_DuplicateVsanNetworkInterfaceFault_QNAME, DuplicateVsanNetworkInterface.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DvsApplyOperationFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DvsApplyOperationFaultFault")
    public JAXBElement<DvsApplyOperationFault> createDvsApplyOperationFaultFault(DvsApplyOperationFault value) {
        return new JAXBElement<DvsApplyOperationFault>(_DvsApplyOperationFaultFault_QNAME, DvsApplyOperationFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DvsFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DvsFaultFault")
    public JAXBElement<DvsFault> createDvsFaultFault(DvsFault value) {
        return new JAXBElement<DvsFault>(_DvsFaultFault_QNAME, DvsFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DvsNotAuthorized }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DvsNotAuthorizedFault")
    public JAXBElement<DvsNotAuthorized> createDvsNotAuthorizedFault(DvsNotAuthorized value) {
        return new JAXBElement<DvsNotAuthorized>(_DvsNotAuthorizedFault_QNAME, DvsNotAuthorized.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DvsOperationBulkFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DvsOperationBulkFaultFault")
    public JAXBElement<DvsOperationBulkFault> createDvsOperationBulkFaultFault(DvsOperationBulkFault value) {
        return new JAXBElement<DvsOperationBulkFault>(_DvsOperationBulkFaultFault_QNAME, DvsOperationBulkFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DvsScopeViolated }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "DvsScopeViolatedFault")
    public JAXBElement<DvsScopeViolated> createDvsScopeViolatedFault(DvsScopeViolated value) {
        return new JAXBElement<DvsScopeViolated>(_DvsScopeViolatedFault_QNAME, DvsScopeViolated.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link EVCAdmissionFailed }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "EVCAdmissionFailedFault")
    public JAXBElement<EVCAdmissionFailed> createEVCAdmissionFailedFault(EVCAdmissionFailed value) {
        return new JAXBElement<EVCAdmissionFailed>(_EVCAdmissionFailedFault_QNAME, EVCAdmissionFailed.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link EVCAdmissionFailedCPUFeaturesForMode }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "EVCAdmissionFailedCPUFeaturesForModeFault")
    public JAXBElement<EVCAdmissionFailedCPUFeaturesForMode> createEVCAdmissionFailedCPUFeaturesForModeFault(EVCAdmissionFailedCPUFeaturesForMode value) {
        return new JAXBElement<EVCAdmissionFailedCPUFeaturesForMode>(_EVCAdmissionFailedCPUFeaturesForModeFault_QNAME, EVCAdmissionFailedCPUFeaturesForMode.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link EVCAdmissionFailedCPUModel }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "EVCAdmissionFailedCPUModelFault")
    public JAXBElement<EVCAdmissionFailedCPUModel> createEVCAdmissionFailedCPUModelFault(EVCAdmissionFailedCPUModel value) {
        return new JAXBElement<EVCAdmissionFailedCPUModel>(_EVCAdmissionFailedCPUModelFault_QNAME, EVCAdmissionFailedCPUModel.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link EVCAdmissionFailedCPUModelForMode }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "EVCAdmissionFailedCPUModelForModeFault")
    public JAXBElement<EVCAdmissionFailedCPUModelForMode> createEVCAdmissionFailedCPUModelForModeFault(EVCAdmissionFailedCPUModelForMode value) {
        return new JAXBElement<EVCAdmissionFailedCPUModelForMode>(_EVCAdmissionFailedCPUModelForModeFault_QNAME, EVCAdmissionFailedCPUModelForMode.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link EVCAdmissionFailedCPUVendor }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "EVCAdmissionFailedCPUVendorFault")
    public JAXBElement<EVCAdmissionFailedCPUVendor> createEVCAdmissionFailedCPUVendorFault(EVCAdmissionFailedCPUVendor value) {
        return new JAXBElement<EVCAdmissionFailedCPUVendor>(_EVCAdmissionFailedCPUVendorFault_QNAME, EVCAdmissionFailedCPUVendor.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link EVCAdmissionFailedCPUVendorUnknown }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "EVCAdmissionFailedCPUVendorUnknownFault")
    public JAXBElement<EVCAdmissionFailedCPUVendorUnknown> createEVCAdmissionFailedCPUVendorUnknownFault(EVCAdmissionFailedCPUVendorUnknown value) {
        return new JAXBElement<EVCAdmissionFailedCPUVendorUnknown>(_EVCAdmissionFailedCPUVendorUnknownFault_QNAME, EVCAdmissionFailedCPUVendorUnknown.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link EVCAdmissionFailedHostDisconnected }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "EVCAdmissionFailedHostDisconnectedFault")
    public JAXBElement<EVCAdmissionFailedHostDisconnected> createEVCAdmissionFailedHostDisconnectedFault(EVCAdmissionFailedHostDisconnected value) {
        return new JAXBElement<EVCAdmissionFailedHostDisconnected>(_EVCAdmissionFailedHostDisconnectedFault_QNAME, EVCAdmissionFailedHostDisconnected.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link EVCAdmissionFailedHostSoftware }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "EVCAdmissionFailedHostSoftwareFault")
    public JAXBElement<EVCAdmissionFailedHostSoftware> createEVCAdmissionFailedHostSoftwareFault(EVCAdmissionFailedHostSoftware value) {
        return new JAXBElement<EVCAdmissionFailedHostSoftware>(_EVCAdmissionFailedHostSoftwareFault_QNAME, EVCAdmissionFailedHostSoftware.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link EVCAdmissionFailedHostSoftwareForMode }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "EVCAdmissionFailedHostSoftwareForModeFault")
    public JAXBElement<EVCAdmissionFailedHostSoftwareForMode> createEVCAdmissionFailedHostSoftwareForModeFault(EVCAdmissionFailedHostSoftwareForMode value) {
        return new JAXBElement<EVCAdmissionFailedHostSoftwareForMode>(_EVCAdmissionFailedHostSoftwareForModeFault_QNAME, EVCAdmissionFailedHostSoftwareForMode.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link EVCAdmissionFailedVmActive }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "EVCAdmissionFailedVmActiveFault")
    public JAXBElement<EVCAdmissionFailedVmActive> createEVCAdmissionFailedVmActiveFault(EVCAdmissionFailedVmActive value) {
        return new JAXBElement<EVCAdmissionFailedVmActive>(_EVCAdmissionFailedVmActiveFault_QNAME, EVCAdmissionFailedVmActive.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link EVCConfigFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "EVCConfigFaultFault")
    public JAXBElement<EVCConfigFault> createEVCConfigFaultFault(EVCConfigFault value) {
        return new JAXBElement<EVCConfigFault>(_EVCConfigFaultFault_QNAME, EVCConfigFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link EVCModeIllegalByVendor }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "EVCModeIllegalByVendorFault")
    public JAXBElement<EVCModeIllegalByVendor> createEVCModeIllegalByVendorFault(EVCModeIllegalByVendor value) {
        return new JAXBElement<EVCModeIllegalByVendor>(_EVCModeIllegalByVendorFault_QNAME, EVCModeIllegalByVendor.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link EVCModeUnsupportedByHosts }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "EVCModeUnsupportedByHostsFault")
    public JAXBElement<EVCModeUnsupportedByHosts> createEVCModeUnsupportedByHostsFault(EVCModeUnsupportedByHosts value) {
        return new JAXBElement<EVCModeUnsupportedByHosts>(_EVCModeUnsupportedByHostsFault_QNAME, EVCModeUnsupportedByHosts.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link EVCUnsupportedByHostHardware }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "EVCUnsupportedByHostHardwareFault")
    public JAXBElement<EVCUnsupportedByHostHardware> createEVCUnsupportedByHostHardwareFault(EVCUnsupportedByHostHardware value) {
        return new JAXBElement<EVCUnsupportedByHostHardware>(_EVCUnsupportedByHostHardwareFault_QNAME, EVCUnsupportedByHostHardware.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link EVCUnsupportedByHostSoftware }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "EVCUnsupportedByHostSoftwareFault")
    public JAXBElement<EVCUnsupportedByHostSoftware> createEVCUnsupportedByHostSoftwareFault(EVCUnsupportedByHostSoftware value) {
        return new JAXBElement<EVCUnsupportedByHostSoftware>(_EVCUnsupportedByHostSoftwareFault_QNAME, EVCUnsupportedByHostSoftware.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link EightHostLimitViolated }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "EightHostLimitViolatedFault")
    public JAXBElement<EightHostLimitViolated> createEightHostLimitViolatedFault(EightHostLimitViolated value) {
        return new JAXBElement<EightHostLimitViolated>(_EightHostLimitViolatedFault_QNAME, EightHostLimitViolated.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ExpiredAddonLicense }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ExpiredAddonLicenseFault")
    public JAXBElement<ExpiredAddonLicense> createExpiredAddonLicenseFault(ExpiredAddonLicense value) {
        return new JAXBElement<ExpiredAddonLicense>(_ExpiredAddonLicenseFault_QNAME, ExpiredAddonLicense.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ExpiredEditionLicense }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ExpiredEditionLicenseFault")
    public JAXBElement<ExpiredEditionLicense> createExpiredEditionLicenseFault(ExpiredEditionLicense value) {
        return new JAXBElement<ExpiredEditionLicense>(_ExpiredEditionLicenseFault_QNAME, ExpiredEditionLicense.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ExpiredFeatureLicense }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ExpiredFeatureLicenseFault")
    public JAXBElement<ExpiredFeatureLicense> createExpiredFeatureLicenseFault(ExpiredFeatureLicense value) {
        return new JAXBElement<ExpiredFeatureLicense>(_ExpiredFeatureLicenseFault_QNAME, ExpiredFeatureLicense.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ExtendedFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ExtendedFaultFault")
    public JAXBElement<ExtendedFault> createExtendedFaultFault(ExtendedFault value) {
        return new JAXBElement<ExtendedFault>(_ExtendedFaultFault_QNAME, ExtendedFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link FailToEnableSPBM }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "FailToEnableSPBMFault")
    public JAXBElement<FailToEnableSPBM> createFailToEnableSPBMFault(FailToEnableSPBM value) {
        return new JAXBElement<FailToEnableSPBM>(_FailToEnableSPBMFault_QNAME, FailToEnableSPBM.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link FailToLockFaultToleranceVMs }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "FailToLockFaultToleranceVMsFault")
    public JAXBElement<FailToLockFaultToleranceVMs> createFailToLockFaultToleranceVMsFault(FailToLockFaultToleranceVMs value) {
        return new JAXBElement<FailToLockFaultToleranceVMs>(_FailToLockFaultToleranceVMsFault_QNAME, FailToLockFaultToleranceVMs.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link FaultToleranceAntiAffinityViolated }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "FaultToleranceAntiAffinityViolatedFault")
    public JAXBElement<FaultToleranceAntiAffinityViolated> createFaultToleranceAntiAffinityViolatedFault(FaultToleranceAntiAffinityViolated value) {
        return new JAXBElement<FaultToleranceAntiAffinityViolated>(_FaultToleranceAntiAffinityViolatedFault_QNAME, FaultToleranceAntiAffinityViolated.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link FaultToleranceCannotEditMem }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "FaultToleranceCannotEditMemFault")
    public JAXBElement<FaultToleranceCannotEditMem> createFaultToleranceCannotEditMemFault(FaultToleranceCannotEditMem value) {
        return new JAXBElement<FaultToleranceCannotEditMem>(_FaultToleranceCannotEditMemFault_QNAME, FaultToleranceCannotEditMem.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link FaultToleranceCpuIncompatible }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "FaultToleranceCpuIncompatibleFault")
    public JAXBElement<FaultToleranceCpuIncompatible> createFaultToleranceCpuIncompatibleFault(FaultToleranceCpuIncompatible value) {
        return new JAXBElement<FaultToleranceCpuIncompatible>(_FaultToleranceCpuIncompatibleFault_QNAME, FaultToleranceCpuIncompatible.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link FaultToleranceNeedsThickDisk }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "FaultToleranceNeedsThickDiskFault")
    public JAXBElement<FaultToleranceNeedsThickDisk> createFaultToleranceNeedsThickDiskFault(FaultToleranceNeedsThickDisk value) {
        return new JAXBElement<FaultToleranceNeedsThickDisk>(_FaultToleranceNeedsThickDiskFault_QNAME, FaultToleranceNeedsThickDisk.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link FaultToleranceNotLicensed }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "FaultToleranceNotLicensedFault")
    public JAXBElement<FaultToleranceNotLicensed> createFaultToleranceNotLicensedFault(FaultToleranceNotLicensed value) {
        return new JAXBElement<FaultToleranceNotLicensed>(_FaultToleranceNotLicensedFault_QNAME, FaultToleranceNotLicensed.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link FaultToleranceNotSameBuild }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "FaultToleranceNotSameBuildFault")
    public JAXBElement<FaultToleranceNotSameBuild> createFaultToleranceNotSameBuildFault(FaultToleranceNotSameBuild value) {
        return new JAXBElement<FaultToleranceNotSameBuild>(_FaultToleranceNotSameBuildFault_QNAME, FaultToleranceNotSameBuild.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link FaultTolerancePrimaryPowerOnNotAttempted }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "FaultTolerancePrimaryPowerOnNotAttemptedFault")
    public JAXBElement<FaultTolerancePrimaryPowerOnNotAttempted> createFaultTolerancePrimaryPowerOnNotAttemptedFault(FaultTolerancePrimaryPowerOnNotAttempted value) {
        return new JAXBElement<FaultTolerancePrimaryPowerOnNotAttempted>(_FaultTolerancePrimaryPowerOnNotAttemptedFault_QNAME, FaultTolerancePrimaryPowerOnNotAttempted.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link FaultToleranceVmNotDasProtected }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "FaultToleranceVmNotDasProtectedFault")
    public JAXBElement<FaultToleranceVmNotDasProtected> createFaultToleranceVmNotDasProtectedFault(FaultToleranceVmNotDasProtected value) {
        return new JAXBElement<FaultToleranceVmNotDasProtected>(_FaultToleranceVmNotDasProtectedFault_QNAME, FaultToleranceVmNotDasProtected.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link FcoeFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "FcoeFaultFault")
    public JAXBElement<FcoeFault> createFcoeFaultFault(FcoeFault value) {
        return new JAXBElement<FcoeFault>(_FcoeFaultFault_QNAME, FcoeFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link FcoeFaultPnicHasNoPortSet }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "FcoeFaultPnicHasNoPortSetFault")
    public JAXBElement<FcoeFaultPnicHasNoPortSet> createFcoeFaultPnicHasNoPortSetFault(FcoeFaultPnicHasNoPortSet value) {
        return new JAXBElement<FcoeFaultPnicHasNoPortSet>(_FcoeFaultPnicHasNoPortSetFault_QNAME, FcoeFaultPnicHasNoPortSet.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link FeatureRequirementsNotMet }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "FeatureRequirementsNotMetFault")
    public JAXBElement<FeatureRequirementsNotMet> createFeatureRequirementsNotMetFault(FeatureRequirementsNotMet value) {
        return new JAXBElement<FeatureRequirementsNotMet>(_FeatureRequirementsNotMetFault_QNAME, FeatureRequirementsNotMet.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link FileAlreadyExists }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "FileAlreadyExistsFault")
    public JAXBElement<FileAlreadyExists> createFileAlreadyExistsFault(FileAlreadyExists value) {
        return new JAXBElement<FileAlreadyExists>(_FileAlreadyExistsFault_QNAME, FileAlreadyExists.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link FileBackedPortNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "FileBackedPortNotSupportedFault")
    public JAXBElement<FileBackedPortNotSupported> createFileBackedPortNotSupportedFault(FileBackedPortNotSupported value) {
        return new JAXBElement<FileBackedPortNotSupported>(_FileBackedPortNotSupportedFault_QNAME, FileBackedPortNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link FileFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "FileFaultFault")
    public JAXBElement<FileFault> createFileFaultFault(FileFault value) {
        return new JAXBElement<FileFault>(_FileFaultFault_QNAME, FileFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link FileLocked }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "FileLockedFault")
    public JAXBElement<FileLocked> createFileLockedFault(FileLocked value) {
        return new JAXBElement<FileLocked>(_FileLockedFault_QNAME, FileLocked.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link FileNameTooLong }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "FileNameTooLongFault")
    public JAXBElement<FileNameTooLong> createFileNameTooLongFault(FileNameTooLong value) {
        return new JAXBElement<FileNameTooLong>(_FileNameTooLongFault_QNAME, FileNameTooLong.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link FileNotFound }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "FileNotFoundFault")
    public JAXBElement<FileNotFound> createFileNotFoundFault(FileNotFound value) {
        return new JAXBElement<FileNotFound>(_FileNotFoundFault_QNAME, FileNotFound.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link FileNotWritable }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "FileNotWritableFault")
    public JAXBElement<FileNotWritable> createFileNotWritableFault(FileNotWritable value) {
        return new JAXBElement<FileNotWritable>(_FileNotWritableFault_QNAME, FileNotWritable.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link FileTooLarge }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "FileTooLargeFault")
    public JAXBElement<FileTooLarge> createFileTooLargeFault(FileTooLarge value) {
        return new JAXBElement<FileTooLarge>(_FileTooLargeFault_QNAME, FileTooLarge.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link FilesystemQuiesceFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "FilesystemQuiesceFaultFault")
    public JAXBElement<FilesystemQuiesceFault> createFilesystemQuiesceFaultFault(FilesystemQuiesceFault value) {
        return new JAXBElement<FilesystemQuiesceFault>(_FilesystemQuiesceFaultFault_QNAME, FilesystemQuiesceFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link FilterInUse }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "FilterInUseFault")
    public JAXBElement<FilterInUse> createFilterInUseFault(FilterInUse value) {
        return new JAXBElement<FilterInUse>(_FilterInUseFault_QNAME, FilterInUse.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link FtIssuesOnHost }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "FtIssuesOnHostFault")
    public JAXBElement<FtIssuesOnHost> createFtIssuesOnHostFault(FtIssuesOnHost value) {
        return new JAXBElement<FtIssuesOnHost>(_FtIssuesOnHostFault_QNAME, FtIssuesOnHost.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link FullStorageVMotionNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "FullStorageVMotionNotSupportedFault")
    public JAXBElement<FullStorageVMotionNotSupported> createFullStorageVMotionNotSupportedFault(FullStorageVMotionNotSupported value) {
        return new JAXBElement<FullStorageVMotionNotSupported>(_FullStorageVMotionNotSupportedFault_QNAME, FullStorageVMotionNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link GatewayConnectFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "GatewayConnectFaultFault")
    public JAXBElement<GatewayConnectFault> createGatewayConnectFaultFault(GatewayConnectFault value) {
        return new JAXBElement<GatewayConnectFault>(_GatewayConnectFaultFault_QNAME, GatewayConnectFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link GatewayHostNotReachable }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "GatewayHostNotReachableFault")
    public JAXBElement<GatewayHostNotReachable> createGatewayHostNotReachableFault(GatewayHostNotReachable value) {
        return new JAXBElement<GatewayHostNotReachable>(_GatewayHostNotReachableFault_QNAME, GatewayHostNotReachable.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link GatewayNotFound }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "GatewayNotFoundFault")
    public JAXBElement<GatewayNotFound> createGatewayNotFoundFault(GatewayNotFound value) {
        return new JAXBElement<GatewayNotFound>(_GatewayNotFoundFault_QNAME, GatewayNotFound.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link GatewayNotReachable }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "GatewayNotReachableFault")
    public JAXBElement<GatewayNotReachable> createGatewayNotReachableFault(GatewayNotReachable value) {
        return new JAXBElement<GatewayNotReachable>(_GatewayNotReachableFault_QNAME, GatewayNotReachable.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link GatewayOperationRefused }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "GatewayOperationRefusedFault")
    public JAXBElement<GatewayOperationRefused> createGatewayOperationRefusedFault(GatewayOperationRefused value) {
        return new JAXBElement<GatewayOperationRefused>(_GatewayOperationRefusedFault_QNAME, GatewayOperationRefused.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link GatewayToHostAuthFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "GatewayToHostAuthFaultFault")
    public JAXBElement<GatewayToHostAuthFault> createGatewayToHostAuthFaultFault(GatewayToHostAuthFault value) {
        return new JAXBElement<GatewayToHostAuthFault>(_GatewayToHostAuthFaultFault_QNAME, GatewayToHostAuthFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link GatewayToHostConnectFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "GatewayToHostConnectFaultFault")
    public JAXBElement<GatewayToHostConnectFault> createGatewayToHostConnectFaultFault(GatewayToHostConnectFault value) {
        return new JAXBElement<GatewayToHostConnectFault>(_GatewayToHostConnectFaultFault_QNAME, GatewayToHostConnectFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link GatewayToHostTrustVerifyFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "GatewayToHostTrustVerifyFaultFault")
    public JAXBElement<GatewayToHostTrustVerifyFault> createGatewayToHostTrustVerifyFaultFault(GatewayToHostTrustVerifyFault value) {
        return new JAXBElement<GatewayToHostTrustVerifyFault>(_GatewayToHostTrustVerifyFaultFault_QNAME, GatewayToHostTrustVerifyFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link GenericDrsFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "GenericDrsFaultFault")
    public JAXBElement<GenericDrsFault> createGenericDrsFaultFault(GenericDrsFault value) {
        return new JAXBElement<GenericDrsFault>(_GenericDrsFaultFault_QNAME, GenericDrsFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link GenericVmConfigFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "GenericVmConfigFaultFault")
    public JAXBElement<GenericVmConfigFault> createGenericVmConfigFaultFault(GenericVmConfigFault value) {
        return new JAXBElement<GenericVmConfigFault>(_GenericVmConfigFaultFault_QNAME, GenericVmConfigFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link GuestAuthenticationChallenge }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "GuestAuthenticationChallengeFault")
    public JAXBElement<GuestAuthenticationChallenge> createGuestAuthenticationChallengeFault(GuestAuthenticationChallenge value) {
        return new JAXBElement<GuestAuthenticationChallenge>(_GuestAuthenticationChallengeFault_QNAME, GuestAuthenticationChallenge.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link GuestComponentsOutOfDate }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "GuestComponentsOutOfDateFault")
    public JAXBElement<GuestComponentsOutOfDate> createGuestComponentsOutOfDateFault(GuestComponentsOutOfDate value) {
        return new JAXBElement<GuestComponentsOutOfDate>(_GuestComponentsOutOfDateFault_QNAME, GuestComponentsOutOfDate.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link GuestMultipleMappings }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "GuestMultipleMappingsFault")
    public JAXBElement<GuestMultipleMappings> createGuestMultipleMappingsFault(GuestMultipleMappings value) {
        return new JAXBElement<GuestMultipleMappings>(_GuestMultipleMappingsFault_QNAME, GuestMultipleMappings.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link GuestOperationsFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "GuestOperationsFaultFault")
    public JAXBElement<GuestOperationsFault> createGuestOperationsFaultFault(GuestOperationsFault value) {
        return new JAXBElement<GuestOperationsFault>(_GuestOperationsFaultFault_QNAME, GuestOperationsFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link GuestOperationsUnavailable }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "GuestOperationsUnavailableFault")
    public JAXBElement<GuestOperationsUnavailable> createGuestOperationsUnavailableFault(GuestOperationsUnavailable value) {
        return new JAXBElement<GuestOperationsUnavailable>(_GuestOperationsUnavailableFault_QNAME, GuestOperationsUnavailable.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link GuestPermissionDenied }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "GuestPermissionDeniedFault")
    public JAXBElement<GuestPermissionDenied> createGuestPermissionDeniedFault(GuestPermissionDenied value) {
        return new JAXBElement<GuestPermissionDenied>(_GuestPermissionDeniedFault_QNAME, GuestPermissionDenied.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link GuestProcessNotFound }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "GuestProcessNotFoundFault")
    public JAXBElement<GuestProcessNotFound> createGuestProcessNotFoundFault(GuestProcessNotFound value) {
        return new JAXBElement<GuestProcessNotFound>(_GuestProcessNotFoundFault_QNAME, GuestProcessNotFound.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link GuestRegistryFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "GuestRegistryFaultFault")
    public JAXBElement<GuestRegistryFault> createGuestRegistryFaultFault(GuestRegistryFault value) {
        return new JAXBElement<GuestRegistryFault>(_GuestRegistryFaultFault_QNAME, GuestRegistryFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link GuestRegistryKeyAlreadyExists }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "GuestRegistryKeyAlreadyExistsFault")
    public JAXBElement<GuestRegistryKeyAlreadyExists> createGuestRegistryKeyAlreadyExistsFault(GuestRegistryKeyAlreadyExists value) {
        return new JAXBElement<GuestRegistryKeyAlreadyExists>(_GuestRegistryKeyAlreadyExistsFault_QNAME, GuestRegistryKeyAlreadyExists.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link GuestRegistryKeyFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "GuestRegistryKeyFaultFault")
    public JAXBElement<GuestRegistryKeyFault> createGuestRegistryKeyFaultFault(GuestRegistryKeyFault value) {
        return new JAXBElement<GuestRegistryKeyFault>(_GuestRegistryKeyFaultFault_QNAME, GuestRegistryKeyFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link GuestRegistryKeyHasSubkeys }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "GuestRegistryKeyHasSubkeysFault")
    public JAXBElement<GuestRegistryKeyHasSubkeys> createGuestRegistryKeyHasSubkeysFault(GuestRegistryKeyHasSubkeys value) {
        return new JAXBElement<GuestRegistryKeyHasSubkeys>(_GuestRegistryKeyHasSubkeysFault_QNAME, GuestRegistryKeyHasSubkeys.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link GuestRegistryKeyInvalid }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "GuestRegistryKeyInvalidFault")
    public JAXBElement<GuestRegistryKeyInvalid> createGuestRegistryKeyInvalidFault(GuestRegistryKeyInvalid value) {
        return new JAXBElement<GuestRegistryKeyInvalid>(_GuestRegistryKeyInvalidFault_QNAME, GuestRegistryKeyInvalid.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link GuestRegistryKeyParentVolatile }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "GuestRegistryKeyParentVolatileFault")
    public JAXBElement<GuestRegistryKeyParentVolatile> createGuestRegistryKeyParentVolatileFault(GuestRegistryKeyParentVolatile value) {
        return new JAXBElement<GuestRegistryKeyParentVolatile>(_GuestRegistryKeyParentVolatileFault_QNAME, GuestRegistryKeyParentVolatile.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link GuestRegistryValueFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "GuestRegistryValueFaultFault")
    public JAXBElement<GuestRegistryValueFault> createGuestRegistryValueFaultFault(GuestRegistryValueFault value) {
        return new JAXBElement<GuestRegistryValueFault>(_GuestRegistryValueFaultFault_QNAME, GuestRegistryValueFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link GuestRegistryValueNotFound }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "GuestRegistryValueNotFoundFault")
    public JAXBElement<GuestRegistryValueNotFound> createGuestRegistryValueNotFoundFault(GuestRegistryValueNotFound value) {
        return new JAXBElement<GuestRegistryValueNotFound>(_GuestRegistryValueNotFoundFault_QNAME, GuestRegistryValueNotFound.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link HAErrorsAtDest }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "HAErrorsAtDestFault")
    public JAXBElement<HAErrorsAtDest> createHAErrorsAtDestFault(HAErrorsAtDest value) {
        return new JAXBElement<HAErrorsAtDest>(_HAErrorsAtDestFault_QNAME, HAErrorsAtDest.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link HeterogenousHostsBlockingEVC }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "HeterogenousHostsBlockingEVCFault")
    public JAXBElement<HeterogenousHostsBlockingEVC> createHeterogenousHostsBlockingEVCFault(HeterogenousHostsBlockingEVC value) {
        return new JAXBElement<HeterogenousHostsBlockingEVC>(_HeterogenousHostsBlockingEVCFault_QNAME, HeterogenousHostsBlockingEVC.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link HostAccessRestrictedToManagementServer }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "HostAccessRestrictedToManagementServerFault")
    public JAXBElement<HostAccessRestrictedToManagementServer> createHostAccessRestrictedToManagementServerFault(HostAccessRestrictedToManagementServer value) {
        return new JAXBElement<HostAccessRestrictedToManagementServer>(_HostAccessRestrictedToManagementServerFault_QNAME, HostAccessRestrictedToManagementServer.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link HostConfigFailed }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "HostConfigFailedFault")
    public JAXBElement<HostConfigFailed> createHostConfigFailedFault(HostConfigFailed value) {
        return new JAXBElement<HostConfigFailed>(_HostConfigFailedFault_QNAME, HostConfigFailed.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link HostConfigFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "HostConfigFaultFault")
    public JAXBElement<HostConfigFault> createHostConfigFaultFault(HostConfigFault value) {
        return new JAXBElement<HostConfigFault>(_HostConfigFaultFault_QNAME, HostConfigFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link HostConnectFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "HostConnectFaultFault")
    public JAXBElement<HostConnectFault> createHostConnectFaultFault(HostConnectFault value) {
        return new JAXBElement<HostConnectFault>(_HostConnectFaultFault_QNAME, HostConnectFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link HostHasComponentFailure }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "HostHasComponentFailureFault")
    public JAXBElement<HostHasComponentFailure> createHostHasComponentFailureFault(HostHasComponentFailure value) {
        return new JAXBElement<HostHasComponentFailure>(_HostHasComponentFailureFault_QNAME, HostHasComponentFailure.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link HostInDomain }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "HostInDomainFault")
    public JAXBElement<HostInDomain> createHostInDomainFault(HostInDomain value) {
        return new JAXBElement<HostInDomain>(_HostInDomainFault_QNAME, HostInDomain.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link HostIncompatibleForFaultTolerance }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "HostIncompatibleForFaultToleranceFault")
    public JAXBElement<HostIncompatibleForFaultTolerance> createHostIncompatibleForFaultToleranceFault(HostIncompatibleForFaultTolerance value) {
        return new JAXBElement<HostIncompatibleForFaultTolerance>(_HostIncompatibleForFaultToleranceFault_QNAME, HostIncompatibleForFaultTolerance.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link HostIncompatibleForRecordReplay }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "HostIncompatibleForRecordReplayFault")
    public JAXBElement<HostIncompatibleForRecordReplay> createHostIncompatibleForRecordReplayFault(HostIncompatibleForRecordReplay value) {
        return new JAXBElement<HostIncompatibleForRecordReplay>(_HostIncompatibleForRecordReplayFault_QNAME, HostIncompatibleForRecordReplay.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link HostInventoryFull }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "HostInventoryFullFault")
    public JAXBElement<HostInventoryFull> createHostInventoryFullFault(HostInventoryFull value) {
        return new JAXBElement<HostInventoryFull>(_HostInventoryFullFault_QNAME, HostInventoryFull.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link HostPowerOpFailed }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "HostPowerOpFailedFault")
    public JAXBElement<HostPowerOpFailed> createHostPowerOpFailedFault(HostPowerOpFailed value) {
        return new JAXBElement<HostPowerOpFailed>(_HostPowerOpFailedFault_QNAME, HostPowerOpFailed.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link HostSpecificationOperationFailed }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "HostSpecificationOperationFailedFault")
    public JAXBElement<HostSpecificationOperationFailed> createHostSpecificationOperationFailedFault(HostSpecificationOperationFailed value) {
        return new JAXBElement<HostSpecificationOperationFailed>(_HostSpecificationOperationFailedFault_QNAME, HostSpecificationOperationFailed.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link HotSnapshotMoveNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "HotSnapshotMoveNotSupportedFault")
    public JAXBElement<HotSnapshotMoveNotSupported> createHotSnapshotMoveNotSupportedFault(HotSnapshotMoveNotSupported value) {
        return new JAXBElement<HotSnapshotMoveNotSupported>(_HotSnapshotMoveNotSupportedFault_QNAME, HotSnapshotMoveNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link IDEDiskNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "IDEDiskNotSupportedFault")
    public JAXBElement<IDEDiskNotSupported> createIDEDiskNotSupportedFault(IDEDiskNotSupported value) {
        return new JAXBElement<IDEDiskNotSupported>(_IDEDiskNotSupportedFault_QNAME, IDEDiskNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link IORMNotSupportedHostOnDatastore }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "IORMNotSupportedHostOnDatastoreFault")
    public JAXBElement<IORMNotSupportedHostOnDatastore> createIORMNotSupportedHostOnDatastoreFault(IORMNotSupportedHostOnDatastore value) {
        return new JAXBElement<IORMNotSupportedHostOnDatastore>(_IORMNotSupportedHostOnDatastoreFault_QNAME, IORMNotSupportedHostOnDatastore.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ImportHostAddFailure }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ImportHostAddFailureFault")
    public JAXBElement<ImportHostAddFailure> createImportHostAddFailureFault(ImportHostAddFailure value) {
        return new JAXBElement<ImportHostAddFailure>(_ImportHostAddFailureFault_QNAME, ImportHostAddFailure.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ImportOperationBulkFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ImportOperationBulkFaultFault")
    public JAXBElement<ImportOperationBulkFault> createImportOperationBulkFaultFault(ImportOperationBulkFault value) {
        return new JAXBElement<ImportOperationBulkFault>(_ImportOperationBulkFaultFault_QNAME, ImportOperationBulkFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InUseFeatureManipulationDisallowed }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InUseFeatureManipulationDisallowedFault")
    public JAXBElement<InUseFeatureManipulationDisallowed> createInUseFeatureManipulationDisallowedFault(InUseFeatureManipulationDisallowed value) {
        return new JAXBElement<InUseFeatureManipulationDisallowed>(_InUseFeatureManipulationDisallowedFault_QNAME, InUseFeatureManipulationDisallowed.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InaccessibleDatastore }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InaccessibleDatastoreFault")
    public JAXBElement<InaccessibleDatastore> createInaccessibleDatastoreFault(InaccessibleDatastore value) {
        return new JAXBElement<InaccessibleDatastore>(_InaccessibleDatastoreFault_QNAME, InaccessibleDatastore.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InaccessibleFTMetadataDatastore }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InaccessibleFTMetadataDatastoreFault")
    public JAXBElement<InaccessibleFTMetadataDatastore> createInaccessibleFTMetadataDatastoreFault(InaccessibleFTMetadataDatastore value) {
        return new JAXBElement<InaccessibleFTMetadataDatastore>(_InaccessibleFTMetadataDatastoreFault_QNAME, InaccessibleFTMetadataDatastore.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InaccessibleVFlashSource }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InaccessibleVFlashSourceFault")
    public JAXBElement<InaccessibleVFlashSource> createInaccessibleVFlashSourceFault(InaccessibleVFlashSource value) {
        return new JAXBElement<InaccessibleVFlashSource>(_InaccessibleVFlashSourceFault_QNAME, InaccessibleVFlashSource.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link IncompatibleDefaultDevice }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "IncompatibleDefaultDeviceFault")
    public JAXBElement<IncompatibleDefaultDevice> createIncompatibleDefaultDeviceFault(IncompatibleDefaultDevice value) {
        return new JAXBElement<IncompatibleDefaultDevice>(_IncompatibleDefaultDeviceFault_QNAME, IncompatibleDefaultDevice.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link IncompatibleHostForFtSecondary }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "IncompatibleHostForFtSecondaryFault")
    public JAXBElement<IncompatibleHostForFtSecondary> createIncompatibleHostForFtSecondaryFault(IncompatibleHostForFtSecondary value) {
        return new JAXBElement<IncompatibleHostForFtSecondary>(_IncompatibleHostForFtSecondaryFault_QNAME, IncompatibleHostForFtSecondary.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link IncompatibleHostForVmReplication }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "IncompatibleHostForVmReplicationFault")
    public JAXBElement<IncompatibleHostForVmReplication> createIncompatibleHostForVmReplicationFault(IncompatibleHostForVmReplication value) {
        return new JAXBElement<IncompatibleHostForVmReplication>(_IncompatibleHostForVmReplicationFault_QNAME, IncompatibleHostForVmReplication.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link IncompatibleSetting }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "IncompatibleSettingFault")
    public JAXBElement<IncompatibleSetting> createIncompatibleSettingFault(IncompatibleSetting value) {
        return new JAXBElement<IncompatibleSetting>(_IncompatibleSettingFault_QNAME, IncompatibleSetting.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link IncorrectFileType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "IncorrectFileTypeFault")
    public JAXBElement<IncorrectFileType> createIncorrectFileTypeFault(IncorrectFileType value) {
        return new JAXBElement<IncorrectFileType>(_IncorrectFileTypeFault_QNAME, IncorrectFileType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link IncorrectHostInformation }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "IncorrectHostInformationFault")
    public JAXBElement<IncorrectHostInformation> createIncorrectHostInformationFault(IncorrectHostInformation value) {
        return new JAXBElement<IncorrectHostInformation>(_IncorrectHostInformationFault_QNAME, IncorrectHostInformation.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link IndependentDiskVMotionNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "IndependentDiskVMotionNotSupportedFault")
    public JAXBElement<IndependentDiskVMotionNotSupported> createIndependentDiskVMotionNotSupportedFault(IndependentDiskVMotionNotSupported value) {
        return new JAXBElement<IndependentDiskVMotionNotSupported>(_IndependentDiskVMotionNotSupportedFault_QNAME, IndependentDiskVMotionNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InsufficientAgentVmsDeployed }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InsufficientAgentVmsDeployedFault")
    public JAXBElement<InsufficientAgentVmsDeployed> createInsufficientAgentVmsDeployedFault(InsufficientAgentVmsDeployed value) {
        return new JAXBElement<InsufficientAgentVmsDeployed>(_InsufficientAgentVmsDeployedFault_QNAME, InsufficientAgentVmsDeployed.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InsufficientCpuResourcesFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InsufficientCpuResourcesFaultFault")
    public JAXBElement<InsufficientCpuResourcesFault> createInsufficientCpuResourcesFaultFault(InsufficientCpuResourcesFault value) {
        return new JAXBElement<InsufficientCpuResourcesFault>(_InsufficientCpuResourcesFaultFault_QNAME, InsufficientCpuResourcesFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InsufficientDisks }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InsufficientDisksFault")
    public JAXBElement<InsufficientDisks> createInsufficientDisksFault(InsufficientDisks value) {
        return new JAXBElement<InsufficientDisks>(_InsufficientDisksFault_QNAME, InsufficientDisks.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InsufficientFailoverResourcesFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InsufficientFailoverResourcesFaultFault")
    public JAXBElement<InsufficientFailoverResourcesFault> createInsufficientFailoverResourcesFaultFault(InsufficientFailoverResourcesFault value) {
        return new JAXBElement<InsufficientFailoverResourcesFault>(_InsufficientFailoverResourcesFaultFault_QNAME, InsufficientFailoverResourcesFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InsufficientGraphicsResourcesFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InsufficientGraphicsResourcesFaultFault")
    public JAXBElement<InsufficientGraphicsResourcesFault> createInsufficientGraphicsResourcesFaultFault(InsufficientGraphicsResourcesFault value) {
        return new JAXBElement<InsufficientGraphicsResourcesFault>(_InsufficientGraphicsResourcesFaultFault_QNAME, InsufficientGraphicsResourcesFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InsufficientHostCapacityFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InsufficientHostCapacityFaultFault")
    public JAXBElement<InsufficientHostCapacityFault> createInsufficientHostCapacityFaultFault(InsufficientHostCapacityFault value) {
        return new JAXBElement<InsufficientHostCapacityFault>(_InsufficientHostCapacityFaultFault_QNAME, InsufficientHostCapacityFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InsufficientHostCpuCapacityFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InsufficientHostCpuCapacityFaultFault")
    public JAXBElement<InsufficientHostCpuCapacityFault> createInsufficientHostCpuCapacityFaultFault(InsufficientHostCpuCapacityFault value) {
        return new JAXBElement<InsufficientHostCpuCapacityFault>(_InsufficientHostCpuCapacityFaultFault_QNAME, InsufficientHostCpuCapacityFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InsufficientHostMemoryCapacityFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InsufficientHostMemoryCapacityFaultFault")
    public JAXBElement<InsufficientHostMemoryCapacityFault> createInsufficientHostMemoryCapacityFaultFault(InsufficientHostMemoryCapacityFault value) {
        return new JAXBElement<InsufficientHostMemoryCapacityFault>(_InsufficientHostMemoryCapacityFaultFault_QNAME, InsufficientHostMemoryCapacityFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InsufficientMemoryResourcesFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InsufficientMemoryResourcesFaultFault")
    public JAXBElement<InsufficientMemoryResourcesFault> createInsufficientMemoryResourcesFaultFault(InsufficientMemoryResourcesFault value) {
        return new JAXBElement<InsufficientMemoryResourcesFault>(_InsufficientMemoryResourcesFaultFault_QNAME, InsufficientMemoryResourcesFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InsufficientNetworkCapacity }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InsufficientNetworkCapacityFault")
    public JAXBElement<InsufficientNetworkCapacity> createInsufficientNetworkCapacityFault(InsufficientNetworkCapacity value) {
        return new JAXBElement<InsufficientNetworkCapacity>(_InsufficientNetworkCapacityFault_QNAME, InsufficientNetworkCapacity.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InsufficientNetworkResourcePoolCapacity }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InsufficientNetworkResourcePoolCapacityFault")
    public JAXBElement<InsufficientNetworkResourcePoolCapacity> createInsufficientNetworkResourcePoolCapacityFault(InsufficientNetworkResourcePoolCapacity value) {
        return new JAXBElement<InsufficientNetworkResourcePoolCapacity>(_InsufficientNetworkResourcePoolCapacityFault_QNAME, InsufficientNetworkResourcePoolCapacity.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InsufficientPerCpuCapacity }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InsufficientPerCpuCapacityFault")
    public JAXBElement<InsufficientPerCpuCapacity> createInsufficientPerCpuCapacityFault(InsufficientPerCpuCapacity value) {
        return new JAXBElement<InsufficientPerCpuCapacity>(_InsufficientPerCpuCapacityFault_QNAME, InsufficientPerCpuCapacity.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InsufficientResourcesFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InsufficientResourcesFaultFault")
    public JAXBElement<InsufficientResourcesFault> createInsufficientResourcesFaultFault(InsufficientResourcesFault value) {
        return new JAXBElement<InsufficientResourcesFault>(_InsufficientResourcesFaultFault_QNAME, InsufficientResourcesFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InsufficientStandbyCpuResource }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InsufficientStandbyCpuResourceFault")
    public JAXBElement<InsufficientStandbyCpuResource> createInsufficientStandbyCpuResourceFault(InsufficientStandbyCpuResource value) {
        return new JAXBElement<InsufficientStandbyCpuResource>(_InsufficientStandbyCpuResourceFault_QNAME, InsufficientStandbyCpuResource.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InsufficientStandbyMemoryResource }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InsufficientStandbyMemoryResourceFault")
    public JAXBElement<InsufficientStandbyMemoryResource> createInsufficientStandbyMemoryResourceFault(InsufficientStandbyMemoryResource value) {
        return new JAXBElement<InsufficientStandbyMemoryResource>(_InsufficientStandbyMemoryResourceFault_QNAME, InsufficientStandbyMemoryResource.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InsufficientStandbyResource }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InsufficientStandbyResourceFault")
    public JAXBElement<InsufficientStandbyResource> createInsufficientStandbyResourceFault(InsufficientStandbyResource value) {
        return new JAXBElement<InsufficientStandbyResource>(_InsufficientStandbyResourceFault_QNAME, InsufficientStandbyResource.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InsufficientStorageIops }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InsufficientStorageIopsFault")
    public JAXBElement<InsufficientStorageIops> createInsufficientStorageIopsFault(InsufficientStorageIops value) {
        return new JAXBElement<InsufficientStorageIops>(_InsufficientStorageIopsFault_QNAME, InsufficientStorageIops.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InsufficientStorageSpace }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InsufficientStorageSpaceFault")
    public JAXBElement<InsufficientStorageSpace> createInsufficientStorageSpaceFault(InsufficientStorageSpace value) {
        return new JAXBElement<InsufficientStorageSpace>(_InsufficientStorageSpaceFault_QNAME, InsufficientStorageSpace.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InsufficientVFlashResourcesFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InsufficientVFlashResourcesFaultFault")
    public JAXBElement<InsufficientVFlashResourcesFault> createInsufficientVFlashResourcesFaultFault(InsufficientVFlashResourcesFault value) {
        return new JAXBElement<InsufficientVFlashResourcesFault>(_InsufficientVFlashResourcesFaultFault_QNAME, InsufficientVFlashResourcesFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidAffinitySettingFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidAffinitySettingFaultFault")
    public JAXBElement<InvalidAffinitySettingFault> createInvalidAffinitySettingFaultFault(InvalidAffinitySettingFault value) {
        return new JAXBElement<InvalidAffinitySettingFault>(_InvalidAffinitySettingFaultFault_QNAME, InvalidAffinitySettingFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidBmcRole }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidBmcRoleFault")
    public JAXBElement<InvalidBmcRole> createInvalidBmcRoleFault(InvalidBmcRole value) {
        return new JAXBElement<InvalidBmcRole>(_InvalidBmcRoleFault_QNAME, InvalidBmcRole.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidBundle }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidBundleFault")
    public JAXBElement<InvalidBundle> createInvalidBundleFault(InvalidBundle value) {
        return new JAXBElement<InvalidBundle>(_InvalidBundleFault_QNAME, InvalidBundle.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidCAMCertificate }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidCAMCertificateFault")
    public JAXBElement<InvalidCAMCertificate> createInvalidCAMCertificateFault(InvalidCAMCertificate value) {
        return new JAXBElement<InvalidCAMCertificate>(_InvalidCAMCertificateFault_QNAME, InvalidCAMCertificate.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidCAMServer }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidCAMServerFault")
    public JAXBElement<InvalidCAMServer> createInvalidCAMServerFault(InvalidCAMServer value) {
        return new JAXBElement<InvalidCAMServer>(_InvalidCAMServerFault_QNAME, InvalidCAMServer.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidClientCertificate }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidClientCertificateFault")
    public JAXBElement<InvalidClientCertificate> createInvalidClientCertificateFault(InvalidClientCertificate value) {
        return new JAXBElement<InvalidClientCertificate>(_InvalidClientCertificateFault_QNAME, InvalidClientCertificate.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidController }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidControllerFault")
    public JAXBElement<InvalidController> createInvalidControllerFault(InvalidController value) {
        return new JAXBElement<InvalidController>(_InvalidControllerFault_QNAME, InvalidController.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidDasConfigArgument }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidDasConfigArgumentFault")
    public JAXBElement<InvalidDasConfigArgument> createInvalidDasConfigArgumentFault(InvalidDasConfigArgument value) {
        return new JAXBElement<InvalidDasConfigArgument>(_InvalidDasConfigArgumentFault_QNAME, InvalidDasConfigArgument.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidDasRestartPriorityForFtVm }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidDasRestartPriorityForFtVmFault")
    public JAXBElement<InvalidDasRestartPriorityForFtVm> createInvalidDasRestartPriorityForFtVmFault(InvalidDasRestartPriorityForFtVm value) {
        return new JAXBElement<InvalidDasRestartPriorityForFtVm>(_InvalidDasRestartPriorityForFtVmFault_QNAME, InvalidDasRestartPriorityForFtVm.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidDatastore }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidDatastoreFault")
    public JAXBElement<InvalidDatastore> createInvalidDatastoreFault(InvalidDatastore value) {
        return new JAXBElement<InvalidDatastore>(_InvalidDatastoreFault_QNAME, InvalidDatastore.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidDatastorePath }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidDatastorePathFault")
    public JAXBElement<InvalidDatastorePath> createInvalidDatastorePathFault(InvalidDatastorePath value) {
        return new JAXBElement<InvalidDatastorePath>(_InvalidDatastorePathFault_QNAME, InvalidDatastorePath.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidDatastoreState }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidDatastoreStateFault")
    public JAXBElement<InvalidDatastoreState> createInvalidDatastoreStateFault(InvalidDatastoreState value) {
        return new JAXBElement<InvalidDatastoreState>(_InvalidDatastoreStateFault_QNAME, InvalidDatastoreState.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidDeviceBacking }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidDeviceBackingFault")
    public JAXBElement<InvalidDeviceBacking> createInvalidDeviceBackingFault(InvalidDeviceBacking value) {
        return new JAXBElement<InvalidDeviceBacking>(_InvalidDeviceBackingFault_QNAME, InvalidDeviceBacking.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidDeviceOperation }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidDeviceOperationFault")
    public JAXBElement<InvalidDeviceOperation> createInvalidDeviceOperationFault(InvalidDeviceOperation value) {
        return new JAXBElement<InvalidDeviceOperation>(_InvalidDeviceOperationFault_QNAME, InvalidDeviceOperation.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidDeviceSpec }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidDeviceSpecFault")
    public JAXBElement<InvalidDeviceSpec> createInvalidDeviceSpecFault(InvalidDeviceSpec value) {
        return new JAXBElement<InvalidDeviceSpec>(_InvalidDeviceSpecFault_QNAME, InvalidDeviceSpec.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidDiskFormat }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidDiskFormatFault")
    public JAXBElement<InvalidDiskFormat> createInvalidDiskFormatFault(InvalidDiskFormat value) {
        return new JAXBElement<InvalidDiskFormat>(_InvalidDiskFormatFault_QNAME, InvalidDiskFormat.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidDrsBehaviorForFtVm }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidDrsBehaviorForFtVmFault")
    public JAXBElement<InvalidDrsBehaviorForFtVm> createInvalidDrsBehaviorForFtVmFault(InvalidDrsBehaviorForFtVm value) {
        return new JAXBElement<InvalidDrsBehaviorForFtVm>(_InvalidDrsBehaviorForFtVmFault_QNAME, InvalidDrsBehaviorForFtVm.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidEditionLicense }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidEditionLicenseFault")
    public JAXBElement<InvalidEditionLicense> createInvalidEditionLicenseFault(InvalidEditionLicense value) {
        return new JAXBElement<InvalidEditionLicense>(_InvalidEditionLicenseFault_QNAME, InvalidEditionLicense.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidEvent }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidEventFault")
    public JAXBElement<InvalidEvent> createInvalidEventFault(InvalidEvent value) {
        return new JAXBElement<InvalidEvent>(_InvalidEventFault_QNAME, InvalidEvent.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidFolder }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidFolderFault")
    public JAXBElement<InvalidFolder> createInvalidFolderFault(InvalidFolder value) {
        return new JAXBElement<InvalidFolder>(_InvalidFolderFault_QNAME, InvalidFolder.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidFormat }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidFormatFault")
    public JAXBElement<InvalidFormat> createInvalidFormatFault(InvalidFormat value) {
        return new JAXBElement<InvalidFormat>(_InvalidFormatFault_QNAME, InvalidFormat.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidGuestLogin }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidGuestLoginFault")
    public JAXBElement<InvalidGuestLogin> createInvalidGuestLoginFault(InvalidGuestLogin value) {
        return new JAXBElement<InvalidGuestLogin>(_InvalidGuestLoginFault_QNAME, InvalidGuestLogin.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidHostConnectionState }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidHostConnectionStateFault")
    public JAXBElement<InvalidHostConnectionState> createInvalidHostConnectionStateFault(InvalidHostConnectionState value) {
        return new JAXBElement<InvalidHostConnectionState>(_InvalidHostConnectionStateFault_QNAME, InvalidHostConnectionState.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidHostName }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidHostNameFault")
    public JAXBElement<InvalidHostName> createInvalidHostNameFault(InvalidHostName value) {
        return new JAXBElement<InvalidHostName>(_InvalidHostNameFault_QNAME, InvalidHostName.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidHostState }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidHostStateFault")
    public JAXBElement<InvalidHostState> createInvalidHostStateFault(InvalidHostState value) {
        return new JAXBElement<InvalidHostState>(_InvalidHostStateFault_QNAME, InvalidHostState.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidIndexArgument }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidIndexArgumentFault")
    public JAXBElement<InvalidIndexArgument> createInvalidIndexArgumentFault(InvalidIndexArgument value) {
        return new JAXBElement<InvalidIndexArgument>(_InvalidIndexArgumentFault_QNAME, InvalidIndexArgument.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidIpfixConfig }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidIpfixConfigFault")
    public JAXBElement<InvalidIpfixConfig> createInvalidIpfixConfigFault(InvalidIpfixConfig value) {
        return new JAXBElement<InvalidIpfixConfig>(_InvalidIpfixConfigFault_QNAME, InvalidIpfixConfig.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidIpmiLoginInfo }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidIpmiLoginInfoFault")
    public JAXBElement<InvalidIpmiLoginInfo> createInvalidIpmiLoginInfoFault(InvalidIpmiLoginInfo value) {
        return new JAXBElement<InvalidIpmiLoginInfo>(_InvalidIpmiLoginInfoFault_QNAME, InvalidIpmiLoginInfo.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidIpmiMacAddress }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidIpmiMacAddressFault")
    public JAXBElement<InvalidIpmiMacAddress> createInvalidIpmiMacAddressFault(InvalidIpmiMacAddress value) {
        return new JAXBElement<InvalidIpmiMacAddress>(_InvalidIpmiMacAddressFault_QNAME, InvalidIpmiMacAddress.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidLicense }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidLicenseFault")
    public JAXBElement<InvalidLicense> createInvalidLicenseFault(InvalidLicense value) {
        return new JAXBElement<InvalidLicense>(_InvalidLicenseFault_QNAME, InvalidLicense.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidLocale }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidLocaleFault")
    public JAXBElement<InvalidLocale> createInvalidLocaleFault(InvalidLocale value) {
        return new JAXBElement<InvalidLocale>(_InvalidLocaleFault_QNAME, InvalidLocale.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidLogin }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidLoginFault")
    public JAXBElement<InvalidLogin> createInvalidLoginFault(InvalidLogin value) {
        return new JAXBElement<InvalidLogin>(_InvalidLoginFault_QNAME, InvalidLogin.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidName }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidNameFault")
    public JAXBElement<InvalidName> createInvalidNameFault(InvalidName value) {
        return new JAXBElement<InvalidName>(_InvalidNameFault_QNAME, InvalidName.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidNasCredentials }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidNasCredentialsFault")
    public JAXBElement<InvalidNasCredentials> createInvalidNasCredentialsFault(InvalidNasCredentials value) {
        return new JAXBElement<InvalidNasCredentials>(_InvalidNasCredentialsFault_QNAME, InvalidNasCredentials.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidNetworkInType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidNetworkInTypeFault")
    public JAXBElement<InvalidNetworkInType> createInvalidNetworkInTypeFault(InvalidNetworkInType value) {
        return new JAXBElement<InvalidNetworkInType>(_InvalidNetworkInTypeFault_QNAME, InvalidNetworkInType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidNetworkResource }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidNetworkResourceFault")
    public JAXBElement<InvalidNetworkResource> createInvalidNetworkResourceFault(InvalidNetworkResource value) {
        return new JAXBElement<InvalidNetworkResource>(_InvalidNetworkResourceFault_QNAME, InvalidNetworkResource.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidOperationOnSecondaryVm }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidOperationOnSecondaryVmFault")
    public JAXBElement<InvalidOperationOnSecondaryVm> createInvalidOperationOnSecondaryVmFault(InvalidOperationOnSecondaryVm value) {
        return new JAXBElement<InvalidOperationOnSecondaryVm>(_InvalidOperationOnSecondaryVmFault_QNAME, InvalidOperationOnSecondaryVm.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidPowerState }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidPowerStateFault")
    public JAXBElement<InvalidPowerState> createInvalidPowerStateFault(InvalidPowerState value) {
        return new JAXBElement<InvalidPowerState>(_InvalidPowerStateFault_QNAME, InvalidPowerState.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidPrivilege }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidPrivilegeFault")
    public JAXBElement<InvalidPrivilege> createInvalidPrivilegeFault(InvalidPrivilege value) {
        return new JAXBElement<InvalidPrivilege>(_InvalidPrivilegeFault_QNAME, InvalidPrivilege.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidProfileReferenceHost }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidProfileReferenceHostFault")
    public JAXBElement<InvalidProfileReferenceHost> createInvalidProfileReferenceHostFault(InvalidProfileReferenceHost value) {
        return new JAXBElement<InvalidProfileReferenceHost>(_InvalidProfileReferenceHostFault_QNAME, InvalidProfileReferenceHost.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidPropertyType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidPropertyTypeFault")
    public JAXBElement<InvalidPropertyType> createInvalidPropertyTypeFault(InvalidPropertyType value) {
        return new JAXBElement<InvalidPropertyType>(_InvalidPropertyTypeFault_QNAME, InvalidPropertyType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidPropertyValue }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidPropertyValueFault")
    public JAXBElement<InvalidPropertyValue> createInvalidPropertyValueFault(InvalidPropertyValue value) {
        return new JAXBElement<InvalidPropertyValue>(_InvalidPropertyValueFault_QNAME, InvalidPropertyValue.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidResourcePoolStructureFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidResourcePoolStructureFaultFault")
    public JAXBElement<InvalidResourcePoolStructureFault> createInvalidResourcePoolStructureFaultFault(InvalidResourcePoolStructureFault value) {
        return new JAXBElement<InvalidResourcePoolStructureFault>(_InvalidResourcePoolStructureFaultFault_QNAME, InvalidResourcePoolStructureFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidSnapshotFormat }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidSnapshotFormatFault")
    public JAXBElement<InvalidSnapshotFormat> createInvalidSnapshotFormatFault(InvalidSnapshotFormat value) {
        return new JAXBElement<InvalidSnapshotFormat>(_InvalidSnapshotFormatFault_QNAME, InvalidSnapshotFormat.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidState }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidStateFault")
    public JAXBElement<InvalidState> createInvalidStateFault(InvalidState value) {
        return new JAXBElement<InvalidState>(_InvalidStateFault_QNAME, InvalidState.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidVmConfig }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidVmConfigFault")
    public JAXBElement<InvalidVmConfig> createInvalidVmConfigFault(InvalidVmConfig value) {
        return new JAXBElement<InvalidVmConfig>(_InvalidVmConfigFault_QNAME, InvalidVmConfig.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidVmState }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidVmStateFault")
    public JAXBElement<InvalidVmState> createInvalidVmStateFault(InvalidVmState value) {
        return new JAXBElement<InvalidVmState>(_InvalidVmStateFault_QNAME, InvalidVmState.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InventoryHasStandardAloneHosts }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InventoryHasStandardAloneHostsFault")
    public JAXBElement<InventoryHasStandardAloneHosts> createInventoryHasStandardAloneHostsFault(InventoryHasStandardAloneHosts value) {
        return new JAXBElement<InventoryHasStandardAloneHosts>(_InventoryHasStandardAloneHostsFault_QNAME, InventoryHasStandardAloneHosts.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link IpHostnameGeneratorError }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "IpHostnameGeneratorErrorFault")
    public JAXBElement<IpHostnameGeneratorError> createIpHostnameGeneratorErrorFault(IpHostnameGeneratorError value) {
        return new JAXBElement<IpHostnameGeneratorError>(_IpHostnameGeneratorErrorFault_QNAME, IpHostnameGeneratorError.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link IscsiFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "IscsiFaultFault")
    public JAXBElement<IscsiFault> createIscsiFaultFault(IscsiFault value) {
        return new JAXBElement<IscsiFault>(_IscsiFaultFault_QNAME, IscsiFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link IscsiFaultInvalidVnic }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "IscsiFaultInvalidVnicFault")
    public JAXBElement<IscsiFaultInvalidVnic> createIscsiFaultInvalidVnicFault(IscsiFaultInvalidVnic value) {
        return new JAXBElement<IscsiFaultInvalidVnic>(_IscsiFaultInvalidVnicFault_QNAME, IscsiFaultInvalidVnic.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link IscsiFaultPnicInUse }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "IscsiFaultPnicInUseFault")
    public JAXBElement<IscsiFaultPnicInUse> createIscsiFaultPnicInUseFault(IscsiFaultPnicInUse value) {
        return new JAXBElement<IscsiFaultPnicInUse>(_IscsiFaultPnicInUseFault_QNAME, IscsiFaultPnicInUse.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link IscsiFaultVnicAlreadyBound }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "IscsiFaultVnicAlreadyBoundFault")
    public JAXBElement<IscsiFaultVnicAlreadyBound> createIscsiFaultVnicAlreadyBoundFault(IscsiFaultVnicAlreadyBound value) {
        return new JAXBElement<IscsiFaultVnicAlreadyBound>(_IscsiFaultVnicAlreadyBoundFault_QNAME, IscsiFaultVnicAlreadyBound.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link IscsiFaultVnicHasActivePaths }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "IscsiFaultVnicHasActivePathsFault")
    public JAXBElement<IscsiFaultVnicHasActivePaths> createIscsiFaultVnicHasActivePathsFault(IscsiFaultVnicHasActivePaths value) {
        return new JAXBElement<IscsiFaultVnicHasActivePaths>(_IscsiFaultVnicHasActivePathsFault_QNAME, IscsiFaultVnicHasActivePaths.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link IscsiFaultVnicHasMultipleUplinks }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "IscsiFaultVnicHasMultipleUplinksFault")
    public JAXBElement<IscsiFaultVnicHasMultipleUplinks> createIscsiFaultVnicHasMultipleUplinksFault(IscsiFaultVnicHasMultipleUplinks value) {
        return new JAXBElement<IscsiFaultVnicHasMultipleUplinks>(_IscsiFaultVnicHasMultipleUplinksFault_QNAME, IscsiFaultVnicHasMultipleUplinks.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link IscsiFaultVnicHasNoUplinks }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "IscsiFaultVnicHasNoUplinksFault")
    public JAXBElement<IscsiFaultVnicHasNoUplinks> createIscsiFaultVnicHasNoUplinksFault(IscsiFaultVnicHasNoUplinks value) {
        return new JAXBElement<IscsiFaultVnicHasNoUplinks>(_IscsiFaultVnicHasNoUplinksFault_QNAME, IscsiFaultVnicHasNoUplinks.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link IscsiFaultVnicHasWrongUplink }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "IscsiFaultVnicHasWrongUplinkFault")
    public JAXBElement<IscsiFaultVnicHasWrongUplink> createIscsiFaultVnicHasWrongUplinkFault(IscsiFaultVnicHasWrongUplink value) {
        return new JAXBElement<IscsiFaultVnicHasWrongUplink>(_IscsiFaultVnicHasWrongUplinkFault_QNAME, IscsiFaultVnicHasWrongUplink.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link IscsiFaultVnicInUse }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "IscsiFaultVnicInUseFault")
    public JAXBElement<IscsiFaultVnicInUse> createIscsiFaultVnicInUseFault(IscsiFaultVnicInUse value) {
        return new JAXBElement<IscsiFaultVnicInUse>(_IscsiFaultVnicInUseFault_QNAME, IscsiFaultVnicInUse.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link IscsiFaultVnicIsLastPath }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "IscsiFaultVnicIsLastPathFault")
    public JAXBElement<IscsiFaultVnicIsLastPath> createIscsiFaultVnicIsLastPathFault(IscsiFaultVnicIsLastPath value) {
        return new JAXBElement<IscsiFaultVnicIsLastPath>(_IscsiFaultVnicIsLastPathFault_QNAME, IscsiFaultVnicIsLastPath.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link IscsiFaultVnicNotBound }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "IscsiFaultVnicNotBoundFault")
    public JAXBElement<IscsiFaultVnicNotBound> createIscsiFaultVnicNotBoundFault(IscsiFaultVnicNotBound value) {
        return new JAXBElement<IscsiFaultVnicNotBound>(_IscsiFaultVnicNotBoundFault_QNAME, IscsiFaultVnicNotBound.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link IscsiFaultVnicNotFound }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "IscsiFaultVnicNotFoundFault")
    public JAXBElement<IscsiFaultVnicNotFound> createIscsiFaultVnicNotFoundFault(IscsiFaultVnicNotFound value) {
        return new JAXBElement<IscsiFaultVnicNotFound>(_IscsiFaultVnicNotFoundFault_QNAME, IscsiFaultVnicNotFound.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link LargeRDMConversionNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "LargeRDMConversionNotSupportedFault")
    public JAXBElement<LargeRDMConversionNotSupported> createLargeRDMConversionNotSupportedFault(LargeRDMConversionNotSupported value) {
        return new JAXBElement<LargeRDMConversionNotSupported>(_LargeRDMConversionNotSupportedFault_QNAME, LargeRDMConversionNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link LargeRDMNotSupportedOnDatastore }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "LargeRDMNotSupportedOnDatastoreFault")
    public JAXBElement<LargeRDMNotSupportedOnDatastore> createLargeRDMNotSupportedOnDatastoreFault(LargeRDMNotSupportedOnDatastore value) {
        return new JAXBElement<LargeRDMNotSupportedOnDatastore>(_LargeRDMNotSupportedOnDatastoreFault_QNAME, LargeRDMNotSupportedOnDatastore.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link LegacyNetworkInterfaceInUse }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "LegacyNetworkInterfaceInUseFault")
    public JAXBElement<LegacyNetworkInterfaceInUse> createLegacyNetworkInterfaceInUseFault(LegacyNetworkInterfaceInUse value) {
        return new JAXBElement<LegacyNetworkInterfaceInUse>(_LegacyNetworkInterfaceInUseFault_QNAME, LegacyNetworkInterfaceInUse.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link LicenseAssignmentFailed }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "LicenseAssignmentFailedFault")
    public JAXBElement<LicenseAssignmentFailed> createLicenseAssignmentFailedFault(LicenseAssignmentFailed value) {
        return new JAXBElement<LicenseAssignmentFailed>(_LicenseAssignmentFailedFault_QNAME, LicenseAssignmentFailed.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link LicenseDowngradeDisallowed }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "LicenseDowngradeDisallowedFault")
    public JAXBElement<LicenseDowngradeDisallowed> createLicenseDowngradeDisallowedFault(LicenseDowngradeDisallowed value) {
        return new JAXBElement<LicenseDowngradeDisallowed>(_LicenseDowngradeDisallowedFault_QNAME, LicenseDowngradeDisallowed.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link LicenseEntityNotFound }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "LicenseEntityNotFoundFault")
    public JAXBElement<LicenseEntityNotFound> createLicenseEntityNotFoundFault(LicenseEntityNotFound value) {
        return new JAXBElement<LicenseEntityNotFound>(_LicenseEntityNotFoundFault_QNAME, LicenseEntityNotFound.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link LicenseExpired }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "LicenseExpiredFault")
    public JAXBElement<LicenseExpired> createLicenseExpiredFault(LicenseExpired value) {
        return new JAXBElement<LicenseExpired>(_LicenseExpiredFault_QNAME, LicenseExpired.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link LicenseKeyEntityMismatch }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "LicenseKeyEntityMismatchFault")
    public JAXBElement<LicenseKeyEntityMismatch> createLicenseKeyEntityMismatchFault(LicenseKeyEntityMismatch value) {
        return new JAXBElement<LicenseKeyEntityMismatch>(_LicenseKeyEntityMismatchFault_QNAME, LicenseKeyEntityMismatch.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link LicenseRestricted }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "LicenseRestrictedFault")
    public JAXBElement<LicenseRestricted> createLicenseRestrictedFault(LicenseRestricted value) {
        return new JAXBElement<LicenseRestricted>(_LicenseRestrictedFault_QNAME, LicenseRestricted.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link LicenseServerUnavailable }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "LicenseServerUnavailableFault")
    public JAXBElement<LicenseServerUnavailable> createLicenseServerUnavailableFault(LicenseServerUnavailable value) {
        return new JAXBElement<LicenseServerUnavailable>(_LicenseServerUnavailableFault_QNAME, LicenseServerUnavailable.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link LicenseSourceUnavailable }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "LicenseSourceUnavailableFault")
    public JAXBElement<LicenseSourceUnavailable> createLicenseSourceUnavailableFault(LicenseSourceUnavailable value) {
        return new JAXBElement<LicenseSourceUnavailable>(_LicenseSourceUnavailableFault_QNAME, LicenseSourceUnavailable.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link LimitExceeded }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "LimitExceededFault")
    public JAXBElement<LimitExceeded> createLimitExceededFault(LimitExceeded value) {
        return new JAXBElement<LimitExceeded>(_LimitExceededFault_QNAME, LimitExceeded.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link LinuxVolumeNotClean }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "LinuxVolumeNotCleanFault")
    public JAXBElement<LinuxVolumeNotClean> createLinuxVolumeNotCleanFault(LinuxVolumeNotClean value) {
        return new JAXBElement<LinuxVolumeNotClean>(_LinuxVolumeNotCleanFault_QNAME, LinuxVolumeNotClean.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link LogBundlingFailed }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "LogBundlingFailedFault")
    public JAXBElement<LogBundlingFailed> createLogBundlingFailedFault(LogBundlingFailed value) {
        return new JAXBElement<LogBundlingFailed>(_LogBundlingFailedFault_QNAME, LogBundlingFailed.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MaintenanceModeFileMove }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MaintenanceModeFileMoveFault")
    public JAXBElement<MaintenanceModeFileMove> createMaintenanceModeFileMoveFault(MaintenanceModeFileMove value) {
        return new JAXBElement<MaintenanceModeFileMove>(_MaintenanceModeFileMoveFault_QNAME, MaintenanceModeFileMove.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MemoryFileFormatNotSupportedByDatastore }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MemoryFileFormatNotSupportedByDatastoreFault")
    public JAXBElement<MemoryFileFormatNotSupportedByDatastore> createMemoryFileFormatNotSupportedByDatastoreFault(MemoryFileFormatNotSupportedByDatastore value) {
        return new JAXBElement<MemoryFileFormatNotSupportedByDatastore>(_MemoryFileFormatNotSupportedByDatastoreFault_QNAME, MemoryFileFormatNotSupportedByDatastore.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MemoryHotPlugNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MemoryHotPlugNotSupportedFault")
    public JAXBElement<MemoryHotPlugNotSupported> createMemoryHotPlugNotSupportedFault(MemoryHotPlugNotSupported value) {
        return new JAXBElement<MemoryHotPlugNotSupported>(_MemoryHotPlugNotSupportedFault_QNAME, MemoryHotPlugNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MemorySizeNotRecommended }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MemorySizeNotRecommendedFault")
    public JAXBElement<MemorySizeNotRecommended> createMemorySizeNotRecommendedFault(MemorySizeNotRecommended value) {
        return new JAXBElement<MemorySizeNotRecommended>(_MemorySizeNotRecommendedFault_QNAME, MemorySizeNotRecommended.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MemorySizeNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MemorySizeNotSupportedFault")
    public JAXBElement<MemorySizeNotSupported> createMemorySizeNotSupportedFault(MemorySizeNotSupported value) {
        return new JAXBElement<MemorySizeNotSupported>(_MemorySizeNotSupportedFault_QNAME, MemorySizeNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MemorySizeNotSupportedByDatastore }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MemorySizeNotSupportedByDatastoreFault")
    public JAXBElement<MemorySizeNotSupportedByDatastore> createMemorySizeNotSupportedByDatastoreFault(MemorySizeNotSupportedByDatastore value) {
        return new JAXBElement<MemorySizeNotSupportedByDatastore>(_MemorySizeNotSupportedByDatastoreFault_QNAME, MemorySizeNotSupportedByDatastore.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MemorySnapshotOnIndependentDisk }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MemorySnapshotOnIndependentDiskFault")
    public JAXBElement<MemorySnapshotOnIndependentDisk> createMemorySnapshotOnIndependentDiskFault(MemorySnapshotOnIndependentDisk value) {
        return new JAXBElement<MemorySnapshotOnIndependentDisk>(_MemorySnapshotOnIndependentDiskFault_QNAME, MemorySnapshotOnIndependentDisk.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MethodAlreadyDisabledFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MethodAlreadyDisabledFaultFault")
    public JAXBElement<MethodAlreadyDisabledFault> createMethodAlreadyDisabledFaultFault(MethodAlreadyDisabledFault value) {
        return new JAXBElement<MethodAlreadyDisabledFault>(_MethodAlreadyDisabledFaultFault_QNAME, MethodAlreadyDisabledFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MethodDisabled }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MethodDisabledFault")
    public JAXBElement<MethodDisabled> createMethodDisabledFault(MethodDisabled value) {
        return new JAXBElement<MethodDisabled>(_MethodDisabledFault_QNAME, MethodDisabled.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MigrationDisabled }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MigrationDisabledFault")
    public JAXBElement<MigrationDisabled> createMigrationDisabledFault(MigrationDisabled value) {
        return new JAXBElement<MigrationDisabled>(_MigrationDisabledFault_QNAME, MigrationDisabled.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MigrationFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MigrationFaultFault")
    public JAXBElement<MigrationFault> createMigrationFaultFault(MigrationFault value) {
        return new JAXBElement<MigrationFault>(_MigrationFaultFault_QNAME, MigrationFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MigrationFeatureNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MigrationFeatureNotSupportedFault")
    public JAXBElement<MigrationFeatureNotSupported> createMigrationFeatureNotSupportedFault(MigrationFeatureNotSupported value) {
        return new JAXBElement<MigrationFeatureNotSupported>(_MigrationFeatureNotSupportedFault_QNAME, MigrationFeatureNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MigrationNotReady }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MigrationNotReadyFault")
    public JAXBElement<MigrationNotReady> createMigrationNotReadyFault(MigrationNotReady value) {
        return new JAXBElement<MigrationNotReady>(_MigrationNotReadyFault_QNAME, MigrationNotReady.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MismatchedBundle }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MismatchedBundleFault")
    public JAXBElement<MismatchedBundle> createMismatchedBundleFault(MismatchedBundle value) {
        return new JAXBElement<MismatchedBundle>(_MismatchedBundleFault_QNAME, MismatchedBundle.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MismatchedNetworkPolicies }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MismatchedNetworkPoliciesFault")
    public JAXBElement<MismatchedNetworkPolicies> createMismatchedNetworkPoliciesFault(MismatchedNetworkPolicies value) {
        return new JAXBElement<MismatchedNetworkPolicies>(_MismatchedNetworkPoliciesFault_QNAME, MismatchedNetworkPolicies.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MismatchedVMotionNetworkNames }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MismatchedVMotionNetworkNamesFault")
    public JAXBElement<MismatchedVMotionNetworkNames> createMismatchedVMotionNetworkNamesFault(MismatchedVMotionNetworkNames value) {
        return new JAXBElement<MismatchedVMotionNetworkNames>(_MismatchedVMotionNetworkNamesFault_QNAME, MismatchedVMotionNetworkNames.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MissingBmcSupport }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MissingBmcSupportFault")
    public JAXBElement<MissingBmcSupport> createMissingBmcSupportFault(MissingBmcSupport value) {
        return new JAXBElement<MissingBmcSupport>(_MissingBmcSupportFault_QNAME, MissingBmcSupport.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MissingController }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MissingControllerFault")
    public JAXBElement<MissingController> createMissingControllerFault(MissingController value) {
        return new JAXBElement<MissingController>(_MissingControllerFault_QNAME, MissingController.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MissingIpPool }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MissingIpPoolFault")
    public JAXBElement<MissingIpPool> createMissingIpPoolFault(MissingIpPool value) {
        return new JAXBElement<MissingIpPool>(_MissingIpPoolFault_QNAME, MissingIpPool.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MissingLinuxCustResources }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MissingLinuxCustResourcesFault")
    public JAXBElement<MissingLinuxCustResources> createMissingLinuxCustResourcesFault(MissingLinuxCustResources value) {
        return new JAXBElement<MissingLinuxCustResources>(_MissingLinuxCustResourcesFault_QNAME, MissingLinuxCustResources.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MissingNetworkIpConfig }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MissingNetworkIpConfigFault")
    public JAXBElement<MissingNetworkIpConfig> createMissingNetworkIpConfigFault(MissingNetworkIpConfig value) {
        return new JAXBElement<MissingNetworkIpConfig>(_MissingNetworkIpConfigFault_QNAME, MissingNetworkIpConfig.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MissingPowerOffConfiguration }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MissingPowerOffConfigurationFault")
    public JAXBElement<MissingPowerOffConfiguration> createMissingPowerOffConfigurationFault(MissingPowerOffConfiguration value) {
        return new JAXBElement<MissingPowerOffConfiguration>(_MissingPowerOffConfigurationFault_QNAME, MissingPowerOffConfiguration.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MissingPowerOnConfiguration }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MissingPowerOnConfigurationFault")
    public JAXBElement<MissingPowerOnConfiguration> createMissingPowerOnConfigurationFault(MissingPowerOnConfiguration value) {
        return new JAXBElement<MissingPowerOnConfiguration>(_MissingPowerOnConfigurationFault_QNAME, MissingPowerOnConfiguration.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MissingWindowsCustResources }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MissingWindowsCustResourcesFault")
    public JAXBElement<MissingWindowsCustResources> createMissingWindowsCustResourcesFault(MissingWindowsCustResources value) {
        return new JAXBElement<MissingWindowsCustResources>(_MissingWindowsCustResourcesFault_QNAME, MissingWindowsCustResources.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MksConnectionLimitReached }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MksConnectionLimitReachedFault")
    public JAXBElement<MksConnectionLimitReached> createMksConnectionLimitReachedFault(MksConnectionLimitReached value) {
        return new JAXBElement<MksConnectionLimitReached>(_MksConnectionLimitReachedFault_QNAME, MksConnectionLimitReached.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MountError }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MountErrorFault")
    public JAXBElement<MountError> createMountErrorFault(MountError value) {
        return new JAXBElement<MountError>(_MountErrorFault_QNAME, MountError.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MultiWriterNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MultiWriterNotSupportedFault")
    public JAXBElement<MultiWriterNotSupported> createMultiWriterNotSupportedFault(MultiWriterNotSupported value) {
        return new JAXBElement<MultiWriterNotSupported>(_MultiWriterNotSupportedFault_QNAME, MultiWriterNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MultipleCertificatesVerifyFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MultipleCertificatesVerifyFaultFault")
    public JAXBElement<MultipleCertificatesVerifyFault> createMultipleCertificatesVerifyFaultFault(MultipleCertificatesVerifyFault value) {
        return new JAXBElement<MultipleCertificatesVerifyFault>(_MultipleCertificatesVerifyFaultFault_QNAME, MultipleCertificatesVerifyFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MultipleSnapshotsNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MultipleSnapshotsNotSupportedFault")
    public JAXBElement<MultipleSnapshotsNotSupported> createMultipleSnapshotsNotSupportedFault(MultipleSnapshotsNotSupported value) {
        return new JAXBElement<MultipleSnapshotsNotSupported>(_MultipleSnapshotsNotSupportedFault_QNAME, MultipleSnapshotsNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NamespaceFull }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NamespaceFullFault")
    public JAXBElement<NamespaceFull> createNamespaceFullFault(NamespaceFull value) {
        return new JAXBElement<NamespaceFull>(_NamespaceFullFault_QNAME, NamespaceFull.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NamespaceLimitReached }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NamespaceLimitReachedFault")
    public JAXBElement<NamespaceLimitReached> createNamespaceLimitReachedFault(NamespaceLimitReached value) {
        return new JAXBElement<NamespaceLimitReached>(_NamespaceLimitReachedFault_QNAME, NamespaceLimitReached.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NamespaceWriteProtected }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NamespaceWriteProtectedFault")
    public JAXBElement<NamespaceWriteProtected> createNamespaceWriteProtectedFault(NamespaceWriteProtected value) {
        return new JAXBElement<NamespaceWriteProtected>(_NamespaceWriteProtectedFault_QNAME, NamespaceWriteProtected.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NasConfigFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NasConfigFaultFault")
    public JAXBElement<NasConfigFault> createNasConfigFaultFault(NasConfigFault value) {
        return new JAXBElement<NasConfigFault>(_NasConfigFaultFault_QNAME, NasConfigFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NasConnectionLimitReached }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NasConnectionLimitReachedFault")
    public JAXBElement<NasConnectionLimitReached> createNasConnectionLimitReachedFault(NasConnectionLimitReached value) {
        return new JAXBElement<NasConnectionLimitReached>(_NasConnectionLimitReachedFault_QNAME, NasConnectionLimitReached.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NasSessionCredentialConflict }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NasSessionCredentialConflictFault")
    public JAXBElement<NasSessionCredentialConflict> createNasSessionCredentialConflictFault(NasSessionCredentialConflict value) {
        return new JAXBElement<NasSessionCredentialConflict>(_NasSessionCredentialConflictFault_QNAME, NasSessionCredentialConflict.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NasVolumeNotMounted }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NasVolumeNotMountedFault")
    public JAXBElement<NasVolumeNotMounted> createNasVolumeNotMountedFault(NasVolumeNotMounted value) {
        return new JAXBElement<NasVolumeNotMounted>(_NasVolumeNotMountedFault_QNAME, NasVolumeNotMounted.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NetworkCopyFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NetworkCopyFaultFault")
    public JAXBElement<NetworkCopyFault> createNetworkCopyFaultFault(NetworkCopyFault value) {
        return new JAXBElement<NetworkCopyFault>(_NetworkCopyFaultFault_QNAME, NetworkCopyFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NetworkDisruptedAndConfigRolledBack }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NetworkDisruptedAndConfigRolledBackFault")
    public JAXBElement<NetworkDisruptedAndConfigRolledBack> createNetworkDisruptedAndConfigRolledBackFault(NetworkDisruptedAndConfigRolledBack value) {
        return new JAXBElement<NetworkDisruptedAndConfigRolledBack>(_NetworkDisruptedAndConfigRolledBackFault_QNAME, NetworkDisruptedAndConfigRolledBack.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NetworkInaccessible }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NetworkInaccessibleFault")
    public JAXBElement<NetworkInaccessible> createNetworkInaccessibleFault(NetworkInaccessible value) {
        return new JAXBElement<NetworkInaccessible>(_NetworkInaccessibleFault_QNAME, NetworkInaccessible.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NetworksMayNotBeTheSame }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NetworksMayNotBeTheSameFault")
    public JAXBElement<NetworksMayNotBeTheSame> createNetworksMayNotBeTheSameFault(NetworksMayNotBeTheSame value) {
        return new JAXBElement<NetworksMayNotBeTheSame>(_NetworksMayNotBeTheSameFault_QNAME, NetworksMayNotBeTheSame.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NicSettingMismatch }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NicSettingMismatchFault")
    public JAXBElement<NicSettingMismatch> createNicSettingMismatchFault(NicSettingMismatch value) {
        return new JAXBElement<NicSettingMismatch>(_NicSettingMismatchFault_QNAME, NicSettingMismatch.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NoActiveHostInCluster }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NoActiveHostInClusterFault")
    public JAXBElement<NoActiveHostInCluster> createNoActiveHostInClusterFault(NoActiveHostInCluster value) {
        return new JAXBElement<NoActiveHostInCluster>(_NoActiveHostInClusterFault_QNAME, NoActiveHostInCluster.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NoAvailableIp }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NoAvailableIpFault")
    public JAXBElement<NoAvailableIp> createNoAvailableIpFault(NoAvailableIp value) {
        return new JAXBElement<NoAvailableIp>(_NoAvailableIpFault_QNAME, NoAvailableIp.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NoClientCertificate }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NoClientCertificateFault")
    public JAXBElement<NoClientCertificate> createNoClientCertificateFault(NoClientCertificate value) {
        return new JAXBElement<NoClientCertificate>(_NoClientCertificateFault_QNAME, NoClientCertificate.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NoCompatibleDatastore }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NoCompatibleDatastoreFault")
    public JAXBElement<NoCompatibleDatastore> createNoCompatibleDatastoreFault(NoCompatibleDatastore value) {
        return new JAXBElement<NoCompatibleDatastore>(_NoCompatibleDatastoreFault_QNAME, NoCompatibleDatastore.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NoCompatibleHardAffinityHost }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NoCompatibleHardAffinityHostFault")
    public JAXBElement<NoCompatibleHardAffinityHost> createNoCompatibleHardAffinityHostFault(NoCompatibleHardAffinityHost value) {
        return new JAXBElement<NoCompatibleHardAffinityHost>(_NoCompatibleHardAffinityHostFault_QNAME, NoCompatibleHardAffinityHost.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NoCompatibleHost }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NoCompatibleHostFault")
    public JAXBElement<NoCompatibleHost> createNoCompatibleHostFault(NoCompatibleHost value) {
        return new JAXBElement<NoCompatibleHost>(_NoCompatibleHostFault_QNAME, NoCompatibleHost.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NoCompatibleHostWithAccessToDevice }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NoCompatibleHostWithAccessToDeviceFault")
    public JAXBElement<NoCompatibleHostWithAccessToDevice> createNoCompatibleHostWithAccessToDeviceFault(NoCompatibleHostWithAccessToDevice value) {
        return new JAXBElement<NoCompatibleHostWithAccessToDevice>(_NoCompatibleHostWithAccessToDeviceFault_QNAME, NoCompatibleHostWithAccessToDevice.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NoCompatibleSoftAffinityHost }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NoCompatibleSoftAffinityHostFault")
    public JAXBElement<NoCompatibleSoftAffinityHost> createNoCompatibleSoftAffinityHostFault(NoCompatibleSoftAffinityHost value) {
        return new JAXBElement<NoCompatibleSoftAffinityHost>(_NoCompatibleSoftAffinityHostFault_QNAME, NoCompatibleSoftAffinityHost.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NoConnectedDatastore }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NoConnectedDatastoreFault")
    public JAXBElement<NoConnectedDatastore> createNoConnectedDatastoreFault(NoConnectedDatastore value) {
        return new JAXBElement<NoConnectedDatastore>(_NoConnectedDatastoreFault_QNAME, NoConnectedDatastore.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NoDiskFound }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NoDiskFoundFault")
    public JAXBElement<NoDiskFound> createNoDiskFoundFault(NoDiskFound value) {
        return new JAXBElement<NoDiskFound>(_NoDiskFoundFault_QNAME, NoDiskFound.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NoDiskSpace }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NoDiskSpaceFault")
    public JAXBElement<NoDiskSpace> createNoDiskSpaceFault(NoDiskSpace value) {
        return new JAXBElement<NoDiskSpace>(_NoDiskSpaceFault_QNAME, NoDiskSpace.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NoDisksToCustomize }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NoDisksToCustomizeFault")
    public JAXBElement<NoDisksToCustomize> createNoDisksToCustomizeFault(NoDisksToCustomize value) {
        return new JAXBElement<NoDisksToCustomize>(_NoDisksToCustomizeFault_QNAME, NoDisksToCustomize.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NoGateway }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NoGatewayFault")
    public JAXBElement<NoGateway> createNoGatewayFault(NoGateway value) {
        return new JAXBElement<NoGateway>(_NoGatewayFault_QNAME, NoGateway.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NoGuestHeartbeat }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NoGuestHeartbeatFault")
    public JAXBElement<NoGuestHeartbeat> createNoGuestHeartbeatFault(NoGuestHeartbeat value) {
        return new JAXBElement<NoGuestHeartbeat>(_NoGuestHeartbeatFault_QNAME, NoGuestHeartbeat.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NoHost }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NoHostFault")
    public JAXBElement<NoHost> createNoHostFault(NoHost value) {
        return new JAXBElement<NoHost>(_NoHostFault_QNAME, NoHost.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NoHostSuitableForFtSecondary }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NoHostSuitableForFtSecondaryFault")
    public JAXBElement<NoHostSuitableForFtSecondary> createNoHostSuitableForFtSecondaryFault(NoHostSuitableForFtSecondary value) {
        return new JAXBElement<NoHostSuitableForFtSecondary>(_NoHostSuitableForFtSecondaryFault_QNAME, NoHostSuitableForFtSecondary.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NoLicenseServerConfigured }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NoLicenseServerConfiguredFault")
    public JAXBElement<NoLicenseServerConfigured> createNoLicenseServerConfiguredFault(NoLicenseServerConfigured value) {
        return new JAXBElement<NoLicenseServerConfigured>(_NoLicenseServerConfiguredFault_QNAME, NoLicenseServerConfigured.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NoPeerHostFound }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NoPeerHostFoundFault")
    public JAXBElement<NoPeerHostFound> createNoPeerHostFoundFault(NoPeerHostFound value) {
        return new JAXBElement<NoPeerHostFound>(_NoPeerHostFoundFault_QNAME, NoPeerHostFound.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NoPermission }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NoPermissionFault")
    public JAXBElement<NoPermission> createNoPermissionFault(NoPermission value) {
        return new JAXBElement<NoPermission>(_NoPermissionFault_QNAME, NoPermission.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NoPermissionOnAD }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NoPermissionOnADFault")
    public JAXBElement<NoPermissionOnAD> createNoPermissionOnADFault(NoPermissionOnAD value) {
        return new JAXBElement<NoPermissionOnAD>(_NoPermissionOnADFault_QNAME, NoPermissionOnAD.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NoPermissionOnHost }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NoPermissionOnHostFault")
    public JAXBElement<NoPermissionOnHost> createNoPermissionOnHostFault(NoPermissionOnHost value) {
        return new JAXBElement<NoPermissionOnHost>(_NoPermissionOnHostFault_QNAME, NoPermissionOnHost.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NoPermissionOnNasVolume }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NoPermissionOnNasVolumeFault")
    public JAXBElement<NoPermissionOnNasVolume> createNoPermissionOnNasVolumeFault(NoPermissionOnNasVolume value) {
        return new JAXBElement<NoPermissionOnNasVolume>(_NoPermissionOnNasVolumeFault_QNAME, NoPermissionOnNasVolume.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NoSubjectName }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NoSubjectNameFault")
    public JAXBElement<NoSubjectName> createNoSubjectNameFault(NoSubjectName value) {
        return new JAXBElement<NoSubjectName>(_NoSubjectNameFault_QNAME, NoSubjectName.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NoVcManagedIpConfigured }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NoVcManagedIpConfiguredFault")
    public JAXBElement<NoVcManagedIpConfigured> createNoVcManagedIpConfiguredFault(NoVcManagedIpConfigured value) {
        return new JAXBElement<NoVcManagedIpConfigured>(_NoVcManagedIpConfiguredFault_QNAME, NoVcManagedIpConfigured.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NoVirtualNic }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NoVirtualNicFault")
    public JAXBElement<NoVirtualNic> createNoVirtualNicFault(NoVirtualNic value) {
        return new JAXBElement<NoVirtualNic>(_NoVirtualNicFault_QNAME, NoVirtualNic.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NoVmInVApp }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NoVmInVAppFault")
    public JAXBElement<NoVmInVApp> createNoVmInVAppFault(NoVmInVApp value) {
        return new JAXBElement<NoVmInVApp>(_NoVmInVAppFault_QNAME, NoVmInVApp.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NonADUserRequired }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NonADUserRequiredFault")
    public JAXBElement<NonADUserRequired> createNonADUserRequiredFault(NonADUserRequired value) {
        return new JAXBElement<NonADUserRequired>(_NonADUserRequiredFault_QNAME, NonADUserRequired.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NonHomeRDMVMotionNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NonHomeRDMVMotionNotSupportedFault")
    public JAXBElement<NonHomeRDMVMotionNotSupported> createNonHomeRDMVMotionNotSupportedFault(NonHomeRDMVMotionNotSupported value) {
        return new JAXBElement<NonHomeRDMVMotionNotSupported>(_NonHomeRDMVMotionNotSupportedFault_QNAME, NonHomeRDMVMotionNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NonPersistentDisksNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NonPersistentDisksNotSupportedFault")
    public JAXBElement<NonPersistentDisksNotSupported> createNonPersistentDisksNotSupportedFault(NonPersistentDisksNotSupported value) {
        return new JAXBElement<NonPersistentDisksNotSupported>(_NonPersistentDisksNotSupportedFault_QNAME, NonPersistentDisksNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NonVmwareOuiMacNotSupportedHost }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NonVmwareOuiMacNotSupportedHostFault")
    public JAXBElement<NonVmwareOuiMacNotSupportedHost> createNonVmwareOuiMacNotSupportedHostFault(NonVmwareOuiMacNotSupportedHost value) {
        return new JAXBElement<NonVmwareOuiMacNotSupportedHost>(_NonVmwareOuiMacNotSupportedHostFault_QNAME, NonVmwareOuiMacNotSupportedHost.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NotADirectory }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NotADirectoryFault")
    public JAXBElement<NotADirectory> createNotADirectoryFault(NotADirectory value) {
        return new JAXBElement<NotADirectory>(_NotADirectoryFault_QNAME, NotADirectory.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NotAFile }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NotAFileFault")
    public JAXBElement<NotAFile> createNotAFileFault(NotAFile value) {
        return new JAXBElement<NotAFile>(_NotAFileFault_QNAME, NotAFile.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NotAuthenticated }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NotAuthenticatedFault")
    public JAXBElement<NotAuthenticated> createNotAuthenticatedFault(NotAuthenticated value) {
        return new JAXBElement<NotAuthenticated>(_NotAuthenticatedFault_QNAME, NotAuthenticated.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NotEnoughCpus }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NotEnoughCpusFault")
    public JAXBElement<NotEnoughCpus> createNotEnoughCpusFault(NotEnoughCpus value) {
        return new JAXBElement<NotEnoughCpus>(_NotEnoughCpusFault_QNAME, NotEnoughCpus.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NotEnoughLogicalCpus }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NotEnoughLogicalCpusFault")
    public JAXBElement<NotEnoughLogicalCpus> createNotEnoughLogicalCpusFault(NotEnoughLogicalCpus value) {
        return new JAXBElement<NotEnoughLogicalCpus>(_NotEnoughLogicalCpusFault_QNAME, NotEnoughLogicalCpus.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NotFound }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NotFoundFault")
    public JAXBElement<NotFound> createNotFoundFault(NotFound value) {
        return new JAXBElement<NotFound>(_NotFoundFault_QNAME, NotFound.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NotSupportedDeviceForFT }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NotSupportedDeviceForFTFault")
    public JAXBElement<NotSupportedDeviceForFT> createNotSupportedDeviceForFTFault(NotSupportedDeviceForFT value) {
        return new JAXBElement<NotSupportedDeviceForFT>(_NotSupportedDeviceForFTFault_QNAME, NotSupportedDeviceForFT.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NotSupportedHost }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NotSupportedHostFault")
    public JAXBElement<NotSupportedHost> createNotSupportedHostFault(NotSupportedHost value) {
        return new JAXBElement<NotSupportedHost>(_NotSupportedHostFault_QNAME, NotSupportedHost.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NotSupportedHostForChecksum }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NotSupportedHostForChecksumFault")
    public JAXBElement<NotSupportedHostForChecksum> createNotSupportedHostForChecksumFault(NotSupportedHostForChecksum value) {
        return new JAXBElement<NotSupportedHostForChecksum>(_NotSupportedHostForChecksumFault_QNAME, NotSupportedHostForChecksum.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NotSupportedHostForVFlash }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NotSupportedHostForVFlashFault")
    public JAXBElement<NotSupportedHostForVFlash> createNotSupportedHostForVFlashFault(NotSupportedHostForVFlash value) {
        return new JAXBElement<NotSupportedHostForVFlash>(_NotSupportedHostForVFlashFault_QNAME, NotSupportedHostForVFlash.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NotSupportedHostForVmcp }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NotSupportedHostForVmcpFault")
    public JAXBElement<NotSupportedHostForVmcp> createNotSupportedHostForVmcpFault(NotSupportedHostForVmcp value) {
        return new JAXBElement<NotSupportedHostForVmcp>(_NotSupportedHostForVmcpFault_QNAME, NotSupportedHostForVmcp.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NotSupportedHostForVmemFile }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NotSupportedHostForVmemFileFault")
    public JAXBElement<NotSupportedHostForVmemFile> createNotSupportedHostForVmemFileFault(NotSupportedHostForVmemFile value) {
        return new JAXBElement<NotSupportedHostForVmemFile>(_NotSupportedHostForVmemFileFault_QNAME, NotSupportedHostForVmemFile.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NotSupportedHostForVsan }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NotSupportedHostForVsanFault")
    public JAXBElement<NotSupportedHostForVsan> createNotSupportedHostForVsanFault(NotSupportedHostForVsan value) {
        return new JAXBElement<NotSupportedHostForVsan>(_NotSupportedHostForVsanFault_QNAME, NotSupportedHostForVsan.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NotSupportedHostInCluster }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NotSupportedHostInClusterFault")
    public JAXBElement<NotSupportedHostInCluster> createNotSupportedHostInClusterFault(NotSupportedHostInCluster value) {
        return new JAXBElement<NotSupportedHostInCluster>(_NotSupportedHostInClusterFault_QNAME, NotSupportedHostInCluster.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NotSupportedHostInDvs }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NotSupportedHostInDvsFault")
    public JAXBElement<NotSupportedHostInDvs> createNotSupportedHostInDvsFault(NotSupportedHostInDvs value) {
        return new JAXBElement<NotSupportedHostInDvs>(_NotSupportedHostInDvsFault_QNAME, NotSupportedHostInDvs.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NotSupportedHostInHACluster }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NotSupportedHostInHAClusterFault")
    public JAXBElement<NotSupportedHostInHACluster> createNotSupportedHostInHAClusterFault(NotSupportedHostInHACluster value) {
        return new JAXBElement<NotSupportedHostInHACluster>(_NotSupportedHostInHAClusterFault_QNAME, NotSupportedHostInHACluster.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NotUserConfigurableProperty }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NotUserConfigurablePropertyFault")
    public JAXBElement<NotUserConfigurableProperty> createNotUserConfigurablePropertyFault(NotUserConfigurableProperty value) {
        return new JAXBElement<NotUserConfigurableProperty>(_NotUserConfigurablePropertyFault_QNAME, NotUserConfigurableProperty.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NumVirtualCoresPerSocketNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NumVirtualCoresPerSocketNotSupportedFault")
    public JAXBElement<NumVirtualCoresPerSocketNotSupported> createNumVirtualCoresPerSocketNotSupportedFault(NumVirtualCoresPerSocketNotSupported value) {
        return new JAXBElement<NumVirtualCoresPerSocketNotSupported>(_NumVirtualCoresPerSocketNotSupportedFault_QNAME, NumVirtualCoresPerSocketNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NumVirtualCpusExceedsLimit }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NumVirtualCpusExceedsLimitFault")
    public JAXBElement<NumVirtualCpusExceedsLimit> createNumVirtualCpusExceedsLimitFault(NumVirtualCpusExceedsLimit value) {
        return new JAXBElement<NumVirtualCpusExceedsLimit>(_NumVirtualCpusExceedsLimitFault_QNAME, NumVirtualCpusExceedsLimit.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NumVirtualCpusIncompatible }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NumVirtualCpusIncompatibleFault")
    public JAXBElement<NumVirtualCpusIncompatible> createNumVirtualCpusIncompatibleFault(NumVirtualCpusIncompatible value) {
        return new JAXBElement<NumVirtualCpusIncompatible>(_NumVirtualCpusIncompatibleFault_QNAME, NumVirtualCpusIncompatible.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NumVirtualCpusNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NumVirtualCpusNotSupportedFault")
    public JAXBElement<NumVirtualCpusNotSupported> createNumVirtualCpusNotSupportedFault(NumVirtualCpusNotSupported value) {
        return new JAXBElement<NumVirtualCpusNotSupported>(_NumVirtualCpusNotSupportedFault_QNAME, NumVirtualCpusNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OperationDisabledByGuest }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OperationDisabledByGuestFault")
    public JAXBElement<OperationDisabledByGuest> createOperationDisabledByGuestFault(OperationDisabledByGuest value) {
        return new JAXBElement<OperationDisabledByGuest>(_OperationDisabledByGuestFault_QNAME, OperationDisabledByGuest.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OperationDisallowedOnHost }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OperationDisallowedOnHostFault")
    public JAXBElement<OperationDisallowedOnHost> createOperationDisallowedOnHostFault(OperationDisallowedOnHost value) {
        return new JAXBElement<OperationDisallowedOnHost>(_OperationDisallowedOnHostFault_QNAME, OperationDisallowedOnHost.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OperationNotSupportedByGuest }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OperationNotSupportedByGuestFault")
    public JAXBElement<OperationNotSupportedByGuest> createOperationNotSupportedByGuestFault(OperationNotSupportedByGuest value) {
        return new JAXBElement<OperationNotSupportedByGuest>(_OperationNotSupportedByGuestFault_QNAME, OperationNotSupportedByGuest.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OutOfBounds }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OutOfBoundsFault")
    public JAXBElement<OutOfBounds> createOutOfBoundsFault(OutOfBounds value) {
        return new JAXBElement<OutOfBounds>(_OutOfBoundsFault_QNAME, OutOfBounds.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfAttribute }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfAttributeFault")
    public JAXBElement<OvfAttribute> createOvfAttributeFault(OvfAttribute value) {
        return new JAXBElement<OvfAttribute>(_OvfAttributeFault_QNAME, OvfAttribute.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfConnectedDevice }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfConnectedDeviceFault")
    public JAXBElement<OvfConnectedDevice> createOvfConnectedDeviceFault(OvfConnectedDevice value) {
        return new JAXBElement<OvfConnectedDevice>(_OvfConnectedDeviceFault_QNAME, OvfConnectedDevice.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfConnectedDeviceFloppy }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfConnectedDeviceFloppyFault")
    public JAXBElement<OvfConnectedDeviceFloppy> createOvfConnectedDeviceFloppyFault(OvfConnectedDeviceFloppy value) {
        return new JAXBElement<OvfConnectedDeviceFloppy>(_OvfConnectedDeviceFloppyFault_QNAME, OvfConnectedDeviceFloppy.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfConnectedDeviceIso }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfConnectedDeviceIsoFault")
    public JAXBElement<OvfConnectedDeviceIso> createOvfConnectedDeviceIsoFault(OvfConnectedDeviceIso value) {
        return new JAXBElement<OvfConnectedDeviceIso>(_OvfConnectedDeviceIsoFault_QNAME, OvfConnectedDeviceIso.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfConstraint }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfConstraintFault")
    public JAXBElement<OvfConstraint> createOvfConstraintFault(OvfConstraint value) {
        return new JAXBElement<OvfConstraint>(_OvfConstraintFault_QNAME, OvfConstraint.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfConsumerCallbackFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfConsumerCallbackFaultFault")
    public JAXBElement<OvfConsumerCallbackFault> createOvfConsumerCallbackFaultFault(OvfConsumerCallbackFault value) {
        return new JAXBElement<OvfConsumerCallbackFault>(_OvfConsumerCallbackFaultFault_QNAME, OvfConsumerCallbackFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfConsumerCommunicationError }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfConsumerCommunicationErrorFault")
    public JAXBElement<OvfConsumerCommunicationError> createOvfConsumerCommunicationErrorFault(OvfConsumerCommunicationError value) {
        return new JAXBElement<OvfConsumerCommunicationError>(_OvfConsumerCommunicationErrorFault_QNAME, OvfConsumerCommunicationError.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfConsumerFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfConsumerFaultFault")
    public JAXBElement<OvfConsumerFault> createOvfConsumerFaultFault(OvfConsumerFault value) {
        return new JAXBElement<OvfConsumerFault>(_OvfConsumerFaultFault_QNAME, OvfConsumerFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfConsumerInvalidSection }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfConsumerInvalidSectionFault")
    public JAXBElement<OvfConsumerInvalidSection> createOvfConsumerInvalidSectionFault(OvfConsumerInvalidSection value) {
        return new JAXBElement<OvfConsumerInvalidSection>(_OvfConsumerInvalidSectionFault_QNAME, OvfConsumerInvalidSection.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfConsumerPowerOnFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfConsumerPowerOnFaultFault")
    public JAXBElement<OvfConsumerPowerOnFault> createOvfConsumerPowerOnFaultFault(OvfConsumerPowerOnFault value) {
        return new JAXBElement<OvfConsumerPowerOnFault>(_OvfConsumerPowerOnFaultFault_QNAME, OvfConsumerPowerOnFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfConsumerUndeclaredSection }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfConsumerUndeclaredSectionFault")
    public JAXBElement<OvfConsumerUndeclaredSection> createOvfConsumerUndeclaredSectionFault(OvfConsumerUndeclaredSection value) {
        return new JAXBElement<OvfConsumerUndeclaredSection>(_OvfConsumerUndeclaredSectionFault_QNAME, OvfConsumerUndeclaredSection.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfConsumerUndefinedPrefix }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfConsumerUndefinedPrefixFault")
    public JAXBElement<OvfConsumerUndefinedPrefix> createOvfConsumerUndefinedPrefixFault(OvfConsumerUndefinedPrefix value) {
        return new JAXBElement<OvfConsumerUndefinedPrefix>(_OvfConsumerUndefinedPrefixFault_QNAME, OvfConsumerUndefinedPrefix.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfConsumerValidationFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfConsumerValidationFaultFault")
    public JAXBElement<OvfConsumerValidationFault> createOvfConsumerValidationFaultFault(OvfConsumerValidationFault value) {
        return new JAXBElement<OvfConsumerValidationFault>(_OvfConsumerValidationFaultFault_QNAME, OvfConsumerValidationFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfCpuCompatibility }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfCpuCompatibilityFault")
    public JAXBElement<OvfCpuCompatibility> createOvfCpuCompatibilityFault(OvfCpuCompatibility value) {
        return new JAXBElement<OvfCpuCompatibility>(_OvfCpuCompatibilityFault_QNAME, OvfCpuCompatibility.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfCpuCompatibilityCheckNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfCpuCompatibilityCheckNotSupportedFault")
    public JAXBElement<OvfCpuCompatibilityCheckNotSupported> createOvfCpuCompatibilityCheckNotSupportedFault(OvfCpuCompatibilityCheckNotSupported value) {
        return new JAXBElement<OvfCpuCompatibilityCheckNotSupported>(_OvfCpuCompatibilityCheckNotSupportedFault_QNAME, OvfCpuCompatibilityCheckNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfDiskMappingNotFound }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfDiskMappingNotFoundFault")
    public JAXBElement<OvfDiskMappingNotFound> createOvfDiskMappingNotFoundFault(OvfDiskMappingNotFound value) {
        return new JAXBElement<OvfDiskMappingNotFound>(_OvfDiskMappingNotFoundFault_QNAME, OvfDiskMappingNotFound.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfDiskOrderConstraint }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfDiskOrderConstraintFault")
    public JAXBElement<OvfDiskOrderConstraint> createOvfDiskOrderConstraintFault(OvfDiskOrderConstraint value) {
        return new JAXBElement<OvfDiskOrderConstraint>(_OvfDiskOrderConstraintFault_QNAME, OvfDiskOrderConstraint.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfDuplicateElement }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfDuplicateElementFault")
    public JAXBElement<OvfDuplicateElement> createOvfDuplicateElementFault(OvfDuplicateElement value) {
        return new JAXBElement<OvfDuplicateElement>(_OvfDuplicateElementFault_QNAME, OvfDuplicateElement.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfDuplicatedElementBoundary }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfDuplicatedElementBoundaryFault")
    public JAXBElement<OvfDuplicatedElementBoundary> createOvfDuplicatedElementBoundaryFault(OvfDuplicatedElementBoundary value) {
        return new JAXBElement<OvfDuplicatedElementBoundary>(_OvfDuplicatedElementBoundaryFault_QNAME, OvfDuplicatedElementBoundary.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfDuplicatedPropertyIdExport }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfDuplicatedPropertyIdExportFault")
    public JAXBElement<OvfDuplicatedPropertyIdExport> createOvfDuplicatedPropertyIdExportFault(OvfDuplicatedPropertyIdExport value) {
        return new JAXBElement<OvfDuplicatedPropertyIdExport>(_OvfDuplicatedPropertyIdExportFault_QNAME, OvfDuplicatedPropertyIdExport.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfDuplicatedPropertyIdImport }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfDuplicatedPropertyIdImportFault")
    public JAXBElement<OvfDuplicatedPropertyIdImport> createOvfDuplicatedPropertyIdImportFault(OvfDuplicatedPropertyIdImport value) {
        return new JAXBElement<OvfDuplicatedPropertyIdImport>(_OvfDuplicatedPropertyIdImportFault_QNAME, OvfDuplicatedPropertyIdImport.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfElement }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfElementFault")
    public JAXBElement<OvfElement> createOvfElementFault(OvfElement value) {
        return new JAXBElement<OvfElement>(_OvfElementFault_QNAME, OvfElement.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfElementInvalidValue }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfElementInvalidValueFault")
    public JAXBElement<OvfElementInvalidValue> createOvfElementInvalidValueFault(OvfElementInvalidValue value) {
        return new JAXBElement<OvfElementInvalidValue>(_OvfElementInvalidValueFault_QNAME, OvfElementInvalidValue.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfExport }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfExportFault")
    public JAXBElement<OvfExport> createOvfExportFault(OvfExport value) {
        return new JAXBElement<OvfExport>(_OvfExportFault_QNAME, OvfExport.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfExportFailed }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfExportFailedFault")
    public JAXBElement<OvfExportFailed> createOvfExportFailedFault(OvfExportFailed value) {
        return new JAXBElement<OvfExportFailed>(_OvfExportFailedFault_QNAME, OvfExportFailed.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfFaultFault")
    public JAXBElement<OvfFault> createOvfFaultFault(OvfFault value) {
        return new JAXBElement<OvfFault>(_OvfFaultFault_QNAME, OvfFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfHardwareCheck }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfHardwareCheckFault")
    public JAXBElement<OvfHardwareCheck> createOvfHardwareCheckFault(OvfHardwareCheck value) {
        return new JAXBElement<OvfHardwareCheck>(_OvfHardwareCheckFault_QNAME, OvfHardwareCheck.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfHardwareExport }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfHardwareExportFault")
    public JAXBElement<OvfHardwareExport> createOvfHardwareExportFault(OvfHardwareExport value) {
        return new JAXBElement<OvfHardwareExport>(_OvfHardwareExportFault_QNAME, OvfHardwareExport.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfHostResourceConstraint }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfHostResourceConstraintFault")
    public JAXBElement<OvfHostResourceConstraint> createOvfHostResourceConstraintFault(OvfHostResourceConstraint value) {
        return new JAXBElement<OvfHostResourceConstraint>(_OvfHostResourceConstraintFault_QNAME, OvfHostResourceConstraint.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfHostValueNotParsed }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfHostValueNotParsedFault")
    public JAXBElement<OvfHostValueNotParsed> createOvfHostValueNotParsedFault(OvfHostValueNotParsed value) {
        return new JAXBElement<OvfHostValueNotParsed>(_OvfHostValueNotParsedFault_QNAME, OvfHostValueNotParsed.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfImport }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfImportFault")
    public JAXBElement<OvfImport> createOvfImportFault(OvfImport value) {
        return new JAXBElement<OvfImport>(_OvfImportFault_QNAME, OvfImport.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfImportFailed }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfImportFailedFault")
    public JAXBElement<OvfImportFailed> createOvfImportFailedFault(OvfImportFailed value) {
        return new JAXBElement<OvfImportFailed>(_OvfImportFailedFault_QNAME, OvfImportFailed.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfInternalError }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfInternalErrorFault")
    public JAXBElement<OvfInternalError> createOvfInternalErrorFault(OvfInternalError value) {
        return new JAXBElement<OvfInternalError>(_OvfInternalErrorFault_QNAME, OvfInternalError.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfInvalidPackage }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfInvalidPackageFault")
    public JAXBElement<OvfInvalidPackage> createOvfInvalidPackageFault(OvfInvalidPackage value) {
        return new JAXBElement<OvfInvalidPackage>(_OvfInvalidPackageFault_QNAME, OvfInvalidPackage.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfInvalidValue }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfInvalidValueFault")
    public JAXBElement<OvfInvalidValue> createOvfInvalidValueFault(OvfInvalidValue value) {
        return new JAXBElement<OvfInvalidValue>(_OvfInvalidValueFault_QNAME, OvfInvalidValue.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfInvalidValueConfiguration }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfInvalidValueConfigurationFault")
    public JAXBElement<OvfInvalidValueConfiguration> createOvfInvalidValueConfigurationFault(OvfInvalidValueConfiguration value) {
        return new JAXBElement<OvfInvalidValueConfiguration>(_OvfInvalidValueConfigurationFault_QNAME, OvfInvalidValueConfiguration.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfInvalidValueEmpty }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfInvalidValueEmptyFault")
    public JAXBElement<OvfInvalidValueEmpty> createOvfInvalidValueEmptyFault(OvfInvalidValueEmpty value) {
        return new JAXBElement<OvfInvalidValueEmpty>(_OvfInvalidValueEmptyFault_QNAME, OvfInvalidValueEmpty.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfInvalidValueFormatMalformed }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfInvalidValueFormatMalformedFault")
    public JAXBElement<OvfInvalidValueFormatMalformed> createOvfInvalidValueFormatMalformedFault(OvfInvalidValueFormatMalformed value) {
        return new JAXBElement<OvfInvalidValueFormatMalformed>(_OvfInvalidValueFormatMalformedFault_QNAME, OvfInvalidValueFormatMalformed.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfInvalidValueReference }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfInvalidValueReferenceFault")
    public JAXBElement<OvfInvalidValueReference> createOvfInvalidValueReferenceFault(OvfInvalidValueReference value) {
        return new JAXBElement<OvfInvalidValueReference>(_OvfInvalidValueReferenceFault_QNAME, OvfInvalidValueReference.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfInvalidVmName }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfInvalidVmNameFault")
    public JAXBElement<OvfInvalidVmName> createOvfInvalidVmNameFault(OvfInvalidVmName value) {
        return new JAXBElement<OvfInvalidVmName>(_OvfInvalidVmNameFault_QNAME, OvfInvalidVmName.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfMappedOsId }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfMappedOsIdFault")
    public JAXBElement<OvfMappedOsId> createOvfMappedOsIdFault(OvfMappedOsId value) {
        return new JAXBElement<OvfMappedOsId>(_OvfMappedOsIdFault_QNAME, OvfMappedOsId.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfMissingAttribute }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfMissingAttributeFault")
    public JAXBElement<OvfMissingAttribute> createOvfMissingAttributeFault(OvfMissingAttribute value) {
        return new JAXBElement<OvfMissingAttribute>(_OvfMissingAttributeFault_QNAME, OvfMissingAttribute.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfMissingElement }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfMissingElementFault")
    public JAXBElement<OvfMissingElement> createOvfMissingElementFault(OvfMissingElement value) {
        return new JAXBElement<OvfMissingElement>(_OvfMissingElementFault_QNAME, OvfMissingElement.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfMissingElementNormalBoundary }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfMissingElementNormalBoundaryFault")
    public JAXBElement<OvfMissingElementNormalBoundary> createOvfMissingElementNormalBoundaryFault(OvfMissingElementNormalBoundary value) {
        return new JAXBElement<OvfMissingElementNormalBoundary>(_OvfMissingElementNormalBoundaryFault_QNAME, OvfMissingElementNormalBoundary.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfMissingHardware }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfMissingHardwareFault")
    public JAXBElement<OvfMissingHardware> createOvfMissingHardwareFault(OvfMissingHardware value) {
        return new JAXBElement<OvfMissingHardware>(_OvfMissingHardwareFault_QNAME, OvfMissingHardware.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfNetworkMappingNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfNetworkMappingNotSupportedFault")
    public JAXBElement<OvfNetworkMappingNotSupported> createOvfNetworkMappingNotSupportedFault(OvfNetworkMappingNotSupported value) {
        return new JAXBElement<OvfNetworkMappingNotSupported>(_OvfNetworkMappingNotSupportedFault_QNAME, OvfNetworkMappingNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfNoHostNic }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfNoHostNicFault")
    public JAXBElement<OvfNoHostNic> createOvfNoHostNicFault(OvfNoHostNic value) {
        return new JAXBElement<OvfNoHostNic>(_OvfNoHostNicFault_QNAME, OvfNoHostNic.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfNoSpaceOnController }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfNoSpaceOnControllerFault")
    public JAXBElement<OvfNoSpaceOnController> createOvfNoSpaceOnControllerFault(OvfNoSpaceOnController value) {
        return new JAXBElement<OvfNoSpaceOnController>(_OvfNoSpaceOnControllerFault_QNAME, OvfNoSpaceOnController.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfNoSupportedHardwareFamily }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfNoSupportedHardwareFamilyFault")
    public JAXBElement<OvfNoSupportedHardwareFamily> createOvfNoSupportedHardwareFamilyFault(OvfNoSupportedHardwareFamily value) {
        return new JAXBElement<OvfNoSupportedHardwareFamily>(_OvfNoSupportedHardwareFamilyFault_QNAME, OvfNoSupportedHardwareFamily.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfProperty }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfPropertyFault")
    public JAXBElement<OvfProperty> createOvfPropertyFault(OvfProperty value) {
        return new JAXBElement<OvfProperty>(_OvfPropertyFault_QNAME, OvfProperty.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfPropertyExport }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfPropertyExportFault")
    public JAXBElement<OvfPropertyExport> createOvfPropertyExportFault(OvfPropertyExport value) {
        return new JAXBElement<OvfPropertyExport>(_OvfPropertyExportFault_QNAME, OvfPropertyExport.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfPropertyNetwork }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfPropertyNetworkFault")
    public JAXBElement<OvfPropertyNetwork> createOvfPropertyNetworkFault(OvfPropertyNetwork value) {
        return new JAXBElement<OvfPropertyNetwork>(_OvfPropertyNetworkFault_QNAME, OvfPropertyNetwork.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfPropertyNetworkExport }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfPropertyNetworkExportFault")
    public JAXBElement<OvfPropertyNetworkExport> createOvfPropertyNetworkExportFault(OvfPropertyNetworkExport value) {
        return new JAXBElement<OvfPropertyNetworkExport>(_OvfPropertyNetworkExportFault_QNAME, OvfPropertyNetworkExport.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfPropertyQualifier }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfPropertyQualifierFault")
    public JAXBElement<OvfPropertyQualifier> createOvfPropertyQualifierFault(OvfPropertyQualifier value) {
        return new JAXBElement<OvfPropertyQualifier>(_OvfPropertyQualifierFault_QNAME, OvfPropertyQualifier.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfPropertyQualifierDuplicate }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfPropertyQualifierDuplicateFault")
    public JAXBElement<OvfPropertyQualifierDuplicate> createOvfPropertyQualifierDuplicateFault(OvfPropertyQualifierDuplicate value) {
        return new JAXBElement<OvfPropertyQualifierDuplicate>(_OvfPropertyQualifierDuplicateFault_QNAME, OvfPropertyQualifierDuplicate.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfPropertyQualifierIgnored }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfPropertyQualifierIgnoredFault")
    public JAXBElement<OvfPropertyQualifierIgnored> createOvfPropertyQualifierIgnoredFault(OvfPropertyQualifierIgnored value) {
        return new JAXBElement<OvfPropertyQualifierIgnored>(_OvfPropertyQualifierIgnoredFault_QNAME, OvfPropertyQualifierIgnored.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfPropertyType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfPropertyTypeFault")
    public JAXBElement<OvfPropertyType> createOvfPropertyTypeFault(OvfPropertyType value) {
        return new JAXBElement<OvfPropertyType>(_OvfPropertyTypeFault_QNAME, OvfPropertyType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfPropertyValue }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfPropertyValueFault")
    public JAXBElement<OvfPropertyValue> createOvfPropertyValueFault(OvfPropertyValue value) {
        return new JAXBElement<OvfPropertyValue>(_OvfPropertyValueFault_QNAME, OvfPropertyValue.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfSystemFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfSystemFaultFault")
    public JAXBElement<OvfSystemFault> createOvfSystemFaultFault(OvfSystemFault value) {
        return new JAXBElement<OvfSystemFault>(_OvfSystemFaultFault_QNAME, OvfSystemFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfToXmlUnsupportedElement }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfToXmlUnsupportedElementFault")
    public JAXBElement<OvfToXmlUnsupportedElement> createOvfToXmlUnsupportedElementFault(OvfToXmlUnsupportedElement value) {
        return new JAXBElement<OvfToXmlUnsupportedElement>(_OvfToXmlUnsupportedElementFault_QNAME, OvfToXmlUnsupportedElement.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfUnableToExportDisk }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfUnableToExportDiskFault")
    public JAXBElement<OvfUnableToExportDisk> createOvfUnableToExportDiskFault(OvfUnableToExportDisk value) {
        return new JAXBElement<OvfUnableToExportDisk>(_OvfUnableToExportDiskFault_QNAME, OvfUnableToExportDisk.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfUnexpectedElement }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfUnexpectedElementFault")
    public JAXBElement<OvfUnexpectedElement> createOvfUnexpectedElementFault(OvfUnexpectedElement value) {
        return new JAXBElement<OvfUnexpectedElement>(_OvfUnexpectedElementFault_QNAME, OvfUnexpectedElement.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfUnknownDevice }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfUnknownDeviceFault")
    public JAXBElement<OvfUnknownDevice> createOvfUnknownDeviceFault(OvfUnknownDevice value) {
        return new JAXBElement<OvfUnknownDevice>(_OvfUnknownDeviceFault_QNAME, OvfUnknownDevice.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfUnknownDeviceBacking }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfUnknownDeviceBackingFault")
    public JAXBElement<OvfUnknownDeviceBacking> createOvfUnknownDeviceBackingFault(OvfUnknownDeviceBacking value) {
        return new JAXBElement<OvfUnknownDeviceBacking>(_OvfUnknownDeviceBackingFault_QNAME, OvfUnknownDeviceBacking.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfUnknownEntity }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfUnknownEntityFault")
    public JAXBElement<OvfUnknownEntity> createOvfUnknownEntityFault(OvfUnknownEntity value) {
        return new JAXBElement<OvfUnknownEntity>(_OvfUnknownEntityFault_QNAME, OvfUnknownEntity.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfUnsupportedAttribute }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfUnsupportedAttributeFault")
    public JAXBElement<OvfUnsupportedAttribute> createOvfUnsupportedAttributeFault(OvfUnsupportedAttribute value) {
        return new JAXBElement<OvfUnsupportedAttribute>(_OvfUnsupportedAttributeFault_QNAME, OvfUnsupportedAttribute.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfUnsupportedAttributeValue }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfUnsupportedAttributeValueFault")
    public JAXBElement<OvfUnsupportedAttributeValue> createOvfUnsupportedAttributeValueFault(OvfUnsupportedAttributeValue value) {
        return new JAXBElement<OvfUnsupportedAttributeValue>(_OvfUnsupportedAttributeValueFault_QNAME, OvfUnsupportedAttributeValue.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfUnsupportedDeviceBackingInfo }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfUnsupportedDeviceBackingInfoFault")
    public JAXBElement<OvfUnsupportedDeviceBackingInfo> createOvfUnsupportedDeviceBackingInfoFault(OvfUnsupportedDeviceBackingInfo value) {
        return new JAXBElement<OvfUnsupportedDeviceBackingInfo>(_OvfUnsupportedDeviceBackingInfoFault_QNAME, OvfUnsupportedDeviceBackingInfo.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfUnsupportedDeviceBackingOption }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfUnsupportedDeviceBackingOptionFault")
    public JAXBElement<OvfUnsupportedDeviceBackingOption> createOvfUnsupportedDeviceBackingOptionFault(OvfUnsupportedDeviceBackingOption value) {
        return new JAXBElement<OvfUnsupportedDeviceBackingOption>(_OvfUnsupportedDeviceBackingOptionFault_QNAME, OvfUnsupportedDeviceBackingOption.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfUnsupportedDeviceExport }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfUnsupportedDeviceExportFault")
    public JAXBElement<OvfUnsupportedDeviceExport> createOvfUnsupportedDeviceExportFault(OvfUnsupportedDeviceExport value) {
        return new JAXBElement<OvfUnsupportedDeviceExport>(_OvfUnsupportedDeviceExportFault_QNAME, OvfUnsupportedDeviceExport.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfUnsupportedDiskProvisioning }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfUnsupportedDiskProvisioningFault")
    public JAXBElement<OvfUnsupportedDiskProvisioning> createOvfUnsupportedDiskProvisioningFault(OvfUnsupportedDiskProvisioning value) {
        return new JAXBElement<OvfUnsupportedDiskProvisioning>(_OvfUnsupportedDiskProvisioningFault_QNAME, OvfUnsupportedDiskProvisioning.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfUnsupportedElement }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfUnsupportedElementFault")
    public JAXBElement<OvfUnsupportedElement> createOvfUnsupportedElementFault(OvfUnsupportedElement value) {
        return new JAXBElement<OvfUnsupportedElement>(_OvfUnsupportedElementFault_QNAME, OvfUnsupportedElement.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfUnsupportedElementValue }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfUnsupportedElementValueFault")
    public JAXBElement<OvfUnsupportedElementValue> createOvfUnsupportedElementValueFault(OvfUnsupportedElementValue value) {
        return new JAXBElement<OvfUnsupportedElementValue>(_OvfUnsupportedElementValueFault_QNAME, OvfUnsupportedElementValue.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfUnsupportedPackage }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfUnsupportedPackageFault")
    public JAXBElement<OvfUnsupportedPackage> createOvfUnsupportedPackageFault(OvfUnsupportedPackage value) {
        return new JAXBElement<OvfUnsupportedPackage>(_OvfUnsupportedPackageFault_QNAME, OvfUnsupportedPackage.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfUnsupportedSection }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfUnsupportedSectionFault")
    public JAXBElement<OvfUnsupportedSection> createOvfUnsupportedSectionFault(OvfUnsupportedSection value) {
        return new JAXBElement<OvfUnsupportedSection>(_OvfUnsupportedSectionFault_QNAME, OvfUnsupportedSection.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfUnsupportedSubType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfUnsupportedSubTypeFault")
    public JAXBElement<OvfUnsupportedSubType> createOvfUnsupportedSubTypeFault(OvfUnsupportedSubType value) {
        return new JAXBElement<OvfUnsupportedSubType>(_OvfUnsupportedSubTypeFault_QNAME, OvfUnsupportedSubType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfUnsupportedType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfUnsupportedTypeFault")
    public JAXBElement<OvfUnsupportedType> createOvfUnsupportedTypeFault(OvfUnsupportedType value) {
        return new JAXBElement<OvfUnsupportedType>(_OvfUnsupportedTypeFault_QNAME, OvfUnsupportedType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfWrongElement }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfWrongElementFault")
    public JAXBElement<OvfWrongElement> createOvfWrongElementFault(OvfWrongElement value) {
        return new JAXBElement<OvfWrongElement>(_OvfWrongElementFault_QNAME, OvfWrongElement.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfWrongNamespace }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfWrongNamespaceFault")
    public JAXBElement<OvfWrongNamespace> createOvfWrongNamespaceFault(OvfWrongNamespace value) {
        return new JAXBElement<OvfWrongNamespace>(_OvfWrongNamespaceFault_QNAME, OvfWrongNamespace.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OvfXmlFormat }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "OvfXmlFormatFault")
    public JAXBElement<OvfXmlFormat> createOvfXmlFormatFault(OvfXmlFormat value) {
        return new JAXBElement<OvfXmlFormat>(_OvfXmlFormatFault_QNAME, OvfXmlFormat.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PatchAlreadyInstalled }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PatchAlreadyInstalledFault")
    public JAXBElement<PatchAlreadyInstalled> createPatchAlreadyInstalledFault(PatchAlreadyInstalled value) {
        return new JAXBElement<PatchAlreadyInstalled>(_PatchAlreadyInstalledFault_QNAME, PatchAlreadyInstalled.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PatchBinariesNotFound }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PatchBinariesNotFoundFault")
    public JAXBElement<PatchBinariesNotFound> createPatchBinariesNotFoundFault(PatchBinariesNotFound value) {
        return new JAXBElement<PatchBinariesNotFound>(_PatchBinariesNotFoundFault_QNAME, PatchBinariesNotFound.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PatchInstallFailed }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PatchInstallFailedFault")
    public JAXBElement<PatchInstallFailed> createPatchInstallFailedFault(PatchInstallFailed value) {
        return new JAXBElement<PatchInstallFailed>(_PatchInstallFailedFault_QNAME, PatchInstallFailed.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PatchIntegrityError }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PatchIntegrityErrorFault")
    public JAXBElement<PatchIntegrityError> createPatchIntegrityErrorFault(PatchIntegrityError value) {
        return new JAXBElement<PatchIntegrityError>(_PatchIntegrityErrorFault_QNAME, PatchIntegrityError.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PatchMetadataCorrupted }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PatchMetadataCorruptedFault")
    public JAXBElement<PatchMetadataCorrupted> createPatchMetadataCorruptedFault(PatchMetadataCorrupted value) {
        return new JAXBElement<PatchMetadataCorrupted>(_PatchMetadataCorruptedFault_QNAME, PatchMetadataCorrupted.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PatchMetadataInvalid }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PatchMetadataInvalidFault")
    public JAXBElement<PatchMetadataInvalid> createPatchMetadataInvalidFault(PatchMetadataInvalid value) {
        return new JAXBElement<PatchMetadataInvalid>(_PatchMetadataInvalidFault_QNAME, PatchMetadataInvalid.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PatchMetadataNotFound }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PatchMetadataNotFoundFault")
    public JAXBElement<PatchMetadataNotFound> createPatchMetadataNotFoundFault(PatchMetadataNotFound value) {
        return new JAXBElement<PatchMetadataNotFound>(_PatchMetadataNotFoundFault_QNAME, PatchMetadataNotFound.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PatchMissingDependencies }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PatchMissingDependenciesFault")
    public JAXBElement<PatchMissingDependencies> createPatchMissingDependenciesFault(PatchMissingDependencies value) {
        return new JAXBElement<PatchMissingDependencies>(_PatchMissingDependenciesFault_QNAME, PatchMissingDependencies.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PatchNotApplicable }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PatchNotApplicableFault")
    public JAXBElement<PatchNotApplicable> createPatchNotApplicableFault(PatchNotApplicable value) {
        return new JAXBElement<PatchNotApplicable>(_PatchNotApplicableFault_QNAME, PatchNotApplicable.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PatchSuperseded }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PatchSupersededFault")
    public JAXBElement<PatchSuperseded> createPatchSupersededFault(PatchSuperseded value) {
        return new JAXBElement<PatchSuperseded>(_PatchSupersededFault_QNAME, PatchSuperseded.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PhysCompatRDMNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PhysCompatRDMNotSupportedFault")
    public JAXBElement<PhysCompatRDMNotSupported> createPhysCompatRDMNotSupportedFault(PhysCompatRDMNotSupported value) {
        return new JAXBElement<PhysCompatRDMNotSupported>(_PhysCompatRDMNotSupportedFault_QNAME, PhysCompatRDMNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PlatformConfigFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PlatformConfigFaultFault")
    public JAXBElement<PlatformConfigFault> createPlatformConfigFaultFault(PlatformConfigFault value) {
        return new JAXBElement<PlatformConfigFault>(_PlatformConfigFaultFault_QNAME, PlatformConfigFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PowerOnFtSecondaryFailed }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PowerOnFtSecondaryFailedFault")
    public JAXBElement<PowerOnFtSecondaryFailed> createPowerOnFtSecondaryFailedFault(PowerOnFtSecondaryFailed value) {
        return new JAXBElement<PowerOnFtSecondaryFailed>(_PowerOnFtSecondaryFailedFault_QNAME, PowerOnFtSecondaryFailed.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PowerOnFtSecondaryTimedout }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PowerOnFtSecondaryTimedoutFault")
    public JAXBElement<PowerOnFtSecondaryTimedout> createPowerOnFtSecondaryTimedoutFault(PowerOnFtSecondaryTimedout value) {
        return new JAXBElement<PowerOnFtSecondaryTimedout>(_PowerOnFtSecondaryTimedoutFault_QNAME, PowerOnFtSecondaryTimedout.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ProfileUpdateFailed }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ProfileUpdateFailedFault")
    public JAXBElement<ProfileUpdateFailed> createProfileUpdateFailedFault(ProfileUpdateFailed value) {
        return new JAXBElement<ProfileUpdateFailed>(_ProfileUpdateFailedFault_QNAME, ProfileUpdateFailed.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link QuarantineModeFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "QuarantineModeFaultFault")
    public JAXBElement<QuarantineModeFault> createQuarantineModeFaultFault(QuarantineModeFault value) {
        return new JAXBElement<QuarantineModeFault>(_QuarantineModeFaultFault_QNAME, QuarantineModeFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link QuestionPending }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "QuestionPendingFault")
    public JAXBElement<QuestionPending> createQuestionPendingFault(QuestionPending value) {
        return new JAXBElement<QuestionPending>(_QuestionPendingFault_QNAME, QuestionPending.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link QuiesceDatastoreIOForHAFailed }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "QuiesceDatastoreIOForHAFailedFault")
    public JAXBElement<QuiesceDatastoreIOForHAFailed> createQuiesceDatastoreIOForHAFailedFault(QuiesceDatastoreIOForHAFailed value) {
        return new JAXBElement<QuiesceDatastoreIOForHAFailed>(_QuiesceDatastoreIOForHAFailedFault_QNAME, QuiesceDatastoreIOForHAFailed.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link RDMConversionNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "RDMConversionNotSupportedFault")
    public JAXBElement<RDMConversionNotSupported> createRDMConversionNotSupportedFault(RDMConversionNotSupported value) {
        return new JAXBElement<RDMConversionNotSupported>(_RDMConversionNotSupportedFault_QNAME, RDMConversionNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link RDMNotPreserved }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "RDMNotPreservedFault")
    public JAXBElement<RDMNotPreserved> createRDMNotPreservedFault(RDMNotPreserved value) {
        return new JAXBElement<RDMNotPreserved>(_RDMNotPreservedFault_QNAME, RDMNotPreserved.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link RDMNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "RDMNotSupportedFault")
    public JAXBElement<RDMNotSupported> createRDMNotSupportedFault(RDMNotSupported value) {
        return new JAXBElement<RDMNotSupported>(_RDMNotSupportedFault_QNAME, RDMNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link RDMNotSupportedOnDatastore }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "RDMNotSupportedOnDatastoreFault")
    public JAXBElement<RDMNotSupportedOnDatastore> createRDMNotSupportedOnDatastoreFault(RDMNotSupportedOnDatastore value) {
        return new JAXBElement<RDMNotSupportedOnDatastore>(_RDMNotSupportedOnDatastoreFault_QNAME, RDMNotSupportedOnDatastore.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link RDMPointsToInaccessibleDisk }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "RDMPointsToInaccessibleDiskFault")
    public JAXBElement<RDMPointsToInaccessibleDisk> createRDMPointsToInaccessibleDiskFault(RDMPointsToInaccessibleDisk value) {
        return new JAXBElement<RDMPointsToInaccessibleDisk>(_RDMPointsToInaccessibleDiskFault_QNAME, RDMPointsToInaccessibleDisk.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link RawDiskNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "RawDiskNotSupportedFault")
    public JAXBElement<RawDiskNotSupported> createRawDiskNotSupportedFault(RawDiskNotSupported value) {
        return new JAXBElement<RawDiskNotSupported>(_RawDiskNotSupportedFault_QNAME, RawDiskNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ReadHostResourcePoolTreeFailed }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ReadHostResourcePoolTreeFailedFault")
    public JAXBElement<ReadHostResourcePoolTreeFailed> createReadHostResourcePoolTreeFailedFault(ReadHostResourcePoolTreeFailed value) {
        return new JAXBElement<ReadHostResourcePoolTreeFailed>(_ReadHostResourcePoolTreeFailedFault_QNAME, ReadHostResourcePoolTreeFailed.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ReadOnlyDisksWithLegacyDestination }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ReadOnlyDisksWithLegacyDestinationFault")
    public JAXBElement<ReadOnlyDisksWithLegacyDestination> createReadOnlyDisksWithLegacyDestinationFault(ReadOnlyDisksWithLegacyDestination value) {
        return new JAXBElement<ReadOnlyDisksWithLegacyDestination>(_ReadOnlyDisksWithLegacyDestinationFault_QNAME, ReadOnlyDisksWithLegacyDestination.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link RebootRequired }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "RebootRequiredFault")
    public JAXBElement<RebootRequired> createRebootRequiredFault(RebootRequired value) {
        return new JAXBElement<RebootRequired>(_RebootRequiredFault_QNAME, RebootRequired.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link RecordReplayDisabled }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "RecordReplayDisabledFault")
    public JAXBElement<RecordReplayDisabled> createRecordReplayDisabledFault(RecordReplayDisabled value) {
        return new JAXBElement<RecordReplayDisabled>(_RecordReplayDisabledFault_QNAME, RecordReplayDisabled.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link RemoteDeviceNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "RemoteDeviceNotSupportedFault")
    public JAXBElement<RemoteDeviceNotSupported> createRemoteDeviceNotSupportedFault(RemoteDeviceNotSupported value) {
        return new JAXBElement<RemoteDeviceNotSupported>(_RemoteDeviceNotSupportedFault_QNAME, RemoteDeviceNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link RemoveFailed }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "RemoveFailedFault")
    public JAXBElement<RemoveFailed> createRemoveFailedFault(RemoveFailed value) {
        return new JAXBElement<RemoveFailed>(_RemoveFailedFault_QNAME, RemoveFailed.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ReplicationConfigFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ReplicationConfigFaultFault")
    public JAXBElement<ReplicationConfigFault> createReplicationConfigFaultFault(ReplicationConfigFault value) {
        return new JAXBElement<ReplicationConfigFault>(_ReplicationConfigFaultFault_QNAME, ReplicationConfigFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ReplicationDiskConfigFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ReplicationDiskConfigFaultFault")
    public JAXBElement<ReplicationDiskConfigFault> createReplicationDiskConfigFaultFault(ReplicationDiskConfigFault value) {
        return new JAXBElement<ReplicationDiskConfigFault>(_ReplicationDiskConfigFaultFault_QNAME, ReplicationDiskConfigFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ReplicationFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ReplicationFaultFault")
    public JAXBElement<ReplicationFault> createReplicationFaultFault(ReplicationFault value) {
        return new JAXBElement<ReplicationFault>(_ReplicationFaultFault_QNAME, ReplicationFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ReplicationIncompatibleWithFT }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ReplicationIncompatibleWithFTFault")
    public JAXBElement<ReplicationIncompatibleWithFT> createReplicationIncompatibleWithFTFault(ReplicationIncompatibleWithFT value) {
        return new JAXBElement<ReplicationIncompatibleWithFT>(_ReplicationIncompatibleWithFTFault_QNAME, ReplicationIncompatibleWithFT.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ReplicationInvalidOptions }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ReplicationInvalidOptionsFault")
    public JAXBElement<ReplicationInvalidOptions> createReplicationInvalidOptionsFault(ReplicationInvalidOptions value) {
        return new JAXBElement<ReplicationInvalidOptions>(_ReplicationInvalidOptionsFault_QNAME, ReplicationInvalidOptions.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ReplicationNotSupportedOnHost }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ReplicationNotSupportedOnHostFault")
    public JAXBElement<ReplicationNotSupportedOnHost> createReplicationNotSupportedOnHostFault(ReplicationNotSupportedOnHost value) {
        return new JAXBElement<ReplicationNotSupportedOnHost>(_ReplicationNotSupportedOnHostFault_QNAME, ReplicationNotSupportedOnHost.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ReplicationVmConfigFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ReplicationVmConfigFaultFault")
    public JAXBElement<ReplicationVmConfigFault> createReplicationVmConfigFaultFault(ReplicationVmConfigFault value) {
        return new JAXBElement<ReplicationVmConfigFault>(_ReplicationVmConfigFaultFault_QNAME, ReplicationVmConfigFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ReplicationVmFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ReplicationVmFaultFault")
    public JAXBElement<ReplicationVmFault> createReplicationVmFaultFault(ReplicationVmFault value) {
        return new JAXBElement<ReplicationVmFault>(_ReplicationVmFaultFault_QNAME, ReplicationVmFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ReplicationVmInProgressFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ReplicationVmInProgressFaultFault")
    public JAXBElement<ReplicationVmInProgressFault> createReplicationVmInProgressFaultFault(ReplicationVmInProgressFault value) {
        return new JAXBElement<ReplicationVmInProgressFault>(_ReplicationVmInProgressFaultFault_QNAME, ReplicationVmInProgressFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ResourceInUse }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ResourceInUseFault")
    public JAXBElement<ResourceInUse> createResourceInUseFault(ResourceInUse value) {
        return new JAXBElement<ResourceInUse>(_ResourceInUseFault_QNAME, ResourceInUse.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ResourceNotAvailable }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ResourceNotAvailableFault")
    public JAXBElement<ResourceNotAvailable> createResourceNotAvailableFault(ResourceNotAvailable value) {
        return new JAXBElement<ResourceNotAvailable>(_ResourceNotAvailableFault_QNAME, ResourceNotAvailable.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link RestrictedByAdministrator }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "RestrictedByAdministratorFault")
    public JAXBElement<RestrictedByAdministrator> createRestrictedByAdministratorFault(RestrictedByAdministrator value) {
        return new JAXBElement<RestrictedByAdministrator>(_RestrictedByAdministratorFault_QNAME, RestrictedByAdministrator.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link RestrictedVersion }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "RestrictedVersionFault")
    public JAXBElement<RestrictedVersion> createRestrictedVersionFault(RestrictedVersion value) {
        return new JAXBElement<RestrictedVersion>(_RestrictedVersionFault_QNAME, RestrictedVersion.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link RollbackFailure }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "RollbackFailureFault")
    public JAXBElement<RollbackFailure> createRollbackFailureFault(RollbackFailure value) {
        return new JAXBElement<RollbackFailure>(_RollbackFailureFault_QNAME, RollbackFailure.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link RuleViolation }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "RuleViolationFault")
    public JAXBElement<RuleViolation> createRuleViolationFault(RuleViolation value) {
        return new JAXBElement<RuleViolation>(_RuleViolationFault_QNAME, RuleViolation.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SSLDisabledFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "SSLDisabledFaultFault")
    public JAXBElement<SSLDisabledFault> createSSLDisabledFaultFault(SSLDisabledFault value) {
        return new JAXBElement<SSLDisabledFault>(_SSLDisabledFaultFault_QNAME, SSLDisabledFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SSLVerifyFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "SSLVerifyFaultFault")
    public JAXBElement<SSLVerifyFault> createSSLVerifyFaultFault(SSLVerifyFault value) {
        return new JAXBElement<SSLVerifyFault>(_SSLVerifyFaultFault_QNAME, SSLVerifyFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SSPIChallenge }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "SSPIChallengeFault")
    public JAXBElement<SSPIChallenge> createSSPIChallengeFault(SSPIChallenge value) {
        return new JAXBElement<SSPIChallenge>(_SSPIChallengeFault_QNAME, SSPIChallenge.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SecondaryVmAlreadyDisabled }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "SecondaryVmAlreadyDisabledFault")
    public JAXBElement<SecondaryVmAlreadyDisabled> createSecondaryVmAlreadyDisabledFault(SecondaryVmAlreadyDisabled value) {
        return new JAXBElement<SecondaryVmAlreadyDisabled>(_SecondaryVmAlreadyDisabledFault_QNAME, SecondaryVmAlreadyDisabled.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SecondaryVmAlreadyEnabled }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "SecondaryVmAlreadyEnabledFault")
    public JAXBElement<SecondaryVmAlreadyEnabled> createSecondaryVmAlreadyEnabledFault(SecondaryVmAlreadyEnabled value) {
        return new JAXBElement<SecondaryVmAlreadyEnabled>(_SecondaryVmAlreadyEnabledFault_QNAME, SecondaryVmAlreadyEnabled.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SecondaryVmAlreadyRegistered }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "SecondaryVmAlreadyRegisteredFault")
    public JAXBElement<SecondaryVmAlreadyRegistered> createSecondaryVmAlreadyRegisteredFault(SecondaryVmAlreadyRegistered value) {
        return new JAXBElement<SecondaryVmAlreadyRegistered>(_SecondaryVmAlreadyRegisteredFault_QNAME, SecondaryVmAlreadyRegistered.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SecondaryVmNotRegistered }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "SecondaryVmNotRegisteredFault")
    public JAXBElement<SecondaryVmNotRegistered> createSecondaryVmNotRegisteredFault(SecondaryVmNotRegistered value) {
        return new JAXBElement<SecondaryVmNotRegistered>(_SecondaryVmNotRegisteredFault_QNAME, SecondaryVmNotRegistered.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SharedBusControllerNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "SharedBusControllerNotSupportedFault")
    public JAXBElement<SharedBusControllerNotSupported> createSharedBusControllerNotSupportedFault(SharedBusControllerNotSupported value) {
        return new JAXBElement<SharedBusControllerNotSupported>(_SharedBusControllerNotSupportedFault_QNAME, SharedBusControllerNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ShrinkDiskFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ShrinkDiskFaultFault")
    public JAXBElement<ShrinkDiskFault> createShrinkDiskFaultFault(ShrinkDiskFault value) {
        return new JAXBElement<ShrinkDiskFault>(_ShrinkDiskFaultFault_QNAME, ShrinkDiskFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SnapshotCloneNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "SnapshotCloneNotSupportedFault")
    public JAXBElement<SnapshotCloneNotSupported> createSnapshotCloneNotSupportedFault(SnapshotCloneNotSupported value) {
        return new JAXBElement<SnapshotCloneNotSupported>(_SnapshotCloneNotSupportedFault_QNAME, SnapshotCloneNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SnapshotCopyNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "SnapshotCopyNotSupportedFault")
    public JAXBElement<SnapshotCopyNotSupported> createSnapshotCopyNotSupportedFault(SnapshotCopyNotSupported value) {
        return new JAXBElement<SnapshotCopyNotSupported>(_SnapshotCopyNotSupportedFault_QNAME, SnapshotCopyNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SnapshotDisabled }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "SnapshotDisabledFault")
    public JAXBElement<SnapshotDisabled> createSnapshotDisabledFault(SnapshotDisabled value) {
        return new JAXBElement<SnapshotDisabled>(_SnapshotDisabledFault_QNAME, SnapshotDisabled.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SnapshotFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "SnapshotFaultFault")
    public JAXBElement<SnapshotFault> createSnapshotFaultFault(SnapshotFault value) {
        return new JAXBElement<SnapshotFault>(_SnapshotFaultFault_QNAME, SnapshotFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SnapshotIncompatibleDeviceInVm }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "SnapshotIncompatibleDeviceInVmFault")
    public JAXBElement<SnapshotIncompatibleDeviceInVm> createSnapshotIncompatibleDeviceInVmFault(SnapshotIncompatibleDeviceInVm value) {
        return new JAXBElement<SnapshotIncompatibleDeviceInVm>(_SnapshotIncompatibleDeviceInVmFault_QNAME, SnapshotIncompatibleDeviceInVm.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SnapshotLocked }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "SnapshotLockedFault")
    public JAXBElement<SnapshotLocked> createSnapshotLockedFault(SnapshotLocked value) {
        return new JAXBElement<SnapshotLocked>(_SnapshotLockedFault_QNAME, SnapshotLocked.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SnapshotMoveFromNonHomeNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "SnapshotMoveFromNonHomeNotSupportedFault")
    public JAXBElement<SnapshotMoveFromNonHomeNotSupported> createSnapshotMoveFromNonHomeNotSupportedFault(SnapshotMoveFromNonHomeNotSupported value) {
        return new JAXBElement<SnapshotMoveFromNonHomeNotSupported>(_SnapshotMoveFromNonHomeNotSupportedFault_QNAME, SnapshotMoveFromNonHomeNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SnapshotMoveNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "SnapshotMoveNotSupportedFault")
    public JAXBElement<SnapshotMoveNotSupported> createSnapshotMoveNotSupportedFault(SnapshotMoveNotSupported value) {
        return new JAXBElement<SnapshotMoveNotSupported>(_SnapshotMoveNotSupportedFault_QNAME, SnapshotMoveNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SnapshotMoveToNonHomeNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "SnapshotMoveToNonHomeNotSupportedFault")
    public JAXBElement<SnapshotMoveToNonHomeNotSupported> createSnapshotMoveToNonHomeNotSupportedFault(SnapshotMoveToNonHomeNotSupported value) {
        return new JAXBElement<SnapshotMoveToNonHomeNotSupported>(_SnapshotMoveToNonHomeNotSupportedFault_QNAME, SnapshotMoveToNonHomeNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SnapshotNoChange }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "SnapshotNoChangeFault")
    public JAXBElement<SnapshotNoChange> createSnapshotNoChangeFault(SnapshotNoChange value) {
        return new JAXBElement<SnapshotNoChange>(_SnapshotNoChangeFault_QNAME, SnapshotNoChange.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SnapshotRevertIssue }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "SnapshotRevertIssueFault")
    public JAXBElement<SnapshotRevertIssue> createSnapshotRevertIssueFault(SnapshotRevertIssue value) {
        return new JAXBElement<SnapshotRevertIssue>(_SnapshotRevertIssueFault_QNAME, SnapshotRevertIssue.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SoftRuleVioCorrectionDisallowed }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "SoftRuleVioCorrectionDisallowedFault")
    public JAXBElement<SoftRuleVioCorrectionDisallowed> createSoftRuleVioCorrectionDisallowedFault(SoftRuleVioCorrectionDisallowed value) {
        return new JAXBElement<SoftRuleVioCorrectionDisallowed>(_SoftRuleVioCorrectionDisallowedFault_QNAME, SoftRuleVioCorrectionDisallowed.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SoftRuleVioCorrectionImpact }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "SoftRuleVioCorrectionImpactFault")
    public JAXBElement<SoftRuleVioCorrectionImpact> createSoftRuleVioCorrectionImpactFault(SoftRuleVioCorrectionImpact value) {
        return new JAXBElement<SoftRuleVioCorrectionImpact>(_SoftRuleVioCorrectionImpactFault_QNAME, SoftRuleVioCorrectionImpact.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SsdDiskNotAvailable }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "SsdDiskNotAvailableFault")
    public JAXBElement<SsdDiskNotAvailable> createSsdDiskNotAvailableFault(SsdDiskNotAvailable value) {
        return new JAXBElement<SsdDiskNotAvailable>(_SsdDiskNotAvailableFault_QNAME, SsdDiskNotAvailable.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StorageDrsCannotMoveDiskInMultiWriterMode }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "StorageDrsCannotMoveDiskInMultiWriterModeFault")
    public JAXBElement<StorageDrsCannotMoveDiskInMultiWriterMode> createStorageDrsCannotMoveDiskInMultiWriterModeFault(StorageDrsCannotMoveDiskInMultiWriterMode value) {
        return new JAXBElement<StorageDrsCannotMoveDiskInMultiWriterMode>(_StorageDrsCannotMoveDiskInMultiWriterModeFault_QNAME, StorageDrsCannotMoveDiskInMultiWriterMode.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StorageDrsCannotMoveFTVm }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "StorageDrsCannotMoveFTVmFault")
    public JAXBElement<StorageDrsCannotMoveFTVm> createStorageDrsCannotMoveFTVmFault(StorageDrsCannotMoveFTVm value) {
        return new JAXBElement<StorageDrsCannotMoveFTVm>(_StorageDrsCannotMoveFTVmFault_QNAME, StorageDrsCannotMoveFTVm.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StorageDrsCannotMoveIndependentDisk }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "StorageDrsCannotMoveIndependentDiskFault")
    public JAXBElement<StorageDrsCannotMoveIndependentDisk> createStorageDrsCannotMoveIndependentDiskFault(StorageDrsCannotMoveIndependentDisk value) {
        return new JAXBElement<StorageDrsCannotMoveIndependentDisk>(_StorageDrsCannotMoveIndependentDiskFault_QNAME, StorageDrsCannotMoveIndependentDisk.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StorageDrsCannotMoveManuallyPlacedSwapFile }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "StorageDrsCannotMoveManuallyPlacedSwapFileFault")
    public JAXBElement<StorageDrsCannotMoveManuallyPlacedSwapFile> createStorageDrsCannotMoveManuallyPlacedSwapFileFault(StorageDrsCannotMoveManuallyPlacedSwapFile value) {
        return new JAXBElement<StorageDrsCannotMoveManuallyPlacedSwapFile>(_StorageDrsCannotMoveManuallyPlacedSwapFileFault_QNAME, StorageDrsCannotMoveManuallyPlacedSwapFile.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StorageDrsCannotMoveManuallyPlacedVm }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "StorageDrsCannotMoveManuallyPlacedVmFault")
    public JAXBElement<StorageDrsCannotMoveManuallyPlacedVm> createStorageDrsCannotMoveManuallyPlacedVmFault(StorageDrsCannotMoveManuallyPlacedVm value) {
        return new JAXBElement<StorageDrsCannotMoveManuallyPlacedVm>(_StorageDrsCannotMoveManuallyPlacedVmFault_QNAME, StorageDrsCannotMoveManuallyPlacedVm.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StorageDrsCannotMoveSharedDisk }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "StorageDrsCannotMoveSharedDiskFault")
    public JAXBElement<StorageDrsCannotMoveSharedDisk> createStorageDrsCannotMoveSharedDiskFault(StorageDrsCannotMoveSharedDisk value) {
        return new JAXBElement<StorageDrsCannotMoveSharedDisk>(_StorageDrsCannotMoveSharedDiskFault_QNAME, StorageDrsCannotMoveSharedDisk.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StorageDrsCannotMoveTemplate }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "StorageDrsCannotMoveTemplateFault")
    public JAXBElement<StorageDrsCannotMoveTemplate> createStorageDrsCannotMoveTemplateFault(StorageDrsCannotMoveTemplate value) {
        return new JAXBElement<StorageDrsCannotMoveTemplate>(_StorageDrsCannotMoveTemplateFault_QNAME, StorageDrsCannotMoveTemplate.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StorageDrsCannotMoveVmInUserFolder }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "StorageDrsCannotMoveVmInUserFolderFault")
    public JAXBElement<StorageDrsCannotMoveVmInUserFolder> createStorageDrsCannotMoveVmInUserFolderFault(StorageDrsCannotMoveVmInUserFolder value) {
        return new JAXBElement<StorageDrsCannotMoveVmInUserFolder>(_StorageDrsCannotMoveVmInUserFolderFault_QNAME, StorageDrsCannotMoveVmInUserFolder.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StorageDrsCannotMoveVmWithMountedCDROM }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "StorageDrsCannotMoveVmWithMountedCDROMFault")
    public JAXBElement<StorageDrsCannotMoveVmWithMountedCDROM> createStorageDrsCannotMoveVmWithMountedCDROMFault(StorageDrsCannotMoveVmWithMountedCDROM value) {
        return new JAXBElement<StorageDrsCannotMoveVmWithMountedCDROM>(_StorageDrsCannotMoveVmWithMountedCDROMFault_QNAME, StorageDrsCannotMoveVmWithMountedCDROM.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StorageDrsCannotMoveVmWithNoFilesInLayout }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "StorageDrsCannotMoveVmWithNoFilesInLayoutFault")
    public JAXBElement<StorageDrsCannotMoveVmWithNoFilesInLayout> createStorageDrsCannotMoveVmWithNoFilesInLayoutFault(StorageDrsCannotMoveVmWithNoFilesInLayout value) {
        return new JAXBElement<StorageDrsCannotMoveVmWithNoFilesInLayout>(_StorageDrsCannotMoveVmWithNoFilesInLayoutFault_QNAME, StorageDrsCannotMoveVmWithNoFilesInLayout.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StorageDrsDatacentersCannotShareDatastore }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "StorageDrsDatacentersCannotShareDatastoreFault")
    public JAXBElement<StorageDrsDatacentersCannotShareDatastore> createStorageDrsDatacentersCannotShareDatastoreFault(StorageDrsDatacentersCannotShareDatastore value) {
        return new JAXBElement<StorageDrsDatacentersCannotShareDatastore>(_StorageDrsDatacentersCannotShareDatastoreFault_QNAME, StorageDrsDatacentersCannotShareDatastore.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StorageDrsDisabledOnVm }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "StorageDrsDisabledOnVmFault")
    public JAXBElement<StorageDrsDisabledOnVm> createStorageDrsDisabledOnVmFault(StorageDrsDisabledOnVm value) {
        return new JAXBElement<StorageDrsDisabledOnVm>(_StorageDrsDisabledOnVmFault_QNAME, StorageDrsDisabledOnVm.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StorageDrsHbrDiskNotMovable }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "StorageDrsHbrDiskNotMovableFault")
    public JAXBElement<StorageDrsHbrDiskNotMovable> createStorageDrsHbrDiskNotMovableFault(StorageDrsHbrDiskNotMovable value) {
        return new JAXBElement<StorageDrsHbrDiskNotMovable>(_StorageDrsHbrDiskNotMovableFault_QNAME, StorageDrsHbrDiskNotMovable.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StorageDrsHmsMoveInProgress }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "StorageDrsHmsMoveInProgressFault")
    public JAXBElement<StorageDrsHmsMoveInProgress> createStorageDrsHmsMoveInProgressFault(StorageDrsHmsMoveInProgress value) {
        return new JAXBElement<StorageDrsHmsMoveInProgress>(_StorageDrsHmsMoveInProgressFault_QNAME, StorageDrsHmsMoveInProgress.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StorageDrsHmsUnreachable }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "StorageDrsHmsUnreachableFault")
    public JAXBElement<StorageDrsHmsUnreachable> createStorageDrsHmsUnreachableFault(StorageDrsHmsUnreachable value) {
        return new JAXBElement<StorageDrsHmsUnreachable>(_StorageDrsHmsUnreachableFault_QNAME, StorageDrsHmsUnreachable.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StorageDrsIolbDisabledInternally }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "StorageDrsIolbDisabledInternallyFault")
    public JAXBElement<StorageDrsIolbDisabledInternally> createStorageDrsIolbDisabledInternallyFault(StorageDrsIolbDisabledInternally value) {
        return new JAXBElement<StorageDrsIolbDisabledInternally>(_StorageDrsIolbDisabledInternallyFault_QNAME, StorageDrsIolbDisabledInternally.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StorageDrsRelocateDisabled }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "StorageDrsRelocateDisabledFault")
    public JAXBElement<StorageDrsRelocateDisabled> createStorageDrsRelocateDisabledFault(StorageDrsRelocateDisabled value) {
        return new JAXBElement<StorageDrsRelocateDisabled>(_StorageDrsRelocateDisabledFault_QNAME, StorageDrsRelocateDisabled.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StorageDrsStaleHmsCollection }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "StorageDrsStaleHmsCollectionFault")
    public JAXBElement<StorageDrsStaleHmsCollection> createStorageDrsStaleHmsCollectionFault(StorageDrsStaleHmsCollection value) {
        return new JAXBElement<StorageDrsStaleHmsCollection>(_StorageDrsStaleHmsCollectionFault_QNAME, StorageDrsStaleHmsCollection.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StorageDrsUnableToMoveFiles }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "StorageDrsUnableToMoveFilesFault")
    public JAXBElement<StorageDrsUnableToMoveFiles> createStorageDrsUnableToMoveFilesFault(StorageDrsUnableToMoveFiles value) {
        return new JAXBElement<StorageDrsUnableToMoveFiles>(_StorageDrsUnableToMoveFilesFault_QNAME, StorageDrsUnableToMoveFiles.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StorageVMotionNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "StorageVMotionNotSupportedFault")
    public JAXBElement<StorageVMotionNotSupported> createStorageVMotionNotSupportedFault(StorageVMotionNotSupported value) {
        return new JAXBElement<StorageVMotionNotSupported>(_StorageVMotionNotSupportedFault_QNAME, StorageVMotionNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StorageVmotionIncompatible }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "StorageVmotionIncompatibleFault")
    public JAXBElement<StorageVmotionIncompatible> createStorageVmotionIncompatibleFault(StorageVmotionIncompatible value) {
        return new JAXBElement<StorageVmotionIncompatible>(_StorageVmotionIncompatibleFault_QNAME, StorageVmotionIncompatible.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SuspendedRelocateNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "SuspendedRelocateNotSupportedFault")
    public JAXBElement<SuspendedRelocateNotSupported> createSuspendedRelocateNotSupportedFault(SuspendedRelocateNotSupported value) {
        return new JAXBElement<SuspendedRelocateNotSupported>(_SuspendedRelocateNotSupportedFault_QNAME, SuspendedRelocateNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SwapDatastoreNotWritableOnHost }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "SwapDatastoreNotWritableOnHostFault")
    public JAXBElement<SwapDatastoreNotWritableOnHost> createSwapDatastoreNotWritableOnHostFault(SwapDatastoreNotWritableOnHost value) {
        return new JAXBElement<SwapDatastoreNotWritableOnHost>(_SwapDatastoreNotWritableOnHostFault_QNAME, SwapDatastoreNotWritableOnHost.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SwapDatastoreUnset }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "SwapDatastoreUnsetFault")
    public JAXBElement<SwapDatastoreUnset> createSwapDatastoreUnsetFault(SwapDatastoreUnset value) {
        return new JAXBElement<SwapDatastoreUnset>(_SwapDatastoreUnsetFault_QNAME, SwapDatastoreUnset.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SwapPlacementOverrideNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "SwapPlacementOverrideNotSupportedFault")
    public JAXBElement<SwapPlacementOverrideNotSupported> createSwapPlacementOverrideNotSupportedFault(SwapPlacementOverrideNotSupported value) {
        return new JAXBElement<SwapPlacementOverrideNotSupported>(_SwapPlacementOverrideNotSupportedFault_QNAME, SwapPlacementOverrideNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SwitchIpUnset }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "SwitchIpUnsetFault")
    public JAXBElement<SwitchIpUnset> createSwitchIpUnsetFault(SwitchIpUnset value) {
        return new JAXBElement<SwitchIpUnset>(_SwitchIpUnsetFault_QNAME, SwitchIpUnset.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SwitchNotInUpgradeMode }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "SwitchNotInUpgradeModeFault")
    public JAXBElement<SwitchNotInUpgradeMode> createSwitchNotInUpgradeModeFault(SwitchNotInUpgradeMode value) {
        return new JAXBElement<SwitchNotInUpgradeMode>(_SwitchNotInUpgradeModeFault_QNAME, SwitchNotInUpgradeMode.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link TaskInProgress }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "TaskInProgressFault")
    public JAXBElement<TaskInProgress> createTaskInProgressFault(TaskInProgress value) {
        return new JAXBElement<TaskInProgress>(_TaskInProgressFault_QNAME, TaskInProgress.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ThirdPartyLicenseAssignmentFailed }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ThirdPartyLicenseAssignmentFailedFault")
    public JAXBElement<ThirdPartyLicenseAssignmentFailed> createThirdPartyLicenseAssignmentFailedFault(ThirdPartyLicenseAssignmentFailed value) {
        return new JAXBElement<ThirdPartyLicenseAssignmentFailed>(_ThirdPartyLicenseAssignmentFailedFault_QNAME, ThirdPartyLicenseAssignmentFailed.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Timedout }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "TimedoutFault")
    public JAXBElement<Timedout> createTimedoutFault(Timedout value) {
        return new JAXBElement<Timedout>(_TimedoutFault_QNAME, Timedout.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link TooManyConcurrentNativeClones }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "TooManyConcurrentNativeClonesFault")
    public JAXBElement<TooManyConcurrentNativeClones> createTooManyConcurrentNativeClonesFault(TooManyConcurrentNativeClones value) {
        return new JAXBElement<TooManyConcurrentNativeClones>(_TooManyConcurrentNativeClonesFault_QNAME, TooManyConcurrentNativeClones.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link TooManyConsecutiveOverrides }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "TooManyConsecutiveOverridesFault")
    public JAXBElement<TooManyConsecutiveOverrides> createTooManyConsecutiveOverridesFault(TooManyConsecutiveOverrides value) {
        return new JAXBElement<TooManyConsecutiveOverrides>(_TooManyConsecutiveOverridesFault_QNAME, TooManyConsecutiveOverrides.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link TooManyDevices }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "TooManyDevicesFault")
    public JAXBElement<TooManyDevices> createTooManyDevicesFault(TooManyDevices value) {
        return new JAXBElement<TooManyDevices>(_TooManyDevicesFault_QNAME, TooManyDevices.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link TooManyDisksOnLegacyHost }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "TooManyDisksOnLegacyHostFault")
    public JAXBElement<TooManyDisksOnLegacyHost> createTooManyDisksOnLegacyHostFault(TooManyDisksOnLegacyHost value) {
        return new JAXBElement<TooManyDisksOnLegacyHost>(_TooManyDisksOnLegacyHostFault_QNAME, TooManyDisksOnLegacyHost.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link TooManyGuestLogons }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "TooManyGuestLogonsFault")
    public JAXBElement<TooManyGuestLogons> createTooManyGuestLogonsFault(TooManyGuestLogons value) {
        return new JAXBElement<TooManyGuestLogons>(_TooManyGuestLogonsFault_QNAME, TooManyGuestLogons.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link TooManyHosts }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "TooManyHostsFault")
    public JAXBElement<TooManyHosts> createTooManyHostsFault(TooManyHosts value) {
        return new JAXBElement<TooManyHosts>(_TooManyHostsFault_QNAME, TooManyHosts.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link TooManyNativeCloneLevels }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "TooManyNativeCloneLevelsFault")
    public JAXBElement<TooManyNativeCloneLevels> createTooManyNativeCloneLevelsFault(TooManyNativeCloneLevels value) {
        return new JAXBElement<TooManyNativeCloneLevels>(_TooManyNativeCloneLevelsFault_QNAME, TooManyNativeCloneLevels.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link TooManyNativeClonesOnFile }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "TooManyNativeClonesOnFileFault")
    public JAXBElement<TooManyNativeClonesOnFile> createTooManyNativeClonesOnFileFault(TooManyNativeClonesOnFile value) {
        return new JAXBElement<TooManyNativeClonesOnFile>(_TooManyNativeClonesOnFileFault_QNAME, TooManyNativeClonesOnFile.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link TooManySnapshotLevels }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "TooManySnapshotLevelsFault")
    public JAXBElement<TooManySnapshotLevels> createTooManySnapshotLevelsFault(TooManySnapshotLevels value) {
        return new JAXBElement<TooManySnapshotLevels>(_TooManySnapshotLevelsFault_QNAME, TooManySnapshotLevels.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ToolsAlreadyUpgraded }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ToolsAlreadyUpgradedFault")
    public JAXBElement<ToolsAlreadyUpgraded> createToolsAlreadyUpgradedFault(ToolsAlreadyUpgraded value) {
        return new JAXBElement<ToolsAlreadyUpgraded>(_ToolsAlreadyUpgradedFault_QNAME, ToolsAlreadyUpgraded.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ToolsAutoUpgradeNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ToolsAutoUpgradeNotSupportedFault")
    public JAXBElement<ToolsAutoUpgradeNotSupported> createToolsAutoUpgradeNotSupportedFault(ToolsAutoUpgradeNotSupported value) {
        return new JAXBElement<ToolsAutoUpgradeNotSupported>(_ToolsAutoUpgradeNotSupportedFault_QNAME, ToolsAutoUpgradeNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ToolsImageCopyFailed }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ToolsImageCopyFailedFault")
    public JAXBElement<ToolsImageCopyFailed> createToolsImageCopyFailedFault(ToolsImageCopyFailed value) {
        return new JAXBElement<ToolsImageCopyFailed>(_ToolsImageCopyFailedFault_QNAME, ToolsImageCopyFailed.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ToolsImageNotAvailable }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ToolsImageNotAvailableFault")
    public JAXBElement<ToolsImageNotAvailable> createToolsImageNotAvailableFault(ToolsImageNotAvailable value) {
        return new JAXBElement<ToolsImageNotAvailable>(_ToolsImageNotAvailableFault_QNAME, ToolsImageNotAvailable.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ToolsImageSignatureCheckFailed }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ToolsImageSignatureCheckFailedFault")
    public JAXBElement<ToolsImageSignatureCheckFailed> createToolsImageSignatureCheckFailedFault(ToolsImageSignatureCheckFailed value) {
        return new JAXBElement<ToolsImageSignatureCheckFailed>(_ToolsImageSignatureCheckFailedFault_QNAME, ToolsImageSignatureCheckFailed.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ToolsInstallationInProgress }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ToolsInstallationInProgressFault")
    public JAXBElement<ToolsInstallationInProgress> createToolsInstallationInProgressFault(ToolsInstallationInProgress value) {
        return new JAXBElement<ToolsInstallationInProgress>(_ToolsInstallationInProgressFault_QNAME, ToolsInstallationInProgress.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ToolsUnavailable }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ToolsUnavailableFault")
    public JAXBElement<ToolsUnavailable> createToolsUnavailableFault(ToolsUnavailable value) {
        return new JAXBElement<ToolsUnavailable>(_ToolsUnavailableFault_QNAME, ToolsUnavailable.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ToolsUpgradeCancelled }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ToolsUpgradeCancelledFault")
    public JAXBElement<ToolsUpgradeCancelled> createToolsUpgradeCancelledFault(ToolsUpgradeCancelled value) {
        return new JAXBElement<ToolsUpgradeCancelled>(_ToolsUpgradeCancelledFault_QNAME, ToolsUpgradeCancelled.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link UnSupportedDatastoreForVFlash }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "UnSupportedDatastoreForVFlashFault")
    public JAXBElement<UnSupportedDatastoreForVFlash> createUnSupportedDatastoreForVFlashFault(UnSupportedDatastoreForVFlash value) {
        return new JAXBElement<UnSupportedDatastoreForVFlash>(_UnSupportedDatastoreForVFlashFault_QNAME, UnSupportedDatastoreForVFlash.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link UncommittedUndoableDisk }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "UncommittedUndoableDiskFault")
    public JAXBElement<UncommittedUndoableDisk> createUncommittedUndoableDiskFault(UncommittedUndoableDisk value) {
        return new JAXBElement<UncommittedUndoableDisk>(_UncommittedUndoableDiskFault_QNAME, UncommittedUndoableDisk.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link UnconfiguredPropertyValue }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "UnconfiguredPropertyValueFault")
    public JAXBElement<UnconfiguredPropertyValue> createUnconfiguredPropertyValueFault(UnconfiguredPropertyValue value) {
        return new JAXBElement<UnconfiguredPropertyValue>(_UnconfiguredPropertyValueFault_QNAME, UnconfiguredPropertyValue.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link UncustomizableGuest }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "UncustomizableGuestFault")
    public JAXBElement<UncustomizableGuest> createUncustomizableGuestFault(UncustomizableGuest value) {
        return new JAXBElement<UncustomizableGuest>(_UncustomizableGuestFault_QNAME, UncustomizableGuest.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link UnexpectedCustomizationFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "UnexpectedCustomizationFaultFault")
    public JAXBElement<UnexpectedCustomizationFault> createUnexpectedCustomizationFaultFault(UnexpectedCustomizationFault value) {
        return new JAXBElement<UnexpectedCustomizationFault>(_UnexpectedCustomizationFaultFault_QNAME, UnexpectedCustomizationFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link UnrecognizedHost }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "UnrecognizedHostFault")
    public JAXBElement<UnrecognizedHost> createUnrecognizedHostFault(UnrecognizedHost value) {
        return new JAXBElement<UnrecognizedHost>(_UnrecognizedHostFault_QNAME, UnrecognizedHost.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link UnsharedSwapVMotionNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "UnsharedSwapVMotionNotSupportedFault")
    public JAXBElement<UnsharedSwapVMotionNotSupported> createUnsharedSwapVMotionNotSupportedFault(UnsharedSwapVMotionNotSupported value) {
        return new JAXBElement<UnsharedSwapVMotionNotSupported>(_UnsharedSwapVMotionNotSupportedFault_QNAME, UnsharedSwapVMotionNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link UnsupportedDatastore }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "UnsupportedDatastoreFault")
    public JAXBElement<UnsupportedDatastore> createUnsupportedDatastoreFault(UnsupportedDatastore value) {
        return new JAXBElement<UnsupportedDatastore>(_UnsupportedDatastoreFault_QNAME, UnsupportedDatastore.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link UnsupportedGuest }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "UnsupportedGuestFault")
    public JAXBElement<UnsupportedGuest> createUnsupportedGuestFault(UnsupportedGuest value) {
        return new JAXBElement<UnsupportedGuest>(_UnsupportedGuestFault_QNAME, UnsupportedGuest.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link UnsupportedVimApiVersion }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "UnsupportedVimApiVersionFault")
    public JAXBElement<UnsupportedVimApiVersion> createUnsupportedVimApiVersionFault(UnsupportedVimApiVersion value) {
        return new JAXBElement<UnsupportedVimApiVersion>(_UnsupportedVimApiVersionFault_QNAME, UnsupportedVimApiVersion.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link UnsupportedVmxLocation }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "UnsupportedVmxLocationFault")
    public JAXBElement<UnsupportedVmxLocation> createUnsupportedVmxLocationFault(UnsupportedVmxLocation value) {
        return new JAXBElement<UnsupportedVmxLocation>(_UnsupportedVmxLocationFault_QNAME, UnsupportedVmxLocation.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link UnusedVirtualDiskBlocksNotScrubbed }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "UnusedVirtualDiskBlocksNotScrubbedFault")
    public JAXBElement<UnusedVirtualDiskBlocksNotScrubbed> createUnusedVirtualDiskBlocksNotScrubbedFault(UnusedVirtualDiskBlocksNotScrubbed value) {
        return new JAXBElement<UnusedVirtualDiskBlocksNotScrubbed>(_UnusedVirtualDiskBlocksNotScrubbedFault_QNAME, UnusedVirtualDiskBlocksNotScrubbed.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link UserNotFound }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "UserNotFoundFault")
    public JAXBElement<UserNotFound> createUserNotFoundFault(UserNotFound value) {
        return new JAXBElement<UserNotFound>(_UserNotFoundFault_QNAME, UserNotFound.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VAppConfigFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VAppConfigFaultFault")
    public JAXBElement<VAppConfigFault> createVAppConfigFaultFault(VAppConfigFault value) {
        return new JAXBElement<VAppConfigFault>(_VAppConfigFaultFault_QNAME, VAppConfigFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VAppNotRunning }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VAppNotRunningFault")
    public JAXBElement<VAppNotRunning> createVAppNotRunningFault(VAppNotRunning value) {
        return new JAXBElement<VAppNotRunning>(_VAppNotRunningFault_QNAME, VAppNotRunning.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VAppOperationInProgress }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VAppOperationInProgressFault")
    public JAXBElement<VAppOperationInProgress> createVAppOperationInProgressFault(VAppOperationInProgress value) {
        return new JAXBElement<VAppOperationInProgress>(_VAppOperationInProgressFault_QNAME, VAppOperationInProgress.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VAppPropertyFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VAppPropertyFaultFault")
    public JAXBElement<VAppPropertyFault> createVAppPropertyFaultFault(VAppPropertyFault value) {
        return new JAXBElement<VAppPropertyFault>(_VAppPropertyFaultFault_QNAME, VAppPropertyFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VAppTaskInProgress }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VAppTaskInProgressFault")
    public JAXBElement<VAppTaskInProgress> createVAppTaskInProgressFault(VAppTaskInProgress value) {
        return new JAXBElement<VAppTaskInProgress>(_VAppTaskInProgressFault_QNAME, VAppTaskInProgress.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VFlashCacheHotConfigNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VFlashCacheHotConfigNotSupportedFault")
    public JAXBElement<VFlashCacheHotConfigNotSupported> createVFlashCacheHotConfigNotSupportedFault(VFlashCacheHotConfigNotSupported value) {
        return new JAXBElement<VFlashCacheHotConfigNotSupported>(_VFlashCacheHotConfigNotSupportedFault_QNAME, VFlashCacheHotConfigNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VFlashModuleNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VFlashModuleNotSupportedFault")
    public JAXBElement<VFlashModuleNotSupported> createVFlashModuleNotSupportedFault(VFlashModuleNotSupported value) {
        return new JAXBElement<VFlashModuleNotSupported>(_VFlashModuleNotSupportedFault_QNAME, VFlashModuleNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VFlashModuleVersionIncompatible }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VFlashModuleVersionIncompatibleFault")
    public JAXBElement<VFlashModuleVersionIncompatible> createVFlashModuleVersionIncompatibleFault(VFlashModuleVersionIncompatible value) {
        return new JAXBElement<VFlashModuleVersionIncompatible>(_VFlashModuleVersionIncompatibleFault_QNAME, VFlashModuleVersionIncompatible.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VMINotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VMINotSupportedFault")
    public JAXBElement<VMINotSupported> createVMINotSupportedFault(VMINotSupported value) {
        return new JAXBElement<VMINotSupported>(_VMINotSupportedFault_QNAME, VMINotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VMOnConflictDVPort }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VMOnConflictDVPortFault")
    public JAXBElement<VMOnConflictDVPort> createVMOnConflictDVPortFault(VMOnConflictDVPort value) {
        return new JAXBElement<VMOnConflictDVPort>(_VMOnConflictDVPortFault_QNAME, VMOnConflictDVPort.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VMOnVirtualIntranet }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VMOnVirtualIntranetFault")
    public JAXBElement<VMOnVirtualIntranet> createVMOnVirtualIntranetFault(VMOnVirtualIntranet value) {
        return new JAXBElement<VMOnVirtualIntranet>(_VMOnVirtualIntranetFault_QNAME, VMOnVirtualIntranet.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VMotionAcrossNetworkNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VMotionAcrossNetworkNotSupportedFault")
    public JAXBElement<VMotionAcrossNetworkNotSupported> createVMotionAcrossNetworkNotSupportedFault(VMotionAcrossNetworkNotSupported value) {
        return new JAXBElement<VMotionAcrossNetworkNotSupported>(_VMotionAcrossNetworkNotSupportedFault_QNAME, VMotionAcrossNetworkNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VMotionInterfaceIssue }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VMotionInterfaceIssueFault")
    public JAXBElement<VMotionInterfaceIssue> createVMotionInterfaceIssueFault(VMotionInterfaceIssue value) {
        return new JAXBElement<VMotionInterfaceIssue>(_VMotionInterfaceIssueFault_QNAME, VMotionInterfaceIssue.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VMotionLinkCapacityLow }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VMotionLinkCapacityLowFault")
    public JAXBElement<VMotionLinkCapacityLow> createVMotionLinkCapacityLowFault(VMotionLinkCapacityLow value) {
        return new JAXBElement<VMotionLinkCapacityLow>(_VMotionLinkCapacityLowFault_QNAME, VMotionLinkCapacityLow.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VMotionLinkDown }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VMotionLinkDownFault")
    public JAXBElement<VMotionLinkDown> createVMotionLinkDownFault(VMotionLinkDown value) {
        return new JAXBElement<VMotionLinkDown>(_VMotionLinkDownFault_QNAME, VMotionLinkDown.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VMotionNotConfigured }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VMotionNotConfiguredFault")
    public JAXBElement<VMotionNotConfigured> createVMotionNotConfiguredFault(VMotionNotConfigured value) {
        return new JAXBElement<VMotionNotConfigured>(_VMotionNotConfiguredFault_QNAME, VMotionNotConfigured.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VMotionNotLicensed }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VMotionNotLicensedFault")
    public JAXBElement<VMotionNotLicensed> createVMotionNotLicensedFault(VMotionNotLicensed value) {
        return new JAXBElement<VMotionNotLicensed>(_VMotionNotLicensedFault_QNAME, VMotionNotLicensed.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VMotionNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VMotionNotSupportedFault")
    public JAXBElement<VMotionNotSupported> createVMotionNotSupportedFault(VMotionNotSupported value) {
        return new JAXBElement<VMotionNotSupported>(_VMotionNotSupportedFault_QNAME, VMotionNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VMotionProtocolIncompatible }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VMotionProtocolIncompatibleFault")
    public JAXBElement<VMotionProtocolIncompatible> createVMotionProtocolIncompatibleFault(VMotionProtocolIncompatible value) {
        return new JAXBElement<VMotionProtocolIncompatible>(_VMotionProtocolIncompatibleFault_QNAME, VMotionProtocolIncompatible.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VimFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VimFaultFault")
    public JAXBElement<VimFault> createVimFaultFault(VimFault value) {
        return new JAXBElement<VimFault>(_VimFaultFault_QNAME, VimFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VirtualDiskBlocksNotFullyProvisioned }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VirtualDiskBlocksNotFullyProvisionedFault")
    public JAXBElement<VirtualDiskBlocksNotFullyProvisioned> createVirtualDiskBlocksNotFullyProvisionedFault(VirtualDiskBlocksNotFullyProvisioned value) {
        return new JAXBElement<VirtualDiskBlocksNotFullyProvisioned>(_VirtualDiskBlocksNotFullyProvisionedFault_QNAME, VirtualDiskBlocksNotFullyProvisioned.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VirtualDiskModeNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VirtualDiskModeNotSupportedFault")
    public JAXBElement<VirtualDiskModeNotSupported> createVirtualDiskModeNotSupportedFault(VirtualDiskModeNotSupported value) {
        return new JAXBElement<VirtualDiskModeNotSupported>(_VirtualDiskModeNotSupportedFault_QNAME, VirtualDiskModeNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VirtualEthernetCardNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VirtualEthernetCardNotSupportedFault")
    public JAXBElement<VirtualEthernetCardNotSupported> createVirtualEthernetCardNotSupportedFault(VirtualEthernetCardNotSupported value) {
        return new JAXBElement<VirtualEthernetCardNotSupported>(_VirtualEthernetCardNotSupportedFault_QNAME, VirtualEthernetCardNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VirtualHardwareCompatibilityIssue }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VirtualHardwareCompatibilityIssueFault")
    public JAXBElement<VirtualHardwareCompatibilityIssue> createVirtualHardwareCompatibilityIssueFault(VirtualHardwareCompatibilityIssue value) {
        return new JAXBElement<VirtualHardwareCompatibilityIssue>(_VirtualHardwareCompatibilityIssueFault_QNAME, VirtualHardwareCompatibilityIssue.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VirtualHardwareVersionNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VirtualHardwareVersionNotSupportedFault")
    public JAXBElement<VirtualHardwareVersionNotSupported> createVirtualHardwareVersionNotSupportedFault(VirtualHardwareVersionNotSupported value) {
        return new JAXBElement<VirtualHardwareVersionNotSupported>(_VirtualHardwareVersionNotSupportedFault_QNAME, VirtualHardwareVersionNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VmAlreadyExistsInDatacenter }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VmAlreadyExistsInDatacenterFault")
    public JAXBElement<VmAlreadyExistsInDatacenter> createVmAlreadyExistsInDatacenterFault(VmAlreadyExistsInDatacenter value) {
        return new JAXBElement<VmAlreadyExistsInDatacenter>(_VmAlreadyExistsInDatacenterFault_QNAME, VmAlreadyExistsInDatacenter.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VmConfigFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VmConfigFaultFault")
    public JAXBElement<VmConfigFault> createVmConfigFaultFault(VmConfigFault value) {
        return new JAXBElement<VmConfigFault>(_VmConfigFaultFault_QNAME, VmConfigFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VmConfigIncompatibleForFaultTolerance }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VmConfigIncompatibleForFaultToleranceFault")
    public JAXBElement<VmConfigIncompatibleForFaultTolerance> createVmConfigIncompatibleForFaultToleranceFault(VmConfigIncompatibleForFaultTolerance value) {
        return new JAXBElement<VmConfigIncompatibleForFaultTolerance>(_VmConfigIncompatibleForFaultToleranceFault_QNAME, VmConfigIncompatibleForFaultTolerance.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VmConfigIncompatibleForRecordReplay }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VmConfigIncompatibleForRecordReplayFault")
    public JAXBElement<VmConfigIncompatibleForRecordReplay> createVmConfigIncompatibleForRecordReplayFault(VmConfigIncompatibleForRecordReplay value) {
        return new JAXBElement<VmConfigIncompatibleForRecordReplay>(_VmConfigIncompatibleForRecordReplayFault_QNAME, VmConfigIncompatibleForRecordReplay.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VmFaultToleranceConfigIssue }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VmFaultToleranceConfigIssueFault")
    public JAXBElement<VmFaultToleranceConfigIssue> createVmFaultToleranceConfigIssueFault(VmFaultToleranceConfigIssue value) {
        return new JAXBElement<VmFaultToleranceConfigIssue>(_VmFaultToleranceConfigIssueFault_QNAME, VmFaultToleranceConfigIssue.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VmFaultToleranceConfigIssueWrapper }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VmFaultToleranceConfigIssueWrapperFault")
    public JAXBElement<VmFaultToleranceConfigIssueWrapper> createVmFaultToleranceConfigIssueWrapperFault(VmFaultToleranceConfigIssueWrapper value) {
        return new JAXBElement<VmFaultToleranceConfigIssueWrapper>(_VmFaultToleranceConfigIssueWrapperFault_QNAME, VmFaultToleranceConfigIssueWrapper.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VmFaultToleranceInvalidFileBacking }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VmFaultToleranceInvalidFileBackingFault")
    public JAXBElement<VmFaultToleranceInvalidFileBacking> createVmFaultToleranceInvalidFileBackingFault(VmFaultToleranceInvalidFileBacking value) {
        return new JAXBElement<VmFaultToleranceInvalidFileBacking>(_VmFaultToleranceInvalidFileBackingFault_QNAME, VmFaultToleranceInvalidFileBacking.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VmFaultToleranceIssue }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VmFaultToleranceIssueFault")
    public JAXBElement<VmFaultToleranceIssue> createVmFaultToleranceIssueFault(VmFaultToleranceIssue value) {
        return new JAXBElement<VmFaultToleranceIssue>(_VmFaultToleranceIssueFault_QNAME, VmFaultToleranceIssue.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VmFaultToleranceOpIssuesList }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VmFaultToleranceOpIssuesListFault")
    public JAXBElement<VmFaultToleranceOpIssuesList> createVmFaultToleranceOpIssuesListFault(VmFaultToleranceOpIssuesList value) {
        return new JAXBElement<VmFaultToleranceOpIssuesList>(_VmFaultToleranceOpIssuesListFault_QNAME, VmFaultToleranceOpIssuesList.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VmFaultToleranceTooManyFtVcpusOnHost }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VmFaultToleranceTooManyFtVcpusOnHostFault")
    public JAXBElement<VmFaultToleranceTooManyFtVcpusOnHost> createVmFaultToleranceTooManyFtVcpusOnHostFault(VmFaultToleranceTooManyFtVcpusOnHost value) {
        return new JAXBElement<VmFaultToleranceTooManyFtVcpusOnHost>(_VmFaultToleranceTooManyFtVcpusOnHostFault_QNAME, VmFaultToleranceTooManyFtVcpusOnHost.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VmFaultToleranceTooManyVMsOnHost }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VmFaultToleranceTooManyVMsOnHostFault")
    public JAXBElement<VmFaultToleranceTooManyVMsOnHost> createVmFaultToleranceTooManyVMsOnHostFault(VmFaultToleranceTooManyVMsOnHost value) {
        return new JAXBElement<VmFaultToleranceTooManyVMsOnHost>(_VmFaultToleranceTooManyVMsOnHostFault_QNAME, VmFaultToleranceTooManyVMsOnHost.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VmHostAffinityRuleViolation }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VmHostAffinityRuleViolationFault")
    public JAXBElement<VmHostAffinityRuleViolation> createVmHostAffinityRuleViolationFault(VmHostAffinityRuleViolation value) {
        return new JAXBElement<VmHostAffinityRuleViolation>(_VmHostAffinityRuleViolationFault_QNAME, VmHostAffinityRuleViolation.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VmLimitLicense }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VmLimitLicenseFault")
    public JAXBElement<VmLimitLicense> createVmLimitLicenseFault(VmLimitLicense value) {
        return new JAXBElement<VmLimitLicense>(_VmLimitLicenseFault_QNAME, VmLimitLicense.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VmMetadataManagerFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VmMetadataManagerFaultFault")
    public JAXBElement<VmMetadataManagerFault> createVmMetadataManagerFaultFault(VmMetadataManagerFault value) {
        return new JAXBElement<VmMetadataManagerFault>(_VmMetadataManagerFaultFault_QNAME, VmMetadataManagerFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VmMonitorIncompatibleForFaultTolerance }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VmMonitorIncompatibleForFaultToleranceFault")
    public JAXBElement<VmMonitorIncompatibleForFaultTolerance> createVmMonitorIncompatibleForFaultToleranceFault(VmMonitorIncompatibleForFaultTolerance value) {
        return new JAXBElement<VmMonitorIncompatibleForFaultTolerance>(_VmMonitorIncompatibleForFaultToleranceFault_QNAME, VmMonitorIncompatibleForFaultTolerance.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VmPowerOnDisabled }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VmPowerOnDisabledFault")
    public JAXBElement<VmPowerOnDisabled> createVmPowerOnDisabledFault(VmPowerOnDisabled value) {
        return new JAXBElement<VmPowerOnDisabled>(_VmPowerOnDisabledFault_QNAME, VmPowerOnDisabled.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VmSmpFaultToleranceTooManyVMsOnHost }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VmSmpFaultToleranceTooManyVMsOnHostFault")
    public JAXBElement<VmSmpFaultToleranceTooManyVMsOnHost> createVmSmpFaultToleranceTooManyVMsOnHostFault(VmSmpFaultToleranceTooManyVMsOnHost value) {
        return new JAXBElement<VmSmpFaultToleranceTooManyVMsOnHost>(_VmSmpFaultToleranceTooManyVMsOnHostFault_QNAME, VmSmpFaultToleranceTooManyVMsOnHost.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VmToolsUpgradeFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VmToolsUpgradeFaultFault")
    public JAXBElement<VmToolsUpgradeFault> createVmToolsUpgradeFaultFault(VmToolsUpgradeFault value) {
        return new JAXBElement<VmToolsUpgradeFault>(_VmToolsUpgradeFaultFault_QNAME, VmToolsUpgradeFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VmValidateMaxDevice }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VmValidateMaxDeviceFault")
    public JAXBElement<VmValidateMaxDevice> createVmValidateMaxDeviceFault(VmValidateMaxDevice value) {
        return new JAXBElement<VmValidateMaxDevice>(_VmValidateMaxDeviceFault_QNAME, VmValidateMaxDevice.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VmWwnConflict }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VmWwnConflictFault")
    public JAXBElement<VmWwnConflict> createVmWwnConflictFault(VmWwnConflict value) {
        return new JAXBElement<VmWwnConflict>(_VmWwnConflictFault_QNAME, VmWwnConflict.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VmfsAlreadyMounted }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VmfsAlreadyMountedFault")
    public JAXBElement<VmfsAlreadyMounted> createVmfsAlreadyMountedFault(VmfsAlreadyMounted value) {
        return new JAXBElement<VmfsAlreadyMounted>(_VmfsAlreadyMountedFault_QNAME, VmfsAlreadyMounted.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VmfsAmbiguousMount }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VmfsAmbiguousMountFault")
    public JAXBElement<VmfsAmbiguousMount> createVmfsAmbiguousMountFault(VmfsAmbiguousMount value) {
        return new JAXBElement<VmfsAmbiguousMount>(_VmfsAmbiguousMountFault_QNAME, VmfsAmbiguousMount.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VmfsMountFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VmfsMountFaultFault")
    public JAXBElement<VmfsMountFault> createVmfsMountFaultFault(VmfsMountFault value) {
        return new JAXBElement<VmfsMountFault>(_VmfsMountFaultFault_QNAME, VmfsMountFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VmotionInterfaceNotEnabled }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VmotionInterfaceNotEnabledFault")
    public JAXBElement<VmotionInterfaceNotEnabled> createVmotionInterfaceNotEnabledFault(VmotionInterfaceNotEnabled value) {
        return new JAXBElement<VmotionInterfaceNotEnabled>(_VmotionInterfaceNotEnabledFault_QNAME, VmotionInterfaceNotEnabled.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VolumeEditorError }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VolumeEditorErrorFault")
    public JAXBElement<VolumeEditorError> createVolumeEditorErrorFault(VolumeEditorError value) {
        return new JAXBElement<VolumeEditorError>(_VolumeEditorErrorFault_QNAME, VolumeEditorError.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VramLimitLicense }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VramLimitLicenseFault")
    public JAXBElement<VramLimitLicense> createVramLimitLicenseFault(VramLimitLicense value) {
        return new JAXBElement<VramLimitLicense>(_VramLimitLicenseFault_QNAME, VramLimitLicense.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VsanClusterUuidMismatch }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VsanClusterUuidMismatchFault")
    public JAXBElement<VsanClusterUuidMismatch> createVsanClusterUuidMismatchFault(VsanClusterUuidMismatch value) {
        return new JAXBElement<VsanClusterUuidMismatch>(_VsanClusterUuidMismatchFault_QNAME, VsanClusterUuidMismatch.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VsanDiskFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VsanDiskFaultFault")
    public JAXBElement<VsanDiskFault> createVsanDiskFaultFault(VsanDiskFault value) {
        return new JAXBElement<VsanDiskFault>(_VsanDiskFaultFault_QNAME, VsanDiskFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VsanFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VsanFaultFault")
    public JAXBElement<VsanFault> createVsanFaultFault(VsanFault value) {
        return new JAXBElement<VsanFault>(_VsanFaultFault_QNAME, VsanFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VsanIncompatibleDiskMapping }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VsanIncompatibleDiskMappingFault")
    public JAXBElement<VsanIncompatibleDiskMapping> createVsanIncompatibleDiskMappingFault(VsanIncompatibleDiskMapping value) {
        return new JAXBElement<VsanIncompatibleDiskMapping>(_VsanIncompatibleDiskMappingFault_QNAME, VsanIncompatibleDiskMapping.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VspanDestPortConflict }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VspanDestPortConflictFault")
    public JAXBElement<VspanDestPortConflict> createVspanDestPortConflictFault(VspanDestPortConflict value) {
        return new JAXBElement<VspanDestPortConflict>(_VspanDestPortConflictFault_QNAME, VspanDestPortConflict.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VspanPortConflict }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VspanPortConflictFault")
    public JAXBElement<VspanPortConflict> createVspanPortConflictFault(VspanPortConflict value) {
        return new JAXBElement<VspanPortConflict>(_VspanPortConflictFault_QNAME, VspanPortConflict.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VspanPortMoveFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VspanPortMoveFaultFault")
    public JAXBElement<VspanPortMoveFault> createVspanPortMoveFaultFault(VspanPortMoveFault value) {
        return new JAXBElement<VspanPortMoveFault>(_VspanPortMoveFaultFault_QNAME, VspanPortMoveFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VspanPortPromiscChangeFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VspanPortPromiscChangeFaultFault")
    public JAXBElement<VspanPortPromiscChangeFault> createVspanPortPromiscChangeFaultFault(VspanPortPromiscChangeFault value) {
        return new JAXBElement<VspanPortPromiscChangeFault>(_VspanPortPromiscChangeFaultFault_QNAME, VspanPortPromiscChangeFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VspanPortgroupPromiscChangeFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VspanPortgroupPromiscChangeFaultFault")
    public JAXBElement<VspanPortgroupPromiscChangeFault> createVspanPortgroupPromiscChangeFaultFault(VspanPortgroupPromiscChangeFault value) {
        return new JAXBElement<VspanPortgroupPromiscChangeFault>(_VspanPortgroupPromiscChangeFaultFault_QNAME, VspanPortgroupPromiscChangeFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VspanPortgroupTypeChangeFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VspanPortgroupTypeChangeFaultFault")
    public JAXBElement<VspanPortgroupTypeChangeFault> createVspanPortgroupTypeChangeFaultFault(VspanPortgroupTypeChangeFault value) {
        return new JAXBElement<VspanPortgroupTypeChangeFault>(_VspanPortgroupTypeChangeFaultFault_QNAME, VspanPortgroupTypeChangeFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VspanPromiscuousPortNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VspanPromiscuousPortNotSupportedFault")
    public JAXBElement<VspanPromiscuousPortNotSupported> createVspanPromiscuousPortNotSupportedFault(VspanPromiscuousPortNotSupported value) {
        return new JAXBElement<VspanPromiscuousPortNotSupported>(_VspanPromiscuousPortNotSupportedFault_QNAME, VspanPromiscuousPortNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VspanSameSessionPortConflict }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "VspanSameSessionPortConflictFault")
    public JAXBElement<VspanSameSessionPortConflict> createVspanSameSessionPortConflictFault(VspanSameSessionPortConflict value) {
        return new JAXBElement<VspanSameSessionPortConflict>(_VspanSameSessionPortConflictFault_QNAME, VspanSameSessionPortConflict.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link WakeOnLanNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "WakeOnLanNotSupportedFault")
    public JAXBElement<WakeOnLanNotSupported> createWakeOnLanNotSupportedFault(WakeOnLanNotSupported value) {
        return new JAXBElement<WakeOnLanNotSupported>(_WakeOnLanNotSupportedFault_QNAME, WakeOnLanNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link WakeOnLanNotSupportedByVmotionNIC }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "WakeOnLanNotSupportedByVmotionNICFault")
    public JAXBElement<WakeOnLanNotSupportedByVmotionNIC> createWakeOnLanNotSupportedByVmotionNICFault(WakeOnLanNotSupportedByVmotionNIC value) {
        return new JAXBElement<WakeOnLanNotSupportedByVmotionNIC>(_WakeOnLanNotSupportedByVmotionNICFault_QNAME, WakeOnLanNotSupportedByVmotionNIC.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link WillLoseHAProtection }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "WillLoseHAProtectionFault")
    public JAXBElement<WillLoseHAProtection> createWillLoseHAProtectionFault(WillLoseHAProtection value) {
        return new JAXBElement<WillLoseHAProtection>(_WillLoseHAProtectionFault_QNAME, WillLoseHAProtection.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link WillModifyConfigCpuRequirements }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "WillModifyConfigCpuRequirementsFault")
    public JAXBElement<WillModifyConfigCpuRequirements> createWillModifyConfigCpuRequirementsFault(WillModifyConfigCpuRequirements value) {
        return new JAXBElement<WillModifyConfigCpuRequirements>(_WillModifyConfigCpuRequirementsFault_QNAME, WillModifyConfigCpuRequirements.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link WillResetSnapshotDirectory }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "WillResetSnapshotDirectoryFault")
    public JAXBElement<WillResetSnapshotDirectory> createWillResetSnapshotDirectoryFault(WillResetSnapshotDirectory value) {
        return new JAXBElement<WillResetSnapshotDirectory>(_WillResetSnapshotDirectoryFault_QNAME, WillResetSnapshotDirectory.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link WipeDiskFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "WipeDiskFaultFault")
    public JAXBElement<WipeDiskFault> createWipeDiskFaultFault(WipeDiskFault value) {
        return new JAXBElement<WipeDiskFault>(_WipeDiskFaultFault_QNAME, WipeDiskFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidCollectorVersion }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidCollectorVersionFault")
    public JAXBElement<InvalidCollectorVersion> createInvalidCollectorVersionFault(InvalidCollectorVersion value) {
        return new JAXBElement<InvalidCollectorVersion>(_InvalidCollectorVersionFault_QNAME, InvalidCollectorVersion.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidProperty }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidPropertyFault")
    public JAXBElement<InvalidProperty> createInvalidPropertyFault(InvalidProperty value) {
        return new JAXBElement<InvalidProperty>(_InvalidPropertyFault_QNAME, InvalidProperty.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MethodFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MethodFaultFault")
    public JAXBElement<MethodFault> createMethodFaultFault(MethodFault value) {
        return new JAXBElement<MethodFault>(_MethodFaultFault_QNAME, MethodFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link RuntimeFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "RuntimeFaultFault")
    public JAXBElement<RuntimeFault> createRuntimeFaultFault(RuntimeFault value) {
        return new JAXBElement<RuntimeFault>(_RuntimeFaultFault_QNAME, RuntimeFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link HostCommunication }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "HostCommunicationFault")
    public JAXBElement<HostCommunication> createHostCommunicationFault(HostCommunication value) {
        return new JAXBElement<HostCommunication>(_HostCommunicationFault_QNAME, HostCommunication.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link HostNotConnected }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "HostNotConnectedFault")
    public JAXBElement<HostNotConnected> createHostNotConnectedFault(HostNotConnected value) {
        return new JAXBElement<HostNotConnected>(_HostNotConnectedFault_QNAME, HostNotConnected.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link HostNotReachable }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "HostNotReachableFault")
    public JAXBElement<HostNotReachable> createHostNotReachableFault(HostNotReachable value) {
        return new JAXBElement<HostNotReachable>(_HostNotReachableFault_QNAME, HostNotReachable.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidArgument }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidArgumentFault")
    public JAXBElement<InvalidArgument> createInvalidArgumentFault(InvalidArgument value) {
        return new JAXBElement<InvalidArgument>(_InvalidArgumentFault_QNAME, InvalidArgument.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidRequest }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidRequestFault")
    public JAXBElement<InvalidRequest> createInvalidRequestFault(InvalidRequest value) {
        return new JAXBElement<InvalidRequest>(_InvalidRequestFault_QNAME, InvalidRequest.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "InvalidTypeFault")
    public JAXBElement<InvalidType> createInvalidTypeFault(InvalidType value) {
        return new JAXBElement<InvalidType>(_InvalidTypeFault_QNAME, InvalidType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ManagedObjectNotFound }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "ManagedObjectNotFoundFault")
    public JAXBElement<ManagedObjectNotFound> createManagedObjectNotFoundFault(ManagedObjectNotFound value) {
        return new JAXBElement<ManagedObjectNotFound>(_ManagedObjectNotFoundFault_QNAME, ManagedObjectNotFound.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MethodNotFound }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "MethodNotFoundFault")
    public JAXBElement<MethodNotFound> createMethodNotFoundFault(MethodNotFound value) {
        return new JAXBElement<MethodNotFound>(_MethodNotFoundFault_QNAME, MethodNotFound.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NotEnoughLicenses }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NotEnoughLicensesFault")
    public JAXBElement<NotEnoughLicenses> createNotEnoughLicensesFault(NotEnoughLicenses value) {
        return new JAXBElement<NotEnoughLicenses>(_NotEnoughLicensesFault_QNAME, NotEnoughLicenses.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NotImplemented }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NotImplementedFault")
    public JAXBElement<NotImplemented> createNotImplementedFault(NotImplemented value) {
        return new JAXBElement<NotImplemented>(_NotImplementedFault_QNAME, NotImplemented.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "NotSupportedFault")
    public JAXBElement<NotSupported> createNotSupportedFault(NotSupported value) {
        return new JAXBElement<NotSupported>(_NotSupportedFault_QNAME, NotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link RequestCanceled }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "RequestCanceledFault")
    public JAXBElement<RequestCanceled> createRequestCanceledFault(RequestCanceled value) {
        return new JAXBElement<RequestCanceled>(_RequestCanceledFault_QNAME, RequestCanceled.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SecurityError }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "SecurityErrorFault")
    public JAXBElement<SecurityError> createSecurityErrorFault(SecurityError value) {
        return new JAXBElement<SecurityError>(_SecurityErrorFault_QNAME, SecurityError.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SystemError }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "SystemErrorFault")
    public JAXBElement<SystemError> createSystemErrorFault(SystemError value) {
        return new JAXBElement<SystemError>(_SystemErrorFault_QNAME, SystemError.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link UnexpectedFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "UnexpectedFaultFault")
    public JAXBElement<UnexpectedFault> createUnexpectedFaultFault(UnexpectedFault value) {
        return new JAXBElement<UnexpectedFault>(_UnexpectedFaultFault_QNAME, UnexpectedFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmRetrieveServiceContentRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmRetrieveServiceContent")
    public JAXBElement<PbmRetrieveServiceContentRequestType> createPbmRetrieveServiceContent(PbmRetrieveServiceContentRequestType value) {
        return new JAXBElement<PbmRetrieveServiceContentRequestType>(_PbmRetrieveServiceContent_QNAME, PbmRetrieveServiceContentRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmCheckComplianceRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmCheckCompliance")
    public JAXBElement<PbmCheckComplianceRequestType> createPbmCheckCompliance(PbmCheckComplianceRequestType value) {
        return new JAXBElement<PbmCheckComplianceRequestType>(_PbmCheckCompliance_QNAME, PbmCheckComplianceRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmFetchComplianceResultRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmFetchComplianceResult")
    public JAXBElement<PbmFetchComplianceResultRequestType> createPbmFetchComplianceResult(PbmFetchComplianceResultRequestType value) {
        return new JAXBElement<PbmFetchComplianceResultRequestType>(_PbmFetchComplianceResult_QNAME, PbmFetchComplianceResultRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmCheckRollupComplianceRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmCheckRollupCompliance")
    public JAXBElement<PbmCheckRollupComplianceRequestType> createPbmCheckRollupCompliance(PbmCheckRollupComplianceRequestType value) {
        return new JAXBElement<PbmCheckRollupComplianceRequestType>(_PbmCheckRollupCompliance_QNAME, PbmCheckRollupComplianceRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmFetchRollupComplianceResultRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmFetchRollupComplianceResult")
    public JAXBElement<PbmFetchRollupComplianceResultRequestType> createPbmFetchRollupComplianceResult(PbmFetchRollupComplianceResultRequestType value) {
        return new JAXBElement<PbmFetchRollupComplianceResultRequestType>(_PbmFetchRollupComplianceResult_QNAME, PbmFetchRollupComplianceResultRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmQueryByRollupComplianceStatusRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmQueryByRollupComplianceStatus")
    public JAXBElement<PbmQueryByRollupComplianceStatusRequestType> createPbmQueryByRollupComplianceStatus(PbmQueryByRollupComplianceStatusRequestType value) {
        return new JAXBElement<PbmQueryByRollupComplianceStatusRequestType>(_PbmQueryByRollupComplianceStatus_QNAME, PbmQueryByRollupComplianceStatusRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmAlreadyExists }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmAlreadyExistsFault")
    public JAXBElement<PbmAlreadyExists> createPbmAlreadyExistsFault(PbmAlreadyExists value) {
        return new JAXBElement<PbmAlreadyExists>(_PbmAlreadyExistsFault_QNAME, PbmAlreadyExists.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmCapabilityProfilePropertyMismatchFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmCapabilityProfilePropertyMismatchFaultFault")
    public JAXBElement<PbmCapabilityProfilePropertyMismatchFault> createPbmCapabilityProfilePropertyMismatchFaultFault(PbmCapabilityProfilePropertyMismatchFault value) {
        return new JAXBElement<PbmCapabilityProfilePropertyMismatchFault>(_PbmCapabilityProfilePropertyMismatchFaultFault_QNAME, PbmCapabilityProfilePropertyMismatchFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmCompatibilityCheckFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmCompatibilityCheckFaultFault")
    public JAXBElement<PbmCompatibilityCheckFault> createPbmCompatibilityCheckFaultFault(PbmCompatibilityCheckFault value) {
        return new JAXBElement<PbmCompatibilityCheckFault>(_PbmCompatibilityCheckFaultFault_QNAME, PbmCompatibilityCheckFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmDefaultProfileAppliesFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmDefaultProfileAppliesFaultFault")
    public JAXBElement<PbmDefaultProfileAppliesFault> createPbmDefaultProfileAppliesFaultFault(PbmDefaultProfileAppliesFault value) {
        return new JAXBElement<PbmDefaultProfileAppliesFault>(_PbmDefaultProfileAppliesFaultFault_QNAME, PbmDefaultProfileAppliesFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmDuplicateName }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmDuplicateNameFault")
    public JAXBElement<PbmDuplicateName> createPbmDuplicateNameFault(PbmDuplicateName value) {
        return new JAXBElement<PbmDuplicateName>(_PbmDuplicateNameFault_QNAME, PbmDuplicateName.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmIncompatibleVendorSpecificRuleSet }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmIncompatibleVendorSpecificRuleSetFault")
    public JAXBElement<PbmIncompatibleVendorSpecificRuleSet> createPbmIncompatibleVendorSpecificRuleSetFault(PbmIncompatibleVendorSpecificRuleSet value) {
        return new JAXBElement<PbmIncompatibleVendorSpecificRuleSet>(_PbmIncompatibleVendorSpecificRuleSetFault_QNAME, PbmIncompatibleVendorSpecificRuleSet.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmFaultInvalidLogin }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmFaultInvalidLoginFault")
    public JAXBElement<PbmFaultInvalidLogin> createPbmFaultInvalidLoginFault(PbmFaultInvalidLogin value) {
        return new JAXBElement<PbmFaultInvalidLogin>(_PbmFaultInvalidLoginFault_QNAME, PbmFaultInvalidLogin.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmLegacyHubsNotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmLegacyHubsNotSupportedFault")
    public JAXBElement<PbmLegacyHubsNotSupported> createPbmLegacyHubsNotSupportedFault(PbmLegacyHubsNotSupported value) {
        return new JAXBElement<PbmLegacyHubsNotSupported>(_PbmLegacyHubsNotSupportedFault_QNAME, PbmLegacyHubsNotSupported.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmNonExistentHubs }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmNonExistentHubsFault")
    public JAXBElement<PbmNonExistentHubs> createPbmNonExistentHubsFault(PbmNonExistentHubs value) {
        return new JAXBElement<PbmNonExistentHubs>(_PbmNonExistentHubsFault_QNAME, PbmNonExistentHubs.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmFaultNotFound }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmFaultNotFoundFault")
    public JAXBElement<PbmFaultNotFound> createPbmFaultNotFoundFault(PbmFaultNotFound value) {
        return new JAXBElement<PbmFaultNotFound>(_PbmFaultNotFoundFault_QNAME, PbmFaultNotFound.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmFaultFault")
    public JAXBElement<PbmFault> createPbmFaultFault(PbmFault value) {
        return new JAXBElement<PbmFault>(_PbmFaultFault_QNAME, PbmFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmFaultProfileStorageFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmFaultProfileStorageFaultFault")
    public JAXBElement<PbmFaultProfileStorageFault> createPbmFaultProfileStorageFaultFault(PbmFaultProfileStorageFault value) {
        return new JAXBElement<PbmFaultProfileStorageFault>(_PbmFaultProfileStorageFaultFault_QNAME, PbmFaultProfileStorageFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmPropertyMismatchFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmPropertyMismatchFaultFault")
    public JAXBElement<PbmPropertyMismatchFault> createPbmPropertyMismatchFaultFault(PbmPropertyMismatchFault value) {
        return new JAXBElement<PbmPropertyMismatchFault>(_PbmPropertyMismatchFaultFault_QNAME, PbmPropertyMismatchFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmResourceInUse }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmResourceInUseFault")
    public JAXBElement<PbmResourceInUse> createPbmResourceInUseFault(PbmResourceInUse value) {
        return new JAXBElement<PbmResourceInUse>(_PbmResourceInUseFault_QNAME, PbmResourceInUse.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmQueryMatchingHubRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmQueryMatchingHub")
    public JAXBElement<PbmQueryMatchingHubRequestType> createPbmQueryMatchingHub(PbmQueryMatchingHubRequestType value) {
        return new JAXBElement<PbmQueryMatchingHubRequestType>(_PbmQueryMatchingHub_QNAME, PbmQueryMatchingHubRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmQueryMatchingHubWithSpecRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmQueryMatchingHubWithSpec")
    public JAXBElement<PbmQueryMatchingHubWithSpecRequestType> createPbmQueryMatchingHubWithSpec(PbmQueryMatchingHubWithSpecRequestType value) {
        return new JAXBElement<PbmQueryMatchingHubWithSpecRequestType>(_PbmQueryMatchingHubWithSpec_QNAME, PbmQueryMatchingHubWithSpecRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmCheckCompatibilityRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmCheckCompatibility")
    public JAXBElement<PbmCheckCompatibilityRequestType> createPbmCheckCompatibility(PbmCheckCompatibilityRequestType value) {
        return new JAXBElement<PbmCheckCompatibilityRequestType>(_PbmCheckCompatibility_QNAME, PbmCheckCompatibilityRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmCheckCompatibilityWithSpecRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmCheckCompatibilityWithSpec")
    public JAXBElement<PbmCheckCompatibilityWithSpecRequestType> createPbmCheckCompatibilityWithSpec(PbmCheckCompatibilityWithSpecRequestType value) {
        return new JAXBElement<PbmCheckCompatibilityWithSpecRequestType>(_PbmCheckCompatibilityWithSpec_QNAME, PbmCheckCompatibilityWithSpecRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmCheckRequirementsRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmCheckRequirements")
    public JAXBElement<PbmCheckRequirementsRequestType> createPbmCheckRequirements(PbmCheckRequirementsRequestType value) {
        return new JAXBElement<PbmCheckRequirementsRequestType>(_PbmCheckRequirements_QNAME, PbmCheckRequirementsRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmFetchResourceTypeRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmFetchResourceType")
    public JAXBElement<PbmFetchResourceTypeRequestType> createPbmFetchResourceType(PbmFetchResourceTypeRequestType value) {
        return new JAXBElement<PbmFetchResourceTypeRequestType>(_PbmFetchResourceType_QNAME, PbmFetchResourceTypeRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmFetchVendorInfoRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmFetchVendorInfo")
    public JAXBElement<PbmFetchVendorInfoRequestType> createPbmFetchVendorInfo(PbmFetchVendorInfoRequestType value) {
        return new JAXBElement<PbmFetchVendorInfoRequestType>(_PbmFetchVendorInfo_QNAME, PbmFetchVendorInfoRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmFetchCapabilityMetadataRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmFetchCapabilityMetadata")
    public JAXBElement<PbmFetchCapabilityMetadataRequestType> createPbmFetchCapabilityMetadata(PbmFetchCapabilityMetadataRequestType value) {
        return new JAXBElement<PbmFetchCapabilityMetadataRequestType>(_PbmFetchCapabilityMetadata_QNAME, PbmFetchCapabilityMetadataRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmFetchCapabilitySchemaRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmFetchCapabilitySchema")
    public JAXBElement<PbmFetchCapabilitySchemaRequestType> createPbmFetchCapabilitySchema(PbmFetchCapabilitySchemaRequestType value) {
        return new JAXBElement<PbmFetchCapabilitySchemaRequestType>(_PbmFetchCapabilitySchema_QNAME, PbmFetchCapabilitySchemaRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmCreateRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmCreate")
    public JAXBElement<PbmCreateRequestType> createPbmCreate(PbmCreateRequestType value) {
        return new JAXBElement<PbmCreateRequestType>(_PbmCreate_QNAME, PbmCreateRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmUpdateRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmUpdate")
    public JAXBElement<PbmUpdateRequestType> createPbmUpdate(PbmUpdateRequestType value) {
        return new JAXBElement<PbmUpdateRequestType>(_PbmUpdate_QNAME, PbmUpdateRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmDeleteRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmDelete")
    public JAXBElement<PbmDeleteRequestType> createPbmDelete(PbmDeleteRequestType value) {
        return new JAXBElement<PbmDeleteRequestType>(_PbmDelete_QNAME, PbmDeleteRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmQueryProfileRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmQueryProfile")
    public JAXBElement<PbmQueryProfileRequestType> createPbmQueryProfile(PbmQueryProfileRequestType value) {
        return new JAXBElement<PbmQueryProfileRequestType>(_PbmQueryProfile_QNAME, PbmQueryProfileRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmRetrieveContentRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmRetrieveContent")
    public JAXBElement<PbmRetrieveContentRequestType> createPbmRetrieveContent(PbmRetrieveContentRequestType value) {
        return new JAXBElement<PbmRetrieveContentRequestType>(_PbmRetrieveContent_QNAME, PbmRetrieveContentRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmQueryAssociatedProfilesRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmQueryAssociatedProfiles")
    public JAXBElement<PbmQueryAssociatedProfilesRequestType> createPbmQueryAssociatedProfiles(PbmQueryAssociatedProfilesRequestType value) {
        return new JAXBElement<PbmQueryAssociatedProfilesRequestType>(_PbmQueryAssociatedProfiles_QNAME, PbmQueryAssociatedProfilesRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmQueryAssociatedProfileRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmQueryAssociatedProfile")
    public JAXBElement<PbmQueryAssociatedProfileRequestType> createPbmQueryAssociatedProfile(PbmQueryAssociatedProfileRequestType value) {
        return new JAXBElement<PbmQueryAssociatedProfileRequestType>(_PbmQueryAssociatedProfile_QNAME, PbmQueryAssociatedProfileRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmQueryAssociatedEntityRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmQueryAssociatedEntity")
    public JAXBElement<PbmQueryAssociatedEntityRequestType> createPbmQueryAssociatedEntity(PbmQueryAssociatedEntityRequestType value) {
        return new JAXBElement<PbmQueryAssociatedEntityRequestType>(_PbmQueryAssociatedEntity_QNAME, PbmQueryAssociatedEntityRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmQueryDefaultRequirementProfileRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmQueryDefaultRequirementProfile")
    public JAXBElement<PbmQueryDefaultRequirementProfileRequestType> createPbmQueryDefaultRequirementProfile(PbmQueryDefaultRequirementProfileRequestType value) {
        return new JAXBElement<PbmQueryDefaultRequirementProfileRequestType>(_PbmQueryDefaultRequirementProfile_QNAME, PbmQueryDefaultRequirementProfileRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmResetDefaultRequirementProfileRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmResetDefaultRequirementProfile")
    public JAXBElement<PbmResetDefaultRequirementProfileRequestType> createPbmResetDefaultRequirementProfile(PbmResetDefaultRequirementProfileRequestType value) {
        return new JAXBElement<PbmResetDefaultRequirementProfileRequestType>(_PbmResetDefaultRequirementProfile_QNAME, PbmResetDefaultRequirementProfileRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmAssignDefaultRequirementProfileRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmAssignDefaultRequirementProfile")
    public JAXBElement<PbmAssignDefaultRequirementProfileRequestType> createPbmAssignDefaultRequirementProfile(PbmAssignDefaultRequirementProfileRequestType value) {
        return new JAXBElement<PbmAssignDefaultRequirementProfileRequestType>(_PbmAssignDefaultRequirementProfile_QNAME, PbmAssignDefaultRequirementProfileRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmFindApplicableDefaultProfileRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmFindApplicableDefaultProfile")
    public JAXBElement<PbmFindApplicableDefaultProfileRequestType> createPbmFindApplicableDefaultProfile(PbmFindApplicableDefaultProfileRequestType value) {
        return new JAXBElement<PbmFindApplicableDefaultProfileRequestType>(_PbmFindApplicableDefaultProfile_QNAME, PbmFindApplicableDefaultProfileRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmQueryDefaultRequirementProfilesRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmQueryDefaultRequirementProfiles")
    public JAXBElement<PbmQueryDefaultRequirementProfilesRequestType> createPbmQueryDefaultRequirementProfiles(PbmQueryDefaultRequirementProfilesRequestType value) {
        return new JAXBElement<PbmQueryDefaultRequirementProfilesRequestType>(_PbmQueryDefaultRequirementProfiles_QNAME, PbmQueryDefaultRequirementProfilesRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmResetVSanDefaultProfileRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmResetVSanDefaultProfile")
    public JAXBElement<PbmResetVSanDefaultProfileRequestType> createPbmResetVSanDefaultProfile(PbmResetVSanDefaultProfileRequestType value) {
        return new JAXBElement<PbmResetVSanDefaultProfileRequestType>(_PbmResetVSanDefaultProfile_QNAME, PbmResetVSanDefaultProfileRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmQuerySpaceStatsForStorageContainerRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmQuerySpaceStatsForStorageContainer")
    public JAXBElement<PbmQuerySpaceStatsForStorageContainerRequestType> createPbmQuerySpaceStatsForStorageContainer(PbmQuerySpaceStatsForStorageContainerRequestType value) {
        return new JAXBElement<PbmQuerySpaceStatsForStorageContainerRequestType>(_PbmQuerySpaceStatsForStorageContainer_QNAME, PbmQuerySpaceStatsForStorageContainerRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmQueryAssociatedEntitiesRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmQueryAssociatedEntities")
    public JAXBElement<PbmQueryAssociatedEntitiesRequestType> createPbmQueryAssociatedEntities(PbmQueryAssociatedEntitiesRequestType value) {
        return new JAXBElement<PbmQueryAssociatedEntitiesRequestType>(_PbmQueryAssociatedEntities_QNAME, PbmQueryAssociatedEntitiesRequestType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PbmQueryReplicationGroupsRequestType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "urn:pbm", name = "PbmQueryReplicationGroups")
    public JAXBElement<PbmQueryReplicationGroupsRequestType> createPbmQueryReplicationGroups(PbmQueryReplicationGroupsRequestType value) {
        return new JAXBElement<PbmQueryReplicationGroupsRequestType>(_PbmQueryReplicationGroups_QNAME, PbmQueryReplicationGroupsRequestType.class, null, value);
    }

}
