
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualIDEControllerOption complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualIDEControllerOption"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VirtualControllerOption"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="numIDEDisks" type="{urn:vim25}IntOption"/&gt;
 *         &lt;element name="numIDECdroms" type="{urn:vim25}IntOption"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualIDEControllerOption", propOrder = {
    "numIDEDisks",
    "numIDECdroms"
})
public class VirtualIDEControllerOption
    extends VirtualControllerOption
{

    @XmlElement(required = true)
    protected IntOption numIDEDisks;
    @XmlElement(required = true)
    protected IntOption numIDECdroms;

    /**
     * Gets the value of the numIDEDisks property.
     * 
     * @return
     *     possible object is
     *     {@link IntOption }
     *     
     */
    public IntOption getNumIDEDisks() {
        return numIDEDisks;
    }

    /**
     * Sets the value of the numIDEDisks property.
     * 
     * @param value
     *     allowed object is
     *     {@link IntOption }
     *     
     */
    public void setNumIDEDisks(IntOption value) {
        this.numIDEDisks = value;
    }

    /**
     * Gets the value of the numIDECdroms property.
     * 
     * @return
     *     possible object is
     *     {@link IntOption }
     *     
     */
    public IntOption getNumIDECdroms() {
        return numIDECdroms;
    }

    /**
     * Sets the value of the numIDECdroms property.
     * 
     * @param value
     *     allowed object is
     *     {@link IntOption }
     *     
     */
    public void setNumIDECdroms(IntOption value) {
        this.numIDECdroms = value;
    }

}
