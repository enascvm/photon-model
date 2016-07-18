
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for AlarmSnmpCompletedEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="AlarmSnmpCompletedEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}AlarmEvent"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="entity" type="{urn:vim25}ManagedEntityEventArgument"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "AlarmSnmpCompletedEvent", propOrder = {
    "entity"
})
public class AlarmSnmpCompletedEvent
    extends AlarmEvent
{

    @XmlElement(required = true)
    protected ManagedEntityEventArgument entity;

    /**
     * Gets the value of the entity property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedEntityEventArgument }
     *     
     */
    public ManagedEntityEventArgument getEntity() {
        return entity;
    }

    /**
     * Sets the value of the entity property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedEntityEventArgument }
     *     
     */
    public void setEntity(ManagedEntityEventArgument value) {
        this.entity = value;
    }

}
