
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ClusterFailoverResourcesAdmissionControlInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ClusterFailoverResourcesAdmissionControlInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}ClusterDasAdmissionControlInfo"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="currentCpuFailoverResourcesPercent" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *         &lt;element name="currentMemoryFailoverResourcesPercent" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ClusterFailoverResourcesAdmissionControlInfo", propOrder = {
    "currentCpuFailoverResourcesPercent",
    "currentMemoryFailoverResourcesPercent"
})
public class ClusterFailoverResourcesAdmissionControlInfo
    extends ClusterDasAdmissionControlInfo
{

    protected int currentCpuFailoverResourcesPercent;
    protected int currentMemoryFailoverResourcesPercent;

    /**
     * Gets the value of the currentCpuFailoverResourcesPercent property.
     * 
     */
    public int getCurrentCpuFailoverResourcesPercent() {
        return currentCpuFailoverResourcesPercent;
    }

    /**
     * Sets the value of the currentCpuFailoverResourcesPercent property.
     * 
     */
    public void setCurrentCpuFailoverResourcesPercent(int value) {
        this.currentCpuFailoverResourcesPercent = value;
    }

    /**
     * Gets the value of the currentMemoryFailoverResourcesPercent property.
     * 
     */
    public int getCurrentMemoryFailoverResourcesPercent() {
        return currentMemoryFailoverResourcesPercent;
    }

    /**
     * Sets the value of the currentMemoryFailoverResourcesPercent property.
     * 
     */
    public void setCurrentMemoryFailoverResourcesPercent(int value) {
        this.currentMemoryFailoverResourcesPercent = value;
    }

}
