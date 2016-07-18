
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for UserNotFound complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="UserNotFound"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VimFault"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="principal" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="unresolved" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "UserNotFound", propOrder = {
    "principal",
    "unresolved"
})
public class UserNotFound
    extends VimFault
{

    @XmlElement(required = true)
    protected String principal;
    protected boolean unresolved;

    /**
     * Gets the value of the principal property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPrincipal() {
        return principal;
    }

    /**
     * Sets the value of the principal property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPrincipal(String value) {
        this.principal = value;
    }

    /**
     * Gets the value of the unresolved property.
     * 
     */
    public boolean isUnresolved() {
        return unresolved;
    }

    /**
     * Sets the value of the unresolved property.
     * 
     */
    public void setUnresolved(boolean value) {
        this.unresolved = value;
    }

}
