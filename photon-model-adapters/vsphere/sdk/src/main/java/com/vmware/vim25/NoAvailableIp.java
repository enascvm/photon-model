
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for NoAvailableIp complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="NoAvailableIp"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VAppPropertyFault"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="network" type="{urn:vim25}ManagedObjectReference"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "NoAvailableIp", propOrder = {
    "network"
})
public class NoAvailableIp
    extends VAppPropertyFault
{

    @XmlElement(required = true)
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
