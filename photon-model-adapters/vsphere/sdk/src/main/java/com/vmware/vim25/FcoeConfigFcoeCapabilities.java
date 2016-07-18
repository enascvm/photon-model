
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for FcoeConfigFcoeCapabilities complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="FcoeConfigFcoeCapabilities"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="priorityClass" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="sourceMacAddress" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="vlanRange" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "FcoeConfigFcoeCapabilities", propOrder = {
    "priorityClass",
    "sourceMacAddress",
    "vlanRange"
})
public class FcoeConfigFcoeCapabilities
    extends DynamicData
{

    protected boolean priorityClass;
    protected boolean sourceMacAddress;
    protected boolean vlanRange;

    /**
     * Gets the value of the priorityClass property.
     * 
     */
    public boolean isPriorityClass() {
        return priorityClass;
    }

    /**
     * Sets the value of the priorityClass property.
     * 
     */
    public void setPriorityClass(boolean value) {
        this.priorityClass = value;
    }

    /**
     * Gets the value of the sourceMacAddress property.
     * 
     */
    public boolean isSourceMacAddress() {
        return sourceMacAddress;
    }

    /**
     * Sets the value of the sourceMacAddress property.
     * 
     */
    public void setSourceMacAddress(boolean value) {
        this.sourceMacAddress = value;
    }

    /**
     * Gets the value of the vlanRange property.
     * 
     */
    public boolean isVlanRange() {
        return vlanRange;
    }

    /**
     * Sets the value of the vlanRange property.
     * 
     */
    public void setVlanRange(boolean value) {
        this.vlanRange = value;
    }

}
