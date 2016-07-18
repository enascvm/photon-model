
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostDasEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostDasEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}HostEvent"&gt;
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
@XmlType(name = "HostDasEvent")
@XmlSeeAlso({
    HostPrimaryAgentNotShortNameEvent.class,
    HostNotInClusterEvent.class,
    HostIsolationIpPingFailedEvent.class,
    HostShortNameInconsistentEvent.class,
    HostNoRedundantManagementNetworkEvent.class,
    HostNoAvailableNetworksEvent.class,
    HostExtraNetworksEvent.class,
    HostNoHAEnabledPortGroupsEvent.class,
    HostMissingNetworksEvent.class
})
public class HostDasEvent
    extends HostEvent
{


}
