
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualPointingDeviceDeviceBackingInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualPointingDeviceDeviceBackingInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VirtualDeviceDeviceBackingInfo"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="hostPointingDevice" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualPointingDeviceDeviceBackingInfo", propOrder = {
    "hostPointingDevice"
})
public class VirtualPointingDeviceDeviceBackingInfo
    extends VirtualDeviceDeviceBackingInfo
{

    @XmlElement(required = true)
    protected String hostPointingDevice;

    /**
     * Gets the value of the hostPointingDevice property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getHostPointingDevice() {
        return hostPointingDevice;
    }

    /**
     * Sets the value of the hostPointingDevice property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setHostPointingDevice(String value) {
        this.hostPointingDevice = value;
    }

}
