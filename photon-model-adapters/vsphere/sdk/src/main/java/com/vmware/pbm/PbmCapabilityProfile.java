
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PbmCapabilityProfile complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmCapabilityProfile"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:pbm}PbmProfile"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="profileCategory" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="resourceType" type="{urn:pbm}PbmProfileResourceType"/&gt;
 *         &lt;element name="constraints" type="{urn:pbm}PbmCapabilityConstraints"/&gt;
 *         &lt;element name="generationId" type="{http://www.w3.org/2001/XMLSchema}long" minOccurs="0"/&gt;
 *         &lt;element name="isDefault" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="systemCreatedProfileType" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="lineOfService" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmCapabilityProfile", propOrder = {
    "profileCategory",
    "resourceType",
    "constraints",
    "generationId",
    "isDefault",
    "systemCreatedProfileType",
    "lineOfService"
})
@XmlSeeAlso({
    PbmDefaultCapabilityProfile.class
})
public class PbmCapabilityProfile
    extends PbmProfile
{

    @XmlElement(required = true)
    protected String profileCategory;
    @XmlElement(required = true)
    protected PbmProfileResourceType resourceType;
    @XmlElement(required = true)
    protected PbmCapabilityConstraints constraints;
    protected Long generationId;
    protected boolean isDefault;
    protected String systemCreatedProfileType;
    protected String lineOfService;

    /**
     * Gets the value of the profileCategory property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getProfileCategory() {
        return profileCategory;
    }

    /**
     * Sets the value of the profileCategory property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setProfileCategory(String value) {
        this.profileCategory = value;
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
     * Gets the value of the constraints property.
     * 
     * @return
     *     possible object is
     *     {@link PbmCapabilityConstraints }
     *     
     */
    public PbmCapabilityConstraints getConstraints() {
        return constraints;
    }

    /**
     * Sets the value of the constraints property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmCapabilityConstraints }
     *     
     */
    public void setConstraints(PbmCapabilityConstraints value) {
        this.constraints = value;
    }

    /**
     * Gets the value of the generationId property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getGenerationId() {
        return generationId;
    }

    /**
     * Sets the value of the generationId property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setGenerationId(Long value) {
        this.generationId = value;
    }

    /**
     * Gets the value of the isDefault property.
     * 
     */
    public boolean isIsDefault() {
        return isDefault;
    }

    /**
     * Sets the value of the isDefault property.
     * 
     */
    public void setIsDefault(boolean value) {
        this.isDefault = value;
    }

    /**
     * Gets the value of the systemCreatedProfileType property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSystemCreatedProfileType() {
        return systemCreatedProfileType;
    }

    /**
     * Sets the value of the systemCreatedProfileType property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSystemCreatedProfileType(String value) {
        this.systemCreatedProfileType = value;
    }

    /**
     * Gets the value of the lineOfService property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getLineOfService() {
        return lineOfService;
    }

    /**
     * Sets the value of the lineOfService property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setLineOfService(String value) {
        this.lineOfService = value;
    }

}
