
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for IscsiFaultPnicInUse complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="IscsiFaultPnicInUse"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}IscsiFault"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="pnicDevice" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "IscsiFaultPnicInUse", propOrder = {
    "pnicDevice"
})
public class IscsiFaultPnicInUse
    extends IscsiFault
{

    @XmlElement(required = true)
    protected String pnicDevice;

    /**
     * Gets the value of the pnicDevice property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPnicDevice() {
        return pnicDevice;
    }

    /**
     * Sets the value of the pnicDevice property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPnicDevice(String value) {
        this.pnicDevice = value;
    }

}
