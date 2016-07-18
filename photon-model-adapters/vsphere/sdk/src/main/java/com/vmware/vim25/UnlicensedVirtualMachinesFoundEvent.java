
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for UnlicensedVirtualMachinesFoundEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="UnlicensedVirtualMachinesFoundEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}LicenseEvent"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="available" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "UnlicensedVirtualMachinesFoundEvent", propOrder = {
    "available"
})
public class UnlicensedVirtualMachinesFoundEvent
    extends LicenseEvent
{

    protected int available;

    /**
     * Gets the value of the available property.
     * 
     */
    public int getAvailable() {
        return available;
    }

    /**
     * Sets the value of the available property.
     * 
     */
    public void setAvailable(int value) {
        this.available = value;
    }

}
