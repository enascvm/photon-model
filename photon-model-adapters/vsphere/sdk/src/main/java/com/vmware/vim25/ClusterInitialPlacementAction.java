
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ClusterInitialPlacementAction complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ClusterInitialPlacementAction"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}ClusterAction"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="targetHost" type="{urn:vim25}ManagedObjectReference"/&gt;
 *         &lt;element name="pool" type="{urn:vim25}ManagedObjectReference" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ClusterInitialPlacementAction", propOrder = {
    "targetHost",
    "pool"
})
public class ClusterInitialPlacementAction
    extends ClusterAction
{

    @XmlElement(required = true)
    protected ManagedObjectReference targetHost;
    protected ManagedObjectReference pool;

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
     * Gets the value of the pool property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getPool() {
        return pool;
    }

    /**
     * Sets the value of the pool property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setPool(ManagedObjectReference value) {
        this.pool = value;
    }

}
