
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for GuestRegKeyNameSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="GuestRegKeyNameSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="registryPath" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="wowBitness" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "GuestRegKeyNameSpec", propOrder = {
    "registryPath",
    "wowBitness"
})
public class GuestRegKeyNameSpec
    extends DynamicData
{

    @XmlElement(required = true)
    protected String registryPath;
    @XmlElement(required = true)
    protected String wowBitness;

    /**
     * Gets the value of the registryPath property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRegistryPath() {
        return registryPath;
    }

    /**
     * Sets the value of the registryPath property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRegistryPath(String value) {
        this.registryPath = value;
    }

    /**
     * Gets the value of the wowBitness property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getWowBitness() {
        return wowBitness;
    }

    /**
     * Sets the value of the wowBitness property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setWowBitness(String value) {
        this.wowBitness = value;
    }

}
