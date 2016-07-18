
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ParseDescriptorRequestType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ParseDescriptorRequestType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="_this" type="{urn:vim25}ManagedObjectReference"/&gt;
 *         &lt;element name="ovfDescriptor" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="pdp" type="{urn:vim25}OvfParseDescriptorParams"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ParseDescriptorRequestType", propOrder = {
    "_this",
    "ovfDescriptor",
    "pdp"
})
public class ParseDescriptorRequestType {

    @XmlElement(required = true)
    protected ManagedObjectReference _this;
    @XmlElement(required = true)
    protected String ovfDescriptor;
    @XmlElement(required = true)
    protected OvfParseDescriptorParams pdp;

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
     * Gets the value of the ovfDescriptor property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getOvfDescriptor() {
        return ovfDescriptor;
    }

    /**
     * Sets the value of the ovfDescriptor property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setOvfDescriptor(String value) {
        this.ovfDescriptor = value;
    }

    /**
     * Gets the value of the pdp property.
     * 
     * @return
     *     possible object is
     *     {@link OvfParseDescriptorParams }
     *     
     */
    public OvfParseDescriptorParams getPdp() {
        return pdp;
    }

    /**
     * Sets the value of the pdp property.
     * 
     * @param value
     *     allowed object is
     *     {@link OvfParseDescriptorParams }
     *     
     */
    public void setPdp(OvfParseDescriptorParams value) {
        this.pdp = value;
    }

}
