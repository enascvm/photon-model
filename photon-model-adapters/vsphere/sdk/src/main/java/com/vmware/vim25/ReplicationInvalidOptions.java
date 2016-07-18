
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ReplicationInvalidOptions complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ReplicationInvalidOptions"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}ReplicationFault"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="options" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="entity" type="{urn:vim25}ManagedObjectReference" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ReplicationInvalidOptions", propOrder = {
    "options",
    "entity"
})
public class ReplicationInvalidOptions
    extends ReplicationFault
{

    @XmlElement(required = true)
    protected String options;
    protected ManagedObjectReference entity;

    /**
     * Gets the value of the options property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getOptions() {
        return options;
    }

    /**
     * Sets the value of the options property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setOptions(String value) {
        this.options = value;
    }

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

}
