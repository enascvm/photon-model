
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfClusterVmOrchestrationInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfClusterVmOrchestrationInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="ClusterVmOrchestrationInfo" type="{urn:vim25}ClusterVmOrchestrationInfo" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfClusterVmOrchestrationInfo", propOrder = {
    "clusterVmOrchestrationInfo"
})
public class ArrayOfClusterVmOrchestrationInfo {

    @XmlElement(name = "ClusterVmOrchestrationInfo")
    protected List<ClusterVmOrchestrationInfo> clusterVmOrchestrationInfo;

    /**
     * Gets the value of the clusterVmOrchestrationInfo property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the clusterVmOrchestrationInfo property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getClusterVmOrchestrationInfo().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ClusterVmOrchestrationInfo }
     * 
     * 
     */
    public List<ClusterVmOrchestrationInfo> getClusterVmOrchestrationInfo() {
        if (clusterVmOrchestrationInfo == null) {
            clusterVmOrchestrationInfo = new ArrayList<ClusterVmOrchestrationInfo>();
        }
        return this.clusterVmOrchestrationInfo;
    }

}
