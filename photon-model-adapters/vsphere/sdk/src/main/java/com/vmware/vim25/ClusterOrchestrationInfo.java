
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ClusterOrchestrationInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ClusterOrchestrationInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="defaultVmReadiness" type="{urn:vim25}ClusterVmReadiness" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ClusterOrchestrationInfo", propOrder = {
    "defaultVmReadiness"
})
public class ClusterOrchestrationInfo
    extends DynamicData
{

    protected ClusterVmReadiness defaultVmReadiness;

    /**
     * Gets the value of the defaultVmReadiness property.
     * 
     * @return
     *     possible object is
     *     {@link ClusterVmReadiness }
     *     
     */
    public ClusterVmReadiness getDefaultVmReadiness() {
        return defaultVmReadiness;
    }

    /**
     * Sets the value of the defaultVmReadiness property.
     * 
     * @param value
     *     allowed object is
     *     {@link ClusterVmReadiness }
     *     
     */
    public void setDefaultVmReadiness(ClusterVmReadiness value) {
        this.defaultVmReadiness = value;
    }

}
