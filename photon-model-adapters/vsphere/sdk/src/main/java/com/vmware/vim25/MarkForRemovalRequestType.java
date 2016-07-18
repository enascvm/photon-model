
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for MarkForRemovalRequestType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="MarkForRemovalRequestType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="_this" type="{urn:vim25}ManagedObjectReference"/&gt;
 *         &lt;element name="hbaName" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="remove" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "MarkForRemovalRequestType", propOrder = {
    "_this",
    "hbaName",
    "remove"
})
public class MarkForRemovalRequestType {

    @XmlElement(required = true)
    protected ManagedObjectReference _this;
    @XmlElement(required = true)
    protected String hbaName;
    protected boolean remove;

    /**
     * Gets the value of the this property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getThis() {
        return _this;
    }

    /**
     * Sets the value of the this property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setThis(ManagedObjectReference value) {
        this._this = value;
    }

    /**
     * Gets the value of the hbaName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getHbaName() {
        return hbaName;
    }

    /**
     * Sets the value of the hbaName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setHbaName(String value) {
        this.hbaName = value;
    }

    /**
     * Gets the value of the remove property.
     * 
     */
    public boolean isRemove() {
        return remove;
    }

    /**
     * Sets the value of the remove property.
     * 
     */
    public void setRemove(boolean value) {
        this.remove = value;
    }

}
