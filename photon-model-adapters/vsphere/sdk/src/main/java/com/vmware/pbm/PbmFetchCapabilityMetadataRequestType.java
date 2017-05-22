
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import com.vmware.vim25.ManagedObjectReference;


/**
 * <p>Java class for PbmFetchCapabilityMetadataRequestType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmFetchCapabilityMetadataRequestType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="_this" type="{urn:vim25}ManagedObjectReference"/&gt;
 *         &lt;element name="resourceType" type="{urn:pbm}PbmProfileResourceType" minOccurs="0"/&gt;
 *         &lt;element name="vendorUuid" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmFetchCapabilityMetadataRequestType", propOrder = {
    "_this",
    "resourceType",
    "vendorUuid"
})
public class PbmFetchCapabilityMetadataRequestType {

    @XmlElement(required = true)
    protected ManagedObjectReference _this;
    protected PbmProfileResourceType resourceType;
    protected String vendorUuid;

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
     * Gets the value of the resourceType property.
     * 
     * @return
     *     possible object is
     *     {@link PbmProfileResourceType }
     *     
     */
    public PbmProfileResourceType getResourceType() {
        return resourceType;
    }

    /**
     * Sets the value of the resourceType property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmProfileResourceType }
     *     
     */
    public void setResourceType(PbmProfileResourceType value) {
        this.resourceType = value;
    }

    /**
     * Gets the value of the vendorUuid property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getVendorUuid() {
        return vendorUuid;
    }

    /**
     * Sets the value of the vendorUuid property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setVendorUuid(String value) {
        this.vendorUuid = value;
    }

}
