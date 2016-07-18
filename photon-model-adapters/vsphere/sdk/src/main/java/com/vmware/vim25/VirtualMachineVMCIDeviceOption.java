
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualMachineVMCIDeviceOption complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualMachineVMCIDeviceOption"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VirtualDeviceOption"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="allowUnrestrictedCommunication" type="{urn:vim25}BoolOption"/&gt;
 *         &lt;element name="filterSpecOption" type="{urn:vim25}VirtualMachineVMCIDeviceOptionFilterSpecOption" minOccurs="0"/&gt;
 *         &lt;element name="filterSupported" type="{urn:vim25}BoolOption" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualMachineVMCIDeviceOption", propOrder = {
    "allowUnrestrictedCommunication",
    "filterSpecOption",
    "filterSupported"
})
public class VirtualMachineVMCIDeviceOption
    extends VirtualDeviceOption
{

    @XmlElement(required = true)
    protected BoolOption allowUnrestrictedCommunication;
    protected VirtualMachineVMCIDeviceOptionFilterSpecOption filterSpecOption;
    protected BoolOption filterSupported;

    /**
     * Gets the value of the allowUnrestrictedCommunication property.
     * 
     * @return
     *     possible object is
     *     {@link BoolOption }
     *     
     */
    public BoolOption getAllowUnrestrictedCommunication() {
        return allowUnrestrictedCommunication;
    }

    /**
     * Sets the value of the allowUnrestrictedCommunication property.
     * 
     * @param value
     *     allowed object is
     *     {@link BoolOption }
     *     
     */
    public void setAllowUnrestrictedCommunication(BoolOption value) {
        this.allowUnrestrictedCommunication = value;
    }

    /**
     * Gets the value of the filterSpecOption property.
     * 
     * @return
     *     possible object is
     *     {@link VirtualMachineVMCIDeviceOptionFilterSpecOption }
     *     
     */
    public VirtualMachineVMCIDeviceOptionFilterSpecOption getFilterSpecOption() {
        return filterSpecOption;
    }

    /**
     * Sets the value of the filterSpecOption property.
     * 
     * @param value
     *     allowed object is
     *     {@link VirtualMachineVMCIDeviceOptionFilterSpecOption }
     *     
     */
    public void setFilterSpecOption(VirtualMachineVMCIDeviceOptionFilterSpecOption value) {
        this.filterSpecOption = value;
    }

    /**
     * Gets the value of the filterSupported property.
     * 
     * @return
     *     possible object is
     *     {@link BoolOption }
     *     
     */
    public BoolOption getFilterSupported() {
        return filterSupported;
    }

    /**
     * Sets the value of the filterSupported property.
     * 
     * @param value
     *     allowed object is
     *     {@link BoolOption }
     *     
     */
    public void setFilterSupported(BoolOption value) {
        this.filterSupported = value;
    }

}
