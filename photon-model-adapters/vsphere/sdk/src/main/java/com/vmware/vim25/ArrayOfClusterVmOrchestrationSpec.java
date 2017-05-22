
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfClusterVmOrchestrationSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfClusterVmOrchestrationSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="ClusterVmOrchestrationSpec" type="{urn:vim25}ClusterVmOrchestrationSpec" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfClusterVmOrchestrationSpec", propOrder = {
    "clusterVmOrchestrationSpec"
})
public class ArrayOfClusterVmOrchestrationSpec {

    @XmlElement(name = "ClusterVmOrchestrationSpec")
    protected List<ClusterVmOrchestrationSpec> clusterVmOrchestrationSpec;

    /**
     * Gets the value of the clusterVmOrchestrationSpec property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the clusterVmOrchestrationSpec property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getClusterVmOrchestrationSpec().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ClusterVmOrchestrationSpec }
     * 
     * 
     */
    public List<ClusterVmOrchestrationSpec> getClusterVmOrchestrationSpec() {
        if (clusterVmOrchestrationSpec == null) {
            clusterVmOrchestrationSpec = new ArrayList<ClusterVmOrchestrationSpec>();
        }
        return this.clusterVmOrchestrationSpec;
    }

}
