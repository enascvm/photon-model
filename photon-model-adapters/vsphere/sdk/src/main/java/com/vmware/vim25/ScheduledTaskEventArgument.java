
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ScheduledTaskEventArgument complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ScheduledTaskEventArgument"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}EntityEventArgument"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="scheduledTask" type="{urn:vim25}ManagedObjectReference"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ScheduledTaskEventArgument", propOrder = {
    "scheduledTask"
})
public class ScheduledTaskEventArgument
    extends EntityEventArgument
{

    @XmlElement(required = true)
    protected ManagedObjectReference scheduledTask;

    /**
     * Gets the value of the scheduledTask property.
     * 
     * @return
     *     possible object is
     *     {@link ManagedObjectReference }
     *     
     */
    public ManagedObjectReference getScheduledTask() {
        return scheduledTask;
    }

    /**
     * Sets the value of the scheduledTask property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManagedObjectReference }
     *     
     */
    public void setScheduledTask(ManagedObjectReference value) {
        this.scheduledTask = value;
    }

}
