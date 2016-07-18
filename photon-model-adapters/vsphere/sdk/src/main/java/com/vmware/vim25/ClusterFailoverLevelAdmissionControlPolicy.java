
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ClusterFailoverLevelAdmissionControlPolicy complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ClusterFailoverLevelAdmissionControlPolicy"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}ClusterDasAdmissionControlPolicy"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="failoverLevel" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *         &lt;element name="slotPolicy" type="{urn:vim25}ClusterSlotPolicy" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ClusterFailoverLevelAdmissionControlPolicy", propOrder = {
    "failoverLevel",
    "slotPolicy"
})
public class ClusterFailoverLevelAdmissionControlPolicy
    extends ClusterDasAdmissionControlPolicy
{

    protected int failoverLevel;
    protected ClusterSlotPolicy slotPolicy;

    /**
     * Gets the value of the failoverLevel property.
     * 
     */
    public int getFailoverLevel() {
        return failoverLevel;
    }

    /**
     * Sets the value of the failoverLevel property.
     * 
     */
    public void setFailoverLevel(int value) {
        this.failoverLevel = value;
    }

    /**
     * Gets the value of the slotPolicy property.
     * 
     * @return
     *     possible object is
     *     {@link ClusterSlotPolicy }
     *     
     */
    public ClusterSlotPolicy getSlotPolicy() {
        return slotPolicy;
    }

    /**
     * Sets the value of the slotPolicy property.
     * 
     * @param value
     *     allowed object is
     *     {@link ClusterSlotPolicy }
     *     
     */
    public void setSlotPolicy(ClusterSlotPolicy value) {
        this.slotPolicy = value;
    }

}
