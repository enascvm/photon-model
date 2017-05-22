
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PermissionUpdatedEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PermissionUpdatedEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}PermissionEvent"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="role" type="{urn:vim25}RoleEventArgument"/&gt;
 *         &lt;element name="propagate" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="prevRole" type="{urn:vim25}RoleEventArgument" minOccurs="0"/&gt;
 *         &lt;element name="prevPropagate" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PermissionUpdatedEvent", propOrder = {
    "role",
    "propagate",
    "prevRole",
    "prevPropagate"
})
public class PermissionUpdatedEvent
    extends PermissionEvent
{

    @XmlElement(required = true)
    protected RoleEventArgument role;
    protected boolean propagate;
    protected RoleEventArgument prevRole;
    protected Boolean prevPropagate;

    /**
     * Gets the value of the role property.
     * 
     * @return
     *     possible object is
     *     {@link RoleEventArgument }
     *     
     */
    public RoleEventArgument getRole() {
        return role;
    }

    /**
     * Sets the value of the role property.
     * 
     * @param value
     *     allowed object is
     *     {@link RoleEventArgument }
     *     
     */
    public void setRole(RoleEventArgument value) {
        this.role = value;
    }

    /**
     * Gets the value of the propagate property.
     * 
     */
    public boolean isPropagate() {
        return propagate;
    }

    /**
     * Sets the value of the propagate property.
     * 
     */
    public void setPropagate(boolean value) {
        this.propagate = value;
    }

    /**
     * Gets the value of the prevRole property.
     * 
     * @return
     *     possible object is
     *     {@link RoleEventArgument }
     *     
     */
    public RoleEventArgument getPrevRole() {
        return prevRole;
    }

    /**
     * Sets the value of the prevRole property.
     * 
     * @param value
     *     allowed object is
     *     {@link RoleEventArgument }
     *     
     */
    public void setPrevRole(RoleEventArgument value) {
        this.prevRole = value;
    }

    /**
     * Gets the value of the prevPropagate property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isPrevPropagate() {
        return prevPropagate;
    }

    /**
     * Sets the value of the prevPropagate property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setPrevPropagate(Boolean value) {
        this.prevPropagate = value;
    }

}
