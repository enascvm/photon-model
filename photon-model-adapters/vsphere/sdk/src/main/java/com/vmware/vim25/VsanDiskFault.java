
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VsanDiskFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VsanDiskFault"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VsanFault"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="device" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VsanDiskFault", propOrder = {
    "device"
})
@XmlSeeAlso({
    DiskHasPartitions.class,
    DiskIsLastRemainingNonSSD.class,
    DiskIsNonLocal.class,
    DiskIsUSB.class,
    DiskTooSmall.class,
    DuplicateDisks.class,
    InsufficientDisks.class,
    VsanIncompatibleDiskMapping.class
})
public class VsanDiskFault
    extends VsanFault
{

    protected String device;

    /**
     * Gets the value of the device property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDevice() {
        return device;
    }

    /**
     * Sets the value of the device property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDevice(String value) {
        this.device = value;
    }

}
