
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PbmCapabilityProfilePropertyMismatchFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmCapabilityProfilePropertyMismatchFault"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:pbm}PbmPropertyMismatchFault"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="resourcePropertyInstance" type="{urn:pbm}PbmCapabilityPropertyInstance"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmCapabilityProfilePropertyMismatchFault", propOrder = {
    "resourcePropertyInstance"
})
@XmlSeeAlso({
    PbmIncompatibleVendorSpecificRuleSet.class
})
public class PbmCapabilityProfilePropertyMismatchFault
    extends PbmPropertyMismatchFault
{

    @XmlElement(required = true)
    protected PbmCapabilityPropertyInstance resourcePropertyInstance;

    /**
     * Gets the value of the resourcePropertyInstance property.
     * 
     * @return
     *     possible object is
     *     {@link PbmCapabilityPropertyInstance }
     *     
     */
    public PbmCapabilityPropertyInstance getResourcePropertyInstance() {
        return resourcePropertyInstance;
    }

    /**
     * Sets the value of the resourcePropertyInstance property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmCapabilityPropertyInstance }
     *     
     */
    public void setResourcePropertyInstance(PbmCapabilityPropertyInstance value) {
        this.resourcePropertyInstance = value;
    }

}
