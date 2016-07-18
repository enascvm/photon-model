
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualUSBControllerPciBusSlotInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualUSBControllerPciBusSlotInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VirtualDevicePciBusSlotInfo"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="ehciPciSlotNumber" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualUSBControllerPciBusSlotInfo", propOrder = {
    "ehciPciSlotNumber"
})
public class VirtualUSBControllerPciBusSlotInfo
    extends VirtualDevicePciBusSlotInfo
{

    protected Integer ehciPciSlotNumber;

    /**
     * Gets the value of the ehciPciSlotNumber property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getEhciPciSlotNumber() {
        return ehciPciSlotNumber;
    }

    /**
     * Sets the value of the ehciPciSlotNumber property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setEhciPciSlotNumber(Integer value) {
        this.ehciPciSlotNumber = value;
    }

}
