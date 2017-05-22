
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PbmPropertyMismatchFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmPropertyMismatchFault"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:pbm}PbmCompatibilityCheckFault"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="capabilityInstanceId" type="{urn:pbm}PbmCapabilityMetadataUniqueId"/&gt;
 *         &lt;element name="requirementPropertyInstance" type="{urn:pbm}PbmCapabilityPropertyInstance"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmPropertyMismatchFault", propOrder = {
    "capabilityInstanceId",
    "requirementPropertyInstance"
})
@XmlSeeAlso({
    PbmCapabilityProfilePropertyMismatchFault.class
})
public class PbmPropertyMismatchFault
    extends PbmCompatibilityCheckFault
{

    @XmlElement(required = true)
    protected PbmCapabilityMetadataUniqueId capabilityInstanceId;
    @XmlElement(required = true)
    protected PbmCapabilityPropertyInstance requirementPropertyInstance;

    /**
     * Gets the value of the capabilityInstanceId property.
     * 
     * @return
     *     possible object is
     *     {@link PbmCapabilityMetadataUniqueId }
     *     
     */
    public PbmCapabilityMetadataUniqueId getCapabilityInstanceId() {
        return capabilityInstanceId;
    }

    /**
     * Sets the value of the capabilityInstanceId property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmCapabilityMetadataUniqueId }
     *     
     */
    public void setCapabilityInstanceId(PbmCapabilityMetadataUniqueId value) {
        this.capabilityInstanceId = value;
    }

    /**
     * Gets the value of the requirementPropertyInstance property.
     * 
     * @return
     *     possible object is
     *     {@link PbmCapabilityPropertyInstance }
     *     
     */
    public PbmCapabilityPropertyInstance getRequirementPropertyInstance() {
        return requirementPropertyInstance;
    }

    /**
     * Sets the value of the requirementPropertyInstance property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmCapabilityPropertyInstance }
     *     
     */
    public void setRequirementPropertyInstance(PbmCapabilityPropertyInstance value) {
        this.requirementPropertyInstance = value;
    }

}
