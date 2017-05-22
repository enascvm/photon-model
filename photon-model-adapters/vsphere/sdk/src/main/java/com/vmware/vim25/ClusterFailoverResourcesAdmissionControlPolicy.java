
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
 *         &lt;element name="failoverLevel" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/&gt;
 *         &lt;element name="autoComputePercentages" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
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
    "memoryFailoverResourcesPercent",
    "failoverLevel",
    "autoComputePercentages"
})
public class ClusterFailoverResourcesAdmissionControlPolicy
    extends ClusterDasAdmissionControlPolicy
{

    protected int cpuFailoverResourcesPercent;
    protected int memoryFailoverResourcesPercent;
    protected Integer failoverLevel;
    protected Boolean autoComputePercentages;

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

    /**
     * Gets the value of the failoverLevel property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getFailoverLevel() {
        return failoverLevel;
    }

    /**
     * Sets the value of the failoverLevel property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setFailoverLevel(Integer value) {
        this.failoverLevel = value;
    }

    /**
     * Gets the value of the autoComputePercentages property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isAutoComputePercentages() {
        return autoComputePercentages;
    }

    /**
     * Sets the value of the autoComputePercentages property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setAutoComputePercentages(Boolean value) {
        this.autoComputePercentages = value;
    }

}
