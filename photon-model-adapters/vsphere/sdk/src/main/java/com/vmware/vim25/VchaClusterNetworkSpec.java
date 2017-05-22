
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VchaClusterNetworkSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VchaClusterNetworkSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="witnessNetworkSpec" type="{urn:vim25}NodeNetworkSpec"/&gt;
 *         &lt;element name="passiveNetworkSpec" type="{urn:vim25}PassiveNodeNetworkSpec"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VchaClusterNetworkSpec", propOrder = {
    "witnessNetworkSpec",
    "passiveNetworkSpec"
})
public class VchaClusterNetworkSpec
    extends DynamicData
{

    @XmlElement(required = true)
    protected NodeNetworkSpec witnessNetworkSpec;
    @XmlElement(required = true)
    protected PassiveNodeNetworkSpec passiveNetworkSpec;

    /**
     * Gets the value of the witnessNetworkSpec property.
     * 
     * @return
     *     possible object is
     *     {@link NodeNetworkSpec }
     *     
     */
    public NodeNetworkSpec getWitnessNetworkSpec() {
        return witnessNetworkSpec;
    }

    /**
     * Sets the value of the witnessNetworkSpec property.
     * 
     * @param value
     *     allowed object is
     *     {@link NodeNetworkSpec }
     *     
     */
    public void setWitnessNetworkSpec(NodeNetworkSpec value) {
        this.witnessNetworkSpec = value;
    }

    /**
     * Gets the value of the passiveNetworkSpec property.
     * 
     * @return
     *     possible object is
     *     {@link PassiveNodeNetworkSpec }
     *     
     */
    public PassiveNodeNetworkSpec getPassiveNetworkSpec() {
        return passiveNetworkSpec;
    }

    /**
     * Sets the value of the passiveNetworkSpec property.
     * 
     * @param value
     *     allowed object is
     *     {@link PassiveNodeNetworkSpec }
     *     
     */
    public void setPassiveNetworkSpec(PassiveNodeNetworkSpec value) {
        this.passiveNetworkSpec = value;
    }

}
