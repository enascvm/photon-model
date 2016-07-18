
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostTpmBootSecurityOptionEventDetails complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostTpmBootSecurityOptionEventDetails"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}HostTpmEventDetails"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="bootSecurityOption" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostTpmBootSecurityOptionEventDetails", propOrder = {
    "bootSecurityOption"
})
public class HostTpmBootSecurityOptionEventDetails
    extends HostTpmEventDetails
{

    @XmlElement(required = true)
    protected String bootSecurityOption;

    /**
     * Gets the value of the bootSecurityOption property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getBootSecurityOption() {
        return bootSecurityOption;
    }

    /**
     * Sets the value of the bootSecurityOption property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setBootSecurityOption(String value) {
        this.bootSecurityOption = value;
    }

}
