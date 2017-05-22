
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import com.vmware.vim25.DynamicData;


/**
 * <p>Java class for PbmCompliancePolicyStatus complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmCompliancePolicyStatus"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="expectedValue" type="{urn:pbm}PbmCapabilityInstance"/&gt;
 *         &lt;element name="currentValue" type="{urn:pbm}PbmCapabilityInstance" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmCompliancePolicyStatus", propOrder = {
    "expectedValue",
    "currentValue"
})
public class PbmCompliancePolicyStatus
    extends DynamicData
{

    @XmlElement(required = true)
    protected PbmCapabilityInstance expectedValue;
    protected PbmCapabilityInstance currentValue;

    /**
     * Gets the value of the expectedValue property.
     * 
     * @return
     *     possible object is
     *     {@link PbmCapabilityInstance }
     *     
     */
    public PbmCapabilityInstance getExpectedValue() {
        return expectedValue;
    }

    /**
     * Sets the value of the expectedValue property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmCapabilityInstance }
     *     
     */
    public void setExpectedValue(PbmCapabilityInstance value) {
        this.expectedValue = value;
    }

    /**
     * Gets the value of the currentValue property.
     * 
     * @return
     *     possible object is
     *     {@link PbmCapabilityInstance }
     *     
     */
    public PbmCapabilityInstance getCurrentValue() {
        return currentValue;
    }

    /**
     * Sets the value of the currentValue property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmCapabilityInstance }
     *     
     */
    public void setCurrentValue(PbmCapabilityInstance value) {
        this.currentValue = value;
    }

}
