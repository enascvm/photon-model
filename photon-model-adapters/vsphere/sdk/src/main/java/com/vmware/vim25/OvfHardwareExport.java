
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for OvfHardwareExport complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="OvfHardwareExport"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}OvfExport"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="device" type="{urn:vim25}VirtualDevice" minOccurs="0"/&gt;
 *         &lt;element name="vmPath" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "OvfHardwareExport", propOrder = {
    "device",
    "vmPath"
})
@XmlSeeAlso({
    OvfConnectedDevice.class,
    OvfUnableToExportDisk.class,
    OvfUnknownDeviceBacking.class,
    OvfUnsupportedDeviceExport.class
})
public class OvfHardwareExport
    extends OvfExport
{

    protected VirtualDevice device;
    @XmlElement(required = true)
    protected String vmPath;

    /**
     * Gets the value of the device property.
     * 
     * @return
     *     possible object is
     *     {@link VirtualDevice }
     *     
     */
    public VirtualDevice getDevice() {
        return device;
    }

    /**
     * Sets the value of the device property.
     * 
     * @param value
     *     allowed object is
     *     {@link VirtualDevice }
     *     
     */
    public void setDevice(VirtualDevice value) {
        this.device = value;
    }

    /**
     * Gets the value of the vmPath property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getVmPath() {
        return vmPath;
    }

    /**
     * Sets the value of the vmPath property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setVmPath(String value) {
        this.vmPath = value;
    }

}
