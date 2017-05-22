
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HealthUpdate complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HealthUpdate"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="entity" type="{urn:vim25}ManagedObjectReference"/&gt;
 *         &lt;element name="healthUpdateInfoId" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="id" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="status" type="{urn:vim25}ManagedEntityStatus"/&gt;
 *         &lt;element name="remediation" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HealthUpdate", propOrder = {
    "entity",
    "healthUpdateInfoId",
    "id",
    "status",
    "remediation"
})
public class HealthUpdate
    extends DynamicData
{

    @XmlElement(required = true)
    protected ManagedObjectReference entity;
    @XmlElement(required = true)
    protected String healthUpdateInfoId;
    @XmlElement(required = true)
    protected String id;
    @XmlElement(required = true)
    @XmlSchemaType(name = "string")
    protected ManagedEntityStatus status;
    @XmlElement(required = true)
    protected String remediation;

    /**
     * Gets the value of the entity property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getEntity() {
        return entity;
    }

    /**
     * Sets the value of the entity property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setEntity(ManagedObjectReference value) {
        this.entity = value;
    }

    /**
     * Gets the value of the healthUpdateInfoId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getHealthUpdateInfoId() {
        return healthUpdateInfoId;
    }

    /**
     * Sets the value of the healthUpdateInfoId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setHealthUpdateInfoId(String value) {
        this.healthUpdateInfoId = value;
    }

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
     * Gets the value of the status property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedEntityStatus }
     *     
     */
    public ManagedEntityStatus getStatus() {
        return status;
    }

    /**
     * Sets the value of the status property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedEntityStatus }
     *     
     */
    public void setStatus(ManagedEntityStatus value) {
        this.status = value;
    }

    /**
     * Gets the value of the remediation property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRemediation() {
        return remediation;
    }

    /**
     * Sets the value of the remediation property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRemediation(String value) {
        this.remediation = value;
    }

}
