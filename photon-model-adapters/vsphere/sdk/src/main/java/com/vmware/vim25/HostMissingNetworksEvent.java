
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostMissingNetworksEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostMissingNetworksEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}HostDasEvent"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="ips" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostMissingNetworksEvent", propOrder = {
    "ips"
})
public class HostMissingNetworksEvent
    extends HostDasEvent
{

    protected String ips;

    /**
     * Gets the value of the ips property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getIps() {
        return ips;
    }

    /**
     * Sets the value of the ips property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setIps(String value) {
        this.ips = value;
    }

}
