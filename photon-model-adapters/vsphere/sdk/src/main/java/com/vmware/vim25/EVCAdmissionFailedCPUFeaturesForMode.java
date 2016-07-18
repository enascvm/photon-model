
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for EVCAdmissionFailedCPUFeaturesForMode complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="EVCAdmissionFailedCPUFeaturesForMode"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}EVCAdmissionFailed"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="currentEVCModeKey" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "EVCAdmissionFailedCPUFeaturesForMode", propOrder = {
    "currentEVCModeKey"
})
public class EVCAdmissionFailedCPUFeaturesForMode
    extends EVCAdmissionFailed
{

    @XmlElement(required = true)
    protected String currentEVCModeKey;

    /**
     * Gets the value of the currentEVCModeKey property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getCurrentEVCModeKey() {
        return currentEVCModeKey;
    }

    /**
     * Sets the value of the currentEVCModeKey property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setCurrentEVCModeKey(String value) {
        this.currentEVCModeKey = value;
    }

}
