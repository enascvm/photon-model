
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for SoftwarePackageCapability complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="SoftwarePackageCapability"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="liveInstallAllowed" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *         &lt;element name="liveRemoveAllowed" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *         &lt;element name="statelessReady" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *         &lt;element name="overlay" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "SoftwarePackageCapability", propOrder = {
    "liveInstallAllowed",
    "liveRemoveAllowed",
    "statelessReady",
    "overlay"
})
public class SoftwarePackageCapability
    extends DynamicData
{

    protected Boolean liveInstallAllowed;
    protected Boolean liveRemoveAllowed;
    protected Boolean statelessReady;
    protected Boolean overlay;

    /**
     * Gets the value of the liveInstallAllowed property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isLiveInstallAllowed() {
        return liveInstallAllowed;
    }

    /**
     * Sets the value of the liveInstallAllowed property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setLiveInstallAllowed(Boolean value) {
        this.liveInstallAllowed = value;
    }

    /**
     * Gets the value of the liveRemoveAllowed property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isLiveRemoveAllowed() {
        return liveRemoveAllowed;
    }

    /**
     * Sets the value of the liveRemoveAllowed property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setLiveRemoveAllowed(Boolean value) {
        this.liveRemoveAllowed = value;
    }

    /**
     * Gets the value of the statelessReady property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isStatelessReady() {
        return statelessReady;
    }

    /**
     * Sets the value of the statelessReady property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setStatelessReady(Boolean value) {
        this.statelessReady = value;
    }

    /**
     * Gets the value of the overlay property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isOverlay() {
        return overlay;
    }

    /**
     * Sets the value of the overlay property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setOverlay(Boolean value) {
        this.overlay = value;
    }

}
