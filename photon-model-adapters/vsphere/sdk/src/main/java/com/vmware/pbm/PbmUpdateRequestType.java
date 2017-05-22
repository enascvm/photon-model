
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import com.vmware.vim25.ManagedObjectReference;


/**
 * <p>Java class for PbmUpdateRequestType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmUpdateRequestType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="_this" type="{urn:vim25}ManagedObjectReference"/&gt;
 *         &lt;element name="profileId" type="{urn:pbm}PbmProfileId"/&gt;
 *         &lt;element name="updateSpec" type="{urn:pbm}PbmCapabilityProfileUpdateSpec"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmUpdateRequestType", propOrder = {
    "_this",
    "profileId",
    "updateSpec"
})
public class PbmUpdateRequestType {

    @XmlElement(required = true)
    protected ManagedObjectReference _this;
    @XmlElement(required = true)
    protected PbmProfileId profileId;
    @XmlElement(required = true)
    protected PbmCapabilityProfileUpdateSpec updateSpec;

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
     * Gets the value of the profileId property.
     * 
     * @return
     *     possible object is
     *     {@link PbmProfileId }
     *     
     */
    public PbmProfileId getProfileId() {
        return profileId;
    }

    /**
     * Sets the value of the profileId property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmProfileId }
     *     
     */
    public void setProfileId(PbmProfileId value) {
        this.profileId = value;
    }

    /**
     * Gets the value of the updateSpec property.
     * 
     * @return
     *     possible object is
     *     {@link PbmCapabilityProfileUpdateSpec }
     *     
     */
    public PbmCapabilityProfileUpdateSpec getUpdateSpec() {
        return updateSpec;
    }

    /**
     * Sets the value of the updateSpec property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmCapabilityProfileUpdateSpec }
     *     
     */
    public void setUpdateSpec(PbmCapabilityProfileUpdateSpec value) {
        this.updateSpec = value;
    }

}
