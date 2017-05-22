
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PbmPlacementCapabilityProfileRequirement complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmPlacementCapabilityProfileRequirement"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:pbm}PbmPlacementRequirement"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="profileId" type="{urn:pbm}PbmProfileId"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmPlacementCapabilityProfileRequirement", propOrder = {
    "profileId"
})
public class PbmPlacementCapabilityProfileRequirement
    extends PbmPlacementRequirement
{

    @XmlElement(required = true)
    protected PbmProfileId profileId;

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

}
