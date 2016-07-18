
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VrpResourceAllocationInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VrpResourceAllocationInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}ResourceAllocationInfo"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="reservationLimit" type="{http://www.w3.org/2001/XMLSchema}long" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VrpResourceAllocationInfo", propOrder = {
    "reservationLimit"
})
public class VrpResourceAllocationInfo
    extends ResourceAllocationInfo
{

    protected Long reservationLimit;

    /**
     * Gets the value of the reservationLimit property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getReservationLimit() {
        return reservationLimit;
    }

    /**
     * Sets the value of the reservationLimit property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setReservationLimit(Long value) {
        this.reservationLimit = value;
    }

}
