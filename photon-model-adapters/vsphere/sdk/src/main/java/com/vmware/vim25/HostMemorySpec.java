
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostMemorySpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostMemorySpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="serviceConsoleReservation" type="{http://www.w3.org/2001/XMLSchema}long" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostMemorySpec", propOrder = {
    "serviceConsoleReservation"
})
public class HostMemorySpec
    extends DynamicData
{

    protected Long serviceConsoleReservation;

    /**
     * Gets the value of the serviceConsoleReservation property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getServiceConsoleReservation() {
        return serviceConsoleReservation;
    }

    /**
     * Sets the value of the serviceConsoleReservation property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setServiceConsoleReservation(Long value) {
        this.serviceConsoleReservation = value;
    }

}
