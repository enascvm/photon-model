
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for GuestRegistryKeyFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="GuestRegistryKeyFault"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}GuestRegistryFault"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="keyName" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "GuestRegistryKeyFault", propOrder = {
    "keyName"
})
@XmlSeeAlso({
    GuestRegistryKeyAlreadyExists.class,
    GuestRegistryKeyHasSubkeys.class,
    GuestRegistryKeyInvalid.class,
    GuestRegistryKeyParentVolatile.class
})
public class GuestRegistryKeyFault
    extends GuestRegistryFault
{

    @XmlElement(required = true)
    protected String keyName;

    /**
     * Gets the value of the keyName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getKeyName() {
        return keyName;
    }

    /**
     * Sets the value of the keyName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setKeyName(String value) {
        this.keyName = value;
    }

}
