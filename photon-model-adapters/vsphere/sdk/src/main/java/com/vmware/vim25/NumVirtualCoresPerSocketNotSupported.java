
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for NumVirtualCoresPerSocketNotSupported complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="NumVirtualCoresPerSocketNotSupported"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VirtualHardwareCompatibilityIssue"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="maxSupportedCoresPerSocketDest" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *         &lt;element name="numCoresPerSocketVm" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "NumVirtualCoresPerSocketNotSupported", propOrder = {
    "maxSupportedCoresPerSocketDest",
    "numCoresPerSocketVm"
})
public class NumVirtualCoresPerSocketNotSupported
    extends VirtualHardwareCompatibilityIssue
{

    protected int maxSupportedCoresPerSocketDest;
    protected int numCoresPerSocketVm;

    /**
     * Gets the value of the maxSupportedCoresPerSocketDest property.
     * 
     */
    public int getMaxSupportedCoresPerSocketDest() {
        return maxSupportedCoresPerSocketDest;
    }

    /**
     * Sets the value of the maxSupportedCoresPerSocketDest property.
     * 
     */
    public void setMaxSupportedCoresPerSocketDest(int value) {
        this.maxSupportedCoresPerSocketDest = value;
    }

    /**
     * Gets the value of the numCoresPerSocketVm property.
     * 
     */
    public int getNumCoresPerSocketVm() {
        return numCoresPerSocketVm;
    }

    /**
     * Sets the value of the numCoresPerSocketVm property.
     * 
     */
    public void setNumCoresPerSocketVm(int value) {
        this.numCoresPerSocketVm = value;
    }

}
