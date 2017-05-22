
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualMachineSriovNetworkDevicePoolInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualMachineSriovNetworkDevicePoolInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VirtualMachineSriovDevicePoolInfo"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="switchKey" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="switchUuid" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualMachineSriovNetworkDevicePoolInfo", propOrder = {
    "switchKey",
    "switchUuid"
})
public class VirtualMachineSriovNetworkDevicePoolInfo
    extends VirtualMachineSriovDevicePoolInfo
{

    protected String switchKey;
    protected String switchUuid;

    /**
     * Gets the value of the switchKey property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSwitchKey() {
        return switchKey;
    }

    /**
     * Sets the value of the switchKey property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSwitchKey(String value) {
        this.switchKey = value;
    }

    /**
     * Gets the value of the switchUuid property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSwitchUuid() {
        return switchUuid;
    }

    /**
     * Sets the value of the switchUuid property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSwitchUuid(String value) {
        this.switchUuid = value;
    }

}
