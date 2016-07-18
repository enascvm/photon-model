
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for OvfInvalidPackage complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="OvfInvalidPackage"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}OvfFault"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="lineNumber" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "OvfInvalidPackage", propOrder = {
    "lineNumber"
})
@XmlSeeAlso({
    OvfConstraint.class,
    OvfAttribute.class,
    OvfProperty.class,
    OvfElement.class,
    OvfWrongNamespace.class,
    OvfXmlFormat.class
})
public class OvfInvalidPackage
    extends OvfFault
{

    protected int lineNumber;

    /**
     * Gets the value of the lineNumber property.
     * 
     */
    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * Sets the value of the lineNumber property.
     * 
     */
    public void setLineNumber(int value) {
        this.lineNumber = value;
    }

}
