
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostTpmEventLogEntry complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostTpmEventLogEntry"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="pcrIndex" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *         &lt;element name="eventDetails" type="{urn:vim25}HostTpmEventDetails"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostTpmEventLogEntry", propOrder = {
    "pcrIndex",
    "eventDetails"
})
public class HostTpmEventLogEntry
    extends DynamicData
{

    protected int pcrIndex;
    @XmlElement(required = true)
    protected HostTpmEventDetails eventDetails;

    /**
     * Gets the value of the pcrIndex property.
     * 
     */
    public int getPcrIndex() {
        return pcrIndex;
    }

    /**
     * Sets the value of the pcrIndex property.
     * 
     */
    public void setPcrIndex(int value) {
        this.pcrIndex = value;
    }

    /**
     * Gets the value of the eventDetails property.
     * 
     * @return
     *     possible object is
     *     {@link HostTpmEventDetails }
     *     
     */
    public HostTpmEventDetails getEventDetails() {
        return eventDetails;
    }

    /**
     * Sets the value of the eventDetails property.
     * 
     * @param value
     *     allowed object is
     *     {@link HostTpmEventDetails }
     *     
     */
    public void setEventDetails(HostTpmEventDetails value) {
        this.eventDetails = value;
    }

}
