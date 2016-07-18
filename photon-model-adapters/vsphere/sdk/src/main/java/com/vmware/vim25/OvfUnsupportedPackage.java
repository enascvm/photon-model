
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for OvfUnsupportedPackage complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="OvfUnsupportedPackage"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}OvfFault"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="lineNumber" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "OvfUnsupportedPackage", propOrder = {
    "lineNumber"
})
@XmlSeeAlso({
    OvfInvalidVmName.class,
    OvfNoHostNic.class,
    OvfNoSupportedHardwareFamily.class,
    OvfUnsupportedAttribute.class,
    OvfUnsupportedElement.class,
    OvfUnsupportedSubType.class,
    OvfUnsupportedType.class
})
public class OvfUnsupportedPackage
    extends OvfFault
{

    protected Integer lineNumber;

    /**
     * Gets the value of the lineNumber property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getLineNumber() {
        return lineNumber;
    }

    /**
     * Sets the value of the lineNumber property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setLineNumber(Integer value) {
        this.lineNumber = value;
    }

}
