
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for OvfAttribute complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="OvfAttribute"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}OvfInvalidPackage"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="elementName" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="attributeName" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "OvfAttribute", propOrder = {
    "elementName",
    "attributeName"
})
@XmlSeeAlso({
    OvfInvalidValue.class,
    OvfMissingAttribute.class
})
public class OvfAttribute
    extends OvfInvalidPackage
{

    @XmlElement(required = true)
    protected String elementName;
    @XmlElement(required = true)
    protected String attributeName;

    /**
     * Gets the value of the elementName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getElementName() {
        return elementName;
    }

    /**
     * Sets the value of the elementName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setElementName(String value) {
        this.elementName = value;
    }

    /**
     * Gets the value of the attributeName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAttributeName() {
        return attributeName;
    }

    /**
     * Sets the value of the attributeName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAttributeName(String value) {
        this.attributeName = value;
    }

}
