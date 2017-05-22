
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import com.vmware.vim25.DynamicData;
import com.vmware.vim25.LocalizedMethodFault;
import com.vmware.vim25.ReplicationGroupId;


/**
 * <p>Java class for PbmQueryReplicationGroupResult complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmQueryReplicationGroupResult"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="object" type="{urn:pbm}PbmServerObjectRef"/&gt;
 *         &lt;element name="replicationGroupId" type="{urn:vim25}ReplicationGroupId" minOccurs="0"/&gt;
 *         &lt;element name="fault" type="{urn:vim25}LocalizedMethodFault" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmQueryReplicationGroupResult", propOrder = {
    "object",
    "replicationGroupId",
    "fault"
})
public class PbmQueryReplicationGroupResult
    extends DynamicData
{

    @XmlElement(required = true)
    protected PbmServerObjectRef object;
    protected ReplicationGroupId replicationGroupId;
    protected LocalizedMethodFault fault;

    /**
     * Gets the value of the object property.
     * 
     * @return
     *     possible object is
     *     {@link PbmServerObjectRef }
     *     
     */
    public PbmServerObjectRef getObject() {
        return object;
    }

    /**
     * Sets the value of the object property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmServerObjectRef }
     *     
     */
    public void setObject(PbmServerObjectRef value) {
        this.object = value;
    }

    /**
     * Gets the value of the replicationGroupId property.
     * 
     * @return
     *     possible object is
     *     {@link ReplicationGroupId }
     *     
     */
    public ReplicationGroupId getReplicationGroupId() {
        return replicationGroupId;
    }

    /**
     * Sets the value of the replicationGroupId property.
     * 
     * @param value
     *     allowed object is
     *     {@link ReplicationGroupId }
     *     
     */
    public void setReplicationGroupId(ReplicationGroupId value) {
        this.replicationGroupId = value;
    }

    /**
     * Gets the value of the fault property.
     * 
     * @return
     *     possible object is
     *     {@link LocalizedMethodFault }
     *     
     */
    public LocalizedMethodFault getFault() {
        return fault;
    }

    /**
     * Sets the value of the fault property.
     * 
     * @param value
     *     allowed object is
     *     {@link LocalizedMethodFault }
     *     
     */
    public void setFault(LocalizedMethodFault value) {
        this.fault = value;
    }

}
