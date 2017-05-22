
package com.vmware.pbm;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;
import com.vmware.vim25.ReplicationGroupId;


/**
 * <p>Java class for PbmPlacementMatchingReplicationResources complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmPlacementMatchingReplicationResources"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:pbm}PbmPlacementMatchingResources"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="replicationGroup" type="{urn:vim25}ReplicationGroupId" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmPlacementMatchingReplicationResources", propOrder = {
    "replicationGroup"
})
public class PbmPlacementMatchingReplicationResources
    extends PbmPlacementMatchingResources
{

    protected List<ReplicationGroupId> replicationGroup;

    /**
     * Gets the value of the replicationGroup property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the replicationGroup property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getReplicationGroup().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ReplicationGroupId }
     * 
     * 
     */
    public List<ReplicationGroupId> getReplicationGroup() {
        if (replicationGroup == null) {
            replicationGroup = new ArrayList<ReplicationGroupId>();
        }
        return this.replicationGroup;
    }

}
