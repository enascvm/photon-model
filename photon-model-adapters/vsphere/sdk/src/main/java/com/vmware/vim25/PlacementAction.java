
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PlacementAction complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PlacementAction"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}ClusterAction"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="vm" type="{urn:vim25}ManagedObjectReference" minOccurs="0"/&gt;
 *         &lt;element name="targetHost" type="{urn:vim25}ManagedObjectReference" minOccurs="0"/&gt;
 *         &lt;element name="relocateSpec" type="{urn:vim25}VirtualMachineRelocateSpec" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PlacementAction", propOrder = {
    "vm",
    "targetHost",
    "relocateSpec"
})
public class PlacementAction
    extends ClusterAction
{

    protected ManagedObjectReference vm;
    protected ManagedObjectReference targetHost;
    protected VirtualMachineRelocateSpec relocateSpec;

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
     * Gets the value of the targetHost property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getTargetHost() {
        return targetHost;
    }

    /**
     * Sets the value of the targetHost property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setTargetHost(ManagedObjectReference value) {
        this.targetHost = value;
    }

    /**
     * Gets the value of the relocateSpec property.
     * 
     * @return
     *     possible object is
     *     {@link VirtualMachineRelocateSpec }
     *     
     */
    public VirtualMachineRelocateSpec getRelocateSpec() {
        return relocateSpec;
    }

    /**
     * Sets the value of the relocateSpec property.
     * 
     * @param value
     *     allowed object is
     *     {@link VirtualMachineRelocateSpec }
     *     
     */
    public void setRelocateSpec(VirtualMachineRelocateSpec value) {
        this.relocateSpec = value;
    }

}
