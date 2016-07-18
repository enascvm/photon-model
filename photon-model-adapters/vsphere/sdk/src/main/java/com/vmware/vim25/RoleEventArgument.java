
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for RoleEventArgument complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="RoleEventArgument"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}EventArgument"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="roleId" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *         &lt;element name="name" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "RoleEventArgument", propOrder = {
    "roleId",
    "name"
})
public class RoleEventArgument
    extends EventArgument
{

    protected int roleId;
    @XmlElement(required = true)
    protected String name;

    /**
     * Gets the value of the roleId property.
     * 
     */
    public int getRoleId() {
        return roleId;
    }

    /**
     * Sets the value of the roleId property.
     * 
     */
    public void setRoleId(int value) {
        this.roleId = value;
    }

    /**
     * Gets the value of the name property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the value of the name property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setName(String value) {
        this.name = value;
    }

}
