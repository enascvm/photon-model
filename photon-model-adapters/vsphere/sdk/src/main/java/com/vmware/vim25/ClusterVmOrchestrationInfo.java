
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ClusterVmOrchestrationInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ClusterVmOrchestrationInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="vm" type="{urn:vim25}ManagedObjectReference"/&gt;
 *         &lt;element name="vmReadiness" type="{urn:vim25}ClusterVmReadiness"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ClusterVmOrchestrationInfo", propOrder = {
    "vm",
    "vmReadiness"
})
public class ClusterVmOrchestrationInfo
    extends DynamicData
{

    @XmlElement(required = true)
    protected ManagedObjectReference vm;
    @XmlElement(required = true)
    protected ClusterVmReadiness vmReadiness;

    /**
     * Gets the value of the vm property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getVm() {
        return vm;
    }

    /**
     * Sets the value of the vm property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setVm(ManagedObjectReference value) {
        this.vm = value;
    }

    /**
     * Gets the value of the vmReadiness property.
     * 
     * @return
     *     possible object is
     *     {@link ClusterVmReadiness }
     *     
     */
    public ClusterVmReadiness getVmReadiness() {
        return vmReadiness;
    }

    /**
     * Sets the value of the vmReadiness property.
     * 
     * @param value
     *     allowed object is
     *     {@link ClusterVmReadiness }
     *     
     */
    public void setVmReadiness(ClusterVmReadiness value) {
        this.vmReadiness = value;
    }

}
