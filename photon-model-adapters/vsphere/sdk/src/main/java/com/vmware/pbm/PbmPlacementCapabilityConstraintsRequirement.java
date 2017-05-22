
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PbmPlacementCapabilityConstraintsRequirement complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmPlacementCapabilityConstraintsRequirement"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:pbm}PbmPlacementRequirement"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="constraints" type="{urn:pbm}PbmCapabilityConstraints"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmPlacementCapabilityConstraintsRequirement", propOrder = {
    "constraints"
})
public class PbmPlacementCapabilityConstraintsRequirement
    extends PbmPlacementRequirement
{

    @XmlElement(required = true)
    protected PbmCapabilityConstraints constraints;

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

}
