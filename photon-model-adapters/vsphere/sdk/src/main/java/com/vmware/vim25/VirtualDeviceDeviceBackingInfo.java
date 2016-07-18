
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualDeviceDeviceBackingInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualDeviceDeviceBackingInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VirtualDeviceBackingInfo"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="deviceName" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="useAutoDetect" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualDeviceDeviceBackingInfo", propOrder = {
    "deviceName",
    "useAutoDetect"
})
@XmlSeeAlso({
    VirtualCdromPassthroughBackingInfo.class,
    VirtualCdromAtapiBackingInfo.class,
    VirtualDiskRawDiskVer2BackingInfo.class,
    VirtualEthernetCardNetworkBackingInfo.class,
    VirtualEthernetCardLegacyNetworkBackingInfo.class,
    VirtualFloppyDeviceBackingInfo.class,
    VirtualPCIPassthroughDeviceBackingInfo.class,
    VirtualParallelPortDeviceBackingInfo.class,
    VirtualPointingDeviceDeviceBackingInfo.class,
    VirtualSCSIPassthroughDeviceBackingInfo.class,
    VirtualSerialPortDeviceBackingInfo.class,
    VirtualSoundCardDeviceBackingInfo.class,
    VirtualUSBUSBBackingInfo.class,
    VirtualUSBRemoteHostBackingInfo.class
})
public class VirtualDeviceDeviceBackingInfo
    extends VirtualDeviceBackingInfo
{

    @XmlElement(required = true)
    protected String deviceName;
    protected Boolean useAutoDetect;

    /**
     * Gets the value of the deviceName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDeviceName() {
        return deviceName;
    }

    /**
     * Sets the value of the deviceName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDeviceName(String value) {
        this.deviceName = value;
    }

    /**
     * Gets the value of the useAutoDetect property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isUseAutoDetect() {
        return useAutoDetect;
    }

    /**
     * Sets the value of the useAutoDetect property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setUseAutoDetect(Boolean value) {
        this.useAutoDetect = value;
    }

}
