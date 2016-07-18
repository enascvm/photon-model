
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for MonthlyByWeekdayTaskScheduler complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="MonthlyByWeekdayTaskScheduler"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}MonthlyTaskScheduler"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="offset" type="{urn:vim25}WeekOfMonth"/&gt;
 *         &lt;element name="weekday" type="{urn:vim25}DayOfWeek"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "MonthlyByWeekdayTaskScheduler", propOrder = {
    "offset",
    "weekday"
})
public class MonthlyByWeekdayTaskScheduler
    extends MonthlyTaskScheduler
{

    @XmlElement(required = true)
    @XmlSchemaType(name = "string")
    protected WeekOfMonth offset;
    @XmlElement(required = true)
    @XmlSchemaType(name = "string")
    protected DayOfWeek weekday;

    /**
     * Gets the value of the offset property.
     * 
     * @return
     *     possible object is
     *     {@link WeekOfMonth }
     *     
     */
    public WeekOfMonth getOffset() {
        return offset;
    }

    /**
     * Sets the value of the offset property.
     * 
     * @param value
     *     allowed object is
     *     {@link WeekOfMonth }
     *     
     */
    public void setOffset(WeekOfMonth value) {
        this.offset = value;
    }

    /**
     * Gets the value of the weekday property.
     * 
     * @return
     *     possible object is
     *     {@link DayOfWeek }
     *     
     */
    public DayOfWeek getWeekday() {
        return weekday;
    }

    /**
     * Sets the value of the weekday property.
     * 
     * @param value
     *     allowed object is
     *     {@link DayOfWeek }
     *     
     */
    public void setWeekday(DayOfWeek value) {
        this.weekday = value;
    }

}
