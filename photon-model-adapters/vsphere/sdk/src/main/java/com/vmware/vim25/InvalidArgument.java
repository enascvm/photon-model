
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for InvalidArgument complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="InvalidArgument"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}RuntimeFault"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="invalidProperty" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "InvalidArgument", propOrder = {
    "invalidProperty"
})
@XmlSeeAlso({
    IncompatibleSetting.class,
    InvalidDasConfigArgument.class,
    InvalidDasRestartPriorityForFtVm.class,
    InvalidDrsBehaviorForFtVm.class,
    InvalidIndexArgument.class
})
public class InvalidArgument
    extends RuntimeFault
{

    protected String invalidProperty;

    /**
     * Gets the value of the invalidProperty property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getInvalidProperty() {
        return invalidProperty;
    }

    /**
     * Sets the value of the invalidProperty property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setInvalidProperty(String value) {
        this.invalidProperty = value;
    }

}
