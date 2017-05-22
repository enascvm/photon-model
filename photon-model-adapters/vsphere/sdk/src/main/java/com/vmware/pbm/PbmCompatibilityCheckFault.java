
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PbmCompatibilityCheckFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmCompatibilityCheckFault"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:pbm}PbmFault"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="hub" type="{urn:pbm}PbmPlacementHub"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmCompatibilityCheckFault", propOrder = {
    "hub"
})
@XmlSeeAlso({
    PbmDefaultProfileAppliesFault.class,
    PbmPropertyMismatchFault.class
})
public class PbmCompatibilityCheckFault
    extends PbmFault
{

    @XmlElement(required = true)
    protected PbmPlacementHub hub;

    /**
     * Gets the value of the hub property.
     * 
     * @return
     *     possible object is
     *     {@link PbmPlacementHub }
     *     
     */
    public PbmPlacementHub getHub() {
        return hub;
    }

    /**
     * Sets the value of the hub property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmPlacementHub }
     *     
     */
    public void setHub(PbmPlacementHub value) {
        this.hub = value;
    }

}
