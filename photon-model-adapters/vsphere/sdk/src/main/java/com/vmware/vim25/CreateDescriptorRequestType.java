
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for CreateDescriptorRequestType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="CreateDescriptorRequestType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="_this" type="{urn:vim25}ManagedObjectReference"/&gt;
 *         &lt;element name="obj" type="{urn:vim25}ManagedObjectReference"/&gt;
 *         &lt;element name="cdp" type="{urn:vim25}OvfCreateDescriptorParams"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "CreateDescriptorRequestType", propOrder = {
    "_this",
    "obj",
    "cdp"
})
public class CreateDescriptorRequestType {

    @XmlElement(required = true)
    protected ManagedObjectReference _this;
    @XmlElement(required = true)
    protected ManagedObjectReference obj;
    @XmlElement(required = true)
    protected OvfCreateDescriptorParams cdp;

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
     * Gets the value of the obj property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getObj() {
        return obj;
    }

    /**
     * Sets the value of the obj property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setObj(ManagedObjectReference value) {
        this.obj = value;
    }

    /**
     * Gets the value of the cdp property.
     * 
     * @return
     *     possible object is
     *     {@link OvfCreateDescriptorParams }
     *     
     */
    public OvfCreateDescriptorParams getCdp() {
        return cdp;
    }

    /**
     * Sets the value of the cdp property.
     * 
     * @param value
     *     allowed object is
     *     {@link OvfCreateDescriptorParams }
     *     
     */
    public void setCdp(OvfCreateDescriptorParams value) {
        this.cdp = value;
    }

}
