
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ConfigureLicenseSourceRequestType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ConfigureLicenseSourceRequestType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="_this" type="{urn:vim25}ManagedObjectReference"/&gt;
 *         &lt;element name="host" type="{urn:vim25}ManagedObjectReference" minOccurs="0"/&gt;
 *         &lt;element name="licenseSource" type="{urn:vim25}LicenseSource"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ConfigureLicenseSourceRequestType", propOrder = {
    "_this",
    "host",
    "licenseSource"
})
public class ConfigureLicenseSourceRequestType {

    @XmlElement(required = true)
    protected ManagedObjectReference _this;
    protected ManagedObjectReference host;
    @XmlElement(required = true)
    protected LicenseSource licenseSource;

    /**
     * Gets the value of the this property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getThis() {
        return _this;
    }

    /**
     * Sets the value of the this property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setThis(ManagedObjectReference value) {
        this._this = value;
    }

    /**
     * Gets the value of the host property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getHost() {
        return host;
    }

    /**
     * Sets the value of the host property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setHost(ManagedObjectReference value) {
        this.host = value;
    }

    /**
     * Gets the value of the licenseSource property.
     * 
     * @return
     *     possible object is
     *     {@link LicenseSource }
     *     
     */
    public LicenseSource getLicenseSource() {
        return licenseSource;
    }

    /**
     * Sets the value of the licenseSource property.
     * 
     * @param value
     *     allowed object is
     *     {@link LicenseSource }
     *     
     */
    public void setLicenseSource(LicenseSource value) {
        this.licenseSource = value;
    }

}
