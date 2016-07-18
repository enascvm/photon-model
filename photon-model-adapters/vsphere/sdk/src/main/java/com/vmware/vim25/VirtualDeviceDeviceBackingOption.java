
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualDeviceDeviceBackingOption complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualDeviceDeviceBackingOption"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VirtualDeviceBackingOption"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="autoDetectAvailable" type="{urn:vim25}BoolOption"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualDeviceDeviceBackingOption", propOrder = {
    "autoDetectAvailable"
})
@XmlSeeAlso({
    VirtualCdromPassthroughBackingOption.class,
    VirtualCdromAtapiBackingOption.class,
    VirtualCdromRemoteAtapiBackingOption.class,
    VirtualDiskRawDiskVer2BackingOption.class,
    VirtualDiskRawDiskMappingVer1BackingOption.class,
    VirtualEthernetCardNetworkBackingOption.class,
    VirtualEthernetCardLegacyNetworkBackingOption.class,
    VirtualFloppyDeviceBackingOption.class,
    VirtualPCIPassthroughDeviceBackingOption.class,
    VirtualParallelPortDeviceBackingOption.class,
    VirtualPointingDeviceBackingOption.class,
    VirtualSCSIPassthroughDeviceBackingOption.class,
    VirtualSerialPortDeviceBackingOption.class,
    VirtualSoundCardDeviceBackingOption.class,
    VirtualUSBUSBBackingOption.class,
    VirtualUSBRemoteHostBackingOption.class
})
public class VirtualDeviceDeviceBackingOption
    extends VirtualDeviceBackingOption
{

    @XmlElement(required = true)
    protected BoolOption autoDetectAvailable;

    /**
     * Gets the value of the autoDetectAvailable property.
     * 
     * @return
     *     possible object is
     *     {@link BoolOption }
     *     
     */
    public BoolOption getAutoDetectAvailable() {
        return autoDetectAvailable;
    }

    /**
     * Sets the value of the autoDetectAvailable property.
     * 
     * @param value
     *     allowed object is
     *     {@link BoolOption }
     *     
     */
    public void setAutoDetectAvailable(BoolOption value) {
        this.autoDetectAvailable = value;
    }

}
