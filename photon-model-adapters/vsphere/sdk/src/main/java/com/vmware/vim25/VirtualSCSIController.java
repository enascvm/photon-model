
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualSCSIController complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualSCSIController"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VirtualController"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="hotAddRemove" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *         &lt;element name="sharedBus" type="{urn:vim25}VirtualSCSISharing"/&gt;
 *         &lt;element name="scsiCtlrUnitNumber" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualSCSIController", propOrder = {
    "hotAddRemove",
    "sharedBus",
    "scsiCtlrUnitNumber"
})
@XmlSeeAlso({
    ParaVirtualSCSIController.class,
    VirtualBusLogicController.class,
    VirtualLsiLogicController.class,
    VirtualLsiLogicSASController.class
})
public class VirtualSCSIController
    extends VirtualController
{

    protected Boolean hotAddRemove;
    @XmlElement(required = true)
    @XmlSchemaType(name = "string")
    protected VirtualSCSISharing sharedBus;
    protected Integer scsiCtlrUnitNumber;

    /**
     * Gets the value of the hotAddRemove property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isHotAddRemove() {
        return hotAddRemove;
    }

    /**
     * Sets the value of the hotAddRemove property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setHotAddRemove(Boolean value) {
        this.hotAddRemove = value;
    }

    /**
     * Gets the value of the sharedBus property.
     * 
     * @return
     *     possible object is
     *     {@link VirtualSCSISharing }
     *     
     */
    public VirtualSCSISharing getSharedBus() {
        return sharedBus;
    }

    /**
     * Sets the value of the sharedBus property.
     * 
     * @param value
     *     allowed object is
     *     {@link VirtualSCSISharing }
     *     
     */
    public void setSharedBus(VirtualSCSISharing value) {
        this.sharedBus = value;
    }

    /**
     * Gets the value of the scsiCtlrUnitNumber property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getScsiCtlrUnitNumber() {
        return scsiCtlrUnitNumber;
    }

    /**
     * Sets the value of the scsiCtlrUnitNumber property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setScsiCtlrUnitNumber(Integer value) {
        this.scsiCtlrUnitNumber = value;
    }

}
