
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for StructuredCustomizations complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="StructuredCustomizations"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}HostProfilesEntityCustomizations"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="entity" type="{urn:vim25}ManagedObjectReference"/&gt;
 *         &lt;element name="customizations" type="{urn:vim25}AnswerFile" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "StructuredCustomizations", propOrder = {
    "entity",
    "customizations"
})
public class StructuredCustomizations
    extends HostProfilesEntityCustomizations
{

    @XmlElement(required = true)
    protected ManagedObjectReference entity;
    protected AnswerFile customizations;

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
     * Gets the value of the customizations property.
     * 
     * @return
     *     possible object is
     *     {@link AnswerFile }
     *     
     */
    public AnswerFile getCustomizations() {
        return customizations;
    }

    /**
     * Sets the value of the customizations property.
     * 
     * @param value
     *     allowed object is
     *     {@link AnswerFile }
     *     
     */
    public void setCustomizations(AnswerFile value) {
        this.customizations = value;
    }

}
