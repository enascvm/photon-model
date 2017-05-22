
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import com.vmware.vim25.DynamicData;


/**
 * <p>Java class for PbmCapabilityPropertyMetadata complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmCapabilityPropertyMetadata"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="id" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="summary" type="{urn:pbm}PbmExtendedElementDescription"/&gt;
 *         &lt;element name="mandatory" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="type" type="{urn:pbm}PbmCapabilityTypeInfo" minOccurs="0"/&gt;
 *         &lt;element name="defaultValue" type="{http://www.w3.org/2001/XMLSchema}anyType" minOccurs="0"/&gt;
 *         &lt;element name="allowedValue" type="{http://www.w3.org/2001/XMLSchema}anyType" minOccurs="0"/&gt;
 *         &lt;element name="requirementsTypeHint" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmCapabilityPropertyMetadata", propOrder = {
    "id",
    "summary",
    "mandatory",
    "type",
    "defaultValue",
    "allowedValue",
    "requirementsTypeHint"
})
public class PbmCapabilityPropertyMetadata
    extends DynamicData
{

    @XmlElement(required = true)
    protected String id;
    @XmlElement(required = true)
    protected PbmExtendedElementDescription summary;
    protected boolean mandatory;
    protected PbmCapabilityTypeInfo type;
    protected Object defaultValue;
    protected Object allowedValue;
    protected String requirementsTypeHint;

    /**
     * Gets the value of the id property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the value of the id property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setId(String value) {
        this.id = value;
    }

    /**
     * Gets the value of the summary property.
     * 
     * @return
     *     possible object is
     *     {@link PbmExtendedElementDescription }
     *     
     */
    public PbmExtendedElementDescription getSummary() {
        return summary;
    }

    /**
     * Sets the value of the summary property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmExtendedElementDescription }
     *     
     */
    public void setSummary(PbmExtendedElementDescription value) {
        this.summary = value;
    }

    /**
     * Gets the value of the mandatory property.
     * 
     */
    public boolean isMandatory() {
        return mandatory;
    }

    /**
     * Sets the value of the mandatory property.
     * 
     */
    public void setMandatory(boolean value) {
        this.mandatory = value;
    }

    /**
     * Gets the value of the type property.
     * 
     * @return
     *     possible object is
     *     {@link PbmCapabilityTypeInfo }
     *     
     */
    public PbmCapabilityTypeInfo getType() {
        return type;
    }

    /**
     * Sets the value of the type property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmCapabilityTypeInfo }
     *     
     */
    public void setType(PbmCapabilityTypeInfo value) {
        this.type = value;
    }

    /**
     * Gets the value of the defaultValue property.
     * 
     * @return
     *     possible object is
     *     {@link Object }
     *     
     */
    public Object getDefaultValue() {
        return defaultValue;
    }

    /**
     * Sets the value of the defaultValue property.
     * 
     * @param value
     *     allowed object is
     *     {@link Object }
     *     
     */
    public void setDefaultValue(Object value) {
        this.defaultValue = value;
    }

    /**
     * Gets the value of the allowedValue property.
     * 
     * @return
     *     possible object is
     *     {@link Object }
     *     
     */
    public Object getAllowedValue() {
        return allowedValue;
    }

    /**
     * Sets the value of the allowedValue property.
     * 
     * @param value
     *     allowed object is
     *     {@link Object }
     *     
     */
    public void setAllowedValue(Object value) {
        this.allowedValue = value;
    }

    /**
     * Gets the value of the requirementsTypeHint property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRequirementsTypeHint() {
        return requirementsTypeHint;
    }

    /**
     * Sets the value of the requirementsTypeHint property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRequirementsTypeHint(String value) {
        this.requirementsTypeHint = value;
    }

}
