
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostSriovConfig complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostSriovConfig"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}HostPciPassthruConfig"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="sriovEnabled" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="numVirtualFunction" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostSriovConfig", propOrder = {
    "sriovEnabled",
    "numVirtualFunction"
})
public class HostSriovConfig
    extends HostPciPassthruConfig
{

    protected boolean sriovEnabled;
    protected int numVirtualFunction;

    /**
     * Gets the value of the sriovEnabled property.
     * 
     */
    public boolean isSriovEnabled() {
        return sriovEnabled;
    }

    /**
     * Sets the value of the sriovEnabled property.
     * 
     */
    public void setSriovEnabled(boolean value) {
        this.sriovEnabled = value;
    }

    /**
     * Gets the value of the numVirtualFunction property.
     * 
     */
    public int getNumVirtualFunction() {
        return numVirtualFunction;
    }

    /**
     * Sets the value of the numVirtualFunction property.
     * 
     */
    public void setNumVirtualFunction(int value) {
        this.numVirtualFunction = value;
    }

}
