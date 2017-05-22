
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualNVMEControllerOption complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualNVMEControllerOption"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VirtualControllerOption"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="numNVMEDisks" type="{urn:vim25}IntOption"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualNVMEControllerOption", propOrder = {
    "numNVMEDisks"
})
public class VirtualNVMEControllerOption
    extends VirtualControllerOption
{

    @XmlElement(required = true)
    protected IntOption numNVMEDisks;

    /**
     * Gets the value of the numNVMEDisks property.
     * 
     * @return
     *     possible object is
     *     {@link IntOption }
     *     
     */
    public IntOption getNumNVMEDisks() {
        return numNVMEDisks;
    }

    /**
     * Sets the value of the numNVMEDisks property.
     * 
     * @param value
     *     allowed object is
     *     {@link IntOption }
     *     
     */
    public void setNumNVMEDisks(IntOption value) {
        this.numNVMEDisks = value;
    }

}
