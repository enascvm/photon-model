
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PassiveNodeDeploymentSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PassiveNodeDeploymentSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}NodeDeploymentSpec"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="failoverIpSettings" type="{urn:vim25}CustomizationIPSettings" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PassiveNodeDeploymentSpec", propOrder = {
    "failoverIpSettings"
})
public class PassiveNodeDeploymentSpec
    extends NodeDeploymentSpec
{

    protected CustomizationIPSettings failoverIpSettings;

    /**
     * Gets the value of the failoverIpSettings property.
     * 
     * @return
     *     possible object is
     *     {@link CustomizationIPSettings }
     *     
     */
    public CustomizationIPSettings getFailoverIpSettings() {
        return failoverIpSettings;
    }

    /**
     * Sets the value of the failoverIpSettings property.
     * 
     * @param value
     *     allowed object is
     *     {@link CustomizationIPSettings }
     *     
     */
    public void setFailoverIpSettings(CustomizationIPSettings value) {
        this.failoverIpSettings = value;
    }

}
