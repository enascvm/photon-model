
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for AfterStartupTaskScheduler complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="AfterStartupTaskScheduler"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}TaskScheduler"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="minute" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "AfterStartupTaskScheduler", propOrder = {
    "minute"
})
public class AfterStartupTaskScheduler
    extends TaskScheduler
{

    protected int minute;

    /**
     * Gets the value of the minute property.
     * 
     */
    public int getMinute() {
        return minute;
    }

    /**
     * Sets the value of the minute property.
     * 
     */
    public void setMinute(int value) {
        this.minute = value;
    }

}
