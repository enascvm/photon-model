
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualDevice complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualDevice"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="key" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *         &lt;element name="deviceInfo" type="{urn:vim25}Description" minOccurs="0"/&gt;
 *         &lt;element name="backing" type="{urn:vim25}VirtualDeviceBackingInfo" minOccurs="0"/&gt;
 *         &lt;element name="connectable" type="{urn:vim25}VirtualDeviceConnectInfo" minOccurs="0"/&gt;
 *         &lt;element name="slotInfo" type="{urn:vim25}VirtualDeviceBusSlotInfo" minOccurs="0"/&gt;
 *         &lt;element name="controllerKey" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/&gt;
 *         &lt;element name="unitNumber" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualDevice", propOrder = {
    "key",
    "deviceInfo",
    "backing",
    "connectable",
    "slotInfo",
    "controllerKey",
    "unitNumber"
})
@XmlSeeAlso({
    VirtualCdrom.class,
    VirtualDisk.class,
    VirtualFloppy.class,
    VirtualKeyboard.class,
    VirtualPCIPassthrough.class,
    VirtualParallelPort.class,
    VirtualPointingDevice.class,
    VirtualSCSIPassthrough.class,
    VirtualSerialPort.class,
    VirtualSoundCard.class,
    VirtualUSB.class,
    VirtualController.class,
    VirtualMachineVMCIDevice.class,
    VirtualMachineVMIROM.class,
    VirtualMachineVideoCard.class,
    VirtualEthernetCard.class
})
public class VirtualDevice
    extends DynamicData
{

    protected int key;
    protected Description deviceInfo;
    protected VirtualDeviceBackingInfo backing;
    protected VirtualDeviceConnectInfo connectable;
    protected VirtualDeviceBusSlotInfo slotInfo;
    protected Integer controllerKey;
    protected Integer unitNumber;

    /**
     * Gets the value of the key property.
     * 
     */
    public int getKey() {
        return key;
    }

    /**
     * Sets the value of the key property.
     * 
     */
    public void setKey(int value) {
        this.key = value;
    }

    /**
     * Gets the value of the deviceInfo property.
     * 
     * @return
     *     possible object is
     *     {@link Description }
     *     
     */
    public Description getDeviceInfo() {
        return deviceInfo;
    }

    /**
     * Sets the value of the deviceInfo property.
     * 
     * @param value
     *     allowed object is
     *     {@link Description }
     *     
     */
    public void setDeviceInfo(Description value) {
        this.deviceInfo = value;
    }

    /**
     * Gets the value of the backing property.
     * 
     * @return
     *     possible object is
     *     {@link VirtualDeviceBackingInfo }
     *     
     */
    public VirtualDeviceBackingInfo getBacking() {
        return backing;
    }

    /**
     * Sets the value of the backing property.
     * 
     * @param value
     *     allowed object is
     *     {@link VirtualDeviceBackingInfo }
     *     
     */
    public void setBacking(VirtualDeviceBackingInfo value) {
        this.backing = value;
    }

    /**
     * Gets the value of the connectable property.
     * 
     * @return
     *     possible object is
     *     {@link VirtualDeviceConnectInfo }
     *     
     */
    public VirtualDeviceConnectInfo getConnectable() {
        return connectable;
    }

    /**
     * Sets the value of the connectable property.
     * 
     * @param value
     *     allowed object is
     *     {@link VirtualDeviceConnectInfo }
     *     
     */
    public void setConnectable(VirtualDeviceConnectInfo value) {
        this.connectable = value;
    }

    /**
     * Gets the value of the slotInfo property.
     * 
     * @return
     *     possible object is
     *     {@link VirtualDeviceBusSlotInfo }
     *     
     */
    public VirtualDeviceBusSlotInfo getSlotInfo() {
        return slotInfo;
    }

    /**
     * Sets the value of the slotInfo property.
     * 
     * @param value
     *     allowed object is
     *     {@link VirtualDeviceBusSlotInfo }
     *     
     */
    public void setSlotInfo(VirtualDeviceBusSlotInfo value) {
        this.slotInfo = value;
    }

    /**
     * Gets the value of the controllerKey property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getControllerKey() {
        return controllerKey;
    }

    /**
     * Sets the value of the controllerKey property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setControllerKey(Integer value) {
        this.controllerKey = value;
    }

    /**
     * Gets the value of the unitNumber property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getUnitNumber() {
        return unitNumber;
    }

    /**
     * Sets the value of the unitNumber property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setUnitNumber(Integer value) {
        this.unitNumber = value;
    }

}
