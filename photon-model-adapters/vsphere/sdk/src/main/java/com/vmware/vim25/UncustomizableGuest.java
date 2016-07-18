
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for UncustomizableGuest complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="UncustomizableGuest"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}CustomizationFault"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="uncustomizableGuestOS" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "UncustomizableGuest", propOrder = {
    "uncustomizableGuestOS"
})
public class UncustomizableGuest
    extends CustomizationFault
{

    @XmlElement(required = true)
    protected String uncustomizableGuestOS;

    /**
     * Gets the value of the uncustomizableGuestOS property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getUncustomizableGuestOS() {
        return uncustomizableGuestOS;
    }

    /**
     * Sets the value of the uncustomizableGuestOS property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setUncustomizableGuestOS(String value) {
        this.uncustomizableGuestOS = value;
    }

}
