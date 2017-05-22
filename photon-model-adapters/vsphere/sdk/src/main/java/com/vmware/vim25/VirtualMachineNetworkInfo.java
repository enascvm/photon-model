
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualMachineNetworkInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualMachineNetworkInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VirtualMachineTargetInfo"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="network" type="{urn:vim25}NetworkSummary"/&gt;
 *         &lt;element name="vswitch" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualMachineNetworkInfo", propOrder = {
    "network",
    "vswitch"
})
public class VirtualMachineNetworkInfo
    extends VirtualMachineTargetInfo
{

    @XmlElement(required = true)
    protected NetworkSummary network;
    protected String vswitch;

    /**
     * Gets the value of the network property.
     * 
     * @return
     *     possible object is
     *     {@link NetworkSummary }
     *     
     */
    public NetworkSummary getNetwork() {
        return network;
    }

    /**
     * Sets the value of the network property.
     * 
     * @param value
     *     allowed object is
     *     {@link NetworkSummary }
     *     
     */
    public void setNetwork(NetworkSummary value) {
        this.network = value;
    }

    /**
     * Gets the value of the vswitch property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getVswitch() {
        return vswitch;
    }

    /**
     * Sets the value of the vswitch property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setVswitch(String value) {
        this.vswitch = value;
    }

}
