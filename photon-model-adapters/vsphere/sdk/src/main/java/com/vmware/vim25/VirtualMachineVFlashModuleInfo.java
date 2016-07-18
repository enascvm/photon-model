
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualMachineVFlashModuleInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualMachineVFlashModuleInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VirtualMachineTargetInfo"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="vFlashModule" type="{urn:vim25}HostVFlashManagerVFlashCacheConfigInfoVFlashModuleConfigOption"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualMachineVFlashModuleInfo", propOrder = {
    "vFlashModule"
})
public class VirtualMachineVFlashModuleInfo
    extends VirtualMachineTargetInfo
{

    @XmlElement(required = true)
    protected HostVFlashManagerVFlashCacheConfigInfoVFlashModuleConfigOption vFlashModule;

    /**
     * Gets the value of the vFlashModule property.
     * 
     * @return
     *     possible object is
     *     {@link HostVFlashManagerVFlashCacheConfigInfoVFlashModuleConfigOption }
     *     
     */
    public HostVFlashManagerVFlashCacheConfigInfoVFlashModuleConfigOption getVFlashModule() {
        return vFlashModule;
    }

    /**
     * Sets the value of the vFlashModule property.
     * 
     * @param value
     *     allowed object is
     *     {@link HostVFlashManagerVFlashCacheConfigInfoVFlashModuleConfigOption }
     *     
     */
    public void setVFlashModule(HostVFlashManagerVFlashCacheConfigInfoVFlashModuleConfigOption value) {
        this.vFlashModule = value;
    }

}
