
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostFirewallDefaultPolicy complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostFirewallDefaultPolicy"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="incomingBlocked" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *         &lt;element name="outgoingBlocked" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostFirewallDefaultPolicy", propOrder = {
    "incomingBlocked",
    "outgoingBlocked"
})
public class HostFirewallDefaultPolicy
    extends DynamicData
{

    protected Boolean incomingBlocked;
    protected Boolean outgoingBlocked;

    /**
     * Gets the value of the incomingBlocked property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isIncomingBlocked() {
        return incomingBlocked;
    }

    /**
     * Sets the value of the incomingBlocked property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setIncomingBlocked(Boolean value) {
        this.incomingBlocked = value;
    }

    /**
     * Gets the value of the outgoingBlocked property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isOutgoingBlocked() {
        return outgoingBlocked;
    }

    /**
     * Sets the value of the outgoingBlocked property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setOutgoingBlocked(Boolean value) {
        this.outgoingBlocked = value;
    }

}
