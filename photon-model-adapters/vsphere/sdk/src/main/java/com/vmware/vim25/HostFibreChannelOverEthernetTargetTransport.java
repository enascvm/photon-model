
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostFibreChannelOverEthernetTargetTransport complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostFibreChannelOverEthernetTargetTransport"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}HostFibreChannelTargetTransport"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="vnportMac" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="fcfMac" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="vlanId" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostFibreChannelOverEthernetTargetTransport", propOrder = {
    "vnportMac",
    "fcfMac",
    "vlanId"
})
public class HostFibreChannelOverEthernetTargetTransport
    extends HostFibreChannelTargetTransport
{

    @XmlElement(required = true)
    protected String vnportMac;
    @XmlElement(required = true)
    protected String fcfMac;
    protected int vlanId;

    /**
     * Gets the value of the vnportMac property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getVnportMac() {
        return vnportMac;
    }

    /**
     * Sets the value of the vnportMac property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setVnportMac(String value) {
        this.vnportMac = value;
    }

    /**
     * Gets the value of the fcfMac property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getFcfMac() {
        return fcfMac;
    }

    /**
     * Sets the value of the fcfMac property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setFcfMac(String value) {
        this.fcfMac = value;
    }

    /**
     * Gets the value of the vlanId property.
     * 
     */
    public int getVlanId() {
        return vlanId;
    }

    /**
     * Sets the value of the vlanId property.
     * 
     */
    public void setVlanId(int value) {
        this.vlanId = value;
    }

}
