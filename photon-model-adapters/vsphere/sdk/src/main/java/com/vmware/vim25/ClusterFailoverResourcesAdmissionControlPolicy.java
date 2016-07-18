
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ClusterFailoverResourcesAdmissionControlPolicy complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ClusterFailoverResourcesAdmissionControlPolicy"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}ClusterDasAdmissionControlPolicy"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="cpuFailoverResourcesPercent" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *         &lt;element name="memoryFailoverResourcesPercent" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ClusterFailoverResourcesAdmissionControlPolicy", propOrder = {
    "cpuFailoverResourcesPercent",
    "memoryFailoverResourcesPercent"
})
public class ClusterFailoverResourcesAdmissionControlPolicy
    extends ClusterDasAdmissionControlPolicy
{

    protected int cpuFailoverResourcesPercent;
    protected int memoryFailoverResourcesPercent;

    /**
     * Gets the value of the cpuFailoverResourcesPercent property.
     * 
     */
    public int getCpuFailoverResourcesPercent() {
        return cpuFailoverResourcesPercent;
    }

    /**
     * Sets the value of the cpuFailoverResourcesPercent property.
     * 
     */
    public void setCpuFailoverResourcesPercent(int value) {
        this.cpuFailoverResourcesPercent = value;
    }

    /**
     * Gets the value of the memoryFailoverResourcesPercent property.
     * 
     */
    public int getMemoryFailoverResourcesPercent() {
        return memoryFailoverResourcesPercent;
    }

    /**
     * Sets the value of the memoryFailoverResourcesPercent property.
     * 
     */
    public void setMemoryFailoverResourcesPercent(int value) {
        this.memoryFailoverResourcesPercent = value;
    }

}
