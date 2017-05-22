
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VchaClusterDeploymentSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VchaClusterDeploymentSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="passiveDeploymentSpec" type="{urn:vim25}PassiveNodeDeploymentSpec"/&gt;
 *         &lt;element name="witnessDeploymentSpec" type="{urn:vim25}NodeDeploymentSpec"/&gt;
 *         &lt;element name="activeVcSpec" type="{urn:vim25}SourceNodeSpec"/&gt;
 *         &lt;element name="activeVcNetworkConfig" type="{urn:vim25}ClusterNetworkConfigSpec" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VchaClusterDeploymentSpec", propOrder = {
    "passiveDeploymentSpec",
    "witnessDeploymentSpec",
    "activeVcSpec",
    "activeVcNetworkConfig"
})
public class VchaClusterDeploymentSpec
    extends DynamicData
{

    @XmlElement(required = true)
    protected PassiveNodeDeploymentSpec passiveDeploymentSpec;
    @XmlElement(required = true)
    protected NodeDeploymentSpec witnessDeploymentSpec;
    @XmlElement(required = true)
    protected SourceNodeSpec activeVcSpec;
    protected ClusterNetworkConfigSpec activeVcNetworkConfig;

    /**
     * Gets the value of the passiveDeploymentSpec property.
     * 
     * @return
     *     possible object is
     *     {@link PassiveNodeDeploymentSpec }
     *     
     */
    public PassiveNodeDeploymentSpec getPassiveDeploymentSpec() {
        return passiveDeploymentSpec;
    }

    /**
     * Sets the value of the passiveDeploymentSpec property.
     * 
     * @param value
     *     allowed object is
     *     {@link PassiveNodeDeploymentSpec }
     *     
     */
    public void setPassiveDeploymentSpec(PassiveNodeDeploymentSpec value) {
        this.passiveDeploymentSpec = value;
    }

    /**
     * Gets the value of the witnessDeploymentSpec property.
     * 
     * @return
     *     possible object is
     *     {@link NodeDeploymentSpec }
     *     
     */
    public NodeDeploymentSpec getWitnessDeploymentSpec() {
        return witnessDeploymentSpec;
    }

    /**
     * Sets the value of the witnessDeploymentSpec property.
     * 
     * @param value
     *     allowed object is
     *     {@link NodeDeploymentSpec }
     *     
     */
    public void setWitnessDeploymentSpec(NodeDeploymentSpec value) {
        this.witnessDeploymentSpec = value;
    }

    /**
     * Gets the value of the activeVcSpec property.
     * 
     * @return
     *     possible object is
     *     {@link SourceNodeSpec }
     *     
     */
    public SourceNodeSpec getActiveVcSpec() {
        return activeVcSpec;
    }

    /**
     * Sets the value of the activeVcSpec property.
     * 
     * @param value
     *     allowed object is
     *     {@link SourceNodeSpec }
     *     
     */
    public void setActiveVcSpec(SourceNodeSpec value) {
        this.activeVcSpec = value;
    }

    /**
     * Gets the value of the activeVcNetworkConfig property.
     * 
     * @return
     *     possible object is
     *     {@link ClusterNetworkConfigSpec }
     *     
     */
    public ClusterNetworkConfigSpec getActiveVcNetworkConfig() {
        return activeVcNetworkConfig;
    }

    /**
     * Sets the value of the activeVcNetworkConfig property.
     * 
     * @param value
     *     allowed object is
     *     {@link ClusterNetworkConfigSpec }
     *     
     */
    public void setActiveVcNetworkConfig(ClusterNetworkConfigSpec value) {
        this.activeVcNetworkConfig = value;
    }

}
