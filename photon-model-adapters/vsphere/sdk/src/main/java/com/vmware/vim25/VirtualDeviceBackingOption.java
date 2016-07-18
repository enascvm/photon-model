
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualDeviceBackingOption complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualDeviceBackingOption"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="type" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualDeviceBackingOption", propOrder = {
    "type"
})
@XmlSeeAlso({
    VirtualEthernetCardOpaqueNetworkBackingOption.class,
    VirtualEthernetCardDVPortBackingOption.class,
    VirtualPCIPassthroughPluginBackingOption.class,
    VirtualDeviceFileBackingOption.class,
    VirtualDevicePipeBackingOption.class,
    VirtualDeviceURIBackingOption.class,
    VirtualSerialPortThinPrintBackingOption.class,
    VirtualSriovEthernetCardSriovBackingOption.class,
    VirtualDeviceDeviceBackingOption.class,
    VirtualDeviceRemoteDeviceBackingOption.class
})
public class VirtualDeviceBackingOption
    extends DynamicData
{

    @XmlElement(required = true)
    protected String type;

    /**
     * Gets the value of the type property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the value of the type property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setType(String value) {
        this.type = value;
    }

}
