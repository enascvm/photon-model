
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DeviceNotSupported complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DeviceNotSupported"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VirtualHardwareCompatibilityIssue"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="device" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="reason" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DeviceNotSupported", propOrder = {
    "device",
    "reason"
})
@XmlSeeAlso({
    DeviceControllerNotSupported.class,
    DigestNotSupported.class,
    FileBackedPortNotSupported.class,
    MultiWriterNotSupported.class,
    NonPersistentDisksNotSupported.class,
    RDMNotSupported.class,
    RawDiskNotSupported.class,
    RemoteDeviceNotSupported.class,
    SharedBusControllerNotSupported.class,
    VMINotSupported.class,
    DeviceBackingNotSupported.class,
    VirtualDiskModeNotSupported.class,
    VirtualEthernetCardNotSupported.class
})
public class DeviceNotSupported
    extends VirtualHardwareCompatibilityIssue
{

    @XmlElement(required = true)
    protected String device;
    protected String reason;

    /**
     * Gets the value of the device property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDevice() {
        return device;
    }

    /**
     * Sets the value of the device property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDevice(String value) {
        this.device = value;
    }

    /**
     * Gets the value of the reason property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getReason() {
        return reason;
    }

    /**
     * Sets the value of the reason property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setReason(String value) {
        this.reason = value;
    }

}
