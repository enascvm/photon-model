
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VmFaultToleranceStateChangedEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VmFaultToleranceStateChangedEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VmEvent"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="oldState" type="{urn:vim25}VirtualMachineFaultToleranceState"/&gt;
 *         &lt;element name="newState" type="{urn:vim25}VirtualMachineFaultToleranceState"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VmFaultToleranceStateChangedEvent", propOrder = {
    "oldState",
    "newState"
})
public class VmFaultToleranceStateChangedEvent
    extends VmEvent
{

    @XmlElement(required = true)
    @XmlSchemaType(name = "string")
    protected VirtualMachineFaultToleranceState oldState;
    @XmlElement(required = true)
    @XmlSchemaType(name = "string")
    protected VirtualMachineFaultToleranceState newState;

    /**
     * Gets the value of the oldState property.
     * 
     * @return
     *     possible object is
     *     {@link VirtualMachineFaultToleranceState }
     *     
     */
    public VirtualMachineFaultToleranceState getOldState() {
        return oldState;
    }

    /**
     * Sets the value of the oldState property.
     * 
     * @param value
     *     allowed object is
     *     {@link VirtualMachineFaultToleranceState }
     *     
     */
    public void setOldState(VirtualMachineFaultToleranceState value) {
        this.oldState = value;
    }

    /**
     * Gets the value of the newState property.
     * 
     * @return
     *     possible object is
     *     {@link VirtualMachineFaultToleranceState }
     *     
     */
    public VirtualMachineFaultToleranceState getNewState() {
        return newState;
    }

    /**
     * Sets the value of the newState property.
     * 
     * @param value
     *     allowed object is
     *     {@link VirtualMachineFaultToleranceState }
     *     
     */
    public void setNewState(VirtualMachineFaultToleranceState value) {
        this.newState = value;
    }

}
