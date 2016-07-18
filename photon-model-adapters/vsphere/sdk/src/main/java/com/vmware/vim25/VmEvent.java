
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VmEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VmEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}Event"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="template" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VmEvent", propOrder = {
    "template"
})
@XmlSeeAlso({
    VmSuspendedEvent.class,
    VmStoppingEvent.class,
    VmSuspendingEvent.class,
    VmResumingEvent.class,
    VmDisconnectedEvent.class,
    VmRemoteConsoleConnectedEvent.class,
    VmRemoteConsoleDisconnectedEvent.class,
    VmDiscoveredEvent.class,
    VmOrphanedEvent.class,
    VmBeingCreatedEvent.class,
    VmCreatedEvent.class,
    VmStartRecordingEvent.class,
    VmEndRecordingEvent.class,
    VmStartReplayingEvent.class,
    VmEndReplayingEvent.class,
    VmRegisteredEvent.class,
    VmAutoRenameEvent.class,
    VmBeingHotMigratedEvent.class,
    VmResettingEvent.class,
    VmStaticMacConflictEvent.class,
    VmMacConflictEvent.class,
    VmBeingDeployedEvent.class,
    VmDeployFailedEvent.class,
    VmDeployedEvent.class,
    VmMacChangedEvent.class,
    VmMacAssignedEvent.class,
    VmUuidConflictEvent.class,
    VmInstanceUuidConflictEvent.class,
    VmBeingMigratedEvent.class,
    VmFailedMigrateEvent.class,
    VmStartingEvent.class,
    VmMigratedEvent.class,
    DrsRuleViolationEvent.class,
    DrsSoftRuleViolationEvent.class,
    DrsRuleComplianceEvent.class,
    VmRelocateSpecEvent.class,
    VmEmigratingEvent.class,
    VmCloneEvent.class,
    VmResourceReallocatedEvent.class,
    VmRenamedEvent.class,
    VmDateRolledBackEvent.class,
    VmNoNetworkAccessEvent.class,
    VmDiskFailedEvent.class,
    VmFailedToPowerOnEvent.class,
    VmFailedToPowerOffEvent.class,
    VmFailedToSuspendEvent.class,
    VmFailedToResetEvent.class,
    VmFailedToShutdownGuestEvent.class,
    VmFailedToRebootGuestEvent.class,
    VmFailedToStandbyGuestEvent.class,
    VmRemovedEvent.class,
    VmGuestShutdownEvent.class,
    VmGuestRebootEvent.class,
    VmGuestStandbyEvent.class,
    VmUpgradingEvent.class,
    VmUpgradeCompleteEvent.class,
    VmUpgradeFailedEvent.class,
    VmPoweredOnEvent.class,
    VmReconfiguredEvent.class,
    VmMessageEvent.class,
    VmMessageWarningEvent.class,
    VmMessageErrorEvent.class,
    VmConfigMissingEvent.class,
    VmPoweredOffEvent.class,
    VmFailoverFailed.class,
    VmDasResetFailedEvent.class,
    VmMaxRestartCountReached.class,
    VmMaxFTRestartCountReached.class,
    VmDasBeingResetEvent.class,
    NotEnoughResourcesToStartVmEvent.class,
    VmUuidAssignedEvent.class,
    VmInstanceUuidAssignedEvent.class,
    VmUuidChangedEvent.class,
    VmInstanceUuidChangedEvent.class,
    VmWwnConflictEvent.class,
    VmAcquiredMksTicketEvent.class,
    VmAcquiredTicketEvent.class,
    VmGuestOSCrashedEvent.class,
    VmWwnAssignedEvent.class,
    VmWwnChangedEvent.class,
    VmSecondaryAddedEvent.class,
    VmFaultToleranceTurnedOffEvent.class,
    VmFaultToleranceStateChangedEvent.class,
    VmSecondaryDisabledEvent.class,
    VmSecondaryDisabledBySystemEvent.class,
    VmSecondaryEnabledEvent.class,
    VmStartingSecondaryEvent.class,
    VmSecondaryStartedEvent.class,
    VmFailedUpdatingSecondaryConfig.class,
    VmFailedStartingSecondaryEvent.class,
    VmTimedoutStartingSecondaryEvent.class,
    VmNoCompatibleHostForSecondaryEvent.class,
    VmPrimaryFailoverEvent.class,
    VmFaultToleranceVmTerminatedEvent.class,
    VmFailedRelayoutOnVmfs2DatastoreEvent.class,
    VmFailedRelayoutEvent.class,
    VmRelayoutSuccessfulEvent.class,
    VmRelayoutUpToDateEvent.class,
    VmConnectedEvent.class,
    VmPoweringOnWithCustomizedDVPortEvent.class,
    VmDasUpdateErrorEvent.class,
    NoMaintenanceModeDrsRecommendationForVM.class,
    VmDasUpdateOkEvent.class,
    MigrationEvent.class,
    VmResourcePoolMovedEvent.class,
    CustomizationEvent.class,
    VmReloadFromPathEvent.class,
    VmReloadFromPathFailedEvent.class,
    VmRequirementsExceedCurrentEVCModeEvent.class
})
public class VmEvent
    extends Event
{

    protected boolean template;

    /**
     * Gets the value of the template property.
     * 
     */
    public boolean isTemplate() {
        return template;
    }

    /**
     * Sets the value of the template property.
     * 
     */
    public void setTemplate(boolean value) {
        this.template = value;
    }

}
