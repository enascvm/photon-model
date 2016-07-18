
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for OvfConsumerCallbackFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="OvfConsumerCallbackFault"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}OvfFault"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="extensionKey" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="extensionName" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "OvfConsumerCallbackFault", propOrder = {
    "extensionKey",
    "extensionName"
})
@XmlSeeAlso({
    OvfConsumerCommunicationError.class,
    OvfConsumerFault.class,
    OvfConsumerInvalidSection.class,
    OvfConsumerUndeclaredSection.class,
    OvfConsumerUndefinedPrefix.class
})
public class OvfConsumerCallbackFault
    extends OvfFault
{

    @XmlElement(required = true)
    protected String extensionKey;
    @XmlElement(required = true)
    protected String extensionName;

    /**
     * Gets the value of the extensionKey property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getExtensionKey() {
        return extensionKey;
    }

    /**
     * Sets the value of the extensionKey property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setExtensionKey(String value) {
        this.extensionKey = value;
    }

    /**
     * Gets the value of the extensionName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getExtensionName() {
        return extensionName;
    }

    /**
     * Sets the value of the extensionName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setExtensionName(String value) {
        this.extensionName = value;
    }

}
