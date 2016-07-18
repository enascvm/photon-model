
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for SessionManagerVmomiServiceRequestSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="SessionManagerVmomiServiceRequestSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}SessionManagerServiceRequestSpec"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="method" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "SessionManagerVmomiServiceRequestSpec", propOrder = {
    "method"
})
public class SessionManagerVmomiServiceRequestSpec
    extends SessionManagerServiceRequestSpec
{

    @XmlElement(required = true)
    protected String method;

    /**
     * Gets the value of the method property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getMethod() {
        return method;
    }

    /**
     * Sets the value of the method property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setMethod(String value) {
        this.method = value;
    }

}
