
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualPCIPassthroughVmiopBackingInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualPCIPassthroughVmiopBackingInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VirtualPCIPassthroughPluginBackingInfo"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="vgpu" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualPCIPassthroughVmiopBackingInfo", propOrder = {
    "vgpu"
})
public class VirtualPCIPassthroughVmiopBackingInfo
    extends VirtualPCIPassthroughPluginBackingInfo
{

    protected String vgpu;

    /**
     * Gets the value of the vgpu property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getVgpu() {
        return vgpu;
    }

    /**
     * Sets the value of the vgpu property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setVgpu(String value) {
        this.vgpu = value;
    }

}
