
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualUSBXHCIController complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualUSBXHCIController"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VirtualController"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="autoConnectDevices" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualUSBXHCIController", propOrder = {
    "autoConnectDevices"
})
public class VirtualUSBXHCIController
    extends VirtualController
{

    protected Boolean autoConnectDevices;

    /**
     * Gets the value of the autoConnectDevices property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isAutoConnectDevices() {
        return autoConnectDevices;
    }

    /**
     * Sets the value of the autoConnectDevices property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setAutoConnectDevices(Boolean value) {
        this.autoConnectDevices = value;
    }

}
