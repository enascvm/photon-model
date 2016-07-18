
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for CannotAccessNetwork complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="CannotAccessNetwork"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}CannotAccessVmDevice"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="network" type="{urn:vim25}ManagedObjectReference" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "CannotAccessNetwork", propOrder = {
    "network"
})
@XmlSeeAlso({
    DestinationSwitchFull.class,
    LegacyNetworkInterfaceInUse.class,
    VMOnConflictDVPort.class,
    VMOnVirtualIntranet.class
})
public class CannotAccessNetwork
    extends CannotAccessVmDevice
{

    protected ManagedObjectReference network;

    /**
     * Gets the value of the network property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getNetwork() {
        return network;
    }

    /**
     * Sets the value of the network property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setNetwork(ManagedObjectReference value) {
        this.network = value;
    }

}
