
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DvsEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DvsEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}Event"&gt;
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
@XmlType(name = "DvsEvent")
@XmlSeeAlso({
    DvsCreatedEvent.class,
    DvsRenamedEvent.class,
    DvsReconfiguredEvent.class,
    DvsUpgradeAvailableEvent.class,
    DvsUpgradeInProgressEvent.class,
    DvsUpgradeRejectedEvent.class,
    DvsUpgradedEvent.class,
    DvsHostJoinedEvent.class,
    DvsHostLeftEvent.class,
    OutOfSyncDvsHost.class,
    DvsHostWentOutOfSyncEvent.class,
    DvsHostBackInSyncEvent.class,
    DvsHostStatusUpdated.class,
    DvsPortCreatedEvent.class,
    DvsPortReconfiguredEvent.class,
    DvsPortDeletedEvent.class,
    DvsPortConnectedEvent.class,
    DvsPortDisconnectedEvent.class,
    DvsPortVendorSpecificStateChangeEvent.class,
    DvsPortRuntimeChangeEvent.class,
    DvsPortLinkUpEvent.class,
    DvsPortLinkDownEvent.class,
    DvsPortJoinPortgroupEvent.class,
    DvsPortLeavePortgroupEvent.class,
    DvsPortBlockedEvent.class,
    DvsPortUnblockedEvent.class,
    DvsPortEnteredPassthruEvent.class,
    DvsPortExitedPassthruEvent.class,
    DvsDestroyedEvent.class,
    DvsMergedEvent.class,
    HostLocalPortCreatedEvent.class,
    RollbackEvent.class,
    RecoveryEvent.class,
    DvsImportEvent.class,
    DvsRestoreEvent.class,
    VmVnicPoolReservationViolationRaiseEvent.class,
    VmVnicPoolReservationViolationClearEvent.class
})
public class DvsEvent
    extends Event
{


}
