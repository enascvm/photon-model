
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualSATAControllerOption complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualSATAControllerOption"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VirtualControllerOption"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="numSATADisks" type="{urn:vim25}IntOption"/&gt;
 *         &lt;element name="numSATACdroms" type="{urn:vim25}IntOption"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualSATAControllerOption", propOrder = {
    "numSATADisks",
    "numSATACdroms"
})
@XmlSeeAlso({
    VirtualAHCIControllerOption.class
})
public class VirtualSATAControllerOption
    extends VirtualControllerOption
{

    @XmlElement(required = true)
    protected IntOption numSATADisks;
    @XmlElement(required = true)
    protected IntOption numSATACdroms;

    /**
     * Gets the value of the numSATADisks property.
     * 
     * @return
     *     possible object is
     *     {@link IntOption }
     *     
     */
    public IntOption getNumSATADisks() {
        return numSATADisks;
    }

    /**
     * Sets the value of the numSATADisks property.
     * 
     * @param value
     *     allowed object is
     *     {@link IntOption }
     *     
     */
    public void setNumSATADisks(IntOption value) {
        this.numSATADisks = value;
    }

    /**
     * Gets the value of the numSATACdroms property.
     * 
     * @return
     *     possible object is
     *     {@link IntOption }
     *     
     */
    public IntOption getNumSATACdroms() {
        return numSATACdroms;
    }

    /**
     * Sets the value of the numSATACdroms property.
     * 
     * @param value
     *     allowed object is
     *     {@link IntOption }
     *     
     */
    public void setNumSATACdroms(IntOption value) {
        this.numSATACdroms = value;
    }

}
