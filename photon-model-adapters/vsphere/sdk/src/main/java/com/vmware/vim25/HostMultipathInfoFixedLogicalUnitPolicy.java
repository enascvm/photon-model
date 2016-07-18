
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostMultipathInfoFixedLogicalUnitPolicy complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostMultipathInfoFixedLogicalUnitPolicy"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}HostMultipathInfoLogicalUnitPolicy"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="prefer" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostMultipathInfoFixedLogicalUnitPolicy", propOrder = {
    "prefer"
})
public class HostMultipathInfoFixedLogicalUnitPolicy
    extends HostMultipathInfoLogicalUnitPolicy
{

    @XmlElement(required = true)
    protected String prefer;

    /**
     * Gets the value of the prefer property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPrefer() {
        return prefer;
    }

    /**
     * Sets the value of the prefer property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPrefer(String value) {
        this.prefer = value;
    }

}
