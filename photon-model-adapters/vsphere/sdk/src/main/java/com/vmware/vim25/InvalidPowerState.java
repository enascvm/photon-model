
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for InvalidPowerState complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="InvalidPowerState"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}InvalidState"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="requestedState" type="{urn:vim25}VirtualMachinePowerState" minOccurs="0"/&gt;
 *         &lt;element name="existingState" type="{urn:vim25}VirtualMachinePowerState"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "InvalidPowerState", propOrder = {
    "requestedState",
    "existingState"
})
public class InvalidPowerState
    extends InvalidState
{

    @XmlSchemaType(name = "string")
    protected VirtualMachinePowerState requestedState;
    @XmlElement(required = true)
    @XmlSchemaType(name = "string")
    protected VirtualMachinePowerState existingState;

    /**
     * Gets the value of the requestedState property.
     * 
     * @return
     *     possible object is
     *     {@link VirtualMachinePowerState }
     *     
     */
    public VirtualMachinePowerState getRequestedState() {
        return requestedState;
    }

    /**
     * Sets the value of the requestedState property.
     * 
     * @param value
     *     allowed object is
     *     {@link VirtualMachinePowerState }
     *     
     */
    public void setRequestedState(VirtualMachinePowerState value) {
        this.requestedState = value;
    }

    /**
     * Gets the value of the existingState property.
     * 
     * @return
     *     possible object is
     *     {@link VirtualMachinePowerState }
     *     
     */
    public VirtualMachinePowerState getExistingState() {
        return existingState;
    }

    /**
     * Sets the value of the existingState property.
     * 
     * @param value
     *     allowed object is
     *     {@link VirtualMachinePowerState }
     *     
     */
    public void setExistingState(VirtualMachinePowerState value) {
        this.existingState = value;
    }

}
