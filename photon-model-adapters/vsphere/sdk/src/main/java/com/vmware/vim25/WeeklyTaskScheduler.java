
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for WeeklyTaskScheduler complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="WeeklyTaskScheduler"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DailyTaskScheduler"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="sunday" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="monday" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="tuesday" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="wednesday" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="thursday" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="friday" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="saturday" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "WeeklyTaskScheduler", propOrder = {
    "sunday",
    "monday",
    "tuesday",
    "wednesday",
    "thursday",
    "friday",
    "saturday"
})
public class WeeklyTaskScheduler
    extends DailyTaskScheduler
{

    protected boolean sunday;
    protected boolean monday;
    protected boolean tuesday;
    protected boolean wednesday;
    protected boolean thursday;
    protected boolean friday;
    protected boolean saturday;

    /**
     * Gets the value of the sunday property.
     * 
     */
    public boolean isSunday() {
        return sunday;
    }

    /**
     * Sets the value of the sunday property.
     * 
     */
    public void setSunday(boolean value) {
        this.sunday = value;
    }

    /**
     * Gets the value of the monday property.
     * 
     */
    public boolean isMonday() {
        return monday;
    }

    /**
     * Sets the value of the monday property.
     * 
     */
    public void setMonday(boolean value) {
        this.monday = value;
    }

    /**
     * Gets the value of the tuesday property.
     * 
     */
    public boolean isTuesday() {
        return tuesday;
    }

    /**
     * Sets the value of the tuesday property.
     * 
     */
    public void setTuesday(boolean value) {
        this.tuesday = value;
    }

    /**
     * Gets the value of the wednesday property.
     * 
     */
    public boolean isWednesday() {
        return wednesday;
    }

    /**
     * Sets the value of the wednesday property.
     * 
     */
    public void setWednesday(boolean value) {
        this.wednesday = value;
    }

    /**
     * Gets the value of the thursday property.
     * 
     */
    public boolean isThursday() {
        return thursday;
    }

    /**
     * Sets the value of the thursday property.
     * 
     */
    public void setThursday(boolean value) {
        this.thursday = value;
    }

    /**
     * Gets the value of the friday property.
     * 
     */
    public boolean isFriday() {
        return friday;
    }

    /**
     * Sets the value of the friday property.
     * 
     */
    public void setFriday(boolean value) {
        this.friday = value;
    }

    /**
     * Gets the value of the saturday property.
     * 
     */
    public boolean isSaturday() {
        return saturday;
    }

    /**
     * Sets the value of the saturday property.
     * 
     */
    public void setSaturday(boolean value) {
        this.saturday = value;
    }

}
