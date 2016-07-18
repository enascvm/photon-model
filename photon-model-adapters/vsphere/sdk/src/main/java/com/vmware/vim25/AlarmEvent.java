
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for AlarmEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="AlarmEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}Event"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="alarm" type="{urn:vim25}AlarmEventArgument"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "AlarmEvent", propOrder = {
    "alarm"
})
@XmlSeeAlso({
    AlarmCreatedEvent.class,
    AlarmStatusChangedEvent.class,
    AlarmActionTriggeredEvent.class,
    AlarmEmailCompletedEvent.class,
    AlarmEmailFailedEvent.class,
    AlarmSnmpCompletedEvent.class,
    AlarmSnmpFailedEvent.class,
    AlarmScriptCompleteEvent.class,
    AlarmScriptFailedEvent.class,
    AlarmRemovedEvent.class,
    AlarmReconfiguredEvent.class,
    AlarmAcknowledgedEvent.class,
    AlarmClearedEvent.class
})
public class AlarmEvent
    extends Event
{

    @XmlElement(required = true)
    protected AlarmEventArgument alarm;

    /**
     * Gets the value of the alarm property.
     * 
     * @return
     *     possible object is
     *     {@link AlarmEventArgument }
     *     
     */
    public AlarmEventArgument getAlarm() {
        return alarm;
    }

    /**
     * Sets the value of the alarm property.
     * 
     * @param value
     *     allowed object is
     *     {@link AlarmEventArgument }
     *     
     */
    public void setAlarm(AlarmEventArgument value) {
        this.alarm = value;
    }

}
