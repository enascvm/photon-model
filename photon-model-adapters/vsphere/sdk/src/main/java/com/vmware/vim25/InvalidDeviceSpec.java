
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for InvalidDeviceSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="InvalidDeviceSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}InvalidVmConfig"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="deviceIndex" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "InvalidDeviceSpec", propOrder = {
    "deviceIndex"
})
@XmlSeeAlso({
    DeviceHotPlugNotSupported.class,
    DeviceNotFound.class,
    DeviceUnsupportedForVmPlatform.class,
    DeviceUnsupportedForVmVersion.class,
    DisallowedDiskModeChange.class,
    InvalidController.class,
    InvalidDeviceBacking.class,
    InvalidDeviceOperation.class,
    MissingController.class
})
public class InvalidDeviceSpec
    extends InvalidVmConfig
{

    protected int deviceIndex;

    /**
     * Gets the value of the deviceIndex property.
     * 
     */
    public int getDeviceIndex() {
        return deviceIndex;
    }

    /**
     * Sets the value of the deviceIndex property.
     * 
     */
    public void setDeviceIndex(int value) {
        this.deviceIndex = value;
    }

}
