
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualDiskOption complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualDiskOption"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VirtualDeviceOption"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="capacityInKB" type="{urn:vim25}LongOption"/&gt;
 *         &lt;element name="ioAllocationOption" type="{urn:vim25}StorageIOAllocationOption" minOccurs="0"/&gt;
 *         &lt;element name="vFlashCacheConfigOption" type="{urn:vim25}VirtualDiskOptionVFlashCacheConfigOption" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualDiskOption", propOrder = {
    "capacityInKB",
    "ioAllocationOption",
    "vFlashCacheConfigOption"
})
public class VirtualDiskOption
    extends VirtualDeviceOption
{

    @XmlElement(required = true)
    protected LongOption capacityInKB;
    protected StorageIOAllocationOption ioAllocationOption;
    protected VirtualDiskOptionVFlashCacheConfigOption vFlashCacheConfigOption;

    /**
     * Gets the value of the capacityInKB property.
     * 
     * @return
     *     possible object is
     *     {@link LongOption }
     *     
     */
    public LongOption getCapacityInKB() {
        return capacityInKB;
    }

    /**
     * Sets the value of the capacityInKB property.
     * 
     * @param value
     *     allowed object is
     *     {@link LongOption }
     *     
     */
    public void setCapacityInKB(LongOption value) {
        this.capacityInKB = value;
    }

    /**
     * Gets the value of the ioAllocationOption property.
     * 
     * @return
     *     possible object is
     *     {@link StorageIOAllocationOption }
     *     
     */
    public StorageIOAllocationOption getIoAllocationOption() {
        return ioAllocationOption;
    }

    /**
     * Sets the value of the ioAllocationOption property.
     * 
     * @param value
     *     allowed object is
     *     {@link StorageIOAllocationOption }
     *     
     */
    public void setIoAllocationOption(StorageIOAllocationOption value) {
        this.ioAllocationOption = value;
    }

    /**
     * Gets the value of the vFlashCacheConfigOption property.
     * 
     * @return
     *     possible object is
     *     {@link VirtualDiskOptionVFlashCacheConfigOption }
     *     
     */
    public VirtualDiskOptionVFlashCacheConfigOption getVFlashCacheConfigOption() {
        return vFlashCacheConfigOption;
    }

    /**
     * Sets the value of the vFlashCacheConfigOption property.
     * 
     * @param value
     *     allowed object is
     *     {@link VirtualDiskOptionVFlashCacheConfigOption }
     *     
     */
    public void setVFlashCacheConfigOption(VirtualDiskOptionVFlashCacheConfigOption value) {
        this.vFlashCacheConfigOption = value;
    }

}
