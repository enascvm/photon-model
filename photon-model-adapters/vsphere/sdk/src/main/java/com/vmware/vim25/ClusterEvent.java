
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ClusterEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ClusterEvent"&gt;
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
@XmlType(name = "ClusterEvent")
@XmlSeeAlso({
    ClusterComplianceCheckedEvent.class,
    DasEnabledEvent.class,
    DasDisabledEvent.class,
    DasAdmissionControlDisabledEvent.class,
    DasAdmissionControlEnabledEvent.class,
    DasHostFailedEvent.class,
    DasHostIsolatedEvent.class,
    DasClusterIsolatedEvent.class,
    DasAgentUnavailableEvent.class,
    DasAgentFoundEvent.class,
    InsufficientFailoverResourcesEvent.class,
    FailoverLevelRestored.class,
    ClusterOvercommittedEvent.class,
    ClusterStatusChangedEvent.class,
    ClusterCreatedEvent.class,
    ClusterDestroyedEvent.class,
    DrsEnabledEvent.class,
    DrsDisabledEvent.class,
    ClusterReconfiguredEvent.class,
    HostMonitoringStateChangedEvent.class,
    VmHealthMonitoringStateChangedEvent.class,
    DrsInvocationFailedEvent.class,
    DrsRecoveredFromFailureEvent.class
})
public class ClusterEvent
    extends Event
{


}
