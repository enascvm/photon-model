
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for NoActiveHostInCluster complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="NoActiveHostInCluster"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}InvalidState"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="computeResource" type="{urn:vim25}ManagedObjectReference"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "NoActiveHostInCluster", propOrder = {
    "computeResource"
})
public class NoActiveHostInCluster
    extends InvalidState
{

    @XmlElement(required = true)
    protected ManagedObjectReference computeResource;

    /**
     * Gets the value of the computeResource property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getComputeResource() {
        return computeResource;
    }

    /**
     * Sets the value of the computeResource property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setComputeResource(ManagedObjectReference value) {
        this.computeResource = value;
    }

}
