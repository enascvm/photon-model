
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DuplicateIpDetectedEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DuplicateIpDetectedEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}HostEvent"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="duplicateIP" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="macAddress" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DuplicateIpDetectedEvent", propOrder = {
    "duplicateIP",
    "macAddress"
})
public class DuplicateIpDetectedEvent
    extends HostEvent
{

    @XmlElement(required = true)
    protected String duplicateIP;
    @XmlElement(required = true)
    protected String macAddress;

    /**
     * Gets the value of the duplicateIP property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDuplicateIP() {
        return duplicateIP;
    }

    /**
     * Sets the value of the duplicateIP property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDuplicateIP(String value) {
        this.duplicateIP = value;
    }

    /**
     * Gets the value of the macAddress property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getMacAddress() {
        return macAddress;
    }

    /**
     * Sets the value of the macAddress property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setMacAddress(String value) {
        this.macAddress = value;
    }

}
